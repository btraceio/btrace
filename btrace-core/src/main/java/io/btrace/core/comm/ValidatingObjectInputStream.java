/*
 * Copyright (c) 2008, 2015, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.core.comm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Hardened {@link ObjectInputStream} that enforces an allow-list on deserialized classes.
 *
 * <p>This is a defense-in-depth layer for safe deserialization. Even after migrating command
 * payloads to type-safe encoding, the underlying stream could still be abused if a future command
 * re-introduces object serialization. The filter ensures only expected BTrace command types and
 * primitive wrappers are materialized.
 *
 * <p>On JDK 9+ an {@code ObjectInputFilter} is also installed via reflection for additional
 * protection, but the {@code resolveClass} override is authoritative and works on Java 8.
 */
public class ValidatingObjectInputStream extends ObjectInputStream {

  // Explicit allow-list: BTrace commands + safe JDK types.
  // Any other class triggers InvalidClassException before callbacks run.
  private static final Set<String> ALLOWED_CLASS_NAMES;

  static {
    Set<String> s = new HashSet<>();
    // BTrace commands
    s.add(Command.class.getName());
    s.add(ErrorCommand.class.getName());
    s.add(EventCommand.class.getName());
    s.add(ExitCommand.class.getName());
    s.add(InstrumentCommand.class.getName());
    s.add(MessageCommand.class.getName());
    s.add(RenameCommand.class.getName());
    s.add(StatusCommand.class.getName());
    s.add(NumberMapDataCommand.class.getName());
    s.add(StringMapDataCommand.class.getName());
    s.add(NumberDataCommand.class.getName());
    s.add(GridDataCommand.class.getName());
    s.add(RetransformationStartNotification.class.getName());
    s.add(RetransformClassNotification.class.getName());
    s.add(SetSettingsCommand.class.getName());
    s.add(ListProbesCommand.class.getName());
    s.add(DisconnectCommand.class.getName());
    s.add(ReconnectCommand.class.getName());
    // JDK safe types - primitives and wrappers, String, arrays, collections, Big* types
    s.add(String.class.getName());
    s.add(Boolean.class.getName());
    s.add(Byte.class.getName());
    s.add(Short.class.getName());
    s.add(Integer.class.getName());
    s.add(Long.class.getName());
    s.add(Float.class.getName());
    s.add(Double.class.getName());
    s.add(BigInteger.class.getName());
    s.add(BigDecimal.class.getName());
    s.add(Number.class.getName());
    s.add(HashMap.class.getName());
    s.add(ArrayList.class.getName());
    s.add(LinkedHashMap.class.getName());
    s.add(Collections.EMPTY_MAP.getClass().getName());
    // Throwable graph is intentionally NOT allowed by default - ErrorCommand now uses safe
    // encoding.
    // Keep StackTraceElement for potential filtered error reporting.
    s.add(StackTraceElement.class.getName());
    s.add(StackTraceElement[].class.getName());
    s.add(Object[].class.getName());
    s.add(byte[].class.getName());
    s.add(String[].class.getName());
    s.add(int[].class.getName());
    s.add(long[].class.getName());
    ALLOWED_CLASS_NAMES = Collections.unmodifiableSet(s);
  }

  // Also allow array variants and primitives
  private static final Set<String> ALLOWED_PREFIXES;

  static {
    Set<String> p = new HashSet<>();
    p.add("[Ljava.lang.String;");
    p.add("[Ljava.lang.Object;");
    p.add("[[Ljava.lang.Object;");
    p.add("[B"); // byte[]
    // Array component types that are explicitly allowed
    p.add("[C");
    p.add("[I");
    p.add("[J");
    p.add("[D");
    p.add("[F");
    p.add("[S");
    p.add("[Z");
    ALLOWED_PREFIXES = Collections.unmodifiableSet(p);
  }

  public ValidatingObjectInputStream(InputStream in) throws IOException {
    super(in);
    tryInstallObjectInputFilter();
  }

  /** Fallback constructor for wrapping - not used directly but required for completeness. */
  ValidatingObjectInputStream() throws IOException {
    super();
  }

  @Override
  protected Class<?> resolveClass(ObjectStreamClass desc)
      throws IOException, ClassNotFoundException {
    String name = desc.getName();
    // Primitives are encoded as single-char or array descriptors - allow them
    if (isAllowed(name)) {
      return super.resolveClass(desc);
    }
    throw new InvalidClassException("Unauthorized deserialization: " + name);
  }

  private static boolean isAllowed(String className) {
    if (ALLOWED_CLASS_NAMES.contains(className)) {
      return true;
    }
    // Allow array types whose component is allowed
    if (className.startsWith("[")) {
      // Check prefixes
      for (String prefix : ALLOWED_PREFIXES) {
        if (className.equals(prefix)) return true;
      }
      // Generic array of allowed type e.g. [Ljava.util.HashMap;
      // For now, be conservative - only allow Object[] and String[] already listed
      return false;
    }
    // Primitive wrappers already covered; primitive names like "int" are allowed
    if (className.equals("int")
        || className.equals("long")
        || className.equals("double")
        || className.equals("float")
        || className.equals("byte")
        || className.equals("short")
        || className.equals("boolean")
        || className.equals("char")
        || className.equals("void")) {
      return true;
    }
    return false;
  }

  /**
   * Try to install JDK 9+ ObjectInputFilter via reflection. No-op on Java 8. We set a filter that
   * mirrors the allow-list to get early rejection with better diagnostics.
   */
  private void tryInstallObjectInputFilter() {
    try {
      Class<?> filterClass = Class.forName("java.io.ObjectInputFilter");
      // ObjectInputFilter.Config.createFilter("...") and setObjectInputFilter
      Class<?> configClass = Class.forName("java.io.ObjectInputFilter$Config");
      Method createFilter = configClass.getMethod("createFilter", String.class);
      // Build filter string: allow-list + reject all else
      StringBuilder pattern = new StringBuilder();
      for (String cn : ALLOWED_CLASS_NAMES) {
        if (pattern.length() > 0) pattern.append(';');
        pattern.append(cn);
      }
      // Add wildcards for BTrace commands subpackage if needed
      pattern.append(";io.btrace.core.comm.*");
      pattern.append(";!*");
      Object filter = createFilter.invoke(null, pattern.toString());
      Method setFilter = ObjectInputStream.class.getMethod("setObjectInputFilter", filterClass);
      setFilter.invoke(this, filter);
    } catch (Exception ignored) {
      // Java 8 or filter not available - resolveClass is sufficient
    }
  }

  /**
   * Factory helper to create a validating stream from a socket InputStream. Handles the
   * ObjectInputStream header handshake correctly.
   */
  public static ValidatingObjectInputStream create(InputStream rawIn) throws IOException {
    return new ValidatingObjectInputStream(rawIn);
  }
}
