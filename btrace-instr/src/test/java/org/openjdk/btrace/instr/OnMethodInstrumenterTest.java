/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openjdk.btrace.instr;

import java.lang.reflect.Field;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Jaroslav Bachorik
 */
public class OnMethodInstrumenterTest extends InstrumentorTestBase {
  private static final Map<String, String> targetClassMap = new HashMap<>();
  private static final Map<String, Boolean> verifyFlagMap = new HashMap<>();

  private static Field instrHiddenClassesFlagFld = null;

  static {
    targetClassMap.put("onmethod/MatchDerived", "DerivedClass");
    targetClassMap.put("issues/BTRACE22", "issues/BTRACE22");
    targetClassMap.put("issues/BTRACE28", "issues/BTRACE28");
    targetClassMap.put("issues/BTRACE53", "DerivedClass");
    targetClassMap.put("issues/BTRACE87", "issues/BTRACE87");
    targetClassMap.put("issues/BTRACE106", "issues/BTRACE106");
    targetClassMap.put("issues/BTRACE189", "Main");
    targetClassMap.put("issues/BTRACE256", "issues/BTRACE256");
    targetClassMap.put("issues/TezSplitter", "classdata/TezSplitter");
    targetClassMap.put("issues/InterestingVarsTest", "InterestingVarsClass");

    verifyFlagMap.put("issues/TezSplitter", Boolean.FALSE);
    verifyFlagMap.put("ServicesTest", Boolean.FALSE);
  }

  private static String getTargetClass(String name) {
    return targetClassMap.getOrDefault(name, "OnMethodTest");
  }

  private static Boolean getVerifyFlag(String name) {
    return verifyFlagMap.getOrDefault(name, Boolean.TRUE);
  }

  @BeforeAll
  public static void classSetup() throws Exception {
    Field f = RandomIntProvider.class.getDeclaredField("useBtraceEnter");
    f.setAccessible(true);
    f.setBoolean(null, false);

    instrHiddenClassesFlagFld = Instrumentor.class.getDeclaredField("useHiddenClassesInTest");
    instrHiddenClassesFlagFld.setAccessible(true);
  }

  @ParameterizedTest
  @MethodSource("listTransformations")
  void testTransformation(
      String trace, String targetClass, boolean verify, boolean useHiddenClasses) throws Exception {
    instrHiddenClassesFlagFld.set(null, useHiddenClasses);
    loadTargetClass(targetClass);
    transform(trace);

    checkTransformation((useHiddenClasses ? "dynamic" : "static") + "/" + trace, verify);
  }

  @SuppressWarnings("resource")
  private static Stream<Arguments> listTransformations() throws Exception {
    Path root = Paths.get("./build/classes/traces");
    return Files.walk(root, FileVisitOption.FOLLOW_LINKS)
        .filter(Files::isRegularFile)
        .map(root::relativize)
        .map(Path::toString)
        .map(p -> p.replace(".class", ""))
        .flatMap(
            p ->
                Stream.of(
                    Arguments.of(
                        Named.of("Trace: " + p, p),
                        Named.of("Target Class: " + getTargetClass(p), getTargetClass(p)),
                        Named.of("Verify: " + getVerifyFlag(p), getVerifyFlag(p)),
                        Named.of("Dispatcher: INVOKESTATIC", false)),
                    Arguments.of(
                        Named.of("Trace: " + p, p),
                        Named.of("Target Class: " + getTargetClass(p), getTargetClass(p)),
                        Named.of("Verify: " + getVerifyFlag(p), getVerifyFlag(p)),
                        Named.of("Dispatcher: INVOKEDYNAMIC", true))));
  }
}
