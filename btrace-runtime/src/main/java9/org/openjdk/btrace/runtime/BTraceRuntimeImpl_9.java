/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.btrace.runtime;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import jdk.internal.perf.Perf;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.core.jfr.JfrEvent;
import org.openjdk.btrace.runtime.auxiliary.Auxiliary;

/**
 * Helper class used by BTrace built-in functions and also acts runtime "manager" for a specific
 * BTrace client and sends Commands to the CommandListener passed.
 *
 * @author A. Sundararajan
 * @author Christian Glencross (aggregation support)
 * @author Joachim Skeie (GC MBean support, advanced Deque manipulation)
 * @author KLynch
 */
public final class BTraceRuntimeImpl_9 extends BTraceRuntimeImplBase {
  public static final class Factory extends BTraceRuntimeImplFactory<BTraceRuntimeImpl_9> {
    public Factory() {
      super(new BTraceRuntimeImpl_9());
    }

    @Override
    public BTraceRuntimeImpl_9 getRuntime(
        String className, ArgsMap args, CommandListener cmdListener, Instrumentation inst) {
      return new BTraceRuntimeImpl_9(className, args, cmdListener, inst);
    }

    @Override
    public boolean isEnabled() {
      Runtime.Version version = Runtime.version();
      int major = version.version().get(0);
      return major == 9 || major == 10;
    }
  }
  // perf counter variability - we always variable variability
  private static final int V_Variable = 3;
  // perf counter units
  private static final int V_None = 1;
  private static final int V_String = 5;
  private static final int PERF_STRING_LIMIT = 256;

  private static Perf perf;

  private static final AtomicLong ANCHOR_SEQ = new AtomicLong();

  private final Method findBootstrapOrNullMtd;

  public BTraceRuntimeImpl_9() {
    fixExports(BTraceRuntime.instrumentation);

    Method m = null;
    try {
      m = ClassLoader.class.getDeclaredMethod("findBootstrapClassOrNull", String.class);
      m.setAccessible(true);
    } catch (NoSuchMethodException | InaccessibleObjectException ignored) {}
    findBootstrapOrNullMtd = m;
  }

  public BTraceRuntimeImpl_9(
      String className, ArgsMap args, CommandListener cmdListener, Instrumentation inst) {
    super(className, args, cmdListener, fixExports(inst));

    Method m = null;
    try {
      m = ClassLoader.class.getDeclaredMethod("findBootstrapClassOrNull", String.class);
      m.setAccessible(true);
    } catch (NoSuchMethodException | InaccessibleObjectException ignored) {}
    findBootstrapOrNullMtd = m;
  }

  private static Instrumentation fixExports(Instrumentation instr) {
    Set<Module> myModules = Collections.singleton(BTraceRuntimeImpl_9.class.getModule());
    if (instr != null) {
      instr.redefineModule(
          String.class.getModule(),
          Collections.emptySet(),
          Map.of(
              "jdk.internal.reflect", myModules,
              "jdk.internal.perf", myModules),
          Collections.singletonMap("java.lang", myModules),
          Collections.emptySet(),
          Collections.emptyMap());
    }
    return instr;
  }

  @Override
  public Class<?> defineClass(byte[] code, boolean mustBeBootstrap) {
    try {
      // Use StackWalker instead of Reflection.getCallerClass() to avoid
      // CallerSensitive annotation requirement (only works from bootstrap CL)
      Class<?> caller =
          StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
              .walk(frames -> frames.skip(1).findFirst())
              .map(StackWalker.StackFrame::getDeclaringClass)
              .orElse(null);
      if (caller == null || !caller.getName().startsWith("org.openjdk.btrace.")) {
        throw new SecurityException("unsafe defineClass");
      }

      // Define the probe inside a fresh per-probe anchor class in a new unnamed
      // ClassLoader. The probe ends up in that loader; once we drop our references
      // and the HandlerRepository evicts its MethodHandles, the loader becomes
      // unreachable and the probe class is unloadable.
      Class<?> anchor = defineAnchorClass();
      Class<?> clz =
          MethodHandles.privateLookupIn(anchor, MethodHandles.lookup()).defineClass(code);
      // initialize the class by creating a dummy instance
      clz.getConstructor().newInstance();
      return clz;
    } catch (IllegalAccessException
        | NoSuchMethodException
        | SecurityException
        | InstantiationException
        | InvocationTargetException ignored) {

    }
    return null;
  }

