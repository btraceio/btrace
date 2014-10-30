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

import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.util.MethodID;
import com.sun.btrace.util.SamplingSupport;
import com.sun.btrace.util.templates.Template;
import com.sun.btrace.util.templates.TemplateExpander;
import com.sun.btrace.util.templates.TemplateExpanderVisitor;

/**
 * A base class for timestamp handling template expanders
 * @author Jaroslav Bachorik
 */
abstract public class TimeStampExpander implements TemplateExpander {
    public static final String SAMPLING_INTERVAl = "sint";

    private final String className;
    private final String desc;
    private int durationIndex = -1;
    private boolean durationSet = false;
    private int endTimeIndex = -1;
    private boolean endTimeSet = false;
    private final String methodName;
    private int sampleHitVarIndex = -1;
    private int startTimeIndex = -1;
    private boolean startTimeSet = false;

    private final Template startTimeTemplate;
    private final Template endTimeTemplate;
    private final Template durationTemplate;

    private Template lastTemplate = null;

    private int samplingInterval = Integer.MAX_VALUE;

    public TimeStampExpander(String className, String desc, String methodName,
                             Template startTimeTemplate,
                             Template endTimeTemplate,
                             Template durationTemplate) {
        this.className = className;
        this.desc = desc;
        this.methodName = methodName;

        this.startTimeTemplate = startTimeTemplate;
        this.endTimeTemplate = endTimeTemplate;
        this.durationTemplate = durationTemplate;
    }

    public Result expand(TemplateExpanderVisitor v, Template t) {
        if (lastTemplate == null) {
            if (!(durationTemplate.equals(t) || endTimeTemplate.equals(t) || startTimeTemplate.equals(t))) {
                return Result.IGNORED;
            }
        }
        try {
            if ((lastTemplate == null && t != null) || !lastTemplate.equals(t)) {
                if (durationTemplate.equals(lastTemplate)) {
                    v.expand(new Consumer<TemplateExpanderVisitor>() {
                        public void consume(TemplateExpanderVisitor visitor) {
                            expandCallDuration(
                                visitor,
                                true
                            );
                        }
                    });

                    resetStartTime();
                    resetEndTime();
                    resetDuration();
                    samplingInterval = Integer.MAX_VALUE;

                    return t != null ? Result.EXPANDED : Result.IGNORED;
                } else if (startTimeTemplate.equals(lastTemplate)) {
                    int sinter = 1;
                    for(String tag : lastTemplate.getTags()) {
                        if (tag.startsWith(SAMPLING_INTERVAl + "=")) {
                            sinter = Integer.valueOf(tag.substring(SAMPLING_INTERVAl.length() + 1));
                            break;
                        }
                    }
                    samplingInterval = Math.min(samplingInterval, sinter);
                    v.expand(new Consumer<TemplateExpanderVisitor>() {
                        public void consume(TemplateExpanderVisitor visitor) {
                            expandCallStartTime(visitor);
                        }
                    });
                    return t != null ? Result.EXPANDED : Result.IGNORED;
                } else if (endTimeTemplate.equals(lastTemplate)) {
                    v.expand(new Consumer<TemplateExpanderVisitor>() {
                        public void consume(TemplateExpanderVisitor visitor) {
                            expandCallEndTime(visitor);
                        }
                    });
                    return t != null ? Result.EXPANDED : Result.IGNORED;
                } else {
                    return Result.CLAIMED;
                }
            } else {
                return Result.CLAIMED;
            }
        } finally {
            lastTemplate = t;
        }
    }

