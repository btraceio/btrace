#!/usr/bin/env bash
# Rebuilds checkout-service/lib/acme-sdk.jar, the binary-only "vendor SDK" the demo-06 checkout
# service runs against. The JAR is checked in so the demo and its btrace-qe case need no build step;
# run this only after changing the sources under vendor/src.
set -euo pipefail
cd "$(dirname "$0")"
: "${JAVA_HOME:?JAVA_HOME must point to a JDK 11 or newer}"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
"$JAVA_HOME/bin/javac" --release 11 -d "$out" $(find src -name '*.java')
"$JAVA_HOME/bin/jar" --create --file ../checkout-service/lib/acme-sdk.jar --date=2026-10-27T00:00:00Z -C "$out" .
echo "wrote checkout-service/lib/acme-sdk.jar"
