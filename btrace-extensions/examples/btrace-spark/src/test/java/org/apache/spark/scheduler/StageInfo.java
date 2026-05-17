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
package org.apache.spark.scheduler;

public final class StageInfo {
  public static boolean nameCalled;
  public static boolean numTasksCalled;

  private final String name;
  private final int numTasks;

  public StageInfo(String name, int numTasks) {
    this.name = name;
    this.numTasks = numTasks;
  }

  public String name() {
    nameCalled = true;
    return name;
  }

  public int numTasks() {
    numTasksCalled = true;
    return numTasks;
  }

  public static void reset() {
    nameCalled = false;
    numTasksCalled = false;
  }
}
