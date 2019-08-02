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
package org.openjdk.btrace.core.aggregation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A key identifying an element of data in an aggregation. This represents a tuple of object values contained in an
 * Object[] array. Elements in the tuple may be null or of type {@link String} or {@link Number}.
 * <p>
 *
 * @author Christian Glencross
 */
public final class AggregationKey {

    private static final Set<Class<?>> validKeyElementTypes = new HashSet<Class<?>>();

    static {
        validKeyElementTypes.add(String.class);
        validKeyElementTypes.add(Boolean.class);
        validKeyElementTypes.add(Byte.class);
        validKeyElementTypes.add(Character.class);
        validKeyElementTypes.add(Short.class);
        validKeyElementTypes.add(Integer.class);
        validKeyElementTypes.add(Long.class);
    }

    private final Object[] elements;

    public AggregationKey(Object[] elements) {

        // Validate that no unusual datatypes are in the key. These
        // values may end up getting serialized to the client so we do not want
        // anything unusual.
        for (int i = 0; i < elements.length; i++) {
            Object element = elements[i];
            if (element != null && (element.getClass() != String.class) && (element.getClass() != Boolean.class) && (element.getClass() != Byte.class) && (element.getClass() != Character.class) && (element.getClass() != Short.class) && (element.getClass() != Integer.class) && (element.getClass() != Long.class)) {
                throw new IllegalArgumentException("Aggregation key element type '" + element.getClass().getName() + "' is not supported");
            }
        }

        this.elements = elements;
    }

    public Object[] getElements() {
        return elements;
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(elements);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AggregationKey other = (AggregationKey) obj;
        return Arrays.equals(elements, other.elements);
    }
}
