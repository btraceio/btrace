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
package org.openjdk.btrace.instr;

import java.util.Comparator;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Sampled;
import org.openjdk.btrace.core.annotations.Where;

/**
 * @author Jaroslav Bachorik
 */
public class BTraceMethodNode extends MethodNode {
  public static final Comparator<MethodNode> COMPARATOR =
      (o1, o2) -> (o1.name + "#" + o1.desc).compareTo(o2.name + "#" + o2.desc);
  private final BTraceProbeNode cn;
  private final CallGraph graph;
  private final String methodId;
  private OnMethod om;
  private OnProbe op;
  private Location loc;
  private boolean sampled;
  private boolean isBTraceHandler;

  BTraceMethodNode(MethodNode from, BTraceProbeNode cn) {
    this(from, cn, false);
  }

  BTraceMethodNode(MethodNode from, BTraceProbeNode cn, boolean initBTraceHandler) {
    super(
        Opcodes.ASM9,
        from.access,
        from.name,
        from.desc,
        from.signature,
        from.exceptions.toArray(new String[0]));
    this.cn = cn;
    graph = cn.getGraph();
    methodId = CallGraph.methodId(name, desc);
    isBTraceHandler = initBTraceHandler;
  }

  @Override
  public void visitEnd() {
    if (om != null) {
      verifySpecialParameters(om);
      cn.addOnMethod(om);
    }
    if (op != null) {
      cn.addOnProbe(op);
    }
    if (isBTraceHandler) {
      graph.addStarting(new CallGraph.Node(methodId));
    }
    super.visitEnd();
  }

  @Override
  public AnnotationVisitor visitAnnotation(String type, boolean visible) {
    AnnotationVisitor av = super.visitAnnotation(type, visible);

    if (type.startsWith("Lorg/openjdk/btrace/core/annotations/")) {
      isBTraceHandler = true;
    }
    if (type.equals(Constants.ONMETHOD_DESC)) {
      om = new OnMethod(this);
      om.setTargetName(name);
      om.setTargetDescriptor(desc);
      return new AnnotationVisitor(Opcodes.ASM9, av) {
        @Override
        public void visit(String name, Object value) {
          super.visit(name, value);

          switch (name) {
            case "clazz":
              om.setClazz((String) value);
              break;
            case "method":
              om.setMethod((String) value);
              break;
            case "type":
              om.setType((String) value);
              break;
            case "exactTypeMatch":
              {
                om.setExactTypeMatch((boolean) value);
                break;
              }
            default:
              System.err.println("btrace WARNING: Unsupported @OnMethod attribute: " + name);
          }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
          AnnotationVisitor av1 = super.visitAnnotation(name, desc);
          if (desc.equals(Constants.LOCATION_DESC)) {
            loc = new Location();
            return new AnnotationVisitor(Opcodes.ASM9, av1) {
              @Override
              public void visitEnum(String name, String desc, String value) {
                super.visitEnum(name, desc, value);

                if (desc.equals(Constants.WHERE_DESC)) {
                  loc.setWhere(Enum.valueOf(Where.class, value));
                } else if (desc.equals(Constants.KIND_DESC)) {
                  loc.setValue(Enum.valueOf(Kind.class, value));
                }
              }

              @Override
              public void visit(String name, Object value) {
                super.visit(name, value);

                switch (name) {
                  case "clazz":
                    loc.setClazz((String) value);
                    break;
                  case "method":
                    loc.setMethod((String) value);
                    break;
                  case "type":
                    loc.setType((String) value);
                    break;
                  case "field":
                    loc.setField((String) value);
                    break;
                  case "line":
                    loc.setLine(((Number) value).intValue());
                    break;
                }
              }

              @Override
              public void visitEnd() {
                if (loc != null) {
                  om.setLocation(loc);
                }
                super.visitEnd();
              }
            };
          } else if (desc.equals(Constants.LEVEL_DESC)) {
            return new AnnotationVisitor(Opcodes.ASM9, av1) {
              @Override
              public void visit(String name, Object value) {
                super.visit(name, value);

                if ("value".equals(name)) {
                  om.setLevel(Level.fromString((String) value));
                }
              }
            };
          }
          return av1;
        }
      };
    } else if (type.equals(Constants.ONPROBE_DESC)) {
      op = new OnProbe(this);
      op.setTargetName(name);
      op.setTargetDescriptor(desc);
      return new AnnotationVisitor(Opcodes.ASM9, av) {
        @Override
        public void visit(String name, Object value) {
          super.visit(name, value);

          switch (name) {
            case "namespace":
              op.setNamespace((String) value);
              break;
            case "name":
              op.setName((String) value);
              break;
          }
        }
      };
    } else if (type.equals(Constants.SAMPLED_DESC)) {
      if (om != null) {
        om.setSamplerKind(Sampled.Sampler.Adaptive);
        return new AnnotationVisitor(Opcodes.ASM9, av) {
          private boolean meanSet = false;

          @Override
          public void visit(String name, Object value) {
            super.visit(name, value);

            if (name.equals("mean")) {
              om.setSamplerMean((Integer) value);
              meanSet = true;
            }
          }

          @Override
          public void visitEnum(String name, String desc, String value) {
            super.visitEnum(name, desc, value);

            if (name.equals("kind") && desc.equals(Constants.SAMPLER_DESC)) {
              om.setSamplerKind(Sampled.Sampler.valueOf(value));
            }
          }

          @Override
          public void visitEnd() {
            if (!meanSet) {
              if (om.getSamplerKind() == Sampled.Sampler.Adaptive) {
                om.setSamplerMean(500);
              } else if (om.getSamplerKind() == Sampled.Sampler.Const) {
                om.setSamplerMean(Sampled.MEAN_DEFAULT);
              }
            }
            if (om.getSamplerKind() == Sampled.Sampler.Adaptive) {
              // the time frame for adaptive sampling
              // should be at least 180ns -
              // (80ns timestamps + 15ns stub) * 2 safety margin
              if (om.getSamplerMean() < 180) {
                System.err.println(
                    "Setting the adaptive sampler time windows to the default of 180ns");
                om.setSamplerMean(180);
              }
            }
            super.visitEnd();
          }
        };
      }
      sampled = true;
    }
    return av;
  }

