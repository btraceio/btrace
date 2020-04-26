#!/bin/bash

echo "== BTrace build config file. You should 'source config_build.sh' to set up the env variables."
echo

SDKMAN_VER=$(sdk version)
if [ $? -ne 0 ]; then
  curl -s "https://get.sdkman.io" | bash
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  SDKMAN_VER=$(sdk version)
fi

SDKMAN_BASE=$HOME/.sdkman/candidates/java

JAVA_8_VERSION="8.0.252.hs-adpt"
JAVA_9_VERSION="9.0.4-open"
JAVA_11_VERSION="11.0.6.hs-adpt"
JAVA_15_VERSION="15.ea.27-open"

echo "Using SDKMAN version: ${SDKMAN_VER}"

echo "Installing Java 8"
sdk install java $JAVA_8_VERSION 2>/dev/null || true
export JAVA_8_HOME=$SDKMAN_BASE/$JAVA_8_VERSION

echo "Installing Java 9"
sdk install java $JAVA_9_VERSION 2>/dev/null || true
export JAVA_9_HOME=$SDKMAN_BASE/$JAVA_9_VERSION

echo "Installing Java 11"
sdk install java $JAVA_11_VERSION 2>/dev/null || true
export JAVA_11_HOME=$SDKMAN_BASE/$JAVA_11_VERSION

echo "Installing Java 15"
sdk install java $JAVA_15_VERSION 2>/dev/null || true
export JAVA_15_HOME=$SDKMAN_BASE/$JAVA_15_VERSION

echo "==="
echo "All set. You can now build BTrace as './gradlew --daemon build'"
