#!/usr/bin/env bash
#
# Checks for newer JDK versions on SDKMan and updates workflow files.
# Exit 0 = changes made, exit 1 = no changes needed, exit 2 = error.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CONTINUOUS="$REPO_ROOT/.github/workflows/continuous.yml"
RELEASE="$REPO_ROOT/.github/workflows/release.yml"

SDKMAN_API="https://api.sdkman.io/2/candidates/java/linuxx64/versions/all"

# Portable in-place sed (works on both GNU and BSD sed)
sed_i() {
  local expr=$1; shift
  for f in "$@"; do
    sed -i.bak "$expr" "$f" && rm -f "$f.bak"
  done
}

echo "Fetching SDKMan version list..." >&2
ALL_VERSIONS=$(curl -sf "$SDKMAN_API") || {
  echo "ERROR: Failed to fetch SDKMan version list" >&2
  exit 2
}

VERSION_LIST=$(printf '%s' "$ALL_VERSIONS" | tr ',' '\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

# Find the latest Temurin GA release for a given major version.
# Handles both X.0.N-tem and bare X-tem identifiers.
latest_temurin_ga() {
  local major=$1
  printf '%s\n' "$VERSION_LIST" | grep -E "^${major}(\\.0\\.[0-9]+)?-tem$" | sort -V | tail -1
}

# Find the latest OpenJDK EA build for a given major version.
latest_ea_open() {
  local major=$1
  printf '%s\n' "$VERSION_LIST" | grep -E "^${major}\\.ea\\.[0-9]+-open$" | sort -t. -k3 -n | tail -1
}

# Find the highest major version that has any EA builds on SDKMan.
latest_ea_major() {
  printf '%s\n' "$VERSION_LIST" | grep -E "^[0-9]+\\.ea\\.[0-9]+-open$" | \
    sed 's/\.ea\..*//' | sort -n | tail -1
}

# --- Extract current versions ---

# Test matrix from continuous.yml (superset of release.yml)
MATRIX_VERSIONS=$(grep 'java:.*\[' "$CONTINUOUS" | head -1 \
  | sed 's/.*\[//;s/\].*//' | tr ',' '\n' \
  | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

# Build JDK from the "echo 'y' | sdk install java" line
BUILD_JDK=$(grep "echo 'y' | sdk install java" "$CONTINUOUS" | head -1 \
  | sed "s/.*sdk install java //" | tr -d "[:space:]")

# --- Resolve and apply updates ---

CHANGES=()

for ver in $MATRIX_VERSIONS; do
  if [[ "$ver" =~ ^([0-9]+)(\.0\.[0-9]+)?-tem$ ]]; then
    major="${BASH_REMATCH[1]}"
    latest=$(latest_temurin_ga "$major")
    if [[ -n "$latest" && "$latest" != "$ver" ]]; then
      CHANGES+=("$ver -> $latest")
      # Only replace on matrix lines to avoid substring matches (e.g. 25-tem inside 11.0.25-tem)
      sed_i "/java:.*\[/s|${ver}|${latest}|g" "$CONTINUOUS" "$RELEASE"
    fi
  elif [[ "$ver" =~ ^([0-9]+)\.ea\.([0-9]+)-open$ ]]; then
    major="${BASH_REMATCH[1]}"
    newest_major=$(latest_ea_major)
    [[ -n "$newest_major" && "$newest_major" -gt "$major" ]] && major="$newest_major"
    latest=$(latest_ea_open "$major")
    if [[ -n "$latest" && "$latest" != "$ver" ]]; then
      CHANGES+=("$ver -> $latest")
      sed_i "/java:.*\[/s|${ver}|${latest}|g" "$CONTINUOUS" "$RELEASE"
    fi
  fi
done

# Update build JDK if stale
if [[ -n "$BUILD_JDK" && "$BUILD_JDK" =~ ^([0-9]+) ]]; then
  build_major="${BASH_REMATCH[1]}"
  latest_build=$(latest_temurin_ga "$build_major")
  if [[ -n "$latest_build" && "$latest_build" != "$BUILD_JDK" ]]; then
    CHANGES+=("build JDK: $BUILD_JDK -> $latest_build")
    sed_i "s|sdk install java ${BUILD_JDK}|sdk install java ${latest_build}|g" "$CONTINUOUS" "$RELEASE"
  fi
fi

# --- Report results ---

if [[ ${#CHANGES[@]} -eq 0 ]]; then
  echo "All JDK versions are up to date." >&2
  exit 1
fi

for change in "${CHANGES[@]}"; do
  echo "- $change"
done
exit 0
