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

package com.sun.btrace.util.templates;

import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A template descriptor
 * @author Jaroslav Bachorik
 * @since 1.3
 */
public final class Template {
    private final String owner = "BTrace$Templates";
    private final String name;
    private final String sig;
    private final Set<String> tags = new HashSet<String>();

    public Template(String name, String sig) {
        this.name = name;
        this.sig = sig;
    }

    public Template(String name, String sig, String ... tags) {
        this.name = name;
        this.sig = sig;
        for(String t : tags) {
            this.tags.add(t);
        }
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getSig() {
        return sig;
    }

    public void insert(MethodVisitor mv) {
        insert(mv, Collections.EMPTY_SET);
    }

    public void insert(MethodVisitor mv, String ... tags) {
        insert(mv, new HashSet<String>(Arrays.asList(tags)));
    }

    public void insert(MethodVisitor mv, Set<String> tags) {
        StringBuilder sb = new StringBuilder(name);
        boolean head = true;
        for (String t : tags) {
            if (head) {
                head = false;
                sb.append(':');
            } else {
                sb.append(',');
            }
            sb.append(t);
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, sb.toString(), sig, false);
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(this.tags);
    }

    void setTags(Set<String> tags) {
        this.tags.clear();
        this.tags.addAll(tags);
    }

    String getId() {
        return owner + "#" + name + "sig";
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + (this.name != null ? this.name.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Template other = (Template) obj;
        if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Template{" + "name=" + name + ", tags=" + tags + '}';
    }

    static String getId(String owner, String name, String sig) {
        return owner + "#" + name + "sig";
    }

    public final Template duplicate() {
        return new Template(name, sig);
    }
}
