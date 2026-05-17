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
package io.btrace.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BTraceBootstrapTest {

  @BeforeEach
  void clearTable() throws Exception {
    var field = BTraceBootstrap.class.getDeclaredField("OP_TABLE");
    field.setAccessible(true);
    ((java.util.concurrent.ConcurrentHashMap<?, ?>) field.get(null)).clear();
  }

  @Test
  void bootstrap_registeredOp_returnsConstantCallSite() throws Throwable {
    MethodType type = MethodType.methodType(void.class, String.class);
    var target =
        MethodHandles.lookup()
            .findStatic(
                BTraceBootstrapTest.class,
                "noopPrint",
                MethodType.methodType(void.class, String.class));
    BTraceBootstrap.registerCoreOp("print", type, target);

    CallSite cs = BTraceBootstrap.bootstrap(MethodHandles.lookup(), "print", type);
    assertInstanceOf(java.lang.invoke.ConstantCallSite.class, cs);
  }

  @Test
  void bootstrap_unknownOp_throwsBootstrapMethodError() {
    MethodType type = MethodType.methodType(void.class, String.class);
    assertThrows(
        BootstrapMethodError.class,
        () -> BTraceBootstrap.bootstrap(MethodHandles.lookup(), "unknown_op_xyz", type));
  }

  @Test
  void registerCoreOp_duplicate_throwsIllegalStateException() throws Exception {
    MethodType type = MethodType.methodType(void.class, String.class);
    var mh =
        MethodHandles.lookup()
            .findStatic(
                BTraceBootstrapTest.class,
                "noopPrint",
                MethodType.methodType(void.class, String.class));
    BTraceBootstrap.registerCoreOp("print", type, mh);
    assertThrows(
        IllegalStateException.class, () -> BTraceBootstrap.registerCoreOp("print", type, mh));
  }

  @Test
  void bootstrap_callSite_invokesRegisteredHandle() throws Throwable {
    MethodType type = MethodType.methodType(void.class, String.class);
    var target =
        MethodHandles.lookup()
            .findStatic(
                BTraceBootstrapTest.class,
                "noopPrint",
                MethodType.methodType(void.class, String.class));
    BTraceBootstrap.registerCoreOp("println", type, target);

    CallSite cs = BTraceBootstrap.bootstrap(MethodHandles.lookup(), "println", type);
    // invoking via the call site should not throw
    assertDoesNotThrow(() -> cs.getTarget().invoke("hello"));
  }

  public static void noopPrint(String s) {}
}
