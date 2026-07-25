#!/usr/bin/env bash
set -euo pipefail

DIST=""
SDKMAN_ARCHIVE=""
MAVEN_POM=""
VERSION=""
CONTAINER_IMAGE=""

usage() {
  cat <<'EOF'
Usage: scripts/verify-release-artifacts.sh --distribution <dir> --sdkman-archive <zip>
       --maven-pom <file> --version <version> [--container-image <tag>]

Verifies release artifact layout and metadata without consulting download counts or analytics.
EOF
}

while (($#)); do
  case "$1" in
    --distribution)
      DIST=$2
      shift 2
      ;;
    --sdkman-archive)
      SDKMAN_ARCHIVE=$2
      shift 2
      ;;
    --maven-pom)
      MAVEN_POM=$2
      shift 2
      ;;
    --version)
      VERSION=$2
      shift 2
      ;;
    --container-image)
      CONTAINER_IMAGE=$2
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 64
      ;;
  esac
done

fail() {
  echo "release artifact verification FAILED: $*" >&2
  exit 1
}

[[ -n "$DIST" && -d "$DIST" ]] || fail "--distribution must name an extracted distribution"
[[ -n "$SDKMAN_ARCHIVE" && -f "$SDKMAN_ARCHIVE" ]] || fail "--sdkman-archive must name a ZIP"
[[ -n "$MAVEN_POM" && -f "$MAVEN_POM" ]] || fail "--maven-pom must name a POM file"
[[ -n "$VERSION" ]] || fail "--version is required"
[[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] || fail "JAVA_HOME must point to a JDK"

masked_jar="$DIST/libs/btrace.jar"
[[ -f "$masked_jar" ]] || fail "distribution does not contain libs/btrace.jar"
[[ -x "$DIST/bin/btrace" ]] || fail "distribution does not contain executable bin/btrace"

version_output=$(BTRACE_HOME="$DIST" "$DIST/bin/btrace" --version 2>&1) \
  || fail "btrace --version failed: $version_output"
[[ "$version_output" == *"$VERSION"* ]] || fail "btrace --version did not contain $VERSION: $version_output"

manifest=$(unzip -p "$masked_jar" META-INF/MANIFEST.MF) \
  || fail "could not read the masked JAR manifest"
for entry in \
  "Main-Class: io.btrace.boot.Loader" \
  "Premain-Class: io.btrace.boot.Loader" \
  "Agent-Class: io.btrace.boot.Loader" \
  "BTrace-Version: $VERSION"; do
  [[ "$manifest" == *"$entry"* ]] || fail "masked JAR manifest missing $entry"
done

sdkman_entries=$(unzip -Z1 "$SDKMAN_ARCHIVE") \
  || fail "could not list the SDKMAN archive"
for entry in bin/btrace bin/btracec libs/btrace.jar; do
  grep -Fx "$entry" <<<"$sdkman_entries" >/dev/null \
    || fail "SDKMAN archive is not installable at its root; missing $entry"
done
if grep -E "^v${VERSION}/(bin|libs)/" <<<"$sdkman_entries" >/dev/null; then
  fail "SDKMAN archive contains an extra v$VERSION directory"
fi

grep -F "<name>The Apache License, Version 2.0</name>" "$MAVEN_POM" >/dev/null \
  || fail "Maven POM is missing the Apache 2.0 license name"
grep -F "<url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>" "$MAVEN_POM" >/dev/null \
  || fail "Maven POM is missing the Apache 2.0 license URL"

if [[ -n "$CONTAINER_IMAGE" ]]; then
  command -v docker >/dev/null 2>&1 || fail "docker is required for --container-image"
  image_license=$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.licenses" }}' "$CONTAINER_IMAGE") \
    || fail "could not inspect the container OCI license"
  [[ "$image_license" == "Apache-2.0" ]] || fail "container OCI license is $image_license"
  image_version=$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.version" }}' "$CONTAINER_IMAGE") \
    || fail "could not inspect the container OCI version"
  [[ "$image_version" == "$VERSION" ]] || fail "container OCI version is $image_version"
  docker run --rm --entrypoint /bin/sh "$CONTAINER_IMAGE" -c \
    'test -f /opt/btrace/libs/btrace.jar && test ! -f /opt/btrace/libs/btrace-agent.jar' \
    || fail "container does not contain the intended masked JAR layout"
fi

echo "release artifact verification PASSED"
