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

public class ThreadSpawner extends TestApp {
  public static void main(String[] args) throws Exception {
    ThreadSpawner i = new ThreadSpawner();
    i.start();
  }

  @Override
  protected void startWork() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        spawnThread();
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void spawnThread() throws InterruptedException {
    Thread t = new Thread(() -> print("thread started"));
    t.setName("testThread");
    t.start();
    t.join();
  }

  @Override
  public void print(String msg) {
    System.out.println(msg);
    System.out.flush();
  }
}
