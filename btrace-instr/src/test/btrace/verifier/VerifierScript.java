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


package traces.verifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Self;

@BTrace
public class VerifierScript implements Runnable {
  private static Properties p = new Properties();
  private static int[] x = new int[100];
  private int i = 10;

  @OnMethod(clazz = "/.*/")
  public static void invalidMethodCall(@Self List l) {
    if (l instanceof ArrayList) {
      System.out.println(l.size());
    }
  }

  @OnMethod(clazz = "/.*/")
  public static void invalidLoops(List<String> l) {
    for (int i = 0; i < 10; i++) {
      BTraceUtils.println(BTraceUtils.str(i));
    }
    for (String s : l) {
      BTraceUtils.println(s);
    }
    while (true) {
      BTraceUtils.print("x");
    }
  }

  @OnMethod(clazz = "/.*/")
  public static int invalidReturn() {
    return 1;
  }

  @OnMethod(clazz = "/.*/")
  public static synchronized void syncHandler() {
    synchronized (VerifierScript.class) {
      BTraceUtils.println("ok");
    }
  }

  @OnMethod(clazz = "/.*/")
  public static void validInstanceHandler() {}

  @OnMethod(clazz = "/.*/")
  public void invalidInstanceHandler() {
    try {
      System.out.println("x");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void run() {
    // do nothing
  }
}
