package org.openjdk.btrace.test.ext;

/**
 * Integration-test service: exercises @ExternalType adapters against a class
 * (resources.ExternalData) that lives only in the target application's classloader.
 */
public interface ExternalTypeTestService {
  /** Returns the static tag from resources.ExternalData via its @ExternalType adapter. */
  String tag();

  /** Returns the instance value from an resources.ExternalData object via its @ExternalType adapter. */
  int value(Object externalData);
}
