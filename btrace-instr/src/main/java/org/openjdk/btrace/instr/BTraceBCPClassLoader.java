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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.openjdk.btrace.core.SharedSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BTraceBCPClassLoader extends URLClassLoader {
  private static final Logger log = LoggerFactory.getLogger(BTraceBCPClassLoader.class);

  BTraceBCPClassLoader(SharedSettings settings) {
    super(getBCPUrls(settings), null);
  }

  private static URL[] getBCPUrls(SharedSettings settings) {
    String bcp = settings.getBootClassPath();
    if (bcp != null && !bcp.isEmpty()) {
      List<URL> urls = new ArrayList<>();
      for (String cpElement : bcp.split(File.pathSeparator)) {
        try {
          urls.add(new File(cpElement).toURI().toURL());
        } catch (MalformedURLException e) {
          log.debug("Invalid classpath definition: {}", cpElement, e);
        }
      }
      return urls.toArray(new URL[0]);
    }
    return new URL[0];
  }

  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    // delegate class loading to parent directly
    ClassLoader parent = getParent();
    if (parent == null) {
      parent = ClassLoader.getSystemClassLoader();
    }
    return parent.loadClass(name);
  }

  @Override
  public URL getResource(String name) {
    // follow the standard process to load resources
    return super.getResource(name);
  }
}
