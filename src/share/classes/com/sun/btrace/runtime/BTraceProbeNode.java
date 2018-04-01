/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.btrace.runtime;

import com.sun.btrace.ArgsMap;
import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.DebugSupport;
import com.sun.btrace.VerifierException;
import com.sun.btrace.comm.RetransformClassNotification;
import com.sun.btrace.org.objectweb.asm.*;
import static com.sun.btrace.org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static com.sun.btrace.org.objectweb.asm.Opcodes.ACC_STATIC;
import com.sun.btrace.org.objectweb.asm.tree.ClassNode;
import com.sun.btrace.org.objectweb.asm.tree.MethodNode;
import static com.sun.btrace.runtime.Constants.ANYTYPE_DESC;
import static com.sun.btrace.runtime.Constants.INJECTED_DESC;
import static com.sun.btrace.runtime.Constants.OBJECT_DESC;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 *
 * @author Jaroslav Bachorik
 */
public final class BTraceProbeNode extends ClassNode implements BTraceProbe {
    final BTraceProbeSupport delegate;

    final BTraceProbeFactory factory;

    final DebugSupport debug;

    private final CallGraph graph;

    private final Map<String, BTraceMethodNode> idmap;
    private final Preprocessor prep;

    private volatile BTraceRuntime rt = null;
    
    private BTraceTransformer transformer;
    private VerifierException verifierException = null;

    private BTraceProbeNode(BTraceProbeFactory factory) {
        super(Opcodes.ASM5);
        this.factory = factory;
        this.debug = new DebugSupport(factory.getSettings());
        this.delegate = new BTraceProbeSupport(debug);
        this.idmap = new HashMap<>();
        this.graph = new CallGraph();
        this.prep = new Preprocessor(debug);
    }

    BTraceProbeNode(BTraceProbeFactory factory, byte[] code) {
        this(factory);
        initialize(code);
    }

    BTraceProbeNode(BTraceProbeFactory factory, InputStream code) throws IOException {
        this(factory);
        initialize(code);
    }

    @Override
    public boolean isTransforming() {
        return delegate.isTransforming();
    }

    @Override
    public void visit(int version, int access, String name, String sig, String superType, String[] itfcs) {
        delegate.setClassName(name);
        super.visit(version, access, delegate.getClassName(true), sig, superType, itfcs);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
        super.visitMethod(access, name, desc, sig, exceptions);
        MethodNode mn = (MethodNode)methods.remove(methods.size() - 1);
        BTraceMethodNode bmn = new BTraceMethodNode(mn, this);
        methods.add(bmn);
        idmap.put(CallGraph.methodId(name, desc), bmn);
        return isTrusted() ? bmn : new MethodVerifier(bmn, access, delegate.getOrigName(), name, desc);
    }

    @Override
    public FieldVisitor	visitField(int access, final String name, final String desc, String signature, Object value) {
        return new FieldVisitor(Opcodes.ASM5, super.visitField(access, name, desc, signature, value)) {
            @Override
            public AnnotationVisitor visitAnnotation(String type, boolean aVisible) {
                if (type.equals(INJECTED_DESC)) {
                    delegate.addServiceField(name, Type.getType(desc).getInternalName());
                }
                return super.visitAnnotation(type, aVisible);
            }
        };
    }

