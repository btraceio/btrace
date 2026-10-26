#!/usr/bin/env bash
# Demo 05 - RAG and inference observability.
# Attaches to a running RAG answerer and finds retrievals that silently return nothing, using the
# bundled btrace-rag-quality and btrace-gpu-bridge extensions.
source "$(dirname "$0")/../lib/common.sh"

say "A RAG answerer is running. Some answers come back hollow; nobody knows the retrieval is returning nothing."
start_app RagSearchApp.java -Dbtrace.permissions=permissions.properties
wait_for_line "$APP_LOG" 'answered [0-9]+ questions' 15
tail -n 2 "$APP_LOG"

say "Attach a probe that feeds the vector store and the embedding model into two extensions."
btrace_until 'empty retrievals: [1-9]' 45 "$APP_PID" AiWorkloadProbe.java

say "The dashboard shows retrieval counts, top scores and embedding latency, and the empty-retrieval counter is climbing: about 1 query in 10 gets no context at all."
finish
