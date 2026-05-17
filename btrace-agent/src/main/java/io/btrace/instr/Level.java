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

import io.btrace.runtime.Interval;

/**
 * @author Jaroslav Bachorik
 */
public class Level {
  private final Interval value;

  public Level() {
    this(">0");
  }

  private Level(Interval i) {
    value = i;
  }

  private Level(String s) {
    this(Interval.fromString(s));
  }

  public static Level fromString(String s) {
    return new Level(s);
  }

  public Interval getValue() {
    return value;
  }
}
