This directory contains btrace-asm-3.3.jar. This file was
generated from "asm-3.3.jar" using the "JarJar" tool (please
see http://code.google.com/p/jarjar/) using the following rule:


    rule org.objectweb.asm.** com.sun.btrace.@0


i.e., We prefix every ASM class with "com.sun.btrace" so that 
the BTrace will not clash with application's use of ASM (if any).
Also, this will allow us to trace application's ASM usage as well.
Such renaming of ASM classes has indeed been suggested in ASM FAQ 
at http://asm.objectweb.org/doc/faq.html.
