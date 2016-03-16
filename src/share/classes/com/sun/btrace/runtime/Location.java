/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Where;

/**
 * This class is used to store data of the annotation
 * com.sun.btrace.annotations.Location. We can not read the
 * Location annotation using reflection API [because we strip
 * @OnMethod annotated methods before defineClass]. Instead,
 * we read Location annotation while parsing the BTrace class and
 * store the data in an instance of this class. Please note that
 * the get/set methods have to be in sync with Location annotation.
 *
 * @author A. Sundararajan
 */
public class Location {
    private String clazz = "";
    private String method = "";
    private String type = "";
    private String field = "";
    private int line = 0;
    private Kind value = Kind.ENTRY;
    private Where where = Where.BEFORE;

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public String getClazz() {
        return clazz;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getField() {
        return field;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getLine() {
        return line;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setValue(Kind value) {
        this.value = value;
    }

    public Kind getValue() {
        return value;
    }

    public void setWhere(Where where) {
        this.where = where;
    }

    public Where getWhere() {
        return where;
    }

    @Override
    public String toString() {
        return "Location{" + "clazz=" + clazz + ", method=" + method + ", type=" + type + ", field=" + field + ", line=" + line + ", value=" + value + ", where=" + where + '}';
    }
}
