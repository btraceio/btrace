package org.openjdk.btrace.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

final class Relinker {
    private static final class Relinking {
        private final MutableCallSite callSite;
        private final MethodHandles.Lookup caller;
        private final String name;
        private final MethodType type;
        private final String probeClassName;

        Relinking(MutableCallSite callSite, MethodHandles.Lookup caller, String name, MethodType type, String probeClassName) {
            this.callSite = callSite;
            this.caller = caller;
            this.name = name;
            this.type = type;
            this.probeClassName = probeClassName;
        }

        void relink() {
            MethodHandle rebound = Indy.resolveHandle(caller, name, type, probeClassName);
            if (rebound != null) {
                callSite.setTarget(rebound);
            }
        }
    }

    private int pos = 0;
    private Relinking[] relinkings = null;

    void recordRelinking(MutableCallSite callSite, MethodHandles.Lookup caller, String name, MethodType type, String probeClassName) {
        Relinking r = new Relinking(callSite, caller, name, type, probeClassName);
        if (relinkings == null) {
            relinkings = new Relinking[8];
        } else if (relinkings.length <= 32768) {
            if (pos >= relinkings.length) {
                Relinking[] newOne = new Relinking[relinkings.length * 2];
                System.arraycopy(relinkings, 0, newOne, 0, relinkings.length);
                relinkings = newOne;
            }
            relinkings[pos++] = r;
        }
    }

    void relink() {
        for (int i = 0; i < pos; i++) {
            relinkings[i].relink();
            relinkings[i] = null;
        }
        pos = 0;
    }
}
