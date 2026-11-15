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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.SharedSettings;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

/**
 * Probes compiled with the genuine BTrace 2.2.6 compiler (package {@code org.openjdk.btrace}) must
 * load and pass verification on the current agent, in both the packed and the plain class form.
 */
class LegacyProbeLoadingTest {
  private final BTraceProbeFactory factory = new BTraceProbeFactory(SharedSettings.GLOBAL);

  private static byte[] fixture(String name) throws Exception {
    try (InputStream is = LegacyProbeLoadingTest.class.getResourceAsStream("/legacy/" + name)) {
      assertNotNull(is, "missing fixture " + name);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = is.read(buf)) > 0) {
        bos.write(buf, 0, n);
      }
      return bos.toByteArray();
    }
  }

  private void assertLegacyProbe(BTraceProbe probe) {
    assertNotNull(probe);
    assertTrue(probe.isVerified(), "legacy probe must pass verification");
    assertTrue(probe.getClassName().contains("SlowChargeProbe"), probe.getClassName());
    assertTrue(!probe.getClassName().startsWith("org.openjdk.btrace"), probe.getClassName());
    List<OnMethod> onMethods = new ArrayList<>();
    probe.onmethods().forEach(onMethods::add);
    assertEquals(1, onMethods.size());
    assertEquals("OrderService", onMethods.get(0).getClazz());
    assertEquals("chargeCard", onMethods.get(0).getMethod());
  }

  @Test
  void packedProbeFrom226LoadsAndVerifies() throws Exception {
    assertLegacyProbe(factory.createProbe(fixture("SlowChargeProbe-2.2.6-pack.class")));
  }

  @Test
  void plainProbeFrom226LoadsAndVerifies() throws Exception {
    assertLegacyProbe(factory.createProbe(fixture("SlowChargeProbe-2.2.6-nopack.class")));
  }

  @Test
  void packedProbeFrom226LinksAgainstTheCurrentBootstrapClasses() throws Exception {
    BTraceProbe probe = factory.createProbe(fixture("SlowChargeProbe-2.2.6-pack.class"));
    assertLinksAgainstCurrentBootstrapClasses(probe);
  }

  /**
   * A pack written by a pre-release 3.0 build names {@code io/btrace/runtime/BTraceRuntimeAccess}
   * and {@code io/btrace/runtime/LinkingFlag}. The pack is built from the 2.2.6 fixture: loaded,
   * written in the current format, and its two class files rewritten to the pre-release names.
   */
  @Test
  void packedProbeFromPreRelease30LinksAgainstTheCurrentBootstrapClasses() throws Exception {
    BTraceProbePersisted loaded =
        BTraceProbePersisted.from(factory.createProbe(fixture("SlowChargeProbe-2.2.6-pack.class")));
    ByteArrayOutputStream written = new ByteArrayOutputStream();
    loaded.write(new DataOutputStream(written));
    byte[] pack = written.toByteArray();
    byte[] holder = loaded.getDataHolderBytecode();
    byte[] full = loaded.getFullBytecode();
    // the data holder and the full class are the last two length-prefixed blocks of a pack
    int tail = 8 + holder.length + full.length;
    ByteBuffer blocks = ByteBuffer.wrap(pack, pack.length - tail, tail);
    assertEquals(holder.length, blocks.getInt());
    blocks.position(blocks.position() + holder.length);
    assertEquals(full.length, blocks.getInt());

    ByteArrayOutputStream preRelease = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(preRelease);
    out.write(pack, 0, pack.length - tail);
    byte[] oldHolder = toPreReleaseNames(holder);
    byte[] oldFull = toPreReleaseNames(full);
    assertTrue(namesPreReleaseClass(oldFull), "fixture must name the pre-release classes");
    out.writeInt(oldHolder.length);
    out.write(oldHolder);
    out.writeInt(oldFull.length);
    out.write(oldFull);

    BTraceProbe probe = factory.createProbe(preRelease.toByteArray());
    assertLegacyProbe(probe);
    assertLinksAgainstCurrentBootstrapClasses(probe);
  }

  private static final Pattern PRE_RELEASE_NAME =
      Pattern.compile("io/btrace/runtime/(BTraceRuntimeAccess|LinkingFlag)(?!\\w)");
  private static final Pattern CURRENT_NAME =
      Pattern.compile("io/btrace/core/(BTraceRuntimeAccess|LinkingFlag)(?!\\w)");

  /**
   * Checks the names the probe's code refers to. The disassembly is used rather than the raw class
   * bytes, because an ASM rewrite keeps the original constant pool, unused entries included.
   */
  private static void assertLinksAgainstCurrentBootstrapClasses(BTraceProbe probe) {
    String full = disassemble(probe.getFullBytecode());
    String holder = disassemble(probe.getDataHolderBytecode());
    assertTrue(!PRE_RELEASE_NAME.matcher(full).find(), full);
    assertTrue(!PRE_RELEASE_NAME.matcher(holder).find(), holder);
    assertTrue(full.contains("io/btrace/core/BTraceRuntimeAccess.enter"), full);
  }

  private static String disassemble(byte[] code) {
    StringWriter text = new StringWriter();
    new ClassReader(code).accept(new TraceClassVisitor(new PrintWriter(text)), 0);
    return text.toString();
  }

  private static boolean namesPreReleaseClass(byte[] code) {
    return PRE_RELEASE_NAME.matcher(disassemble(code)).find();
  }

  private static byte[] toPreReleaseNames(byte[] cls) throws IOException {
    return rewriteUtf8(cls, s -> CURRENT_NAME.matcher(s).replaceAll("io/btrace/runtime/$1"));
  }

  /** Rewrites the CONSTANT_Utf8 entries of a class file and copies every other byte unchanged. */
  private static byte[] rewriteUtf8(byte[] cls, UnaryOperator<String> fn) throws IOException {
    ByteBuffer in = ByteBuffer.wrap(cls);
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bos);
    int count = in.getShort(8) & 0xffff;
    int pos = 10;
    out.write(cls, 0, pos);
    for (int i = 1; i < count; i++) {
      int tag = cls[pos] & 0xff;
      int size;
      switch (tag) {
        case 1:
          int len = in.getShort(pos + 1) & 0xffff;
          byte[] value = fn.apply(new String(cls, pos + 3, len, ISO_8859_1)).getBytes(ISO_8859_1);
          out.writeByte(1);
          out.writeShort(value.length);
          out.write(value);
          pos += 3 + len;
          continue;
        case 5: // long
        case 6: // double
          size = 9;
          i++;
          break;
        case 7: // class
        case 8: // string
        case 16: // method type
        case 19: // module
        case 20: // package
          size = 3;
          break;
        case 15: // method handle
          size = 4;
          break;
        case 3: // int
        case 4: // float
        case 9: // field ref
        case 10: // method ref
        case 11: // interface method ref
        case 12: // name and type
        case 17: // dynamic
        case 18: // invokedynamic
          size = 5;
          break;
        default:
          throw new IllegalArgumentException("unknown constant pool tag " + tag);
      }
      out.write(cls, pos, size);
      pos += size;
    }
    out.write(cls, pos, cls.length - pos);
    return bos.toByteArray();
  }
}
