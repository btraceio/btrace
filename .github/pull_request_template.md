## What does this change do?

<!-- Summarize the user-visible or maintenance change in a few sentences. -->

## Related issue

<!-- Link an issue, discussion, design, or release-plan item. Use "N/A" if none applies. -->

Closes #

## Scope and compatibility

- [ ] I identified the affected module(s) and kept unrelated changes out of this PR.
- [ ] This preserves the supported Java/runtime compatibility tiers, or the change is documented below.
- [ ] This does not change the masked-JAR layout, class-loader boundary, or wire protocol.
- [ ] If it does, I updated the relevant architecture documentation and verification plan.

**Compatibility or migration notes:**

<!-- Mention API, CLI, probe, extension, protocol, packaging, or documentation changes. -->

## Testing

<!-- List the commands you ran and the relevant result. Follow the repository Gradle guidance. -->

- [ ] Unit tests
- [ ] Integration tests (if applicable)
- [ ] `spotlessCheck`
- [ ] Documentation/link or sample verification (if applicable)

**Commands and results:**

```text
# Example:
# GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :module:test
```

## Documentation and release impact

- [ ] User-facing documentation is updated, or no documentation change is needed.
- [ ] Release notes/changelog are updated, or no release-note entry is needed.
- [ ] Samples, distribution contents, or published coordinates are updated if affected.
- [ ] This change is safe to merge independently of a release, or the dependency is explained below.

**Release notes / follow-up work:**

<!-- Call out deprecations, breaking changes, follow-up PRs, or release coordination. -->

## Final checklist

- [ ] I reviewed the complete diff and removed unrelated changes.
- [ ] New or changed behavior has appropriate tests, or the reason for not adding them is explained above.
- [ ] User-facing behavior, APIs, samples, and documentation are consistent with this change.
- [ ] I did not include generated build output, local configuration, credentials, or other accidental files.
