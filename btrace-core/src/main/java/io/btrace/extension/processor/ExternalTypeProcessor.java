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

import io.btrace.core.extensions.ExternalType;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
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
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes({
  "io.btrace.core.extensions.ExternalType",
  "io.btrace.core.extensions.ExternalType.Type"
})
public final class ExternalTypeProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<Element> markers = new HashSet<>(roundEnv.getElementsAnnotatedWith(ExternalType.Type.class));
    Set<Element> consumedMarkers = new HashSet<>();
    for (Element e : roundEnv.getElementsAnnotatedWith(ExternalType.class)) {
      if (e.getKind() != ElementKind.INTERFACE) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "@ExternalType can only be applied to interfaces; found " + e.getKind() + " " + e,
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
                "@ExternalType.value() must be a non-empty class name on "
                    + iface.getQualifiedName(),
                iface);
        continue;
      }
      try {
        AdapterSpec spec = buildSpec(iface, externalFqn, markers, consumedMarkers);
        if (spec != null) emit(spec, iface);
      } catch (Exception ex) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to emit adapter for " + iface.getQualifiedName() + ": " + ex,
                iface);
      }
    }
    for (Element marker : markers) {
      if (consumedMarkers.contains(marker)) continue;
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "@ExternalType.Type is only valid as @ExternalType.Type(OtherContract.class) Object "
                  + "on a non-static, non-default method of an @ExternalType interface.",
              marker);
    }
    return true;
  }

  private AdapterSpec buildSpec(
      TypeElement iface,
      String externalFqn,
      Set<Element> markers,
      Set<Element> consumedMarkers) {
    String pkg = processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
    String simple = iface.getSimpleName().toString();
    List<MethodSpec> methods = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    boolean invalid = false;
    for (Element m : iface.getEnclosedElements()) {
      if (m.getKind() != ElementKind.METHOD) continue;
      ExecutableElement em = (ExecutableElement) m;
      if (em.isDefault() || em.getModifiers().contains(Modifier.STATIC)) continue;
      String methodName = em.getSimpleName().toString();
      if (!seen.add(methodName)) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "@ExternalType does not support overloaded methods: '"
                    + methodName
                    + "' is declared more than once in "
                    + iface.getQualifiedName()
                    + ". Rename the methods or use MethodHandleCache directly for overloaded dispatch.",
                em);
        invalid = true;
        continue;
      }
      boolean isStatic = em.getAnnotation(ExternalType.Static.class) != null;
      String rt = processingEnv.getTypeUtils().erasure(em.getReturnType()).toString();
      String returnTargetFqn = null;
      if (markers.contains(em)) {
        consumedMarkers.add(em);
        returnTargetFqn = targetFqn(em, rt);
        invalid |= returnTargetFqn == null;
      }
      List<String> params = new ArrayList<>();
      List<String> paramTargetFqns = new ArrayList<>();
      for (VariableElement p : em.getParameters()) {
        String paramType = processingEnv.getTypeUtils().erasure(p.asType()).toString();
        params.add(paramType);
        String targetFqn = null;
        if (markers.contains(p)) {
          consumedMarkers.add(p);
          targetFqn = targetFqn(p, paramType);
          invalid |= targetFqn == null;
        }
        paramTargetFqns.add(targetFqn);
      }
      methods.add(new MethodSpec(methodName, rt, returnTargetFqn, params, paramTargetFqns, isStatic));
    }
    if (invalid) return null;
    return new AdapterSpec(pkg, simple, externalFqn, methods);
  }

  private String targetFqn(Element marker, String erasedType) {
    if (!"java.lang.Object".equals(erasedType)) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "@ExternalType.Type is valid only as @ExternalType.Type(OtherContract.class) Object.",
              marker);
      return null;
    }
    TypeMirror contractType = annotationTypeValue(marker);
    if (contractType == null || !(processingEnv.getTypeUtils().asElement(contractType) instanceof TypeElement)) {
      return invalidContract(marker);
    }
    TypeElement contract = (TypeElement) processingEnv.getTypeUtils().asElement(contractType);
    ExternalType externalType = contract.getAnnotation(ExternalType.class);
    if (contract.getKind() != ElementKind.INTERFACE
        || externalType == null
        || externalType.value().isEmpty()) {
      return invalidContract(marker);
    }
    return externalType.value();
  }

  private String invalidContract(Element marker) {
    processingEnv
        .getMessager()
        .printMessage(
            Diagnostic.Kind.ERROR,
            "@ExternalType.Type must reference an interface with a non-empty @ExternalType value.",
            marker);
    return null;
  }

  private TypeMirror annotationTypeValue(Element marker) {
    for (javax.lang.model.element.AnnotationMirror annotation : marker.getAnnotationMirrors()) {
      if (!annotation
          .getAnnotationType()
          .toString()
          .equals("io.btrace.core.extensions.ExternalType.Type")) continue;
      for (javax.lang.model.element.AnnotationValue value :
          processingEnv.getElementUtils().getElementValuesWithDefaults(annotation).values()) {
        Object raw = value.getValue();
        if (raw instanceof TypeMirror) return (TypeMirror) raw;
      }
    }
    return null;
  }

  private void emit(AdapterSpec spec, TypeElement origin) throws IOException {
    JavaFileObject jfo = processingEnv.getFiler().createSourceFile(spec.adapterFqn(), origin);
    try (PrintWriter w = new PrintWriter(jfo.openWriter())) {
      new AdapterEmitter(spec).render(w);
    }
  }
}
