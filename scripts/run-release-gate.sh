#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/run-release-gate.sh <label> <log-directory>
       [--working-directory <dir>] -- <command> [args...]

Runs one release gate, records the exact shell-escaped command and filtered output, and prints the
last 200 log lines on failure. The full log and Markdown summary remain in <log-directory>.
EOF
}

[[ $# -ge 4 ]] || {
  usage >&2
  exit 64
}

LABEL=$1
LOG_DIR=$2
shift 2
WORKING_DIRECTORY=.

if [[ ${1:-} == "--working-directory" ]]; then
  [[ $# -ge 3 ]] || {
    usage >&2
    exit 64
  }
  WORKING_DIRECTORY=$2
  shift 2
fi

[[ ${1:-} == "--" ]] || {
  usage >&2
  exit 64
}
shift
[[ $# -gt 0 ]] || {
  usage >&2
  exit 64
}

mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/$LABEL.log"
SUMMARY_FILE="$LOG_DIR/$LABEL-summary.md"

printf -v COMMAND '%q ' "$@"
COMMAND=${COMMAND% }

set +e
(
  cd "$WORKING_DIRECTORY"
  "$@"
) >"$LOG_FILE" 2>&1
STATUS=$?
set -e

if [[ $STATUS -eq 0 ]]; then
  RESULT=PASS
else
  RESULT=FAIL
fi

{
  echo "### $LABEL — $RESULT"
  echo
  echo "- Working directory: \`$WORKING_DIRECTORY\`"
  echo "- Command: \`$COMMAND\`"
  echo "- Exit code: \`$STATUS\`"
  echo
  echo '```text'
  grep -E 'BUILD (SUCCESSFUL|FAILED)|[0-9]+ tests? completed|[0-9]+ actionable tasks|PASSED|FAILED|FAILURE:|Execution failed|warning policy' \
    "$LOG_FILE" | tail -n 80 || true
  echo '```'
} >"$SUMMARY_FILE"

cat "$SUMMARY_FILE"

if [[ $STATUS -ne 0 ]]; then
  echo "release gate '$LABEL' failed; tail of $LOG_FILE:" >&2
  tail -n 200 "$LOG_FILE" >&2
fi

exit "$STATUS"
