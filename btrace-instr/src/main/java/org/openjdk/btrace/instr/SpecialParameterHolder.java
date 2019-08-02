/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A generalized super-class for various runtime classes
 * representing the BTrace annotations
 *
 * @author Jaroslav Bachorik
 */
abstract class SpecialParameterHolder {
    private int selfParameter = -1;
    private int methodParameter = -1;
    private int classNameParameter = -1;
    private int returnParameter = -1;
    private int targetMethodOrFieldParameter = -1;
    private int targetInstanceParameter = -1;
    private int durationParameter = -1;

    private boolean methodFqn = false;
    private boolean targetMethodFqn = false;

    public final int getSelfParameter() {
        return selfParameter;
    }

    public final void setSelfParameter(int selfParameter) {
        this.selfParameter = selfParameter;
    }

    public final int getClassNameParameter() {
        return classNameParameter;
    }

    public final void setClassNameParameter(int classNameParameter) {
        this.classNameParameter = classNameParameter;
    }

    public final int getMethodParameter() {
        return methodParameter;
    }

    public final void setMethodParameter(int methodParameter) {
        this.methodParameter = methodParameter;
    }

    public final boolean isMethodFqn() {
        return methodFqn;
    }

    public final void setMethodFqn(boolean val) {
        methodFqn = val;
    }

    public final boolean isTargetMethodOrFieldFqn() {
        return targetMethodFqn;
    }

    public final void setTargetMethodOrFieldFqn(boolean val) {
        targetMethodFqn = val;
    }

    public final int getReturnParameter() {
        return returnParameter;
    }

    public final void setReturnParameter(int returnParameter) {
        this.returnParameter = returnParameter;
    }

    public final int getTargetMethodOrFieldParameter() {
        return targetMethodOrFieldParameter;
    }

    public final void setTargetMethodOrFieldParameter(int calledMethodParameter) {
        targetMethodOrFieldParameter = calledMethodParameter;
    }

    public final int getTargetInstanceParameter() {
        return targetInstanceParameter;
    }

    public final void setTargetInstanceParameter(int calledInstanceParameter) {
        targetInstanceParameter = calledInstanceParameter;
    }

    public final int getDurationParameter() {
        return durationParameter;
    }

    public final void setDurationParameter(int durationParameter) {
        this.durationParameter = durationParameter;
    }

    public final void copyFrom(SpecialParameterHolder other) {
        classNameParameter = other.classNameParameter;
        durationParameter = other.durationParameter;
        methodParameter = other.methodParameter;
        returnParameter = other.returnParameter;
        selfParameter = other.selfParameter;
        targetInstanceParameter = other.targetInstanceParameter;
        targetMethodOrFieldParameter = other.targetMethodOrFieldParameter;
        methodFqn = other.methodFqn;
        targetMethodFqn = other.targetMethodFqn;
    }
}
