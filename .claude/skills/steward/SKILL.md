---
name: steward
description: BTrace conventions for driving a pull request to green — CI triage, review follow-up, dependency-bot (Renovate) PRs, and what to verify before pushing. Use when watching, fixing, or shepherding a PR in this repository.
---

# Stewarding a BTrace pull request

Repository rules in [AGENTS.md](../../../AGENTS.md) apply throughout; this file adds only what is specific to driving a PR.

## Branches and merging

- PRs target `develop`. They are squash-merged, so the PR title becomes the commit subject: use a Conventional Commits title (`fix(agent): ...`, `build: ...`, `chore(deps): ...`).
- On a branch you created, bring `develop` in with a merge commit once the PR is open; do not force-push a branch a reviewer has already looked at.

## Before every push

- Run what the change can affect: `spotlessCheck` and the changed modules' tests, with output redirected to a log as AGENTS.md describes.
- Configuring the root build needs a JDK 8 toolchain (`common.gradle` resolves it eagerly), and `btrace-agent` needs a JDK 24 toolchain to compile its `java24` source set and a JDK 28 launcher for `classFileApiBackendTest`, which `check` depends on. If a required JDK is unavailable locally, Gradle cannot verify the change: say so in the PR's Testing section instead of ticking the box, and treat the PR's CI as the check.
- Golden instrumentation data in `btrace-agent/src/test/resources/instrumentorTestData/` is regenerated only with `./gradlew test -PupdateTestData`, and only for an intentional instrumentation change. Never edit it by hand, and never regenerate it to make an unexplained diff go away.

## CI triage

- **`spotlessCheck` failing on files the PR did not touch** means the formatter changed, not the code. An unpinned `googleJavaFormat()` in `gradle/spotless.gradle` follows the Spotless plugin's default, so a Spotless bump can move it; check whether the formatter pin or the Spotless plugin version moved before running `spotlessApply` across the tree.
- **`Cannot find a Java installation ... matching: {languageVersion=N}`** means a workflow no longer installs a JDK the build needs. The `setup-java` steps in `.github/workflows/` are toolchain requirements (JDK 24 feeds `btrace-agent`'s `java24` source set); restore the step rather than changing the build.
- **JUnit resolving a JDK 17+ artifact for a JVM 8 configuration** means the JUnit 5.14.4 pin was bumped. `btrace-agent` and `integration-tests` compile tests with a JDK 8 javac, and `btrace-gradle-plugin` keeps Java 8-compatible test dependencies; the pin is intentional.
- Maven Central can answer `429 Too Many Requests` during dependency resolution. That is an infrastructure failure: one re-run is justified; a second identical failure needs reporting, not more re-runs.

## Dependency-bot PRs

Renovate opens PRs on `renovate/*` branches; `.github/workflows/dependency-auto-merge.yml` approves them and merges them once `BTrace CI/CD` and `CodeQL` pass.

- Do not push to `renovate/*` branches. Renovate force-rebases them, which discards your commits, and any commit you add would be auto-merged under the bot's approval.
- When a bot PR fails, find the root cause and fix it in a separate PR from `develop`:
  - an intentional pin or toolchain requirement was bumped → add or tighten a `packageRules` entry in `renovate.json` and close the bot PR;
  - the upgrade is wanted but needs code changes → make them in the separate PR, then let Renovate rebase its PR onto the result.
- Validate `renovate.json` with `npx --package renovate@latest renovate-config-validator --strict renovate.json` before pushing a change to it.

## Review follow-up

- Fix small, local asks directly. Anything touching the masked-JAR layout, class-loader boundaries, or the wire protocol is not small: propose the change on the thread and read the architecture docs linked from AGENTS.md first.
- A behavior change that crosses module or process boundaries needs integration-test coverage (see AGENTS.md); a review asking for it is not optional.
