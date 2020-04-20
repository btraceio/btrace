package org.openjdk.btrace.instr;

import org.objectweb.asm.ClassVisitor;

public final class InstrumentingClassVisitor extends ClassVisitor
    implements MethodInstrumentorHelper.Accessor {
  private String className;
  private volatile MethodInstrumentorHelper helper;

  public InstrumentingClassVisitor(int i) {
    super(i);
  }

  public InstrumentingClassVisitor(int i, ClassVisitor cv) {
    super(i, cv);
  }

  @Override
  public void visit(
      int version, int access, String name, String signature, String superName, String[] ifcs) {
    className = name;
    super.visit(version, access, name, signature, superName, ifcs);
  }

  @Override
  public InstrumentingMethodVisitor visitMethod(
      int access, String name, String desc, String sig, String[] exceptions) {
    InstrumentingMethodVisitor mv =
        new InstrumentingMethodVisitor(
            access, className, name, desc, super.visitMethod(access, name, desc, sig, exceptions));
    helper = mv;
    return mv;
  }

  @Override
  public MethodInstrumentorHelper methodHelper() {
    return helper;
  }
}
