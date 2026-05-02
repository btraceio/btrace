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

import java.util.ArrayList;
import java.util.Map;

/**
 * @author Jaroslav Bachorik jaroslav.bachorik@sun.com
 */
public class DerivedClass extends AbstractClass {
  private static volatile String defaultString;

  static {
    final String name = AbstractClass.class.getName();
    try {
      defaultString = "value1";
    } catch (Throwable t1) {
      try {
        defaultString = "value2";
      } catch (Throwable t2) {
        defaultString = "value3";
      }
    }

    defaultString = "value4";
  }

  public DerivedClass() {
    super(new ArrayList());
  }

  @Override
  public void doGet(String a, Map b) {
    System.err.println(a);
  }
}
