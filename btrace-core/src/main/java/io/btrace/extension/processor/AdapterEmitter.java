/*
 * Copyright (c) 2008, 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.extension.processor;

import java.io.PrintWriter;

final class AdapterEmitter {
  private final AdapterSpec spec;

  AdapterEmitter(AdapterSpec spec) {
    this.spec = spec;
  }

  void render(PrintWriter w) {
    if (!spec.pkg.isEmpty()) w.println("package " + spec.pkg + ";");
    w.println();
    w.println("import java.lang.invoke.MethodHandle;");
    w.println("import java.lang.invoke.MethodHandles;");
    w.println("import java.lang.invoke.MethodType;");
    w.println("import java.lang.ref.ReferenceQueue;");
    w.println("import java.lang.ref.WeakReference;");
    w.println("import java.util.HashMap;");
    w.println("import java.util.Iterator;");
    w.println("import java.util.Map;");
    w.println("import io.btrace.core.extensions.ExternalTypeResolutionException;");
    w.println();
    w.println("public final class " + spec.adapterSimpleName() + " {");
    w.println("  private static final String OWNER = \"" + javaString(spec.externalFqn) + "\";");
    w.println("  private " + spec.adapterSimpleName() + "() {}");
    renderResolvedCall(w);
    renderLoaderKey(w);
    for (int i = 0; i < spec.methods.size(); i++) renderMethod(w, spec.methods.get(i), i);
    renderSneak(w);
    w.println("}");
  }

  private void renderResolvedCall(PrintWriter w) {
    w.println();
    w.println("  private static final class ResolvedCall {");
    w.println("    final Class<?> owner;");
    w.println("    final MethodHandle handle;");
    w.println();
    w.println("    ResolvedCall(Class<?> owner, MethodHandle handle) {");
    w.println("      this.owner = owner;");
    w.println("      this.handle = handle;");
    w.println("    }");
    w.println("  }");
  }

  private void renderLoaderKey(PrintWriter w) {
    w.println();
    w.println("  private static final class LoaderKey extends WeakReference<ClassLoader> {");
    w.println("    private static final int BOOTSTRAP = 1;");
    w.println("    private static final int SYSTEM = 2;");
    w.println("    private final int hash;");
    w.println("    private final int sentinel;");
    w.println();
    w.println("    LoaderKey(ClassLoader loader, ReferenceQueue<ClassLoader> queue) {");
    w.println("      super(loader, queue);");
    w.println("      hash = System.identityHashCode(loader);");
    w.println("      sentinel = 0;");
    w.println("    }");
    w.println();
    w.println("    LoaderKey(ClassLoader loader) {");
    w.println("      super(loader);");
    w.println("      hash = System.identityHashCode(loader);");
    w.println("      sentinel = 0;");
    w.println("    }");
    w.println();
    w.println("    LoaderKey(int sentinel) {");
    w.println("      super(null);");
    w.println("      hash = sentinel;");
    w.println("      this.sentinel = sentinel;");
    w.println("    }");
    w.println();
    w.println("    boolean isWeak() {");
    w.println("      return sentinel == 0;");
    w.println("    }");
    w.println();
    w.println("    @Override");
    w.println("    public boolean equals(Object other) {");
    w.println("      if (this == other) return true;");
    w.println("      if (!(other instanceof LoaderKey)) return false;");
    w.println("      LoaderKey that = (LoaderKey) other;");
    w.println("      if (sentinel != 0 || that.sentinel != 0) return sentinel == that.sentinel;");
    w.println("      ClassLoader loader = get();");
    w.println("      return loader != null && loader == that.get();");
    w.println("    }");
    w.println();
    w.println("    @Override");
    w.println("    public int hashCode() {");
    w.println("      return hash;");
    w.println("    }");
    w.println("  }");
  }

  private void renderMethod(PrintWriter w, MethodSpec method, int ordinal) {
    if (method.operation == MethodSpec.OperationKind.INSTANCE_OF
        || method.operation == MethodSpec.OperationKind.CAST) {
      renderPredicate(w, method, ordinal);
      return;
    }
    String cache = "$" + ordinal + "$calls";
    String attempts = "__btraceExternalTypeResolutionAttempts$" + ordinal;
    String[] lists = paramAndArgLists(method);
    w.println();
    w.println("  private static int " + attempts + ";");
    w.println("  private static final ClassValue<ResolvedCall> " + cache + " =");
    w.println("      new ClassValue<ResolvedCall>() {");
    w.println("        @Override");
    w.println(
        "        protected ResolvedCall computeValue(Class<?> "
            + (loaderOperation(method) ? "owner" : "receiver")
            + ") {");
    w.println("          try {");
    if (!loaderOperation(method)) {
      w.println(
          "            Class<?> owner = Class.forName(OWNER, false, receiver.getClassLoader());");
    }
    renderMarkedTypes(w, method, ordinal);
    w.println("            " + attempts + "++;");
    w.println(
        "            return new ResolvedCall(owner, " + lookupExpression(method, ordinal) + ");");
    w.println("          } catch (" + resolutionExceptions(method) + " e) {");
    w.println(
        "            throw new ExternalTypeResolutionException(OWNER, \""
            + javaString(method.targetName)
            + "\", e);");
    w.println("          }");
    w.println("        }");
    w.println("      };");
    if (loaderOperation(method)) renderStaticSupport(w, method, ordinal, cache);
    w.println();
    w.println(
        "  public static " + method.returnType + " " + method.adapterName + "(" + lists[0] + ") {");
    w.println("    try {");
    if (loaderOperation(method)) {
      w.println(
          "      ResolvedCall call = $"
              + ordinal
              + "$resolveStatic($"
              + ordinal
              + "$legacyStaticLoader());");
    } else {
      w.println("      ResolvedCall call = " + cache + ".get(self.getClass());");
    }
    w.print("      ");
    if (!"void".equals(method.returnType)) w.print("return (" + method.returnType + ") ");
    w.println("call.handle.invoke(" + lists[1] + ");");
    w.println("    } catch (Throwable t) { throw sneak(t); }");
    w.println("  }");
    if (loaderOperation(method)) {
      String explicitParameters = "ClassLoader applicationLoader";
      if (!lists[0].isEmpty()) explicitParameters += ", " + lists[0];
      w.println();
      w.println(
          "  public static "
              + method.returnType
              + " "
              + method.adapterName
              + "("
              + explicitParameters
              + ") {");
      w.println(
          "    if (applicationLoader == null) throw new NullPointerException(\"applicationLoader\");");
      w.println("    try {");
      w.println("      ResolvedCall call = $" + ordinal + "$resolveStatic(applicationLoader);");
      w.print("      ");
      if (!"void".equals(method.returnType)) w.print("return (" + method.returnType + ") ");
      w.println("call.handle.invoke(" + lists[1] + ");");
      w.println("    } catch (Throwable t) { throw sneak(t); }");
      w.println("  }");
    }
  }

  private void renderPredicate(PrintWriter w, MethodSpec method, int ordinal) {
    String cache = "$" + ordinal + "$owners";
    String attempts = "__btraceExternalTypeResolutionAttempts$" + ordinal;
    w.println();
    w.println("  private static int " + attempts + ";");
    w.println("  private static final ClassValue<Class<?>> " + cache + " =");
    w.println("      new ClassValue<Class<?>>() {");
    w.println("        @Override");
    w.println("        protected Class<?> computeValue(Class<?> valueType) {");
    w.println("          try {");
    w.println("            " + attempts + "++;");
    w.println(
        "            Class<?> owner = Class.forName(OWNER, false, valueType.getClassLoader());");
    w.println(
        "            MethodHandles.publicLookup().findVirtual(owner, \"getClass\", MethodType.methodType(Class.class));");
    w.println("            return owner;");
    w.println(
        "          } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {");
    w.println(
        "            throw new ExternalTypeResolutionException(OWNER, \""
            + javaString(method.targetName)
            + "\", e);");
    w.println("          }");
    w.println("        }");
    w.println("      };");
    w.println();
    w.println(
        "  public static "
            + method.returnType
            + " "
            + method.adapterName
            + "(java.lang.Object p0) {");
    if (method.operation == MethodSpec.OperationKind.INSTANCE_OF) {
      w.println("    if (p0 == null) return false;");
      w.println("    return " + cache + ".get(p0.getClass()).isInstance(p0);");
    } else {
      w.println("    if (p0 == null) return null;");
      w.println("    return " + cache + ".get(p0.getClass()).cast(p0);");
    }
    w.println("  }");
  }

  private String lookupExpression(MethodSpec method, int ordinal) {
    String name = "\"" + javaString(method.targetName) + "\"";
    if (method.operation == MethodSpec.OperationKind.GETTER) {
      return "MethodHandles.publicLookup()."
          + (method.isStatic ? "findStaticGetter" : "findGetter")
          + "(owner, "
          + name
          + ", "
          + fieldTypeLiteral(method, ordinal)
          + ")";
    }
    if (method.operation == MethodSpec.OperationKind.SETTER) {
      return "MethodHandles.publicLookup()."
          + (method.isStatic ? "findStaticSetter" : "findSetter")
          + "(owner, "
          + name
          + ", "
          + fieldTypeLiteral(method, ordinal)
          + ")";
    }
    if (method.operation == MethodSpec.OperationKind.CONSTRUCTOR) {
      return "MethodHandles.publicLookup().findConstructor(owner, "
          + constructorTypeLiteral(method, ordinal)
          + ")";
    }
    return "MethodHandles.publicLookup()."
        + (method.isStatic ? "findStatic" : "findVirtual")
        + "(owner, "
        + name
        + ", "
        + methodTypeLiteral(method, ordinal)
        + ")";
  }

  private boolean loaderOperation(MethodSpec method) {
    return method.isStatic || method.operation == MethodSpec.OperationKind.CONSTRUCTOR;
  }

  private void renderStaticSupport(PrintWriter w, MethodSpec method, int ordinal, String cache) {
    String prefix = "$" + ordinal + "$";
    w.println();
    w.println("  private static final Object " + prefix + "monitor = new Object();");
    w.println(
        "  private static final ReferenceQueue<ClassLoader> "
            + prefix
            + "loaderQueue = new ReferenceQueue<ClassLoader>();");
    w.println(
        "  private static final Map<LoaderKey, WeakReference<Class<?>>> "
            + prefix
            + "loaderIndex = new HashMap<LoaderKey, WeakReference<Class<?>>>();");
    w.println(
        "  private static final LoaderKey "
            + prefix
            + "bootstrapKey = new LoaderKey(LoaderKey.BOOTSTRAP);");
    w.println(
        "  private static final LoaderKey "
            + prefix
            + "systemKey = new LoaderKey(LoaderKey.SYSTEM);");
    w.println();
    w.println("  private static ClassLoader $" + ordinal + "$legacyStaticLoader() {");
    w.println("    ClassLoader loader = Thread.currentThread().getContextClassLoader();");
    w.println("    return loader != null ? loader : ClassLoader.getSystemClassLoader();");
    w.println("  }");
    w.println();
    w.println("  private static ResolvedCall $" + ordinal + "$resolveStatic(ClassLoader loader) {");
    w.println("    synchronized (" + prefix + "monitor) {");
    w.println("      $" + ordinal + "$expungeStatic();");
    w.println("      LoaderKey lookupKey = $" + ordinal + "$loaderKey(loader, false);");
    w.println(
        "      WeakReference<Class<?>> ownerReference = " + prefix + "loaderIndex.get(lookupKey);");
    w.println("      Class<?> owner = ownerReference != null ? ownerReference.get() : null;");
    w.println("      if (owner != null) return " + cache + ".get(owner);");
    w.println("      try {");
    w.println("        owner = Class.forName(OWNER, false, loader);");
    w.println("      } catch (ClassNotFoundException e) {");
    w.println(
        "        throw new ExternalTypeResolutionException(OWNER, \""
            + javaString(method.targetName)
            + "\", e);");
    w.println("      }");
    w.println("      ResolvedCall call = " + cache + ".get(owner);");
    w.println(
        "      "
            + prefix
            + "loaderIndex.put($"
            + ordinal
            + "$loaderKey(loader, true), new WeakReference<Class<?>>(owner));");
    w.println("      return call;");
    w.println("    }");
    w.println("  }");
    w.println();
    w.println(
        "  private static LoaderKey $"
            + ordinal
            + "$loaderKey(ClassLoader loader, boolean stored) {");
    w.println("    if (loader == null) return " + prefix + "bootstrapKey;");
    w.println(
        "    if (loader == ClassLoader.getSystemClassLoader()) return " + prefix + "systemKey;");
    w.println(
        "    return stored ? new LoaderKey(loader, "
            + prefix
            + "loaderQueue) : new LoaderKey(loader);");
    w.println("  }");
    w.println();
    w.println("  private static void $" + ordinal + "$expungeStatic() {");
    w.println("    LoaderKey stale;");
    w.println(
        "    while ((stale = (LoaderKey) "
            + prefix
            + "loaderQueue.poll()) != null) "
            + prefix
            + "loaderIndex.remove(stale);");
    w.println(
        "    for (Iterator<Map.Entry<LoaderKey, WeakReference<Class<?>>>> it = "
            + prefix
            + "loaderIndex.entrySet().iterator(); it.hasNext();) {");
    w.println("      Map.Entry<LoaderKey, WeakReference<Class<?>>> entry = it.next();");
    w.println(
        "      if ((entry.getKey().isWeak() && entry.getKey().get() == null) || entry.getValue().get() == null) it.remove();");
    w.println("    }");
    w.println("  }");
  }

  private void renderMarkedTypes(PrintWriter w, MethodSpec method, int ordinal) {
    if (method.returnTargetFqn != null) {
      w.println(
          "            Class<?> $"
              + ordinal
              + "$returnType = Class.forName(\""
              + javaString(method.returnTargetFqn)
              + "\", false, owner.getClassLoader());");
    }
    for (int i = 0; i < method.paramTargetFqns.size(); i++) {
      String fqn = method.paramTargetFqns.get(i);
      if (fqn != null)
        w.println(
            "            Class<?> $"
                + ordinal
                + "$parameterType"
                + i
                + " = Class.forName(\""
                + javaString(fqn)
                + "\", false, owner.getClassLoader());");
    }
  }

  private String resolutionExceptions(MethodSpec method) {
    if (method.operation == MethodSpec.OperationKind.GETTER
        || method.operation == MethodSpec.OperationKind.SETTER) {
      if (!loaderOperation(method)
          || method.returnTargetFqn != null
          || hasMarkedParameter(method)) {
        return "ClassNotFoundException | NoSuchFieldException | IllegalAccessException";
      }
      return "NoSuchFieldException | IllegalAccessException";
    }
    if (!loaderOperation(method) || method.returnTargetFqn != null || hasMarkedParameter(method)) {
      return "ClassNotFoundException | NoSuchMethodException | IllegalAccessException";
    }
    return "NoSuchMethodException | IllegalAccessException";
  }

  private boolean hasMarkedParameter(MethodSpec method) {
    for (String fqn : method.paramTargetFqns) if (fqn != null) return true;
    return false;
  }

  private String fieldTypeLiteral(MethodSpec method, int ordinal) {
    if (method.operation == MethodSpec.OperationKind.GETTER)
      return method.returnTargetFqn == null
          ? method.returnType + ".class"
          : "$" + ordinal + "$returnType";
    return method.paramTargetFqns.get(0) == null
        ? method.paramTypes.get(0) + ".class"
        : "$" + ordinal + "$parameterType0";
  }

  private String constructorTypeLiteral(MethodSpec method, int ordinal) {
    StringBuilder result = new StringBuilder("MethodType.methodType(void.class");
    for (int i = 0; i < method.paramTypes.size(); i++)
      result.append(", ").append(typeLiteral(method, ordinal, i));
    return result.append(")").toString();
  }

  private String methodTypeLiteral(MethodSpec method, int ordinal) {
    StringBuilder result = new StringBuilder("MethodType.methodType(");
    result.append(
        method.returnTargetFqn == null
            ? method.returnType + ".class"
            : "$" + ordinal + "$returnType");
    for (int i = 0; i < method.paramTypes.size(); i++)
      result.append(", ").append(typeLiteral(method, ordinal, i));
    return result.append(")").toString();
  }

  private String typeLiteral(MethodSpec method, int ordinal, int parameter) {
    return method.paramTargetFqns.get(parameter) == null
        ? method.paramTypes.get(parameter) + ".class"
        : "$" + ordinal + "$parameterType" + parameter;
  }

  private String[] paramAndArgLists(MethodSpec method) {
    StringBuilder parameters = new StringBuilder();
    StringBuilder arguments = new StringBuilder();
    if (!loaderOperation(method)) {
      parameters.append("java.lang.Object self");
      arguments.append("self");
    }
    for (int i = 0; i < method.paramTypes.size(); i++) {
      if (parameters.length() > 0) parameters.append(", ");
      if (arguments.length() > 0) arguments.append(", ");
      parameters.append(method.paramTypes.get(i)).append(" p").append(i);
      arguments.append("p").append(i);
    }
    return new String[] {parameters.toString(), arguments.toString()};
  }

  private void renderSneak(PrintWriter w) {
    w.println();
    w.println("  @SuppressWarnings(\"unchecked\")");
    w.println(
        "  private static <T extends Throwable> RuntimeException sneak(Throwable t) throws T {");
    w.println("    throw (T) t;");
    w.println("  }");
  }

  private String javaString(String text) {
    StringBuilder escaped = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\') escaped.append("\\\\");
      else if (c == '\"') escaped.append("\\\"");
      else if (c == '\n') escaped.append("\\n");
      else if (c == '\r') escaped.append("\\r");
      else if (c == '\t') escaped.append("\\t");
      else if (c < 0x20 || c == 0x7f) escaped.append(String.format("\\u%04x", (int) c));
      else escaped.append(c);
    }
    return escaped.toString();
  }
}
