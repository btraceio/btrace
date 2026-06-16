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
 * Target app for ClassFile API backend integration tests. Repeatedly calls {@code Math.abs} and
 * {@code Math.max} so BTrace probes on those methods produce output. On JDK 27+ the JDK classes
 * have class-file major version &ge; 70, which routes instrumentation through the ClassFile API
 * backend instead of ASM.
 */
public class MainJdkApi extends TestApp {
  public static void main(String[] args) throws Exception {
    new MainJdkApi().start();
  }

  @Override
  protected void startWork() {
    while (!Thread.currentThread().isInterrupted()) {
      Math.abs(-7);
      Math.max(3, 99);
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
