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

package com.sun.btrace.agent;

import com.sun.btrace.DebugSupport;
import java.io.File;
import java.util.Map;
import javax.xml.bind.*;
import javax.xml.bind.helpers.DefaultValidationEventHandler;
import com.sun.btrace.runtime.ProbeDescriptor;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class loads BTrace probe descriptor XML files and
 * caches the probe descriptions in a map. The XML to object
 * unmarshalling is done using JAXB.
 *
 * @author A. Sundararajan
 */
final class ProbeDescriptorLoader {
    // directories to search probe descriptor XML files.
    private final String[] probeDescDirs;
    private final DebugSupport debug;

    ProbeDescriptorLoader(String probeDescPath, DebugSupport ds) {
        // split probe descriptor path into directories
        this.probeDescDirs = probeDescPath.split(File.pathSeparator);
        this.debug = ds;
    }

    // cache for loaded probe descriptors
    private static final Map<String, ProbeDescriptor> probeDescMap;
    static {
        probeDescMap = new ConcurrentHashMap<>();
    }

    ProbeDescriptor load(String namespace) {
        // check in the cache
        ProbeDescriptor res = probeDescMap.get(namespace);
        if (res != null) {
            if (debug.isDebug()) debug.print("probe descriptor cache hit for " + namespace);
            return res;
        } else {
            // load probe descriptor for the given namespace
            File file = findFile(namespace);
            if (file == null) {
                if (debug.isDebug()) debug.print("didn't find probe descriptor file " + namespace);
                return null;
            }
            ProbeDescriptor pd = load(file);
            if (pd != null) {
                if (debug.isDebug()) debug.print("read probe descriptor for " + namespace);
                probeDescMap.put(namespace, pd);
            }
            return pd;
        }
    }

    // unmarshall BTrace probe descriptor from XML
    private ProbeDescriptor load(File file) {
        try {
            JAXBContext jc = JAXBContext.newInstance("com.sun.btrace.annotations:com.sun.btrace.runtime");
            if (debug.isDebug()) debug.print("reading " + file);
            Unmarshaller u = jc.createUnmarshaller();
            u.setEventHandler(new DefaultValidationEventHandler());
            ProbeDescriptor pd = (ProbeDescriptor)u.unmarshal(file);
            pd.setProbes(pd.getProbes());
            return pd;
        } catch (JAXBException exp) {
            if (debug.isDebug()) debug.print(exp);
            return null;
        }
    }

    // look for <namespace>.xml file in each probe descriptor dir
    private File findFile(String namespace) {
        for (String dir : probeDescDirs) {
            File f = new File(dir.trim(), namespace.trim() + ".xml");
            if (debug.isDebug()) debug.print("looking for probe descriptor file '" + f.getPath() + "' (" + f.exists() + ", " + f.isFile() + ")");
            if (f.exists() && f.isFile()) {
                if (debug.isDebug()) debug.print("probe descriptor for " + namespace + " is " + f);
                return f;
            }
        }
        if (debug.isDebug()) debug.print("no probe descriptor found for " + namespace);
        return null;
    }
}
