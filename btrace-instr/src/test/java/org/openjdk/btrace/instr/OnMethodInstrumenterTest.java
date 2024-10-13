/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
  void testTransformation(String trace, String targetClass, boolean verify, boolean useHiddenClasses) throws Exception {
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
              .flatMap(p ->
                Stream.of(
                        Arguments.of(Named.of("Trace: " + p, p), Named.of("Target Class: " + getTargetClass(p), getTargetClass(p)), Named.of("Verify: " + getVerifyFlag(p), getVerifyFlag(p)), Named.of("Dispatcher: INVOKESTATIC", false)),
                        Arguments.of(Named.of("Trace: " + p, p), Named.of("Target Class: " + getTargetClass(p), getTargetClass(p)), Named.of("Verify: " + getVerifyFlag(p), getVerifyFlag(p)), Named.of("Dispatcher: INVOKEDYNAMIC", true))
                )
              );
  }

}
