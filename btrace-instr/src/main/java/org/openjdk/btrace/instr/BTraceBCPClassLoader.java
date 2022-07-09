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
