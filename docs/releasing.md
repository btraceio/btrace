# Releasing BTrace

## Automated Release Process

BTrace uses an automated release process via GitHub Actions. The release is triggered using the `scripts/release.sh` script.

### Prerequisites

1. **GitHub CLI**: Install and authenticate with `gh auth login`
2. **Clean working directory**: Commit or stash any local changes
3. **Correct branch**: Be on `develop` for major/minor releases, or `release/X.Y` for patch releases

### Release Types

| Type | When to Use | Example |
|------|-------------|---------|
| **major** | Breaking changes, major new features | 2.3.0-SNAPSHOT → 3.0.0 |
| **minor** | New features, non-breaking changes | 2.3.0-SNAPSHOT → 2.3.0 |
| **patch** | Bug fixes on release branch | 2.3.1-SNAPSHOT → 2.3.1 |

### Running a Release

```bash
# Minor release from develop (most common)
./scripts/release.sh minor

# Major release from develop
./scripts/release.sh major

# Patch release from a release branch
./scripts/release.sh patch release/2.3

# Dry run (see what would happen without triggering)
DRY_RUN=true ./scripts/release.sh minor
```

### What Happens Automatically

The release workflow performs these steps:

1. **Validation**: Verifies inputs, checks tag doesn't exist
2. **Build & Test**: Runs full build and unit tests
3. **Integration Tests**: Tests on JDK 8, 11, 17, 21
4. **Prepare Release**: Creates/updates release branch, updates version, creates tag
5. **Publish to Maven Central**: Publishes artifacts via Central Portal
6. **Build Distributions**: Creates tar.gz, zip, deb, rpm packages
7. **GitHub Release**: Creates release with artifacts and changelog
8. **SDKMan Update**: Announces new version to SDKMan
9. **Version Bumps**: Updates develop and release branch to next snapshots
10. **Milestones**: Creates/closes milestone, associates merged PRs

### Branch Strategy

BTrace uses trunk-based development with `develop` as the main branch:

- **develop**: Main trunk - all development and releases start here
- **release/X.Y**: Long-lived branches for patch releases (created automatically during major/minor releases)
- **Tags**: `vX.Y.Z` format (e.g., `v2.3.0`) - the latest release is always identifiable via tags

### Manual Verification (Optional)

For critical releases, you can trigger a dry run first:

```bash
DRY_RUN=true ./scripts/release.sh minor
```

Or use the GitHub Actions UI to trigger with `dry_run: true`.

## Maven Central

Artifacts are published to Maven Central via the [Central Portal](https://central.sonatype.com/).

### Maven Coordinates

```xml
<dependency>
    <groupId>io.btrace</groupId>
    <artifactId>btrace-client</artifactId>
    <version>VERSION</version>
</dependency>
```

Available artifacts:
- `io.btrace:btrace-agent`
- `io.btrace:btrace-client`
- `io.btrace:btrace-boot`

### Credentials

The workflow uses these GitHub secrets:
- `SONATYPE_USERNAME`: Central Portal user token username
- `SONATYPE_PASSWORD`: Central Portal user token password
- `GPG_SIGNING_KEY`: GPG private key for artifact signing
- `GPG_SIGNING_PWD`: GPG key passphrase

Generate Central Portal tokens at: https://central.sonatype.com/account

## SDKMan

After release, BTrace is available via SDKMan:

```bash
sdk install btrace
```

## Rollback Procedure

If a release fails after tagging but before full completion:

```bash
# Delete tag locally and remotely
git tag -d vX.Y.Z
git push origin :refs/tags/vX.Y.Z

# Reset release branch if needed
git checkout release/X.Y
git reset --hard <previous-commit>
git push --force origin release/X.Y
```

If artifacts were published to staging but not released, drop the staging repository via the [Central Portal UI](https://central.sonatype.com/publishing/deployments).

## Troubleshooting

### Workflow fails during tests
- Check test reports in workflow artifacts
- Fix issues and re-run the release script

### Maven Central publishing fails
- Verify credentials are valid (regenerate tokens if needed)
- Check signing key hasn't expired
- Review Sonatype status: https://status.sonatype.com/

### SDKMan update fails
- Verify SDKMan API credentials
- SDKMan updates can be retried manually via Gradle:
  ```bash
  ./gradlew :btrace-dist:sdkMinorRelease
  ```
