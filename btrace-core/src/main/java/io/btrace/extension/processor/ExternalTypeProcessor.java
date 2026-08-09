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

import io.btrace.core.extensions.ExternalType;
import io.btrace.extension.processor.MethodSpec.OperationKind;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
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
  "io.btrace.core.extensions.ExternalType.Overload",
  "io.btrace.core.extensions.ExternalType.Getter",
  "io.btrace.core.extensions.ExternalType.Setter",
  "io.btrace.core.extensions.ExternalType.Constructor",
  "io.btrace.core.extensions.ExternalType.InstanceOf",
  "io.btrace.core.extensions.ExternalType.Cast"
})
public final class ExternalTypeProcessor extends AbstractProcessor {
  private static final String TYPE = "io.btrace.core.extensions.ExternalType.Type";
  private static final String OVERLOAD = "io.btrace.core.extensions.ExternalType.Overload";
  private static final String GETTER = "io.btrace.core.extensions.ExternalType.Getter";
  private static final String SETTER = "io.btrace.core.extensions.ExternalType.Setter";
  private static final String CONSTRUCTOR = "io.btrace.core.extensions.ExternalType.Constructor";
  private static final String INSTANCE_OF = "io.btrace.core.extensions.ExternalType.InstanceOf";
  private static final String CAST = "io.btrace.core.extensions.ExternalType.Cast";

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<Element> typeMarkers = elements(roundEnv, ExternalType.Type.class);
    Set<Element> overloadMarkers = elements(roundEnv, ExternalType.Overload.class);
    Set<Element> getterMarkers = elements(roundEnv, ExternalType.Getter.class);
    Set<Element> setterMarkers = elements(roundEnv, ExternalType.Setter.class);
    Set<Element> constructorMarkers = elements(roundEnv, ExternalType.Constructor.class);
    Set<Element> instanceOfMarkers = elements(roundEnv, ExternalType.InstanceOf.class);
    Set<Element> castMarkers = elements(roundEnv, ExternalType.Cast.class);
    Set<Element> consumed = new HashSet<>();

