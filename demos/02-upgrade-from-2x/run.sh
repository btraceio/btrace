#!/usr/bin/env bash
# Demo 02 - Upgrade from 2.x: compiled probes need nothing, sources need one line.
# A probe compiled with BTrace 2.2.6 deploys on the 3.0 agent as is; the same probe's source gets
# its org.openjdk.btrace imports rewritten by the bundled migration script.
source "$(dirname "$0")/../lib/common.sh"
REPO_ROOT="$(cd "$DEMO_DIR/../.." && pwd)"

say "The same order service as demo 01, started without any BTrace flags."
start_app ../01-oneliner/OrderServiceApp.java

say "legacy/SlowChargeProbe.class was compiled with BTrace 2.2.6 (org.openjdk.btrace). Deploy it on 3.0 as is."
show "strings legacy/SlowChargeProbe.class | grep -m1 org/openjdk/btrace"
strings legacy/SlowChargeProbe.class | grep -m1 org/openjdk/btrace | sed 's/^/  /'
btrace_until 'slow chargeCard' 40 "$APP_TARGET" legacy/SlowChargeProbe.class

say "The agent rewrote the org/openjdk/btrace references in memory when it loaded the class. Nothing to recompile."

say "Sources are different: a 2.x source file does not compile against 3.0. Preview the one-line fix."
WORK="$DEMO_LOG_DIR/migrated"; rm -rf "$WORK"; mkdir -p "$WORK"
cp legacy/SlowChargeProbe.java "$WORK/"
tool_run "$REPO_ROOT/scripts/migrate-btrace-script.sh" --dry-run "$WORK/SlowChargeProbe.java"

say "Apply it, then deploy the migrated source the normal 3.0 way."
tool_run "$REPO_ROOT/scripts/migrate-btrace-script.sh" "$WORK/SlowChargeProbe.java"
tool_run grep -n 'import' "$WORK/SlowChargeProbe.java"
btrace_until 'slow chargeCard' 40 "$APP_TARGET" "$WORK/SlowChargeProbe.java"

say "Same probe, same output, io.btrace imports. Exit retracts it."
finish
