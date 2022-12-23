/*
 * Copyright (c) 2017, 2018, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Copyright owner designates
 * this particular file as subject to the "Classpath" exception as provided
 * by the owner in the LICENSE file that accompanied this code.
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
 */
package org.openjdk.btrace.instr;

import static org.objectweb.asm.Opcodes.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.VerifierException;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Sampled;
import org.openjdk.btrace.core.annotations.Where;
import org.openjdk.btrace.core.comm.RetransformClassNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BTraceProbePersisted implements BTraceProbe {
  private static final Logger log = LoggerFactory.getLogger(BTraceProbePersisted.class);

  static final int MAGIC = 0xbacecaca;

  private static final int VERSION = 2;

  final BTraceProbeSupport delegate;
  private final BTraceProbeFactory factory;
  private final DebugSupport debug;
  private final AtomicBoolean triedVerify = new AtomicBoolean(false);
  private final Map<String, Set<String>> calleeMap = new HashMap<>();
  private volatile BTraceRuntime.Impl rt = null;
  private BTraceTransformer transformer;
  private byte[] fullData = null;
  private byte[] dataHolder = null;
  private boolean preverified;

  BTraceProbePersisted(BTraceProbeFactory f) {
    this(f, null);
  }

  private BTraceProbePersisted(BTraceProbeFactory f, BTraceProbeSupport delegate) {
    this.debug = new DebugSupport(f.getSettings());
    this.delegate = delegate != null ? delegate : new BTraceProbeSupport();
    factory = f;
    preverified = false;
  }

  private BTraceProbePersisted(BTraceProbeNode bpn) {
    this(bpn.factory, bpn.delegate);
    fullData = bpn.getFullBytecode();
    dataHolder = bpn.getDataHolderBytecode();
    loadCalleeMap(bpn, calleeMap);
    preverified = true;
  }

  public static BTraceProbePersisted from(BTraceProbe bp) {
    return bp instanceof BTraceProbePersisted
        ? (BTraceProbePersisted) bp
        : new BTraceProbePersisted((BTraceProbeNode) bp);
  }

  private static void loadCalleeMap(BTraceProbeNode bpn, Map<String, Set<String>> cMap) {
    Set<Handler> roots = new HashSet<>();
    for (OnMethod om : bpn.onmethods()) {
      roots.add(new Handler(om.getTargetName(), om.getTargetDescriptor()));
    }
    for (OnProbe op : bpn.onprobes()) {
      roots.add(new Handler(op.getTargetName(), op.getTargetDescriptor()));
    }
    for (Handler h : roots) {
      String rootKey = CallGraph.methodId(h.name, h.desc);
      Set<String> cs = cMap.computeIfAbsent(rootKey, k -> new HashSet<>());
      for (BTraceMethodNode bmn : bpn.callees(h.name, h.desc)) {
        cs.add(CallGraph.methodId(bmn.name, bmn.desc));
      }
    }
  }

  private static String getClazz(OnMethod om) {
    String clzName = om.getClazz();

    if (om.isSubtypeMatcher()) {
      return "+" + om.getClazz();
    } else {
      if (om.isClassRegexMatcher()) {
        clzName = "/" + clzName + "/";
      }
      if (om.isClassAnnotationMatcher()) {
        clzName = "@" + clzName;
      }
    }

    return clzName;
  }

  private static String getMethod(OnMethod om) {
    String mName = om.getMethod();

    if (om.isMethodRegexMatcher()) {
      mName = "/" + mName + "/";
    }

    if (om.isMethodAnnotationMatcher()) {
      mName = "@" + mName;
    }

    return mName;
  }

  public void read(DataInputStream dis) throws IOException {
    int version = dis.readInt();
    switch (version) {
      case 1:
        {
          read_1(dis);
          break;
        }
      case 2:
        {
          read_2(dis);
          break;
        }
      default:
        {
          throw new IOException("Unsupported version for persisted probe: " + version);
        }
    }
  }

  /**
   * Read in the structure for version 1
   *
   * @param dis data input stream
   * @throws IOException
   */
  private void read_1(DataInputStream dis) throws IOException {
    delegate.setClassName(dis.readUTF());
    readServices(dis);
    readOnMethods(dis);
    readOnProbes(dis);
    readCallees(dis);
    readDataHolderClass(dis);
    readFullData(dis);
    upgradeBytecode();
  }

  /**
   * Read in the structure for version 2
   *
   * @param dis data input stream
   * @throws IOException
   */
  private void read_2(DataInputStream dis) throws IOException {
    delegate.setClassName(dis.readUTF());
    readServices(dis);
    readOnMethods(dis);
    readOnProbes(dis);
    readCallees(dis);
    readDataHolderClass(dis);
    readFullData(dis);
  }

  public void write(DataOutputStream dos) {
    try {
      dos.writeInt(MAGIC);
      dos.writeInt(VERSION);
      dos.writeUTF(getClassName(true));
      writeServices(dos);
      writeOnMethods(dos);
      writeOnProbes(dos);
      writeCallees(dos);
      writeDataHolderClass(dos);
      writeFullData(dos);
    } catch (IOException e) {
      log.debug("Failed to write probe {}", getClassName(), e);
    }
  }

  private void readServices(DataInputStream dis) throws IOException {
    int num = dis.readInt();
    for (int i = 0; i < num; i++) {
      delegate.addServiceField(dis.readUTF(), dis.readUTF());
    }
  }

  private void readOnMethods(DataInputStream dis) throws IOException {
    int num = dis.readInt();
    for (int i = 0; i < num; i++) {
      OnMethod om = new OnMethod();
      om.setClazz(dis.readUTF());
      om.setMethod(dis.readUTF());
      om.setExactTypeMatch(dis.readBoolean());
      om.setTargetDescriptor(dis.readUTF());
      om.setTargetName(dis.readUTF());
      om.setType(dis.readUTF());
      om.setClassNameParameter(dis.readInt());
      om.setDurationParameter(dis.readInt());
      om.setMethodParameter(dis.readInt());
      om.setReturnParameter(dis.readInt());
      om.setSelfParameter(dis.readInt());
      om.setTargetInstanceParameter(dis.readInt());
      om.setTargetMethodOrFieldParameter(dis.readInt());
      om.setMethodFqn(dis.readBoolean());
      om.setTargetMethodOrFieldFqn(dis.readBoolean());
      om.setSamplerKind(Sampled.Sampler.valueOf(dis.readUTF()));
      om.setSamplerMean(dis.readInt());
      om.setLevel(dis.readBoolean() ? Level.fromString(dis.readUTF()) : null);
      Location loc = new Location();
      loc.setValue(Kind.valueOf(dis.readUTF()));
      loc.setWhere(Where.valueOf(dis.readUTF()));
      loc.setClazz(dis.readBoolean() ? dis.readUTF() : null);
      loc.setField(dis.readBoolean() ? dis.readUTF() : null);
      loc.setMethod(dis.readBoolean() ? dis.readUTF() : null);
      loc.setType(dis.readBoolean() ? dis.readUTF() : null);
      loc.setLine(dis.readInt());
      om.setLocation(loc);
      delegate.addOnMethod(om);
    }
  }

  private void readOnProbes(DataInputStream dis) throws IOException {
    int num = dis.readInt();
    for (int i = 0; i < num; i++) {
      OnProbe op = new OnProbe();
      op.setNamespace(dis.readUTF());
      op.setName(dis.readUTF());
      op.setTargetDescriptor(dis.readUTF());
      op.setTargetName(dis.readUTF());
      op.setClassNameParameter(dis.readInt());
      op.setDurationParameter(dis.readInt());
      op.setMethodParameter(dis.readInt());
      op.setReturnParameter(dis.readInt());
      op.setSelfParameter(dis.readInt());
      op.setTargetInstanceParameter(dis.readInt());
      op.setTargetMethodOrFieldParameter(dis.readInt());
      op.setMethodFqn(dis.readBoolean());
      op.setTargetMethodOrFieldFqn(dis.readBoolean());
      delegate.addOnProbe(op);
    }
  }

  private void readFullData(DataInputStream dis) throws IOException {
    int fullDataLen = dis.readInt();
    fullData = new byte[fullDataLen];
    dis.readFully(fullData);
  }

  private void readDataHolderClass(DataInputStream dis) throws IOException {
    int holderLen = dis.readInt();
    dataHolder = new byte[holderLen];
    dis.readFully(dataHolder);
    if (dataHolder.length > 0 && isClassRenamed()) {
      dataHolder = ProbeRenameVisitor.rename(getClassName(), dataHolder);
    }
  }

  private void readCallees(DataInputStream dis) throws IOException {
    int cnt = dis.readInt();
    for (int i = 0; i < cnt; i++) {
      String from = dis.readUTF();
      Set<String> calleeSet = calleeMap.computeIfAbsent(from, k -> new HashSet<>());
      int callees = dis.readInt();
      for (int j = 0; j < callees; j++) {
        String to = dis.readUTF();
        calleeSet.add(to);
      }
    }
  }

  private void writeServices(DataOutputStream dos) throws IOException {
    Map<String, String> svcFields = delegate.serviceFields();
    dos.writeInt(svcFields.size());
    for (Map.Entry<String, String> e : svcFields.entrySet()) {
      dos.writeUTF(e.getKey());
      dos.writeUTF(e.getValue());
    }
  }

  private void writeOnMethods(DataOutputStream dos) throws IOException {
    Collection<OnMethod> onMethods = delegate.getOnMethods();
    int cnt = onMethods.size();
    dos.writeInt(cnt);
    for (OnMethod om : onMethods) {
      dos.writeUTF(getClazz(om));
      dos.writeUTF(getMethod(om));
      dos.writeBoolean(om.isExactTypeMatch());
      dos.writeUTF(om.getTargetDescriptor());
      dos.writeUTF(om.getTargetName());
      dos.writeUTF(om.getType());
      dos.writeInt(om.getClassNameParameter());
      dos.writeInt(om.getDurationParameter());
      dos.writeInt(om.getMethodParameter());
      dos.writeInt(om.getReturnParameter());
      dos.writeInt(om.getSelfParameter());
      dos.writeInt(om.getTargetInstanceParameter());
      dos.writeInt(om.getTargetMethodOrFieldParameter());
      dos.writeBoolean(om.isMethodFqn());
      dos.writeBoolean(om.isTargetMethodOrFieldFqn());
      dos.writeUTF(om.getSamplerKind().name());
      dos.writeInt(om.getSamplerMean());
      dos.writeBoolean(om.getLevel() != null);
      if (om.getLevel() != null) {
        dos.writeUTF(om.getLevel().getValue().toString());
      }
      Location loc = om.getLocation();
      dos.writeUTF(loc.getValue().name());
      dos.writeUTF(loc.getWhere().name());
      dos.writeBoolean(loc.getClazz() != null);
      if (loc.getClazz() != null) {
        dos.writeUTF(loc.getClazz());
      }
      dos.writeBoolean(loc.getField() != null);
      if (loc.getField() != null) {
        dos.writeUTF(loc.getField());
      }
      dos.writeBoolean(loc.getMethod() != null);
      if (loc.getMethod() != null) {
        dos.writeUTF(loc.getMethod());
      }
      dos.writeBoolean(loc.getType() != null);
      if (loc.getType() != null) {
        dos.writeUTF(loc.getType());
      }
      dos.writeInt(loc.getLine());
    }
  }

  private void writeOnProbes(DataOutputStream dos) throws IOException {
    Collection<OnProbe> onProbes = delegate.getOnProbes();
    int cnt = onProbes.size();
    dos.writeInt(cnt);
    for (OnProbe op : onProbes) {
      dos.writeUTF(op.getNamespace());
      dos.writeUTF(op.getName());
      dos.writeUTF(op.getTargetDescriptor());
      dos.writeUTF(op.getTargetName());
      dos.writeInt(op.getClassNameParameter());
      dos.writeInt(op.getDurationParameter());
      dos.writeInt(op.getMethodParameter());
      dos.writeInt(op.getReturnParameter());
      dos.writeInt(op.getSelfParameter());
      dos.writeInt(op.getTargetInstanceParameter());
      dos.writeInt(op.getTargetMethodOrFieldParameter());
      dos.writeBoolean(op.isMethodFqn());
      dos.writeBoolean(op.isTargetMethodOrFieldFqn());
    }
  }

  private void writeFullData(DataOutputStream dos) throws IOException {
    dos.writeInt(fullData.length);
    dos.write(fullData);
  }

  private void writeDataHolderClass(DataOutputStream dos) throws IOException {
    dos.writeInt(dataHolder.length);
    dos.write(dataHolder);
  }

  private void writeCallees(DataOutputStream dos) throws IOException {
    int cnt = 0;
    for (Set<String> callees : calleeMap.values()) {
      if (!callees.isEmpty()) {
        cnt++;
      }
    }
    dos.writeInt(cnt);
    for (Map.Entry<String, Set<String>> e : calleeMap.entrySet()) {
      if (!e.getValue().isEmpty()) {
        dos.writeUTF(e.getKey());
        dos.writeInt(e.getValue().size());
        for (String c : e.getValue()) {
          dos.writeUTF(c);
        }
      }
    }
  }

  @Override
  public Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
    return delegate.getApplicableHandlers(cr);
  }

  @Override
  public byte[] getFullBytecode() {
    return fullData;
  }

  @Override
  public byte[] getDataHolderBytecode() {
    return dataHolder;
  }

  @Override
  public String getClassName() {
    return delegate.getClassName(false);
  }

  @Override
  public String getClassName(boolean internal) {
    return delegate.getClassName(internal);
  }

  @Override
  public boolean isClassRenamed() {
    return delegate.isClassRenamed();
  }

  @Override
  public boolean isTransforming() {
    return delegate.isTransforming();
  }

  @Override
  public boolean isVerified() {
    if (factory.getSettings().isTrusted()) {
      return true;
    }
    if (triedVerify.compareAndSet(false, true)) {
      try {
        verifyBytecode();
        return true;
      } catch (VerifierException e) {
        log.debug("Class '{}' verification failed", getClassName(), e);
      }
    }
    return false;
  }

  @Override
  public void notifyTransform(String className) {
    if (rt != null && factory.getSettings().isTrackRetransforms()) {
      rt.send(new RetransformClassNotification(className.replace('/', '.')));
    }
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
  public Class<?> register(BTraceRuntime.Impl rt, BTraceTransformer t) {
    byte[] code = dataHolder;
    if (debug.isDumpClasses()) {
      debug.dumpClass(delegate.getClassName(true) + "_bcp", code);
    }
    Class<?> clz = delegate.defineClass(rt, code);
    t.register(this);
    transformer = t;
    this.rt = rt;
    return clz;
  }

  @Override
  public void unregister() {
    if (transformer != null && isTransforming()) {
      if (log.isDebugEnabled()) {
        log.debug("onExit: removing transformer for {}", getClassName());
      }
      transformer.unregister(this);
    }
    rt = null;
  }

  @Override
  public boolean willInstrument(Class<?> clz) {
    return delegate.willInstrument(clz);
  }

  @Override
  public void checkVerified() {
    if (!preverified) {
      isVerified();
    }
  }

  @Override
  public void copyHandlers(ClassVisitor copyingVisitor) {
    ClassReader cr = new ClassReader(fullData);
    Set<String> copiedMethods = new HashSet<>();
    for (OnMethod om : onmethods()) {
      if (om.isCalled()) {
        String mid = CallGraph.methodId(om.getTargetName(), om.getTargetDescriptor());
        copiedMethods.add(mid);
        Set<String> callees = calleeMap.get(mid);
        if (callees != null) {
          copiedMethods.addAll(calleeMap.get(mid));
        }
      }
    }
    cr.accept(
        new ClassVisitor(ASM9) {
          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] interfaces) {
            copyingVisitor.visit(version, access, name, signature, superName, interfaces);
          }

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String signature, String[] exceptions) {
            String mid = CallGraph.methodId(name, desc);
            if (copiedMethods.contains(mid)) {
              return copyingVisitor.visitMethod(
                  ACC_PRIVATE | ACC_STATIC,
                  InstrumentUtils.getActionPrefix(getClassName(true)) + name,
                  desc.replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC),
                  signature != null
                      ? signature.replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC)
                      : null,
                  exceptions);
            }
            return super.visitMethod(access, name, desc, signature, exceptions);
          }
        },
        0);
  }

  @Override
  public void applyArgs(ArgsMap argsMap) {
    delegate.applyArgs(argsMap);
  }

  @Override
  public BTraceRuntime.Impl getRuntime() {
    return rt;
  }

  private void upgradeBytecode() {
    fullData = ProbeUpgradeVisitor_1_2.upgrade(new ClassReader(fullData));
    dataHolder = ProbeUpgradeVisitor_1_2.upgrade(new ClassReader(dataHolder));
  }

  private void verifyBytecode() throws VerifierException {
    ClassReader cr = new ClassReader(fullData);
    cr.accept(
        new ClassVisitor(ASM9) {
          private String className;

          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] interfaces) {
            if ((access & ACC_INTERFACE) != 0 || (access & ACC_ENUM) != 0) {
              Verifier.reportError("btrace.program.should.be.class");
            }
            if ((access & ACC_PUBLIC) == 0) {
              Verifier.reportError("class.should.be.public", name);
            }

            if (!superName.equals(Constants.OBJECT_INTERNAL)) {
              Verifier.reportError("object.superclass.required", superName);
            }
            if (interfaces != null && interfaces.length > 0) {
              Verifier.reportError("no.interface.implementation");
            }
            className = name;
            super.visit(version, access, name, signature, superName, interfaces);
          }

          @Override
          public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (className.equals(outerName)) {
              Verifier.reportError("no.nested.class");
            }
          }

          @Override
          public void visitOuterClass(String s, String s1, String s2) {
            Verifier.reportError("no.outer.class");
          }

          @Override
          public FieldVisitor visitField(
              int access, String name, String desc, String sig, Object dflt) {
            if ((access & ACC_STATIC) == 0) {
              Verifier.reportError("agent.no.instance.variables", name);
            }
            return super.visitField(access, name, desc, sig, dflt);
          }

          @Override
          public MethodVisitor visitMethod(
              int access, String methodName, String desc, String sig, String[] exceptions) {
            if ((access & ACC_SYNCHRONIZED) != 0) {
              Verifier.reportError(
                  "no.synchronized.methods",
                  TypeUtils.descriptorToSimplified(desc, className, methodName));
            }

            if (!methodName.equals(Constants.CONSTRUCTOR)) {
              if ((access & ACC_STATIC) == 0) {
                Verifier.reportError(
                    "no.instance.method",
                    TypeUtils.descriptorToSimplified(desc, className, methodName));
              }
            }

            if (methodName.equals(Constants.CLASS_INITIALIZER)) {
              return super.visitMethod(access, methodName, desc, sig, exceptions);
            }

            return new MethodVisitor(
                ASM9, super.visitMethod(access, methodName, desc, sig, exceptions)) {
              private final Map<Label, Label> labels = new HashMap<>();

              @Override
              public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                if (opcode == PUTFIELD) {
                  Verifier.reportError("no.assignment");
                }

                if (opcode == PUTSTATIC) {
                  if (!owner.equals(className)) {
                    Verifier.reportError("no.assignment");
                  }
                }
                super.visitFieldInsn(opcode, owner, name, desc);
              }

              @Override
              public void visitInsn(int opcode) {
                switch (opcode) {
                  case IASTORE:
                  case LASTORE:
                  case FASTORE:
                  case DASTORE:
                  case AASTORE:
                  case BASTORE:
                  case CASTORE:
                  case SASTORE:
                    Verifier.reportError("no.assignment");
                    break;
                  case ATHROW:
                    Verifier.reportError("no.throw");
                    break;
                  case MONITORENTER:
                  case MONITOREXIT:
                    Verifier.reportError("no.synchronized.blocks");
                    break;
                }
                super.visitInsn(opcode);
              }

              @Override
              public void visitIntInsn(int opcode, int operand) {
                if (opcode == NEWARRAY) {
                  Verifier.reportError("no.array.creation");
                }
                super.visitIntInsn(opcode, operand);
              }

              @Override
              public void visitJumpInsn(int opcode, Label label) {
                if (labels.get(label) != null) {
                  Verifier.reportError("no.loops");
                }
                super.visitJumpInsn(opcode, label);
              }

              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String desc, boolean itfc) {
                switch (opcode) {
                  case INVOKEVIRTUAL:
                    if (MethodVerifier.isPrimitiveWrapper(owner)
                        && MethodVerifier.isUnboxMethod(name)) {
                      // allow primitive type unbox methods.
                      // These calls are generated by javac for auto-unboxing `
                      // and can't be caught by source AST analyzer as well.
                    } else if (owner.equals(Constants.STRING_BUILDER_INTERNAL)) {
                      // allow string concatenation via StringBuilder
                    } else if (owner.equals(Constants.THREAD_LOCAL_INTERNAL)) {
                      // allow ThreadLocal methods
                    } else if (owner.equals(Constants.BTRACERTACCESS_INTERNAL)) {
                      // allow BTraceRuntimeAccess methods
                    } else if (owner.equals(Constants.BTRACERTBASE_INTERNAL)) {
                      // allow BTraceRuntimeImplBase methods
                    } else {
                      if (!delegate.isServiceType(owner)) {
                        Verifier.reportError("no.method.calls", owner + "." + name + desc);
                      }
                    }
                    break;
                  case INVOKEINTERFACE:
                    Verifier.reportError("no.method.calls", owner + "." + name + desc);
                    break;
                  case INVOKESPECIAL:
                    if (owner.equals(Constants.OBJECT_INTERNAL)
                        && name.equals(Constants.CONSTRUCTOR)) {
                      // allow object initializer
                    } else if (owner.equals(Constants.STRING_BUILDER_INTERNAL)) {
                      // allow string concatenation via StringBuilder
                    } else if (owner.equals(Constants.THREAD_LOCAL_INTERNAL)) {
                      // allow ThreadLocal methods
                    } else if (delegate.isServiceType(owner)) {
                      // allow services invocations
                    } else {
                      Verifier.reportError("no.method.calls", owner + "." + name + desc);
                    }
                    break;
                  case INVOKESTATIC:
                    if (!owner.startsWith("org/openjdk/btrace/") && !owner.equals(className)) {
                      if ("valueOf".equals(name) && MethodVerifier.isPrimitiveWrapper(owner)) {
                        // allow primitive wrapper boxing methods.
                        // These calls are generated by javac for autoboxing
                        // and can't be caught sourc AST analyzer as well.
                      } else {
                        Verifier.reportError("no.method.calls", owner + "." + name + desc);
                      }
                    }
                    break;
                }
                super.visitMethodInsn(opcode, owner, name, desc, itfc);
              }

              @Override
              public void visitMultiANewArrayInsn(String desc, int dims) {
                Verifier.reportError("no.array.creation");
              }

              @Override
              public void visitTypeInsn(int opcode, String desc) {
                if (opcode == ANEWARRAY) {
                  Verifier.reportError("no.array.creation", desc);
                }
                if (opcode == NEW) {
                  // allow StringBuilder creation for string concatenation
                  if (!desc.equals(Constants.STRING_BUILDER_INTERNAL)
                      && !delegate.isServiceType(desc)) {
                    Verifier.reportError("no.new.object", desc);
                  }
                }
                super.visitTypeInsn(opcode, desc);
              }

              @Override
              public void visitVarInsn(int opcode, int var) {
                if (opcode == RET) {
                  Verifier.reportError("no.try");
                }
                super.visitVarInsn(opcode, var);
              }
            };
          }
        },
        ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
  }

  private static final class Handler {
    private final String name;
    private final String desc;

    public Handler(String name, String desc) {
      this.name = name;
      this.desc = desc;
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 29 * hash + Objects.hashCode(name);
      hash = 29 * hash + Objects.hashCode(desc);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Handler other = (Handler) obj;
      if (!Objects.equals(name, other.name)) {
        return false;
      }
      return Objects.equals(desc, other.desc);
    }
  }
}
