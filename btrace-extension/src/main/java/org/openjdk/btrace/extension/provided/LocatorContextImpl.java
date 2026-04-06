package org.openjdk.btrace.extension.provided;

import org.openjdk.btrace.core.extensions.LocatorContext;

import java.util.Collections;
import java.util.Map;

/** Default implementation of {@link LocatorContext}. */
public final class LocatorContextImpl implements LocatorContext {

  private final String extensionId;
  private final Map<String, String> properties;

  public LocatorContextImpl(String extensionId, Map<String, String> properties) {
    this.extensionId = extensionId;
    this.properties = properties != null ? properties : Collections.emptyMap();
  }

  @Override
  public String getExtensionId() {
    return extensionId;
  }

  @Override
  public Map<String, String> getProperties() {
    return properties;
  }

  @Override
  public boolean hasClass(String className) {
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      if (cl == null) cl = ClassLoader.getSystemClassLoader();
      Class.forName(className, false, cl);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @Override
  public String getSystemProperty(String key) {
    return System.getProperty(key);
  }

  @Override
  public String getEnv(String name) {
    return System.getenv(name);
  }
}
