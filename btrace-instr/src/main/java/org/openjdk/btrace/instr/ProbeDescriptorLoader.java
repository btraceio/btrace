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

package org.openjdk.btrace.instr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.helpers.DefaultValidationEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class loads BTrace probe descriptor XML files and caches the probe descriptions in a map.
 * The XML to object unmarshalling is done using JAXB.
 *
 * @author A. Sundararajan
 */
final class ProbeDescriptorLoader {
  private static final Logger log = LoggerFactory.getLogger(ProbeDescriptorLoader.class);

  // cache for loaded probe descriptors
  private static final Map<String, ProbeDescriptor> probeDescMap;

  static {
    probeDescMap = new ConcurrentHashMap<>();
  }

  // directories to search probe descriptor XML files.
  private final String[] probeDescDirs;

  ProbeDescriptorLoader(String probeDescPath) {
    // split probe descriptor path into directories
    probeDescDirs = probeDescPath != null ? probeDescPath.split(File.pathSeparator) : null;
  }

  ProbeDescriptor load(String namespace) {
    // check in the cache
    ProbeDescriptor res = probeDescMap.get(namespace);
    if (res != null) {
      if (log.isDebugEnabled()) {
        log.debug("probe descriptor cache hit for namespace {}", namespace);
      }
      return res;
    } else {
      // load probe descriptor for the given namespace
      InputStream file = openDescriptor(namespace);
      if (file == null) {
        if (log.isDebugEnabled()) {
          log.debug("didn't find probe descriptor file for namespace {}", namespace);
        }
        return null;
      }
      ProbeDescriptor pd = load(file);
      if (pd != null) {
        if (log.isDebugEnabled()) {
          log.debug("read probe descriptor for namespace {}", namespace);
        }
        probeDescMap.put(namespace, pd);
      }
      return pd;
    }
  }

  // unmarshall BTrace probe descriptor from XML
  private ProbeDescriptor load(InputStream stream) {
    try {
      JAXBContext jc =
          JAXBContext.newInstance("org.openjdk.btrace.core.annotations:org.openjdk.btrace.instr");
      Unmarshaller u = jc.createUnmarshaller();
      u.setEventHandler(new DefaultValidationEventHandler());
      ProbeDescriptor pd = (ProbeDescriptor) u.unmarshal(stream);
      pd.setProbes(pd.getProbes());
      return pd;
    } catch (JAXBException exp) {
      log.debug("Failed to load a BTrace probe descriptor", exp);
      return null;
    }
  }

  // look for <namespace>.xml file in each probe descriptor dir or on classpath
  private InputStream openDescriptor(String namespace) {
    InputStream is = null;
    if (probeDescDirs != null) {
      is = openDescriptorFromDirs(namespace);
    }
    if (is == null) {
      is = openDescriptorFromClassPath(namespace);
    }
    if (is == null && log.isDebugEnabled()) {
      log.debug("no probe descriptor found for namespace {}", namespace);
    }
    return is;
  }

  private InputStream openDescriptorFromDirs(String namespace) {
    String desc = namespace.trim() + ".xml";
    for (String dir : probeDescDirs) {
      File f = new File(dir.trim(), desc);
      if (log.isDebugEnabled())
        log.debug(
            "looking for probe descriptor file '{}' ({}, {})", f.getPath(), f.exists(), f.isFile());
      if (f.exists() && f.isFile()) {
        if (log.isDebugEnabled()) {
          log.debug("probe descriptor for namespace {} is {}", namespace, f);
        }
        try {
          return new FileInputStream(f);
        } catch (FileNotFoundException e) {
          // ignore; never happens
        }
      }
    }
    return null;
  }

  private InputStream openDescriptorFromClassPath(String namespace) {
    String target = Constants.EMBEDDED_BTRACE_SECTION_HEADER + namespace.trim() + ".xml";
    if (log.isDebugEnabled()) {
      log.debug("looking for probe descriptor file '{}'", target);
    }
    return ClassLoader.getSystemResourceAsStream(target);
  }
}
