# Issue #931, Phase 3: target-type signatures and chained `@ExternalType` adapters

Date: 2026-08-07
Status: design proposal; implementation and planning must wait for a CLEAN design review
Baseline: `develop` at `a6a7ed57` (#941 and #942 merged)

## Problem and goal

Phase 1 supports target methods only when their erased Java signatures can be written directly by
the extension. `Object` is deliberately not a coercion escape hatch: an adapter declaration
returning or accepting `Object` cannot resolve a target member that returns or accepts an
application-library type. For example, `Parent.child(): vendor.Child` still needs manual
`ClassLoadingUtil` and `MethodHandleCache` code even if both types are public external types.

Phase 3 adds an explicit declaration for direct target-library parameter and return positions.
Generated code resolves those target `Class<?>` values in the resolved owner's defining
application loader and substitutes them into the exact `MethodType`. The Java dispatcher boundary
remains `Object`, allowing opaque target objects to flow directly into another generated adapter:
no wrapper, proxy, target-library compile dependency, or loader lookup by name alone.

This phase is limited to method signatures and chains. It does not add overload selection, fields,
constructors, casts/predicates, automatic loader selection, non-public access, module rewrites, or
a general reflection language.

## Decision: `@ExternalType.Type(Contract.class) Object`

Add the nested public source annotation:

```java
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.PARAMETER})
public @interface Type {
  Class<?> value();
}
```

It is legal only as a method declaration annotation for a **direct** `Object` return, or as a
formal-parameter declaration annotation for a direct `Object` parameter, in an `@ExternalType`
interface. Its value names another interface (the enclosing contract is allowed) that has a
non-empty `@ExternalType("target.fqn")`. Java syntax remains the natural
`@ExternalType.Type(ChildApi.class) Object child()` and
`void replace(@ExternalType.Type(ChildApi.class) Object child)` forms. The processor reads the
contract's target FQN from the compiler model and treats it solely as generated lookup metadata.

```java
@ExternalType("vendor.Child")
public interface ChildApi {
  String label();
}

@ExternalType("vendor.Parent")
public interface ParentApi {
  @ExternalType.Type(ChildApi.class)
  Object child();

  void replaceChild(@ExternalType.Type(ChildApi.class) Object child);
}
```

The target descriptors are `()Lvendor/Child;` and `(Lvendor/Child;)V`, while generated Java
methods remain `Object child(Object self)` and `void replaceChild(Object self, Object child)`.
The extension can chain without a target-library dependency or cast:

```java
Object child = ParentApi$Ext.child(parent);
String label = ChildApi$Ext.label(child);
```

The first call resolves `vendor.Child` to construct `Parent.child`'s exact `MethodType`; the
second derives its owner from the returned object's defining loader. The actual object is never
wrapped or transformed.

### Options assessed

| Option | Decision | Reason |
| --- | --- | --- |
| `@ExternalType.Type(ChildApi.class) Object` declaration marker | Chosen | It is explicit at the only JVM-signature position needing it, while `METHOD`/`PARAMETER` targets make exhaustive JSR-269 validation possible. The class literal is compiler-refactor-safe and links to the existing external contract; the adapter API remains opaque `Object`. |
| Use an annotated contract interface directly in method signatures | Rejected | `vendor.Child` does not implement `ChildApi`; generated return casts fail and dispatcher descriptors would leak extension contract types. |
| A string marker such as `@Type("vendor.Child")` | Rejected | It duplicates the target FQN owned by `ChildApi`, is typo/refactor-prone, and cannot validate an available chained contract. |
| Wrapper/proxy result objects | Rejected | They add allocation, identity, null, lifecycle, cross-loader, and runtime-public-API problems unrelated to exact method lookup. |
| A method-level signature string | Rejected | It duplicates Java declarations and becomes an overload-selection language; selectors remain Phase 4. |

`SOURCE` retention is intentional: this is processor input, not runtime policy. The published
masked JAR must contain `ExternalType$Type.class` for external compilation, but generated adapters
must never reference `ChildApi.class` or inspect this annotation at runtime.

## Processor and generated-code contract

### Validation and Java 8 annotation processing

Update `ExternalTypeProcessor`'s supported annotation types to claim both
`io.btrace.core.extensions.ExternalType` and `io.btrace.core.extensions.ExternalType.Type`; there
is no new processor option or Gradle DSL. It must read declaration annotation mirrors through the
Java 8 annotation-processing model rather than class-loading annotation values (which risks
`MirroredTypeException`).

For each selected interface method, the processor must:

1. Retain Phase 1/2 validation: interface/non-empty owner, duplicate method-name rejection,
   default/static interface method skipping, and exact erased ordinary types.
2. Consume the non-repeatable `@ExternalType.Type` annotation only from an `ExecutableElement`
   return declaration or `VariableElement` formal-parameter declaration. The corresponding erased
   source type must be exactly `java.lang.Object`; reject any declared return/parameter type other
   than direct `Object`.
3. Resolve the marker's class-valued element to a `TypeMirror`. It must name an interface annotated
   with non-empty `@ExternalType`; otherwise issue a source-positioned diagnostic and emit no
   partial adapter.
4. Retain ordinary generic erasure. Unannotated `List<String>` remains `java.util.List`; a target
   method erased to `List` does not need a marker. Phase 3 does not model generic elements.

`Type` intentionally is not a `TYPE_USE` annotation. Generic-argument, array-component, local,
cast, and `new`-expression type-use attempts are javac annotation-target applicability errors
before processor validation. A declaration annotation such as
`@ExternalType.Type(ChildApi.class) Object[] child()` is legal as a method annotation but fails
the processor's direct-`Object` rule. Repeated `@ExternalType.Type` annotations are a native javac
non-repeatable-annotation error, not a processor-owned diagnostic.

The processor must diagnose every declaration marker it receives that is not consumed by the
allowed direct-`Object` return/parameter path. In each round, iterate
`roundEnv.getElementsAnnotatedWith(ExternalType.Type.class)`, which is exhaustive for the chosen
`METHOD`/`PARAMETER` targets. Track consumed annotation mirrors/elements while adapting valid
interface methods; after validation, emit one source-positioned diagnostic for each unconsumed
marker: outside an adapted `@ExternalType` interface, on a skipped default/static interface method,
or on a non-`Object` return/parameter declaration. Do not rely only on the outer-`ExternalType`
scan, and do not emit a second catch-all error for a marker already consumed or directly diagnosed.
The supported-annotation declaration ensures javac invokes the processor even when invalid source
contains only `@ExternalType.Type`.

`MethodSpec` (and `AdapterSpec` if useful) must represent both the emitted Java type and lookup
type for each position: `Object` plus a referenced target FQN for a marked position, and the
existing erased source type/class literal otherwise. Generated source must not refer to the
referenced contract interface. This preserves Java 8 source compatibility and prevents the
contract from entering generated public descriptors or runtime linkage.

### Exact runtime lookup and static forms

Within each existing `ClassValue<ResolvedCall>` computation, resolve the owner first under the
current policy, then resolve every marked type before `findVirtual`/`findStatic` with:

```java
Class.forName(targetTypeFqn, false, owner.getClassLoader())
```

Build the `MethodType` from those resolved return/parameter classes and ordinary class literals
for unmarked positions. The `false` initialization flag is required.

Resolving via `owner.getClassLoader()` is essential. An explicit static call can be given a child
loader which delegates the owner to a parent; resolving a same-named target type via the child
would create a different identity than the owner's method descriptor. For a bootstrap owner,
`owner.getClassLoader()` is null and `Class.forName(..., false, null)` resolves in bootstrap. There
is no fallback to TCCL, selected static loader, extension implementation loader, or loader search.

Phase 2 static public forms stay exact:

- `method(targetArguments...)` selects TCCL then system only for null TCCL.
- `method(ClassLoader applicationLoader, targetArguments...)` rejects null before cache access and
  resolves the owner only through that loader.

After either resolves the owner, marked target types use the owner's defining loader. The leading
control loader remains absent from `MethodType` and target invocation arguments.

### Chaining and null semantics

Generated adapters neither wrap a marked value nor cast it to the referenced contract interface.
Marked returns and parameters are emitted as `Object`; `MethodHandle.invoke` performs ordinary
runtime conversion to the resolved target class needed by the exact descriptor.

- An object from loader A chains only into a method whose resolved target class has loader-A
  identity. A same-FQN object from loader B must fail transparently with normal invocation
  `ClassCastException`/`WrongMethodTypeException`, not trigger coercion or another loader search.
- A marked parameter may be null. Its target class is still resolved to form `MethodType`, then
  null is forwarded unchanged and target code controls the outcome.
- A marked return may be null and returns as null. Passing it as a marked parameter is normal.
  Passing it as `self` to a subsequent virtual adapter preserves the current null-receiver failure
  at `self.getClass()`; no sentinel/wrapper or virtual-null semantic change is authorized.

Self references and repeated nested chains are supported because no chain graph or adapter instance
is retained.

## Cache, lifecycle, and concurrency

Phase 3 creates no second cache or global type-name map.

- Virtual `ClassValue<ResolvedCall>` remains keyed by `self.getClass()`. The resolved handle
  naturally retains the owner and exact type identities until that class-value entry is releasable.
- Static dispatch retains the Phase 1/2 monitor, weak-identity loader-to-owner index, and
  owner-keyed `ClassValue`; legacy and explicit forms share it. The index never contains a
  `ResolvedCall` or marked target-type cache.
- Marked type lookup is inside the existing `ClassValue` computation before publication. Owner,
  target-type, or member failure publishes no class value/index entry and the next call retries.
  Static calls remain single-flight under the current member monitor; Phase 3 adds no virtual
  single-flight promise.
- No `Map<String, Class<?>>`, thread-local type cache, FQN-only key, or strong loader key is
  permitted. Isolated loaders with identical FQNs retain separate classes/handles; existing weak
  index expunging and VM `ClassValue` lifecycle continue to govern unloadability.

## Failure, access, and security boundary

The established `ExternalTypeResolutionException` boundary includes a marked target class missing
during `MethodType` construction: it keeps the adapted owner/member message and the original
`ClassNotFoundException` cause. Exact member `NoSuchMethodException` and public-lookup
`IllegalAccessException` retain the same treatment; all are retryable and never negatively cached.

Only those resolution failures are translated. Target-thrown exceptions (including a target-thrown
`ExternalTypeResolutionException`), wrong-loader argument failures, `SecurityException`, linkage
errors, and target-code errors propagate unchanged. Generated code must not set TCCL, initialize a
target class, modify classpaths, use an implementation loader, `privateLookupIn`, `setAccessible`,
`--add-opens`, module reads/exports, or privileged lookup. `MethodHandles.publicLookup()` remains
the public/exported-module boundary; Phase 3 does not make inaccessible types/members available.

## Compatibility and release surface

- Existing contracts without `@ExternalType.Type` retain Phase 1/2 output and behaviour.
- New source opts in only by annotating a direct `Object`; writing `ChildApi` as a parameter or
  return is invalid because it falsely claims a target object implements that contract.
- An opted-in regenerated adapter still exposes `Object` in its descriptor, so existing consumers
  of that generated method remain binary-compatible. Its lookup semantics change only after an
  extension rebuild. Existing already-built adapters remain unchanged until regenerated.
- `ExternalType.Type` is the only new public API. It is source-retained and carries a contract
  class only at source processing; generated public methods/fields/class literals expose neither
  target-library types nor contract interfaces. The generated owner/type FQN strings are normal
  internal resolution metadata.
- The actual published `io.btrace:btrace` masked JAR must expose `ExternalType$Type.class`,
  `ExternalType`, the processor service/implementation, and required processor support to external
  `javac`. Validate that JAR rather than `btrace-core` output. No packaging/plugin/DSL change is
  expected unless this concrete artifact proof detects one.

## Affected components

- `btrace-core`: `ExternalType` nested annotation/Javadoc, `ExternalTypeProcessor`, `MethodSpec`,
  optionally `AdapterSpec`, `AdapterEmitter`, and focused processor/runtime tests. Generated output
  remains Java 8.
- `btrace-dist`: external processor smoke sources and masked-artifact assertions only, unless a
  demonstrated narrow packaging omission requires a build-script correction.
- `btrace-extensions:btrace-ext-test` and `integration-tests`: nested application-only parent/
  child path through staged extension, real client, agent, target JVM, and protocol.
- `docs/BTraceExtensionDevelopmentGuide.md` (normative) and
  `docs/architecture/provided-style-extensions.md` (pointer/manual boundary). Change tutorial or
  getting-started only if targeted search finds contradictory behaviour claims.
- `btrace-gradle-plugin`: validation only; no new DSL/options.

## Acceptance criteria

1. External contracts compile with direct `@ExternalType.Type(OtherContract.class) Object`
   parameter/return positions. Generated source retains an `Object` public boundary but resolves
   the referenced target FQN in its `MethodType`, without `OtherContract` public signatures,
   descriptors, or runtime class literals.
2. Compiler diagnostics reject non-`@ExternalType` references, empty target FQNs, and non-`Object`
   declarations while preserving duplicate-name overload rejection. Generic-argument, array-
   component, local, cast, and `new` type-use attempts fail through javac annotation-target
   applicability; repeated markers fail through javac's native non-repeatable-annotation diagnostic.
3. A virtual public target returning and accepting `Child` resolves/invokes exactly and returns
   the opaque object; it chains into a `Child` virtual adapter without wrapper, cast, or target
   library on the extension compile classpath.
4. Two hostile isolated loaders defining the same owner/child FQNs produce their own chain values.
   A loader-A handle never accepts loader-B child identity; loader `equals`/`hashCode` are unused.
5. Static target methods with marked parameter/return types pass through legacy TCCL and explicit
   loader forms. A selected child loader delegating the owner to a parent proves type resolution
   follows `owner.getClassLoader()`, not the selected child. The control loader is absent from
   target `MethodType` and invocation arguments.
6. A missing marked target class gives cause-preserving `ExternalTypeResolutionException`, leaves
   no successful cache entry, and succeeds after the same mutable loader exposes it. Missing
   member/access and target-thrown failure semantics stay unchanged.
7. Marked null returns/arguments and null virtual chaining follow the documented rules; wrong-loader
   objects fail transparently rather than being coerced.
8. Existing unmarked `Object`, primitive/JDK/generic-erasure, virtual, legacy null-TCCL, and
   explicit null-loader coverage remains green.
9. The staged extension/client/agent/target path runs a nested parent-returning-child call and a
   child adapter call, emitting a target-owned marker while retaining separate Phase 2 legacy and
   explicit static scenarios and release-extension isolation.
10. A clean masked JAR contains `ExternalType$Type.class` plus processor service/implementation.
    Compile parent/child **target** classes into a runtime-only output separately. In a distinct
    `javac --release 8` invocation, compile external parent/child contracts and their driver with only the masked
    JAR as both classpath and processorpath--with neither target sources nor target classes on that
    compilation path. Assert generated source declarations retain `Object` and contain neither an
    `OtherContract.class` reference nor a target-library `.class` literal. Run with extension
    classes, the separately compiled target output, and the masked JAR only; the chain must emit
    its target marker without `btrace-core` output or an extension implementation JAR.
11. Docs state direct target-type support precisely and keep generic elements/arrays, overload
    selectors, fields/constructors, casts/predicates, and non-public/JPMS access out of scope.

## Verification gates

Implementation must redirect Gradle output to `/tmp` before filtering, use
`GRADLE_USER_HOME=$(pwd)/.gradle-user`, build `btrace-dist` before integration, and inspect:

```text
:btrace-core:test
:btrace-core:spotlessCheck
clean :btrace-dist:btraceJar
:btrace-dist:build
:btrace-extensions:btrace-ext-test:spotlessCheck
:integration-tests:spotlessCheck
:integration-tests:test -Pintegration --tests tests.ExternalTypeAdapterIntegrationTest
```

The implementation plan must turn these into redirected Gradle commands with relevant `rg` reads;
inspect the clean masked JAR and processor service; and run this separated external smoke proof:

1. compile target-only `Parent`/`Child` sources with `javac --release 8` to a runtime directory
   without putting them on the contracts compiler classpath;
2. compile only extension contracts plus driver with the masked JAR as both `-cp` and
   `-processorpath` using `javac --release 8`, writing generated source/classes to separate
   directories;
3. assert generated declarations are `Object`-based and that generated source does not contain
   `OtherContract.class` or target-library `.class` literals; and
4. run the driver with extension classes, target runtime classes, and the masked JAR.

The compiler tests must also include a `@ExternalType.Type` marker outside an adapted contract and
one non-`Object` declaration, asserting one useful processor diagnostic each, while a valid direct
marker emits no duplicate diagnostic. Type-use misuse and repeated-marker fixtures must assert the
native javac applicability/non-repeatable diagnostics. Each new positive test needs a
discriminating rejected-input or unfixed-behaviour proof. Use the documented IPv4
`JAVA_TOOL_OPTIONS` retry only for the known restricted-environment address-selection failure.

## Non-goals and completion boundary

Phase 3 excludes target types inside generic containers or arrays, target-type fields,
constructors/casts/predicates, overload selection, target classpath dependencies, wrappers/proxies,
fallback loader search, `MethodHandleCache` redesign, static-policy changes, and private/JPMS
bypasses.

It is complete only when opt-in declaration metadata creates exact owner-loader-resolved `MethodType`s,
opaque values chain safely, loader/null/cache/failure compatibility holds, the published artifact
supports external compilation/execution, and the real integration path proves nested
application-only types. It does not authorize Phase 4 or later work.
