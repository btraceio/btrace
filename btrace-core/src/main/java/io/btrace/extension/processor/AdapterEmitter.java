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
    w.println("  private static final String OWNER = \"" + spec.externalFqn + "\";");
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

  private void renderMethod(PrintWriter w, MethodSpec m, int ordinal) {
    String cache = "$" + ordinal + "$calls";
    String attempts = "__btraceExternalTypeResolutionAttempts$" + ordinal;
    String[] lists = paramAndArgLists(m);
    String paramList = lists[0];
    String argList = lists[1];

    w.println();
    w.println("  private static int " + attempts + ";");
    w.println("  private static final ClassValue<ResolvedCall> " + cache + " =");
    w.println("      new ClassValue<ResolvedCall>() {");
    w.println("        @Override");
    w.println(
        "        protected ResolvedCall computeValue(Class<?> "
            + (m.isStatic ? "owner" : "receiver")
            + ") {");
    w.println("          try {");
    if (!m.isStatic) {
      w.println(
          "            Class<?> owner = Class.forName(OWNER, false, receiver.getClassLoader());");
    }
    renderMarkedTypes(w, m, ordinal);
    w.println("            " + attempts + "++;");
    if (m.isStatic) {
      w.println(
          "            return new ResolvedCall(owner, MethodHandles.publicLookup().findStatic(owner, \""
              + m.name
              + "\", "
              + methodTypeLiteral(m, ordinal)
              + ")); ");
    } else {
      w.println(
          "            return new ResolvedCall(owner, MethodHandles.publicLookup().findVirtual(owner, \""
              + m.name
              + "\", "
              + methodTypeLiteral(m, ordinal)
              + ")); ");
    }
    w.println(
        "          } catch ("
            + resolutionExceptions(m)
            + " e) {");
    w.println(
        "            throw new ExternalTypeResolutionException(OWNER, \"" + m.name + "\", e);");
    w.println("          }");
    w.println("        }");
    w.println("      };");
    if (m.isStatic) renderStaticSupport(w, m, ordinal, cache);
    w.println();
    w.println("  public static " + m.returnType + " " + m.name + "(" + paramList + ") {");
    w.println("    try {");
    if (m.isStatic) {
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
    if (!"void".equals(m.returnType)) w.print("return (" + m.returnType + ") ");
    w.println("call.handle.invoke(" + argList + ");");
    w.println("    } catch (Throwable t) { throw sneak(t); }");
    w.println("  }");
    if (m.isStatic) {
      String explicitParamList = "ClassLoader applicationLoader";
      if (!paramList.isEmpty()) explicitParamList += ", " + paramList;
      w.println();
      w.println("  public static " + m.returnType + " " + m.name + "(" + explicitParamList + ") {");
      w.println(
          "    if (applicationLoader == null) throw new NullPointerException(\"applicationLoader\");");
      w.println("    try {");
      w.println("      ResolvedCall call = $" + ordinal + "$resolveStatic(applicationLoader);");
      w.print("      ");
      if (!"void".equals(m.returnType)) w.print("return (" + m.returnType + ") ");
      w.println("call.handle.invoke(" + argList + ");");
      w.println("    } catch (Throwable t) { throw sneak(t); }");
      w.println("  }");
    }
  }

  private void renderStaticSupport(PrintWriter w, MethodSpec m, int ordinal, String cache) {
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
    w.println("        throw new ExternalTypeResolutionException(OWNER, \"" + m.name + "\", e);");
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

  private void renderMarkedTypes(PrintWriter w, MethodSpec m, int ordinal) {
    if (m.returnTargetFqn != null) {
      w.println(
          "            Class<?> $"
              + ordinal
              + "$returnType = Class.forName(\""
              + m.returnTargetFqn
              + "\", false, owner.getClassLoader());");
    }
    for (int i = 0; i < m.paramTargetFqns.size(); i++) {
      String fqn = m.paramTargetFqns.get(i);
      if (fqn == null) continue;
      w.println(
          "            Class<?> $"
              + ordinal
              + "$parameterType"
              + i
              + " = Class.forName(\""
              + fqn
              + "\", false, owner.getClassLoader());");
    }
  }

  private String resolutionExceptions(MethodSpec m) {
    if (!m.isStatic || m.returnTargetFqn != null || hasMarkedParameter(m)) {
      return "ClassNotFoundException | NoSuchMethodException | IllegalAccessException";
    }
    return "NoSuchMethodException | IllegalAccessException";
  }

  private boolean hasMarkedParameter(MethodSpec m) {
    for (String targetFqn : m.paramTargetFqns) {
      if (targetFqn != null) return true;
    }
    return false;
  }

  private String methodTypeLiteral(MethodSpec m, int ordinal) {
    StringBuilder result = new StringBuilder("MethodType.methodType(");
    result.append(
        m.returnTargetFqn == null
            ? m.returnType + ".class"
            : "$" + ordinal + "$returnType");
    for (int i = 0; i < m.paramTypes.size(); i++) {
      result.append(", ");
      result.append(
          m.paramTargetFqns.get(i) == null
              ? m.paramTypes.get(i) + ".class"
              : "$" + ordinal + "$parameterType" + i);
    }
    return result.append(")").toString();
  }

  private void renderSneak(PrintWriter w) {
    w.println();
    w.println("  @SuppressWarnings(\"unchecked\")");
    w.println(
        "  private static <T extends Throwable> RuntimeException sneak(Throwable t) throws T {");
    w.println("    throw (T) t;");
    w.println("  }");
  }

  private String[] paramAndArgLists(MethodSpec m) {
    StringBuilder params = new StringBuilder();
    StringBuilder args = new StringBuilder();
    if (!m.isStatic) {
      params.append("java.lang.Object self");
      args.append("self");
    }
    for (int i = 0; i < m.paramTypes.size(); i++) {
      if (params.length() > 0) params.append(", ");
      if (args.length() > 0) args.append(", ");
      params.append(m.paramTypes.get(i)).append(" p").append(i);
      args.append("p").append(i);
    }
    return new String[] {params.toString(), args.toString()};
  }
}
