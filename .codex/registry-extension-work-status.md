# Registry Extension Work Status

Date: 2026-05-17

## What is done

- The registry publishing flow no longer requires a BTrace-owned PAT for third-party extension publishers.
- `BTraceExtensionPlugin` now supports two `auto` publishing paths:
  - direct push when `BTRACE_EXTENSIONS_REGISTRY_PUSH_REPO_GIT_URL` is configured
  - fork-based PR creation through the publisher's own `gh` auth when no direct push repo is configured
- The first-party release workflow now uses `BTRACE_EXTENSIONS_PAT` only in the BTrace CI job.
- Docs were updated to say the PAT is CI-only and that third-party publishers use fork-based PRs.
- A regression test was added for the fork-based PR path.

## Important files

- `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceExtensionPlugin.groovy`
- `.github/workflows/release.yml`
- `docs/releasing.md`
- `docs/BTraceExtensionDevelopmentGuide.md`
- `btrace-gradle-plugin/src/test/java/io/btrace/gradle/BTraceExtensionPluginTest.java`

## Current blocker

`javap` on the compiled plugin class shows that `io.btrace.gradle.BTraceExtensionMetadata` does not currently expose `publishToRegistry`, even though the intended source changes were being worked on. The Gradle test failure is therefore a classpath/source mismatch problem, not a registry logic problem.

The visible test failure is:

- `groovy.lang.MissingPropertyException: Could not set unknown property 'publishToRegistry' for extension 'btraceExtension' of type io.btrace.gradle.BTraceExtensionMetadata`

## What to verify next

1. Check the authoritative source file:
   - `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceExtensionPlugin.groovy`
2. Confirm that `BTraceExtensionMetadata` really contains:
   - `boolean publishToRegistry = true`
3. Rebuild the plugin cleanly so the testkit classpath picks up the updated bytecode.
4. Re-run:
   - `./gradlew --no-daemon :btrace-gradle-plugin:compileGroovy :btrace-gradle-plugin:compileTestJava`
   - `./gradlew --no-daemon :btrace-gradle-plugin:test --tests io.btrace.gradle.BTraceExtensionPluginTest`

## Notes

- The compile task passed in the latest run.
- The test harness failure happens during project evaluation, before the registry PR logic executes.
- Do not reintroduce a BTrace-owned PAT requirement for third-party publishers.
