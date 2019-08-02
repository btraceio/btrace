package org.openjdk.btrace.compiler;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;

class Printer {
    static int debugPrintIndentLevel = 0;
    ////////////
    // Output //
    ////////////
    private final PrintWriter writer;
    private final ArrayList enabledBits = new ArrayList();

    Printer() {
        writer = new PrintWriter(System.err);
    }

    Printer(Writer out) {
        writer = (out instanceof PrintWriter) ? (PrintWriter) out : new PrintWriter(out);
    }

    public static int getDebugPrintIndentLevel() {
        return debugPrintIndentLevel;
    }

    void println() {
        if (enabled()) {
            writer.println();
        }
    }

    boolean enabled() {
        return (enabledBits.isEmpty() ||
                ((Boolean) enabledBits.get(enabledBits.size() - 1)));
    }

    void pushEnableBit(boolean enabled) {
        enabledBits.add(enabled);
        ++debugPrintIndentLevel;
        //debugPrint(false, "PUSH_ENABLED, NOW: " + enabled());
    }

    void print(String s) {
        if (enabled()) {
            writer.print(s);
            //System.out.print(s);//debug
        }
    }

    void flush() {
        if (enabled()) {
            writer.flush();
            //System.err.flush(); //debug
        }
    }

    void popEnableBit() {
        if (enabledBits.isEmpty()) {
            System.err.println("WARNING: mismatched #ifdef/endif pairs");
            return;
        }
        enabledBits.remove(enabledBits.size() - 1);
        --debugPrintIndentLevel;
        //debugPrint(false, "POP_ENABLED, NOW: " + enabled());
    }
}