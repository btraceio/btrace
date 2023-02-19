package org.openjdk.btrace.instr;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * These checks are relying on 'golden' diffs in test/resources/golden folder. Whenever the injected
 * bytecode is changed the `regenerateGoldenFiles` task should be rerun and the diff files in
 * 'test/resources/golden' folder should be sanity checked.
 */
public class OnMethodInstrumentorTest extends InstrumentorTestBase {
  @BeforeAll
  static void classSetup() throws Exception {
    try {
      Field f = RandomIntProvider.class.getDeclaredField("useBtraceEnter");
      f.setAccessible(true);
      f.setBoolean(null, false);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @AfterAll
  static void classTeardown() throws Exception {
    System.setProperty("btrace.indy", "");
  }

  @ParameterizedTest
  @MethodSource("testTransformation")
  void testTransformationInplace(String caseName) throws Throwable {
    forceIndyDispatch(false);
    loadTargetClass("OnMethodTest");
    String trace = "onmethod" + File.separator + caseName;
    transform(trace);

    checkTransformation("onmethod" + File.separator + caseName, "inplace");
  }

  @ParameterizedTest
  @MethodSource("testTransformation")
  void testTransformationIndy(String caseName) throws Throwable {
    forceIndyDispatch(true);
    try {
      loadTargetClass("OnMethodTest");
      String trace = "onmethod" + File.separator + caseName;
      transform(trace);

      checkTransformation("onmethod" + File.separator + caseName, "indy");
    } finally {
      forceIndyDispatch(false);
    }
  }

  private static Stream<String> testTransformation() throws Throwable {
    Path scriptDir =
        Paths.get(System.getProperty("project.test.resources"))
            .getParent()
            .resolve("btrace")
            .resolve("onmethod");

    Set<String> scripts = new TreeSet<>();
    try (DirectoryStream<Path> paths = Files.newDirectoryStream(scriptDir)) {
      for (Path path : paths) {
        if (Files.isDirectory(path)) continue;
        scripts.add(path.getFileName().toString().replace(".java", ""));
      }
    }
    try (DirectoryStream<Path> paths = Files.newDirectoryStream(scriptDir.resolve("leveled"))) {
      for (Path path : paths) {
        if (Files.isDirectory(path)) continue;
        scripts.add(
            "leveled" + File.separator + path.getFileName().toString().replace(".java", ""));
      }
    }
    return scripts.stream();
  }
}
