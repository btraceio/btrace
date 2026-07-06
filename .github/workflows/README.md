# BTrace GitHub Actions Workflows

## Overview

This directory contains GitHub Actions workflows for continuous integration and testing of the BTrace project, with special focus on the new Binary Protocol v2 implementation.

## Workflows

### 1. `continuous.yml` - Main CI/CD Pipeline

**Purpose:** Main continuous integration pipeline for all BTrace components.

**Triggers:**
- Push to `develop` branch
- Pull requests to `develop`
- Manual workflow dispatch

**Jobs:**
- **build:** Compiles the project and runs all tests
  - Java 11 with Temurin distribution
  - Parallel build with caching
  - **V2 Protocol Tests:** Runs dedicated v2 protocol test suite
  - Uploads dist build artifacts
- **test:** Runs integration tests on multiple Java versions
  - Matrix: Java 8, 11, 17, 21, 25 (EA)
  - Uses SDKMAN for multiple JDK management
  - Downloads build artifacts from previous job
  - Runs integration tests with `-Pintegration` flag
- **publish:** Publishes artifacts to Maven Central
  - Only on `develop` branch
  - Requires GPG signing credentials
- **cleanup:** Removes temporary artifacts

**Enhancements for V2 Protocol:**
- Added explicit V2 protocol test execution in build job
- Tests all v2 packages: `v2.*`, `Protocol*`, `WireProtocol*`

### 2. `v2-protocol-tests.yml` - V2 Protocol Test Suite

**Purpose:** Comprehensive testing suite specifically for Binary Protocol v2.

**Triggers:**
- Push to `develop`, `master`, or `jb/comm_v2` branches
- Pull requests to `develop`
- Changes to protocol-related files
- Manual workflow dispatch
- Commit messages containing `[benchmark]`

**Jobs:**

#### **unit-tests**
- Runs all v2 protocol unit tests on Java 11, 17, 21
- Tests binary serialization/deserialization
- Validates all 17 command types
- Upload test reports as artifacts

#### **protocol-negotiation-tests**
- Tests protocol version detection
- Validates V1/V2 negotiation
- Tests configuration management
- Verifies magic byte detection

#### **edge-case-tests**
- Runs 35 edge case scenarios
- Tests boundary conditions
- Validates large message handling
- Tests compression functionality
- Unicode and special character handling

#### **jmh-benchmarks** (Manual/Opt-in)
- Runs JMH performance benchmarks
- Triggered by workflow dispatch or `[benchmark]` in commit message
- Quick benchmarks: warmup=1, iterations=2, fork=1
- Focuses on serialization performance
- Uploads JMH results for 30 days

#### **protocol-compatibility**
- Tests all 4 compatibility scenarios:
  - V2 client ↔ V2 agent (optimal)
  - V1 client ↔ V1 agent (legacy)
  - V2 client ↔ V1 agent (fallback)
  - V1 client ↔ V2 agent (detection)
- Matrix strategy for comprehensive coverage
- Validates backward compatibility

#### **test-summary**
- Aggregates test results from all jobs
- Generates GitHub Step Summary
- Reports total/passed/failed counts
- Fails if any tests failed

#### **code-coverage**
- Generates JaCoCo coverage reports
- Focuses on `io.btrace.core.comm` package
- Uploads coverage artifacts for 30 days
- Creates coverage summary in step output

### 3. `codeql-analysis.yml` - Security Analysis

**Purpose:** CodeQL security scanning for vulnerability detection.

**Triggers:** Push/PR to default branch

### 4. `dependency-auto-merge.yml` - Dependency Bot Auto-Merge

**Purpose:** Automatically approves and merges clean dependency update PRs from Dependabot and Renovate.

**Triggers:**
- Pull request target events for dependency bot PR approval
- Successful `BTrace CI/CD` or `CodeQL` workflow completion for merge checks

**Behavior:**
- Only acts on PRs authored by `dependabot[bot]` or `renovate[bot]`
- Does not check out PR code
- Requires both `BTrace CI/CD` and `CodeQL` to pass for the exact PR head commit
- Verifies all latest commit checks/statuses, excluding this automation itself, are successful, skipped, or neutral before merging

### 5. `stale.yml` - Issue Management

**Purpose:** Automatically marks stale issues and PRs.

**Schedule:** Daily at midnight

### 6. `release.yml` - Release Management

**Purpose:** Handles the complete release process with a manual checkpoint for Maven Central.

**Trigger:** Manual via `scripts/release.sh` or workflow_dispatch

**Key Features:**
- Stages artifacts to Maven Central (does NOT auto-release)
- Waits up to 30 minutes for manual release via Central Portal
- Creates GitHub release only after Maven artifacts are available
- Updates SDKMan and manages milestones

