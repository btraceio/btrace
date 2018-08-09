package com.sun.btrace.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.io.Writer;
import java.util.*;

public class Printer {
    ArrayList enabledBits = new ArrayList();
    static int debugPrintIndentLevel = 0;

    public static int getDebugPrintIndentLevel() {
        return debugPrintIndentLevel;
    }

    ////////////
    // Output //
    ////////////
    PrintWriter writer;

    public Printer() {
    }

    void println() {
        if (enabled()) {
            writer.println();
            //System.err.println();//debug
        }
    }

    boolean enabled() {
        return (enabledBits.isEmpty() ||
                ((Boolean) enabledBits.get(enabledBits.size() - 1)));
    }

    public void setOut(Writer out) {
        writer = (out instanceof PrintWriter) ? (PrintWriter) out : new PrintWriter(out);
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