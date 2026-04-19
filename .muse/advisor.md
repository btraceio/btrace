# BTrace Project Advisor

## Repository: btraceio/btrace

### Branch model

- **`develop`** is the sole integration branch — ALL pull requests must target `develop`, never `master`.
- `master` does not exist on the remote.
- Release tags are cut from `develop` after a stabilization window.

### Build commands

```bash
# Full build with dist packages
./gradlew :btrace-dist:build

# Module-specific build (faster)
./gradlew :<module>:build -x test

# All tests
./gradlew test

# Instrumentation tests only (fastest for instr changes)
./gradlew :btrace-instr:test

# Regenerate golden files after intentional bytecode changes
./gradlew :btrace-instr:test -PupdateTestData

# Integration tests (requires Docker + full dist build first)
./gradlew :btrace-dist:build
./gradlew :integration-tests:test -Pintegration

# Format check / auto-format (Google Java Format via Spotless)
./gradlew spotlessCheck
./gradlew spotlessApply
```

### Module layout

| Module | Purpose |
|---|---|
| `btrace-core` | Annotations, wire protocol, shared types — must stay Java 8 compatible |
| `btrace-compiler` | Script compilation + safety verification (`Verifier.java`) |
| `btrace-instr` | ASM-based bytecode instrumentation; probe factory; `HandlerRepositoryImpl` |
| `btrace-runtime` | Multi-version runtime impls (base/java9/java11/java15); `IndyDispatcher` |
| `btrace-agent` | Java agent entry point; `RemoteClient`, `FileClient` |
| `btrace-client` | CLI client tool |
| `btrace-dist` | Distribution packaging |
| `integration-tests` | End-to-end Docker-based tests |
| `benchmarks` | JMH benchmarks |

### Key architectural constraints

- `btrace-core` and `btrace-runtime` (src/main/java/) must compile at Java 8 source/target level.
- Multi-version runtime jars: `src/main/java9/`, `src/main/java11/`, `src/main/java15/` — each compiled at its respective release level.
- `btrace-boot.jar` is on the bootstrap classpath; classes in `btrace-runtime` that are referenced from INVOKEDYNAMIC bootstrap methods must be bootstrap-loadable.
- All probe script classes are defined in the bootstrap CL (via `Unsafe.defineClass` with `mustBeBootstrap=true` when `isTransforming()`).
- BTrace verifier enforces that probe handler methods are `public static void` — `publicLookup().findStatic()` is therefore sufficient.
- Golden files for instrumentation tests live in `btrace-instr/src/test/resources/instrumentorTestData/dynamic/`. Run with `-PupdateTestData` to regenerate after intentional bytecode changes.

### PR checklist

Before opening a PR, verify:
1. Branch targets **`develop`** (never `master`)
2. `./gradlew spotlessApply` applied
3. `./gradlew :btrace-instr:test` passes
4. Golden files regenerated if instrumented bytecode changed (`-PupdateTestData`)
5. If touching runtime multi-version code: test on JDK 11 and JDK 17+
