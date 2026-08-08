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
package resources;

/**
 * Target class used by the @ExternalType integration tests.
 *
 * <p>This class exists only on the target application's classpath — it is never on the extension's
 * compile classpath — which is exactly the scenario @ExternalType is designed for.
 */
public final class ExternalData {
  private final int value;

  public ExternalData(int value) {
    this.value = value;
  }

  public int value() {
    return value;
  }

  public ExternalChild child() {
    return new ExternalChild();
  }

  public String acceptChild(ExternalChild child) {
    return child != null ? child.marker() : "ext-child-parameter-null";
  }

  public static String tag() {
    return "ext-data-ok";
  }

  public static String explicitTag() {
    return Thread.currentThread().getContextClassLoader() == ExternalData.class.getClassLoader()
        ? "ext-data-explicit-wrong-tccl"
        : "ext-data-explicit-ok";
  }
}
