package org.openjdk.btrace.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.btrace.core.extensions.PermissionSet;
import org.openjdk.btrace.extension.impl.ExtensionConfig;
import org.openjdk.btrace.extension.impl.ExtensionLoaderImpl;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionLoaderImplConcurrencyTest {
  @TempDir
  Path tempDir;

  @Test
  void loadIsIdempotentUnderConcurrency() throws Exception {
    Path extDir = tempDir.resolve("test-ext");
    Files.createDirectories(extDir);

    String implName = "test-ext-impl.jar";
    Path apiJar = extDir.resolve("test-ext-api.jar");
    Path implJar = extDir.resolve(implName);
    writeJar(apiJar, implName);
    writeJar(implJar, null);

    DummyInstrumentation instrumentation = new DummyInstrumentation();
    ExtensionLoaderImpl loader =
        new ExtensionLoaderImpl(
            Collections.emptyList(),
            getClass().getClassLoader(),
            ExtensionConfig.createDefault(),
            instrumentation);

    ExtensionDescriptorDTO descriptor =
        new ExtensionDescriptorDTO.Builder()
            .id("test-ext")
            .version("1.0.0")
            .name("Test")
            .description("Test")
            .jarPath(extDir)
            .services(List.of())
            .requiredExtensions(List.of())
            .repository(null)
            .requiredPermissions(PermissionSet.empty())
            .build();

    int threads = 8;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    AtomicInteger success = new AtomicInteger();
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      for (int i = 0; i < threads; i++) {
        executor.execute(
            () -> {
              try {
                start.await();
                if (loader.load(descriptor)) {
                  success.incrementAndGet();
                }
              } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
              } finally {
                done.countDown();
              }
            });
      }
      start.countDown();
      assertTrue(done.await(5, TimeUnit.SECONDS), "Load operations did not finish in time");
    } finally {
      executor.shutdownNow();
    }

    assertEquals(threads, success.get());
    assertEquals(1, instrumentation.appendCount.get());
    assertTrue(descriptor.isLoaded());
    assertNotNull(descriptor.getClassLoader());
    assertEquals(1, loader.getLoadedExtensions().size());
  }

  private static void writeJar(Path jarPath, String implJarName) throws IOException {
    Manifest manifest = new Manifest();
    Attributes attrs = manifest.getMainAttributes();
    attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    if (implJarName != null) {
      attrs.putValue("BTrace-Extension-Impl", implJarName);
    }
    try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      // empty jar is sufficient for loader tests
    }
  }

  private static final class DummyInstrumentation implements Instrumentation {
    private final AtomicInteger appendCount = new AtomicInteger();

    @Override
    public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
    }

    @Override
    public void addTransformer(ClassFileTransformer transformer) {
    }

    @Override
    public boolean removeTransformer(ClassFileTransformer transformer) {
      return false;
    }

    @Override
    public boolean isRetransformClassesSupported() {
      return false;
    }

    @Override
    public void retransformClasses(Class<?>... classes) {
    }

    @Override
    public boolean isRedefineClassesSupported() {
      return false;
    }

    @Override
    public void redefineClasses(ClassDefinition... definitions) {
    }

    @Override
    public boolean isModifiableClass(Class<?> theClass) {
      return false;
    }

    @Override
    public Class<?>[] getAllLoadedClasses() {
      return new Class<?>[0];
    }

    @Override
    public Class<?>[] getInitiatedClasses(ClassLoader loader) {
      return new Class<?>[0];
    }

    @Override
    public long getObjectSize(Object objectToSize) {
      return 0L;
    }

    @Override
    public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {
      appendCount.incrementAndGet();
    }

    @Override
    public void appendToSystemClassLoaderSearch(JarFile jarfile) {
    }

    @Override
    public boolean isNativeMethodPrefixSupported() {
      return false;
    }

    @Override
    public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
    }

    @Override
    public void redefineModule(
        Module module,
        Set<Module> extraReads,
        Map<String, Set<Module>> extraExports,
        Map<String, Set<Module>> extraOpens,
        Set<Class<?>> extraUses,
        Map<Class<?>, List<Class<?>>> extraProvides) {
    }

    @Override
    public boolean isModifiableModule(Module module) {
      return false;
    }
  }
}
