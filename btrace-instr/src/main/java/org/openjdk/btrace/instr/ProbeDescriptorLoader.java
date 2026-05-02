/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
