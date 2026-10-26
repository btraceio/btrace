#!/usr/bin/env bash
# Demo 06 - Let an AI assistant debug your JVM.
# Starts the order service from demo 01 and leaves it running. The rest of the demo happens in an
# AI coding host (Claude Code, Codex or Pi) with the btrace-observability plugin installed; the
# rehearsed prompts are in PROMPTS.md.
source "$(dirname "$0")/../lib/common.sh"

say "An order service is running. Some orders are slow and some fail silently. We will not trace it by hand."
start_app ../01-oneliner/OrderServiceApp.java
wait_for_line "$APP_LOG" 'processed [0-9]+ orders' 10
tail -n 2 "$APP_LOG"

say "The demo app is running as pid $APP_PID, and it stays running: the AI host does the rest."
say "Open your AI host with the btrace-observability plugin installed and follow:"
echo "$DEMO_DIR/PROMPTS.md"
say "When you are done, stop the app with: kill $APP_PID"

DEMO_KEEP=1
finish
