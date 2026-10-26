#!/usr/bin/env bash
# Demo 04 - See what your LLM calls cost, live.
# Attaches a probe to a running "AI ticket summarizer" and reads token totals, estimated cost and
# latency-budget violations off the btrace-llm-trace and btrace-contracts extensions.
source "$(dirname "$0")/../lib/common.sh"

say "An AI ticket summarizer calls a chat completion API. About 1 call in 12 runs away: 14x the output tokens, seconds of latency."
say "The app itself only prints 'answered N tickets'. Grant just these two extensions, for this JVM only."
start_app LlmTicketApp.java -Dbtrace.permissions=permissions.properties
wait_for_line "$APP_LOG" 'answered [0-9]+ tickets' 10
tail -n 2 "$APP_LOG"

say "What does the llm-trace extension declare?"
tool_run btracex inspect btrace-llm-trace

say "Attach the probe. It records every chat() call: tokens, latency, cost, and a 500 ms budget."
btrace_until 'VIOLATIONS: [1-9]' 40 "$APP_PID" LlmTraceProbe.java

say "Read the dashboard: token totals, estimated cost, and the latency-budget violations."
say "The violations are the runaway calls - the same ones that inflate max latency and the cost line."
finish
