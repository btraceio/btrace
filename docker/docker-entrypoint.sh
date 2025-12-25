#!/bin/bash
set -e

# BTrace Docker Entrypoint Script
# Initializes environment and runs BTrace commands

# Print version information if verbose
if [ "${BTRACE_VERBOSE:-false}" = "true" ]; then
    echo "BTrace Docker Image"
    echo "BTRACE_HOME: ${BTRACE_HOME:-/opt/btrace}"
    echo "JAVA_HOME: ${JAVA_HOME}"
    java -version
fi

# Validate Java installation
if ! command -v java &> /dev/null; then
    echo "ERROR: Java not found in PATH"
    exit 1
fi

# Initialize BTRACE_HOME if not set
if [ -z "$BTRACE_HOME" ]; then
    export BTRACE_HOME=/opt/btrace
fi

# Verify BTrace installation
if [ ! -d "$BTRACE_HOME" ]; then
    echo "ERROR: BTRACE_HOME directory not found: $BTRACE_HOME"
    exit 1
fi

# Add BTrace bin to PATH if not already there
if [[ ":$PATH:" != *":$BTRACE_HOME/bin:"* ]]; then
    export PATH="$PATH:$BTRACE_HOME/bin"
fi

# If first argument is a btrace command, execute it directly
if [ "${1#-}" != "$1" ] || [ "$1" = "btrace" ] || [ "$1" = "btracer" ] || [ "$1" = "btracec" ] || [ "$1" = "btracep" ]; then
    exec "$@"
fi

# If first argument is a shell or starts with /, execute as-is
if [ "$1" = "/bin/bash" ] || [ "$1" = "/bin/sh" ] || [ "${1:0:1}" = "/" ]; then
    exec "$@"
fi

# Default: execute the provided command
exec "$@"
