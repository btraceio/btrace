#!/bin/bash

echo "== BTrace build config file. You should 'source config_build.sh' to set up the env variables."
echo

SDKMAN_VER=$(sdk version 2>/dev/null || true)
if [ -z "$SDKMAN_VER" ]; then
  curl -s "https://get.sdkman.io" | bash
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  SDKMAN_VER=$(sdk version)
fi

SDKMAN_BASE=$HOME/.sdkman/candidates/java

JAVA_8_VERSION="8.0.322-tem"
JAVA_9_VERSION="9.0.4-open"
JAVA_11_VERSION="11.0.14-tem"
JAVA_17_VERSION="17.0.2-tem"
JAVA_18_VERSION="18-amzn"

echo "Using SDKMAN version: ${SDKMAN_VER}"

rm -f .java.versions

echo "Installing Java 8"
sdk install java $JAVA_8_VERSION 2>/dev/null || true
export JAVA_8_HOME=$SDKMAN_BASE/$JAVA_8_VERSION
echo "export JAVA_8_HOME=${JAVA_8_HOME}" >> .java.versions

echo "Installing Java 9"
sdk install java $JAVA_9_VERSION 2>/dev/null || true
export JAVA_9_HOME=$SDKMAN_BASE/$JAVA_9_VERSION
echo "export JAVA_9_HOME=${JAVA_9_HOME}" >> .java.versions

echo "Installing Java 11"
sdk install java $JAVA_11_VERSION 2>/dev/null || true
export JAVA_11_HOME=$SDKMAN_BASE/$JAVA_11_VERSION
echo "export JAVA_11_HOME=${JAVA_11_HOME}" >> .java.versions

echo "Installing Java 17"
sdk install java $JAVA_17_VERSION 2>/dev/null || true
export JAVA_17_HOME=$SDKMAN_BASE/$JAVA_17_VERSION
echo "export JAVA_17_HOME=${JAVA_17_HOME}" >> .java.versions

echo "Installing Java 18"
sdk install java $JAVA_18_VERSION 2>/dev/null || true
export JAVA_18_HOME=$SDKMAN_BASE/$JAVA_18_VERSION
echo "export JAVA_18_HOME=${JAVA_18_HOME}" >> .java.versions

echo "==="
echo "Load the configured java version via 'source .java.versions'"
echo "All set. You can now build BTrace as './gradlew --daemon build'"
