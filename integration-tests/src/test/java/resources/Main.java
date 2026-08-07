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
 * @author Jaroslav Bachorik
 */
public class Main extends TestApp {
  private static final ClassLoader EXTERNAL_TYPE_DECOY_LOADER = new ClassLoader(null) {};
  private String id = "xxx";
  private String field;
  private static String sField;

  public static void main(String[] args) throws Exception {
    Main i = new Main();
    i.start();
  }

  @Override
  protected void startWork() {
    while (!Thread.currentThread().isInterrupted()) {
      callA();
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void callA() {
    field = "AAA";
    sField = "BBB";
    print("i=" + callB(1, "Hello World"));
    probeExternal(new ExternalData(42));
    Thread thread = Thread.currentThread();
    ClassLoader previous = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(EXTERNAL_TYPE_DECOY_LOADER);
      probeExternalExplicit(new ExternalData(42));
    } finally {
      thread.setContextClassLoader(previous);
    }
  }

  // Entry point for @ExternalType integration tests: provides an ExternalData
  // instance to the probe via @Self without the extension needing to compile against ExternalData.
  ExternalData probeExternal(ExternalData data) {
    return data;
  }

  // Separate entry point: its decoy TCCL makes accidental legacy static dispatch fail.
  ExternalData probeExternalExplicit(ExternalData data) {
    return data;
  }

  private int callB(int i, String s) {
    print("[" + i + "] = " + s + ", field = " + field + ", sField = " + sField);
    return i + 1;
  }

  @Override
  public void print(String msg) {
    System.out.println(msg);
    System.out.flush();
  }
}
