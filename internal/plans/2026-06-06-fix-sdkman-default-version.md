# Fix SDKman Default Version Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix SDKman so that `sdk install btrace` installs the latest release (v2.2.6) instead of the stale default (v2.2.0), and prevent the problem from recurring in future releases.

**Architecture:** Two-part fix: (1) add a manual `sdkman-set-default.yml` workflow to immediately hotfix the current wrong default without a full release, and (2) patch `release.yml` so every release type (major, minor, patch) calls `sdkDefaultVersion` to update the SDKman default.

**Tech Stack:** GitHub Actions workflow YAML, Gradle SDKman vendors plugin v3.0.0 (`io.sdkman:gradle-sdkvendor-plugin`), SDKman Vendor API.

---

## Root Cause

The `update-sdkman` job in `.github/workflows/release.yml` branches on `release_type`:
- `major` → `./gradlew :btrace-dist:sdkMajorRelease` (releases + **sets default** + rich announcement)
- `minor` / `patch` → `./gradlew :btrace-dist:sdkMinorRelease` (releases + quiet announcement, **no default update**)

v2.2.0 (April 2021) was the last release with type `major`. All subsequent v2.2.x releases used `sdkMinorRelease` and never updated the SDKman default, so `sdk install btrace` has been pinned to 2.2.0 for over three years.

Available Gradle tasks from plugin v3.0.0:
- `sdkReleaseVersion` – registers the version binary URL
- `sdkDefaultVersion` – calls `PUT /default` to set the installed-by-default version  
- `sdkAnnounceVersion` – posts an announcement
- `sdkMajorRelease` = `sdkReleaseVersion` + `sdkDefaultVersion` + struct announcement
- `sdkMinorRelease` = `sdkReleaseVersion` + minor announcement (NO `sdkDefaultVersion`)

---

## File Map

| Action | File |
|--------|------|
| Create | `.github/workflows/sdkman-set-default.yml` |
| Modify | `.github/workflows/release.yml` (Job 8: Update SDKMan, lines 730–771) |

---

## Task 1: Add `sdkman-set-default.yml` hotfix workflow

This workflow lets the maintainer manually set the SDKman default version to any released version without going through a full release pipeline. Used immediately to fix v2.2.6 and available for future emergencies.

**Files:**
- Create: `.github/workflows/sdkman-set-default.yml`

- [ ] **Step 1: Create the workflow file**

```yaml
# .github/workflows/sdkman-set-default.yml
name: Set SDKMan Default Version

on:
  workflow_dispatch:
    inputs:
      version:
        description: 'Version to set as SDKMan default (e.g., 2.2.6)'
        required: true
        type: string

defaults:
  run:
    shell: bash

jobs:
  set-default:
    name: Set SDKMan Default to ${{ inputs.version }}
    runs-on: ubuntu-latest
    steps:
      - name: Checkout tag
        uses: actions/checkout@v6
        with:
          ref: v${{ inputs.version }}

      - name: Cache Java binaries
        id: cache-java
        uses: actions/cache@v5
        with:
          path: ${{ runner.tool_cache }}/Java_*
          key: java-${{ runner.os }}-temurin-11

      - name: Set up Java
        if: steps.cache-java.outputs.cache-hit != 'true'
        uses: actions/setup-java@v5
        with:
          java-version: 11
          distribution: temurin

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Set SDKMan default version
        env:
          SDKMAN_API_KEY: ${{ secrets.SDKMAN_KEY }}
          SDKMAN_API_TOKEN: ${{ secrets.SDKMAN_TOKEN }}
        run: |
          echo "Setting SDKMan default for btrace to ${{ inputs.version }}..."
          ./gradlew :btrace-dist:sdkDefaultVersion --no-daemon
          echo "Done. 'sdk install btrace' will now install ${{ inputs.version }}"
```

- [ ] **Step 2: Verify the workflow YAML is valid**

```bash
# From repo root
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/sdkman-set-default.yml'))" && echo "YAML valid"
```
Expected output: `YAML valid`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/sdkman-set-default.yml
git commit -m "ci: add workflow to manually set SDKMan default version"
```

---

## Task 2: Fix the release workflow to always set SDKMan default

Patch the `update-sdkman` job in `release.yml` so every new release — regardless of `release_type` — becomes the SDKman default. The simplest correct fix is to always run `sdkMajorRelease` (which includes `sdkDefaultVersion`). The only difference between major and minor tasks is announcement style; both register the version and both should update the default.

**Files:**
- Modify: `.github/workflows/release.yml` (lines 761–771, the "Announce to SDKMan" step)

Current code in `release.yml` (lines 761–771):
```yaml
      - name: Announce to SDKMan
        env:
          SDKMAN_API_KEY: ${{ secrets.SDKMAN_KEY }}
          SDKMAN_API_TOKEN: ${{ secrets.SDKMAN_TOKEN }}
        run: |
          # Use sdkMajorRelease for major releases, sdkMinorRelease otherwise
          if [[ "${{ inputs.release_type }}" == "major" ]]; then
            ./gradlew :btrace-dist:sdkMajorRelease --no-daemon
          else
            ./gradlew :btrace-dist:sdkMinorRelease --no-daemon
          fi
