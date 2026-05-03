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

import java.util.Collections;
import java.util.List;

final class AdapterSpec {
  final String pkg;
  final String interfaceSimpleName;
  final String externalFqn;
  final List<MethodSpec> methods;

  AdapterSpec(
      String pkg, String interfaceSimpleName, String externalFqn, List<MethodSpec> methods) {
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
