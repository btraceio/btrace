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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AccessController;

import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.core.jfr.JfrEvent;
import sun.misc.Perf;
import sun.misc.Unsafe;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;

/**
 * Helper class used by BTrace built-in functions and also acts runtime "manager" for a specific
 * BTrace client and sends Commands to the CommandListener passed.
 *
 * @author A. Sundararajan
 * @author Christian Glencross (aggregation support)
 * @author Joachim Skeie (GC MBean support, advanced Deque manipulation)
 * @author KLynch
 */
public final class BTraceRuntimeImpl_7 extends BTraceRuntimeImplBase {
  public static final class Factory extends BTraceRuntimeImplFactory<BTraceRuntimeImpl_7> {
    public Factory() {
      super(new BTraceRuntimeImpl_7());
    }

    @Override
    public BTraceRuntimeImpl_7 getRuntime(
        String className,
        ArgsMap args,
        CommandListener cmdListener,
        DebugSupport ds,
        Instrumentation inst) {
      return new BTraceRuntimeImpl_7(className, args, cmdListener, ds, inst);
    }

    @Override
    public boolean isEnabled() {
      try {
        Class.forName("java.lang.Module");
        return false;
      } catch (ClassNotFoundException ignored) {
      }
      return true;
    }
  }

  // perf counter variability - we always variable variability
  private static final int V_Variable = 3;
  // perf counter units
  private static final int V_None = 1;
  private static final int V_String = 5;
  private static final int PERF_STRING_LIMIT = 256;

  private static Perf perf;

  public BTraceRuntimeImpl_7() {}

  public BTraceRuntimeImpl_7(
      String className,
      ArgsMap args,
      CommandListener cmdListener,
      DebugSupport ds,
      Instrumentation inst) {
    super(className, args, cmdListener, ds, inst);
  }

  @Override
  @CallerSensitive
  public Class<?> defineClass(byte[] code, boolean mustBeBootstrap) {
    Unsafe unsafe = BTraceRuntime.initUnsafe();
    if (unsafe != null) {
      Class<?> caller = Reflection.getCallerClass(2);
      if (!caller.getName().startsWith("org.openjdk.btrace.")) {
        throw new SecurityException("unsafe defineClass");
      }
      ClassLoader loader = null;
      if (!mustBeBootstrap) {
        loader = new ClassLoader(null) {};
      }
      Class<?> cl = unsafe.defineClass(getClassName(), code, 0, code.length, loader, null);
      unsafe.ensureClassInitialized(cl);
      return cl;
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

  @CallerSensitive
  @Override
  public ClassLoader getCallerClassLoader(int stackDec) {
    return Reflection.getCallerClass(stackDec + 1).getClassLoader();
  }

  @Override
  public Class<?> getCallerClass(int stackDec) {
    return Reflection.getCallerClass(stackDec + 1);
  }

  @Override
  public JfrEvent.Factory createEventFactory(JfrEvent.Template eventTemplate) {
      System.out.printf("===> JFR JDK 7");
      return new JfrEvent.Factory() {
          private final JfrEvent dummy = new JfrEvent() {
              @Override
              public JfrEvent withValue(String fieldName, byte value) {
                  return this;
              }

              @Override
              public JfrEvent withValue(String fieldName, boolean value) {
                  return this;
              }

              @Override
              public JfrEvent withValue(String fieldName, char value) {
                  return this;
              }

              @Override
              public JfrEvent withValue(String fieldName, short value) {
                  return this;
              }

              @Override
              public JfrEvent withValue(String fieldName, int value) {
                  return this;
              }

              @Override
              public JfrEvent withValue(String fieldName, float value) {
                  return this;
              }

              @Override
              public JfrEvent withValue(String fieldName, long value) {
                  return this;
              }

              @Override
              public JfrEvent withValue(String fieldName, double value) {
                  return this;
              }

              @Override
              public JfrEvent withValue(String fieldName, String value) {
                  return this;
              }

              @Override
              public void commit() {

              }

              @Override
              public boolean shouldCommit() {
                  return false;
              }

              @Override
              public <T extends Class<?>> T getJfrClass() {
                  return null;
              }
          };
          @Override
          public JfrEvent newEvent() {
              return dummy;
          }
      };
  }

  @Override
  public int version() {
    return 7;
  }

  private static Perf getPerf() {
    synchronized (BTraceRuntimeImpl_7.class) {
      if (perf == null) {
        perf = AccessController.doPrivileged(new Perf.GetPerfAction());
      }
    }
    return perf;
  }
}
