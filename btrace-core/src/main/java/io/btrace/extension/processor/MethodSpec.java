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

final class MethodSpec {
  final String name;
  final String returnType;
  final String returnTargetFqn;
  final List<String> paramTypes;
  final List<String> paramTargetFqns;
  final boolean isStatic;

  MethodSpec(
      String name,
      String returnType,
      String returnTargetFqn,
      List<String> paramTypes,
      List<String> paramTargetFqns,
      boolean isStatic) {
    this.name = name;
    this.returnType = returnType;
    this.returnTargetFqn = returnTargetFqn;
    this.paramTypes = Collections.unmodifiableList(new java.util.ArrayList<>(paramTypes));
    this.paramTargetFqns =
        Collections.unmodifiableList(new java.util.ArrayList<>(paramTargetFqns));
    this.isStatic = isStatic;
  }
}
