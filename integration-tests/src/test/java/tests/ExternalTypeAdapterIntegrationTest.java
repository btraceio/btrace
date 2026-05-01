package tests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test for the @ExternalType annotation processor.
 *
 * Verifies that:
 * 1. btracec accepts a probe that uses a service backed by @ExternalType adapters
 * 2. The agent loads the extension without errors
 * 3. The generated ExternalDataType$Ext adapter resolves resources.ExternalData at
 *    runtime via the target application's classloader (TCCL) and successfully
 *    dispatches both a virtual instance call (value()) and a static call (tag())
 */
public class ExternalTypeAdapterIntegrationTest extends RuntimeTest {

  @BeforeAll
  public static void setup() throws Exception {
    classSetup();
  }

  @BeforeEach
  @Override
  public void reset() {
    super.reset();
    try (ServerSocket ss = new ServerSocket(0)) {
      btracePort = ss.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException("Failed to find a free port", e);
    }
  }

  @Test
  public void testExternalTypeAdapterTagAndValue() throws Exception {
    attachDelayMs = 500;
    testDynamic(
        "resources.Main",
        "btrace/ExternalTypeAdapterTest.java",
        null,
        2,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Probe should not have failed. stderr: " + stderr);

            // Static dispatch via TCCL: ExternalDataType$Ext.tag() -> ExternalData.tag()
            assertTrue(
                stdout.contains("tag=ext-data-ok"),
                "@ExternalType static dispatch failed. stdout: " + stdout);

            // Virtual dispatch: ExternalDataType$Ext.value(data) -> ExternalData.value()
            assertTrue(
                stdout.contains("value=42"),
                "@ExternalType virtual dispatch failed. stdout: " + stdout);
          }
        });
  }
}
