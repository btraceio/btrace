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

import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.DebugSupport;
import com.sun.btrace.VerifierException;
import com.sun.btrace.comm.RetransformClassNotification;
import com.sun.btrace.org.objectweb.asm.*;
import com.sun.btrace.org.objectweb.asm.tree.ClassNode;
import com.sun.btrace.org.objectweb.asm.tree.MethodNode;
import static com.sun.btrace.runtime.Constants.INJECTED_DESC;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.Iterator;
import static com.sun.btrace.runtime.ClassFilter.isSubTypeOf;

/**
 *
 * @author Jaroslav Bachorik
 */
public final class BTraceProbe extends ClassNode {
    private final List<OnMethod> onMethods;
    private final List<OnProbe> onProbes;
    private boolean trustedScript = false;
    private boolean classRenamed = false;
    private final CallGraph graph;
    protected final Set<String> injectedFields;

    private final Map<String, BTraceMethodNode> idmap;
    private final Preprocessor prep = new Preprocessor();

    private final BTraceProbeFactory factory;
    private volatile BTraceRuntime rt = null;
    private final DebugSupport debug;
    private ClassFilter filter = null;
    private String className, origName;
    private BTraceTransformer transformer;
    private VerifierException verifierException = null;

    private BTraceProbe(BTraceProbeFactory factory) {
        super(Opcodes.ASM5);
        this.factory = factory;
        this.debug = new DebugSupport(factory.getSettings());
        this.onMethods = new ArrayList<>();
        this.onProbes = new ArrayList<>();
        this.injectedFields = new HashSet<>();
        this.idmap = new HashMap<>();
        this.graph = new CallGraph();
    }

    BTraceProbe(BTraceProbeFactory factory, byte[] code) {
        this(factory);
        initialize(code);
    }

    BTraceProbe(BTraceProbeFactory factory, InputStream code) throws IOException {
        this(factory);
        initialize(code);
    }

    public boolean isTransforming() {
        return onMethods != null && onMethods.size() > 0;
    }

    @Override
    public void visit(int version, int access, String name, String sig, String superType, String[] itfcs) {
        this.origName = name;
        this.className = BTraceRuntime.getClientName(name);
        classRenamed = !className.equals(name);
        super.visit(version, access, className, sig, superType, itfcs);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
        super.visitMethod(access, name, desc, sig, exceptions);
        MethodNode mn = (MethodNode)methods.remove(methods.size() - 1);
        BTraceMethodNode bmn = new BTraceMethodNode(mn, this);
        methods.add(bmn);
        idmap.put(CallGraph.methodId(name, desc), bmn);
        return isTrusted() ? bmn : new MethodVerifier(bmn, access, this.origName, name, desc);
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

    public Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
        final Collection<OnMethod> applicables = new ArrayList<>(onMethods.size());
        final String targetName = cr.getJavaClassName();

        outer:
        for(OnMethod om : onMethods) {
            String probeClass = om.getClazz();
            if (probeClass == null && probeClass.isEmpty()) continue;

            if (probeClass.equals(targetName)) {
                applicables.add(om);
                continue;
            }
            // Check regex match
            if (om.isClassRegexMatcher() && !om.isClassAnnotationMatcher()) {
                Pattern p = Pattern.compile(probeClass);
                if (p.matcher(targetName).matches()) {
                    applicables.add(om);
                    continue;
                }
            }
            if (om.isClassAnnotationMatcher()) {
                Collection<String> annoTypes = cr.getAnnotationTypes();
                if (om.isClassRegexMatcher()) {
                    Pattern p = Pattern.compile(probeClass);
                    for(String annoType : annoTypes) {
                        if (p.matcher(annoType).matches()) {
                            applicables.add(om);
                            continue outer;
                        }
                    }
                } else {
                    if (annoTypes.contains(probeClass)) {
                        applicables.add(om);
                        continue;
                    }
                }
            }
            // And, finally, check the class hierarchy
            if (om.isSubtypeMatcher()) {
                // internal name of super type.
                if (isSubTypeOf(cr.getClassName(), cr.getClassLoader(), probeClass)) {
                    applicables.add(om);
                }
            }
        }
        return applicables;
    }