    @Override
    public Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
        return delegate.getApplicableHandlers(cr);
    }

    @Override
    public Iterable<OnMethod> onmethods() {
        return delegate.onmethods();
    }

    public Collection<OnMethod> getOnMethods() {
        return delegate.getOnMethods();
    }

    @Override
    public Iterable<OnProbe> onprobes() {
        return delegate.onprobes();
    }

    @Override
    public String getClassName() {
        return getClassName(false);
    }

    @Override
    public String getClassName(boolean internal) {
        return delegate.getClassName(internal);
    }

    String translateOwner(String owner) {
        return delegate.translateOwner(owner);
    }

    @Override
    public Class register(BTraceRuntime rt, BTraceTransformer t) {
        byte[] code = getBytecode(true);
        if (debug.isDumpClasses()) {
            debug.dumpClass(name + "_bcp", code);
        }
        Class clz = delegate.defineClass(rt, code);
        t.register(this);
        this.transformer = t;
        this.rt = rt;
        return clz;
    }

    @Override
    public void unregister() {
        if (transformer != null && isTransforming()) {
            if (debug.isDebug()) {
                debug.debug("onExit: removing transformer for " + getClassName());
            }
            transformer.unregister(this);
        }
        this.rt = null;
    }

    @Override
    public byte[] getFullBytecode() {
        return getBytecode(false);
    }

    @Override
    public byte[] getDataHolderBytecode() {
        return getBytecode(true);
    }

    private byte[] getBytecode(boolean onlyBcpMethods) {
        ClassWriter cw = InstrumentUtils.newClassWriter(true);
        ClassVisitor cv = cw;
        if (onlyBcpMethods) {
            cv = new ClassVisitor(Opcodes.ASM5, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                    if (name.startsWith("<")) {
                        // never check constructor and static initializer
                        return super.visitMethod(access, name, desc, sig, exceptions);
                    }
                    BTraceMethodNode bmn = idmap.get(CallGraph.methodId(name, desc));
                    if (bmn != null) {
                        if (bmn.isBcpRequired()) {
                            return super.visitMethod(access, name, desc, sig, exceptions);
                        }
                        for(BTraceMethodNode c : bmn.getCallers()) {
                            if (c.isBcpRequired()) {
                                return super.visitMethod(access, name, desc, sig, exceptions);
                            }
                        }
                        return null;
                    }
                    return super.visitMethod(access, name, desc, sig, exceptions);
                }
            };
        }
        this.accept(cv);
        return cw.toByteArray();
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

    @Override
    public boolean willInstrument(Class clz) {
        return delegate.willInstrument(clz);
    }

    @Override
    public boolean isClassRenamed() {
        return delegate.isClassRenamed();
    }

    @Override
    public boolean isVerified() {
        return verifierException == null;
    }

    private VerifierException getVerifierException() {
        return verifierException;
    }

    boolean isFieldInjected(String name) {
        return delegate.isFieldInjected(name);
    }

    void addOnMethod(OnMethod om) {
        delegate.addOnMethod(om);
    }

    void addOnProbe(OnProbe op) {
        delegate.addOnProbe(op);
    }

    void setTrusted() {
        delegate.setTrusted();
    }

    boolean isTrusted() {
        return delegate.isTrusted();
    }

    CallGraph getGraph() {
        return graph;
    }

    @Override
    public void notifyTransform(String className) {
        if (rt != null && factory.getSettings().isTrackRetransforms()) {
            rt.send(new RetransformClassNotification(className.replace('/', '.')));
        }
    }

    @Override
    public void checkVerified() {
        if (!isVerified()) {
            throw getVerifierException();
        }
    }

    @Override
    public void copyHandlers(ClassVisitor copyingVisitor) {
        Set<MethodNode> copyNodes = new TreeSet<>(BTraceMethodNode.COMPARATOR);

        for (OnMethod om : onmethods()) {
            if (!om.isCalled()) {
                continue;
            }

            BTraceMethodNode bmn = om.getMethodNode();

            MethodNode mn = copy(bmn);

            copyNodes.add(mn);
            for(BTraceMethodNode c : bmn.getCallees()) {
                copyNodes.add(copy(c));
            }
        }
        for(MethodNode mn : copyNodes) {
            mn.accept(copyingVisitor);
        }
    }

    @Override
    public void applyArgs(ArgsMap argsMap) {
        delegate.applyArgs(argsMap);
    }

    /**
     * Maps a list of @OnProbe's to a list @OnMethod's using
     * probe descriptor XML files.
     */
    private void mapOnProbes() {
        ProbeDescriptorLoader pdl = getProbeDescriptorLoader();
        if (pdl == null) return;

        for (OnProbe op : delegate.onprobes()) {
            String ns = op.getNamespace();
            if (debug.isDebug()) debug.debug("about to load probe descriptor for " + ns);
            // load probe descriptor for this namespace
            ProbeDescriptor probeDesc = pdl.load(ns);
            if (probeDesc == null) {
                if (debug.isDebug()) debug.debug("failed to find probe descriptor for " + ns);
                continue;
            }
            // find particular probe mappings using "local" name
            OnProbe foundProbe = probeDesc.findProbe(op.getName());
            if (foundProbe == null) {
                if (debug.isDebug()) debug.debug("no probe mappings for " + op.getName());
                continue;
            }
            if (debug.isDebug()) debug.debug("found probe mappings for " + op.getName());
            Collection<OnMethod> omColl = foundProbe.getOnMethods();
            for (OnMethod om : omColl) {
                 // copy the info in a new OnMethod so that
                 // we can set target method name and descriptor
                 // Note that the probe descriptor cache is used
                 // across BTrace sessions. So, we should not update
                 // cached OnProbes (and their OnMethods).
                 OnMethod omn = new OnMethod(op.getMethodNode(), debug);
                 omn.copyFrom(om);
                 omn.setTargetName(op.getTargetName());
                 omn.setTargetDescriptor(op.getTargetDescriptor());
                 omn.setClassNameParameter(op.getClassNameParameter());
                 omn.setMethodParameter(op.getMethodParameter());
                 omn.setDurationParameter(op.getDurationParameter());
                 omn.setMethodFqn(op.isMethodFqn());
                 omn.setReturnParameter(op.getReturnParameter());
                 omn.setSelfParameter(op.getSelfParameter());
                 omn.setTargetInstanceParameter(op.getTargetInstanceParameter());
                 omn.setTargetMethodOrFieldFqn(op.isTargetMethodOrFieldFqn());
                 omn.setTargetMethodOrFieldParameter(op.getTargetMethodOrFieldParameter());
                 addOnMethod(omn);
            }
        }
    }

    private ProbeDescriptorLoader getProbeDescriptorLoader() {
        String path = factory.getSettings().getProbeDescPath();
        return new ProbeDescriptorLoader(path, debug);
    }

    private void initialize(byte[] code) {
        ClassReader cr = new ClassReader(code);
        if (debug.isDumpClasses()) {
            debug.dumpClass(cr.getClassName() + "_orig", code);
        }
        initialize(cr);
    }

    private void initialize(InputStream code) throws IOException {
        initialize(readFully(code));
    }

    private void initialize(ClassReader cr) {
        try {
            Verifier v = new Verifier(this, factory.getSettings().isTrusted());
            if (debug.isDebug()) {
                debug.debug("verifying BTrace class ...");
            }
            cr.accept(v, ClassReader.SKIP_DEBUG);
            if (debug.isDebug()) {
                debug.debug("BTrace class " + getClassName() + " verified");
                debug.debug("preprocessing BTrace class " + getClassName() + " ...");
            }
            prep.process(this);
            if (debug.isDebug()) {
                debug.debug("... preprocessed");
            }
            mapOnProbes();
        } catch (VerifierException e) {
            verifierException = e;
        } finally {
            if (debug.isDumpClasses()) {
                debug.dumpClass(name, getBytecode(false));
            }
        }
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

    private MethodNode copy(MethodNode n) {
        String[] exceptions = n.exceptions != null ? ((List<String>)n.exceptions).toArray(new String[0]) : null;
        MethodNode mn = new MethodNode(Opcodes.ASM5, n.access, n.name, n.desc, n.signature, exceptions);
        n.accept(mn);
        mn.access = ACC_STATIC | ACC_PRIVATE;
        mn.desc = mn.desc.replace(ANYTYPE_DESC, OBJECT_DESC);
        mn.signature = mn.signature != null ? mn.signature.replace(ANYTYPE_DESC, OBJECT_DESC) : null;
        mn.name = InstrumentUtils.getActionPrefix(getClassName(true)) + mn.name;
        return mn;
    }

    private byte[] readFully(InputStream is) throws IOException {
        final int bufSize = 512;
        int pos = 0;
        byte[] finArr = new byte[1024];
        byte[] buff = new byte[bufSize];

        int read = 0;
        while ((read = is.read(buff, 0, bufSize)) > 0) {
            int newpos = pos + read;
            if (newpos >= finArr.length) {
                finArr = Arrays.copyOf(finArr, finArr.length * 2);
            }
            System.arraycopy(buff, 0, finArr, pos, read);
            pos = newpos;
        }
        return Arrays.copyOfRange(finArr, 0, pos);
    }


    @Override
    public String toString() {
        return "BTraceProbe{" + "delegate=" + delegate + '}';
    }
}