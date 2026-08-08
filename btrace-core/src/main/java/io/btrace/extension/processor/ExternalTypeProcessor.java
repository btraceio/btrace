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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
  "io.btrace.core.extensions.ExternalType.Type",
  "io.btrace.core.extensions.ExternalType.Overload"
})
public final class ExternalTypeProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<Element> markers =
        new HashSet<>(roundEnv.getElementsAnnotatedWith(ExternalType.Type.class));
    Set<Element> consumedMarkers = new HashSet<>();
    Set<Element> selectors =
        new HashSet<>(roundEnv.getElementsAnnotatedWith(ExternalType.Overload.class));
    Set<Element> consumedSelectors = new HashSet<>();
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
        AdapterSpec spec =
            buildSpec(iface, externalFqn, markers, consumedMarkers, selectors, consumedSelectors);
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
    reportUnconsumed(
        selectors,
        consumedSelectors,
        "@ExternalType.Overload is only valid on an abstract method of an @ExternalType interface.");
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
      Set<Element> consumedMarkers,
      Set<Element> selectors,
      Set<Element> consumedSelectors) {
    String pkg = processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
    String simple = iface.getSimpleName().toString();
    List<MethodSpec> methods = new ArrayList<>();
    Map<String, List<MethodSpec>> localGroups = new HashMap<>();
    Map<String, List<MethodSpec>> targetGroups = new HashMap<>();
    Map<MethodSpec, Boolean> selected = new HashMap<>();
    Map<MethodSpec, ExecutableElement> origins = new HashMap<>();
    boolean invalid = false;
    for (Element m : iface.getEnclosedElements()) {
      if (m.getKind() != ElementKind.METHOD) continue;
      ExecutableElement em = (ExecutableElement) m;
      if (em.isDefault() || em.getModifiers().contains(Modifier.STATIC)) continue;
      String methodName = em.getSimpleName().toString();
      String selector = null;
      if (selectors.contains(em)) {
        consumedSelectors.add(em);
        selector = selectorValue(em);
        if (!validTargetName(selector)) {
          processingEnv
              .getMessager()
              .printMessage(
                  Diagnostic.Kind.ERROR, "Invalid @ExternalType.Overload target name.", em);
          invalid = true;
        }
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
      String targetName = selector != null ? selector : methodName;
      MethodSpec spec =
          new MethodSpec(
              methodName, targetName, rt, returnTargetFqn, params, paramTargetFqns, isStatic);
      methods.add(spec);
      origins.put(spec, em);
      selected.put(spec, selector != null);
      localGroups.computeIfAbsent(methodName, ignored -> new ArrayList<>()).add(spec);
      targetGroups.computeIfAbsent(targetName, ignored -> new ArrayList<>()).add(spec);
    }
    for (Map.Entry<String, List<MethodSpec>> entry : localGroups.entrySet()) {
      if (entry.getValue().size() > 1
          && entry.getValue().stream().anyMatch(m -> !selected.get(m))) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "@ExternalType does not support overloaded methods: '"
                    + entry.getKey()
                    + "' is declared more than once in "
                    + iface.getQualifiedName()
                    + ". Rename the methods or select every member.",
                origins.get(entry.getValue().get(0)));
        invalid = true;
      }
    }
    for (Map.Entry<String, List<MethodSpec>> entry : targetGroups.entrySet()) {
      if (entry.getValue().size() == 1 && selected.get(entry.getValue().get(0))) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "@ExternalType.Overload requires at least two methods selecting '"
                    + entry.getKey()
                    + "'.",
                origins.get(entry.getValue().get(0)));
        invalid = true;
      }
      if (entry.getValue().size() > 1
          && entry.getValue().stream().anyMatch(m -> !selected.get(m))) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "Every method selecting target '"
                    + entry.getKey()
                    + "' must use @ExternalType.Overload.",
                origins.get(entry.getValue().get(0)));
        invalid = true;
      }
    }
    Map<TargetKey, MethodSpec> targetKeys = new HashMap<>();
    Map<String, MethodSpec> generatedDescriptors = new HashMap<>();
    for (MethodSpec method : methods) {
      TargetKey targetKey = targetKey(method);
      if (targetKeys.put(targetKey, method) != null) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "@ExternalType selected methods must have distinct exact target signatures.",
                origins.get(method));
        invalid = true;
      }
      for (String descriptor : generatedDescriptors(method)) {
        if (generatedDescriptors.put(descriptor, method) != null) {
          processingEnv
              .getMessager()
              .printMessage(
                  Diagnostic.Kind.ERROR,
                  "@ExternalType selected methods collide in generated adapter descriptors; rename the local method.",
                  origins.get(method));
          invalid = true;
        }
      }
    }
    if (invalid) return null;
    return new AdapterSpec(pkg, simple, externalFqn, methods);
  }

  private TargetKey targetKey(MethodSpec method) {
    List<String> params = new ArrayList<>();
    for (int i = 0; i < method.paramTypes.size(); i++)
      params.add(
          method.paramTargetFqns.get(i) != null
              ? method.paramTargetFqns.get(i)
              : method.paramTypes.get(i));
    return new TargetKey(
        method.targetName,
        method.isStatic,
        method.returnTargetFqn != null ? method.returnTargetFqn : method.returnType,
        params);
  }

  private static final class TargetKey {
    final String name;
    final boolean statik;
    final String result;
    final List<String> params;

    TargetKey(String name, boolean statik, String result, List<String> params) {
      this.name = name;
      this.statik = statik;
      this.result = result;
      this.params = params;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof TargetKey)) return false;
      TargetKey k = (TargetKey) o;
      return statik == k.statik
          && name.equals(k.name)
          && result.equals(k.result)
          && params.equals(k.params);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(name, statik, result, params);
    }
  }

  private List<String> generatedDescriptors(MethodSpec method) {
    List<String> descriptors = new ArrayList<>();
    StringBuilder legacy = new StringBuilder(method.adapterName).append('(');
    if (!method.isStatic) legacy.append("java.lang.Object,");
    for (String param : method.paramTypes) legacy.append(param).append(',');
    descriptors.add(legacy.append(')').toString());
    if (method.isStatic) {
      StringBuilder explicit =
          new StringBuilder(method.adapterName).append("(java.lang.ClassLoader,");
      for (String param : method.paramTypes) explicit.append(param).append(',');
      descriptors.add(explicit.append(')').toString());
    }
    return descriptors;
  }

  private void reportUnconsumed(Set<Element> values, Set<Element> consumed, String message) {
    for (Element value : values)
      if (!consumed.contains(value))
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, value);
  }

  private String selectorValue(Element element) {
    return annotationStringValue(element, "io.btrace.core.extensions.ExternalType.Overload");
  }

  private boolean validTargetName(String name) {
    return name != null
        && !name.trim().isEmpty()
        && !name.equals("<init>")
        && !name.equals("<clinit>")
        && name.indexOf('.') < 0
        && name.indexOf(';') < 0
        && name.indexOf('[') < 0
        && name.indexOf('/') < 0;
  }

  private String annotationStringValue(Element marker, String annotationName) {
    for (javax.lang.model.element.AnnotationMirror annotation : marker.getAnnotationMirrors()) {
      if (!annotation.getAnnotationType().toString().equals(annotationName)) continue;
      for (javax.lang.model.element.AnnotationValue value :
          processingEnv.getElementUtils().getElementValuesWithDefaults(annotation).values()) {
        Object raw = value.getValue();
        if (raw instanceof String) return (String) raw;
      }
    }
    return null;
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
    if (contractType == null
        || !(processingEnv.getTypeUtils().asElement(contractType) instanceof TypeElement)) {
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
