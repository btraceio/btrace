package org.openjdk.btrace.instr;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.openjdk.btrace.compiler.PackGenerator;
import org.openjdk.btrace.core.SharedSettings;

public final class InstrPackGenerator implements PackGenerator {
  @Override
  public byte[] generateProbePack(byte[] classData) throws IOException {
    BTraceProbeNode bpn =
        (BTraceProbeNode) new BTraceProbeFactory(SharedSettings.GLOBAL).createProbe(classData);
    // force bytecode verification before creating the persisted representation
    bpn.checkVerified();

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (DataOutputStream dos = new DataOutputStream(bos)) {
      BTraceProbePersisted bpp = BTraceProbePersisted.from(bpn);
      if (!bpp.isVerified()) {
        throw new Error();
      }
      bpp.write(dos);
    }

    return bos.toByteArray();
  }
}
