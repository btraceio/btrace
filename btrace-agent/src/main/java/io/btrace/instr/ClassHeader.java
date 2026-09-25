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
package io.btrace.instr;

/**
 * The class-file metadata {@link ClassInfo} needs to build the type hierarchy: internal names of
 * the class, its superclass and its interfaces, plus the interface flag.
 */
final class ClassHeader {
  private final String className;
  private final String superName;
  private final String[] interfaces;
  private final boolean isInterface;

  ClassHeader(String className, String superName, String[] interfaces, boolean isInterface) {
    this.className = className;
    this.superName = superName;
    this.interfaces = interfaces == null ? new String[0] : interfaces.clone();
    this.isInterface = isInterface;
  }

  String getClassName() {
    return className;
  }

  String getSuperName() {
    return superName;
  }

  String[] getInterfaces() {
    return interfaces.clone();
  }

  boolean isInterface() {
    return isInterface;
  }
}
