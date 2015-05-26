This directory contains btrace-asm-5.0.4.jar. This file was
generated from "asm-all-5.0.4.jar" using the "JarJar" tool (please
see http://code.google.com/p/jarjar/) using the following rules:


    rule org.objectweb.asm.** com.sun.btrace.@0
    zap org.objectweb.asm.commons.**
    zap org.objectweb.asm.xml.**
    zap org.objectweb.asm.util.**
    zap org.objectweb.asm.tree.analysis.**

i.e., We prefix every ASM class with "com.sun.btrace" so that
the BTrace will not clash with application's use of ASM (if any).
Also, this will allow us to trace application's ASM usage as well.
Such renaming of ASM classes has indeed been suggested in ASM FAQ
at http://asm.objectweb.org/doc/faq.html.
