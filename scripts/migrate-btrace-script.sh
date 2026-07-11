#!/usr/bin/env bash
#
# Migrates BTrace script sources from the legacy 2.x package namespace
# (org.openjdk.btrace) to the 3.0 namespace (io.btrace).
#
# Rewrites both import statements and fully-qualified references in place,
# keeping a .bak backup next to every modified file. With --dry-run, prints
# the lines that would change without touching any file.
#
# Usage: migrate-btrace-script.sh [--dry-run] [-r] <file-or-directory>...
#
# Exit 0 = success, exit 2 = usage/argument error.
set -euo pipefail

OLD_PKG_RE='org\.openjdk\.btrace'
NEW_PKG='io.btrace'

DRY_RUN=0
RECURSIVE=0

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") [--dry-run] [-r] <file-or-directory>...

Rewrites org.openjdk.btrace -> io.btrace in BTrace script sources.

Options:
  --dry-run   Show what would change; do not modify any file.
  -r          Recurse into directories (collects *.java files).
  -h, --help  Show this help.

Modified files get a <file>.bak backup with the original content.
EOF
}

# --- parse options ---
ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY_RUN=1 ;;
    -r) RECURSIVE=1 ;;
    -h|--help) usage; exit 0 ;;
    --) shift; while [ $# -gt 0 ]; do ARGS+=("$1"); shift; done; break ;;
    -*) echo "ERROR: unknown option: $1" >&2; usage; exit 2 ;;
    *) ARGS+=("$1") ;;
  esac
  shift
done

if [ ${#ARGS[@]} -eq 0 ]; then
  echo "ERROR: no input files given" >&2
  usage
  exit 2
fi

# --- collect files ---
FILES=()
for arg in "${ARGS[@]}"; do
  if [ -d "$arg" ]; then
    if [ "$RECURSIVE" -ne 1 ]; then
      echo "ERROR: '$arg' is a directory (use -r to recurse)" >&2
      exit 2
    fi
    while IFS= read -r f; do
      FILES+=("$f")
    done < <(find "$arg" -type f -name '*.java' | sort)
  elif [ -f "$arg" ]; then
    FILES+=("$arg")
  else
    echo "ERROR: no such file: $arg" >&2
    exit 2
  fi
done

if [ ${#FILES[@]} -eq 0 ]; then
  echo "No .java files found." >&2
  exit 0
fi

# --- migrate ---
CHANGED=0
for f in "${FILES[@]}"; do
  if ! grep -q "$OLD_PKG_RE" "$f"; then
    echo "unchanged: $f"
    continue
  fi
  CHANGED=$((CHANGED + 1))
  if [ "$DRY_RUN" -eq 1 ]; then
    echo "would migrate: $f"
    grep -n "$OLD_PKG_RE" "$f" | sed 's/^/    /'
  else
    # Portable in-place sed (works on both GNU and BSD sed); keep the backup.
    sed -i.bak "s/${OLD_PKG_RE}/${NEW_PKG}/g" "$f"
    echo "migrated: $f (backup: $f.bak)"
    grep -n "$NEW_PKG" "$f" | sed 's/^/    /'
  fi
done

if [ "$DRY_RUN" -eq 1 ]; then
  echo "Dry run: $CHANGED file(s) would be migrated (of ${#FILES[@]} examined)."
else
  echo "Done: $CHANGED file(s) migrated (of ${#FILES[@]} examined)."
fi
