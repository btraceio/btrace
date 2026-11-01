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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.BTraceRuntime;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.MessageCommand;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A handler whose unannotated parameters fit no matching method is never applied, which used to
 * look exactly like a method that never ran.
 */
class HandlerMismatchReportTest extends InstrumentorTestBase {
  private final List<Command> sent = new ArrayList<>();
  private boolean failSends = false;

  @Test
  void mismatchedHandlerIsReportedOncePerClass() throws Exception {
    loadTargetClass("OnMethodTest");
    BTraceProbe probe = recordingProbe("onmethod/ArgsMismatch");

    instrument(probe);
    instrument(probe);

    assertEquals(1, sent.size(), "commands sent to the client: " + sent);
    String message = ((MessageCommand) sent.get(0)).getMessage();
    assertTrue(message.startsWith("[BTRACE WARN] "), message);
    assertTrue(message.contains("args"), message);
    assertTrue(message.contains("java.util.Date"), message);
    assertTrue(message.contains("resources.OnMethodTest"), message);
  }

  @Test
  void appliedHandlerIsNotReported() throws Exception {
    loadTargetClass("OnMethodTest");

    instrument(recordingProbe("onmethod/Args"));

    assertTrue(sent.isEmpty(), "commands sent to the client: " + sent);
  }

  @Test
  void aBadAnnotatedParameterIsNamedInTheReport() throws Exception {
    loadTargetClass("OnMethodTest");

    instrument(recordingProbe("onmethod/ArgsBadSelf"));

    assertEquals(1, sent.size(), "commands sent to the client: " + sent);
    String message = ((MessageCommand) sent.get(0)).getMessage();
    assertTrue(message.contains("@Self"), message);
  }

  @Test
  void passingValidationWithoutInjectingAnythingStillCountsAsNotApplied() throws Exception {
    loadTargetClass("CallInBranchTarget");

    // The constructor call passes validation but gets no code before it; intArg(int) is rejected
    instrument(recordingProbe("onmethod/CallBeforeCtorOrMismatch"));

    assertEquals(1, sent.size(), "commands sent to the client: " + sent);
  }

  @Test
  void aFailedReportKeepsTheInstrumentationOfOtherHandlers() throws Exception {
    loadTargetClass("OnMethodTest");
    failSends = true;

    instrument(recordingProbe("onmethod/ArgsMismatch"));
  }

  @Test
  void reportsStopAfterAFewClasses() {
    OnMethod om = new OnMethod();
    int reported = 0;
    String last = null;
    for (int i = 0; i < 20; i++) {
      String suffix = om.mismatchReportSuffix("com.acme.C" + i);
      if (suffix != null) {
        reported++;
        last = suffix;
      }
    }
    assertEquals(OnMethod.MAX_MISMATCH_REPORTS, reported);
    assertTrue(last.contains("further classes"), last);
    assertEquals(null, om.mismatchReportSuffix("com.acme.C0"), "a retransform stays quiet");
  }

  private void instrument(BTraceProbe probe) throws Exception {
    ClassLoader cl = HandlerMismatchReportTest.class.getClassLoader();
    BTraceClassReader cr = InstrumentUtils.newClassReader(cl, originalBC);
    BTraceClassWriter cw = InstrumentUtils.newClassWriter(cr);
    cw.addInstrumentor(probe);
    cw.instrument();
  }

  private BTraceProbe recordingProbe(String traceName) throws Exception {
    BTraceProbe probe = loadTrace(traceName);
    BTraceRuntime.Impl rt =
        (BTraceRuntime.Impl)
            Proxy.newProxyInstance(
                BTraceRuntime.Impl.class.getClassLoader(),
                new Class<?>[] {BTraceRuntime.Impl.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("sendCommand")) {
                    if (failSends) {
                      throw new IllegalStateException("runtime is shutting down");
                    }
                    sent.add((Command) args[0]);
                  }
                  return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });
    return (BTraceProbe)
        Proxy.newProxyInstance(
            BTraceProbe.class.getClassLoader(),
            new Class<?>[] {BTraceProbe.class},
            (proxy, method, args) ->
                method.getName().equals("getRuntime") ? rt : method.invoke(probe, args));
  }
}
