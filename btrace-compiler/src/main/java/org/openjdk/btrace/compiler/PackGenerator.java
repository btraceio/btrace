package org.openjdk.btrace.compiler;

import java.io.IOException;

public interface PackGenerator {
  byte[] generateProbePack(byte[] data) throws IOException;
}
