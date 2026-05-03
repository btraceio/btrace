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
package org.openjdk.btrace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.core.ArgsMap;

public class ArgsMapTest {
  private static final String KEY1 = "key1";
  private static final String KEY2 = "key2";
  private static final String VALUE1 = "value1";
  private static final String VALUE2 = "value2";

  private ArgsMap instance;

  @BeforeEach
  public void setUp() {
    instance = new ArgsMap();
    instance.put(KEY1, VALUE1);
    instance.put(KEY2, VALUE2);
  }

  @Test
  public void templateExisting() {
    String value = instance.template(KEY1 + "=${" + KEY1 + "}");
    assertEquals(KEY1 + "=" + VALUE1, value);
  }

  @Test
  public void templateNonExisting() {
    String orig = KEY1 + "=${key3}";
    String value = instance.template(orig);
    assertEquals(orig, value);
  }

  @Test
  public void templateTrailing$() {
    String orig = KEY1 + "$";
    String value = instance.template(orig);
    assertEquals(orig, value);
  }

  @Test
  public void templateUnclosedPlaceholder() {
    String orig = KEY1 + "${";
    String value = instance.template(orig);
    assertEquals(orig, value);
  }

  @Test
  public void templateSingle$() {
    String orig = KEY1 + "$" + KEY2;
    String value = instance.template(orig);
    assertEquals(orig, value);
  }
}
