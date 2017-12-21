/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import java.util.Collection;
import java.util.HashSet;

/**
 * This class is used to store data of the annotation
 * com.sun.btrace.annotations.OnProbe. We can not read the
 * OnMethod annotation using reflection API [because we strip
 * {@code @OnProbe} annotated methods before defineClass]. Instead,
 * we read OnProbe annotation while parsing the BTrace class and
 * store the data in an instance of this class. Please note that
 * the get/set methods have to be in sync with OnProbe annotation.
 *
 * @author A. Sundararajan
 */
public final class OnProbe extends SpecialParameterHolder {
    private String namespace;
    private String name;
    // target method name on which this annotation is specified
    private String targetName;
    // target method descriptor on which this annotation is specified
    private String targetDescriptor;
    private Collection<OnMethod> onMethods;
    private BTraceMethodNode bmn;

    public OnProbe(BTraceMethodNode bmn) {
        this.bmn = bmn;
    }

    public OnProbe() {
        // need this to deserialize from the probe descriptor
    }

    void setMethodNode(BTraceMethodNode bmn) {
        this.bmn = bmn;
    }

    @XmlAttribute
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @XmlAttribute
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    @XmlElement(name="map")
    public Collection<OnMethod> getOnMethods() {
        return onMethods;
    }

    public void setOnMethods(Collection<OnMethod> om) {
        onMethods = om;
    }

    public void copyFrom(OnProbe other) {
        super.copyFrom(other);
        namespace = other.namespace;
        name = other.name;
        targetName = other.targetName;
        targetDescriptor = other.targetDescriptor;
        onMethods = new HashSet<OnMethod>(other.onMethods);
    }

    public BTraceMethodNode getMethodNode() {
        return bmn;
    }
}
