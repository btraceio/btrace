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

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.reflect.AccessFlag;

/**
 * Reads a {@link ClassHeader} with the JDK ClassFile API. Used by {@link ClassHeaderReader} for
 * class files newer than the bundled ASM release can parse; the JDK's own ClassFile API always
 * understands the class-file version of the JDK it ships with.
 */
public final class ClassFileApiClassHeaderReader {
  private ClassFileApiClassHeaderReader() {}

  public static ClassHeader read(byte[] bytes) {
    ClassModel model = ClassFile.of().parse(bytes);
    String superName = model.superclass().map(ClassEntry::asInternalName).orElse(null);
    String[] interfaces =
        model.interfaces().stream().map(ClassEntry::asInternalName).toArray(String[]::new);
    return new ClassHeader(
        model.thisClass().asInternalName(),
        superName,
        interfaces,
        model.flags().has(AccessFlag.INTERFACE));
  }
}
