#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
DIST=""
FAT_AGENT=""
VERSION=""
KEEP_WORK=false
WORK=""
TARGET_PID=""
CLIENT_PID=""

usage() {
  cat <<'EOF'
Usage: scripts/release-smoke.sh --distribution <dir> --fat-agent <jar> --version <version> [--keep-work]

Runs the release-candidate smoke matrix against an already built distribution. The test requires
JDK 11+, Bash, Python 3, and a local Attach API-capable JVM. It performs no analytics or download
count queries.
EOF
}

while (($#)); do
  case "$1" in
    --distribution)
      DIST=$2
      shift 2
      ;;
    --fat-agent)
      FAT_AGENT=$2
      shift 2
      ;;
    --version)
      VERSION=$2
      shift 2
      ;;
    --keep-work)
      KEEP_WORK=true
      shift
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
  echo "release smoke FAILED: $*" >&2
  if [[ -n "$WORK" && -d "$WORK/logs" ]]; then
    for log in "$WORK"/logs/*.log; do
      [[ -f "$log" ]] || continue
      echo "===== $log =====" >&2
      tail -n 200 "$log" >&2
    done
  fi
  exit 1
}

stop_process() {
  local pid=${1:-}
  [[ -n "$pid" ]] || return 0
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    for _ in {1..20}; do
      kill -0 "$pid" 2>/dev/null || break
      sleep 0.1
    done
    kill -9 "$pid" 2>/dev/null || true
  fi
  wait "$pid" 2>/dev/null || true
}

cleanup() {
  stop_process "$CLIENT_PID"
  stop_process "$TARGET_PID"
  if [[ -n "$WORK" && -d "$WORK" ]]; then
    if [[ "$KEEP_WORK" == true ]]; then
      echo "release smoke work directory: $WORK"
    else
      rm -rf "$WORK"
    fi
  fi
}
trap cleanup EXIT

[[ -n "$DIST" && -d "$DIST" ]] || fail "--distribution must name an extracted distribution"
[[ -n "$FAT_AGENT" && -f "$FAT_AGENT" ]] || fail "--fat-agent must name a built fat-agent JAR"
[[ -n "$VERSION" ]] || fail "--version is required"
[[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] || fail "JAVA_HOME must point to a JDK"
command -v python3 >/dev/null 2>&1 || fail "python3 is required for socket assertions"

DIST=$(cd "$DIST" && pwd)
FAT_AGENT=$(cd "$(dirname "$FAT_AGENT")" && pwd)/$(basename "$FAT_AGENT")
BTRACE="$DIST/bin/btrace"
BTRACEC="$DIST/bin/btracec"
MASKED_JAR="$DIST/libs/btrace.jar"
[[ -x "$BTRACE" ]] || fail "missing executable $BTRACE"
[[ -x "$BTRACEC" ]] || fail "missing executable $BTRACEC"
[[ -f "$MASKED_JAR" ]] || fail "missing masked JAR $MASKED_JAR"

WORK=$(mktemp -d "${TMPDIR:-/tmp}/btrace-release-smoke.XXXXXX")
mkdir -p "$WORK/logs" "$WORK/classes"
cp "$ROOT/docs/tutorials/demo/DemoApp.java" "$WORK/DemoApp.java"
cp "$ROOT/docs/tutorials/demo/OrderTiming.java" "$WORK/OrderTiming.java"
cp "$ROOT/docs/tutorials/demo/legacy/SlowChargeProbe.java" "$WORK/SlowChargeProbe.java"
cp "$ROOT/scripts/release-smoke/"*.java "$WORK/"

wait_for_text() {
  local file=$1
  local text=$2
  local timeout_seconds=${3:-45}
  local deadline=$((SECONDS + timeout_seconds))
  while ((SECONDS < deadline)); do
    grep -F "$text" "$file" >/dev/null 2>&1 && return 0
    sleep 0.2
  done
  return 1
}

# Set NO_BTRACE_HOME=1 for a single call to launch the target with BTRACE_HOME unset. Only the
# embedded-extension legs want this: it stops a developer's SDKMAN install from satisfying an
# @Injected service from $BTRACE_HOME/extensions/, which would leave the embedded copy untested.
# It is defence-in-depth for local runs rather than the CI mechanism, since this script never
# exports BTRACE_HOME - it passes it as a per-command prefix assignment.
start_patient() {
  local label=$1
  shift
  stop_process "$TARGET_PID"
  TARGET_PID=""
  local log="$WORK/logs/${label}-target.log"
  if [ "${NO_BTRACE_HOME:-}" = "1" ]; then
    env -u BTRACE_HOME "$JAVA_HOME/bin/java" "$@" "$WORK/DemoApp.java" >"$log" 2>&1 &
  else
    "$JAVA_HOME/bin/java" "$@" "$WORK/DemoApp.java" >"$log" 2>&1 &
  fi
  TARGET_PID=$!
  wait_for_text "$log" "[demo] order service running" 30 || fail "$label target did not become ready"
}

run_doctor() {
  local label=$1
  local expected_exit=$2
  local expected_text=$3
  local log="$WORK/logs/${label}-doctor.log"
  local exit_code=0
  for attempt in 1 2; do
    set +e
    BTRACE_HOME="$DIST" JAVA_HOME="$JAVA_HOME" "$BTRACE" doctor "$TARGET_PID" >"$log" 2>&1
    exit_code=$?
    set -e
    [[ "$exit_code" -ne 3 || "$expected_exit" -eq 3 ]] && break
    sleep 1
  done
  [[ "$exit_code" -eq "$expected_exit" ]] || fail "$label doctor exit $exit_code, expected $expected_exit"
  grep -F "$expected_text" "$log" >/dev/null || fail "$label doctor output did not contain: $expected_text"
}

start_trace() {
  local label=$1
  local expected_text=$2
  local java_tool_options=$3
  shift 3
  local log="$WORK/logs/${label}-client.log"
  stop_process "$CLIENT_PID"
  CLIENT_PID=""
  (
    cd "$WORK"
    BTRACE_HOME="$DIST" JAVA_HOME="$JAVA_HOME" JAVA_TOOL_OPTIONS="$java_tool_options" \
      "$BTRACE" "$@"
  ) >"$log" 2>&1 &
  CLIENT_PID=$!
  wait_for_text "$log" "$expected_text" 60 || fail "$label did not produce: $expected_text"
}

finish_trace() {
  stop_process "$CLIENT_PID"
  CLIENT_PID=""
  sleep 1
}

echo "[smoke] distribution metadata"
version_output=$(BTRACE_HOME="$DIST" JAVA_HOME="$JAVA_HOME" "$BTRACE" --version 2>&1) \
  || fail "btrace --version failed: $version_output"
[[ "$version_output" == *"$VERSION"* ]] || fail "btrace --version did not contain $VERSION: $version_output"
manifest=$(unzip -p "$MASKED_JAR" META-INF/MANIFEST.MF) \
  || fail "could not read the masked JAR manifest"
for entry in \
  "Main-Class: io.btrace.boot.Loader" \
  "Premain-Class: io.btrace.boot.Loader" \
  "Agent-Class: io.btrace.boot.Loader" \
  "BTrace-Version: $VERSION"; do
  [[ "$manifest" == *"$entry"* ]] || fail "masked JAR manifest missing $entry"
done

echo "[smoke] documented dynamic oneliner, source script, and doctor"
start_patient dynamic
run_doctor before-attach 2 "PREPARATION_REQUIRED"
start_trace documented-oneliner "processOrder" "" -n \
  'OrderService::processOrder @return { print method, time }' "$TARGET_PID"
run_doctor after-attach 0 "READY"
finish_trace
start_trace documented-source "worker=" "" "$TARGET_PID" OrderTiming.java
finish_trace
stop_process "$TARGET_PID"
TARGET_PID=""

echo "[smoke] authenticated prepared mode"
start_patient prepared "-javaagent:$MASKED_JAR=port=0"
run_doctor prepared 0 "READY"
grep -F "Authentication: required" "$WORK/logs/prepared-doctor.log" >/dev/null \
  || fail "prepared mode did not require authentication"
start_trace prepared-source "worker=" "" "$TARGET_PID" OrderTiming.java
finish_trace
stop_process "$TARGET_PID"
TARGET_PID=""

echo "[smoke] startup script with noServer=true"
(
  cd "$WORK"
  BTRACE_HOME="$DIST" JAVA_HOME="$JAVA_HOME" "$BTRACEC" -d "$WORK/classes" StartupProbe.java
) >"$WORK/logs/startup-compile.log" 2>&1 || fail "startup probe compilation failed"
startup_class="$WORK/classes/StartupProbe.class"
[[ -f "$startup_class" ]] || fail "btracec did not produce StartupProbe.class"
closed_port=$(python3 - <<'PY'
import socket
s = socket.socket()
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PY
)
start_patient no-server \
  "-javaagent:$MASKED_JAR=script=$startup_class,noServer=true,port=$closed_port,stdout=true"
wait_for_text "$WORK/logs/no-server-target.log" "release-smoke: startup probe" 45 \
  || fail "startup probe did not run"
run_doctor no-server 2 "PREPARATION_REQUIRED"
python3 - "$closed_port" <<'PY' || fail "noServer=true opened the configured command socket"
import socket
import sys
s = socket.socket()
s.settimeout(0.5)
try:
    s.connect(("127.0.0.1", int(sys.argv[1])))
except OSError:
    raise SystemExit(0)
finally:
    s.close()
raise SystemExit(1)
PY
stop_process "$TARGET_PID"
TARGET_PID=""

echo "[smoke] 2.x source migration and distribution compiler"
"$ROOT/scripts/migrate-btrace-script.sh" "$WORK/SlowChargeProbe.java" \
  >"$WORK/logs/migration.log" 2>&1 || fail "source migration helper failed"
if grep -F "org.openjdk.btrace" "$WORK/SlowChargeProbe.java" >/dev/null; then
  fail "source migration left legacy package references"
fi
(
  cd "$WORK"
  BTRACE_HOME="$DIST" JAVA_HOME="$JAVA_HOME" "$BTRACEC" -d "$WORK/classes" SlowChargeProbe.java
) >"$WORK/logs/migration-compile.log" 2>&1 || fail "migrated source did not compile"

echo "[smoke] forced V1, forced V2, and automatic negotiation"
for mode in v1 v2 auto; do
  case "$mode" in
    v1) protocol_options="-Dbtrace.comm.protocol=1 -Dbtrace.comm.autoNegotiate=false -Dbtrace.comm.forceVersion=true" ;;
    v2) protocol_options="-Dbtrace.comm.protocol=2 -Dbtrace.comm.autoNegotiate=false -Dbtrace.comm.forceVersion=true" ;;
    auto) protocol_options="-Dbtrace.comm.protocol=2 -Dbtrace.comm.autoNegotiate=true -Dbtrace.comm.forceVersion=false" ;;
  esac
  read -r -a target_options <<<"$protocol_options"
  start_patient "protocol-$mode" "${target_options[@]}"
  start_trace "protocol-$mode" "worker=" "$protocol_options" "$TARGET_PID" OrderTiming.java
  finish_trace
  stop_process "$TARGET_PID"
  TARGET_PID=""
done

echo "[smoke] embedded non-privileged and privileged extensions"
# No allowExtensions entry: the policy only consults it on the privileged branch, so listing a
# non-privileged extension asserts nothing. allowPrivileged=false is the meaningful constraint.
cat >"$WORK/permissions.properties" <<'EOF'
allowPrivileged=false
EOF
NO_BTRACE_HOME=1 start_patient embedded-non-privileged \
  "-Dbtrace.permissions=$WORK/permissions.properties" \
  "-javaagent:$FAT_AGENT=port=0,debug=true"
run_doctor embedded-non-privileged 0 "READY"
# Prove the extension came from the fat agent rather than $BTRACE_HOME/extensions/. Every
# release extension is also exploded into the distribution, so without this the leg would pass
# on the filesystem copy and never exercise the embedded one.
wait_for_text "$WORK/logs/embedded-non-privileged-target.log" \
  "BTRACE_HOME not set; using embedded extensions only" 30 \
  || fail "embedded-non-privileged target resolved a BTRACE_HOME instead of embedded extensions"
wait_for_text "$WORK/logs/embedded-non-privileged-target.log" \
  "Scanning repository: embedded:META-INF/btrace-extensions/" 30 \
  || fail "embedded-non-privileged target did not scan the embedded extension repository"
start_trace embedded-non-privileged "release-smoke: non-privileged extension" "" \
  "$TARGET_PID" NonPrivilegedExtensionProbe.java
finish_trace
stop_process "$TARGET_PID"
TARGET_PID=""

cat >"$WORK/permissions.properties" <<'EOF'
allowExtensions=btrace-metrics
allowPrivileged=true
EOF
NO_BTRACE_HOME=1 start_patient embedded-privileged \
  "-Dbtrace.permissions=$WORK/permissions.properties" \
  "-javaagent:$FAT_AGENT=port=0,debug=true"
run_doctor embedded-privileged 0 "READY"
start_trace embedded-privileged "release-smoke: privileged extension" "" \
  "$TARGET_PID" PrivilegedExtensionProbe.java
finish_trace
stop_process "$TARGET_PID"
TARGET_PID=""

echo "release smoke PASSED"
