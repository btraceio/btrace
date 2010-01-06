/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace.runtime;

/**
 * This class is used to store data of the annotation
 * com.sun.btrace.annotations.OnMethod. We can not read the
 * OnMethod annotation using reflection API [because we strip
 * @OnMethod annotated methods before defineClass]. Instead,
 * we read OnMethod annotation while parsing the BTrace class and 
 * store the data in an instance of this class. Please note that
 * the get/set methods have to be in sync with OnMethod annotation.
 * 
 * @author A. Sundararajan
 */
public class OnMethod {
    private String clazz;
    private String method = "";
    private String type = "";
    private Location loc = new Location();
    // target method name on which this annotation is specified
    private String targetName;
    // target method descriptor on which this annotation is specified
    private String targetDescriptor;

    private int selfParameter = -1;
    private int methodParameter = -1;
    private int classNameParameter = -1;
    private int returnParameter = -1;
    private int targetMethodOrFieldParameter = -1;
    private int targetInstanceParameter = -1;
    private int durationParameter = -1;
    
    public OnMethod() {
    }

    public void copyFrom(OnMethod other) {
        setClazz(other.getClazz());
        setMethod(other.getMethod());
        setType(other.getType());
        setLocation(other.getLocation());
    }

    public String getClazz() {
        return clazz;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Location getLocation() {
        return loc;
    }

    public void setLocation(Location loc) {
        this.loc = loc;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String name) {
        this.targetName = name;
    }

    public String getTargetDescriptor() {
        return targetDescriptor;
    }

    public void setTargetDescriptor(String desc) {
        this.targetDescriptor = desc;
    }

    public int getSelfParameter() {
        return selfParameter;
    }

    public void setSelfParameter(int selfParameter) {
        this.selfParameter = selfParameter;
    }

    public int getClassNameParameter() {
        return classNameParameter;
    }

    public void setClassNameParameter(int classNameParameter) {
        this.classNameParameter = classNameParameter;
    }

    public int getMethodParameter() {
        return methodParameter;
    }

    public void setMethodParameter(int methodParameter) {
        this.methodParameter = methodParameter;
    }

    public int getReturnParameter() {
        return returnParameter;
    }

    public void setReturnParameter(int returnParameter) {
        this.returnParameter = returnParameter;
    }

    public int getTargetMethodOrFieldParameter() {
        return targetMethodOrFieldParameter;
    }

    public void setTargetMethodOrFieldParameter(int calledMethodParameter) {
        this.targetMethodOrFieldParameter = calledMethodParameter;
    }

    public int getTargetInstanceParameter() {
        return targetInstanceParameter;
    }

    public void setTargetInstanceParameter(int calledInstanceParameter) {
        this.targetInstanceParameter = calledInstanceParameter;
    }

    public int getDurationParameter() {
        return durationParameter;
    }

    public void setDurationParameter(int durationParameter) {
        this.durationParameter = durationParameter;
    }


}
