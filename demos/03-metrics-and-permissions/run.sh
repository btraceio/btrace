#!/usr/bin/env bash
# Demo 03 - Percentiles from an extension, and what happens when policy blocks it.
# btrace-metrics (HdrHistogram) replaces the removed Aggregations API. It is a privileged
# extension: blocked until the agent-side policy allows it, and the block is explained, not silent.
source "$(dirname "$0")/../lib/common.sh"

say "The order service again, started plain: no BTrace flags, no permission policy."
start_app ../01-oneliner/OrderServiceApp.java

say "LatencyHistogram.java records p50/p95/p99 through the btrace-metrics extension. Deploy it."
btrace_until 'optional service unavailable' 30 "$APP_TARGET" LatencyHistogram.java

say "The probe ran, the service was a throwing stub. Ask the agent which extensions failed and why."
btrace_run -le "$APP_TARGET"

say "Privileged extensions link only when allowed. Restart the app with a policy file that allows btrace-metrics."
stop_app
tool_run cat permissions.properties
start_app ../01-oneliner/OrderServiceApp.java -Dbtrace.permissions=permissions.properties

say "Same probe, now with the real HdrHistogram behind it."
btrace_until 'p99=' 40 "$APP_TARGET" LatencyHistogram.java

say "chargeCard's p95 is what drags processOrder's p95. Exit retracts the probe."
finish
