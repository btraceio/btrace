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

package com.sun.btrace.agent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import javax.xml.bind.*;
import javax.xml.bind.helpers.DefaultValidationEventHandler;
import com.sun.btrace.runtime.OnMethod;
import com.sun.btrace.runtime.OnProbe;
import com.sun.btrace.runtime.ProbeDescriptor;

/**
 * This class loads BTrace probe descriptor XML files and
 * caches the probe descriptions in a map. The XML to object
 * unmarshalling is done using JAXB.
 *
 * @author A. Sundararajan
 */
final class ProbeDescriptorLoader {
    private ProbeDescriptorLoader() {}

    // directories to search probe descriptor XML files.
    private static String[] probeDescDirs;
    static void init(String probeDescPath) {
        // split probe descriptor path into directories
        probeDescDirs = probeDescPath.split(File.pathSeparator);
    }

    // cache for loaded probe descriptors
    private static Map<String, ProbeDescriptor> probeDescMap;
    static {
        probeDescMap = Collections.synchronizedMap(
            new HashMap<String, ProbeDescriptor>());
    }
    
    static synchronized ProbeDescriptor load(String namespace) {
        // check in the cache
        ProbeDescriptor res = probeDescMap.get(namespace);
        if (res != null) {
            if (Main.isDebug()) Main.debugPrint("probe descriptor cache hit for " + namespace);
            return res;
        } else {
            // load probe descriptor for the given namespace
            File file = findFile(namespace);
            if (file == null) {
                return null;
            }
            ProbeDescriptor pd = load(file);
            if (pd != null) {
                if (Main.isDebug()) Main.debugPrint("read probe descriptor for " + namespace);
                probeDescMap.put(namespace, pd);
            }
            return pd;
        }
    }   

    // unmarshell BTrace probe descriptor from XML
    private static ProbeDescriptor load(File file) {
        try {
            JAXBContext jc = JAXBContext.newInstance("com.sun.btrace.annotations:com.sun.btrace.runtime");
            if (Main.isDebug()) Main.debugPrint("reading " + file);
            Unmarshaller u = jc.createUnmarshaller();
            u.setEventHandler(new DefaultValidationEventHandler());
            ProbeDescriptor probeDescriptor = (ProbeDescriptor)u.unmarshal(file);
            probeDescriptor.setProbes(probeDescriptor.getProbes());
            return probeDescriptor;
        } catch (JAXBException exp) {
            if (Main.isDebug()) Main.debugPrint(exp);
            return null;
        }
    }

    // look for <namespace>.xml file in each probe descriptor dir
    private static File findFile(String namespace) {
        for (String dir : probeDescDirs) {
            File f = new File(dir, namespace + ".xml");
            if (f.exists() && f.isFile()) {
                if (Main.isDebug()) Main.debugPrint("probe descriptor for " + namespace + " is " + f);
                return f;
            }
        }
        if (Main.isDebug()) Main.debugPrint("no probe descriptor found for " + namespace);
        return null;
    }
}
