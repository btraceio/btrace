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
  final List<String> paramTypes;
  final boolean isStatic;

  MethodSpec(String name, String returnType, List<String> paramTypes, boolean isStatic) {
    this.name = name;
    this.returnType = returnType;
    this.paramTypes = Collections.unmodifiableList(new java.util.ArrayList<>(paramTypes));
    this.isStatic = isStatic;
  }
}