    protected void expandCallDuration(TemplateExpanderVisitor v, boolean trans) {
        if (startTimeSet && endTimeSet) {
            if (!durationSet || trans) {
                if (getSamplingInterval() > 1) {
                    Label elseLabel = new Label();
                    Label endLabel = new Label();
                    v.visitVarInsn(Type.BOOLEAN_TYPE.getOpcode(Opcodes.ILOAD), sampleHitVarIndex);
                    v.visitJumpInsn(Opcodes.IFEQ, elseLabel);
                    v.visitVarInsn(Opcodes.LLOAD, endTimeIndex);
                    v.visitVarInsn(Opcodes.LLOAD, startTimeIndex);
                    v.visitInsn(Opcodes.LSUB);
                    v.visitJumpInsn(Opcodes.GOTO, endLabel);
                    v.visitLabel(elseLabel);
                    v.visitLdcInsn(0L);
                    v.visitLabel(endLabel);
                } else {
                    v.visitVarInsn(Opcodes.LLOAD, endTimeIndex);
                    v.visitVarInsn(Opcodes.LLOAD, startTimeIndex);
                    v.visitInsn(Opcodes.LSUB);
                }
                if (!trans) {
                    v.visitInsn(Opcodes.DUP2);
                    durationIndex = v.storeNewLocal(Type.LONG_TYPE);
                    durationSet = true;
                }
            } else {
                v.visitVarInsn(Opcodes.LLOAD, durationIndex);
            }
        }
    }

    protected void expandCallEndTime(TemplateExpanderVisitor v) {
        if (!endTimeSet) {
            Label skipLabel = new Label();
            if (getSamplingInterval() > 1) {
                v.visitLdcInsn(0L);
                endTimeIndex = v.storeNewLocal(Type.LONG_TYPE);
                endTimeSet = true;
                v.visitVarInsn(Type.BOOLEAN_TYPE.getOpcode(Opcodes.ILOAD), sampleHitVarIndex);
                v.visitJumpInsn(Opcodes.IFEQ, skipLabel);
            }
            v.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "nanoTime", "()J", false);
            if (getSamplingInterval() > 1) {
                v.visitVarInsn(Opcodes.LSTORE, endTimeIndex);
                v.visitLabel(skipLabel);
            } else {
                endTimeIndex = v.storeNewLocal(Type.LONG_TYPE);
                endTimeSet = true;
            }
        }
    }

    protected void expandCallStartTime(TemplateExpanderVisitor v) {
        if (!startTimeSet) {
            Label skipLabel = new Label();
            if (getSamplingInterval() > 1) {
                v.visitLdcInsn(0L);
                startTimeIndex = v.storeNewLocal(Type.LONG_TYPE);
                startTimeSet = true;
                v.visitLdcInsn(getSamplingInterval() * 2); // to get average "rate" we need a uniform distribution (0, 2*rate)
                v.visitLdcInsn(MethodID.getMethodId(getMethodIdString(lastTemplate)));
                v.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(SamplingSupport.class), "sampleHit", "(II)Z", false);
                v.visitInsn(Opcodes.DUP);
                // store the measurement flag for later reuse
                sampleHitVarIndex = v.storeNewLocal(Type.BOOLEAN_TYPE);
                v.visitJumpInsn(Opcodes.IFEQ, skipLabel);
            }
            v.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "nanoTime", "()J", false);
            if (getSamplingInterval() > 1) {
                v.visitVarInsn(Opcodes.LSTORE, startTimeIndex);
                v.visitLabel(skipLabel);
            } else {
                startTimeIndex = v.storeNewLocal(Type.LONG_TYPE);
                startTimeSet = true;
            }
        }
    }

    protected int getSamplingInterval() {
        return samplingInterval == Integer.MAX_VALUE ? 1 : samplingInterval;
    }

    protected String getMethodIdString(Template t) {
        return className + "#" + methodName + "#" + desc;
    }

    protected void resetDuration() {
        durationIndex = -1;
        durationSet = false;
    }

    protected void resetEndTime() {
        endTimeIndex = -1;
        endTimeSet = false;
    }
    protected void resetStartTime() {
        startTimeIndex = -1;
        startTimeSet = false;
    }

    public void resetState() {
        resetStartTime();
        resetEndTime();
        resetDuration();
    }
}
