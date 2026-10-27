#!/usr/bin/env bash
# Demo 06 - record a real AI-assistant session that debugs the checkout service through BTrace's
# MCP server, then render it as a chat-style GIF and MP4.
#
#   AGENT_PLUGINS_DIR=~/src/agent-plugins ./record-session.sh
#
# Needs the `claude` CLI (logged in), JBang, Python 3 with Pillow (and ffmpeg for the MP4), and a
# checkout of github.com/btraceio/agent-plugins. The session runs headless (`claude -p`) from a
# scratch copy of the service's project: its source and the binary-only ACME SDK it calls. Only the
# btrace-observability plugin is loaded, its BTrace MCP server is the only MCP server, and only
# that server's tools and read-only file tools are allowed, so the assistant can read the service's
# code but has to probe the live JVM for anything that happens inside the SDK. The raw transcript stays in the demo's log directory; the copy kept
# as session.jsonl goes through ../lib/redact-transcript.py first, which turns home and temp paths
# into ~ and /tmp and drops JVMs the session never probed or mentioned. Render it again without
# re-recording with:
#
#   python3 ../lib/render-chat.py session.jsonl ../../docs/tutorials/images/06-ai-assistant \
#     "claude  —  checkout service on call"
#
# The assistant is non-deterministic. Read the transcript before publishing it, against the
# "Do not record if" rules in PROMPTS.md.
source "$(dirname "$0")/../lib/common.sh"

AGENT_PLUGINS_DIR="${AGENT_PLUGINS_DIR:-$DEMO_DIR/../../../agent-plugins}"
PLUGIN_DIR="$AGENT_PLUGINS_DIR/plugins/btrace-observability"
MODEL="${DEMO_MODEL:-sonnet}"
PYTHON="${PYTHON:-python3}"
[ -f "$PLUGIN_DIR/.claude-plugin/plugin.json" ] || {
  echo "btrace-observability plugin not found under $AGENT_PLUGINS_DIR; set AGENT_PLUGINS_DIR" >&2
  exit 2
}
command -v claude >/dev/null || { echo "the claude CLI is not on PATH" >&2; exit 2; }

PROMPT_1="Customers report that checkout is sometimes slow, and that some orders never complete, \
but nothing shows up in the logs. The checkout service is running on this machine and we cannot \
restart it; its source is in this directory. Find out what is going on. Keep any probe narrow, \
and leave nothing attached when you are done."
PROMPT_2="Double-check that no BTrace probe is still running on the checkout service."

# The assistant's project: the service source and the SDK JAR, nothing else from this repository.
WORK_DIR="$DEMO_LOG_DIR/checkout-service"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/lib"
cp checkout-service/CheckoutServiceApp.java "$WORK_DIR/"
cp checkout-service/lib/acme-sdk.jar "$WORK_DIR/lib/"

APP_LOG="$DEMO_LOG_DIR/app.log"
: > "$APP_LOG"
show "java -cp lib/acme-sdk.jar CheckoutServiceApp.java"
(cd "$WORK_DIR" && exec "$JAVA_HOME/bin/java" -cp lib/acme-sdk.jar CheckoutServiceApp.java) >"$APP_LOG" 2>&1 &
APP_PID=$!
wait_for_line "$APP_LOG" 'checkouts completed' 30 || { echo "the service did not start; see $APP_LOG" >&2; exit 1; }
echo "[demo app] pid $APP_PID"

# Only the BTrace MCP server: --strict-mcp-config keeps out every other server the CLI would load,
# including connectors attached to the claude.ai account.
MCP_CONFIG="$DEMO_LOG_DIR/mcp.json"
cat > "$MCP_CONFIG" <<JSON
{"mcpServers": {"btrace": {"command": "jbang", "args": ["$PLUGIN_DIR/mcp/src/main/java/io/btrace/mcp/BTraceMcpServer.java"]}}}
JSON
# Build the server once up front, so it is connected before the first prompt.
jbang build "$PLUGIN_DIR/mcp/src/main/java/io/btrace/mcp/BTraceMcpServer.java" >/dev/null 2>&1 || {
  echo "could not build the BTrace MCP server with JBang" >&2
  exit 2
}

RAW="$DEMO_LOG_DIR/session.raw.jsonl"
OUT="$DEMO_DIR/session.jsonl"
: > "$RAW"

ask() {
  local resume=()
  [ -n "${SESSION_ID:-}" ] && resume=(--resume "$SESSION_ID")
  printf '{"type":"demo_prompt","text":%s}\n' \
    "$("$PYTHON" -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$1")" >> "$RAW"
  (cd "$WORK_DIR" && claude -p "$1" \
      ${resume[@]+"${resume[@]}"} \
      --model "$MODEL" \
      --plugin-dir "$PLUGIN_DIR" \
      --strict-mcp-config --mcp-config "$MCP_CONFIG" \
      --setting-sources "" \
      --tools "Read,Grep,Glob" \
      --allowedTools "Read,Grep,Glob,mcp__btrace" \
      --permission-mode dontAsk \
      --output-format stream-json --verbose < /dev/null) | tee -a "$RAW" | "$PYTHON" -c '
import json, sys
for line in sys.stdin:
    try:
        ev = json.loads(line)
    except ValueError:
        continue
    if ev.get("type") == "assistant":
        for block in ev["message"].get("content", []):
            if block.get("type") == "tool_use":
                print("  tool:", block["name"].split("__")[-1], json.dumps(block.get("input"))[:200])
            elif block.get("type") == "text":
                print("  says:", block["text"][:200].replace("\n", " "))
    elif ev.get("type") == "result":
        print("  (turns: %s, cost: %s USD)" % (ev.get("num_turns"), ev.get("total_cost_usd")))
' >&2
  SESSION_ID="$("$PYTHON" -c '
import json, sys
sid = ""
for line in open(sys.argv[1]):
    try:
        sid = json.loads(line).get("session_id") or sid
    except ValueError:
        pass
print(sid)' "$RAW")"
}

say "Recording an assistant session against pid $APP_PID (model: $MODEL)"
ask "$PROMPT_1"
ask "$PROMPT_2"

# Keep only a redacted transcript in the repository: no home or temp paths, no unrelated JVMs.
"$PYTHON" "$DEMO_DIR/../lib/redact-transcript.py" "$RAW" "$OUT" --home "$HOME" --tmp "${TMPDIR:-/tmp}"
say "Transcript: $OUT (raw: $RAW)"
if "$PYTHON" -c 'import PIL' 2>/dev/null; then
  "$PYTHON" "$DEMO_DIR/../lib/render-chat.py" "$OUT" "$DEMO_DIR/../../docs/tutorials/images/06-ai-assistant" \
    "claude  —  checkout service on call"
else
  echo "Pillow is not installed for $PYTHON; skipping the render" >&2
fi
finish