  /**
   * Emit a tiny, unique, public anchor class into a brand-new unnamed {@link ClassLoader}
   * so that a subsequent {@code privateLookupIn(anchor, ...).defineClass(probeBytes)}
   * places the probe into that isolated loader.
   */
  private static Class<?> defineAnchorClass() {
    long seq = ANCHOR_SEQ.incrementAndGet();
    final String binaryName = "org.openjdk.btrace.runtime.auxiliary.Anchor$" + seq;
    final String internalName = binaryName.replace('.', '/');
    final byte[] bytes = generateAnchorBytes(internalName);
    ClassLoader cl = new ClassLoader(null) {
      @Override
      protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (name.equals(binaryName)) {
          return defineClass(name, bytes, 0, bytes.length);
        }
        throw new ClassNotFoundException(name);
      }
    };
    try {
      return Class.forName(binaryName, true, cl);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("failed to define probe anchor class", e);
    }
  }

  /**
   * Hand-assembled class file for:
   * <pre>public final class &lt;internalName&gt; { public &lt;init&gt;() { super(); } }</pre>
   * No ASM dependency on the runtime module's classpath.
   */
  private static byte[] generateAnchorBytes(String internalName) {
    // Build a minimal class file targeting version 52 (Java 8) — works on 9+.
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
    try {
      // Constant pool entries:
      //  #1 Methodref  #2.#3     -> java/lang/Object."<init>":()V
      //  #2 Class      #4        -> java/lang/Object
      //  #3 NameAndType #5:#6    -> <init>:()V
      //  #4 Utf8       java/lang/Object
      //  #5 Utf8       <init>
      //  #6 Utf8       ()V
      //  #7 Class      #8        -> this class
      //  #8 Utf8       internalName
      //  #9 Utf8       Code
      dos.writeInt(0xCAFEBABE);
      dos.writeShort(0); // minor
      dos.writeShort(52); // major (Java 8 classfile)
      dos.writeShort(10); // constant_pool_count = entries + 1
      // #1 Methodref
      dos.writeByte(10);
      dos.writeShort(2);
      dos.writeShort(3);
      // #2 Class java/lang/Object
      dos.writeByte(7);
      dos.writeShort(4);
      // #3 NameAndType <init>:()V
      dos.writeByte(12);
      dos.writeShort(5);
      dos.writeShort(6);
      // #4 Utf8 "java/lang/Object"
      dos.writeByte(1);
      dos.writeUTF("java/lang/Object");
      // #5 Utf8 "<init>"
      dos.writeByte(1);
      dos.writeUTF("<init>");
      // #6 Utf8 "()V"
      dos.writeByte(1);
      dos.writeUTF("()V");
      // #7 Class this
      dos.writeByte(7);
      dos.writeShort(8);
      // #8 Utf8 internalName
      dos.writeByte(1);
      dos.writeUTF(internalName);
      // #9 Utf8 "Code"
      dos.writeByte(1);
      dos.writeUTF("Code");

      // access_flags: public(0x0001) + final(0x0010) + super(0x0020)
      dos.writeShort(0x0001 | 0x0010 | 0x0020);
      dos.writeShort(7); // this_class
      dos.writeShort(2); // super_class (Object)
      dos.writeShort(0); // interfaces_count
      dos.writeShort(0); // fields_count

      // methods_count = 1  (public <init>()V)
      dos.writeShort(1);
      dos.writeShort(0x0001); // access_flags = public
      dos.writeShort(5); // name_index = <init>
      dos.writeShort(6); // descriptor_index = ()V
      dos.writeShort(1); // attributes_count = 1 (Code)

      // Code attribute bytes: aload_0; invokespecial #1; return
      byte[] codeBytes = new byte[] {
          0x2A,            // aload_0
          (byte) 0xB7, 0x00, 0x01, // invokespecial #1
          (byte) 0xB1      // return
      };
      // attribute_length = 2(max_stack)+2(max_locals)+4(code_length)+code.length+2(exc)+2(attrs)
      int attrLen = 2 + 2 + 4 + codeBytes.length + 2 + 2;
      dos.writeShort(9);        // attribute_name_index = "Code"
      dos.writeInt(attrLen);
      dos.writeShort(1);        // max_stack
      dos.writeShort(1);        // max_locals
      dos.writeInt(codeBytes.length);
      dos.write(codeBytes);
      dos.writeShort(0);        // exception_table_length
      dos.writeShort(0);        // attributes_count (inside Code)

      dos.writeShort(0); // class attributes_count
      dos.flush();
      return baos.toByteArray();
    } catch (java.io.IOException e) {
      throw new IllegalStateException("failed to assemble anchor class bytes", e);
    }
  }

  /**
   * A utility class to load class data in JPMS (Java 9+)
   *
   * @param code class data
   * @return loaded class
   */
  public static Class<?> defineClass(byte[] code) {
    try {
      Class<?> clz =
          MethodHandles.privateLookupIn(Auxiliary.class, MethodHandles.lookup()).defineClass(code);
      // initialize the class by creating a dummy instance
      clz.getConstructor().newInstance();
      return clz;
    } catch (IllegalAccessException
        | NoSuchMethodException
        | SecurityException
        | InstantiationException
        | InvocationTargetException ignored) {

    }
    return null;
  }

  @Override
  public void newPerfCounter(Object value, String name, String desc) {
    Perf perf = getPerf();
    char tc = desc.charAt(0);
    switch (tc) {
      case 'C':
      case 'Z':
      case 'B':
      case 'S':
      case 'I':
      case 'J':
      case 'F':
      case 'D':
        {
          long initValue = (value != null) ? ((Number) value).longValue() : 0L;
          ByteBuffer b = perf.createLong(name, V_Variable, V_None, initValue);
          b.order(ByteOrder.nativeOrder());
          counters.put(name, b);
        }
        break;

      case '[':
        break;
      case 'L':
        {
          if (desc.equals("Ljava/lang/String;")) {
            byte[] buf;
            if (value != null) {
              buf = getStringBytes((String) value);
            } else {
              buf = new byte[PERF_STRING_LIMIT];
              buf[0] = '\0';
            }
            ByteBuffer b = perf.createByteArray(name, V_Variable, V_String, buf, buf.length);
            counters.put(name, b);
          }
        }
        break;
    }
  }

  @Override
  public ClassLoader getCallerClassLoader(int stackDec) {
    AtomicInteger cont = new AtomicInteger(stackDec);
    AtomicReference<ClassLoader> cl = new AtomicReference<>(null);
    StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
        .forEach(
            f -> {
              if (f.getClassName().startsWith("org.openjdk.btrace.runtime.auxiliary.")) {
                return;
              }
              if (cont.getAndDecrement() == 0) {
                cl.compareAndSet(null, f.getDeclaringClass().getClassLoader());
              }
            });
    return cl.get();
  }

  @Override
  public Class<?> getCallerClass(int stackDec) {
    AtomicInteger cont = new AtomicInteger(stackDec);
    AtomicReference<Class<?>> cl = new AtomicReference<>(null);
    StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
        .forEach(
            f -> {
              if (f.getClassName().startsWith("org.openjdk.btrace.runtime.auxiliary.")) {
                return;
              }
              if (cont.getAndDecrement() == 0) {
                cl.compareAndSet(null, f.getDeclaringClass());
              }
            });
    return cl.get();
  }

  @Override
  public int version() {
    return 9;
  }

  @Override
  public JfrEvent.Factory createEventFactory(JfrEvent.Template template) {
    return () -> JfrEvent.EMPTY;
  }

  @Override
  public boolean isBootstrapClass(String className) {
    try {
      return findBootstrapOrNullMtd != null
          && findBootstrapOrNullMtd.invoke(ClassLoader.getSystemClassLoader(), className) != null;
    } catch (IllegalAccessException | InvocationTargetException ignored) {}
    return false;
  }

  private static Perf getPerf() {
    synchronized (BTraceRuntimeImpl_9.class) {
      if (perf == null) {
        perf = AccessController.doPrivileged(new Perf.GetPerfAction());
      }
    }
    return perf;
  }
}
