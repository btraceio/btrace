#! /bin/bash

./gradlew check -DexcludeBenchmark
TEST_JAVA_HOME=$JAVA_8_HOME ./gradlew test --tests  org.openjdk.btrace.BTraceFunctionalTests
TEST_JAVA_HOME=$JAVA_9_HOME ./gradlew test --tests  org.openjdk.btrace.BTraceFunctionalTests