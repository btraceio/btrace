package org.openjdk.btrace.utils;

import org.openjdk.btrace.core.extensions.ServiceDescriptor;

/**
 * Simple printer service API for BTrace scripts.
 */
@ServiceDescriptor
public interface PrinterService {
  void print(String s);
  void println(String s);
  void println();
}
