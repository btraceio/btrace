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
package org.openjdk.btrace.runtime;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared helper for probe-class isolation on the JDK 9-14 code paths.
 *
 * <p>On those paths we cannot define a probe into a hidden class yet ({@code
 * Lookup.defineHiddenClass} is JDK 15+), so to keep probes unloadable we instead define each probe
 * in a <em>fresh, throwaway {@link ClassLoader}</em>:
 *
 * <ol>
 *   <li>{@link #defineAnchor(ClassLoader)} emits a tiny, unique public "anchor" class into a
 *       brand-new unnamed {@code ClassLoader} (parented to the given {@code ClassLoader}).
 *   <li>The caller then invokes {@code MethodHandles.privateLookupIn(anchor,
 *       lookup()).defineClass(probeBytes)} which lands the probe into the anchor's loader — i.e.
 *       the same fresh loader.
 *   <li>When the probe is dropped and all {@code MethodHandle}s / {@code Class<?>} references are
 *       cleared, the loader becomes unreachable and the probe class is eligible for unload.
 * </ol>
 *
 * <p>Kept in the JDK 8 source set so it is visible to both the {@code java9} and {@code java11}
 * source sets through the main compile output.
 */
final class ProbeAnchor {
  private static final AtomicLong ANCHOR_SEQ = new AtomicLong();

  private ProbeAnchor() {}

  /**
   * Emit a tiny, unique, public anchor class into a brand-new unnamed {@link ClassLoader} so that a
   * subsequent {@code privateLookupIn(anchor, ...).defineClass(probeBytes)} places the probe into
   * that isolated loader.
   *
   * @param parent the parent ClassLoader. Allows probe to resolve classes (e.g., BTraceUtils) from
   *     the agent's ClassLoader.
   */
  static Class<?> defineAnchor(ClassLoader parent) {
    long seq = ANCHOR_SEQ.incrementAndGet();
    final String binaryName = "org.openjdk.btrace.runtime.auxiliary.Anchor$" + seq;
    final byte[] bytes = generateAnchorBytes(binaryName.replace('.', '/'));
    ClassLoader cl =
        new ClassLoader(parent) {
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
   *
   * <pre>public final class &lt;internalName&gt; { public &lt;init&gt;() { super(); } }</pre>
   *
   * No ASM dependency on the runtime module's classpath.
   */
  static byte[] generateAnchorBytes(String internalName) {
    // Build a minimal class file targeting version 52 (Java 8) — works on 9+.
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
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
      byte[] codeBytes =
          new byte[] {
            0x2A, // aload_0
            (byte) 0xB7,
            0x00,
            0x01, // invokespecial #1
            (byte) 0xB1 // return
          };
      // attribute_length = 2(max_stack)+2(max_locals)+4(code_length)+code.length+2(exc)+2(attrs)
      int attrLen = 2 + 2 + 4 + codeBytes.length + 2 + 2;
      dos.writeShort(9); // attribute_name_index = "Code"
      dos.writeInt(attrLen);
      dos.writeShort(1); // max_stack
      dos.writeShort(1); // max_locals
      dos.writeInt(codeBytes.length);
      dos.write(codeBytes);
      dos.writeShort(0); // exception_table_length
      dos.writeShort(0); // attributes_count (inside Code)

      dos.writeShort(0); // class attributes_count
      dos.flush();
      return baos.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("failed to assemble anchor class bytes", e);
    }
  }
}
