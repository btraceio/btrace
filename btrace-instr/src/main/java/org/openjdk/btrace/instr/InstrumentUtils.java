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

package org.openjdk.btrace.instr;

import static org.objectweb.asm.Opcodes.*;
import static org.openjdk.btrace.instr.TypeUtils.isAnyType;
import static org.openjdk.btrace.instr.TypeUtils.isPrimitive;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;

/**
 * @author A. Sundararajan
 * @author J. Bachorik
 */
public final class InstrumentUtils {
  private static final int CW_FLAGS = 0; // ClassWriter.COMPUTE_MAXS;

  /**
   * Collects the type hierarchy into the provided list, sorted from the actual type to root. Common
   * superclasses may be present multiple times (eg. {@code java.lang.Object}) It will use the
   * associated classloader to locate the class file resources.
   *
   * @param cl the associated classloader
   * @param type the type to compute the hierarchy closure for (either Java or internal name format)
   * @param closure the ordered set to store the closure in
   * @param useInternal should internal types names be used in the closure
   */
  public static void collectHierarchyClosure(
      ClassLoader cl, String type, Set<String> closure, boolean useInternal) {
    collectHierarchyClosure(cl, type, closure, useInternal, false);
  }

  /**
   * Collects the type hierarchy into the provided list, sorted from the actual type to root. Common
   * superclasses may be present multiple times (eg. {@code java.lang.Object}) It will use the
   * associated classloader to locate the class file resources.
   *
   * @param cl the associated classloader
   * @param type the type to compute the hierarchy closure for (either Java or internal name format)
   * @param closure the ordered set to store the closure in
   * @param useInternal should internal types names be used in the closure
   */
  public static void collectHierarchyClosure(
      ClassLoader cl, String type, Set<String> closure, boolean useInternal, boolean ifcs) {
    if (type == null || type.equals(Constants.OBJECT_INTERNAL)) {
      return;
    }
    ClassInfo ci = ClassCache.getInstance().get(cl, type);

    Set<ClassInfo> ciSet = new LinkedHashSet<>();

    // add self
    ciSet.add(ci);
    for (ClassInfo sci : ci.getSupertypes(false)) {
      if ((ifcs || !sci.isInterface()) && !sci.getClassName().equals(Constants.OBJECT_INTERNAL)) {
        ciSet.add(sci);
      }
    }

    for (ClassInfo sci : ciSet) {
      closure.add(useInternal ? sci.getClassName() : sci.getJavaClassName());
    }
  }

  public static boolean isAssignable(
      Type left, Type right, ClassLoader cl, boolean exactTypeCheck) {
    boolean isSame = left.equals(right);

    if (isSame) {
      return true;
    }

    if (TypeUtils.isVoid(left)) {
      return TypeUtils.isVoid(right);
    }

    if (TypeUtils.isAnyType(left)) {
      return true;
    }

    if (exactTypeCheck) {
      return false;
    }

    if (TypeUtils.isObject(left)) {
      return true;
    }

    Set<String> closure = new HashSet<>();
    collectHierarchyClosure(cl, right.getInternalName(), closure, true, true);
    return closure.contains(left.getInternalName());
  }

  public static boolean isAssignable(
      Type[] args1, Type[] args2, ClassLoader cl, boolean exactTypeCheck) {
    if (args1.length != args2.length) {
      return false;
    }
    for (int i = 0; i < args1.length; i++) {
      if (!args1[i].equals(args2[i])) {
        int sort2 = args2[i].getSort();
        /*
         * if destination is AnyType and right side is
         * Object or Array (i.e., any reference type)
         * then we allow it - because AnyType is mapped to
         * java.lang.Object.
         */
        if (!(isAnyType(args1[i])
            && (sort2 == Type.OBJECT || sort2 == Type.ARRAY || isPrimitive(args2[i])))) {
          if (!isAssignable(args1[i], args2[i], cl, exactTypeCheck)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  public static String arrayDescriptorFor(int typeCode) {
    switch (typeCode) {
      case T_BOOLEAN:
        return "[Z";
      case T_CHAR:
        return "[C";
      case T_FLOAT:
        return "[F";
      case T_DOUBLE:
        return "[D";
      case T_BYTE:
        return "[B";
      case T_SHORT:
        return "[S";
      case T_INT:
        return "[I";
      case T_LONG:
        return "[J";
      default:
        throw new IllegalArgumentException();
    }
  }

  public static void accept(BTraceClassReader reader, ClassVisitor visitor) {
    accept(reader, visitor, 0);
  }

  public static void accept(BTraceClassReader reader, ClassVisitor visitor, int flags) {
    if (reader == null || visitor == null) return;

    reader.accept(visitor, flags);
  }

  private static boolean isJDK16OrAbove(byte[] code) {
    return isJDK16OrAbove(getMajor(code));
  }

  private static boolean isJDK16OrAbove(BTraceClassReader cr) {
    return isJDK16OrAbove(getMajor(cr));
  }

  private static boolean isJDK16OrAbove(int major) {
    return major >= 50;
  }

  private static int getMajor(BTraceClassReader cr) {
    return cr.getClassVersion();
  }

  private static int getMajor(byte[] code) {
    // skip 0xCAFEBABE magic and minor version
    int majorOffset = 4 + 2;
    return (((code[majorOffset] << 8) & 0xFF00) | ((code[majorOffset + 1]) & 0xFF));
  }

  public static ClassWriter newClassWriter() {
    return newClassWriter(false);
  }

  public static ClassWriter newClassWriter(boolean computeFrames) {
    return newClassWriter(computeFrames, false);
  }

  public static ClassWriter newClassWriter(boolean computeFrames, boolean computeMaxs) {
    int flags = CW_FLAGS;
    if (computeFrames) {
      flags |= ClassWriter.COMPUTE_FRAMES;
    }
    if (computeMaxs) {
      flags |= ClassWriter.COMPUTE_MAXS;
    }
    return newClassWriter(null, flags);
  }

  static BTraceClassWriter newClassWriter(BTraceClassReader cr) {
    return newClassWriter(cr, CW_FLAGS);
  }

  static BTraceClassWriter newClassWriter(BTraceClassReader reader, int flags) {
    BTraceClassWriter cw;
    cw =
        reader != null
            ? new BTraceClassWriter(reader.getClassLoader(), reader, flags)
            : new BTraceClassWriter(null, flags);

    return cw;
  }

  static BTraceClassReader newClassReader(byte[] code) {
    return new BTraceClassReader(ClassLoader.getSystemClassLoader(), code);
  }

  static BTraceClassReader newClassReader(ClassLoader cl, byte[] code) {
    return new BTraceClassReader(cl, code);
  }

  static BTraceClassReader newClassReader(InputStream is) throws IOException {
    return new BTraceClassReader(ClassLoader.getSystemClassLoader(), is);
  }

  static BTraceClassReader newClassReader(ClassLoader cl, InputStream is) throws IOException {
    return new BTraceClassReader(cl, is);
  }

  static String getActionPrefix(String className) {
    return Constants.BTRACE_METHOD_PREFIX + className.replace('/', '$') + "$";
  }
}
