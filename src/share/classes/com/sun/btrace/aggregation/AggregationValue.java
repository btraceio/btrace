/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.btrace.aggregation;

/**
 * An element of aggregated data stored in an {@link Aggregation}.
 * 
 * Concrete implementations of this interface will implement different aggregating functions.
 * <p>
 * 
 * @author Christian Glencross
 */
public interface AggregationValue {

    /**
     * Adds a data item to the aggregated value.
     * 
     * @param data
     *            the data value
     */
    void add(int data);

    /**
     * Removes all data items previously added.
     */
    void clear();

    /**
     * @return the aggregated value of all data items added since the aggregation was created or last cleared. The
     *         aggregation function is determined by the concrete implementation of the interface.
     */
    int getValue();

    /**
     * @return an object representation of the aggregated value. For most implementations this may be equivalent to
     *         <code>Integer.valueOf( getValue() )</code>. More complex aggregations such may return objects
     *         representing histograms, etc.
     */
    Object getData();
}
