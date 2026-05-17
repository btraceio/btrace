# BTrace Module Consolidation Design

**Date:** 2026-05-03  
**Status:** Approved

## Goal

Reduce BTrace's 18-module Gradle project to 10 modules by merging small or logically coupled modules into their natural parents, removing empty/dead modules, and absorbing source-less packaging modules into `btrace-dist`. Package names are unchanged throughout; this is purely a source-directory and build-wiring restructuring.

## Final Module Map

| After | Absorbs | Source files (approx) |
|---|---|---|
| `btrace-core` | `btrace-core` + `btrace-extension` + `btrace-extension-processor` | 151 |
| `btrace-runtime` | unchanged | 25 |
| `btrace-compiler` | `btrace-compiler` only (runtime dep removed) | 20 |
| `btrace-boot` | unchanged | 3 |
| `btrace-agent` | `btrace-agent` + `btrace-instr` | 71 |
| `btrace-client` | `btrace-client` + `btrace-ext-cli` | 17 |
| `btrace-dist` | `btrace-dist` + `btrace-bootstrap` packaging logic | 51 |
| `btrace-dtrace` | unchanged | 9 |
| `btrace-gradle-plugin` | unchanged | — (Groovy) |
| `btrace-maven-plugin` | unchanged | 1 |

**Removed (no source):** `btrace-api`, `btrace-ui`, `btrace-extensions` (parent aggregator)  
**Unchanged:** `btrace-extensions/*` subprojects, `integration-tests`, `benchmarks/*`

## The One Source Change: `PackGenerator` Move

`btrace-instr` (merging into `btrace-agent`) currently depends on `btrace-compiler` solely for
the `PackGenerator` interface (`io.btrace.compiler.PackGenerator`, 5 lines). After the
merge, `btrace-agent` depending on `btrace-compiler` would be a wrong-direction dependency.

**Fix:** Move `PackGenerator` to `io.btrace.core` in `btrace-core`. Both `btrace-compiler`
and `btrace-agent` already depend on `btrace-core`, so no new dependency edges are introduced.
Update the single import in `InstrPackGenerator.java` and the class declaration in `PackGenerator.java`.

This is the only package-path change in the entire consolidation.

## Build Constraints to Preserve

| Constraint | Location after consolidation |
|---|---|
| `btrace-runtime` java9 / java11 extra source sets with per-version toolchains | `btrace-runtime` build.gradle — unchanged |
| `btrace-boot` compiles at `JavaLanguageVersion.of(8)` | `btrace-boot` build.gradle — unchanged |
| `btrace-instr` `compileJava` targets Java 8, `compileTestJava` forks with javac8 | Move these `compileJava`/`compileTestJava` blocks verbatim into `btrace-agent` build.gradle |
| `btrace-instr` `compileTestProbes` task | Move verbatim into `btrace-agent` build.gradle |
| `btrace-bootstrap` shadow JAR with `bootIncludes` filter and relocations | Becomes a new `shadowJar`-based task inside `btrace-dist` build.gradle, same filter/relocation logic |
| `btrace-extension-processor` on `annotationProcessor` classpath for extension authors | Extension `build.gradle` files change `annotationProcessor project(':btrace-extension-processor')` to `annotationProcessor project(':btrace-core')` |
| `btrace-ext-cli` `lanterna` dependency | Moves to `btrace-client` build.gradle |
| `btrace-ext-cli` compiles at `JavaLanguageVersion.of(11)` | `btrace-client` build.gradle sets toolchain to Java 11 (client already targets 11) |

## Dependency Graph After Consolidation

```
btrace-core  (no project deps)
    ↓
btrace-boot  → btrace-core (implicit; no project deps declared)
btrace-runtime  → btrace-core, btrace-extension (now in btrace-core)
btrace-compiler → btrace-core, btrace-boot
    ↓
btrace-agent    → btrace-core, btrace-extension (core), btrace-runtime, btrace-compiler
btrace-client   → btrace-core, btrace-compiler, btrace-boot, btrace-agent
btrace-dist     → btrace-agent, btrace-client, btrace-compiler, btrace-ext-cli (now in client)
btrace-dtrace   → btrace-core
```

`btrace-runtime` loses its `btrace-extension` dep reference (extension types now live in `btrace-core`).
`btrace-compiler` loses its `btrace-runtime` dep (the `BTraceRuntimeAccess` field reference was the only reason; after `PackGenerator` moves to core this dep is no longer needed — confirm during implementation by removing it and checking compilation).

## Execution Sequence

Each step leaves the build green before the next step begins.

**Step 1 — Remove dead modules**
- Delete `btrace-ui/` directory
- Delete `btrace-api/` directory
- Remove both from `settings.gradle`
- Remove the `apiJar` task from `btrace-dist/build.gradle` entirely — `btrace-api` had zero source so the produced JAR was always empty; remove it from `shadowJar`/`buildTgz`/`buildZip`/`buildDeb`/`buildRpm`/`buildDockerContext` `dependsOn` lists too
- Run: `./gradlew :btrace-dist:build` — must pass

**Step 2 — Dissolve `btrace-extensions` parent aggregator**
- Remove `include 'btrace-extensions'` from `settings.gradle`; keep all child `include` lines
- The `buildExtensionsApi` aggregate task in `btrace-extensions/build.gradle` is replaced by wiring in `btrace-dist` or removed (dist already discovers extension projects dynamically)
- Run: `./gradlew :btrace-dist:build` — must pass

