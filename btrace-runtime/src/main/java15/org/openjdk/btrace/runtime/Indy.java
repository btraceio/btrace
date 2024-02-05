package org.openjdk.btrace.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.openjdk.btrace.core.HandlerRepository;

/** Invoke-dynamic linking support class */
public final class Indy {
  // Indy must reside in bootstrap but the HandlerRepository implementation is in agent area.
  // This field will be dynamically set from the actual HandlerRepository implementation class
  // static initializer.
  public static volatile HandlerRepository repository = null;

  public static CallSite bootstrap(
      MethodHandles.Lookup caller, String name, MethodType type, String probeClassName)
      throws Exception {
    assert repository != null;
    try {
      MethodHandle mh;
      try {
        byte[] classData =
                repository.getProbeHandler(
                        caller.lookupClass().getName(), probeClassName, name, type.toMethodDescriptorString());

        caller =
                caller.defineHiddenClass(classData, false, MethodHandles.Lookup.ClassOption.NESTMATE);
        mh = caller.findStatic(caller.lookupClass(), name.substring(name.lastIndexOf("$") + 1), type);
      } catch (Throwable t) {
        // if unable to properly link just ignore the instrumentation
        MethodHandle noopHandle =
                MethodHandles.lookup().findStatic(Indy.class, "noop", MethodType.methodType(void.class));
        mh = MethodHandles.dropArguments(noopHandle, 0, type.parameterArray());
      }

      return new ConstantCallSite(mh);
    } finally {
//      LinkingFlag.reset();
    }
  }

  public static void noop() {}
}
