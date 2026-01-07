package org.openjdk.btrace.utils;

import org.openjdk.btrace.core.extensions.Extension;

public final class PrinterServiceImpl extends Extension implements PrinterService {
  private static final String NL = System.getProperty("line.separator");

  @Override
  public void print(String s) {
    if (s != null) {
      getContext().send(s);
    }
  }

  @Override
  public void println(String s) {
    print(s + NL);
  }

  @Override
  public void println() {
    print(NL);
  }
}
