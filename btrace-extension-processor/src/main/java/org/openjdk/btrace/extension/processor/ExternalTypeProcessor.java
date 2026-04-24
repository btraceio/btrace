/* (C) 2024 */
package org.openjdk.btrace.extension.processor;

import java.io.PrintWriter;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.openjdk.btrace.core.extensions.ExternalType;

@SupportedAnnotationTypes("org.openjdk.btrace.core.extensions.ExternalType")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public final class ExternalTypeProcessor extends AbstractProcessor {
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element e : roundEnv.getElementsAnnotatedWith(ExternalType.class)) {
      if (e.getKind() != ElementKind.INTERFACE) continue;
      TypeElement iface = (TypeElement) e;
      String pkg =
          processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
      String simple = iface.getSimpleName().toString();
      String adapterFqn = (pkg.isEmpty() ? "" : pkg + ".") + simple + "$Ext";
      try {
        JavaFileObject jfo = processingEnv.getFiler().createSourceFile(adapterFqn, iface);
        try (PrintWriter w = new PrintWriter(jfo.openWriter())) {
          if (!pkg.isEmpty()) w.println("package " + pkg + ";");
          w.println();
          w.println("public final class " + simple + "$Ext {");
          w.println("  private " + simple + "$Ext() {}");
          w.println("}");
        }
      } catch (Exception ex) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to emit adapter for " + iface.getQualifiedName() + ": " + ex,
                iface);
      }
    }
    return true;
  }
}
