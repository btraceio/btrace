/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.btrace.runtime;

import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.DebugSupport;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.org.objectweb.asm.FieldVisitor;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.tree.ClassNode;
import com.sun.btrace.org.objectweb.asm.tree.MethodNode;
import static com.sun.btrace.runtime.Constants.INJECTED_DESC;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Jaroslav Bachorik
 */
public final class BTraceClassNode extends ClassNode {
    private final List<OnMethod> onMethods;
    private final List<OnProbe> onProbes;
    private boolean unsafeScript = false;
    private boolean classRenamed = false;
    private final CycleDetector graph;
    protected final Set<String> injectedFields;

    private final Map<String, BTraceMethodNode> idmap;
    private final Preprocessor prep = new Preprocessor();

    BTraceClassNode() {
        super(Opcodes.ASM5);
        onMethods = new ArrayList<>();
        onProbes = new ArrayList<>();
        injectedFields = new HashSet<>();
        idmap = new HashMap<>();
        graph = new CycleDetector();
    }

    public static BTraceClassNode from(byte[] code, boolean unsafe) {
        BTraceClassNode bcn = new BTraceClassNode();
        Verifier verifier = new Verifier(bcn, unsafe);
        InstrumentUtils.accept(new ClassReader(code), verifier);
        bcn.preprocess();
        return bcn;
    }

    public static BTraceClassNode from(InputStream code, boolean unsafe) {
        try {
            BTraceClassNode bcn = new BTraceClassNode();
            Verifier verifier = new Verifier(bcn, unsafe);
            InstrumentUtils.accept(new ClassReader(code), verifier);
            bcn.preprocess();
            return bcn;
        } catch (IOException e) {
            DebugSupport.warning(e);
        }
        return null;
    }

    private void preprocess() {
        prep.process(this);
    }

    @Override
    public void visit(int version, int access, String name, String sig, String superType, String[] itfcs) {
        String className = checkClassName(name);
        classRenamed = !className.equals(name);
        super.visit(version, access, className, sig, superType, itfcs);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
        super.visitMethod(access, name, desc, sig, exceptions);
        MethodNode mn = (MethodNode)methods.remove(methods.size() - 1);
        BTraceMethodNode bmn = new BTraceMethodNode(mn, this);
        methods.add(bmn);
        idmap.put(CycleDetector.methodId(name, desc), bmn);
        return isUnsafe() ? bmn : new MethodVerifier(bmn, access, this.name, name, desc);
    }

    @Override
    public FieldVisitor	visitField(int access, final String name, String desc, String signature, Object value) {
        return new FieldVisitor(Opcodes.ASM5, super.visitField(access, name, desc, signature, value)) {
            @Override
            public AnnotationVisitor visitAnnotation(String type, boolean aVisible) {
                if (type.equals(INJECTED_DESC)) {
                    injectedFields.add(name);
                }
                return super.visitAnnotation(type, aVisible);
            }
        };
    }

    boolean isFieldInjected(String name) {
        return injectedFields.contains(name);
    }

    void addOnMethod(OnMethod om) {
        onMethods.add(om);
    }

    void addOnProbe(OnProbe op) {
        onProbes.add(op);
    }

    public List<OnMethod> getOnMethods() {
        return onMethods;
    }

    public List<OnProbe> getOnProbes() {
        return onProbes;
    }

    /**
     * Collects all the methods reachable from this particular method
     * @param name the method name
     * @param desc the method descriptor
     * @return the callee reachability closure
     */
    Set<BTraceMethodNode> callees(String name, String desc) {
        Set<String> closure = new HashSet<>();
        graph.callees(name, desc, closure);
        return fromIdSet(closure);
    }

    /**
     * Collects all the methods from which this particular method is reachable
     * @param name the method name
     * @param desc the method descriptor
     * @return the caller reachability closure
     */
    Set<BTraceMethodNode> callers(String name, String desc) {
        Set<String> closure = new HashSet<>();
        graph.callers(name, desc, closure);
        return fromIdSet(closure);
    }

    private Set<BTraceMethodNode> fromIdSet(Set<String> ids) {
        Set<BTraceMethodNode> methods = new HashSet<>();
        for(String id : ids) {
            BTraceMethodNode mn = idmap.get(id);
            if (mn != null) {
                methods.add(mn);
            }
        }
        return methods;
    }

    public byte[] getBytecode() {
        return getBytecode(false);
    }

    public byte[] getBytecode(boolean onlyBcpMethods) {
        ClassWriter cw = InstrumentUtils.newClassWriter();
        ClassVisitor cv = cw;
        if (onlyBcpMethods) {
            cv = new ClassVisitor(Opcodes.ASM5, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                    BTraceMethodNode bmn = idmap.get(CycleDetector.methodId(name, desc));
                    if (bmn != null) {
                        if (bmn.isBcpRequired()) {
                            return super.visitMethod(access, name, desc, sig, exceptions);
                        }
                        for(BTraceMethodNode c : bmn.getCallers()) {
                            if (c.isBcpRequired()) {
                                return super.visitMethod(access, name, desc, sig, exceptions);
                            }
                        }
                    }
                    return super.visitMethod(access, name, desc, sig, exceptions);
                }
            };
        }
        this.accept(cv);
        return cw.toByteArray();
    }

    void setUnsafe() {
        unsafeScript = true;
    }

    boolean isUnsafe() {
        return unsafeScript;
    }

    CycleDetector getGraph() {
        return graph;
    }

    public boolean isClassRenamed() {
        return classRenamed;
    }

    private static String checkClassName(String name) {
        int suffix = 1;
        String origClassName = name;
        while (BTraceRuntime.classNameExists(name.replace("/", "."))) {
            name = origClassName + "$" + (suffix++);
        }
        return name;
    }
}