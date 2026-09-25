# JDK Compatibility

BTrace is tested against a range of JDK versions and distributions to ensure compatibility
across diverse runtime environments.

## Tested Versions

The CI pipeline runs integration tests against the following JDK versions on every pull request
and push to `develop`:

| JDK Version | How CI installs it | Distribution | Status |
|-------------|--------------------|--------------|--------|
| 8 | `actions/setup-java` with `java-version: 8`, `check-latest: true` → newest Temurin 8 release at run time | Eclipse Temurin | Supported (LTS) — deprecated target, removed in 4.0 |
| 11 | `actions/setup-java` with `java-version: 11`, `check-latest: true` → newest Temurin 11 release at run time | Eclipse Temurin | Supported (LTS) — minimum build JDK; deprecated target, removed in 4.0 |
| 17 | `actions/setup-java` with `java-version: 17`, `check-latest: true` → newest Temurin 17 release at run time | Eclipse Temurin | Supported (LTS) |
| 21 | `actions/setup-java` with `java-version: 21`, `check-latest: true` → newest Temurin 21 release at run time | Eclipse Temurin | Supported (LTS) |
| 25 | `actions/setup-java` with `java-version: 25`, `check-latest: true` → newest Temurin 25 release at run time | Eclipse Temurin | Supported (LTS) |
| 27 | lane spec `27` with `sdkman: true` → newest 27 GA build SDKMAN lists (Temurin, else Oracle JDK, else java.net; `scripts/resolve-sdkman-java.sh`), `27.0.0-oracle` until Temurin 27 is published | GA 2026-09-15 | Supported — the newest GA release |
| 28 (EA) | lane spec `28-ea` → Temurin early-access build via `actions/setup-java` (SDKMAN publishes java.net EA builds late and retires them at GA) | Temurin Early Access | Experimental — tracked for future readiness; exercises the ClassFile API instrumentation backend (class-file major 72) |

No lane pins a build. SDKMan drops superseded builds from its catalogue (`27.ea.31-open` was
retired at 27 GA; its list shows one build per vendor and major), so a pinned identifier fails
`sdk install` once that happens. `actions/setup-java` resolves the newest Temurin release of a
major through the Adoptium API at run time, which removes that failure mode; SDKMan remains in
use only for a major that Adoptium has not published yet (27 at the time of writing). [`.github/workflows/update-jdk-versions.yml`](../.github/workflows/update-jdk-versions.yml)
keeps running every Monday; it rewrites pinned `-tem` and `N.ea.M-open` entries, so it becomes
active again as soon as a lane pins a build.

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
