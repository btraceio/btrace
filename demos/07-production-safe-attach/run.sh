#!/usr/bin/env bash
# Demo 07 - Attach to production without fear.
# The JVM starts with the agent in prepared mode (loopback, token-protected endpoint). `btrace
# doctor` checks readiness without loading anything, the client finds the token through the Attach
# API, and when the client exits the probe's call sites become no-ops.
source "$(dirname "$0")/../lib/common.sh"
PORT_OPT=()   # prepared mode with port=0: the client discovers the port through the Attach API

say "A hot loop is running with the agent prepared at launch: port=0, loopback, token-protected."
start_app HotLoopApp.java "-javaagent:$BTRACE_HOME/libs/btrace.jar=port=0"
wait_for_line "$APP_LOG" 'calls/s' 15
sleep 2
wait_for_line "$APP_LOG" 'calls/s.*' 15
sleep 2
say "Baseline throughput, measured by the app itself:"
tail -n 2 "$APP_LOG"
BASELINE="$(grep -o '[0-9.]* M calls/s' "$APP_LOG" | tail -n 1 | cut -d' ' -f1)"

say "Ask the doctor first. It reads JVM properties through the Attach API and loads nothing."
show "btrace doctor $APP_PID"
(cd "$DEMO_DIR" && "$BTRACE_HOME/bin/btrace" doctor "$APP_PID" 2>&1)
echo "exit code: $?"

say "For contrast: the same app in a plain JVM, started without the agent."
PLAIN_LOG="$DEMO_LOG_DIR/plain.log"
(cd "$DEMO_DIR" && exec "$JAVA_HOME/bin/java" HotLoopApp.java) >"$PLAIN_LOG" 2>&1 &
PLAIN_PID=$!
wait_for_line "$PLAIN_LOG" '\[demo\]' 30
show "btrace doctor $PLAIN_PID"
(cd "$DEMO_DIR" && "$BTRACE_HOME/bin/btrace" doctor "$PLAIN_PID" 2>&1)
echo "exit code: $?"
kill "$PLAIN_PID" 2>/dev/null; wait "$PLAIN_PID" 2>/dev/null

say "Attach with no flags: the client finds the endpoint and token through the Attach API."
say "The probe handler is empty, so any slowdown is pure call-site dispatch."
btrace_until '\[probe\] attached' 30 "$APP_TARGET" HotPathProbe.java
say "Throughput while the probe was attached:"
tail -n 3 "$APP_LOG"

say "The client has exited: the agent restored the original bytecode. Watch the app's own number recover."
RECOVERED=""
for _ in $(seq 1 30); do
  sleep 1
  LAST="$(grep -o '[0-9.]* M calls/s' "$APP_LOG" | tail -n 1 | cut -d' ' -f1)"
  if awk -v a="$LAST" -v b="$BASELINE" 'BEGIN { exit !(a >= b * 0.7) }'; then RECOVERED=1; break; fi
done
tail -n 3 "$APP_LOG"
if [ -n "$RECOVERED" ]; then
  say "Back at baseline (${LAST} vs ${BASELINE} M calls/s before). Nothing is left behind in the JVM."
else
  echo "[demo] throughput did not recover within 30s (last ${LAST}, baseline ${BASELINE})" >&2
  BTRACE_DEMO_FAILED=1
fi
finish
