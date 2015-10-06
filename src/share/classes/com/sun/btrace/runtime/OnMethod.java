/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.btrace.annotations.Sampled;

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
public class OnMethod extends SpecialParameterHolder {
    private String clazz;
    private String method = "";
    private String type = "";
    private Location loc = new Location();
    // target method name on which this annotation is specified
    private String targetName;
    // target method descriptor on which this annotation is specified
    private String targetDescriptor;

    private int samplerMean = 0;
    private Sampled.Sampler samplerKind = Sampled.Sampler.None;

    private com.sun.btrace.runtime.Level level = new com.sun.btrace.runtime.Level();

    public OnMethod() {
    }

    public void copyFrom(OnMethod other) {
        super.copyFrom(other);
        setClazz(other.getClazz());
        setMethod(other.getMethod());
        setType(other.getType());
        setLocation(other.getLocation());
        setLevel(other.getLevel());
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

    public void setSamplerKind(Sampled.Sampler kind) {
        this.samplerKind = kind;
    }

    public Sampled.Sampler getSamplerKind() {
        return this.samplerKind;
    }

    public void setSamplerMean(int mean) {
        this.samplerMean = mean;
    }

    public int getSamplerMean() {
        return samplerMean;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }
}
