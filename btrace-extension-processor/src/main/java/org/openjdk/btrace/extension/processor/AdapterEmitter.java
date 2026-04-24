package org.openjdk.btrace.extension.processor;

import java.io.PrintWriter;

final class AdapterEmitter {
  private final AdapterSpec spec;

  AdapterEmitter(AdapterSpec spec) {
    this.spec = spec;
  }

  void render(PrintWriter w) {
    if (!spec.pkg.isEmpty()) w.println("package " + spec.pkg + ";");
    w.println();
    w.println("public final class " + spec.adapterSimpleName() + " {");
    w.println("  private " + spec.adapterSimpleName() + "() {}");
    for (MethodSpec m : spec.methods) {
      renderMethod(w, m);
    }
    w.println("}");
  }

  private void renderMethod(PrintWriter w, MethodSpec m) {
    StringBuilder params = new StringBuilder();
    if (!m.isStatic) params.append("java.lang.Object self");
    for (int i = 0; i < m.paramTypes.size(); i++) {
      if (params.length() > 0) params.append(", ");
      params.append(m.paramTypes.get(i)).append(" p").append(i);
    }
    w.println();
    w.println("  public static " + m.returnType + " " + m.name + "(" + params + ") {");
    w.println("    throw new UnsupportedOperationException(\"not implemented\");");
    w.println("  }");
  }
}
