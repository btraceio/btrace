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
package io.btrace.test.ext;

import io.btrace.core.extensions.ExternalType;

/**
 * {@linkplain ExternalType} contract for resources.ExternalData — a class that exists only in the
 * target application's classloader and is never on the extension's compile classpath.
 */
@ExternalType("resources.ExternalData")
public interface ExternalDataType {
  /** Maps to ExternalData.value() — virtual dispatch. */
  int value();

  /** Maps to ExternalData.child() while retaining an opaque Object adapter boundary. */
  @ExternalType.Type(ExternalChildType.class)
  Object child();

  /** Maps to ExternalData.acceptChild(ExternalChild) through an opaque Object boundary. */
  String acceptChild(@ExternalType.Type(ExternalChildType.class) Object child);

  @ExternalType.Overload("describe")
  String describeText(String text);

  @ExternalType.Overload("describe")
  String describeChild(@ExternalType.Type(ExternalChildType.class) Object child);

  @ExternalType.Getter("fieldValue")
  int fieldValue();

  @ExternalType.Setter("fieldValue")
  void setFieldValue(int value);

  @ExternalType.Getter("childField")
  @ExternalType.Type(ExternalChildType.class)
  Object childField();

  @ExternalType.Setter("childField")
  void setChildField(@ExternalType.Type(ExternalChildType.class) Object child);

  @ExternalType.Constructor
  Object createWithChild(@ExternalType.Type(ExternalChildType.class) Object child);

  @ExternalType.Static
  @ExternalType.Getter("staticField")
  int staticField();

  @ExternalType.Static
  @ExternalType.Setter("staticField")
  void setStaticField(int value);

  @ExternalType.Static
  @ExternalType.Getter("DEFAULT_CHILD")
  @ExternalType.Type(ExternalChildType.class)
  Object defaultChild();

  @ExternalType.Constructor
  Object create(int value);

  @ExternalType.InstanceOf
  boolean isExternalData(Object value);

  @ExternalType.Cast
  Object castExternalData(Object value);

  /** Maps to ExternalData.tag() — static dispatch via TCCL. */
  @ExternalType.Static
  String tag();

  /** Maps to ExternalData.explicitTag() — static dispatch with an explicit application loader. */
  @ExternalType.Static
  String explicitTag();
}
