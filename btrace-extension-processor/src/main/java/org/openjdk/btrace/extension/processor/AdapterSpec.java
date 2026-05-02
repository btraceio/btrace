package org.openjdk.btrace.extension.processor;

import java.util.Collections;
import java.util.List;

final class AdapterSpec {
  final String pkg;
  final String interfaceSimpleName;
  final String externalFqn;
  final List<MethodSpec> methods;

  AdapterSpec(String pkg, String interfaceSimpleName, String externalFqn, List<MethodSpec> methods) {
    this.pkg = pkg;
    this.interfaceSimpleName = interfaceSimpleName;
    this.externalFqn = externalFqn;
    this.methods = Collections.unmodifiableList(new java.util.ArrayList<>(methods));
  }

  String adapterSimpleName() {
    return interfaceSimpleName + "$Ext";
  }

  String adapterFqn() {
    return pkg.isEmpty() ? adapterSimpleName() : pkg + "." + adapterSimpleName();
  }
}
