# Issue #931, Phase 5: public fields, constructors, and type predicates

Date: 2026-08-08
Status: design proposal; implementation and planning must wait for a CLEAN design review
Baseline: `develop` after Phase 4 (#941, #942, #943, and #944)

## Problem and goal

Phases 1--4 make public methods, target-type positions, static-loader selection, and explicit
method-overload groups available through generated adapters. A typical application API also exposes
public state, factories expressed as constructors, and values that must be recognised or narrowed
before another adapter is called. Today all four cases require a hand-written public lookup or
`Class` operation.

Phase 5 adds only the five operations required for those cases: public instance/static field get,
public instance/static field set, public construction, `isInstance`, and `cast`. It is deliberately
not a general member-description language. It preserves the Phase 1 successful-only cache,
Phase 2 static-loader policy, Phase 3 target-type metadata, Phase 4 method-selector rules, and the
existing public/exported access boundary.

## Decision: five source-only operation markers

Add these nested annotations to `ExternalType`, all retained only in source and targeted at a
method declaration:

```java
@interface Getter { String value(); }
@interface Setter { String value(); }
@interface Constructor {}
@interface InstanceOf {}
@interface Cast {}
```

`Getter` and `Setter` bind to the named target field. `Constructor` binds to the owning external
type's public `<init>`. `InstanceOf` and `Cast` bind to the owning external type itself, not to a
member. Each marker is a distinct operation selector, so no `Overload` value or descriptor string
is needed.

```java
@ExternalType("vendor.Widget")
interface WidgetApi {
  @ExternalType.Getter("name")
  String name();

  @ExternalType.Setter("name")
  void setName(String name);

  @ExternalType.Static
  @ExternalType.Getter("DEFAULT")
  @ExternalType.Type(ChildApi.class)
  Object defaultChild();

  @ExternalType.Constructor
  Object create(String name);

  @ExternalType.InstanceOf
  boolean isWidget(Object value);

  @ExternalType.Cast
  Object castWidget(Object value);
}
```

The generated calls are respectively `name(Object self)`, `setName(Object self, String)`,
`defaultChild()` / `defaultChild(ClassLoader)`, `create(String)` /
`create(ClassLoader, String)`, `isWidget(Object)`, and `castWidget(Object)`. The explicit
`ClassLoader` parameter is adapter control data, as in Phase 2; it never enters a target field
type, constructor `MethodType`, or invocation argument list.

The markers are source-only processor input. The published masked JAR must expose their class
files for external compilation, but generated adapters do not inspect annotations at runtime.

### Why separate markers

| Option | Decision | Reason |
| --- | --- | --- |
| Five narrow operation markers | Chosen | Java declarations continue to describe Java values; each marker supplies exactly the missing member kind and permits exhaustive processor validation. |
| General `@ExternalType.Member(kind, name, descriptor)` | Rejected | Duplicates Java signatures, encourages reflection-like expansion, and would need a broad grammar, coercion policy, and migration rules. |
| Encode fields and constructors as method names (`getX`, `<init>`) | Rejected | It is ambiguous, makes typo diagnostics poor, and Phase 4 correctly reserves method selectors for methods. |
| Extend `MethodHandleCache` first | Rejected | Generated adapters need Phase 1's loader-safe `ClassValue`/weak-index lifecycle, while the manual helper intentionally retains strong owner keys. The manual API remains a direct public-lookup escape hatch in this phase. |

## Contract validation

All Phase 1--4 interface, non-empty-owner, abstract-method, generic-erasure, target-type-marker,
generated-descriptor, and method-selector validation continues to apply unless a rule below
supersedes it. A method has at most one operation marker. `Static` is legal only with `Getter` or
`Setter` or an ordinary `METHOD`; it remains the sole way to select a static **field** and retains
its existing meaning for a static target method. It is illegal with `CONSTRUCTOR`, `INSTANCE_OF`,
or `CAST`. `Overload` is legal only on an ordinary method operation and is rejected on every Phase
5 operation. `Type` is permitted only in the positions described below; existing unconsumed-marker
diagnostics must continue to be source-positioned and no adapter is emitted for an invalid
contract.

The processor model gains an explicit `OperationKind`: `METHOD`, `GETTER`, `SETTER`,
`CONSTRUCTOR`, `INSTANCE_OF`, or `CAST`. `MethodSpec` carries that kind and the operation-specific
lookup metadata. Both Phase 4 Java method-name duplicate grouping and target-name grouping,
including the rule that every member of a selected overload group carries `@Overload`, apply
**only** to `METHOD` entries. A field name, `<init>`, or predicate operation is never a member of
either Phase 4 group and cannot carry `Overload`. Exact target identity includes the operation
kind, so an ordinary method and a field/predicate whose local or target text happens to coincide
are not duplicate target members. Generated Java descriptor collision checking deliberately
remains cross-kind and is the sole local-name-overlap restriction across operation kinds: two
operations cannot emit the same Java method descriptor even when their target identities differ.

Field names use the same frozen JVM-name validation as Phase 4 selectors: they are nonblank,
unqualified names without `.`, `;`, `[`, or `/`, and are neither `<init>` nor `<clinit>`. Preserve
a nonblank name exactly; do not use host-JDK identifier/keyword rules. A field marker outside an
adapted abstract interface method, on a default/static-interface method, or combined with another
operation marker is a processor error.

One contract may declare one getter and one setter for the same target field. They must use the
same `Static` state and the same exact lookup type (including a Phase 3 target FQN rather than the
emitted `Object` when marked). This is the supported read/write pair. A second getter or second
setter for the same target field is a rejected same-field alias, even when its local adapter name
or ordinary Java type differs. A field name cannot be used once as static and once as instance in
one contract. These checks are processor-owned and prevent redundant caches or a misleading
appearance of multiple physical fields; runtime lookup remains the authority for whether the
named public field actually exists.

### Getter

`@Getter("field")` requires a non-`void` return and no declared target parameters. Its return type
is the exact erased target field type. A direct target-library field type uses the existing
`@Type(OtherContract.class) Object` return marker; ordinary primitive, JDK, generic-erasure, and
`Object` field types keep their existing meaning. The generated virtual form has `Object self` as
its only parameter; `@Static` instead produces Phase 2 legacy and explicit-loader forms.

### Setter

`@Setter("field")` requires `void` return and exactly one declared target parameter. That parameter
is the exact erased target field type and may use the existing direct-`Object` `@Type` marker. The
generated virtual form is `(Object self, fieldValue)`; `@Static` has the existing zero-receiver
legacy and explicit-loader forms. Fluent setters, multiple-field writes, and a getter/setter
combined in one declaration are out of scope.

### Constructor

`@Constructor` requires direct `Object` return, no return `@Type` marker, and no `Static` or
`Overload` annotation. The owning `@ExternalType` name itself is the produced target type, so a
return marker would be redundant and misleading. Parameters use the ordinary exact erased types
and may use Phase 3 direct-`Object` `@Type` markers.

Construction has no target receiver. It therefore has the same two loader forms as a static
operation: a legacy form that resolves through TCCL (falling back to the system loader only when
TCCL is null), and an explicit form whose leading non-null `ClassLoader` selects the owner.
Distinct constructor parameter signatures may use ordinary Java overloads under the same local
adapter name; each signature directly forms its exact constructor `MethodType`, so no Phase 4
selector/group is involved. They are valid only when **every** generated legacy and explicit-loader
descriptor is distinct, including descriptors generated for another constructor or operation. For
example, `create()` generates explicit `create(ClassLoader)`, which collides with the legacy form
generated for `create(ClassLoader)`. The processor must reject that source with the established
generated-descriptor collision diagnostic rather than emitting an ambiguous adapter. The same
cross-kind generated-descriptor check rejects a real collision with another operation.

### Type predicates and casts

`@InstanceOf` requires `boolean` return and exactly one direct `Object` parameter.
`@Cast` requires direct `Object` return and exactly one direct `Object` parameter. Neither permits
`Static`, `Overload`, or `Type`: the adapted owner is already the target class and the argument is
the value being tested or narrowed. At most one `InstanceOf` and one `Cast` operation may appear in
one contract; aliases and multiple predicate variants would be general reflection surface without
new capability.

For non-null values, both operations resolve the owner with the candidate value's defining loader:

```java
Class<?> owner = Class.forName(OWNER, false, value.getClass().getClassLoader());
```

This is the virtual loader rule applied to the value that supplies the only meaningful application
loader. It avoids an ambient TCCL choice and prevents a same-FQN class from a different isolated
application loader being accepted. The resolved owner is then used as `owner.isInstance(value)` or
`owner.cast(value)`.

Null follows the corresponding `Class` operation exactly: `isInstance(null)` returns `false` and
`cast(null)` returns `null`. Neither case attempts class resolution, so it cannot turn an ordinary
null check into an availability failure. A non-null wrong-type `cast` throws the normal
`ClassCastException`; it is not converted to `ExternalTypeResolutionException`.

## Generated lookup and caching

### Fields and constructors

Every getter, setter, and constructor has one independent `ClassValue<ResolvedCall>` and uses the
existing generated `ResolvedCall` layout. The value stores the owner and exact handle. Its compute
function performs `Class.forName(OWNER, false, loader)`, resolves all Phase 3 marked field or
constructor parameter types through `owner.getClassLoader()`, and then performs exactly one public
lookup:

```java
lookup.findGetter(owner, fieldName, exactFieldType)
lookup.findSetter(owner, fieldName, exactFieldType)
lookup.findStaticGetter(owner, fieldName, exactFieldType)
lookup.findStaticSetter(owner, fieldName, exactFieldType)
lookup.findConstructor(owner, MethodType.methodType(void.class, exactParameterTypes))
```

Instance fields key their `ClassValue` with `self.getClass()` and retain the Phase 1 null-receiver
behaviour. Static fields and constructors reuse, unchanged, the per-operation monitor,
weak-identity loader-to-owner index, bootstrap/system sentinels, expunging, and owner-keyed
`ClassValue` from Phases 1--2. The legacy and explicit forms share each successful entry. No
strong loader key, FQN-only class cache, second target-type cache, negative cache, or new global
runtime helper is permitted.

This preserves success-only publication and retry: class, marked-type, field, constructor, or
access failure places no `ClassValue`/loader-index entry. Static and constructor resolution remain
single-flight under their per-operation monitor; virtual field and predicate first use makes no
new single-flight promise.

### Predicates and casts

Each predicate/cast has a `ClassValue<Class<?>>` keyed by `value.getClass()`. Its computation loads
the owner with that class's defining loader and validates that the owner is accessible to
`MethodHandles.publicLookup()` by resolving the inherited public `Object.getClass` member without
invoking it:

```java
MethodHandles.publicLookup().findVirtual(owner, "getClass", MethodType.methodType(Class.class));
```

The returned probe handle is discarded. Unlike `Lookup.in(owner)`, this lookup enforces that the
owner is public and accessible/exported to public lookup. The class value is published only after
both class load and probe succeed. The probe does not discover or invoke an arbitrary target
member, initialize the target, or introduce a class-loader fallback.

The successful class value naturally distinguishes same-FQN owners from isolated application
loaders and is releasable with its key class. It contains no target values. Failures are not
cached. Null predicate/cast calls bypass this cache according to the documented `Class` semantics.

## Failure, initialization, access, and security

All owner and Phase 3 marked-type loads use `Class.forName(..., false, loader)`. Handle lookup,
the predicate access probe, and cache setup must not initialize a target class. Invoking a static
field getter/setter or constructor may initialize the target class when the JVM normally requires
it; that is ordinary target-operation behaviour and must not be hidden or pre-triggered during
resolution. `isInstance` and `cast` do not initialize a resolved target class.

`ClassNotFoundException`, `NoSuchFieldException`, `NoSuchMethodException`, and
`IllegalAccessException` raised while resolving an owner, marked type, public operation, or the
non-invoking predicate/cast access probe become the established cause-preserving
`ExternalTypeResolutionException`. Its owner remains the contract's target FQN; its member is the
selected field name, `<init>` for a constructor, or the literal operation name `isInstance`/`cast`
for a predicate/cast. Update its JavaDoc to include the field exception type and predicate/cast
access probe.

Static/instance-kind mismatches, absent fields/constructors, final-field writes rejected by the
lookup, non-public members, and inaccessible named-module packages are resolution failures under
that rule. Normal invocation-time failures are deliberately not caught by resolution handling:
field/constructor-triggered initializer errors, constructor-thrown exceptions, wrong-loader or
wrong-type handle arguments, `ClassCastException` from `cast`, `SecurityException`, linkage
errors, and a target-thrown `ExternalTypeResolutionException` propagate unchanged. There is no
fallback to another field, constructor, loader, or compatible type.

Phase 5 does not use `setAccessible`, core reflection fields/constructors, `privateLookupIn`,
module-opening flags, instrumentation/module rewrites, classpath changes, an implementation
loader, privileged lookup, or TCCL mutation. Only public members of an owner accessible to public
lookup are supported.

## Compatibility and published surface

Existing contracts, generated class names, ordinary/overloaded method dispatch (including ordinary
`@Static` methods), `Type` behaviour, virtual defining-loader selection, static legacy TCCL
fallback, explicit static loader overloads, success-only caching, failure translation, and target
exception propagation do not change. Already-built extensions retain their old generated output; a
new operation requires a rebuild.

The five nested source annotations are the only new public API. The generated API exposes only
ordinary erased Java types and `Object`; it never exposes target-library types or contract class
literals. Constructors return `Object` by design, and predicate/cast values remain opaque. The
published masked `io.btrace:btrace` JAR must contain all five annotation class files, the processor
service/implementation, `ExternalType`, `ExternalTypeResolutionException`, and all currently
required processor support. No Gradle DSL, extension-loading, attach/protocol, permission, or
packaging policy change is intended unless the external artifact proof identifies a specific
omission.

## Affected components

- `btrace-core`: `ExternalType` and resolution-exception JavaDoc; processor validation/model;
  `MethodSpec`/adapter specification as needed; generated emitter; focused processor/runtime
  tests. Generated source stays Java 8 compatible.
- `btrace-dist`: external processor smoke fixtures and masked-JAR assertions only, absent a
  demonstrated packaging defect.
- `btrace-extensions:btrace-ext-test` and `integration-tests`: a staged application-only fixture
  that exercises a field, construction, and a predicate/cast through extension, client, agent,
  target JVM, and protocol.
- `docs/BTraceExtensionDevelopmentGuide.md` (normative) and
  `docs/architecture/provided-style-extensions.md` (manual boundary/pointer). Update their
  unsupported-feature tables and direct manual examples precisely.

## Acceptance criteria

1. External contracts can generate and invoke exact public instance and static getter/setter
   handles, including primitive/JDK and Phase 3 direct-`Object` target field types. Static field
   legacy and explicit-loader calls retain Phase 2 behaviour and share a successful cache entry.
2. Contracts can construct a public target through `@Constructor Object localName(...)`, including
   marked target-only parameter types. The two constructor loader forms are exact, reject a null
   explicit loader before cache access, and never pass that loader to the target constructor.
3. `isInstance(Object)` and `cast(Object)` use the non-null value's defining loader, distinguish
   same-FQN types from isolated loaders, use no TCCL, and follow `Class.isInstance` / `Class.cast`
   null and `ClassCastException` behaviour exactly.
4. Field, constructor, predicate, and cast resolution remains loader-isolated, success-only,
   unloadable under the existing `ClassValue`/weak-static-index topology, and retryable after a
   mutable loader makes a missing class/member available. Hostile loader `equals`/`hashCode` is not
   used.
5. Diagnostics reject malformed field names; incompatible marker combinations; non-void getters;
   malformed setters; invalid same-field aliases/read-write pairs; constructor
   non-`Object`/return-marker/static forms; malformed predicate or cast signatures; duplicate
   predicates/casts; Phase 4 selectors on special operations; and generated descriptor collisions.
   Fixtures prove both Phase 4 local-Java-name and target-name overload grouping exclude fields,
   constructors, and predicates; prove `create()` plus `create(ClassLoader)` fails because the
   former's explicit form collides with the latter's legacy form; and prove a cross-kind descriptor
   collision still fails. Invalid contracts emit no partial adapter.
6. Missing owner/marked type/field/constructor, inaccessible field/constructor, or an inaccessible
   predicate/cast owner rejected by the non-invoking public `getClass` lookup gives a
   cause-preserving `ExternalTypeResolutionException` using the defined member text and is
   retried. Target invocation failures, wrong-type arguments, cast failures, security/linkage
   errors, and target initialization failures remain transparent.
7. Resolution never initializes a target class. A fixture proves the class is uninitialized after
   a successful lookup-only path and initialized only when a static field operation or constructor
   is invoked, as required by JVM semantics.
8. Existing Phase 1--4 processor/runtime coverage remains green: ordinary and selected methods,
   ordinary `@Static` methods, target-type chains, legacy null-TCCL static dispatch, explicit
   loader dispatch, and their cache/failure boundaries are unchanged. Add a regression fixture
   proving an ordinary static method can coexist with Phase 5 operations and retains both generated
   static forms.
9. The real staged extension/client/agent/target path invokes an instance or static field,
   constructs a target object, and performs an `isInstance`/cast before another adapter call,
   producing a target-owned marker. The target library remains absent from the extension compile
   classpath and the test extension remains unstaged from release output.
10. A clean masked JAR contains all five annotation classes and processor support. A separated
    `javac --release 8` smoke compiles target sources separately, then contracts/driver with only
    that JAR on classpath and processorpath; generated descriptors contain `Object` rather than
    target/contract class literals, and the driver runs solely with target output, extension
    output, and the masked JAR.
11. Documentation explains all five operation forms, their exact signatures, loader selection,
    null/failure/initialization rules, static field and constructor forms, and the continuing
    manual path for generic/array types, bulk/reflection-style operations, non-public/JPMS access,
    and all deferred features.

## Verification gates

The implementation plan must run from the repository root with a workspace-local Gradle cache,
redirect each Gradle invocation to `/tmp`, and filter it before inspection. Build distribution
artifacts before the integration test:

```text
:btrace-core:test
:btrace-core:spotlessCheck
clean :btrace-dist:btraceJar
:btrace-dist:build
:btrace-extensions:btrace-ext-test:spotlessCheck
:integration-tests:spotlessCheck
:integration-tests:test -Pintegration --tests tests.ExternalTypeAdapterIntegrationTest
```

It must inspect the clean masked JAR and processor service, run the separated Java 8 external
smoke described in acceptance criterion 10, and finish with `git diff --check`. Each positive
operation test needs a discriminating rejection or unfixed-behaviour proof: e.g. a wrong-type
field/constructor signature, isolated-loader cast, or missing-field retry. If the restricted
environment hits the known address-selection failure, retry only the affected Gradle command with
the repository's documented IPv4 `JAVA_TOOL_OPTIONS` setting.

## Non-goals and completion boundary

Phase 5 does not add static initializers, arbitrary reflection, field enumeration, bulk copying,
array/generic target types, fluent setters, constructor selector groups, target-type wrappers,
one-member method aliases, runtime overload/coercion, a `MethodHandleCache` lifecycle redesign,
loader fallback or TCCL mutation, non-public/private access, JPMS bypasses, target classpath
dependencies, or changes to extension loading and protocol behaviour.

It is complete only when these five explicit operations preserve all Phase 1--4 loader, cache,
exact-type, public-access, and failure boundaries; are externally compilable from the masked JAR;
and are exercised across the real extension/client/agent/target path. It does not authorize Phase
6 security/access work.
