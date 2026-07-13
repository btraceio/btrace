#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TARGET_JAVA_HOME=""
DIST=""

usage() {
  cat <<'EOF'
Usage: scripts/verify-jdk-warning.sh --java-home <target-jdk> --distribution <dir>

Runs the BTrace Java-version warning code twice in one target JVM. Java 8 and 11 must emit exactly
one deprecation warning; Java 17 and newer must emit none.
EOF
}

while (($#)); do
  case "$1" in
    --java-home)
      TARGET_JAVA_HOME=$2
      shift 2
      ;;
    --distribution)
      DIST=$2
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
  echo "JDK warning policy FAILED: $*" >&2
  exit 1
}

[[ -n ${JAVA_HOME:-} && -x "$JAVA_HOME/bin/javac" ]] \
  || fail "JAVA_HOME must point to the build JDK"
[[ -n "$TARGET_JAVA_HOME" && -x "$TARGET_JAVA_HOME/bin/java" ]] \
  || fail "--java-home must point to the target JDK"
[[ -n "$DIST" && -f "$DIST/libs/btrace.jar" ]] \
  || fail "--distribution must contain libs/btrace.jar"

WORK=$(mktemp -d "${TMPDIR:-/tmp}/btrace-jdk-warning.XXXXXX")
trap 'rm -rf "$WORK"' EXIT

"$JAVA_HOME/bin/javac" --release 8 -cp "$DIST/libs/btrace.jar" -d "$WORK" \
  "$ROOT/scripts/release-gates/JdkWarningProbe.java"

OUTPUT=$("$TARGET_JAVA_HOME/bin/java" -cp "$WORK:$DIST/libs/btrace.jar" JdkWarningProbe 2>&1) \
  || fail "probe process failed: $OUTPUT"
printf '%s\n' "$OUTPUT"

FEATURE=$(printf '%s\n' "$OUTPUT" | sed -n 's/^JAVA_FEATURE=//p')
[[ "$FEATURE" =~ ^[0-9]+$ ]] || fail "probe did not report a Java feature version"

WARNING_COUNT=$(printf '%s\n' "$OUTPUT" \
  | grep -cF '[BTrace] WARNING: This JVM is Java' || true)
if ((FEATURE < 17)); then
  [[ "$WARNING_COUNT" -eq 1 ]] \
    || fail "Java $FEATURE emitted $WARNING_COUNT deprecation warnings; expected exactly one"
else
  [[ "$WARNING_COUNT" -eq 0 ]] \
    || fail "Java $FEATURE emitted a Java-version deprecation warning"
fi

echo "JDK warning policy PASSED for Java $FEATURE (warnings=$WARNING_COUNT)"
