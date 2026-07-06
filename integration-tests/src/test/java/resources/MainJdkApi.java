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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Target app for ClassFile API backend integration tests. Repeatedly calls JDK APIs with stable
 * bytecode shapes so BTrace probes on JDK classes produce output. On JDK 26+ the JDK classes have
 * class-file major version &ge; 70, which routes instrumentation through the ClassFile API backend
 * instead of ASM.
 */
public class MainJdkApi extends TestApp {
  private static volatile int sink;

  public static void main(String[] args) throws Exception {
    new MainJdkApi().start();
  }

  @Override
  protected void startWork() {
    while (!Thread.currentThread().isInterrupted()) {
      int result = Math.abs(-7);
      result += Math.max(3, 99);

      Date date = new Date();
      date.setTime(42L);
      result += (int) date.getTime();

      int[] copy = Arrays.copyOf(new int[] {1, 2, 3}, 4);
      Arrays.fill(copy, 7);
      result += Arrays.equals(new Object[] {"same"}, new Object[] {"same"}) ? 1 : 0;

      Map<String, String> map = Collections.synchronizedMap(new HashMap<String, String>());
      map.put("key", "value");
      String value = map.get("key");
      result += value.length();
      result += map.keySet().size();

      try {
        Base64.getDecoder().decode(ByteBuffer.wrap(new byte[] {'!'}));
      } catch (IllegalArgumentException expected) {
        result += 1;
      }

      sink = result;
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public void print(String msg) {
    System.out.println(msg);
    System.out.flush();
  }
}