**Manual Checkpoint:** After staging, you must release via [Central Portal](https://central.sonatype.com/publishing/deployments). This allows reviewing artifacts before they become permanent.

## V2 Protocol Test Coverage

The workflows ensure comprehensive testing of the v2 protocol implementation:

### Unit Tests (113 total)
- ✅ Binary protocol serialization (26 tests)
- ✅ Edge cases and boundaries (35 tests)
- ✅ Performance comparison (2 tests)
- ✅ Protocol negotiation (16 tests)
- ✅ Configuration management (18 tests)
- ✅ WireProtocol abstraction (16 tests)

### Test Categories
1. **Command Serialization**
   - All 17 BTrace command types
   - Round-trip serialization/deserialization
   - Compression testing

2. **Protocol Negotiation**
   - V1/V2 auto-detection
   - Magic byte validation
   - Configuration-based selection
   - Stream handling (pushback & mark/reset)

3. **Edge Cases**
   - Null/empty values
   - Large messages (10MB)
   - Unicode and emojis
   - Malformed data
   - Numeric boundaries

4. **Performance**
   - JMH benchmarks (180 configurations)
   - V1 vs V2 comparison
   - Compression effectiveness

5. **Compatibility**
   - V1 ↔ V1 (legacy)
   - V2 ↔ V2 (optimal)
   - V1 ↔ V2 (cross-version)
   - V2 ↔ V1 (fallback)

## Artifacts

### Retained Artifacts (7 days)
- Test reports (per Java version)
- Negotiation test results
- Edge case test results
- Compatibility test matrices

### Long-term Artifacts (30 days)
- JMH benchmark results
- Code coverage reports

### Build Artifacts (1 day)
- Distribution builds
- Test trace data

## Configuration

### Environment Variables

**Build Job:**
- Standard Gradle environment
- Parallel execution enabled
- Build cache enabled

**Test Job:**
- `TEST_JAVA_HOME`: Set per matrix Java version
- SDKMAN for multiple JDK management

**Publish Job:**
- `GPG_SIGNING_KEY`: GPG key for artifact signing
- `GPG_SIGNING_PWD`: GPG key password
- `BTRACE_SONATYPE_USER`: Sonatype credentials
- `BTRACE_SONATYPE_PWD`: Sonatype credentials

### Gradle Properties for V2 Testing

```bash
# Run only v2 tests
./gradlew :btrace-core:test --tests "io.btrace.core.comm.v2.*"

# Run protocol negotiation tests
./gradlew :btrace-core:test --tests "*Protocol*"

# Run specific JMH benchmarks
./gradlew :btrace-core:jmh -PjmhInclude=".*MessageCommand.*"

# Generate coverage report
./gradlew :btrace-core:test jacocoTestReport
```

## JMH Benchmark Workflow

### Trigger Benchmark Run

**Option 1: Workflow Dispatch**
```bash
# Via GitHub UI: Actions → V2 Protocol Tests → Run workflow
```

**Option 2: Commit Message**
```bash
git commit -m "Optimize binary protocol [benchmark]"
```

### Benchmark Configuration

**Quick Benchmarks (CI):**
- Warmup: 1 iteration
- Measurement: 2 iterations
- Forks: 1
- Focus: Serialization methods only

**Full Benchmarks (Local):**
- Warmup: 3 iterations
- Measurement: 5 iterations
- Forks: 2
- Coverage: All 180 configurations

## Test Failure Handling

### Automatic Retry
- Tests use `--rerun-tasks` to ensure fresh execution
- No test result caching to catch flaky tests

### Artifact Upload
- All test reports uploaded on failure (`if: always()`)
- Artifacts retained for 7 days for analysis

### Summary Generation
- Test summary job aggregates all results
- Reports failures clearly in GitHub UI
- Step summary provides quick overview

## Code Coverage

### JaCoCo Configuration

**Focus Area:**
- Package: `io.btrace.core.comm.**`
- Includes v2 protocol, negotiation, and abstraction

**Reports Generated:**
- XML (for CI tools)
- HTML (for human review)
- Available in artifacts for 30 days

**Coverage Goals:**
- Unit test coverage: >90%
- Edge case coverage: >80%
- Integration coverage: >70%

## Performance Monitoring

### JMH Results
- Benchmark results uploaded as artifacts
- Compare across runs to detect regressions
- Focus on serialization/deserialization speed
- Monitor wire size changes

### Expected Metrics
- Serialization: 3-6x faster than V1
- Wire size: 2-5x smaller than V1
- Compression: 10-100x size reduction (large messages)

## Maintenance

### Cache Management
- Gradle cache keyed by build files hash
- Automatic cache eviction after 7 days
- Cache size monitored in test job

### Artifact Cleanup
- Temporary artifacts cleaned after publish
- Test reports retained for 7 days
- Performance results retained for 30 days

## Future Enhancements

### Planned Additions
1. **Integration Tests:**
   - Full client-agent communication tests
   - Mixed protocol version scenarios
   - Reconnection testing

2. **Stress Tests:**
   - High concurrency scenarios
   - Large message throughput
   - Memory leak detection

3. **Performance Regression Detection:**
   - Automated benchmark comparison
   - Alert on >10% performance degradation
   - Historical trend analysis

4. **Security Scanning:**
   - Dependency vulnerability checks
   - OWASP security analysis
   - Protocol fuzzing tests

## References

- [BTrace v2 Protocol Architecture](../../docs/architecture/Version2ProtocolArchitecture.md)
- [Phase 3 Integration Guide](../../docs/architecture/phase3-integration-guide.md)
- [V2 Implementation Summary](../../docs/architecture/v2-implementation-summary.md)
- [JMH Benchmarks Guide](../../btrace-core/JMH_BENCHMARKS.md)

## Support

For workflow issues or questions:
1. Check GitHub Actions logs
2. Review artifact contents
3. Check Gradle build logs
4. Open issue with workflow run link
