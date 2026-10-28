#!/usr/bin/env bash
# Shared helpers for the BTrace demo scenarios. Sourced by each demos/<nn>-<name>/run.sh.
#
# Environment:
#   BTRACE_HOME  BTrace 3.0 installation (defaults to the parent of the `btrace` on PATH)
#   JAVA_HOME    JDK 11+ used for both the demo app and the BTrace client
#   DEMO_PORT    optional: pass `-p $DEMO_PORT` to every btrace command so several demos can run
#                side by side (the default agent port is 2020); demos sharing an app (02 and 03
#                both run OrderServiceApp) can not, since btrace finds the app by its main class
#   DEMO_KEEP    set by `run.sh --keep`: leave the demo app running at the end for a live demo
#
# Every helper prints the command it runs, prefixed with `$`, so a recording reads like a shell.

set -u
# A btrace client that dies before reading its stdin menu must not take the script with it.
trap '' PIPE

DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[1]}")" && pwd)"
DEMO_NAME="$(basename "$DEMO_DIR")"
cd "$DEMO_DIR" || exit 2   # every run.sh works from its own directory
DEMO_LOG_DIR="${DEMO_LOG_DIR:-${TMPDIR:-/tmp}/btrace-demo-$DEMO_NAME}"
mkdir -p "$DEMO_LOG_DIR"

if [ -z "${JAVA_HOME:-}" ]; then
  echo "JAVA_HOME must point to a JDK 11 or newer" >&2
  exit 2
fi
if [ -z "${BTRACE_HOME:-}" ]; then
  if command -v btrace >/dev/null 2>&1; then
    BTRACE_HOME="$(cd "$(dirname "$(command -v btrace)")/.." && pwd)"
  else
    echo "BTRACE_HOME must point to a BTrace 3.0 installation (or put bin/btrace on PATH)" >&2
    exit 2
  fi
fi
export BTRACE_HOME JAVA_HOME
PATH="$BTRACE_HOME/bin:$JAVA_HOME/bin:$PATH"

DEMO_KEEP="${DEMO_KEEP:-}"
for arg in "$@"; do
  [ "$arg" = "--keep" ] && DEMO_KEEP=1
done

PORT_OPT=()
[ -n "${DEMO_PORT:-}" ] && PORT_OPT=(-p "$DEMO_PORT")

APP_PID=""
APP_TARGET=""
APP_LOG=""

say() { printf '\n# %s\n' "$*"; }
show() { printf '$ %s\n' "$*"; }

# show_btrace <btrace args...>: echoes a btrace command, quoting `~<regex>` targets so the line
# can be pasted into a shell without tilde expansion.
show_btrace() {
  local line="btrace" arg
  for arg in ${PORT_OPT[@]+"${PORT_OPT[@]}"} "$@"; do
    case "$arg" in
      "~"*) line="$line '$arg'" ;;
      *) line="$line $arg" ;;
    esac
  done
  show "$line"
}

# start_app <Main.java> [java options...]: launches the demo app in the background from this
# demo's directory and waits for its "[demo]" banner. Sets APP_PID, APP_LOG and APP_TARGET, the
# `~<regex>` btrace target naming the app by its main class instead of its PID.
start_app() {
  local main="$1"; shift
  APP_LOG="$DEMO_LOG_DIR/app.log"
  : > "$APP_LOG"
  show "java $* $main"
  (cd "$DEMO_DIR" && exec "$JAVA_HOME/bin/java" "$@" "$main") >"$APP_LOG" 2>&1 &
  APP_PID=$!
  APP_TARGET="~$(basename "$main" .java)"
  wait_for_line "$APP_LOG" '\[demo\]' 30 || { echo "demo app did not start; see $APP_LOG" >&2; exit 1; }
  echo "[demo app] pid $APP_PID"
}

# stop_app: stops the demo app unless --keep was given.
stop_app() {
  if [ -n "$DEMO_KEEP" ]; then
    echo "[demo app] left running as pid $APP_PID (--keep); stop it with: kill $APP_PID"
    return
  fi
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    show "kill $APP_PID"
    kill "$APP_PID" 2>/dev/null
    wait "$APP_PID" 2>/dev/null
  fi
}

# wait_for_line <file> <regex> <seconds>: polls a file for a line matching the regex.
wait_for_line() {
  local file="$1" regex="$2" seconds="$3" i=0
  while [ $i -lt $((seconds * 10)) ]; do
    grep -Eq "$regex" "$file" 2>/dev/null && return 0
    sleep 0.1; i=$((i + 1))
  done
  return 1
}

# btrace_until <regex> <seconds> <btrace args...>: runs `btrace` against the demo app, streams
# its output, and ends the session once a line matches the regex (or the time is up). The client
# is sent `exit` through its stdin menu, so the agent retracts the probe cleanly.
btrace_until() {
  local regex="$1" seconds="$2"; shift 2
  local out="$DEMO_LOG_DIR/btrace-$RANDOM.log"
  local fifo="$DEMO_LOG_DIR/stdin-$RANDOM"
  mkfifo "$fifo"
  show_btrace "$@"
  (cd "$DEMO_DIR" && exec "$BTRACE_HOME/bin/btrace" ${PORT_OPT[@]+"${PORT_OPT[@]}"} "$@") <"$fifo" >"$out" 2>&1 &
  local pid=$!
  exec 3>"$fifo"
  tail -n +1 -f "$out" 2>/dev/null &
  local tail_pid=$!
  if wait_for_line "$out" "$regex" "$seconds"; then
    sleep 1
  else
    echo "[demo] expected output not seen within ${seconds}s: $regex" >&2
    BTRACE_DEMO_FAILED=1
  fi
  # End the session. The launcher script runs the client JVM as a child; a TERM to that JVM runs
  # the client's exit hook, which tells the agent to retract the probe and restore the original
  # bytecode (the same path as "1. exit" in the interactive Ctrl+C menu).
  local jvm
  jvm="$(pgrep -P "$pid" 2>/dev/null | head -n 1)"
  kill -TERM "${jvm:-$pid}" 2>/dev/null
  for _ in $(seq 1 40); do kill -0 "$pid" 2>/dev/null || break; sleep 0.25; done
  if kill -0 "$pid" 2>/dev/null; then
    echo "[demo] btrace client did not exit on request; killing it" >&2
    kill -KILL "$pid" "${jvm:-}" 2>/dev/null
  fi
  wait "$pid" 2>/dev/null
  exec 3>&-
  kill "$tail_pid" 2>/dev/null; wait "$tail_pid" 2>/dev/null
  rm -f "$fifo"
  echo
}

# btrace_run <btrace args...>: runs a one-shot btrace command to completion and echoes its output.
btrace_run() {
  show_btrace "$@"
  (cd "$DEMO_DIR" && "$BTRACE_HOME/bin/btrace" ${PORT_OPT[@]+"${PORT_OPT[@]}"} "$@" 2>&1)
  echo
}

# tool_run <cmd...>: runs any other command from the demo directory, echoing it first.
tool_run() {
  show "$*"
  (cd "$DEMO_DIR" && "$@" 2>&1)
  echo
}

BTRACE_DEMO_FAILED=""
finish() {
  trap - EXIT
  stop_app
  if [ -n "$BTRACE_DEMO_FAILED" ]; then
    echo "[demo] FAILED: some expected output never appeared (logs in $DEMO_LOG_DIR)" >&2
    exit 1
  fi
  echo "[demo] done"
}
trap 'stop_app' EXIT
