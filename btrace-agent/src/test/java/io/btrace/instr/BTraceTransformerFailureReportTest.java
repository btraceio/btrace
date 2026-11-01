/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.BTraceRuntime;
import io.btrace.core.DebugSupport;
import io.btrace.core.SharedSettings;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.MessageCommand;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A class the transformer fails to instrument keeps its original code. The attached clients must
 * hear about it: until now the failure went only to the target JVM's log, and a probe that matched
 * the class printed nothing with no explanation.
 */
class BTraceTransformerFailureReportTest {
  // The transformer leaves io.btrace classes alone, so the target goes by another name
  private static final String TARGET = "com/acme/Target";

  static final class Target {
    void run() {}
  }

  @Test
  void failedTransformIsReportedToTheClient() throws Exception {
    List<Command> sent = new ArrayList<>();
    BTraceTransformer transformer = new BTraceTransformer(new DebugSupport(new SharedSettings()));
    transformer.register(failingProbe(TARGET.replace('/', '.'), recordingRuntime(sent)));

    byte[] result =
        transformer.transform(new ClassLoader(null) {}, TARGET, null, null, bytesOf(Target.class));

    assertNull(result, "a failed transform keeps the original class");
    assertEquals(1, sent.size(), "commands sent to the client: " + sent);
    String message = ((MessageCommand) sent.get(0)).getMessage();
    assertTrue(message.startsWith("[BTRACE WARN] "), message);
    assertTrue(message.contains("com.acme.Target"), message);
    assertTrue(message.contains("IllegalStateException: instrumentation failed"), message);
  }

  @Test
  void reportsStopAfterAFewFailures() throws Exception {
    List<Command> sent = new ArrayList<>();
    BTraceTransformer transformer = new BTraceTransformer(new DebugSupport(new SharedSettings()));
    transformer.register(failingProbe(TARGET.replace('/', '.'), recordingRuntime(sent)));

    for (int i = 0; i < 25; i++) {
      transformer.transform(new ClassLoader(null) {}, TARGET, null, null, bytesOf(Target.class));
    }

    assertEquals(BTraceTransformer.MAX_FAILURE_REPORTS, sent.size());
    String last = ((MessageCommand) sent.get(sent.size() - 1)).getMessage();
    assertTrue(last.contains("further failures"), last);
  }

  @Test
  void aFailureThatCannotBeDescribedDoesNotEscape() throws Exception {
    List<Command> sent = new ArrayList<>();
    BTraceTransformer transformer = new BTraceTransformer(new DebugSupport(new SharedSettings()));
    Throwable unprintable =
        new IllegalStateException() {
          @Override
          public String toString() {
            throw new OutOfMemoryError("while describing the failure");
          }
        };
    transformer.register(
        failingProbe(TARGET.replace('/', '.'), recordingRuntime(sent), unprintable));

    assertNull(
        transformer.transform(new ClassLoader(null) {}, TARGET, null, null, bytesOf(Target.class)));
  }

  /** A probe matching {@code className} that throws as soon as instrumentation consults it. */
  private static BTraceProbe failingProbe(String className, BTraceRuntime.Impl rt) {
    return failingProbe(className, rt, new IllegalStateException("instrumentation failed"));
  }

  private static BTraceProbe failingProbe(
      String className, BTraceRuntime.Impl rt, Throwable failure) {
    OnMethod om = new OnMethod();
    om.setClazz(className);
    om.setMethod("run");
    return (BTraceProbe)
        Proxy.newProxyInstance(
            BTraceProbe.class.getClassLoader(),
            new Class<?>[] {BTraceProbe.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "onmethods":
                  return Collections.singletonList(om);
                case "getRuntime":
                  return rt;
                case "notifyTransform":
                  return null;
                case "toString":
                  return "failingProbe";
                case "hashCode":
                  return System.identityHashCode(proxy);
                case "equals":
                  return proxy == args[0];
                default:
                  throw failure;
              }
            });
  }

  private static BTraceRuntime.Impl recordingRuntime(List<Command> sent) {
    return (BTraceRuntime.Impl)
        Proxy.newProxyInstance(
            BTraceRuntime.Impl.class.getClassLoader(),
            new Class<?>[] {BTraceRuntime.Impl.class},
            (proxy, method, args) -> {
              if (method.getName().equals("sendCommand")) {
                sent.add((Command) args[0]);
              }
              return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
            });
  }

  private static String internalName(Class<?> clz) {
    return clz.getName().replace('.', '/');
  }

  private static byte[] bytesOf(Class<?> clz) throws Exception {
    String resource = "/" + internalName(clz) + ".class";
    try (InputStream is = clz.getResourceAsStream(resource)) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      for (int n = is.read(buf); n != -1; n = is.read(buf)) {
        bos.write(buf, 0, n);
      }
      return bos.toByteArray();
    }
  }
}
