# JDK Compatibility

BTrace is tested against a range of JDK versions and distributions to ensure compatibility
across diverse runtime environments.

## Tested Versions

The CI pipeline runs integration tests against the following JDK versions on every pull request
and push to `develop`:

| JDK Version | SDKMan Identifier | Distribution | Status |
|-------------|-------------------|--------------|--------|
| 8 | `8.0.492-tem` | Eclipse Temurin | Supported |
| 11 | `11.0.31-tem` | Eclipse Temurin | Supported — minimum build JDK |
| 17 | `17.0.19-tem` | Eclipse Temurin | Supported |
| 21 | `21.0.11-tem` | Eclipse Temurin | Supported |
| 25 | `25.0.3-tem` | Eclipse Temurin | Supported |
| 27 (EA) | `27.ea.20-open` | OpenJDK Early Access | Experimental — tracked for future readiness |

Version identifiers are updated automatically each Monday via
[`.github/workflows/update-jdk-versions.yml`](./../.github/workflows/update-jdk-versions.yml)
using the SDKMan API.

## Distribution Support Policy

CI validates BTrace against **Eclipse Temurin** as the primary distribution. Other distributions
(Amazon Corretto, Azul Zulu, GraalVM CE, Eclipse OpenJ9, Oracle JDK, etc.) are expected to work
when they conform to the Java SE specification, but they are not formally tested in the CI pipeline.

If you discover a distribution-specific issue, please [open a GitHub issue](#reporting-compatibility-issues).

## Minimum Java Version

BTrace compiles with `sourceCompatibility = 8` and `targetCompatibility = 8`, targeting the widest
possible deployment base. The build toolchain requires JDK 11 or later.

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
