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

### What Happens During Release

The release workflow performs these steps:

1. **Validation**: Verifies inputs, checks tag doesn't exist
2. **Build & Test**: Runs full build and unit tests
3. **Integration Tests**: Tests on JDK 8, 11, 17, 21
4. **Prepare Release**: Creates/updates release branch, updates version, creates tag
5. **Stage to Maven Central**: Uploads artifacts to staging (requires manual release)
6. **⏸️ MANUAL CHECKPOINT**: You must release artifacts via Central Portal
7. **Wait for Maven Central**: Polls until artifacts are available (30 min timeout)
8. **Build Distributions**: Creates tar.gz, zip, deb, rpm packages
9. **GitHub Release**: Creates release with artifacts and changelog
10. **SDKMan Update**: Announces new version to SDKMan
11. **JBang**: Automatic - uses Maven Central artifacts
12. **Version Bumps**: Updates develop and release branch to next snapshots
13. **Milestones**: Creates/closes milestone, associates merged PRs

### Manual Release Step

After step 5, the workflow pauses and waits for you to manually release the Maven artifacts:

1. Go to [Central Portal Deployments](https://central.sonatype.com/publishing/deployments)
2. Find the staged repository for BTrace
3. **Review** the artifacts to ensure everything looks correct
4. Click **Publish** to release to Maven Central
5. The workflow will detect the release and continue automatically

If you don't want to proceed:
- Simply let the workflow timeout (30 minutes), or
- Cancel the workflow run
- Drop the staging repository via Central Portal

This checkpoint allows you to verify the release before it becomes irreversible.

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

Artifacts are staged to Maven Central via the [Central Portal](https://central.sonatype.com/).
The workflow does **not** auto-release - you must manually publish from the Central Portal after reviewing the staged artifacts.

### Staged Release Process

1. Workflow uploads signed artifacts to a staging repository
2. You review artifacts at [Central Portal Deployments](https://central.sonatype.com/publishing/deployments)
3. Click "Publish" to release, or "Drop" to discard
4. Once published, artifacts sync to Maven Central within ~10 minutes

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

After the GitHub release is created, the workflow announces the new version to SDKMan.
BTrace will be available via:

```bash
sdk install btrace
```

For major releases, `sdkMajorRelease` is used; for minor/patch, `sdkMinorRelease` is used.

## JBang

JBang automatically picks up new versions from Maven Central. No manual action required.
Once Maven Central has the artifacts, users can run:

```bash
jbang btrace <PID> script.java
```

## Rollback Procedure

### Before Maven Central release (reversible)

If the workflow is waiting for Maven Central and you want to abort:

1. **Cancel the workflow** in GitHub Actions
2. **Drop the staging repository** via [Central Portal](https://central.sonatype.com/publishing/deployments)
3. **Delete the tag** (if created):
   ```bash
   git tag -d vX.Y.Z
   git push origin :refs/tags/vX.Y.Z
   ```
4. **Reset release branch** if needed:
   ```bash
   git checkout release/X.Y
   git reset --hard <previous-commit>
   git push --force origin release/X.Y
   ```

### After Maven Central release (irreversible)

Once artifacts are released to Maven Central, they cannot be deleted. You can only:
- Release a new patch version with fixes
- Document the issue in release notes

Similarly, SDKMan announcements cannot be retracted.

## Troubleshooting

### Workflow fails during tests
- Check test reports in workflow artifacts
- Fix issues and re-run the release script

### Wait for Maven Central times out
- The workflow waits 30 minutes for you to release via Central Portal
- If you missed it, manually complete the release:
  1. Release artifacts via [Central Portal](https://central.sonatype.com/publishing/deployments)
  2. Create GitHub release manually with artifacts from the workflow
  3. Run SDKMan update: `./gradlew :btrace-dist:sdkMinorRelease`

### Maven Central staging fails
- Verify credentials are valid (regenerate tokens if needed)
- Check signing key hasn't expired
- Review Sonatype status: https://status.sonatype.com/

### SDKMan update fails
- Verify SDKMan API credentials
- SDKMan updates can be retried manually via Gradle:
  ```bash
  # For minor/patch releases
  ./gradlew :btrace-dist:sdkMinorRelease

  # For major releases
  ./gradlew :btrace-dist:sdkMajorRelease
  ```
