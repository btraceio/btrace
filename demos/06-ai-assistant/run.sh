#!/usr/bin/env bash
# Demo 06 - Let an AI assistant debug your JVM.
# Starts the checkout service and leaves it running. The rest of the demo happens in an AI coding
# host (Claude Code, Codex or Pi) with the btrace-observability plugin installed, opened in the
# checkout-service directory; the rehearsed prompts are in PROMPTS.md. To record a session
# unattended instead, use record-session.sh.
source "$(dirname "$0")/../lib/common.sh"

say "A checkout service is running. Some checkouts are slow and many orders end up in manual review, with nothing in the error logs."
start_app checkout-service/CheckoutServiceApp.java \
  -Dbtrace.permissions=checkout-service/btrace-permissions.properties -cp checkout-service/lib/acme-sdk.jar
wait_for_line "$APP_LOG" 'checkouts completed' 10
tail -n 1 "$APP_LOG"

say "The service is running as pid $APP_PID, and it stays running: the AI host does the rest."
say "Open your AI host in $DEMO_DIR/checkout-service with the btrace-observability plugin installed and follow:"
echo "$DEMO_DIR/PROMPTS.md"
say "When you are done, stop the service with: kill $APP_PID"

DEMO_KEEP=1
finish
