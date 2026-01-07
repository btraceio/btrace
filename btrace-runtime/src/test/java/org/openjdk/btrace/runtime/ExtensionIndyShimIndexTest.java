package org.openjdk.btrace.runtime;

import org.junit.jupiter.api.Test;
import test.shim.ShimService;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionIndyShimIndexTest {

  @Test
  void resolvesNoopShimFromIndex() throws Throwable {
    MethodHandles.Lookup lk = MethodHandles.lookup();
    MethodType mt = MethodType.methodType(ShimService.class);
    // Force fallback path by using a bogus service name; optional=1 and mode=SHIM
    CallSite cs = ExtensionIndy.bootstrapFieldGet(
        lk, "svc", mt, "no.such.Service", "SIMPLE", "", 1, "SHIM");
    Object o = cs.getTarget().invokeWithArguments();
    assertNotNull(o, "Expected shim instance for bogus service in SHIM mode");
    assertTrue(o instanceof ShimService, "Resolved shim is not a ShimService: " + o.getClass().getName());
    assertEquals(42, ((ShimService) o).value(), "Noop shim should return 42");
  }

  @Test
  void resolvesThrowShimFromIndex() throws Throwable {
    MethodHandles.Lookup lk = MethodHandles.lookup();
    MethodType mt = MethodType.methodType(ShimService.class);
    // Force fallback path by using a bogus service name; optional=1 and mode=THROW
    CallSite cs = ExtensionIndy.bootstrapFieldGet(
        lk, "svc", mt, "no.such.Service", "SIMPLE", "", 1, "THROW");
    ShimService shim = (ShimService) cs.getTarget().invokeWithArguments();
    assertNotNull(shim, "Expected shim instance for bogus service in THROW mode");
    IllegalStateException ex = assertThrows(IllegalStateException.class, shim::value,
        "Expected IllegalStateException when invoking throw-shim");
    assertEquals("shim-throw", ex.getMessage(), "Throw shim message mismatch");
  }
}
