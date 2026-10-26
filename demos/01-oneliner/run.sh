#!/usr/bin/env bash
# Demo 01 - Your first trace in one line.
# Attaches to a live JVM with oneliners and finds a latency bug and a swallowed exception,
# without a script file, a compile step, a restart, or a code change.
source "$(dirname "$0")/../lib/common.sh"

say "An order service is running. It says some orders fail and some are slow, not why."
start_app OrderServiceApp.java
wait_for_line "$APP_LOG" 'processed [0-9]+ orders' 10
tail -n 2 "$APP_LOG"

say "Watch one method, live. No file, no compile, no restart."
btrace_until 'execution time' 30 -n 'OrderService::processOrder @return { print method, time }' "$APP_PID"

say "Most orders are fast, some take 300 ms+. Widen the net and keep only the slow ones."
btrace_until '^chargeCard' 40 -n 'OrderService::* @return if duration>200ms { print method, time }' "$APP_PID"

say "chargeCard is the culprit. Now the failures: hook the error path of validateOrder."
btrace_until 'OrderService\.processOrder\(OrderServiceApp\.java' 40 -n 'OrderService::validateOrder @error { print method, stack }' "$APP_PID"

say "Exit retracts the probe: its hooks become no-ops, nothing lingers in the JVM."
finish
