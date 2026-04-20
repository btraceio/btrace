package org.openjdk.btrace.runtime.auxiliary;

import java.lang.invoke.MethodHandles;

public final class Auxiliary {
  private Auxiliary() {}

  /**
   * Returns a {@link MethodHandles.Lookup} anchored on this class.
   *
   * <p>Probes on JDK 15+ are installed via {@link MethodHandles.Lookup#defineHiddenClass}
   * into {@code Auxiliary}'s runtime package and require a lookup with full privilege
   * access. Obtaining the lookup here (rather than via {@code privateLookupIn} from a
   * caller in a different module) keeps the MODULE bit and avoids
   * {@code IllegalAccessException} when the caller's class loader is distinct from
   * this class's — e.g. a masked-jar deployment where the agent sits on
   * {@code MaskedClassLoader} and {@code Auxiliary} sits on the bootstrap loader.
   *
   * <p>Public because the primary caller lives in the sibling package
   * {@code org.openjdk.btrace.runtime}.
   */
  public static MethodHandles.Lookup lookup() {
    return MethodHandles.lookup();
  }
}
