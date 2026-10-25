#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

usage() {
  echo "Usage: scripts/qe-agent.sh [--surface <csv>] [--case <id>] [--seed <n>] [--timeout <ms>]" >&2
}

GRADLE_ARGS=(:btrace-qe:qe)
while [[ $# -gt 0 ]]; do
  [[ $# -ge 2 ]] || { usage; exit 64; }
  case "$1" in
    --surface) GRADLE_ARGS+=("-Pqe.surface=$2") ;;
    --case) GRADLE_ARGS+=("-Pqe.case=$2") ;;
    --seed) GRADLE_ARGS+=("-Pqe.seed=$2") ;;
    --timeout) GRADLE_ARGS+=("-Pqe.timeout=$2") ;;
    *) echo "Unknown option: $1" >&2; usage; exit 64 ;;
  esac
  shift 2
done

GRADLE_USER_HOME="$(pwd)/.gradle-user" ./gradlew "${GRADLE_ARGS[@]}"
echo "Report: btrace-qe/build/qe-report/report.md"
