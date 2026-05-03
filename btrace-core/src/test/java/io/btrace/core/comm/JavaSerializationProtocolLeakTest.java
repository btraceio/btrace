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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class JavaSerializationProtocolLeakTest {

  static class FailingInputStream extends InputStream {
    @Override
    public int read() throws IOException {
      throw new IOException("boom");
    }
  }

  static class CloseTrackingOutputStream extends OutputStream {
    final AtomicBoolean closed = new AtomicBoolean(false);

    @Override
    public void write(int b) throws IOException {
      // accept anything
    }

    @Override
    public void close() throws IOException {
      closed.set(true);
      super.close();
    }
  }

  @Test
  void constructorClosesOutputStreamOnInputInitFailure() {
    InputStream failingIn = new FailingInputStream();
    CloseTrackingOutputStream trackingOut = new CloseTrackingOutputStream();

    IOException ex =
        assertThrows(
            IOException.class, () -> new JavaSerializationProtocol(failingIn, trackingOut));
    assertTrue(trackingOut.closed.get(), "Output stream should be closed when input init fails");
    assertEquals("boom", ex.getMessage());
  }
}
