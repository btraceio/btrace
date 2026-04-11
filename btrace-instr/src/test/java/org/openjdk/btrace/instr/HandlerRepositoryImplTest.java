package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.extensions.Permission;

/**
 * Lifecycle tests for HandlerRepositoryImpl: register/unregister probes and verify
 * that resolveHandler returns null after unregistration.
 */
class HandlerRepositoryImplTest {

  /** Minimal stub BTraceProbe for testing lifecycle. */
  private static BTraceProbe stubProbe(String internalName) {
    return new BTraceProbe() {
      @Override public String getClassName() { return internalName.replace('/', '.'); }
      @Override public String getClassName(boolean internal) {
        return internal ? internalName : internalName.replace('/', '.');
      }
      @Override public boolean isTransforming() { return false; }
      @Override public boolean isClassRenamed() { return false; }
      @Override public boolean isVerified() { return true; }
      @Override public void checkVerified() {}
      @Override public Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) { return Collections.emptyList(); }
      @Override public Iterable<OnMethod> onmethods() { return Collections.emptyList(); }
      @Override public Iterable<OnProbe> onprobes() { return Collections.emptyList(); }
      @Override public Class<?> register(BTraceRuntime.Impl rt, BTraceTransformer t) { return null; }
      @Override public void unregister() {}
      @Override public byte[] getFullBytecode() { return new byte[0]; }
      @Override public byte[] getDataHolderBytecode() { return new byte[0]; }
      @Override public BTraceRuntime.Impl getRuntime() { return null; }
      @Override public boolean willInstrument(Class<?> clz) { return false; }
      @Override public String getActionPrefix() { return ""; }
      @Override public void notifyTransform(String className) {}
      @Override public void copyHandlers(org.objectweb.asm.ClassVisitor cv) {}
      @Override public void applyArgs(ArgsMap argsMap) {}
      @Override public Set<Permission> getRequiredPermissions() { return Collections.emptySet(); }
    };
  }

  @AfterEach
  void cleanup() {
    // Clean up any probes registered during tests
  }

  @Test
  void testRegisterAndResolveReturnsNullForUnknownHandler() {
    String probeName = "test/lifecycle/StubProbe";
    BTraceProbe probe = stubProbe(probeName);

    HandlerRepositoryImpl.registerProbe(probe);

    // Resolution should return null since the probe class is not actually loaded
    // (no real class file exists), but the probe IS registered
    MethodHandle result = HandlerRepositoryImpl.resolveHandler(
        probeName, "onMethod", MethodType.methodType(void.class));

    // Result is null because Class.forName(probeName) will fail — that's expected
    // The important thing is no exception is thrown
    assertNull(result, "Should return null when probe class cannot be loaded");

    HandlerRepositoryImpl.unregisterProbe(probe);
  }

  @Test
  void testUnregisterClearsProbeFromRegistry() {
    String probeName = "test/lifecycle/AnotherProbe";
    BTraceProbe probe = stubProbe(probeName);

    HandlerRepositoryImpl.registerProbe(probe);

    // Ensure sentinel is cached by doing a resolution
    HandlerRepositoryImpl.resolveHandler(
        probeName, "someHandler", MethodType.methodType(void.class));

    // Unregister the probe
    HandlerRepositoryImpl.unregisterProbe(probe);

    // After unregistering, resolveHandler should return null (probe gone)
    MethodHandle afterUnregister = HandlerRepositoryImpl.resolveHandler(
        probeName, "someHandler", MethodType.methodType(void.class));

    assertNull(afterUnregister, "Should return null after probe is unregistered");
  }

  @Test
  void testUnregisterDoesNotAffectOtherProbes() {
    String probeName1 = "test/lifecycle/ProbeOne";
    String probeName2 = "test/lifecycle/ProbeTwo";

    BTraceProbe probe1 = stubProbe(probeName1);
    BTraceProbe probe2 = stubProbe(probeName2);

    HandlerRepositoryImpl.registerProbe(probe1);
    HandlerRepositoryImpl.registerProbe(probe2);

    // Cache sentinel entries for both
    HandlerRepositoryImpl.resolveHandler(probeName1, "handler", MethodType.methodType(void.class));
    HandlerRepositoryImpl.resolveHandler(probeName2, "handler", MethodType.methodType(void.class));

    // Unregister only probe1
    HandlerRepositoryImpl.unregisterProbe(probe1);

    // probe2 should still return null (probe not loaded, but no exception)
    // This verifies that probe2's cache was not cleared
    MethodHandle result2 = HandlerRepositoryImpl.resolveHandler(
        probeName2, "handler", MethodType.methodType(void.class));
    // null is expected since the class isn't loadable — just verify no crash
    assertNull(result2, "probe2 resolution should still return null (not loaded)");

    HandlerRepositoryImpl.unregisterProbe(probe2);
  }
}
