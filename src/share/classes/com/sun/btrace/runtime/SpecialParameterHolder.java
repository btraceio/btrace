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
package com.sun.btrace.runtime;

/**
 * A generalized super-class for various runtime classes
 * representing the BTrace annotations
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

    final public int getSelfParameter() {
        return selfParameter;
    }

    final public void setSelfParameter(int selfParameter) {
        this.selfParameter = selfParameter;
    }

    final public int getClassNameParameter() {
        return classNameParameter;
    }

    final public void setClassNameParameter(int classNameParameter) {
        this.classNameParameter = classNameParameter;
    }

    final public int getMethodParameter() {
        return methodParameter;
    }

    final public void setMethodParameter(int methodParameter) {
        this.methodParameter = methodParameter;
    }

    final public boolean isMethodFqn() {
        return methodFqn;
    }

    final public void setMethodFqn(boolean val) {
        methodFqn = val;
    }

    final public boolean isTargetMethodOrFieldFqn() {
        return targetMethodFqn;
    }

    final public void setTargetMethodOrFieldFqn(boolean val) {
        targetMethodFqn = val;
    }

    final public int getReturnParameter() {
        return returnParameter;
    }

    final public void setReturnParameter(int returnParameter) {
        this.returnParameter = returnParameter;
    }

    final public int getTargetMethodOrFieldParameter() {
        return targetMethodOrFieldParameter;
    }

    final public void setTargetMethodOrFieldParameter(int calledMethodParameter) {
        this.targetMethodOrFieldParameter = calledMethodParameter;
    }

    final public int getTargetInstanceParameter() {
        return targetInstanceParameter;
    }

    final public void setTargetInstanceParameter(int calledInstanceParameter) {
        this.targetInstanceParameter = calledInstanceParameter;
    }

    final public int getDurationParameter() {
        return durationParameter;
    }

    final public void setDurationParameter(int durationParameter) {
        this.durationParameter = durationParameter;
    }

    final public void copyFrom(SpecialParameterHolder other) {
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
