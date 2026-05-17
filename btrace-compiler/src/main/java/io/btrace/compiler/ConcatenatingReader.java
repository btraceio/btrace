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
package io.btrace.compiler;

import java.io.BufferedReader;
import java.io.FilterReader;
import java.io.IOException;

/**
 * A {@link FilterReader} that implements C-preprocessor line-continuation: any line whose last
 * character is {@code \} is joined to the immediately following line without an intervening
 * newline.
 */
final class ConcatenatingReader extends FilterReader {

  private static final String LINE_SEP = System.lineSeparator();

  private final BufferedReader source;
  private char[] pending;
  private int pos;

  ConcatenatingReader(BufferedReader in) {
    super(in);
    this.source = in;
  }

  @Override
  public int read() throws IOException {
    char[] buf = new char[1];
    return read(buf, 0, 1) < 0 ? -1 : buf[0];
  }

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException {
    if (pending == null) {
      loadLine();
    }
    if (pending == null) {
      return -1;
    }
    int copied = 0;
    while (len > 0 && pending != null && pos < pending.length) {
      cbuf[off++] = pending[pos++];
      len--;
      copied++;
      if (pos == pending.length) {
        loadLine();
      }
    }
    return copied;
  }

  @Override
  public boolean ready() throws IOException {
    return pending != null || source.ready();
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public void mark(int readAheadLimit) throws IOException {
    throw new IOException("mark/reset not supported");
  }

  @Override
  public void reset() throws IOException {
    throw new IOException("mark/reset not supported");
  }

  @Override
  public long skip(long n) throws IOException {
    long skipped = 0;
    char[] buf = new char[512];
    while (n > 0) {
      int chunk = (int) Math.min(n, buf.length);
      int r = read(buf, 0, chunk);
      if (r < 0) break;
      skipped += r;
      n -= r;
    }
    return skipped;
  }

  private void loadLine() throws IOException {
    String line = source.readLine();
    if (line == null) {
      pending = null;
      return;
    }
    boolean continuation = !line.isEmpty() && line.charAt(line.length() - 1) == '\\';
    String content = continuation ? line.substring(0, line.length() - 1) : line + LINE_SEP;
    pending = content.toCharArray();
    pos = 0;
  }
}
