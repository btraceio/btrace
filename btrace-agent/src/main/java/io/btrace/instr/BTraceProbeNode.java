/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.DebugSupport;
import io.btrace.core.Messages;
import io.btrace.core.VerifierException;
import io.btrace.core.comm.RetransformClassNotification;
import io.btrace.core.extensions.Permission;
import io.btrace.extension.ServiceDeclarationRegistry;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jaroslav Bachorik
 */
public final class BTraceProbeNode extends ClassNode implements BTraceProbe {
  private static final Logger log = LoggerFactory.getLogger(BTraceProbeNode.class);

  final BTraceProbeSupport delegate;

  final BTraceProbeFactory factory;

  final DebugSupport debug;

  private final CallGraph graph;

  private final Map<String, BTraceMethodNode> idmap;
  private final Set<String> jfrHandlers = new HashSet<>();
  private final Preprocessor prep;
  private final BTraceBCPClassLoader bcpResourceClassLoader;

  private volatile BTraceRuntime.Impl rt = null;

  private BTraceTransformer transformer;
  private VerifierException verifierException = null;

  private BTraceProbeNode(BTraceProbeFactory factory) {
    super(Opcodes.ASM9);
    this.factory = factory;
    bcpResourceClassLoader = new BTraceBCPClassLoader(factory.getSettings());
    debug = new DebugSupport(factory.getSettings());
    delegate = new BTraceProbeSupport();
    idmap = new HashMap<>();
    graph = new CallGraph();
    prep = new Preprocessor();
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
  public void visit(
      int version, int access, String name, String sig, String superType, String[] itfcs) {
    delegate.setClassName(name);
    super.visit(version, access, delegate.getClassName(true), sig, superType, itfcs);
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String desc, String sig, String[] exceptions) {
    super.visitMethod(access, name, desc, sig, exceptions);
    MethodNode mn = methods.remove(methods.size() - 1);
    BTraceMethodNode bmn = new BTraceMethodNode(mn, this, jfrHandlers.contains(name));
    methods.add(bmn);
    idmap.put(CallGraph.methodId(name, desc), bmn);
    return isTrusted()
        ? bmn
        : new MethodVerifier(
            bmn, access, delegate.getOrigName(), name, desc, bcpResourceClassLoader);
  }

  @Override
  public FieldVisitor visitField(
      int access, String name, String desc, String signature, Object value) {
    return new FieldVisitor(Opcodes.ASM9, super.visitField(access, name, desc, signature, value)) {
      @Override
      public AnnotationVisitor visitAnnotation(String type, boolean aVisible) {
        AnnotationVisitor av = super.visitAnnotation(type, aVisible);
        if (type.equals(Constants.INJECTED_DESC)) {
          // Bytecode-time validation for @Injected fields:
          // This ASM-based check enforces that the injected service type is declared by
          // some available extension (as exposed via ServiceDeclarationRegistry). It does
          // not load classes; instead, the agent registers a resolver that knows about
          // extension manifests. This complements the runtime validation in
          // io.btrace.agent.Client#validateDeclaredServices, which uses reflection
          // to account for classloader identity, JPMS access, and linkage in the actual
          // target JVM.
          String internal = Type.getType(desc).getInternalName();
          String fqcn = internal.replace('/', '.');
          if (!ServiceDeclarationRegistry.isDeclaredService(fqcn)) {
            throw new VerifierException(Messages.get("invalid.injected.service") + ": " + fqcn);
          }
          delegate.addServiceField(name, internal);
        }
        if (type.equals("Lio/btrace/core/annotations/Event;")) {
          av =
              new AnnotationVisitor(Opcodes.ASM8, av) {
                @Override
                public void visit(String name, Object value) {
                  if (name.equals("handler") && value instanceof String) {
                    jfrHandlers.add((String) value);
                  }
                  super.visit(name, value);
                }
              };
        }
        return av;
      }
    };
  }

  @Override
  public Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
    return delegate.getApplicableHandlers(cr);
  }

