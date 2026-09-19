# JDK Compatibility

BTrace is tested against a range of JDK versions and distributions to ensure compatibility
across diverse runtime environments.

## Tested Versions

The CI pipeline runs integration tests against the following JDK versions on every pull request
and push to `develop`:

| JDK Version | How CI installs it | Distribution | Status |
|-------------|--------------------|--------------|--------|
| 8 | lane spec `8` → newest 8 GA build at run time (Temurin preferred; `scripts/resolve-sdkman-java.sh`) | Eclipse Temurin | Supported (LTS) — deprecated target, removed in 4.0 |
| 11 | lane spec `11` → newest 11 GA build at run time (Temurin preferred; `scripts/resolve-sdkman-java.sh`) | Eclipse Temurin | Supported (LTS) — minimum build JDK; deprecated target, removed in 4.0 |
| 17 | lane spec `17` → newest 17 GA build at run time (Temurin preferred; `scripts/resolve-sdkman-java.sh`) | Eclipse Temurin | Supported (LTS) |
| 21 | lane spec `21` → newest 21 GA build at run time (Temurin preferred; `scripts/resolve-sdkman-java.sh`) | Eclipse Temurin | Supported (LTS) |
| 25 | lane spec `25` → newest 25 GA build at run time (Temurin preferred; `scripts/resolve-sdkman-java.sh`) | Eclipse Temurin | Supported (LTS) |
| 27 | lane spec `27` → newest 27 GA build at run time (Temurin, else java.net, else Oracle; `scripts/resolve-sdkman-java.sh`) | GA 2026-09-15 | Supported — the newest GA release |
| 28 (EA) | lane spec `28-ea` → Temurin early-access build via `actions/setup-java` (SDKMAN publishes java.net EA builds late and retires them at GA) | Temurin Early Access | Experimental — tracked for future readiness; exercises the ClassFile API instrumentation backend (class-file major 72) |

No lane pins a build. SDKMan drops superseded builds (`sdk list java` shows only `25.0.4-tem`
for 25 once it ships, and `27.ea.31-open` was retired at 27 GA), so a pinned identifier fails
`sdk install` until someone bumps it; resolving the newest GA build of each major at run time
removes that failure mode. [`.github/workflows/update-jdk-versions.yml`](../.github/workflows/update-jdk-versions.yml)
still runs every Monday but only rewrites pinned `-tem` and `N.ea.M-open` entries, of which there
are none left.

## Distribution Support Policy

CI validates BTrace against **Eclipse Temurin** as the primary distribution. Other distributions
(Amazon Corretto, Azul Zulu, GraalVM CE, Eclipse OpenJ9, Oracle JDK, etc.) are expected to work
when they conform to the Java SE specification, but they are not formally tested in the CI pipeline.

If you discover a distribution-specific issue, please [open a GitHub issue](#reporting-compatibility-issues).

## Minimum Java Version

BTrace compiles with `sourceCompatibility = 8` and `targetCompatibility = 8`, targeting the widest
possible deployment base. The build toolchain auto-provisions a JDK 11 compiler via Gradle
toolchains; the CI pipeline runs with JDK 24. A local build requires JDK 11 or later available
to Gradle (installed locally or auto-provisioned).

## Reporting Compatibility Issues

| Situation | Where to report |
|-----------|-----------------|
| A specific JDK version or distribution breaks BTrace | [Open a GitHub issue](https://github.com/btraceio/btrace/issues) with the `compatibility` label |
| General question about JDK version support | [GitHub Discussions](https://github.com/btraceio/btrace/discussions) |
| You have a fix | Open a pull request — see [Contributing](../CONTRIBUTING.md) |

Please include:
- JDK version and distribution (e.g., `GraalVM CE 21.0.3`)
- BTrace version
- Minimal reproduction: the BTrace script + target app + command used
- Full error output

## External Compatibility Validation

Community members are welcome to validate BTrace against additional JDK distributions and versions
not covered by the CI matrix. If you find an issue:

1. Search [existing issues](https://github.com/btraceio/btrace/issues) before opening a new one.
2. Open an issue with the `compatibility` label and the details listed above.
3. If you can provide a targeted fix, a pull request is strongly preferred over a long-running fork.

Long-lived compatibility forks are discouraged: they diverge from upstream quickly and often become
impossible to reconcile. Instead, contribute focused patches upstream so all users benefit.
