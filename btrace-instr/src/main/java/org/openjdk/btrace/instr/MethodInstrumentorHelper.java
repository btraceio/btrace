package org.openjdk.btrace.instr;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

public interface MethodInstrumentorHelper {
  void insertFrameReplaceStack(Label l, Type... stack);

  void insertFrameAppendStack(Label l, Type... stack);

  void insertFrameSameStack(Label l);

  void addTryCatchHandler(Label start, Label handler);

  int newVar(Type t);

  int storeAsNew();

  interface Accessor {
    MethodInstrumentorHelper methodHelper();
  }
}
