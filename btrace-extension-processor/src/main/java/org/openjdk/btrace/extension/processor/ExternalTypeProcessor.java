/* (C) 2024 */
package org.openjdk.btrace.extension.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.openjdk.btrace.core.extensions.ExternalType;

@SupportedAnnotationTypes("org.openjdk.btrace.core.extensions.ExternalType")
public final class ExternalTypeProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element e : roundEnv.getElementsAnnotatedWith(ExternalType.class)) {
      if (e.getKind() != ElementKind.INTERFACE) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "@ExternalType can only be applied to interfaces; found "
                    + e.getKind()
                    + " "
                    + e,
                e);
        continue;
      }
      TypeElement iface = (TypeElement) e;
      String externalFqn = iface.getAnnotation(ExternalType.class).value();
      if (externalFqn == null || externalFqn.isEmpty()) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "@ExternalType.value() must be a non-empty class name on " + iface.getQualifiedName(),
                iface);
        continue;
      }
      try {
        AdapterSpec spec = buildSpec(iface, externalFqn);
        emit(spec, iface);
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

  private AdapterSpec buildSpec(TypeElement iface, String externalFqn) {
    String pkg =
        processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
    String simple = iface.getSimpleName().toString();
    List<MethodSpec> methods = new ArrayList<>();
    for (Element m : iface.getEnclosedElements()) {
      if (m.getKind() != ElementKind.METHOD) continue;
      ExecutableElement em = (ExecutableElement) m;
      if (em.isDefault() || em.getModifiers().contains(Modifier.STATIC)) continue;
      boolean isStatic = em.getAnnotation(ExternalType.Static.class) != null;
      String rt = processingEnv.getTypeUtils().erasure(em.getReturnType()).toString();
      List<String> params = new ArrayList<>();
      for (VariableElement p : em.getParameters()) {
        params.add(processingEnv.getTypeUtils().erasure(p.asType()).toString());
      }
      methods.add(new MethodSpec(em.getSimpleName().toString(), rt, params, isStatic));
    }
    return new AdapterSpec(pkg, simple, externalFqn, methods);
  }

  private void emit(AdapterSpec spec, TypeElement origin) throws IOException {
    JavaFileObject jfo = processingEnv.getFiler().createSourceFile(spec.adapterFqn(), origin);
    try (PrintWriter w = new PrintWriter(jfo.openWriter())) {
      new AdapterEmitter(spec).render(w);
    }
  }
}
