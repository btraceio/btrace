/*
 * Validates enabling manifest-driven libs and skipping preconfigured libs does not break
 * attach and launch flows. This toggles:
 *  -Dbtrace.feature.manifestLibs=true
 *  -Dbtrace.test.skipLibs=true
 */
package tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManifestLibsTests extends RuntimeTest {
  @BeforeAll
  public static void setup() throws Exception {
    classSetup();
  }

  @BeforeEach
  @Override
  public void reset() {
    super.reset();
    // Toggle manifest-libs feature and skip libs for these tests
    System.setProperty("btrace.feature.manifestLibs", "true");
    System.setProperty("btrace.test.skipLibs", "true");
  }

  @AfterEach
  public void cleanupProps() {
    System.clearProperty("btrace.feature.manifestLibs");
    System.clearProperty("btrace.test.skipLibs");
  }

  @Test
  @DisplayName("Dynamic attach with manifest-libs enabled")
  public void dynamicAttach_manifestLibs() throws Exception {
    // Use a timer script with a short interval to ensure timely output
    testDynamic(
        "resources.Main",
        "btrace/OnTimerArgTest.java",
        new String[] {"timer=200"},
        10,
        (stdout, stderr, retcode, jfrFile) -> {
          assertFalse(stdout.contains("FAILED"), "Script should not have failed");
          assertTrue(stderr.isEmpty(), "Non-empty stderr");
          assertTrue(stdout.contains("timer"));
        });
  }

  @Test
  @DisplayName("Launch-time agent with manifest-libs enabled")
  public void launchAgent_manifestLibs() throws Exception {
    // Startup mode uses precompiled class and should work with the feature flag
    testStartup(
        "resources.Main",
        "traces/TraceAllTest.class",
        null,
        5,
        (stdout, stderr, retcode, jfrFile) -> {
          assertFalse(stdout.contains("FAILED"), "Script should not have failed");
          assertTrue(stderr.isEmpty(), "Non-empty stderr");
        });
  }
}
