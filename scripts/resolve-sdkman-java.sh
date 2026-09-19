#!/usr/bin/env bash
# Resolves a CI JDK lane spec to a concrete SDKMAN java identifier.
#
#   scripts/resolve-sdkman-java.sh <spec>
#
# spec forms:
#   <full identifier>   e.g. 25.0.3-tem       -> used as is when SDKMAN lists it
#   <major>             e.g. 27               -> newest GA build of that major, preferring
#                                                Temurin, then the OpenJDK (java.net) build,
#                                                then Oracle JDK
#   <major>-ea          e.g. 28-ea            -> newest early-access build of that major
#                                                (the N.ea.M-open java.net builds)
#
# Identifiers come from `sdk list java` (SDKMAN must be initialised in the calling shell or
# installed under $HOME/.sdkman); set SDKMAN_JAVA_IDENTIFIERS (newline-separated) to bypass
# SDKMAN, e.g. in tests. Exits 1 with a message on stderr when nothing matches, so a lane fails
# loudly instead of installing the wrong JDK.
set -euo pipefail

spec=${1:-}
[[ -n "$spec" ]] || { echo "usage: $0 <spec>" >&2; exit 64; }

identifiers() {
  if [[ -n "${SDKMAN_JAVA_IDENTIFIERS:-}" ]]; then
    printf '%s\n' "$SDKMAN_JAVA_IDENTIFIERS"
    return
  fi
  if ! command -v sdk >/dev/null 2>&1; then
    # sdkman-init.sh reads unset variables such as ZSH_VERSION, so it must not run under `set -u`
    set +u
    # shellcheck disable=SC1091
    source "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh"
    set -u
  fi
  set +u
  sdk list java 2>/dev/null | tr ' |' '\n\n'
  set -u
}

ids=$(identifiers | grep -E '^[0-9]+(\.[0-9]+)*(\.ea\.[0-9]+)?-[a-z]+$' | sort -u || true)

if grep -qx -- "$spec" <<<"$ids"; then
  echo "$spec"
  exit 0
fi

if [[ "$spec" =~ ^([0-9]+)-ea$ ]]; then
  major=${BASH_REMATCH[1]}
  match=$(grep -E "^${major}\.ea\.[0-9]+-open$" <<<"$ids" | sort -t. -k3,3n | tail -1 || true)
elif [[ "$spec" =~ ^([0-9]+)$ ]]; then
  major=${BASH_REMATCH[1]}
  match=""
  for vendor in tem open oracle; do
    match=$(grep -E "^${major}(\.[0-9]+)*-${vendor}$" <<<"$ids" | sort -V | tail -1 || true)
    [[ -n "$match" ]] && break
  done
else
  match=""
fi

if [[ -z "$match" ]]; then
  echo "No SDKMAN java identifier matches '${spec}'. Known identifiers:" >&2
  printf '  %s\n' $ids >&2
  exit 1
fi
echo "$match"
