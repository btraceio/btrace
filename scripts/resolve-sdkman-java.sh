#!/usr/bin/env bash
# Resolves a CI JDK lane spec to a concrete SDKMAN java identifier.
#
#   scripts/resolve-sdkman-java.sh <spec>
#
# spec forms:
#   <full identifier>   e.g. 25.0.3-tem   -> printed unchanged; `sdk install` fails loudly if SDKMAN
#                                            does not carry it
#   <major>             e.g. 27           -> newest GA build of that major that SDKMAN lists,
#                                            preferring Temurin, then Oracle JDK, then the java.net
#                                            build (last: java.net GA builds such as 21.0.2-open are
#                                            frozen at GA and never receive updates)
#
# Early-access lanes (`<major>-ea`) are not SDKMAN lanes: SDKMAN publishes java.net EA builds late
# and retires them at GA, so the workflows install those with actions/setup-java (Temurin EA) and
# never call this script for them.
#
# The identifier list is the SDKMAN API's list for linuxx64 (the endpoint
# scripts/update-jdk-versions.sh already uses), with `sdk list java` as a fallback when the API is
# unreachable. Both show one build per vendor and major and omit vendors whose newest build SDKMAN
# has not ingested yet (on 2026-09-19 neither listed any Temurin 8, 11 or 21 build although the
# pinned 8.0.492-tem, 11.0.31-tem and 21.0.11-tem still installed), so `<major>` can only pick
# among listed builds; the CI workflows therefore use this script only for majors that
# actions/setup-java cannot serve yet. Set SDKMAN_JAVA_IDENTIFIERS (newline-separated) to bypass
# both sources, e.g. in tests. Exits 1 with a message on stderr when nothing matches, so a lane
# fails loudly instead of installing the wrong JDK.
set -euo pipefail

SDKMAN_API=${SDKMAN_API:-https://api.sdkman.io/2/candidates/java/linuxx64/versions/all}
IDENTIFIER_RE='^[0-9]+(\.[0-9]+)*(\.ea\.[0-9]+)?-[a-z]+$'

spec=${1:-}
[[ -n "$spec" ]] || { echo "usage: $0 <spec>" >&2; exit 64; }

if [[ "$spec" =~ ^[0-9]+-ea$ ]]; then
  echo "Lane spec '${spec}' is an early-access lane: the workflows install it with actions/setup-java," \
    "not through SDKMAN." >&2
  exit 64
fi

if [[ "$spec" =~ $IDENTIFIER_RE ]]; then
  echo "$spec"
  exit 0
fi

if [[ ! "$spec" =~ ^([0-9]+)$ ]]; then
  echo "Unsupported lane spec '${spec}': expected a full SDKMAN identifier (e.g. 25.0.3-tem) or a major" \
    "version (e.g. 27)." >&2
  exit 64
fi
major=${BASH_REMATCH[1]}

identifiers() {
  if [[ -n "${SDKMAN_JAVA_IDENTIFIERS:-}" ]]; then
    printf '%s\n' "$SDKMAN_JAVA_IDENTIFIERS"
    return
  fi
  local all
  if all=$(curl -sf --max-time 30 "$SDKMAN_API") && [[ -n "$all" ]]; then
    tr ',' '\n' <<<"$all"
    return
  fi
  echo "warning: ${SDKMAN_API} is unreachable; falling back to 'sdk list java' (a curated subset)" >&2
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

ids=$(identifiers | grep -E "$IDENTIFIER_RE" | sort -u || true)

match=""
for vendor in tem oracle open; do
  match=$(grep -E "^${major}(\.[0-9]+)*-${vendor}$" <<<"$ids" | sort -V | tail -1 || true)
  [[ -n "$match" ]] && break
done

if [[ -z "$match" ]]; then
  echo "No SDKMAN java GA build of ${major} (Temurin, Oracle or java.net) is listed. Known identifiers:" >&2
  printf '  %s\n' $ids >&2
  exit 1
fi
echo "$match"
