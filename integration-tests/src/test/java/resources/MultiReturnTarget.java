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

/** Target for the RETURN-duration test: a method with an early return that is never taken. */
public class MultiReturnTarget extends TestApp {
  public static void main(String[] args) throws Exception {
    new MultiReturnTarget().start();
  }

  @Override
  protected void startWork() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        earlyReturn(false);
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /** Two return instructions; the second one, reached after the sleep, is the one that runs. */
  int earlyReturn(boolean skip) throws InterruptedException {
    if (skip) {
      return -1;
    }
    Thread.sleep(120);
    return 1;
  }

  @Override
  public void print(String msg) {
    System.out.println(msg);
    System.out.flush();
  }
}
