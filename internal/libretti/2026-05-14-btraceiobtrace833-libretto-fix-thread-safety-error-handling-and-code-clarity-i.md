---
spec_id: REQ-btraceio-btrace-833
source: github
source_ref: "btraceio/btrace#833"
title: "[Libretto] Fix thread safety, error handling, and code clarity in agent bootstrap "
status: draft
clarity_score: null
created: 2026-05-14
implementing_session: null
implemented_pr: null
---

# [Libretto] Fix thread safety, error handling, and code clarity in agent bootstrap 

<!-- muse:libretto spec_id=REQ-btraceio-btrace-807 source=github source_ref=btraceio/btrace#807 title="Fix thread safety, error handling, and code clarity in agent bootstrap " status=implementing clarity_score=null created=2026-05-03 implementing_session=null implemented_pr=null -->

## Summary

Addresses code review findings in the masked agent JAR bootstrap code.

- **Fix class load ordering:** Append agent JAR to bootstrap classpath *before* loading `Main`, so `Main`'s static initializers can find bootstrap classes
- **Improve error handling:** Surface clear error messages on init failure; throw `RuntimeException` in `premain` so the JVM aborts instead of silently continuing without BTrace
- **Fix dual-parent classloader:** Pass parent to `super()` instead of keeping a separate `parent` field, eliminating confusing dual delegation paths
- **Simplify `AgentClassLoader`:** Override `loadClass()` instead of `findClass()` for explicit delegation model; use `readAllBytes()` instead of manual 4KB+65KB buffer management
- **Fix build tasks:** Use `DefaultTask` for rename task, add `inputs`/`outputs` for Gradle cacheability, replace deprecated `buildDir` with `layout.buildDirectory`
- **Fix shadowJar filter:** Replace dead `org/openjdk/btrace/services/` reference with `org/openjdk/btrace/extension/`
- **Simplify `processClasspaths`:** Bootstrap path is now handled by `Agent`; remove redundant `Loader.class` detection logic; move debug log inside null-check

## Test plan

- [ ] Build the agent JAR: `./gradlew :btrace-agent:agentJar`
- [ ] Verify JAR structure: only `Agent.class` and `AgentClassLoader*.class` remain as `.class` under `org/openjdk/btrace/agent/`
- [ ] Run test suite: `./gradlew test`
- [ ] Test agent attach with a sample BTrace script against a target JVM


<!-- Reviewable:start -->
This change is [<img src="https://reviewable.io/review_button.svg" height="34" align="absmiddle" alt="Reviewable"/>](https://reviewable.io/reviews/btraceio/btrace/807)
<!-- Reviewable:end -->
