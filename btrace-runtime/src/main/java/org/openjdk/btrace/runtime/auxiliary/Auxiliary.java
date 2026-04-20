package org.openjdk.btrace.runtime.auxiliary;

import java.lang.invoke.MethodHandles;

public final class Auxiliary {
  private Auxiliary() {}

  /**
   * Returns a {@link MethodHandles.Lookup} captured inside this class.
   *
   * <p>Why this indirection exists: on JDK 15+, probe classes are installed via
   * {@link MethodHandles.Lookup#defineHiddenClass}, which requires a lookup
   * with both PRIVATE and MODULE access (see
   * {@link MethodHandles.Lookup#hasFullPrivilegeAccess}). Probes are renamed into
   * {@code Auxiliary}'s runtime package, so the defining lookup must target
   * this class.
   *
   * <p>The natural callsite — {@code BTraceRuntimeImpl_11} — lives on the
   * agent's {@code MaskedClassLoader} while {@code Auxiliary} lives on the
   * bootstrap loader. Calling
   * {@code MethodHandles.privateLookupIn(Auxiliary.class, MethodHandles.lookup())}
   * from there crosses a module boundary and yields a lookup without the
   * MODULE bit, so {@code defineHiddenClass} fails with
   * {@code IllegalAccessException: ... does not have full privilege access}.
   *
   * <p>Capturing the lookup from within {@code Auxiliary} keeps both the
   * lookup class and the class-being-defined anchored in the same module,
   * preserving full privilege access.
   */
  public static MethodHandles.Lookup lookup() {
    return MethodHandles.lookup();
  }
}
