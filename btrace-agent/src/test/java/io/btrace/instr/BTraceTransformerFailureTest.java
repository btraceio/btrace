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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
 * A failure inside the transformer must not propagate into the JVM's class loading and
 * retransformation machinery: {@code transform} reports it and returns {@code null}, which means
 * "keep the class as it was".
 */
class BTraceTransformerFailureTest {
  // The transformer leaves io.btrace classes alone, so the target goes by another name
  private static final String TARGET = "com/acme/Target";

  static final class Target {
    void run() {}
  }

  @Test
  void transformReturnsNullAndReportsWhenTheBackendThrows() throws Exception {
    List<Command> sent = new ArrayList<>();
    BTraceTransformer transformer = new BTraceTransformer(new DebugSupport(new SharedSettings()));
    transformer.register(throwingProbe(recordingRuntime(sent)));
    byte[] original = bytesOf(Target.class);

    byte[] result =
        assertDoesNotThrow(
            () -> transformer.transform(new ClassLoader(null) {}, TARGET, null, null, original),
            "a failure must not escape into class loading");

    assertNull(result, "a failed transform keeps the original class");
    // The probe throws only once instrumentation consults it, so the report carrying its
    // exception shows the transform got past the filters into the instrumentation step
    assertEquals(1, sent.size(), "commands sent to the client: " + sent);
    String message = ((MessageCommand) sent.get(0)).getMessage();
    assertTrue(message.contains("com.acme.Target"), message);
    assertTrue(message.contains("IllegalStateException: instrumentation failed"), message);
  }

  /** A probe matching the target class that throws as soon as instrumentation consults it. */
  private static BTraceProbe throwingProbe(BTraceRuntime.Impl rt) {
    OnMethod om = new OnMethod();
    om.setClazz(TARGET.replace('/', '.'));
    om.setMethod("run");
    List<OnMethod> oms = Collections.singletonList(om);
    return (BTraceProbe)
        Proxy.newProxyInstance(
            BTraceProbe.class.getClassLoader(),
            new Class<?>[] {BTraceProbe.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "onmethods":
                  return oms;
                case "getRuntime":
                  return rt;
                case "notifyTransform":
                  return null;
                case "toString":
                  return "throwingProbe";
                case "hashCode":
                  return System.identityHashCode(proxy);
                case "equals":
                  return proxy == args[0];
                default:
                  throw new IllegalStateException("instrumentation failed");
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

  private static byte[] bytesOf(Class<?> clz) throws Exception {
    try (InputStream is =
        clz.getResourceAsStream("/" + clz.getName().replace('.', '/') + ".class")) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      for (int n = is.read(buf); n != -1; n = is.read(buf)) {
        bos.write(buf, 0, n);
      }
      return bos.toByteArray();
    }
  }
}