  @Override
  public Collection<OnMethod> getApplicableHandlers(ClassMeta meta) {
    return delegate.getApplicableHandlers(meta);
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
  public Class<?> register(BTraceRuntime.Impl rt, BTraceTransformer t) {
    byte[] code = getBytecode(true);
    if (debug.isDumpClasses()) {
      debug.dumpClass(name + "_bcp", code);
    }
    Class<?> clz = delegate.defineClass(rt, code);
    HandlerRepositoryImpl.registerProbe(this);
    t.register(this);
    transformer = t;
    this.rt = rt;
    return clz;
  }

  @Override
  public Class<?> getProbeClass() {
    return delegate.getProbeClass();
  }

  @Override
  public java.lang.invoke.MethodHandle getCachedHandler(
      String handlerName, java.lang.invoke.MethodType type) {
    return delegate.getCachedHandler(handlerName, type);
  }

  @Override
  public void cacheHandler(
      String handlerName, java.lang.invoke.MethodType type, java.lang.invoke.MethodHandle mh) {
    delegate.cacheHandler(handlerName, type, mh);
  }

  @Override
  public void unregister() {
    HandlerRepositoryImpl.unregisterProbe(this);
    if (transformer != null && isTransforming()) {
      if (log.isDebugEnabled()) {
        log.debug("onExit: removing transformer for {}", getClassName());
      }
      transformer.unregister(this);
    }
    delegate.clearProbeClass();
    rt = null;
  }

  @Override
  public byte[] getFullBytecode() {
    return getBytecode(false);
  }

  @Override
  public byte[] getDataHolderBytecode() {
    return getBytecode(true);
  }

  @Override
  public BTraceRuntime.Impl getRuntime() {
    return rt;
  }

  private byte[] getBytecode(boolean onlyBcpMethods) {
    ClassWriter cw = InstrumentUtils.newClassWriter(true);
    ClassVisitor cv = cw;
    if (onlyBcpMethods) {
      cv =
          new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String desc, String sig, String[] exceptions) {
              if (name.startsWith("<")) {
                // never check constructor and static initializer
                return super.visitMethod(access, name, desc, sig, exceptions);
              }
              BTraceMethodNode bmn = idmap.get(CallGraph.methodId(name, desc));
              if (bmn != null) {
                // Include BCP-required methods AND probe handler methods:
                // Handlers are invoked via INVOKEDYNAMIC (IndyDispatcher.bootstrap),
                // so the handler method body must be present in the bootstrap-CL probe class.
                // This applies to @OnMethod handlers (om != null) and @OnProbe handlers
                // (op != null — mapped to @OnMethod entries via mapOnProbes()).
                boolean isHandler = bmn.getOnMethod() != null || bmn.getOnProbe() != null;
                if (bmn.isBcpRequired() || isHandler) {
                  // Handlers: rewrite descriptor AnyType → Object to match the INDY call site
                  // type (Instrumentor.invokeBTraceAction replaces AnyType with Object in
                  // the INDY descriptor for JVM stack compatibility).
                  String effectiveDesc =
                      isHandler
                          ? desc.replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC)
                          : desc;
                  return super.visitMethod(access, name, effectiveDesc, sig, exceptions);
                }
                for (BTraceMethodNode c : bmn.getCallers()) {
                  boolean callerIsHandler = c.getOnMethod() != null || c.getOnProbe() != null;
                  if (c.isBcpRequired() || callerIsHandler) {
                    return super.visitMethod(access, name, desc, sig, exceptions);
                  }
                }
                return null;
              }
              return super.visitMethod(access, name, desc, sig, exceptions);
            }
          };
    }
    accept(cv);
    return cw.toByteArray();
  }

  /**
   * Collects all the methods reachable from this particular method
   *
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
   *
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
  public boolean willInstrument(Class<?> clz) {
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

  @Override
  public String getActionPrefix() {
    return delegate.getActionPrefix();
  }

  private VerifierException getVerifierException() {
    return verifierException;
  }

  boolean isFieldInjected(String name) {
    return delegate.isFieldInjected(name);
  }

  boolean isServiceType(String typeName) {
    return delegate.isServiceType(typeName);
  }

  void addOnMethod(OnMethod om) {
    delegate.addOnMethod(om);
  }

  void addOnProbe(OnProbe op) {
    delegate.addOnProbe(op);
  }

  void addRequiredPermission(Permission permission) {
    delegate.addRequiredPermission(permission);
  }

  @Override
  public Set<Permission> getRequiredPermissions() {
    return delegate.getRequiredPermissions();
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
      rt.sendCommand(new RetransformClassNotification(className.replace('/', '.')));
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
      for (BTraceMethodNode c : bmn.getCallees()) {
        copyNodes.add(copy(c));
      }
    }
    copyingVisitor.visit(
        Opcodes.V1_7,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
        getClassName(true),
        null,
        "java/lang/Object",
        null);
    for (MethodNode mn : copyNodes) {
      mn.accept(copyingVisitor);
    }
    copyingVisitor.visitEnd();
  }

  @Override
  public void applyArgs(ArgsMap argsMap) {
    delegate.applyArgs(argsMap);
  }

  /** Maps a list of @OnProbe's to a list @OnMethod's using probe descriptor XML files. */
  private void mapOnProbes() {
    ProbeDescriptorLoader pdl = getProbeDescriptorLoader();

    for (OnProbe op : delegate.onprobes()) {
      String ns = op.getNamespace();
      if (log.isDebugEnabled()) {
        log.debug("about to load probe descriptor for namespace {}", ns);
      }
      // load probe descriptor for this namespace
      ProbeDescriptor probeDesc = pdl.load(ns);
      if (probeDesc == null) {
        if (log.isDebugEnabled()) {
          log.debug("failed to find probe descriptor for namespace {}", ns);
        }
        continue;
      }
      // find particular probe mappings using "local" name
      OnProbe foundProbe = probeDesc.findProbe(op.getName());
      if (foundProbe == null) {
        if (log.isDebugEnabled()) {
          log.debug("no probe mappings for {}", op.getName());
        }
        continue;
      }
      if (log.isDebugEnabled()) {
        log.debug("found probe mappings for {}", op.getName());
      }
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
    return new ProbeDescriptorLoader(path);
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
      log.debug("verifying BTrace class ...");
      cr.accept(v, ClassReader.SKIP_DEBUG);
      if (log.isDebugEnabled()) {
        String clzName = getClassName();
        log.debug("BTrace class {} verified", clzName);
        log.debug("preprocessing BTrace class {} ...", clzName);
      }
      prep.process(this);
      log.debug("... preprocessed");
      try {
        Class.forName("javax.xml.bind.JAXBException");
        mapOnProbes();
      } catch (ClassNotFoundException e) {
        log.debug("XML bindings are missing. @OnProbe support is disabled.");
      }
    } catch (VerifierException e) {
      verifierException = e;
    } finally {
      if (debug.isDumpClasses() && name != null) {
        debug.dumpClass(name, getBytecode(false));
      }
    }
  }

  private Set<BTraceMethodNode> fromIdSet(Set<String> ids) {
    Set<BTraceMethodNode> methods = new HashSet<>();
    for (String id : ids) {
      BTraceMethodNode mn = idmap.get(id);
      if (mn != null) {
        methods.add(mn);
      }
    }
    return methods;
  }

  private MethodNode copy(MethodNode n) {
    String[] exceptions = n.exceptions != null ? n.exceptions.toArray(new String[0]) : null;
    MethodNode mn = new MethodNode(Opcodes.ASM9, n.access, n.name, n.desc, n.signature, exceptions);
    n.accept(mn);
    mn.access = Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE;
    mn.desc = mn.desc.replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    mn.signature =
        mn.signature != null
            ? mn.signature.replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC)
            : null;
    mn.name = getActionPrefix() + mn.name;
    return mn;
  }

  private byte[] readFully(InputStream is) throws IOException {
    int bufSize = 512;
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
