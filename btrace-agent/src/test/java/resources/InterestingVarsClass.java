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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

public class InterestingVarsClass {
  public static class Token<T> {
    public boolean getKind() {
      return true;
    }
  }

  public static void initAndStartApp(String a, String b, String c) {
    Collection<Token<String>> tokens = tokens();
    for (Token<?> token : tokens) {
      System.out.println(token);
    }

    StringBuilder nextVar = new StringBuilder(a);
    nextVar.append(b);

    Iterator<Token<String>> iter = tokens.iterator();
    while (iter.hasNext()) {
      Token<?> token = iter.next();
      if (token.getKind()) {
        iter.remove();
      }
    }
  }

  private static Collection<Token<String>> tokens() {
    return Arrays.asList(new Token<String>());
  }
}
