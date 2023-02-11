package org.openjdk.btrace.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.HandlerRepository;

/** Invoke-dynamic linking support class */
public final class Indy {
  // Indy must reside in bootstrap but the HandlerRepository implementation is in agent area.
  // This field will be dynamically set from the actual HandlerRepository implementation class
  // static initializer.
  public static volatile HandlerRepository repository = null;

  private static final ThreadLocal<Relinker> relinkers = ThreadLocal.withInitial(Relinker::new);
  private static final MethodHandle NOOP_HANDLE;


  static {
    try {
      NOOP_HANDLE = MethodHandles.lookup().findStatic(Indy.class, "noop", MethodType.methodType(void.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // should never happen
      throw new RuntimeException(e);
    }
  }

  public static CallSite bootstrap(
      MethodHandles.Lookup caller, String name, MethodType type, String probeClassName)
      throws Exception {
    assert repository != null;

    Relinker relinker = relinkers.get();
    boolean failedLinking = BTraceRuntime.setLinking(true);
    if (failedLinking) {
      MutableCallSite callSite = new MutableCallSite(MethodHandles.dropArguments(NOOP_HANDLE, 0, type.parameterArray()));
      relinker.recordRelinking(callSite,  caller, name, type, probeClassName);
      return callSite;
    }

    try {
      MethodHandle mh = resolveHandle(caller, name, type, probeClassName);
      if (mh == null) {
        // if unable to properly link just ignore the instrumentation
        System.err.println("[BTRACE] Failed to link probe handler: " + probeClassName + "." + name);

        mh = MethodHandles.dropArguments(NOOP_HANDLE, 0, type.parameterArray());
      }

      return new ConstantCallSite(mh);
    } catch (Throwable t) {
      System.err.println("[BTRACE] Failed to link probe handler: " + probeClassName + "." + name + ": " + t.toString());
      return new ConstantCallSite(MethodHandles.dropArguments(NOOP_HANDLE, 0, type.parameterArray()));
    } finally {
      if (!failedLinking) {
        relinker.relink();
        BTraceRuntime.setLinking(false);
      }
    }
  }

  static MethodHandle resolveHandle(MethodHandles.Lookup caller, String name, MethodType type, String probeClassName) {
    byte[] classData =
            repository.getProbeHandler(
                    caller.lookupClass().getName(), probeClassName, name, type.toMethodDescriptorString());
    MethodHandle mh;
    try {
      caller =
              caller.defineHiddenClass(classData, false, MethodHandles.Lookup.ClassOption.NESTMATE);
      mh = caller.findStatic(caller.lookupClass(), name.substring(name.lastIndexOf("$") + 1), type);
      return mh;
    } catch (Throwable t) {
      return null;
    }
  }

  public static void noop() {}
}
