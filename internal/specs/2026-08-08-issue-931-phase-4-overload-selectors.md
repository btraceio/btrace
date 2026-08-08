# Issue #931, Phase 4: opt-in `@ExternalType` overload selectors

Date: 2026-08-08
Status: design proposal; implementation and planning must wait for a CLEAN design review
Baseline: `develop` at `96c7e4b4` (#941, #942, and #943 merged)

## Problem and goal

Phase 1 deliberately rejects duplicate adapted method names, preventing accidental overload
selection. Phase 3 makes target-library signatures expressible, but two target types can both have
an opaque `Object` Java boundary and therefore need distinct local adapter method names. Phase 4
adds an explicit compile-time target-overload selector. It maps only to a target name; the existing
declared erased return/parameter types, including Phase 3 marked types, form the exact signature.
There is no runtime compatible-method search, coercion, or argument-driven overload selection.

## Decision: `@ExternalType.Overload("targetName")`

Add a nested public source-only annotation:

```java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Overload {
  String value();
}
```

`Overload` supplies the target method name. It is allowed only on an abstract adapted method in an
`@ExternalType` interface, and only in a group of at least two adapted methods selecting that same
target name. Every member of that target-name group must carry the annotation; a one-member target
selection remains the existing unannotated method-name binding.

```java
@ExternalType("vendor.Parent")
public interface ParentApi {
  @ExternalType.Overload("describe")
  String describeText(String text);

  @ExternalType.Overload("describe")
  String describeChild(@ExternalType.Type(ChildApi.class) Object child);
}
```

The target keys are `(describe, virtual, (String)String)` and
`(describe, virtual, (vendor.Child)String)`. Generated Java methods remain
`describeText(Object self, String)` and `describeChild(Object self, Object)`: neither `ChildApi`
nor `vendor.Child` enters the public adapter API.

The annotation has no descriptor, `Class<?>[]`, or return-type attributes. The full selector is
target name plus static/virtual kind plus the existing exact erased return and parameter types.

| Option | Decision | Reason |
| --- | --- | --- |
| `@ExternalType.Overload("name")` plus declared types | Chosen | Minimal Java-8 opt-in; supports aliases for multiple `Object`-erased target types and reuses Phase 3 lookup types. |
| Descriptor or `Class<?>[]` attributes | Rejected | Duplicates source signatures and drifts from Phase 3 metadata. |
| Runtime assignability/first-match selection | Rejected | Loader-sensitive, ambiguous, and violates exact `MethodHandle` semantics. |
| Implicitly permit duplicate names | Rejected | Changes legacy invalid-source behaviour and cannot handle distinct aliases. |
| One-method target-name aliases | Rejected | Broadens scope; a unique legacy declaration is already exact. |

`SOURCE` retention is intentional. The masked published JAR must expose
`ExternalType$Overload.class` for external compilation, but generated adapters do not retain or
inspect the annotation at runtime.

## Validation and processor contract

`ExternalTypeProcessor` adds `io.btrace.core.extensions.ExternalType.Overload` to supported
annotation types and reads its value through `AnnotationMirror`/`AnnotationValue`. Before emitting
an outer interface, it collects all non-default, non-Java-static methods and validates selectors as
one model.

1. A selector uses frozen JVM member-name semantics, not host-JDK
   `SourceVersion.isIdentifier`/`isKeyword` behaviour. It is nonblank and an unqualified JVM
   method name: it contains none of `.`, `;`, `[`, or `/`, and is not `<init>` or `<clinit>`.
   Preserve the value exactly for lookup; do not trim a nonblank value. This intentionally accepts
   `_` and other legal JVM aliases even when a newer host JDK treats them differently from Java 8.
   Local adapter method names remain ordinary Java source declarations and are consequently
   validated by javac as Java identifiers. Staticness comes solely from `ExternalType.Static`.
2. A selector outside an adapted interface or on a default/Java-static skipped method is an
   unconsumed, source-positioned processor error. Parameter/type uses and repeated annotations
   retain normal javac applicability/non-repeatable diagnostics.
3. For each **Java method-name** duplicate group, if any member lacks a selector, preserve the
   current `@ExternalType does not support overloaded methods` error. Such a group can proceed
   only when every member has a valid selector.
4. Group candidates by **target name** (selector value or legacy Java name). A group with more than
   one member is valid only when every member selects explicitly. A selector group of one is an
   error: this phase is not general aliasing.

5. Selected members must have distinct exact target keys: target name, static/virtual kind, erased
   lookup return type, and erased lookup parameter types. Phase 3 positions compare referenced
   target FQNs, not emitted `Object`. Reject duplicate keys rather than redundant aliases/caches.
6. Independently calculate generated Java descriptors: virtual `(Object self, emitted args...)`,
   static legacy `(emitted args...)`, and static explicit `(ClassLoader, emitted args...)`. For one
   generated Java name, reject collisions, including selected `read()` and `read(ClassLoader)`,
   whose Phase 2 explicit/legacy forms collide.
7. Retain Phase 3 `Type` validation, marker consumption, and owner-loader lookup metadata. Any
   invalid selected method prevents emitting that interface’s adapter.

Diagnostics identify the method/group/collision and prescribe marking the whole selected group,
using distinct exact signatures, or renaming a local method. Existing no-selector unique methods
retain their source behaviour and emitted lookup.

## Generated lookup, loaders, and invocation

Extend `MethodSpec` with separate `adapterName` and `targetName`. They are equal for legacy
members. `AdapterEmitter` uses `adapterName` for generated Java declarations and `targetName` in
`findVirtual`, `findStatic`, and `ExternalTypeResolutionException` member text.

For every member, retain Phases 1-3 resolution: ordinary types become erased class literals;
Phase 3 marked types use `Class.forName(fqn, false, owner.getClassLoader())`; and
`MethodHandles.publicLookup().findVirtual/findStatic(owner, targetName, exactMethodType)` is the
only lookup. Return types participate in the exact `MethodType`.

The handle sees only the adapter method’s declared values. There is no candidate enumeration,
`isAssignableFrom`, varargs packing, boxing policy, fallback selector, or second-signature retry.
Wrong-loader opaque objects and incompatible arguments fail transparently; they never change the
selected overload.

Static members retain both Phase 2 forms: legacy TCCL with null-to-system fallback, and explicit
non-null leading loader. After either obtains the owner, Phase 3 marked types resolve from
`owner.getClassLoader()`, never TCCL, selected loader, or extension loader. Virtual dispatch keeps
its receiver defining-loader policy.

## Cache, failure, and security boundaries

Each selected member has its existing independent `ClassValue<ResolvedCall>`, static monitor, weak
identity loader index, and resolution counter. Different exact target keys require different
handles; no selector/name global map, second target cache, negative cache, or strong loader map is
allowed. Success-only publication and unloadability remain unchanged.

Owner/marked-type/missing-selected-member/access failures remain retryable
`ExternalTypeResolutionException`s. The member in the message is the selected target name. Target
exceptions, wrong-argument invocation errors, security/linkage errors, and target-thrown resolution
exceptions remain transparent.

`publicLookup()` remains the public/exported-module boundary. Phase 4 does not initialize classes,
mutate TCCL/classpaths, use implementation loaders, bypass SecurityManager checks, open modules,
use private lookup, or access non-public members.

## Compatibility and published surface

- Existing unique no-selector contracts retain source, binary, runtime, static-form, failure, and
  cache behaviour.
- Existing duplicate Java-name contracts keep the current duplicate-name error until every member
  opts in; no previously-invalid source becomes silently valid.
- A new selected group needs an extension rebuild. Its local generated methods are new opt-in
  surface and its target aliases make no compatibility claim for a previously nonexistent adapter.
- `ExternalType.Overload` is the only new public source API. It is source-retained and needs no
  runtime helper or target-class reference. External builds require its narrow class plus current
  processor service/support in published `io.btrace:btrace`.
- Phase 3 opaque `Object` descriptors remain intact; selectors do not leak contract or target types
  into generated public APIs.

## Affected components

- `btrace-core`: `ExternalType`, `ExternalTypeProcessor`, `MethodSpec`, possibly `AdapterSpec`,
  `AdapterEmitter`, `ExternalTypeAnnotationTest`, and `ExternalTypeProcessorTest`.
- `btrace-dist`: separated smoke inputs/assertions and masked-JAR inspection only unless a specific
  missing annotation/processor entry proves a narrow packaging change necessary.
- `btrace-extensions:btrace-ext-test` and `integration-tests`: application-only overloads,
  regenerated test extension, dedicated marker, and real client-agent-target coverage.
- `docs/BTraceExtensionDevelopmentGuide.md` (canonical) and
  `docs/architecture/provided-style-extensions.md` (manual-path pointer). No Gradle plugin DSL.

## Acceptance criteria

1. `ExternalType.Overload` is source-retained, method-targeted, and externally compilable from the
   masked JAR. A valid two-member group uses selector target names in generated lookup.
2. A unique no-selector method is generated/resolved as before. A duplicate Java-name group with a
   legacy member has the established duplicate-name error.
3. Valid source overloads and distinct-name aliases select target `describe(String)` and
   `describe(vendor.Child)` exactly. The latter uses the Phase 3 FQN in `MethodType` while keeping
   `Object` public descriptors. Return type participates in the exact target key.
4. Diagnostics reject blank, JVM-illegal, and special selector values; selector outside/skip
   contexts; one-member selector groups; target groups with legacy members; duplicate exact keys;
   failed Phase 3 markers; and static legacy/explicit generated descriptor collisions. Valid groups
   have no duplicate diagnostics; repeated/inapplicable annotations retain javac native diagnostics.
   A `javac --release 8` fixture must successfully select an application method named `_`, proving
   selector validation is not host-JDK keyword dependent; fixtures reject `<init>`, `<clinit>`,
   blank, and a name containing `/`.
5. Two hostile isolated loaders with same-FQN overloaded owner/child types resolve independent
   selected handles and values. A loader-A child cannot invoke loader-B’s child overload, and
   hostile loader equality/hash code is unused.
6. Legacy-TCCL and explicit-loader static overloads retain Phase 2/3 selection and type-loader
   rules. Explicit null fails before lookup/cache; null TCCL retains system fallback.
7. Missing selected overload, inaccessible selected overload, and missing marked type are
   cause-preserving/retryable `ExternalTypeResolutionException`s using target selector name.
   Target failures and wrong-argument invocation failures are transparent; no fallback overload.
8. The staged test extension, real client, agent, target JVM, and protocol invoke ordinary and
   marked-target application overloads through local aliases, emit target-owned markers, and retain
   Phase 2 explicit-loader coverage/release-extension isolation.
9. A clean masked JAR contains `ExternalType$Overload.class`, processor service/implementation,
   and support. `javac --release 8` separately compiles target classes and contract/driver sources;
   contracts use only the masked JAR on classpath/processorpath, and the driver runs both selected
   paths with only target output, extension output, and that JAR.
10. Docs state exact selector semantics and retain all Phase 2/3 and access boundaries; they do not
    promise coercion, target-type generic/array support, fields, constructors, private/JPMS access,
    or a general alias mechanism.

## Verification gates

Use a workspace-local Gradle cache, redirect every Gradle command to `/tmp`, and filter before
reading. The implementation plan must run:

```text
:btrace-core:test
:btrace-core:spotlessCheck
clean :btrace-dist:btraceJar
:btrace-dist:build
:btrace-extensions:btrace-ext-test:spotlessCheck
:integration-tests:spotlessCheck
:integration-tests:test -Pintegration --tests tests.ExternalTypeAdapterIntegrationTest
```

It must inspect exactly one masked `btrace.jar`, its processor service, `ExternalType$Overload`,
and processor implementation; compile target sources separately with `javac --release 8`; compile
contracts/driver using `javac --release 8 -cp/-processorpath <masked-jar>` without target
source/class paths; inspect generated local `Object` signatures and exact target names/no target or
contract `.class` leakage; run the driver with only separated outputs/JAR; and finish with
`git diff --check`. Each positive test needs a malformed, wrong-loader, missing-overload, or
unfixed-behaviour case that demonstrably fails for the intended reason. Use IPv4
`JAVA_TOOL_OPTIONS` only for the known restricted-environment address-selection issue.

## Non-goals and completion boundary

Phase 4 excludes one-method aliases, overload-by-value heuristics, varargs adaptation, boxing or
coercion policy, generic/array target types, fields, constructors, casts/predicates,
`MethodHandleCache` redesign, loader fallback, TCCL mutation, private lookup, and JPMS bypasses.

It is complete only when explicit groups map local declarations plus Phase 3 lookup types to exact
public target handles, legacy duplicate protection remains, cache/security boundaries hold, masked
external compilation works, and real integration proves both selected overloads. It does not
authorize Phase 5 or later work.
