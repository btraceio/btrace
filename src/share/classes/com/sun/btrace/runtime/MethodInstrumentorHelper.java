package com.sun.btrace.runtime;

import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.Type;

public interface MethodInstrumentorHelper {
    public interface Accessor {
        MethodInstrumentorHelper methodHelper();
    }

    void insertFrameReplaceStack(Label l,Type ... stack);
    void insertFrameAppendStack(Label l, Type ... stack);
    void insertFrameSameStack(Label l);
    int newVar(Type t);
    int storeAsNew();
}
