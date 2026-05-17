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
  private String id = "xxx";

  public static void main(String[] args) throws Exception {
    Main i = new Main();
    i.start();
  }

  @Override
  protected void startWork() {
    while (!Thread.currentThread().isInterrupted() && !finished.get()) {
      callA();
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    Thread.currentThread().interrupt();
  }

  private void callA() {
    print("i=" + callB(1, "Hello World"));
  }

  private int callB(int i, String s) {
    print("[" + i + "] = " + s);
    return i + 1;
  }

  @Override
  public void print(String msg) {
    System.out.println(msg);
    System.out.flush();
  }
}
