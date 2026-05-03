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

public class StackTrackerTest {
  private static String testStatic(int a, Class<?> clz) {
    int i = 10;

    try {
      int j = i + 10;
      if (j > 10) {
        consumeInt(j - 10);
      } else {
        consumeInt(j);
      }
      int x = 0;
      out:
      while (x++ < 10) {
        consumeInt(x);
        for (int z = x; z >= 0; z--) {
          consumeInt(x);
          if (convertInt(z) == 1L) {
            break out;
          }
        }
      }
      return "ok";
    } catch (Throwable l) {

    }

    return "fail";
  }

  private static void consumeInt(int x) {
    System.out.println(x);
  }

  private static long convertInt(int x) {
    return x + 1;
  }
}
