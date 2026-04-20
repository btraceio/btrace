/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package org.openjdk.btrace.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.openjdk.btrace.runtime.auxiliary.Auxiliary;

/**
 * Regression tests for two linked failures that together broke the entire
 * dynamic-attach path on JDK 15+:
 *
 * <ol>
 *   <li>{@link Auxiliary#lookup()} must return a {@link MethodHandles.Lookup}
 *       usable with {@code Lookup.defineHiddenClass}. The previous approach of
 *       calling {@code MethodHandles.privateLookupIn(Auxiliary.class, lookup())}
 *       from {@code BTraceRuntimeImpl_11} crossed a module boundary (the agent's
 *       MaskedClassLoader vs the bootstrap loader that hosts {@code Auxiliary}
 *       in a masked btrace.jar deployment), which dropped the MODULE bit and
 *       caused {@code defineHiddenClass} to fail with
 *       {@code IllegalAccessException: ... does not have full privilege access}.</li>
 *   <li>{@link BTraceRuntimeAccessImpl#forClassInternal} must tolerate probe
 *       class names carrying the hidden-class suffix (e.g. {@code "pkg.Name/0x..."}).
 *       The runtime is registered under the plain name before defineClass runs,
 *       so the lookup has to strip the suffix. Failing to strip produced an NPE
 *       during the probe's {@code <clinit>}, which surfaced as a causeless
 *       "can not load BTrace class" exception.</li>
 * </ol>
 */
class HiddenClassDefineRegressionTest {

  /**
   * The lookup returned from {@link Auxiliary#lookup()} must have full privilege
   * access, which is the precondition for {@code Lookup.defineHiddenClass}. If
   * someone refactors it back to a cross-module {@code privateLookupIn}, the
   * MODULE bit disappears and this assertion breaks.
   */
  @Test
  void auxiliaryLookupHasFullPrivilegeAccess() throws Exception {
    MethodHandles.Lookup lookup = Auxiliary.lookup();
    assertEquals(Auxiliary.class, lookup.lookupClass(),
        "lookup must be anchored on Auxiliary");
    // Lookup.hasFullPrivilegeAccess() is JDK 14+. Reflect to keep this source set
    // compilable against older targets.
    Method hasFullPriv;
    try {
      hasFullPriv = MethodHandles.Lookup.class.getMethod("hasFullPrivilegeAccess");
    } catch (NoSuchMethodException tooOld) {
      // Older JDK — nothing to assert here; the hidden-class path only exists on 15+.
      return;
    }
    assertTrue((Boolean) hasFullPriv.invoke(lookup),
        "Auxiliary.lookup() must grant PRIVATE|MODULE (defineHiddenClass precondition)");
  }

  /**
   * End-to-end: the lookup from {@link Auxiliary} must actually produce a usable
   * hidden class via {@code defineHiddenClass(bytes, true, options)}. This
   * exercises both the module check and the classfile-to-class-runtime-package
   * match (probe bytes declare a name in Auxiliary's package).
   */
  @Test
  void defineHiddenClassWithAuxiliaryLookupSucceeds() throws Throwable {
    assumeTrue(jdkFeatureVersion() >= 15,
        "defineHiddenClass is JDK 15+");

    String internalName = Auxiliary.class.getPackage().getName().replace('.', '/')
        + "/HiddenClassProbe$Test";
    byte[] bytes = minimalClassBytes(internalName);

    MethodHandles.Lookup lookup = Auxiliary.lookup();
    Class<?> classOptionClass =
        Class.forName("java.lang.invoke.MethodHandles$Lookup$ClassOption");
    Object emptyOptions = Array.newInstance(classOptionClass, 0);
    Method defineHidden = MethodHandles.Lookup.class.getMethod(
        "defineHiddenClass", byte[].class, boolean.class, emptyOptions.getClass());
    Object hiddenLookup = defineHidden.invoke(lookup, bytes, true, emptyOptions);
    Method lookupClassMtd = hiddenLookup.getClass().getMethod("lookupClass");
    Class<?> defined = (Class<?>) lookupClassMtd.invoke(hiddenLookup);

    assertNotNull(defined, "defineHiddenClass returned null");
    assertTrue(defined.isHidden(), "expected a hidden class");
    // Hidden classes carry a VM-assigned suffix past a '/'.
    assertTrue(defined.getName().contains("/"),
        "hidden class name should contain '/': " + defined.getName());
    assertTrue(defined.getName().startsWith(internalName.replace('/', '.')),
        "hidden class name should start with declared name: " + defined.getName());
  }