```

- [ ] **Step 1: Replace the conditional SDKman step with a unified call**

Open `.github/workflows/release.yml` and replace the "Announce to SDKMan" step's `run` block with the unified version below. The change: remove the `if/else` and always run `sdkMajorRelease`. This registers the version, sets it as default, and posts a structured announcement.

New content for the `run` block (lines 766–771):
```yaml
        run: |
          ./gradlew :btrace-dist:sdkMajorRelease --no-daemon
```

After the edit the full step should look like:
```yaml
      - name: Announce to SDKMan
        env:
          SDKMAN_API_KEY: ${{ secrets.SDKMAN_KEY }}
          SDKMAN_API_TOKEN: ${{ secrets.SDKMAN_TOKEN }}
        run: |
          ./gradlew :btrace-dist:sdkMajorRelease --no-daemon
```

- [ ] **Step 2: Verify the YAML is still valid**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))" && echo "YAML valid"
```
Expected output: `YAML valid`

- [ ] **Step 3: Verify the comment in the "Release Summary" step still links to SDKMan correctly**

```bash
grep -n "sdkman\|SDKMan" .github/workflows/release.yml
```
Expected: lines 480, 770 (approx) — no stale references.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "ci: always set SDKMan default version on every release type"
```

---

## Task 3: Push and trigger the hotfix

After both commits are on the branch and merged to the default/release branch, trigger the new workflow to immediately fix the live SDKman default.

**Files:** none (operational)

- [ ] **Step 1: Open a PR from develop → master (or push directly if you're the maintainer)**

```bash
gh pr create \
  --base master \
  --head develop \
  --title "fix: SDKMan default version stuck at 2.2.0" \
  --body "$(cat <<'EOF'
Fixes #848.

The `sdkMinorRelease` Gradle task (used for minor/patch releases) registers a new version with SDKMan but does not call `sdkDefaultVersion`, so the installed-by-default version was never updated after v2.2.0.

Changes:
- Always use `sdkMajorRelease` in the release workflow so every release sets the new version as the SDKMan default.
- Add a manual `sdkman-set-default.yml` workflow to hotfix the current default (v2.2.0 → v2.2.6) and for future emergency corrections.
EOF
)"
```

- [ ] **Step 2: Merge the PR**

Wait for CI to pass, then merge.

- [ ] **Step 3: Dispatch the hotfix workflow**

After merge, go to **Actions → Set SDKMan Default Version → Run workflow**, enter `2.2.6`, and click Run.

OR via CLI:
```bash
gh workflow run sdkman-set-default.yml \
  --repo btraceio/btrace \
  --field version=2.2.6
```

- [ ] **Step 4: Verify the fix is live**

Wait ~5 minutes for SDKman's cache to propagate, then verify:
```bash
curl -s "https://api.sdkman.io/2/candidates/default/btrace" 2>/dev/null || \
  curl -s "https://vendors.sdkman.io/candidates/btrace/versions/default" 2>/dev/null
# Should return: 2.2.6
```

OR install in a test environment:
```bash
sdk install btrace
# Expected: "Downloading: btrace 2.2.6"
```

- [ ] **Step 5: Close issue #848**

```bash
gh issue close 848 --repo btraceio/btrace \
  --comment "Fixed in this PR. SDKMan default has been updated to v2.2.6. Future releases will always update the default automatically."
```

---

## Self-Review

**Spec coverage:**
- ✓ SDKman installs wrong version → fixed by Task 3 (hotfix workflow)
- ✓ Future releases will fix the default → fixed by Task 2 (release.yml)
- ✓ No regression: `sdkMajorRelease` does everything `sdkMinorRelease` did plus `sdkDefaultVersion`

**Placeholder scan:** No TBDs or "implement later" present.

**Type consistency:** No custom types; Gradle task names match plugin jar inspection (`SdkDefaultVersion`, `SdkMajorRelease`, `SdkMinorRelease`).

**Edge case — `onlyIf` guard:** `btrace-dist/build.gradle:854` guards `sdkReleaseVersion` and `sdkAnnounceVersion` (both called by `sdkMajorRelease`) against snapshot versions. This guard is correct and unchanged; release workflow only runs against tagged non-snapshot versions.