  @Override
  public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
    AnnotationVisitor av = super.visitParameterAnnotation(parameter, desc, visible);

    if (om != null) {
      av = setSpecialParameters(om, desc, parameter, av);
    } else if (op != null) {
      av = setSpecialParameters(op, desc, parameter, av);
    }

    return av;
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
    owner = cn.translateOwner(owner);
    if (opcode == Opcodes.INVOKESTATIC) {
      graph.addEdge(methodId, CallGraph.methodId(name, desc));
    }
    super.visitMethodInsn(opcode, owner, name, desc, itf);
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String desc) {
    super.visitFieldInsn(opcode, cn.translateOwner(owner), name, desc);
  }

  public boolean isBcpRequired() {
    return isBTraceHandler && om == null && op == null;
  }

  public boolean isBTraceHandler() {
    return isBTraceHandler;
  }

  public OnMethod getOnMethod() {
    return om;
  }

  public boolean isSampled() {
    return sampled;
  }

  public Set<BTraceMethodNode> getCallees() {
    return cn.callees(name, desc);
  }

  public Set<BTraceMethodNode> getCallers() {
    return cn.callers(name, desc);
  }

  boolean isFieldInjected(String name) {
    return cn.isFieldInjected(name);
  }

  OnProbe getOnProbe() {
    return op;
  }

  private AnnotationVisitor setSpecialParameters(
      SpecialParameterHolder ph, String desc, int parameter, AnnotationVisitor av) {
    // for OnProbe the 'loc' variable will be null; we will need to verfiy the placement later on
    if (desc.equals(Constants.SELF_DESC)) {
      ph.setSelfParameter(parameter);
    } else if (desc.equals(Constants.BTRACE_PROBECLASSNAME_DESC)) {
      ph.setClassNameParameter(parameter);
    } else if (desc.equals(Constants.BTRACE_PROBEMETHODNAME_DESC)) {
      ph.setMethodParameter(parameter);
      av =
          new AnnotationVisitor(Opcodes.ASM9, av) {
            @Override
            public void visit(String name, Object val) {
              if (name.equals("fqn")) {
                ph.setMethodFqn((Boolean) val);
              }
              super.visit(name, val);
            }
          };
    } else if (desc.equals(Constants.RETURN_DESC)) {
      ph.setReturnParameter(parameter);
    } else if (desc.equals(Constants.TARGETMETHOD_DESC)) {
      ph.setTargetMethodOrFieldParameter(parameter);

      av =
          new AnnotationVisitor(Opcodes.ASM9, av) {
            @Override
            public void visit(String name, Object val) {
              if (name.equals("fqn")) {
                ph.setTargetMethodOrFieldFqn((Boolean) val);
              }
              super.visit(name, val);
            }
          };
    } else if (desc.equals(Constants.TARGETINSTANCE_DESC)) {
      ph.setTargetInstanceParameter(parameter);
    } else if (desc.equals(Constants.DURATION_DESC)) {
      ph.setDurationParameter(parameter);
    }
    return av;
  }

  private void verifySpecialParameters(OnMethod om) {
    Location loc = om.getLocation();
    if (om.getReturnParameter() != -1) {
      if (!(loc.getValue() == Kind.RETURN
          || (loc.getValue() == Kind.CALL && loc.getWhere() == Where.AFTER)
          || (loc.getValue() == Kind.ARRAY_GET && loc.getWhere() == Where.AFTER)
          || (loc.getValue() == Kind.FIELD_GET && loc.getWhere() == Where.AFTER)
          || (loc.getValue() == Kind.NEW && loc.getWhere() == Where.AFTER)
          || (loc.getValue() == Kind.NEWARRAY && loc.getWhere() == Where.AFTER))) {
        Verifier.reportError(
            "return.desc.invalid",
            om.getTargetName() + om.getTargetDescriptor() + "(" + om.getReturnParameter() + ")");
      }
    }
    if (om.getTargetMethodOrFieldParameter() != -1) {
      if (!(loc.getValue() == Kind.CALL
          || loc.getValue() == Kind.FIELD_GET
          || loc.getValue() == Kind.FIELD_SET
          || loc.getValue() == Kind.ARRAY_GET
          || loc.getValue() == Kind.ARRAY_SET)) {
        Verifier.reportError(
            "target-method.desc.invalid",
            om.getTargetName()
                + om.getTargetDescriptor()
                + "("
                + om.getTargetMethodOrFieldParameter()
                + ")");
      }
    }
    if (om.getTargetInstanceParameter() != -1) {
      if (!(loc.getValue() == Kind.CALL
          || loc.getValue() == Kind.FIELD_GET
          || loc.getValue() == Kind.FIELD_SET
          || loc.getValue() == Kind.ARRAY_GET
          || loc.getValue() == Kind.ARRAY_SET
          || loc.getValue() == Kind.INSTANCEOF
          || loc.getValue() == Kind.CHECKCAST
          || loc.getValue() == Kind.ERROR
          || loc.getValue() == Kind.THROW
          || loc.getValue() == Kind.CATCH
          || loc.getValue() == Kind.SYNC_ENTRY
          || loc.getValue() == Kind.SYNC_EXIT)) {
        Verifier.reportError(
            "target-instance.desc.invalid",
            om.getTargetName()
                + om.getTargetDescriptor()
                + "("
                + om.getTargetInstanceParameter()
                + ")");
      }
    }
    if (om.getDurationParameter() != -1) {
      if (!((loc.getValue() == Kind.RETURN || loc.getValue() == Kind.ERROR)
          || (loc.getValue() == Kind.CALL && loc.getWhere() == Where.AFTER))) {
        Verifier.reportError(
            "duration.desc.invalid",
            om.getTargetName() + om.getTargetDescriptor() + "(" + om.getDurationParameter() + ")");
      }
    }
  }

  @Override
  public String toString() {
    return "BTraceMethodNode{name = " + name + ", desc=" + desc + '}';
  }
}
