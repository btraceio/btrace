# BTrace 3.0 Release Gate Checklist

Use this checklist for every 3.0 release candidate. The GitHub release workflow runs the same
commands and uploads both full logs and filtered Markdown summaries. A release is a **GO** only when
every required lane is green and every exception row has an owner, rationale, and explicit release
decision.

## Candidate identity

| Field | Value |
|-------|-------|
| Version | |
| Source commit | |
| RC tag | |
| Workflow run | |
| Release owner | |
| Decision (`GO` / `NO-GO`) | |

## Recorded build and test gates

The workflow stores these under `release-gate-logs/`. `scripts/run-release-gate.sh` records the
shell-escaped command, exit code, filtered result lines, and complete command output.

| Gate | Recorded command | Required result |
|------|------------------|-----------------|
| Core, agent, client | `./gradlew --continue :btrace-core:test :btrace-agent:test :btrace-client:test` | All module tests pass |
| Gradle plugins | `cd btrace-gradle-plugin && ../gradlew test` | All extension and fat-agent tests pass |
| Maven fat-agent removal | `test ! -e btrace-maven-plugin/build.gradle` | The unpublished module remains absent |
| Formatting | `./gradlew spotlessCheck` | Pass |
| Clean masked JAR | `./gradlew clean :btrace-dist:btraceJar` | Pass; `libs/btrace.jar` exists |
| Full distribution | `./gradlew :btrace-dist:build` | ZIP, TGZ, SDKMAN, DEB, and RPM tasks pass |
| Integration matrix | `./gradlew -Pintegration :integration-tests:test` | Pass after the distribution build on every target JDK |

The JDK matrix is Java 8, 11, 17, 21, 25, and the configured OpenJDK early-access build. In each
lane, `scripts/verify-jdk-warning.sh` runs the real BTrace warning code twice in one target JVM:

- Java 8 and 11 must emit exactly one Java-version deprecation warning.
- Java 17 and newer, including EA, must emit no Java-version deprecation warning.

## Security and compatibility behavior

The built-distribution smoke matrix must prove all of the following:

- default startup performs no telemetry DNS or network operation;
- prepared mode uses a loopback/ephemeral endpoint rather than a wildcard listener;
- no prepared-mode command is accepted before authentication;
- `btrace doctor` reports preparation state before attach and authenticated readiness afterward;
- `noServer=true` executes the startup probe and leaves the configured command socket closed;
- privileged embedded extensions are denied unless the policy grants them;
- non-privileged embedded extensions execute without privileged consent;
- forced V1, forced V2, and automatic negotiation all produce the expected probe output.

## Launch incident-kit commands

These five operator paths are release-blocking. `scripts/release-smoke.sh` runs them against the
extracted candidate distribution and retains target, client, compiler, migration, and doctor logs.

1. Dynamic first trace:

   ```bash
   btrace -n 'OrderService::processOrder @return { print method, time }' <PID>
   ```

2. Source probe:

   ```bash
   btrace <PID> OrderTiming.java
   ```

3. Readiness diagnosis before and after attach:

   ```bash
   btrace doctor <PID>
   ```

4. Authenticated prepared mode:

   ```bash
   java -javaagent:libs/btrace.jar=port=0 DemoApp.java
   ```

5. Startup-only mode with no command socket:

   ```bash
   java -javaagent:libs/btrace.jar=script=StartupProbe.class,noServer=true,port=<PORT>,stdout=true DemoApp.java
   ```

## Release-candidate artifacts

| Surface | Required evidence |
|---------|-------------------|
| Maven Central | Candidate POM and masked JAR resolve; version and Apache-2.0 metadata match |
| JBang | Exact Maven coordinate and `btrace@btraceio` catalog alias report the candidate version |
| SDKMAN | Archive has `bin/` and `libs/` at its root; no extra version directory |
| Docker | OCI version/license labels match and `/opt/btrace/libs/btrace.jar` is the intended layout |
| GitHub release | Binary ZIP, TGZ, SDKMAN ZIP, DEB, RPM, and masked Maven JAR assets are present |

Public download counts and analytics are never release inputs.

## Exceptions and release decision

Blank owner, rationale, or decision cells are an automatic **NO-GO**.

| Gate or exception | Owner | Rationale | Release decision |
|-------------------|-------|-----------|------------------|
| Maven fat-agent module | Release engineering | It was never publicly released and used obsolete artifact/classdata contracts | Removed from 3.0; reappearance is a release failure |

Do not add an exception for a silent no-op, authentication bypass, telemetry opt-out failure,
permission bypass, missing artifact, or red required JDK lane. Those conditions are always
**NO-GO**.
