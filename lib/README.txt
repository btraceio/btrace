This directory contains 3rd party libraries: btrace-asm-5.0.4.jar and
btrace-jctools-core-1.1.jar.

These files were generated from "asm-all-5.0.4.jar" and "jctools-core-1.1.jar"
files respectively using the "JarJar" tool (please see
http://code.google.com/p/jarjar/) using the following rules:


    rule org.objectweb.asm.** com.sun.btrace.@0
    zap org.objectweb.asm.commons.**
    zap org.objectweb.asm.xml.**
    zap org.objectweb.asm.util.**
    zap org.objectweb.asm.tree.analysis.**

    rule org.jctools.** com.sun.btrace.@0
    zap org.jctools.queues.spec.**
    zap org.jctools.queues.atomic.**
    zap org.jctools.queues.Spmc**
    zap org.jctools.queues.Spsc**
    zap org.jctools.queues.MpscLinkedQueue**

i.e., We prefix every ASM and JCTools class with "com.sun.btrace" so that
the BTrace will not clash with application's use of ASM or JCTools (if any).
Also, this will allow us to trace application's ASM and JCTools usage as well.

Such renaming of ASM classes has indeed been suggested in ASM FAQ
at http://asm.objectweb.org/doc/faq.html. Renaming of JCTools just follows
this good advice.

License for ASM classes can be found in LICENSE_ASM.txt file.
License for JCTools classes can be found in LICENSE_JCTOOLS.txt file.
