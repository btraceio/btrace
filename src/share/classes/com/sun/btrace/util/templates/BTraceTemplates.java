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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * A central place for bytecode templates
 * @author Jaroslav Bachorik
 * @since 1.3
 */
public final class BTraceTemplates {
    private static final Map<String, Template> TEMPLATE_MAP = new HashMap<>();

    public static void registerTemplates(Template ... templates) {
        for(Template t : templates) {
            TEMPLATE_MAP.put(t.getId(), t);
        }
    }

    public static Template getTemplate(String owner, String name, String sig) {
        Template t;

        int idx = name.indexOf(':');
        if (idx > -1) {
            String tName = name.substring(0, idx);
            String tagList = name.substring(idx + 1);

            t = getTemplate(Template.getId(owner, tName, sig));
            if (t != null) {
                StringTokenizer st = new StringTokenizer(tagList, ",");

                Set<String> tags = new HashSet<>();
                while (st.hasMoreTokens()) {
                    String param = st.nextToken();
                    tags.add(param);
                }

                t.setTags(tags);
            }
        } else {
            t = getTemplate(Template.getId(owner, name, sig));
        }

        return t;
    }

    private static Template getTemplate(String id) {
        Template t = TEMPLATE_MAP.get(id);
        return t != null ? t.duplicate() : null;
    }
}
