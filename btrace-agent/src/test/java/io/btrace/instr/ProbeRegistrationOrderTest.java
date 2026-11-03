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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.btrace.core.BTraceRuntime;
import io.btrace.core.DebugSupport;
import io.btrace.core.SharedSettings;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A probe becomes visible to the transformer the moment it registers, so a class loading on another
 * thread may be transformed for it straight away. A failure there is reported through the probe's
 * runtime, which therefore has to be in place before the probe registers.
 */
class ProbeRegistrationOrderTest {
  @Test
  void aProbeHasItsRuntimeWhenTheTransformerFirstSeesIt() throws Exception {
    BTraceProbeNode probe = probe();

    assertRuntimeSetBeforeRegistration(probe, probe.delegate);
  }

  @Test
  void aPersistedProbeHasItsRuntimeWhenTheTransformerFirstSeesIt() throws Exception {
    BTraceProbePersisted probe = BTraceProbePersisted.from(probe());

    assertRuntimeSetBeforeRegistration(probe, probe.delegate);
  }

  private static void assertRuntimeSetBeforeRegistration(
      BTraceProbe probe, BTraceProbeSupport support) throws Exception {
    // The transformer lists a probe's handlers only after adding the probe to those it transforms
    // for; the runtime seen then is what a concurrent transform would see
    List<BTraceRuntime.Impl> seen = new ArrayList<>();
    Field onMethods = BTraceProbeSupport.class.getDeclaredField("onMethods");
    onMethods.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<OnMethod> handlers = (List<OnMethod>) onMethods.get(support);
    onMethods.set(
        support,
        new ArrayList<OnMethod>(handlers) {
          @Override
          public Iterator<OnMethod> iterator() {
            seen.add(probe.getRuntime());
            return super.iterator();
          }
        });
    BTraceRuntime.Impl rt = runtime();
    try {
      probe.register(rt, new BTraceTransformer(new DebugSupport(new SharedSettings())));
    } finally {
      HandlerRepositoryImpl.unregisterProbe(probe);
    }

    assertFalse(seen.isEmpty(), "the transformer did not list the probe's handlers");
    for (BTraceRuntime.Impl atRegistration : seen) {
      assertSame(rt, atRegistration, "runtime when the transformer saw the probe");
    }
  }

  private static BTraceProbeNode probe() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "TestProbe", null, "java/lang/Object", null);
    cw.visitAnnotation("Lio/btrace/core/annotations/BTrace;", true).visitEnd();
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "handler", "()V", null, null);
    AnnotationVisitor av = mv.visitAnnotation("Lio/btrace/core/annotations/OnMethod;", true);
    av.visit("clazz", "java.lang.String");
    av.visitEnd();
    mv.visitCode();
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();
    return (BTraceProbeNode)
        new BTraceProbeFactory(new SharedSettings()).createProbe(cw.toByteArray());
  }

  private static BTraceRuntime.Impl runtime() {
    return (BTraceRuntime.Impl)
        Proxy.newProxyInstance(
            BTraceRuntime.Impl.class.getClassLoader(),
            new Class<?>[] {BTraceRuntime.Impl.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "defineClass":
                  return Object.class;
                case "hashCode":
                  return System.identityHashCode(proxy);
                case "equals":
                  return proxy == args[0];
                default:
                  return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
              }
            });
  }
}
