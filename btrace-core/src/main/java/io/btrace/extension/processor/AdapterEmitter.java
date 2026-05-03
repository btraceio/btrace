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
    w.println();
    w.println("public final class " + spec.adapterSimpleName() + " {");
    w.println("  private static final String OWNER = \"" + spec.externalFqn + "\";");
    w.println("  private " + spec.adapterSimpleName() + "() {}");
    for (MethodSpec m : spec.methods) renderMethod(w, m);
    renderSneak(w);
    w.println("}");
  }

  private void renderMethod(PrintWriter w, MethodSpec m) {
    String mhField = "$" + m.name + "$mh";
    String[] lists = paramAndArgLists(m);
    String paramList = lists[0];
    String argList = lists[1];
    String loaderExpr =
        m.isStatic
            ? "(Thread.currentThread().getContextClassLoader() != null ? Thread.currentThread().getContextClassLoader() : ClassLoader.getSystemClassLoader())"
            : "self.getClass().getClassLoader()";

    w.println();
    w.println("  private static volatile java.lang.invoke.MethodHandle " + mhField + ";");
    w.println();
    w.println("  public static " + m.returnType + " " + m.name + "(" + paramList + ") {");
    w.println("    try {");
    w.println("      MethodHandle h = " + mhField + ";");
    w.println("      if (h == null) h = $" + m.name + "$resolve(" + loaderExpr + ");");
    w.print("      ");
    if (!"void".equals(m.returnType)) w.print("return (" + m.returnType + ") ");
    w.println("h.invoke(" + argList + ");");
    w.println("    } catch (Throwable t) { throw sneak(t); }");
    w.println("  }");
    renderResolver(w, m, mhField);
  }

  private void renderResolver(PrintWriter w, MethodSpec m, String field) {
    w.println();
    w.println(
        "  private static MethodHandle $" + m.name + "$resolve(ClassLoader cl) throws Exception {");
    w.println("    MethodHandle local = " + field + ";");
    w.println("    if (local == null) {");
    w.println("      Class<?> c = Class.forName(OWNER, false, cl);");
    w.print("      MethodType mt = MethodType.methodType(");
    w.print(m.returnType + ".class");
    for (String p : m.paramTypes) w.print(", " + p + ".class");
    w.println(");");
    if (m.isStatic) {
      w.println(
          "      local = MethodHandles.publicLookup().findStatic(c, \"" + m.name + "\", mt);");
    } else {
      w.println(
          "      local = MethodHandles.publicLookup().findVirtual(c, \"" + m.name + "\", mt);");
    }
    w.println("      " + field + " = local;");
    w.println("    }");
    w.println("    return local;");
    w.println("  }");
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
