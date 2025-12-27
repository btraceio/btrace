package org.openjdk.btrace.instr;

import java.util.function.Supplier;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

public interface MethodInstrumentorHelper {
  void insertFrameReplaceStack(Label l, Type... stack);

  void insertFrameAppendStack(Label l, Type... stack);

  void insertFrameSameStack(Label l);

  void addTryCatchHandler(Label start, Label handler);

  int newVar(Type t);

  int storeAsNew();

  /**
   * Get or create a MethodTrackingContext for the given method ID.
   * Ensures multiple instrumentors for the same method share the same context.
   *
   * @param methodId unique method identifier
   * @param factory supplier to create new context if needed
   * @return shared MethodTrackingContext instance
   */
  MethodTrackingContext getOrCreateTrackingContext(
      int methodId, Supplier<MethodTrackingContext> factory);

  /**
   * Returns whether level variable caching should be used.
   * Caching is only needed when multiple handlers require level checks.
   *
   * @return true if level variable should be cached, false otherwise
   */
  boolean shouldCacheLevelVar();

  interface Accessor {
    MethodInstrumentorHelper methodHelper();
  }
}
