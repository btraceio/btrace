#! /bin/bash

set -e

JAVA_HOME=$JAVA_8_HOME
echo "Building BTrace binary artifacts"
./gradlew -x test :btrace-dist:build

for VERSION in 8 9 11 15 16 17; do
  declare home_string=JAVA_${VERSION}_HOME
  if [ -z "${!home_string}" ]; then
    echo "Skipping test for Java ${VERSINO}"
    continue
  fi
  echo "Running tests with TEST_JAVA_HOME=${!home_string}"
  TEST_JAVA_HOME=${!home_string} BTRACE_TEST_DEBUG="true" ./gradlew test
done
