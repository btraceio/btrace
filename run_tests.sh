#! /bin/bash

set -e

export JAVA_HOME=$JAVA_8_HOME
echo "Building BTrace binary artifacts"
./gradlew -x test :btrace-dist:build

mkdir -p build/reports
for VERSION in 8 11 17 18 19 20; do
  declare home_string=JAVA_${VERSION}_HOME
  if [ -z "${!home_string}" ]; then
    echo "Skipping test for Java ${VERSINO}"
    continue
  fi
  echo "Running tests with TEST_JAVA_HOME=${!home_string}"
  TEST_JAVA_HOME=${!home_string} BTRACE_TEST_DEBUG="true" ./gradlew test | tee -a build/reports/test_java_$VERSION.out  || true
done
