package org.openjdk.btrace.core.comm;

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

    Runnable writer = () -> {
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

    Runnable reader = () -> {
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

