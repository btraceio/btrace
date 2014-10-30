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
package com.sun.btrace.util.templates.impl;

import com.sun.btrace.MethodCounter;
import com.sun.btrace.annotations.Sampled;
import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.util.templates.BaseTemplateExpander;
import com.sun.btrace.util.templates.Template;
import com.sun.btrace.util.templates.TemplateExpanderVisitor;
import java.util.Map;

/**
 *
 * @author jbachorik
 */
public class MethodCounterExpander extends BaseTemplateExpander {
    public static final Template M_ENTRY = new Template("mc$entry", "()V");
    public static final Template M_LAST_DUR = new Template("mc$dur", "()J");
    public static final Template M_TEST = new Template("mc$test", "()V");
    public static final Template M_TEST_ELSE = new Template("mc$else", "()V");
    public static final Template M_EXIT = new Template("mc$exit", "()V");
    public static final Template M_RESET = new Template("mc$reset", "()V");
    public static final String TIMED_TAG = "timed";
    public static final String MEAN_TAG = "mean";
    public static final String SAMPLER_TAG = "sampler";
    public static final String METHODID_TAG = "methodid";

    private static final String METHOD_COUNTER_CLASS = "com/sun/btrace/MethodCounter";

    private boolean isTimed = false;
    private boolean isSampled = false;
    private Sampled.Sampler samplerKind = Sampled.Sampler.None;
    private int samplerMean = -1;
    private final int methodId;

    private int entryTsVar = Integer.MIN_VALUE;
    private int sHitVar = Integer.MIN_VALUE;
    private int durationVar = Integer.MIN_VALUE;
    private boolean durationComputed = false;

    private Label elseLabel = null;

    public MethodCounterExpander(int methodId) {
        super(M_ENTRY, M_LAST_DUR, M_TEST, M_TEST_ELSE, M_EXIT, M_RESET);
        this.methodId = methodId;
    }

    protected void recordTemplate(Template t) {
        if (M_ENTRY.equals(t)) {
            Map<String, String> m = t.getTagMap();
            isTimed = m.containsKey(TIMED_TAG);

            String sKind = m.get(SAMPLER_TAG);
            String sMean = m.get(MEAN_TAG);

            if (sKind != null) {
                if (samplerMean != 0) {
                    int mean = sMean != null ? Integer.parseInt(sMean) : Sampled.MEAN_DEFAULT;

                    if (samplerKind != Sampled.Sampler.Avg) {
                        samplerKind = Sampled.Sampler.valueOf(sKind);
                    }

                    if (samplerMean == -1) {
                        samplerMean = mean;
                    } else if (samplerMean > 0) {
                        samplerMean = Math.min(samplerMean, mean);
                    }

                    isSampled = (samplerKind != null && samplerMean > 0);
                }
            } else {
                // hitting a method in non-sampled mode means that no
                // sampling will be done even though other scripts request it
                samplerMean = 0;
                isSampled = false;
            }
        }
    }

