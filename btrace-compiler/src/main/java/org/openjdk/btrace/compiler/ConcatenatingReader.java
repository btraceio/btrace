/*
 * Copyright (c) 2008 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed or intended for use
 * in the design, construction, operation or maintenance of any nuclear
 * facility.
 *
 * Sun gratefully acknowledges that this software was originally authored
 * and developed by Kenneth Bradley Russell and Christopher John Kline.
 */
package org.openjdk.btrace.compiler;

import java.io.BufferedReader;
import java.io.FilterReader;
import java.io.IOException;

/**
 * This code is based on PCPP code from the GlueGen project.
 *
 * <p>A Reader implementation which finds lines ending in the backslash character ('\') and
 * concatenates them with the next line.
 *
 * @author Kenneth B. Russell (original author)
 * @author A. Sundararajan (changes documented below)
 *     <p>Changes:
 *     <p>* Changed the package name. * Formatted with NetBeans.
 */
public class ConcatenatingReader extends FilterReader {
  private static final String NEW_LINE = System.getProperty("line.separator");
  private final BufferedReader inReader;
  // Any leftover characters go here
  private char[] curBuf;
  private int curPos;

  /**
   * This class requires that the input reader be a BufferedReader so it can do line-oriented
   * operations.
   */
  public ConcatenatingReader(BufferedReader in) {
    super(in);
    inReader = in;
  }

  @Override
  public int read() throws IOException {
    char[] tmp = new char[1];
    int num = read(tmp, 0, 1);
    if (num < 0) {
      return -1;
    }
    return tmp[0];
  }

  // It's easier not to support mark/reset since we don't need it
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
  public boolean ready() throws IOException {
    return curBuf != null || inReader.ready();
  }

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException {
    if (curBuf == null) {
      nextLine();
    }

    if (curBuf == null) {
      return -1;
    }

    int numRead = 0;

    while ((len > 0) && (curBuf != null) && (curPos < curBuf.length)) {
      cbuf[off] = curBuf[curPos];
      ++curPos;
      ++off;
      --len;
      ++numRead;
      if (curPos == curBuf.length) {
        nextLine();
      }
    }

    return numRead;
  }

  @Override
  public long skip(long n) throws IOException {
    long numSkipped = 0;

    while (n > 0) {
      int intN = (int) n;
      char[] tmp = new char[intN];
      int numRead = read(tmp, 0, intN);
      n -= numRead;
      numSkipped += numRead;
      if (numRead < intN) {
        break;
      }
    }
    return numSkipped;
  }

  private void nextLine() throws IOException {
    String cur = inReader.readLine();
    if (cur == null) {
      curBuf = null;
      return;
    }
    // The trailing newline was trimmed by the readLine() method. See
    // whether we have to put it back or not, depending on whether the
    // last character of the line is the concatenation character.
    int numChars = cur.length();
    boolean needNewline = true;
    if ((numChars > 0) && (cur.charAt(cur.length() - 1) == '\\')) {
      --numChars;
      needNewline = false;
    }
    char[] buf = new char[numChars + (needNewline ? NEW_LINE.length() : 0)];
    cur.getChars(0, numChars, buf, 0);
    if (needNewline) {
      NEW_LINE.getChars(0, NEW_LINE.length(), buf, numChars);
    }
    curBuf = buf;
    curPos = 0;
  }

  // Test harness
  /*
  public static void main(String[] args) throws IOException {
  if (args.length != 1) {
  System.out.println("Usage: java ConcatenatingReader [file name]");
  System.exit(1);
  }
  ConcatenatingReader reader = new ConcatenatingReader(new BufferedReader(new FileReader(args[0])));
  OutputStreamWriter writer = new OutputStreamWriter(System.out);
  char[] buf = new char[8192];
  boolean done = false;
  while (!done && reader.ready()) {
  int numRead = reader.read(buf, 0, buf.length);
  writer.write(buf, 0, numRead);
  if (numRead < buf.length)
  done = true;
  }
  writer.flush();
  }
   */
}
