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
 * Target for the ERROR-binding test: a method that always throws, called in a loop that swallows
 * it.
 */
public class FailingTarget extends TestApp {
  public static void main(String[] args) throws Exception {
    new FailingTarget().start();
  }

  @Override
  protected void startWork() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        validate("order-42");
      } catch (IllegalStateException expected) {
        // swallowed, as the service under diagnosis would
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  void validate(String orderId) {
    throw new IllegalStateException("order failed validation: " + orderId);
  }

  @Override
  public void print(String msg) {
    System.out.println(msg);
    System.out.flush();
  }
}