**Step 3 — Merge `btrace-extension` and `btrace-extension-processor` into `btrace-core`**
- Copy `btrace-extension/src/main/java/` tree into `btrace-core/src/main/java/`
- Copy `btrace-extension-processor/src/main/java/` tree into `btrace-core/src/main/java/`
- Copy test sources from both into `btrace-core/src/test/java/`
- Merge their `build.gradle` deps into `btrace-core/build.gradle`
- Change `compileOnly project(':btrace-core')` in old extension to nothing (it's now the same module)
- Update all references to `project(':btrace-extension')` and `project(':btrace-extension-processor')` across all build files → `project(':btrace-core')`
- Delete `btrace-extension/` and `btrace-extension-processor/` directories
- Remove both from `settings.gradle`
- Run: `./gradlew test` — must pass

**Step 4 — Move `PackGenerator` to `btrace-core`**
- Move `btrace-compiler/src/main/java/io/btrace/compiler/PackGenerator.java` to `btrace-core/src/main/java/io/btrace/core/PackGenerator.java`
- Update package declaration to `io.btrace.core`
- Update import in `btrace-instr/src/main/java/io/btrace/instr/InstrPackGenerator.java`
- Update import in any other files referencing `io.btrace.compiler.PackGenerator`
- Remove `implementation project(':btrace-compiler')` from `btrace-instr/build.gradle`
- Run: `./gradlew :btrace-instr:compileJava :btrace-compiler:compileJava` — must pass

**Step 5 — Merge `btrace-instr` into `btrace-agent`**
- Copy `btrace-instr/src/main/java/` tree into `btrace-agent/src/main/java/`
- Copy `btrace-instr/src/test/java/`, `src/test/btrace/`, `src/test/resources/` into `btrace-agent/src/test/`
- Merge `btrace-instr/build.gradle` deps and task definitions into `btrace-agent/build.gradle`:
  - Copy `compileJava` / `compileTestJava` blocks (Java 8 target, javac8 fork)
  - Copy `compileTestProbes` task verbatim
  - Add `asm-tree`, `asm-util`, `autoService`, `jctools` deps
- Update all `project(':btrace-instr')` references → `project(':btrace-agent')`
- Delete `btrace-instr/` directory; remove from `settings.gradle`
- Run: `./gradlew :btrace-agent:test -PupdateTestData` to regenerate golden files if needed, then `./gradlew :btrace-agent:test`

**Step 6 — Merge `btrace-ext-cli` into `btrace-client`**
- Copy `btrace-ext-cli/src/main/java/` into `btrace-client/src/main/java/`
- Copy `btrace-ext-cli/src/test/java/` into `btrace-client/src/test/java/`
- Merge deps into `btrace-client/build.gradle`:
  - Add `lanterna` dependency
  - Set toolchain to `JavaLanguageVersion.of(11)` (already appropriate for client)
  - Add `implementation project(':btrace-agent')` (ext-cli depends on agent)
- Update `project(':btrace-ext-cli')` references → `project(':btrace-client')` in `btrace-dist/build.gradle`
- Delete `btrace-ext-cli/` directory; remove from `settings.gradle`
- Run: `./gradlew :btrace-client:test`

**Step 7 — Absorb `btrace-bootstrap` into `btrace-dist`**
- Copy the `bootIncludes` closure and `shadowJar` task configuration from `btrace-bootstrap/build.gradle` into `btrace-dist/build.gradle` as a new task (e.g., `bootstrapJar`)
- Update `btrace-dist` references from `project(':btrace-bootstrap').tasks.shadowJar` → the new local `bootstrapJar` task
- Add `btrace-bootstrap`'s project dependencies (`btrace-core`, `btrace-runtime`, `btrace-instr`→`btrace-agent`, `btrace-extension`→`btrace-core`) to `btrace-dist`'s dependency block
- Delete `btrace-bootstrap/` directory; remove from `settings.gradle`
- Run: `./gradlew :btrace-dist:build`

**Step 8 — Final wiring cleanup**
- Update `integration-tests/build.gradle`: replace any merged module references
- Update `benchmarks/*/build.gradle`: same
- Verify `btrace-gradle-plugin` and `btrace-maven-plugin` need no changes (they reference final artifact names, not subproject paths)
- Remove compiler's `btrace-runtime` dep if Step 4 made it unnecessary (verify by removing and checking compilation)
- Run: `./gradlew :btrace-dist:build && ./gradlew :integration-tests:test -Pintegration`

**Step 9 — Full validation**
- `./gradlew test` — all unit tests pass
- `./gradlew :btrace-dist:build` — distribution artifacts produced correctly
- `./gradlew :integration-tests:test -Pintegration` — integration tests pass
- Inspect distribution layout (`btrace-dist/build/resources/main/`) — verify JAR contents unchanged

## Testing Strategy

- Golden files in `btrace-instr/src/test/resources/instrumentorTestData/` move with the test sources to `btrace-agent/src/test/resources/`; no regeneration needed unless bytecode output changes
- After Step 5, run `./gradlew :btrace-agent:test` first without `-PupdateTestData`; only regenerate if a test fails due to path-related loading (not content)
- Each step is independently buildable; if a step breaks the build, fix before proceeding

## What Does Not Change

- All Java package names (`io.btrace.*`) — no package renames
- Published artifact names and contents — `btrace-dist` produces the same JARs
- `btrace-dtrace` — unchanged, preserved for future systemtap/USDT porting
- `btrace-runtime` — unchanged, multi-JDK source set structure preserved
- `btrace-boot` — unchanged, Java-8 toolchain constraint preserved
- `btrace-extensions/*` subproject structure — unchanged, only their build files updated to reference `:btrace-core` instead of `:btrace-extension` / `:btrace-extension-processor`
- Extension authoring API — `btrace-core` exposes the annotation processor; extension authors change one line in their `build.gradle`
