/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.btrace.runtime;

import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Sampled;
import com.sun.btrace.annotations.Where;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import static com.sun.btrace.runtime.Constants.*;

import com.sun.btrace.org.objectweb.asm.tree.MethodNode;
import static com.sun.btrace.runtime.Verifier.BTRACE_DURATION_DESC;
import static com.sun.btrace.runtime.Verifier.BTRACE_RETURN_DESC;
import static com.sun.btrace.runtime.Verifier.BTRACE_SELF_DESC;
import static com.sun.btrace.runtime.Verifier.BTRACE_TARGETINSTANCE_DESC;
import static com.sun.btrace.runtime.Verifier.BTRACE_TARGETMETHOD_DESC;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceMethodNode extends MethodNode {
    public static final Comparator<MethodNode> COMPARATOR = new Comparator<MethodNode>() {
        @Override
        public int compare(MethodNode o1, MethodNode o2) {
            return (o1.name + "#" + o1.desc).compareTo(o2.name + "#" + o2.desc);
        }
    };

    private OnMethod om;
    private OnProbe op;
    private Location loc;
    private boolean sampled;
    private BTraceClassNode cn;
    private boolean isBTraceHandler;
    private final CycleDetector graph;
    private final String methodId;

    BTraceMethodNode(MethodNode from, BTraceClassNode cn) {
        super(Opcodes.ASM5, from.access, from.name, from.desc, from.signature, ((List<String>)from.exceptions).toArray(new String[0]));
        this.cn = cn;
        this.graph = cn.getGraph();
        this.methodId = CycleDetector.methodId(name, desc);
    }

    @Override
    public void visitEnd() {
        if (om != null) {
            cn.addOnMethod(om);
        }
        if (op != null) {
            cn.addOnProbe(op);
        }
        if (isBTraceHandler) {
            graph.addStarting(new CycleDetector.Node(CycleDetector.methodId(name, desc)));
        }
        super.visitEnd();
    }

    @Override
    public AnnotationVisitor visitAnnotation(String type, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(type, visible);

        if (type.startsWith("com/sun/btrace/annotations")) {
            isBTraceHandler = true;
        }
        if (type.equals(ONMETHOD_DESC)) {
            om = new OnMethod(this);
            om.setTargetName(name);
            om.setTargetDescriptor(desc);
            return new AnnotationVisitor(Opcodes.ASM5, av) {
                @Override
                public void visit(String name, Object value) {
                    super.visit(name, value);

                    switch (name) {
                        case "clazz":
                            om.setClazz((String)value);
                            break;
                        case "method":
                            om.setMethod((String)value);
                            break;
                        case "type":
                            om.setType((String)value);
                            break;
                        default:
                            System.err.println("btrace WARNING: Unsupported @OnMethod attribute: " + name);
                    }
                }

                @Override
                public AnnotationVisitor visitAnnotation(String name, String desc) {
                    AnnotationVisitor av1 = super.visitAnnotation(name, desc);
                    if (desc.equals(LOCATION_DESC)) {
                        loc = new Location();
                        return new AnnotationVisitor(Opcodes.ASM5, av1) {
                            @Override
                            public void visitEnum(String name, String desc, String value) {
                                super.visitEnum(name, desc, value);

                                if (desc.equals(WHERE_DESC)) {
                                    loc.setWhere(Enum.valueOf(Where.class, value));
                                } else if (desc.equals(KIND_DESC)) {
                                    loc.setValue(Enum.valueOf(Kind.class, value));
                                }
                            }

                            @Override
                            public void	visit(String name, Object value) {
                                super.visit(name, value);

                                switch (name) {
                                    case "clazz":
                                        loc.setClazz((String)value);
                                        break;
                                    case "method":
                                        loc.setMethod((String)value);
                                        break;
                                    case "type":
                                        loc.setType((String)value);
                                        break;
                                    case "field":
                                        loc.setField((String)value);
                                        break;
                                    case "line":
                                        loc.setLine(((Number)value).intValue());
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
                    } else if (desc.equals(LEVEL_DESC)) {
                        return new AnnotationVisitor(Opcodes.ASM5, av1) {
                            @Override
                            public void	visit(String name, Object value) {
                                super.visit(name, value);

                                if ("value".equals(name)) {
                                    om.setLevel(Level.fromString((String)value));
                                }
                            }
                        };
                    }
                    return av1;
                }
            };
        } else if (type.equals(ONPROBE_DESC)) {
            op = new OnProbe(this);
            op.setTargetName(name);
            op.setTargetDescriptor(desc);
            return new AnnotationVisitor(Opcodes.ASM5, av) {
                @Override
                public void visit(String name, Object value) {
                    super.visit(name, value);

                    switch (name) {
                        case "namespace":
                            op.setNamespace((String)value);
                            break;
                        case "name":
                            op.setName((String)value);
                            break;
                    }
                }
            };
        } else if (type.equals(SAMPLER_DESC)) {
            if (om != null) {
                om.setSamplerKind(Sampled.Sampler.Adaptive);
                return new AnnotationVisitor(Opcodes.ASM5, av) {
                    private boolean meanSet = false;
                    @Override
                    public void visit(String name, Object value) {
                        super.visit(name, value);

                        if (name.equals("mean")) {
                            om.setSamplerMean((Integer)value);
                            meanSet = true;
                        }
                    }

                    @Override
                    public void visitEnum(String name, String desc, String value) {
                        super.visitEnum(name, desc, value);

                        if (name.equals("kind") && desc.equals(Type.getDescriptor(Sampled.Sampler.class))) {
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
                                System.err.println("Setting the adaptive sampler time windows to the default of 180ns");
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
    public AnnotationVisitor visitParameterAnnotation(int parameter, final String desc, boolean visible) {
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
        if (opcode == Opcodes.INVOKESTATIC) {
            graph.addEdge(methodId, CycleDetector.methodId(name, desc));
        }
        super.visitMethodInsn(opcode, owner, name, desc, itf);
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

    private AnnotationVisitor setSpecialParameters(final SpecialParameterHolder ph, final String desc, int parameter, AnnotationVisitor av) {
        // for OnProbe the 'loc' variable will be null; we will need to verfiy the placement later on
        if (desc.equals(BTRACE_SELF_DESC)) {
            ph.setSelfParameter(parameter);
        } else if (desc.equals(BTRACE_PROBECLASSNAME_DESC)) {
            ph.setClassNameParameter(parameter);
        } else if (desc.equals(BTRACE_PROBEMETHODNAME_DESC)) {
            ph.setMethodParameter(parameter);
            av = new AnnotationVisitor(Opcodes.ASM5, av) {
                @Override
                public void visit(String name, Object val) {
                    if (name.equals("fqn")) {
                        ph.setMethodFqn((Boolean)val);
                    }
                    super.visit(name, val);
                }

            };
        } else if (desc.equals(BTRACE_RETURN_DESC)) {
            ph.setReturnParameter(parameter);
        } else if (desc.equals(BTRACE_TARGETMETHOD_DESC)) {
            ph.setTargetMethodOrFieldParameter(parameter);

            av = new AnnotationVisitor(Opcodes.ASM5, av) {
                @Override
                public void visit(String name, Object val) {
                    if (name.equals("fqn")) {
                        ph.setTargetMethodOrFieldFqn((Boolean)val);
                    }
                    super.visit(name, val);
                }

            };
        } else if (desc.equals(BTRACE_TARGETINSTANCE_DESC)) {
            ph.setTargetInstanceParameter(parameter);
        } else if (desc.equals(BTRACE_DURATION_DESC)) {
            ph.setDurationParameter(parameter);
        }
        return av;
    }

    @Override
    public String toString() {
        return "BTraceMethodNode{name = " + name + ", desc=" + desc + '}';
    }
}
