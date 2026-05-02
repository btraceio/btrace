package org.openjdk.btrace.test.ext;

import org.openjdk.btrace.core.extensions.ExternalType;

/**
 * {@linkplain ExternalType} contract for resources.ExternalData — a class that exists only in the
 * target application's classloader and is never on the extension's compile classpath.
 */
@ExternalType("resources.ExternalData")
public interface ExternalDataType {
  /** Maps to ExternalData.value() — virtual dispatch. */
  int value();

  /** Maps to ExternalData.tag() — static dispatch via TCCL. */
  @ExternalType.Static
  String tag();
}
