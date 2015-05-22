/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Sampled;
import com.sun.btrace.annotations.Where;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import static com.sun.btrace.runtime.Constants.*;
import static com.sun.btrace.runtime.Verifier.BTRACE_DURATION_DESC;
import static com.sun.btrace.runtime.Verifier.BTRACE_RETURN_DESC;
import static com.sun.btrace.runtime.Verifier.BTRACE_SELF_DESC;
import static com.sun.btrace.runtime.Verifier.BTRACE_TARGETINSTANCE_DESC;
import static com.sun.btrace.runtime.Verifier.BTRACE_TARGETMETHOD_DESC;
import java.util.List;

/**
 * Parses the annotated BTrace source and builds up the structures for
 * the instrumentor.
 *
 * @author Jaroslav Bachorik
 */
final public class BTraceConfigurator extends MethodVisitor {
    private OnMethod om = null;
    private OnProbe op = null;

    private boolean sampled = false;

    protected boolean asBTrace = false;
    protected Location loc;

    private final String methodName;
    private final String methodDesc;
    private final String methodId;
    private final CycleDetector graph;
    private final List<OnMethod> onMethods;
    private final List<OnProbe> onProbes;

    public BTraceConfigurator(MethodVisitor mv, CycleDetector graph, List<OnMethod> onMethods, List<OnProbe> onProbes, String methodName, String desc) {
        super(Opcodes.ASM5, mv);
        this.methodName = methodName;
        this.methodDesc = desc;
        this.methodId = methodName + desc;
        this.graph = graph;
        this.onMethods = onMethods;
        this.onProbes = onProbes;
    }

    /**
     * The {@linkplain OnMethod} instance created for the processed method
     * @return {@linkplain OnMethod} or null if the method is not accordingly annotated
     */
    public OnMethod getOnMethod() {
        return om;
    }

    /**
     *
     * @return True if the method is annotated by @Sampled annotation
     */
    public boolean isSampled() {
        return sampled;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc,
                          boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(desc, visible);

        if (asBTrace) {
            graph.addStarting(new CycleDetector.Node(methodName + methodDesc));
        }

        System.err.println("*** checking annotation on " + methodName + " - @" + desc);
        if (desc.equals(ONMETHOD_DESC)) {
            om = new OnMethod();
            onMethods.add(om);
            om.setTargetName(methodName);
            om.setTargetDescriptor(methodDesc);
            return new AnnotationVisitor(Opcodes.ASM4, av) {
                @Override
                public void visit(String name, Object value) {
                    super.visit(name, value);

                    if (name.equals("clazz")) {
                        om.setClazz((String)value);
                    } else if (name.equals("method")) {
                        om.setMethod((String)value);
                    } else if (name.equals("type")) {
                        om.setType((String)value);
                    }
                }

                @Override
                public AnnotationVisitor visitAnnotation(String name,
                          String desc) {
                    AnnotationVisitor av1 = super.visitAnnotation(name, desc);
                    if (desc.equals(LOCATION_DESC)) {
                        loc = new Location();
                        return new AnnotationVisitor(Opcodes.ASM4, av1) {
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

                                if (name.equals("clazz")) {
                                    loc.setClazz((String)value);
                                } else if (name.equals("method")) {
                                    loc.setMethod((String)value);
                                } else if (name.equals("type")) {
                                    loc.setType((String)value);
                                } else if (name.equals("field")) {
                                    loc.setField((String)value);
                                } else if (name.equals("line")) {
                                    loc.setLine(((Number)value).intValue());
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
                    }
                    return av1;
                }
            };
        } else if (desc.equals(ONPROBE_DESC)) {
            op = new OnProbe();
            onProbes.add(op);
            op.setTargetName(methodName);
            op.setTargetDescriptor(methodDesc);
            return new AnnotationVisitor(Opcodes.ASM4, av) {
                @Override
                public void visit(String name, Object value) {
                    super.visit(name, value);

                    if (name.equals("namespace")) {
                        op.setNamespace((String)value);
                    } else if (name.equals("name")) {
                        op.setName((String)value);
                    }
                }
            };
        } else if (desc.equals(SAMPLER_DESC)) {
            if (om != null) {
                om.setSamplerKind(Sampled.Sampler.Adaptive);
                return new AnnotationVisitor(Opcodes.ASM4, av) {
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

    private AnnotationVisitor setSpecialParameters(final SpecialParameterHolder ph, final String desc, int parameter, AnnotationVisitor av) {
        // for OnProbe the 'loc' variable will be null; we will need to verfiy the placement later on
        if (desc.equals(BTRACE_SELF_DESC)) {
            ph.setSelfParameter(parameter);
        } else if (desc.equals(Verifier.BTRACE_PROBECLASSNAME_DESC)) {
            ph.setClassNameParameter(parameter);
        } else if (desc.equals(Verifier.BTRACE_PROBEMETHODNAME_DESC)) {
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
    public void visitMethodInsn(int opcode, String owner,
            String name, String desc, boolean itf) {
        if (opcode == Opcodes.INVOKESTATIC) {
            graph.addEdge(methodId, name + desc);
        }
        super.visitMethodInsn(opcode, owner, name, desc, itf);
    }
}