    protected Result expandTemplate(TemplateExpanderVisitor v, Template t) {
        int localMethodId = methodId;
        String sMethodId = t.getTagMap().get(METHODID_TAG);
        if (sMethodId != null) {
            localMethodId = Integer.parseInt(sMethodId);
        }
        final int mid = localMethodId;

        if (M_ENTRY.equals(t)) {
            if (isSampled) {
                MethodCounter.registerCounter(mid, samplerMean);
                if (isTimed) {
                    v.expand(new Consumer<TemplateExpanderVisitor>() {
                        public void consume(TemplateExpanderVisitor e) {
                            if (durationVar == Integer.MIN_VALUE) {
                                e.visitLdcInsn(0L);
                                durationVar = e.storeNewLocal(Type.LONG_TYPE);
                            }
                            if (sHitVar == Integer.MIN_VALUE && entryTsVar == Integer.MIN_VALUE) {
                                e.visitLdcInsn(mid);
                                switch (samplerKind) {
                                    case Avg: {
                                        e.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            METHOD_COUNTER_CLASS,
                                            "hitTimed", "(I)J", false);
                                        break;
                                    }
                                    case Adaptive: {
                                        e.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            METHOD_COUNTER_CLASS,
                                            "hitTimedAdaptive", "(I)J", false);
                                        break;
                                    }
                                }

                                e.visitInsn(Opcodes.DUP2);
                                entryTsVar = e.storeNewLocal(Type.LONG_TYPE);
                                e.visitInsn(Opcodes.L2I);
                                sHitVar = e.storeNewLocal(Type.INT_TYPE);
                            }
                        }
                    });
                } else {
                    v.expand(new Consumer<TemplateExpanderVisitor>() {
                        public void consume(TemplateExpanderVisitor e) {
                            if (sHitVar == Integer.MIN_VALUE) {
                                e.visitLdcInsn(mid);
                                switch (samplerKind) {
                                    case Avg: {
                                        e.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            METHOD_COUNTER_CLASS,
                                            "hit", "(I)Z", false);
                                        break;
                                    }
                                    case Adaptive: {
                                        e.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            METHOD_COUNTER_CLASS,
                                            "hitAdaptive", "(I)Z", false);
                                        break;
                                    }
                                }
                                sHitVar = e.storeNewLocal(Type.INT_TYPE);
                            }
                        }
                    });
                }
            } else {
                if (isTimed) {
                    v.expand(new Consumer<TemplateExpanderVisitor>() {
                        public void consume(TemplateExpanderVisitor e) {
                            if (entryTsVar == Integer.MIN_VALUE) {
                                e.visitLdcInsn(0L);
                                durationVar = e.storeNewLocal(Type.LONG_TYPE);

                                e.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "java/lang/System",
                                    "nanoTime", "()J", false);
                                entryTsVar = e.storeNewLocal(Type.LONG_TYPE);
                            }
                        }
                    });
                }
            }
        } else if (M_TEST.equals(t)) {
            boolean collectTime = t.getTagMap().containsKey(TIMED_TAG);
            if (isSampled) {
                if (sHitVar != Integer.MIN_VALUE) {
                    elseLabel = new Label();
                    v.expand(new Consumer<TemplateExpanderVisitor>() {
                        public void consume(TemplateExpanderVisitor e) {
                            e.visitVarInsn(Opcodes.ILOAD, sHitVar);
                            e.visitJumpInsn(Opcodes.IFEQ, elseLabel);
                        }
                    });
                }
                if (isTimed && collectTime) {
                    if (!durationComputed) {
                        v.expand(new Consumer<TemplateExpanderVisitor>() {
                            public void consume(TemplateExpanderVisitor e) {
                                if (entryTsVar != Integer.MIN_VALUE) {
                                    e.visitLdcInsn(mid);
                                    e.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        METHOD_COUNTER_CLASS,
                                        "updateEndTs", "(I)J", false);
                                    e.visitVarInsn(Opcodes.LLOAD, entryTsVar);
                                    e.visitInsn(Opcodes.LSUB);
                                } else {
                                    e.visitLdcInsn(0L);
                                }
                                e.visitVarInsn(Opcodes.LSTORE, durationVar);
                                durationComputed = true;
                            }
                        });
                    }
                }
            } else {
                if (isTimed && collectTime) {
                    if (!durationComputed) {
                        v.expand(new Consumer<TemplateExpanderVisitor>() {
                            public void consume(TemplateExpanderVisitor e) {
                                if (entryTsVar != Integer.MIN_VALUE) {
                                   e.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "java/lang/System",
                                        "nanoTime", "()J", false);
                                    e.visitVarInsn(Opcodes.LLOAD, entryTsVar);
                                    e.visitInsn(Opcodes.LSUB);
                                } else {
                                    e.visitLdcInsn(0L);
                                }
                                e.visitVarInsn(Opcodes.LSTORE, durationVar);
                                durationComputed = true;
                            }
                        });
                    }
                }
            }
        } else if (M_TEST_ELSE.equals(t)) {
            if (elseLabel != null) {
                v.expand(new Consumer<TemplateExpanderVisitor>() {
                    public void consume(TemplateExpanderVisitor e) {
                        e.visitLabel(elseLabel);
                        elseLabel = null;
                    }
                });
            }
        } else if (M_LAST_DUR.equals(t)) {
            v.expand(new Consumer<TemplateExpanderVisitor>() {
                public void consume(TemplateExpanderVisitor e) {
                    if (!durationComputed) {
                        e.visitLdcInsn(0L);
                    } else {
                        e.visitVarInsn(Opcodes.LLOAD, durationVar);
                    }
                }
            });
        } else if (M_EXIT.equals(t)) {
            v.expand(new Consumer<TemplateExpanderVisitor>() {
                public void consume(TemplateExpanderVisitor e) {
                    if (samplerKind == Sampled.Sampler.Adaptive) {
                        Label l = new Label();
                        e.visitVarInsn(Opcodes.ILOAD, sHitVar);
                        e.visitJumpInsn(Opcodes.IFEQ, l);
                        e.visitLdcInsn(mid);
                        e.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            METHOD_COUNTER_CLASS,
                            "updateEndTs", "(I)J", false);
                        e.visitInsn(Opcodes.POP2);
                        e.visitLabel(l);
                    }
                }
            });
        } else if (M_RESET.equals(t)) {
            entryTsVar = Integer.MIN_VALUE;
            sHitVar = Integer.MIN_VALUE;
            durationComputed = false;
        } else {
            return Result.IGNORED;
        }

        return Result.EXPANDED;
    }

    public void resetState() {
        durationComputed = false;
    }
}
