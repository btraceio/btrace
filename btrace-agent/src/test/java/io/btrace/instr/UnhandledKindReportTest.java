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
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.BTraceRuntime;
import io.btrace.core.annotations.Kind;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.MessageCommand;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A handler whose probe kind a backend cannot instrument is reported to the client, once, and the
 * report never throws into the class-loading thread. Both backends dispatch every real kind, so the
 * backends' fall-through branches themselves cannot be reached from a test; this covers the
 * reporting they share.
 */
class UnhandledKindReportTest {

  @Test
  void theClientHearsWhichHandlerKindAndClass() {
    List<Command> sent = new ArrayList<>();

    UnhandledKindReport.report(
        probe(recordingRuntime(sent)), handler("onSync", Kind.SYNC_EXIT), "com/acme/Target", "ASM");

    assertEquals(1, sent.size(), "commands sent to the client: " + sent);
    String message = ((MessageCommand) sent.get(0)).getMessage();
    assertTrue(message.startsWith("[BTRACE WARN] Handler onSync was not applied in "), message);
    assertTrue(message.contains("com.acme.Target"), message);
    assertTrue(message.contains("the ASM instrumentation backend"), message);
    assertTrue(message.contains("probe kind SYNC_EXIT"), message);
  }

  @Test
  void aHandlerIsReportedOnceAcrossClasses() {
    List<Command> sent = new ArrayList<>();
    BTraceProbe probe = probe(recordingRuntime(sent));
    OnMethod om = handler("onLine", Kind.LINE);

    for (int i = 0; i < 3; i++) {
      UnhandledKindReport.report(probe, om, "com/acme/Target" + i, "ClassFile API");
    }

    assertEquals(1, sent.size(), "commands sent to the client: " + sent);
  }

  @Test
  void bothBackendsWordTheReportTheSameWay() {
    String asm = UnhandledKindReport.message(Kind.NEW, "h", "a.B", "ASM");
    String cf = UnhandledKindReport.message(Kind.NEW, "h", "a.B", "ClassFile API");

    assertEquals(asm.replace("ASM", "ClassFile API"), cf);
  }

  @Test
  void aFailingClientOrMissingRuntimeNeverThrows() {
    BTraceRuntime.Impl failing =
        (BTraceRuntime.Impl)
            Proxy.newProxyInstance(
                BTraceRuntime.Impl.class.getClassLoader(),
                new Class<?>[] {BTraceRuntime.Impl.class},
                (proxy, method, args) -> {
                  throw new IllegalStateException("client gone");
                });

    assertDoesNotThrow(
        () ->
            UnhandledKindReport.report(
                probe(failing), handler("a", Kind.CATCH), "com/acme/Target", "ASM"));
    assertDoesNotThrow(
        () ->
            UnhandledKindReport.report(
                probe(null), handler("b", Kind.CATCH), "com/acme/Target", "ASM"));
    assertDoesNotThrow(
        () -> UnhandledKindReport.report(null, handler("c", Kind.CATCH), "com/acme/Target", "ASM"));
  }

  private static OnMethod handler(String name, Kind kind) {
    Location loc = new Location();
    loc.setValue(kind);
    OnMethod om = new OnMethod();
    om.setClazz("com.acme.Target");
    om.setMethod("run");
    om.setTargetName(name);
    om.setLocation(loc);
    return om;
  }

  private static BTraceProbe probe(BTraceRuntime.Impl rt) {
    return (BTraceProbe)
        Proxy.newProxyInstance(
            BTraceProbe.class.getClassLoader(),
            new Class<?>[] {BTraceProbe.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "getRuntime":
                  return rt;
                case "hashCode":
                  return System.identityHashCode(proxy);
                case "equals":
                  return proxy == args[0];
                default:
                  return null;
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
}