  /**
   * {@code forClassInternal} looks up the registered runtime by class name. Hidden
   * classes report {@code getName()} with a runtime-assigned suffix after the
   * first {@code '/'}; the runtime is registered under the plain name. The
   * normalization helper strips the suffix so the lookup matches the registry.
   */
  @Test
  void hiddenClassNameIsStrippedForRuntimeLookup() {
    String plain = "org.openjdk.btrace.runtime.auxiliary.SampleProbe";
    String hidden = plain + "/0x00000007ff0abcd0";
    assertEquals(plain, BTraceRuntimeAccessImpl.normalizeProbeName(hidden),
        "hidden-class suffix must be stripped");
    assertEquals(plain, BTraceRuntimeAccessImpl.normalizeProbeName(plain),
        "plain names must be returned unchanged");
  }

  private static int jdkFeatureVersion() {
    String v = System.getProperty("java.specification.version", "0");
    if (v.startsWith("1.")) {
      v = v.substring(2);
    }
    try {
      return Integer.parseInt(v);
    } catch (NumberFormatException nfe) {
      return 0;
    }
  }

  /**
   * Hand-assembled class file for
   * {@code public final class <internalName> { public <init>() { super(); } }}.
   * Deliberately avoids ASM to keep the test dependency-free; the bytes only
   * need to pass verification.
   */
  private static byte[] minimalClassBytes(String internalName) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    try {
      dos.writeInt(0xCAFEBABE);
      dos.writeShort(0);          // minor
      dos.writeShort(52);         // major — Java 8 classfile works on 15+
      dos.writeShort(10);         // constant_pool_count = entries + 1
      dos.writeByte(10);          // #1 Methodref #2.#3
      dos.writeShort(2);
      dos.writeShort(3);
      dos.writeByte(7);           // #2 Class #4 -> java/lang/Object
      dos.writeShort(4);
      dos.writeByte(12);          // #3 NameAndType #5:#6
      dos.writeShort(5);
      dos.writeShort(6);
      dos.writeByte(1);           // #4 Utf8 "java/lang/Object"
      dos.writeUTF("java/lang/Object");
      dos.writeByte(1);           // #5 Utf8 "<init>"
      dos.writeUTF("<init>");
      dos.writeByte(1);           // #6 Utf8 "()V"
      dos.writeUTF("()V");
      dos.writeByte(7);           // #7 Class #8 -> this
      dos.writeShort(8);
      dos.writeByte(1);           // #8 Utf8 <internalName>
      dos.writeUTF(internalName);
      dos.writeByte(1);           // #9 Utf8 "Code"
      dos.writeUTF("Code");

      dos.writeShort(0x0001 | 0x0010 | 0x0020); // public final super
      dos.writeShort(7);          // this_class
      dos.writeShort(2);          // super_class
      dos.writeShort(0);          // interfaces_count
      dos.writeShort(0);          // fields_count

      dos.writeShort(1);          // methods_count
      dos.writeShort(0x0001);     // public
      dos.writeShort(5);          // name <init>
      dos.writeShort(6);          // descriptor ()V
      dos.writeShort(1);          // attributes_count

      byte[] code = new byte[] {
          0x2A,                   // aload_0
          (byte) 0xB7, 0x00, 0x01,// invokespecial #1
          (byte) 0xB1             // return
      };
      int attrLen = 2 + 2 + 4 + code.length + 2 + 2;
      dos.writeShort(9);          // "Code"
      dos.writeInt(attrLen);
      dos.writeShort(1);          // max_stack
      dos.writeShort(1);          // max_locals
      dos.writeInt(code.length);
      dos.write(code);
      dos.writeShort(0);          // exception_table_length
      dos.writeShort(0);          // code attributes_count

      dos.writeShort(0);          // class attributes_count
      dos.flush();
      return baos.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("failed to assemble class bytes", e);
    }
  }
}