    public Iterable<OnMethod> onmethods() {
        return new Iterable<OnMethod>() {
            @Override
            public Iterator<OnMethod> iterator() {
                return Collections.unmodifiableCollection(onMethods).iterator();
            }
        };
    }

    public String getClassName() {
        return getClassName(false);
    }

    public String getClassName(boolean internal) {
        return internal ? className : className.replace("/", ".");
    }

    String translateOwner(String owner) {
        if (owner.equals(origName)) {
            return this.className;
        }
        return owner;
    }

    public Class register(BTraceRuntime rt, BTraceTransformer t) {
        byte[] code = getBytecode(true);
        if (debug.isDumpClasses()) {
            debug.dumpClass(name + "_bcp", code);
        }
        Class clz = defineClass(rt, code);
        t.register(this);
        this.transformer = t;
        this.rt = rt;
        return clz;
    }

    public void unregister() {
        if (transformer != null && isTransforming()) {
            if (debug.isDebug()) {
                debug.debug("onExit: removing transformer for " + getClassName());
            }
            transformer.unregister(this);
        }
        this.rt = null;
    }

    public byte[] getBytecode(boolean onlyBcpMethods) {
        ClassWriter cw = InstrumentUtils.newClassWriter();
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

    public boolean willInstrument(Class clz) {
        return filter.isCandidate(clz);
    }

    public boolean isClassRenamed() {
        return classRenamed;
    }

    public boolean isVerified() {
        return verifierException == null;
    }

    public VerifierException getVerifierException() {
        return verifierException;
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

    void setTrusted() {
        trustedScript = true;
    }

    boolean isTrusted() {
        return trustedScript;
    }

    CallGraph getGraph() {
        return graph;
    }

    void notifyTransform(String className) {
        if (rt != null && factory.getSettings().isTrackRetransforms()) {
            rt.send(new RetransformClassNotification(className.replace('/', '.')));
        }
    }

    /**
     * Maps a list of @OnProbe's to a list @OnMethod's using
     * probe descriptor XML files.
     */
    private void mapOnProbes() {
        ProbeDescriptorLoader pdl = getProbeDescriptorLoader();
        if (pdl == null) return;

        for (OnProbe op : onProbes) {
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
                 OnMethod omn = new OnMethod(op.getMethodNode());
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
        initialize(new ClassReader(code));
    }

    private void initialize(ClassReader cr) {
        try {
            Verifier v = new Verifier(this, factory.getSettings().isTrusted());
            if (debug.isDebug()) {
                debug.debug("verifying BTrace class ...");
            }
            cr.accept(v, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            if (debug.isDebug()) {
                debug.debug("BTrace class " + getClassName() + " verified");
                debug.debug("preprocessing BTrace class " + getClassName() + " ...");
            }
            prep.process(this);
            if (debug.isDebug()) {
                debug.debug("... preprocessed");
            }
            mapOnProbes();
            this.filter = new ClassFilter(onMethods);
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

    private Class defineClass(BTraceRuntime rt, byte[] code) {
        // This extra BTraceRuntime.enter is needed to
        // check whether we have already entered before.
        boolean enteredHere = BTraceRuntime.enter();
        try {
            // The trace class static initializer needs to be run
            // without BTraceRuntime.enter(). Please look at the
            // static initializer code of trace class.
            BTraceRuntime.leave();
            if (debug.isDebug()) {
                debug.debug("about to defineClass " + className);
            }
            Class clz = rt.defineClass(code, isTransforming());
            if (debug.isDebug()) {
                debug.debug("defineClass succeeded for " + className);
            }
            return clz;
        } finally {
            // leave BTraceRuntime enter state as it was before
            // we started executing this method.
            if (! enteredHere) BTraceRuntime.enter();
        }
    }

    @Override
    public String toString() {
        return "BTraceProbe{" + "onMethods=" + onMethods + ", onProbes=" + onProbes + ", trustedScript=" + trustedScript + ", classRenamed=" + classRenamed + ", injectedFields=" + injectedFields + ", className=" + className + '}';
    }
}