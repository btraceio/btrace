# Releasing BTrace

Release process is very simple:
1. Create branch `release/v<version>`
2. On that branch make sure all references to previous version or the current **SNAPSHOT** version are replaced with
   the version being released
3. Commit the changes with message like `Preparing BTrace <version>`
4. Tag the branch with `v<version>` - this will initiate the automated release process in GHA
5. Check the 'pre-release' page and once satisfied with the release notes, make the release to be the latest one
6. Head to [OSSRH](https://oss.sonatype.org/) and finish the maven artifacts release for the current staging repo