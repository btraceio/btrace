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

JAVA_8_VERSION="8.0.332-tem"
JAVA_11_VERSION="11.0.15-tem"
JAVA_17_VERSION="17.0.3-tem"
JAVA_18_VERSION="18.0.1-tem"
JAVA_19_VERSION="19.ea.29-open"
JAVA_20_VERSION="20.ea.4-open"

echo "Using SDKMAN version: ${SDKMAN_VER}"

rm -f .java.versions

echo "Installing Java 8"
sdk install java $JAVA_8_VERSION 2>/dev/null || true
export JAVA_8_HOME=$SDKMAN_BASE/$JAVA_8_VERSION
echo "export JAVA_8_HOME=${JAVA_8_HOME}" >> .java.versions

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

echo "Installing Java 19"
sdk install java $JAVA_19_VERSION 2>/dev/null || true
export JAVA_19_HOME=$SDKMAN_BASE/$JAVA_19_VERSION
echo "export JAVA_19_HOME=${JAVA_19_HOME}" >> .java.versions

echo "Installing Java 20"
sdk install java $JAVA_20_VERSION 2>/dev/null || true
export JAVA_20_HOME=$SDKMAN_BASE/$JAVA_20_VERSION
echo "export JAVA_20_HOME=${JAVA_20_HOME}" >> .java.versions

echo "==="
echo "Load the configured java version via 'source .java.versions'"
echo "All set. You can now build BTrace as './gradlew --daemon build'"