    for (Element e : roundEnv.getElementsAnnotatedWith(ExternalType.class)) {
      if (e.getKind() != ElementKind.INTERFACE) {
        error("@ExternalType can only be applied to interfaces; found " + e.getKind() + " " + e, e);
        continue;
      }
      TypeElement iface = (TypeElement) e;
      String externalFqn = iface.getAnnotation(ExternalType.class).value();
      if (externalFqn == null || externalFqn.isEmpty()) {
        error(
            "@ExternalType.value() must be a non-empty class name on " + iface.getQualifiedName(),
            e);
        continue;
      }
      try {
        AdapterSpec spec =
            buildSpec(
                iface,
                externalFqn,
                typeMarkers,
                overloadMarkers,
                getterMarkers,
                setterMarkers,
                constructorMarkers,
                instanceOfMarkers,
                castMarkers,
                consumed);
        if (spec != null) emit(spec, iface);
      } catch (Exception ex) {
        error("Failed to emit adapter for " + iface.getQualifiedName() + ": " + ex, iface);
      }
    }
    reportUnconsumed(
        typeMarkers,
        consumed,
        "@ExternalType.Type is only valid as @ExternalType.Type(OtherContract.class) Object on a non-static, non-default method of an @ExternalType interface.");
    reportUnconsumed(
        overloadMarkers,
        consumed,
        "@ExternalType.Overload is only valid on an abstract method of an @ExternalType interface.");
    reportUnconsumed(
        getterMarkers,
        consumed,
        "@ExternalType.Getter is only valid on an abstract method of an @ExternalType interface.");
    reportUnconsumed(
        setterMarkers,
        consumed,
        "@ExternalType.Setter is only valid on an abstract method of an @ExternalType interface.");
    reportUnconsumed(
        constructorMarkers,
        consumed,
        "@ExternalType.Constructor is only valid on an abstract method of an @ExternalType interface.");
    reportUnconsumed(
        instanceOfMarkers,
        consumed,
        "@ExternalType.InstanceOf is only valid on an abstract method of an @ExternalType interface.");
    reportUnconsumed(
        castMarkers,
        consumed,
        "@ExternalType.Cast is only valid on an abstract method of an @ExternalType interface.");
    return true;
  }

  private Set<Element> elements(RoundEnvironment roundEnv, Class<? extends Annotation> annotation) {
    return new HashSet<>(roundEnv.getElementsAnnotatedWith(annotation));
  }

  private AdapterSpec buildSpec(
      TypeElement iface,
      String externalFqn,
      Set<Element> typeMarkers,
      Set<Element> overloadMarkers,
      Set<Element> getterMarkers,
      Set<Element> setterMarkers,
      Set<Element> constructorMarkers,
      Set<Element> instanceOfMarkers,
      Set<Element> castMarkers,
      Set<Element> consumed) {
    String pkg = processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
    List<MethodSpec> methods = new ArrayList<>();
    Map<MethodSpec, ExecutableElement> origins = new HashMap<>();
    Map<MethodSpec, Boolean> selected = new HashMap<>();
    Map<String, List<MethodSpec>> localMethodGroups = new HashMap<>();
    Map<String, List<MethodSpec>> targetMethodGroups = new HashMap<>();
    Map<FieldKey, MethodSpec> getters = new HashMap<>();
    Map<FieldKey, MethodSpec> setters = new HashMap<>();
    Map<String, Boolean> fieldStatic = new HashMap<>();
    boolean invalid = false;

    for (Element member : iface.getEnclosedElements()) {
      if (member.getKind() != ElementKind.METHOD) continue;
      ExecutableElement method = (ExecutableElement) member;
      OperationKind operation =
          operation(
              method,
              getterMarkers,
              setterMarkers,
              constructorMarkers,
              instanceOfMarkers,
              castMarkers);
      boolean hasOverload = overloadMarkers.contains(method);
      boolean isStatic = method.getAnnotation(ExternalType.Static.class) != null;
      if (method.isDefault() || method.getModifiers().contains(Modifier.STATIC)) {
        continue;
      }
      if (operation != OperationKind.METHOD) consumed.add(method);
      if (hasOverload) consumed.add(method);

      if (operation == null) {
        error("An @ExternalType method may have at most one operation marker.", method);
        invalid = true;
        operation = OperationKind.METHOD;
      }
      String selector = hasOverload ? annotationStringValue(method, OVERLOAD) : null;
      if (hasOverload && !validMemberName(selector)) {
        error("Invalid @ExternalType.Overload target name.", method);
        invalid = true;
      }
      if (operation != OperationKind.METHOD && hasOverload) {
        error("@ExternalType.Overload is only valid on ordinary method operations.", method);
        invalid = true;
      }
      if ((operation == OperationKind.CONSTRUCTOR
              || operation == OperationKind.INSTANCE_OF
              || operation == OperationKind.CAST)
          && isStatic) {
        error("@ExternalType.Static is not valid on this operation.", method);
        invalid = true;
      }

      String returnType = erased(method.getReturnType());
      String returnTargetFqn = null;
      if (typeMarkers.contains(method)) {
        consumed.add(method);
        returnTargetFqn = targetFqn(method, returnType);
        invalid |= returnTargetFqn == null;
      }
      List<String> parameterTypes = new ArrayList<>();
      List<String> parameterTargetFqns = new ArrayList<>();
      for (VariableElement parameter : method.getParameters()) {
        String parameterType = erased(parameter.asType());
        parameterTypes.add(parameterType);
        String targetFqn = null;
        if (typeMarkers.contains(parameter)) {
          consumed.add(parameter);
          targetFqn = targetFqn(parameter, parameterType);
          invalid |= targetFqn == null;
        }
        parameterTargetFqns.add(targetFqn);
      }

      invalid |=
          validateSignature(
              method, operation, returnType, returnTargetFqn, parameterTypes, parameterTargetFqns);
      String adapterName = method.getSimpleName().toString();
      String targetName =
          operation == OperationKind.METHOD
              ? (selector != null ? selector : adapterName)
              : operationTargetName(method, operation);
      if ((operation == OperationKind.GETTER || operation == OperationKind.SETTER)
          && !validFieldName(targetName)) {
        error("Invalid @ExternalType field name.", method);
        invalid = true;
      }
      MethodSpec spec =
          new MethodSpec(
              adapterName,
              targetName,
              operation,
              returnType,
              returnTargetFqn,
              parameterTypes,
              parameterTargetFqns,
              isStatic);
      methods.add(spec);
      origins.put(spec, method);
      if (operation == OperationKind.METHOD) {
        selected.put(spec, hasOverload);
        localMethodGroups.computeIfAbsent(adapterName, ignored -> new ArrayList<>()).add(spec);
        targetMethodGroups.computeIfAbsent(targetName, ignored -> new ArrayList<>()).add(spec);
      }
      if (operation == OperationKind.GETTER || operation == OperationKind.SETTER) {
        Boolean previousStatic = fieldStatic.putIfAbsent(targetName, isStatic);
        if (previousStatic != null && previousStatic.booleanValue() != isStatic) {
          error(
              "An @ExternalType field cannot be both static and instance in one contract.", method);
          invalid = true;
        }
        FieldKey fieldKey = new FieldKey(targetName, isStatic);
        Map<FieldKey, MethodSpec> kind = operation == OperationKind.GETTER ? getters : setters;
        if (kind.put(fieldKey, spec) != null) {
          error(
              "An @ExternalType contract permits only one getter and one setter per target field.",
              method);
          invalid = true;
        }
      }
    }
    invalid |=
        validateMethodGroups(iface, localMethodGroups, targetMethodGroups, selected, origins);
    for (FieldKey key : getters.keySet()) {
      MethodSpec getter = getters.get(key);
      MethodSpec setter = setters.get(key);
      if (setter != null
          && !"void".equals(getter.returnType)
          && setter.paramTypes.size() == 1
          && !lookupType(getter, true).equals(lookupType(setter, false))) {
        error(
            "@ExternalType getter and setter for a field must use the same exact field type.",
            origins.get(setter));
        invalid = true;
      }
    }
    invalid |= validateKeys(methods, origins);
    return invalid
        ? null
        : new AdapterSpec(pkg, iface.getSimpleName().toString(), externalFqn, methods);
  }

  private OperationKind operation(
      ExecutableElement method,
      Set<Element> getters,
      Set<Element> setters,
      Set<Element> constructors,
      Set<Element> instanceOfs,
      Set<Element> casts) {
    int count = 0;
    OperationKind result = OperationKind.METHOD;
    if (getters.contains(method)) {
      count++;
      result = OperationKind.GETTER;
    }
    if (setters.contains(method)) {
      count++;
      result = OperationKind.SETTER;
    }
    if (constructors.contains(method)) {
      count++;
      result = OperationKind.CONSTRUCTOR;
    }
    if (instanceOfs.contains(method)) {
      count++;
      result = OperationKind.INSTANCE_OF;
    }
    if (casts.contains(method)) {
      count++;
      result = OperationKind.CAST;
    }
    return count > 1 ? null : result;
  }

  private boolean validateSignature(
      ExecutableElement method,
      OperationKind operation,
      String returnType,
      String returnTargetFqn,
      List<String> parameterTypes,
      List<String> parameterTargetFqns) {
    boolean invalid = false;
    if (operation == OperationKind.GETTER) {
      invalid |=
          require(
              !"void".equals(returnType) && parameterTypes.isEmpty(),
              "@ExternalType.Getter requires a non-void return and no target arguments.",
              method);
      invalid |=
          require(
              allNull(parameterTargetFqns),
              "@ExternalType.Getter does not permit parameter @ExternalType.Type markers.",
              method);
    } else if (operation == OperationKind.SETTER) {
      invalid |=
          require(
              "void".equals(returnType) && parameterTypes.size() == 1,
              "@ExternalType.Setter requires void return and exactly one target argument.",
              method);
      invalid |=
          require(
              returnTargetFqn == null,
              "@ExternalType.Setter does not permit a return @ExternalType.Type marker.",
              method);
    } else if (operation == OperationKind.CONSTRUCTOR) {
      invalid |=
          require(
              "java.lang.Object".equals(returnType) && returnTargetFqn == null,
              "@ExternalType.Constructor requires direct Object return without @ExternalType.Type.",
              method);
    } else if (operation == OperationKind.INSTANCE_OF) {
      invalid |=
          require(
              "boolean".equals(returnType)
                  && parameterTypes.size() == 1
                  && "java.lang.Object".equals(parameterTypes.get(0))
                  && returnTargetFqn == null
                  && allNull(parameterTargetFqns),
              "@ExternalType.InstanceOf requires boolean return and one direct unmarked Object argument.",
              method);
    } else if (operation == OperationKind.CAST) {
      invalid |=
          require(
              "java.lang.Object".equals(returnType)
                  && parameterTypes.size() == 1
                  && "java.lang.Object".equals(parameterTypes.get(0))
                  && returnTargetFqn == null
                  && allNull(parameterTargetFqns),
              "@ExternalType.Cast requires direct Object return and one direct unmarked Object argument.",
              method);
    }
    return invalid;
  }

  private boolean require(boolean condition, String message, Element element) {
    if (!condition) error(message, element);
    return !condition;
  }

  private boolean validateMethodGroups(
      TypeElement iface,
      Map<String, List<MethodSpec>> localGroups,
      Map<String, List<MethodSpec>> targetGroups,
      Map<MethodSpec, Boolean> selected,
      Map<MethodSpec, ExecutableElement> origins) {
    boolean invalid = false;
    for (Map.Entry<String, List<MethodSpec>> entry : localGroups.entrySet()) {
      if (entry.getValue().size() > 1
          && entry.getValue().stream().anyMatch(m -> !selected.get(m))) {
        error(
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
      List<MethodSpec> group = entry.getValue();
      if (group.size() == 1 && selected.get(group.get(0))) {
        error(
            "@ExternalType.Overload requires at least two methods selecting '"
                + entry.getKey()
                + "'.",
            origins.get(group.get(0)));
        invalid = true;
      }
      if (group.size() > 1 && group.stream().anyMatch(m -> !selected.get(m))) {
        error(
            "Every method selecting target '"
                + entry.getKey()
                + "' must use @ExternalType.Overload.",
            origins.get(group.get(0)));
        invalid = true;
      }
    }
    return invalid;
  }

  private boolean validateKeys(
      List<MethodSpec> methods, Map<MethodSpec, ExecutableElement> origins) {
    boolean invalid = false;
    Map<TargetKey, MethodSpec> targetKeys = new HashMap<>();
    Map<String, MethodSpec> descriptors = new HashMap<>();
    Map<OperationKind, MethodSpec> predicates = new HashMap<>();
    for (MethodSpec method : methods) {
      if ((method.operation == OperationKind.INSTANCE_OF || method.operation == OperationKind.CAST)
          && predicates.put(method.operation, method) != null) {
        error(
            "An @ExternalType contract permits only one " + method.operation + " operation.",
            origins.get(method));
        invalid = true;
      }
      if (targetKeys.put(targetKey(method), method) != null) {
        error(
            "@ExternalType operations must have distinct exact target signatures.",
            origins.get(method));
        invalid = true;
      }
      for (String descriptor : generatedDescriptors(method)) {
        if (descriptors.put(descriptor, method) != null) {
          error(
              "@ExternalType operations collide in generated adapter descriptors; rename the local method.",
              origins.get(method));
          invalid = true;
        }
      }
    }
    return invalid;
  }

  private TargetKey targetKey(MethodSpec method) {
    List<String> types = new ArrayList<>();
    for (int i = 0; i < method.paramTypes.size(); i++) types.add(lookupType(method, i));
    return new TargetKey(
        method.operation, method.targetName, method.isStatic, lookupType(method, true), types);
  }

  private String lookupType(MethodSpec method, boolean returnType) {
    return returnType
        ? method.returnTargetFqn != null ? method.returnTargetFqn : method.returnType
        : lookupType(method, 0);
  }

  private String lookupType(MethodSpec method, int index) {
    return method.paramTargetFqns.get(index) != null
        ? method.paramTargetFqns.get(index)
        : method.paramTypes.get(index);
  }

  private List<String> generatedDescriptors(MethodSpec method) {
    List<String> descriptors = new ArrayList<>();
    StringBuilder legacy = new StringBuilder(method.adapterName).append('(');
    if (hasGeneratedReceiver(method)) legacy.append("java.lang.Object,");
    for (String parameter : method.paramTypes) legacy.append(parameter).append(',');
    descriptors.add(legacy.append(')').toString());
    if (method.isStatic || method.operation == OperationKind.CONSTRUCTOR) {
      StringBuilder explicit =
          new StringBuilder(method.adapterName).append("(java.lang.ClassLoader,");
      for (String parameter : method.paramTypes) explicit.append(parameter).append(',');
      descriptors.add(explicit.append(')').toString());
    }
    return descriptors;
  }

  private boolean hasGeneratedReceiver(MethodSpec method) {
    return !method.isStatic
        && (method.operation == OperationKind.METHOD
            || method.operation == OperationKind.GETTER
            || method.operation == OperationKind.SETTER);
  }

  private String operationTargetName(ExecutableElement method, OperationKind operation) {
    if (operation == OperationKind.GETTER) return annotationStringValue(method, GETTER);
    if (operation == OperationKind.SETTER) return annotationStringValue(method, SETTER);
    if (operation == OperationKind.CONSTRUCTOR) return "<init>";
    if (operation == OperationKind.INSTANCE_OF) return "isInstance";
    if (operation == OperationKind.CAST) return "cast";
    return method.getSimpleName().toString();
  }

  private boolean allNull(List<String> values) {
    for (String value : values) if (value != null) return false;
    return true;
  }

  private String erased(TypeMirror type) {
    return processingEnv.getTypeUtils().erasure(type).toString();
  }

  private boolean validFieldName(String name) {
    return validMemberName(name);
  }

  private boolean validMemberName(String name) {
    return name != null
        && !name.trim().isEmpty()
        && !name.equals("<init>")
        && !name.equals("<clinit>")
        && name.indexOf('.') < 0
        && name.indexOf(';') < 0
        && name.indexOf('[') < 0
        && name.indexOf('/') < 0;
  }

  private void reportUnconsumed(Set<Element> markers, Set<Element> consumed, String message) {
    for (Element marker : markers) if (!consumed.contains(marker)) error(message, marker);
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
      error(
          "@ExternalType.Type is valid only as @ExternalType.Type(OtherContract.class) Object.",
          marker);
      return null;
    }
    TypeMirror contractType = annotationTypeValue(marker);
    if (contractType == null
        || !(processingEnv.getTypeUtils().asElement(contractType) instanceof TypeElement))
      return invalidContract(marker);
    TypeElement contract = (TypeElement) processingEnv.getTypeUtils().asElement(contractType);
    ExternalType externalType = contract.getAnnotation(ExternalType.class);
    if (contract.getKind() != ElementKind.INTERFACE
        || externalType == null
        || externalType.value().isEmpty()) return invalidContract(marker);
    return externalType.value();
  }

  private String invalidContract(Element marker) {
    error(
        "@ExternalType.Type must reference an interface with a non-empty @ExternalType value.",
        marker);
    return null;
  }

  private TypeMirror annotationTypeValue(Element marker) {
    for (javax.lang.model.element.AnnotationMirror annotation : marker.getAnnotationMirrors()) {
      if (!annotation.getAnnotationType().toString().equals(TYPE)) continue;
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

  private void error(String message, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
  }

  private static final class FieldKey {
    final String name;
    final boolean statik;

    FieldKey(String name, boolean statik) {
      this.name = name;
      this.statik = statik;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof FieldKey)) return false;
      FieldKey that = (FieldKey) other;
      return statik == that.statik && name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(name, statik);
    }
  }

  private static final class TargetKey {
    final OperationKind operation;
    final String name;
    final boolean statik;
    final String result;
    final List<String> parameters;

    TargetKey(
        OperationKind operation,
        String name,
        boolean statik,
        String result,
        List<String> parameters) {
      this.operation = operation;
      this.name = name;
      this.statik = statik;
      this.result = result;
      this.parameters = parameters;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof TargetKey)) return false;
      TargetKey that = (TargetKey) other;
      return operation == that.operation
          && statik == that.statik
          && name.equals(that.name)
          && result.equals(that.result)
          && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(operation, name, statik, result, parameters);
    }
  }
}
