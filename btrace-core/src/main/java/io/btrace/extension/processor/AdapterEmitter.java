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
    String mhCache = "$" + m.name + "$handles";
    String[] lists = paramAndArgLists(m);
    String paramList = lists[0];
    String argList = lists[1];
    String loaderExpr =
        m.isStatic
            ? "(Thread.currentThread().getContextClassLoader() != null ? Thread.currentThread().getContextClassLoader() : ClassLoader.getSystemClassLoader())"
            : "self.getClass().getClassLoader()";

    w.println();
    w.println("  private static final ClassValue<MethodHandle> " + mhCache + " =");
    w.println("      new ClassValue<MethodHandle>() {");
    w.println("        @Override");
    w.println("        protected MethodHandle computeValue(Class<?> owner) {");
    w.println("          try {");
    if (m.isStatic) {
      w.println(
          "            return MethodHandles.publicLookup().findStatic(owner, \""
              + m.name
              + "\", "
              + methodTypeLiteral(m)
              + ");");
    } else {
      w.println(
          "            return MethodHandles.publicLookup().findVirtual(owner, \""
              + m.name
              + "\", "
              + methodTypeLiteral(m)
              + ");");
    }
    w.println("          } catch (NoSuchMethodException | IllegalAccessException e) {");
    w.println("            throw new IllegalStateException(\"Unable to resolve \" + OWNER, e);");
    w.println("          }");
    w.println("        }");
    w.println("      };");
    w.println();
    w.println("  public static " + m.returnType + " " + m.name + "(" + paramList + ") {");
    w.println("    try {");
    w.println("      Class<?> owner = Class.forName(OWNER, false, " + loaderExpr + ");");
    w.println("      MethodHandle h = " + mhCache + ".get(owner);");
    w.print("      ");
    if (!"void".equals(m.returnType)) w.print("return (" + m.returnType + ") ");
    w.println("h.invoke(" + argList + ");");
    w.println("    } catch (Throwable t) { throw sneak(t); }");
    w.println("  }");
  }

  private String methodTypeLiteral(MethodSpec m) {
    StringBuilder result = new StringBuilder("MethodType.methodType(");
    result.append(m.returnType).append(".class");
    for (String p : m.paramTypes) result.append(", ").append(p).append(".class");
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
