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
package io.btrace.core.comm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ListProbesCommandConcurrencyTest {

  @Test
  void concurrentSetAndIterateDoesNotThrow() throws Exception {
    ListProbesCommand cmd = new ListProbesCommand();

    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(2);

    Runnable writer =
        () -> {
          try {
            start.await();
            for (int r = 0; r < 500; r++) {
              int size = (r % 10) + 1;
              List<String> list = new ArrayList<>(size);
              for (int i = 0; i < size; i++) {
                list.add("probe-" + r + '-' + i);
              }
              cmd.setProbes(list);
            }
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
          } finally {
            done.countDown();
          }
        };

    Runnable reader =
        () -> {
          try {
            start.await();
            for (int r = 0; r < 500; r++) {
              // print()
              StringWriter sw = new StringWriter();
              PrintWriter pw = new PrintWriter(sw);
              assertDoesNotThrow(() -> cmd.print(pw));

              // write()
              assertDoesNotThrow(
                  () -> {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                      cmd.write(oos);
                    }
                  });
            }
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
          } finally {
            done.countDown();
          }
        };

    new Thread(writer).start();
    new Thread(reader).start();
    start.countDown();

    // Ensure both loops finished
    done.await(10, TimeUnit.SECONDS);
  }
}
