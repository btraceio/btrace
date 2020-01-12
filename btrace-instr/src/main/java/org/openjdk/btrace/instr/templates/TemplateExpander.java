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

package org.openjdk.btrace.instr.templates;

/**
 * An interface to be implemented by the template expanders
 *
 * @author Jaroslav Bachorik
 * @since 1.3
 */
public interface TemplateExpander {
    /**
     * The expander identifies and, possibly, expands the given template
     *
     * @param v The expander parent
     * @param t The template to process
     * @return appropriate {@linkplain Result} value
     */
    Result expand(TemplateExpanderVisitor v, Template t);

    /**
     * Called upon code points invalidating the current expander status
     */
    void resetState();

    /**
     * The result of expansion
     * <ul>
     * <li><b>EXPANDED</b> = expander has claimed and expanded the template</li>
     * <li><b>CLAIMED</b> = expander has claimed the template but didn't expand it</li>
     * <li><b>IGNORED</b> = expander has not claimed the template</li>
     * </ul>
     */
    enum Result {
        EXPANDED, CLAIMED, IGNORED
    }

    /**
     * A knockoff of the java.util.function.Consumer interface for pre-8 usage
     */
    interface Consumer<T> {
        void consume(T visitor);
    }
}
