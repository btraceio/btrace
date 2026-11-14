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
import org.objectweb.asm.FieldVisitor;
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
 * CONSTANT_MethodType}) plus the descriptors of its declared fields and methods. Array types count
 * as their element type and primitive types are ignored. Annotation descriptors and generic
 * signatures are not counted: a missing annotation type is skipped by reflection and a signature is
 * erased, so neither can fail linking. A referenced type is bootstrap-visible when it is a JDK type
 * (internal name under {@code java/}, {@code javax/}, {@code jdk/}, {@code sun/} or {@code
 * com/sun/management/}, which is in {@code rt.jar} on JDK 8 and in the boot-loader module {@code
 * jdk.management} from JDK 9), an SLF4J type in the package the distribution relocates it to
 * ({@code io/btrace/libs/org/slf4j/}, from {@code relocate 'org.slf4j', 'io.btrace.libs.org.slf4j'}
 * in {@code btrace-dist/build.gradle}), or another root class. Any other reference must be listed
 * in {@code bootstrap-closure-baseline.txt} as {@code <root class> -> <referenced class> # reason},
 * in internal names. An unlisted reference fails as new and a listed one that no longer occurs
 * fails as stale, so the baseline only shrinks.
 */
class BootstrapClosureTest {
  private static final String ALLOWLIST = "bootstrap-root-allowlist.txt";
  private static final String BASELINE = "bootstrap-closure-baseline.txt";

  private static final List<String> JDK_PREFIXES =
      Collections.unmodifiableList(
          Arrays.asList("java/", "javax/", "jdk/", "sun/", "com/sun/management/"));
  private static final String RELOCATED_SLF4J = "io/btrace/libs/org/slf4j/";

  private static final int CONSTANT_CLASS = 7;
  private static final int CONSTANT_NAME_AND_TYPE = 12;
  private static final int CONSTANT_METHOD_TYPE = 16;

  @Test
  void rootClassesMatchTheAllowlist() throws IOException {
    List<String> allowlist = readEntries(ALLOWLIST, false);
    assertFalse(allowlist.isEmpty(), ALLOWLIST + " lists nothing");
    for (String line : allowlist) {
      assertTrue(
          line.endsWith("/") || line.endsWith(".class"),
          ALLOWLIST + ": '" + line + "' is neither a package (ending in /) nor a .class entry");
    }

    Set<String> rootClasses = readRootClasses().keySet();
    List<String> unlisted = new ArrayList<>();
    Set<String> unused = new TreeSet<>(allowlist);
    for (String entry : rootClasses) {
      boolean matched = false;
      for (String line : allowlist) {
        if (matches(line, entry)) {
          matched = true;
          unused.remove(line);
        }
      }
      if (!matched) {
        unlisted.add(entry);
      }
    }

    List<String> errors = new ArrayList<>();
    for (String entry : unlisted) {
      errors.add("root class not in " + ALLOWLIST + ": " + entry);
    }
    for (String line : unused) {
      errors.add(ALLOWLIST + " line matches no root class (remove it): " + line);
    }
    assertTrue(errors.isEmpty(), String.join("\n", errors));
  }

  @Test
  void rootClassesReferenceOnlyBootstrapVisibleTypes() throws IOException {
    Map<String, byte[]> rootClasses = readRootClasses();
    Set<String> rootNames = new TreeSet<>();
    for (String entry : rootClasses.keySet()) {
      rootNames.add(internalName(entry));
    }

    Set<String> found = new TreeSet<>();
    for (Map.Entry<String, byte[]> root : rootClasses.entrySet()) {
      String rootName = internalName(root.getKey());
      for (String type : referencedTypes(root.getKey(), root.getValue())) {
        if (!isJdkType(type) && !type.startsWith(RELOCATED_SLF4J) && !rootNames.contains(type)) {
          found.add(rootName + " -> " + type);
        }
      }
    }

    Set<String> listed = new TreeSet<>(readEntries(BASELINE, true));
    List<String> errors = new ArrayList<>();
    for (String violation : found) {
      if (!listed.contains(violation)) {
        errors.add("new violation: " + violation);
      }
    }
    for (String entry : listed) {
      if (!found.contains(entry)) {
        errors.add("stale " + BASELINE + " entry (remove it): " + entry);
      }
    }
    assertTrue(errors.isEmpty(), String.join("\n", errors));
  }

  private static boolean matches(String line, String entry) {
    if (line.endsWith("/")) {
      return entry.startsWith(line) && entry.indexOf('/', line.length()) < 0;
    }
    String outer = line.substring(0, line.length() - ".class".length());
    return entry.equals(line) || entry.startsWith(outer + "$");
  }

  private static boolean isJdkType(String internalName) {
    for (String prefix : JDK_PREFIXES) {
      if (internalName.startsWith(prefix)) {
        return true;
      }
    }
    return false;
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

  /** Returns the internal names of the non-primitive types the class file references. */
  private static Set<String> referencedTypes(String entry, byte[] bytes) {
    ClassReader reader;
    try {
      reader = new ClassReader(bytes);
    } catch (RuntimeException e) {
      throw new AssertionError("cannot read root class " + entry, e);
    }
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

  /**
   * Reads the entries of a test resource: blank lines and lines starting with {@code #} are
   * ignored, and a trailing {@code # reason} is stripped. When {@code reasonRequired} is set, every
   * entry must carry a reason.
   */
  private static List<String> readEntries(String resource, boolean reasonRequired)
      throws IOException {
    InputStream in = BootstrapClosureTest.class.getResourceAsStream("/" + resource);
    assertNotNull(in, "missing test resource " + resource);
    Map<String, Integer> entries = new LinkedHashMap<>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      int number = 0;
      while ((line = reader.readLine()) != null) {
        number++;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }
        String[] parts = trimmed.split("\\s+#", 2);
        String entry = parts[0];
        if (reasonRequired && (parts.length < 2 || parts[1].trim().isEmpty())) {
          fail(resource + ":" + number + ": entry has no '# reason': " + trimmed);
        }
        if (entries.put(entry, number) != null) {
          fail(resource + ":" + number + ": duplicate entry: " + entry);
        }
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
}
