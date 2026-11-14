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
package io.btrace.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Checks the bootstrap section of the masked {@code btrace.jar}: which classes sit at its root, and
 * that they reference nothing the bootstrap class loader cannot see.
 *
 * <p>A <em>root class</em> is a JAR entry whose name ends in {@code .class} and does not start with
 * {@code META-INF/}. Masked classes are stored as {@code .classdata} under {@code META-INF/btrace/}
 * and are therefore never root classes. The manifest declares {@code Boot-Class-Path: btrace.jar},
 * which puts exactly these entries on the bootstrap class path. A versioned entry under {@code
 * META-INF/versions/} would also become bootstrap-visible if the manifest declared {@code
 * Multi-Release: true}; this rule does not cover such entries, so the test fails if the attribute
 * appears.
 *
 * <p>{@code bootstrap-root-allowlist.txt} lists the permitted root classes, derived from {@code
 * bootIncludes} and the extra root {@code from} blocks of the {@code btraceJar} task in {@code
 * btrace-dist/build.gradle}. A line ending in {@code /} matches the classes directly in that
 * package, not in its subpackages, so a new package must be listed explicitly. A line ending in
 * {@code .class} matches that class and its nested classes ({@code Name$*.class}). Every root class
 * must match a line and every line must match a root class.
 *
 * <p>The types a root class references are the classes named in its constant pool ({@code
 * CONSTANT_Class}, and the descriptors of {@code CONSTANT_NameAndType} and {@code
 * CONSTANT_MethodType}, which also covers method handles and {@code invokedynamic} bootstrap
 * arguments) plus the descriptors of its declared fields and methods. Array types count as their
 * element type and primitive types are ignored. Annotation descriptors and generic signatures are
 * not counted: a missing annotation type is skipped by reflection and a signature is erased, so
 * neither can fail linking.
 *
 * <p>A referenced type is bootstrap-visible when it is another root class, or a JDK type the boot
 * loader defines. JDK types are recognised by a fixed rule, so the result does not depend on the
 * JDK running the test: the internal name is under {@code java/}, {@code javax/}, {@code jdk/},
 * {@code sun/} or {@code com/sun/management/} (in {@code rt.jar} on JDK 8 and in the boot-loader
 * module {@code jdk.management} from JDK 9), and its package is not listed in {@code
 * bootstrap-jdk-nonboot-packages.txt}. That file lists the packages exported by JDK 9+ modules
 * defined to the platform or application loader, such as {@code java.sql} and {@code
 * java.compiler}; how it was generated is in its header. Relocated SLF4J types ({@code
 * io/btrace/libs/org/slf4j/}) get no exemption: they are bootstrap-visible only as root classes.
 *
 * <p>Any other reference must be listed in {@code bootstrap-closure-baseline.txt} as {@code <root
 * class> -> <referenced class> # reason}, in internal names. An unlisted reference fails as new and
 * a listed one that no longer occurs fails as stale, so the baseline only shrinks.
 *
 * <p>The tests whose names start with {@code fixture} exercise the extraction and matching logic on
 * classes built in memory, so the gate is seen failing on bad input independently of the jar.
 */
class BootstrapClosureTest {
  private static final String ALLOWLIST = "bootstrap-root-allowlist.txt";
  private static final String BASELINE = "bootstrap-closure-baseline.txt";
  private static final String NON_BOOT_PACKAGES = "bootstrap-jdk-nonboot-packages.txt";

  private static final List<String> JDK_PREFIXES =
      Collections.unmodifiableList(
          Arrays.asList("java/", "javax/", "jdk/", "sun/", "com/sun/management/"));

  private static final int CONSTANT_CLASS = 7;
  private static final int CONSTANT_NAME_AND_TYPE = 12;
  private static final int CONSTANT_METHOD_TYPE = 16;

  @Test
  void rootClassesMatchTheAllowlist() throws IOException {
    List<String> allowlist = readEntries(ALLOWLIST, false);
    assertFalse(allowlist.isEmpty(), ALLOWLIST + " lists nothing");
    List<String> errors = allowlistErrors(allowlist, readRootClasses().keySet());
    assertTrue(errors.isEmpty(), String.join("\n", errors));
  }

  @Test
  void rootClassesReferenceOnlyBootstrapVisibleTypes() throws IOException {
    Set<String> nonBootPackages = new HashSet<>(readEntries(NON_BOOT_PACKAGES, false));
    assertFalse(nonBootPackages.isEmpty(), NON_BOOT_PACKAGES + " lists nothing");
    Map<String, Set<String>> references = new TreeMap<>();
    for (Map.Entry<String, byte[]> root : readRootClasses().entrySet()) {
      references.put(internalName(root.getKey()), referencedTypes(root.getKey(), root.getValue()));
    }
    Set<String> baseline = new TreeSet<>(readEntries(BASELINE, true));
    List<String> errors = closureErrors(references, baseline, nonBootPackages);
    assertTrue(errors.isEmpty(), String.join("\n", errors));
  }

  // ---- fixtures for the gate's own logic ----

  @Test
  void fixtureArrayClassConstantCountsAsItsElementType() {
    byte[] bytes =
        fixtureClass(
            cw -> {
              MethodVisitor mv = method(cw, "m", "()V");
              mv.visitInsn(Opcodes.ACONST_NULL);
              mv.visitTypeInsn(Opcodes.CHECKCAST, "[[Lfixture/ArrayElement;");
              mv.visitInsn(Opcodes.POP);
              mv.visitInsn(Opcodes.RETURN);
              end(mv);
            });
    Set<String> types = referencedTypes("fixture/Root.class", bytes);
    assertTrue(types.contains("fixture/ArrayElement"), types.toString());
    for (String type : types) {
      assertFalse(type.startsWith("["), "array descriptor leaked: " + type);
    }
  }

  @Test
  void fixtureMethodDescriptorsAndMethodTypesAreRead() {
    byte[] bytes =
        fixtureClass(
            cw -> {
              MethodVisitor mv = method(cw, "m", "(Lfixture/Param;)[Lfixture/Result;");
              mv.visitInsn(Opcodes.ACONST_NULL);
              mv.visitMethodInsn(
                  Opcodes.INVOKESTATIC, "fixture/Owner", "call", "(Lfixture/CallArg;)V", false);
              mv.visitLdcInsn(Type.getMethodType("(Lfixture/LdcArg;)Lfixture/LdcResult;"));
              mv.visitInsn(Opcodes.POP);
              mv.visitInsn(Opcodes.ACONST_NULL);
              mv.visitInsn(Opcodes.ARETURN);
              end(mv);
            });
    Set<String> types = referencedTypes("fixture/Root.class", bytes);
    assertTrue(
        types.containsAll(
            Arrays.asList(
                "fixture/Param",
                "fixture/Result",
                "fixture/Owner",
                "fixture/CallArg",
                "fixture/LdcArg",
                "fixture/LdcResult")),
        types.toString());
  }

  @Test
  void fixtureUnusedFieldTypeIsRead() {
    byte[] bytes =
        fixtureClass(
            cw ->
                cw.visitField(Opcodes.ACC_PRIVATE, "unused", "Lfixture/FieldType;", null, null)
                    .visitEnd());
    assertTrue(referencedTypes("fixture/Root.class", bytes).contains("fixture/FieldType"));
  }

  @Test
  void fixtureInvokedynamicBootstrapArgumentsAreRead() {
    byte[] bytes =
        fixtureClass(
            cw -> {
              MethodVisitor mv = method(cw, "m", "()V");
              Handle bsm =
                  new Handle(
                      Opcodes.H_INVOKESTATIC,
                      "fixture/Bootstraps",
                      "bootstrap",
                      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                          + "Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)"
                          + "Ljava/lang/invoke/CallSite;",
                      false);
              mv.visitInvokeDynamicInsn(
                  "site",
                  "()V",
                  bsm,
                  Type.getObjectType("fixture/BsmClassArg"),
                  Type.getMethodType("(Lfixture/BsmMethodTypeArg;)V"));
              mv.visitInsn(Opcodes.RETURN);
              end(mv);
            });
    Set<String> types = referencedTypes("fixture/Root.class", bytes);
    assertTrue(
        types.containsAll(
            Arrays.asList("fixture/Bootstraps", "fixture/BsmClassArg", "fixture/BsmMethodTypeArg")),
        types.toString());
  }

  @Test
  void fixtureMalformedClassFailsNamingTheEntry() {
    AssertionError error =
        assertThrows(
            AssertionError.class,
            () -> referencedTypes("fixture/Broken.class", new byte[] {(byte) 0xCA, (byte) 0xFE}));
    assertTrue(error.getMessage().contains("fixture/Broken.class"), error.getMessage());
  }

  @Test
  void fixtureClosureReportsNewStaleAndDenyListedReferences() {
    Map<String, Set<String>> references = new TreeMap<>();
    references.put(
        "fixture/Root",
        new TreeSet<>(
            Arrays.asList(
                "java/lang/String",
                "fixture/Other",
                "java/sql/Connection",
                "io/btrace/libs/org/slf4j/Logger",
                "fixture/Masked")));
    references.put("fixture/Other", new TreeSet<>(Arrays.asList("fixture/Root")));
    Set<String> baseline =
        new TreeSet<>(
            Arrays.asList("fixture/Root -> fixture/Masked", "fixture/Root -> fixture/Gone"));
    Set<String> nonBoot = new HashSet<>(Arrays.asList("java/sql/"));

    List<String> errors = closureErrors(references, baseline, nonBoot);

    assertEquals(
        Arrays.asList(
            "new violation: fixture/Root -> io/btrace/libs/org/slf4j/Logger",
            "new violation: fixture/Root -> java/sql/Connection",
            "stale " + BASELINE + " entry (remove it): fixture/Root -> fixture/Gone"),
        errors);
  }

  @Test
  void fixtureDenyListMatchesOnlyTheListedPackage() {
    Set<String> nonBoot = new HashSet<>(Arrays.asList("javax/lang/model/"));
    Set<String> roots = Collections.emptySet();
    assertFalse(isBootstrapVisible("javax/lang/model/SourceVersion", roots, nonBoot));
    assertTrue(isBootstrapVisible("javax/lang/model/element/Element", roots, nonBoot));
    assertTrue(isBootstrapVisible("com/sun/management/OperatingSystemMXBean", roots, nonBoot));
    assertFalse(isBootstrapVisible("com/sun/tools/attach/VirtualMachine", roots, nonBoot));
  }

  @Test
  void fixtureAllowlistReportsUnlistedClassesAndUnusedLines() {
    List<String> allowlist =
        Arrays.asList("fixture/pkg/", "fixture/Single.class", "fixture/unused/", "bad-line");
    Set<String> entries =
        new TreeSet<>(
            Arrays.asList(
                "fixture/pkg/A.class",
                "fixture/pkg/sub/B.class",
                "fixture/Single.class",
                "fixture/Single$Nested.class",
                "fixture/SingleOther.class"));

    List<String> errors = allowlistErrors(allowlist, entries);

    assertEquals(
        Arrays.asList(
            ALLOWLIST + ": 'bad-line' is neither a package (ending in /) nor a .class entry",
            "root class not in " + ALLOWLIST + ": fixture/SingleOther.class",
            "root class not in " + ALLOWLIST + ": fixture/pkg/sub/B.class",
            ALLOWLIST + " line matches no root class (remove it): bad-line",
            ALLOWLIST + " line matches no root class (remove it): fixture/unused/"),
        errors);
  }

  @Test
  void fixtureBaselineLineWithoutReasonFails() {
    AssertionError error =
        assertThrows(
            AssertionError.class,
            () ->
                parseEntries(
                    BASELINE, Arrays.asList("# header", "", "a -> b  # reason", "c -> d"), true));
    assertTrue(error.getMessage().contains(BASELINE + ":4: entry has no '# reason'"));
    assertEquals(
        Arrays.asList("a -> b", "c -> d"),
        parseEntries(BASELINE, Arrays.asList("a -> b  # reason", "c -> d"), false));
  }

  // ---- the gate's logic ----

  static List<String> allowlistErrors(List<String> allowlist, Set<String> rootEntries) {
    List<String> errors = new ArrayList<>();
    for (String line : allowlist) {
      if (!line.endsWith("/") && !line.endsWith(".class")) {
        errors.add(
            ALLOWLIST + ": '" + line + "' is neither a package (ending in /) nor a .class entry");
      }
    }
    Set<String> unused = new TreeSet<>(allowlist);
    for (String entry : new TreeSet<>(rootEntries)) {
      boolean matched = false;
      for (String line : allowlist) {
        if (matches(line, entry)) {
          matched = true;
          unused.remove(line);
        }
      }
      if (!matched) {
        errors.add("root class not in " + ALLOWLIST + ": " + entry);
      }
    }
    for (String line : unused) {
      errors.add(ALLOWLIST + " line matches no root class (remove it): " + line);
    }
    return errors;
  }

  /**
   * Returns the new and stale violations, given each root class's referenced types by internal
   * name.
   */
  static List<String> closureErrors(
      Map<String, Set<String>> references, Set<String> baseline, Set<String> nonBootPackages) {
    Set<String> rootNames = references.keySet();
    Set<String> found = new TreeSet<>();
    for (Map.Entry<String, Set<String>> root : references.entrySet()) {
      for (String type : root.getValue()) {
        if (!isBootstrapVisible(type, rootNames, nonBootPackages)) {
          found.add(root.getKey() + " -> " + type);
        }
      }
    }
    List<String> errors = new ArrayList<>();
    for (String violation : found) {
      if (!baseline.contains(violation)) {
        errors.add("new violation: " + violation);
      }
    }
    for (String entry : new TreeSet<>(baseline)) {
      if (!found.contains(entry)) {
        errors.add("stale " + BASELINE + " entry (remove it): " + entry);
      }
    }
    return errors;
  }

  static boolean isBootstrapVisible(
      String internalName, Set<String> rootNames, Set<String> nonBootPackages) {
    if (rootNames.contains(internalName)) {
      return true;
    }
    String pkg = internalName.substring(0, internalName.lastIndexOf('/') + 1);
    if (nonBootPackages.contains(pkg)) {
      return false;
    }
    for (String prefix : JDK_PREFIXES) {
      if (internalName.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matches(String line, String entry) {
    if (line.endsWith("/")) {
      return entry.startsWith(line) && entry.indexOf('/', line.length()) < 0;
    }
    if (!line.endsWith(".class")) {
      return false;
    }
    String outer = line.substring(0, line.length() - ".class".length());
    return entry.equals(line) || entry.startsWith(outer + "$");
  }

  private static String internalName(String entry) {
    return entry.substring(0, entry.length() - ".class".length());
  }

  /** Returns the root classes of the built jar, by entry name, with their bytes. */
  private static Map<String, byte[]> readRootClasses() throws IOException {
    File jar = locateBTraceJar();
    Map<String, byte[]> classes = new TreeMap<>();
    try (JarFile jarFile = new JarFile(jar)) {
      Attributes attributes = jarFile.getManifest().getMainAttributes();
      assertNull(
          attributes.getValue("Multi-Release"),
          "btrace.jar is a multi-release jar; versioned entries would be bootstrap-visible and"
              + " this test does not check them");
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if (entry.isDirectory() || name.startsWith("META-INF/") || !name.endsWith(".class")) {
          continue;
        }
        try (InputStream in = jarFile.getInputStream(entry)) {
          classes.put(name, readAll(in));
        }
      }
    }
    assertFalse(classes.isEmpty(), jar + " has no root classes");
    return classes;
  }

  /** Locates the assembled jar the same way {@link BTraceJarPackagingTest} does. */
  private static File locateBTraceJar() {
    File resourcesDir = new File("build/resources/main");
    File[] versionDirs = resourcesDir.listFiles(File::isDirectory);
    assertNotNull(versionDirs, "Expected versioned dist directory under build/resources/main");
    assertEquals(1, versionDirs.length, "Expected exactly one versioned dist directory");
    File btraceJar = new File(versionDirs[0], "libs/btrace.jar");
    assertTrue(btraceJar.isFile(), "Expected assembled btrace.jar at " + btraceJar);
    return btraceJar;
  }

  /**
   * Returns the internal names of the non-primitive types the class file references. A class file
   * ASM cannot parse fails with an {@link AssertionError} naming the entry.
   */
  static Set<String> referencedTypes(String entry, byte[] bytes) {
    try {
      return parseReferencedTypes(bytes);
    } catch (RuntimeException e) {
      throw new AssertionError("cannot read root class " + entry + ": " + e, e);
    }
  }

  private static Set<String> parseReferencedTypes(byte[] bytes) {
    ClassReader reader = new ClassReader(bytes);
    Set<String> types = new TreeSet<>();
    char[] buffer = new char[reader.getMaxStringLength()];
    for (int i = 1; i < reader.getItemCount(); i++) {
      int offset = reader.getItem(i);
      if (offset == 0) {
        continue; // the unused slot after a long or double constant
      }
      switch (reader.readByte(offset - 1)) {
        case CONSTANT_CLASS:
          String name = reader.readUTF8(offset, buffer);
          if (name.startsWith("[")) {
            addType(Type.getType(name), types);
          } else {
            types.add(name);
          }
          break;
        case CONSTANT_NAME_AND_TYPE:
          addDescriptor(reader.readUTF8(offset + 2, buffer), types);
          break;
        case CONSTANT_METHOD_TYPE:
          addDescriptor(reader.readUTF8(offset, buffer), types);
          break;
        default:
          break;
      }
    }
    reader.accept(
        new ClassVisitor(Opcodes.ASM9) {
          @Override
          public FieldVisitor visitField(
              int access, String name, String descriptor, String signature, Object value) {
            addDescriptor(descriptor, types);
            return null;
          }

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            addDescriptor(descriptor, types);
            return null;
          }
        },
        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return types;
  }

  private static void addDescriptor(String descriptor, Set<String> types) {
    if (descriptor.startsWith("(")) {
      Type method = Type.getMethodType(descriptor);
      addType(method.getReturnType(), types);
      for (Type argument : method.getArgumentTypes()) {
        addType(argument, types);
      }
    } else {
      addType(Type.getType(descriptor), types);
    }
  }

  private static void addType(Type type, Set<String> types) {
    Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
    if (element.getSort() == Type.OBJECT) {
      types.add(element.getInternalName());
    }
  }

  /** Reads the entries of a test resource; see {@link #parseEntries}. */
  private static List<String> readEntries(String resource, boolean reasonRequired)
      throws IOException {
    InputStream in = BootstrapClosureTest.class.getResourceAsStream("/" + resource);
    assertNotNull(in, "missing test resource " + resource);
    List<String> lines = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
    }
    return parseEntries(resource, lines, reasonRequired);
  }

  /**
   * Blank lines and lines starting with {@code #} are ignored, and a trailing {@code # reason} is
   * stripped. When {@code reasonRequired} is set, every entry must carry a reason. A duplicate
   * entry fails.
   */
  static List<String> parseEntries(String source, List<String> lines, boolean reasonRequired) {
    Map<String, Integer> entries = new LinkedHashMap<>();
    int number = 0;
    for (String line : lines) {
      number++;
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      String[] parts = trimmed.split("\\s+#", 2);
      String entry = parts[0];
      if (reasonRequired && (parts.length < 2 || parts[1].trim().isEmpty())) {
        fail(source + ":" + number + ": entry has no '# reason': " + trimmed);
      }
      if (entries.put(entry, number) != null) {
        fail(source + ":" + number + ": duplicate entry: " + entry);
      }
    }
    return new ArrayList<>(entries.keySet());
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int read;
    while ((read = in.read(chunk)) != -1) {
      out.write(chunk, 0, read);
    }
    return out.toByteArray();
  }

  // ---- fixture class builders ----

  private interface ClassBody {
    void write(ClassWriter cw);
  }

  private static byte[] fixtureClass(ClassBody body) {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/Root", null, "java/lang/Object", null);
    body.write(cw);
    cw.visitEnd();
    return cw.toByteArray();
  }

  private static MethodVisitor method(ClassWriter cw, String name, String descriptor) {
    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, name, descriptor, null, null);
    mv.visitCode();
    return mv;
  }

  private static void end(MethodVisitor mv) {
    mv.visitMaxs(4, 4);
    mv.visitEnd();
  }
}
