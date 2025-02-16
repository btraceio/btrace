package org.openjdk.btrace.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;

import org.openjdk.btrace.core.BTraceUtils;
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
    MethodHandle mh;
    try {
      HandlerRepository.Handler loaded =
              repository.getProbeHandler(
                      caller, probeClassName, cdata -> {
                        try {
                          return caller.defineHiddenClass(cdata, false, MethodHandles.Lookup.ClassOption.NESTMATE);
                        } catch (IllegalAccessException e) {
                          throw new RuntimeException(e);
                        }
                      });

      mh = loaded.findProbeMethod(name.substring(name.lastIndexOf("$") + 1), type);
    } catch (Throwable t) {
      // if unable to properly link just ignore the instrumentation
      MethodHandle noopHandle =
              MethodHandles.lookup().findStatic(Indy.class, "noop", MethodType.methodType(void.class));
      mh = MethodHandles.dropArguments(noopHandle, 0, type.parameterArray());
    }

    return new ConstantCallSite(mh);
  }

  public static CallSite methodRef(MethodHandles.Lookup caller, String name, MethodType type, String className, String methodName, String desc) throws Exception {
    assert repository != null;
    MethodHandle mh = null;
    HandlerRepository.Handler loaded = null;
    if (name.startsWith("BTraceUtils#")) {
      try {
        if (!caller.toString().contains("/")) {
          mh = caller.findStatic(caller.lookupClass(), methodName, MethodType.fromMethodDescriptorString(desc, caller.lookupClass().getClassLoader()));
        } else {
          loaded =
                  repository.getProbeHandler(
                          caller, className, cdata -> {
                            try {
                              return caller.defineHiddenClass(cdata, false, MethodHandles.Lookup.ClassOption.NESTMATE);
                            } catch (IllegalAccessException e) {
                              throw new RuntimeException(e);
                            }
                          });

          mh = loaded.findProbeMethod(methodName, MethodType.fromMethodDescriptorString(desc, caller.lookupClass().getClassLoader()));
        }
      } catch (Throwable ignored) {
      }
    }

    if (mh == null) {
      // if unable to properly link just ignore the method reference
      MethodHandle noopHandle =
              MethodHandles.lookup().findStatic(Indy.class, "noop", MethodType.methodType(void.class));
      mh = MethodHandles.dropArguments(noopHandle, 0, type.parameterArray());
      return new ConstantCallSite(mh);
    }

    // offset 12 - BTraceUtils#
    name = name.substring(12);
    MethodHandle target = MethodHandles.publicLookup().findStatic(BTraceUtils.class, name, MethodType.methodType(void.class, MethodHandle.class, Collection.class));
    target = target.bindTo(mh);

    return new ConstantCallSite(target);
  }

  public static void noop() {}
}
