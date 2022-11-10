package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URL;
import org.junit.jupiter.api.Test;

class BTraceProbeFactoryTest {

  @Test
  void canLoadNullFile() {
    assertFalse(BTraceProbeFactory.canLoad(null));
  }

  @Test
  void canLoadNonExistingFile() {
    assertFalse(BTraceProbeFactory.canLoad("!invalid path"));
  }

  @Test
  void canLoadBTracePack() throws Exception {
    URL rsrc = BTraceProbeFactory.class.getResource("/resources/classdata/AllStuff.btrc");
    assertNotNull(rsrc);

    assertTrue(BTraceProbeFactory.canLoad(new File(rsrc.toURI()).getPath()));
  }

  @Test
  void canLoadClass() throws Exception {
    URL rsrc = BTraceProbeFactory.class.getResource("BTraceProbeFactoryTest.class");
    assertNotNull(rsrc);

    assertTrue(BTraceProbeFactory.canLoad(new File(rsrc.toURI()).getPath()));
  }

  @Test
  void refuseUnknown() throws Exception {
    URL rsrc = BTraceProbeFactory.class.getResource("/plain.txt");
    assertNotNull(rsrc);

    assertFalse(BTraceProbeFactory.canLoad(new File(rsrc.toURI()).getPath()));
  }
}
