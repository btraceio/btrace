package org.openjdk.btrace.extension.processor;

import java.util.Collections;
import java.util.List;

final class MethodSpec {
  final String name;
  final String returnType;
  final List<String> paramTypes;
  final boolean isStatic;

  MethodSpec(String name, String returnType, List<String> paramTypes, boolean isStatic) {
    this.name = name;
    this.returnType = returnType;
    this.paramTypes = Collections.unmodifiableList(new java.util.ArrayList<>(paramTypes));
    this.isStatic = isStatic;
  }
}
