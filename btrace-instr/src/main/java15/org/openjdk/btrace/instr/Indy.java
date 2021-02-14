package org.openjdk.btrace.instr;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Invoke-dynamic linking support class */
public final class Indy {
  public static CallSite bootstrap(
      MethodHandles.Lookup caller, String name, MethodType type, String probeClassName)
      throws Exception {
    byte[] classData =
        HandlerRepository.getProbeHandler(
            caller.lookupClass().getName(), probeClassName, name, type.toMethodDescriptorString());
    MethodHandle mh;
    try {
      caller =
          caller.defineHiddenClass(classData, false, MethodHandles.Lookup.ClassOption.NESTMATE);
      mh = caller.findStatic(caller.lookupClass(), name.substring(name.lastIndexOf("$") + 1), type);
    } catch (Throwable t) {
      // if unable to properly link just ignore the instrumentation
      System.err.println(
          "[BTRACE] Failed to link probe handler: " + probeClassName + "." + name + "\n" + t);
      MethodHandle noopHandle =
          MethodHandles.lookup().findStatic(Indy.class, "noop", MethodType.methodType(void.class));
      mh = MethodHandles.dropArguments(noopHandle, 0, type.parameterArray());
    }

    return new ConstantCallSite(mh);
  }

  public static void noop() {}
}
