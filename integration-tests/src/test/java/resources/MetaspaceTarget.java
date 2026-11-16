/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import java.lang.reflect.Proxy;

/**
 * Target for the {@code @OnLowMemory} tests: keeps allocating a little Metaspace by defining a
 * proxy class in a fresh class loader every 100 ms. The JVM checks a non-heap pool's usage
 * threshold when that pool allocates, so the probe's Metaspace threshold notification fires on
 * every JDK and GC without filling the heap. Each loader is dropped right away, so the classes can
 * be unloaded and the target stays small.
 */
public class MetaspaceTarget extends TestApp {
  public static void main(String[] args) throws Exception {
    new MetaspaceTarget().start();
  }

  @Override
  protected void startWork() {
    while (!Thread.currentThread().isInterrupted()) {
      ClassLoader loader = new ClassLoader(MetaspaceTarget.class.getClassLoader()) {};
      Proxy.newProxyInstance(loader, new Class<?>[] {Runnable.class}, (p, m, a) -> null);
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
