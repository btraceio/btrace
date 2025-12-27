/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.openjdk.btrace.core.comm;

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

    IOException ex = assertThrows(IOException.class, () -> new JavaSerializationProtocol(failingIn, trackingOut));
    assertTrue(trackingOut.closed.get(), "Output stream should be closed when input init fails");
    assertEquals("boom", ex.getMessage());
  }
}

