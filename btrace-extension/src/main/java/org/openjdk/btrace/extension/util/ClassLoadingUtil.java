package org.openjdk.btrace.extension.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Helper utilities for extension implementations to resolve classes/services using
 * the application class loader context without polluting bootstrap or system classpaths.
 */
public final class ClassLoadingUtil {
  private ClassLoadingUtil() {}

  /** Returns the current thread context class loader. */
  public static ClassLoader tccl() {
    return Thread.currentThread().getContextClassLoader();
  }

  /** Returns the defining class loader of the given object's class (may be null for bootstrap). */
  public static ClassLoader definingLoader(Object o) {
    return o != null ? o.getClass().getClassLoader() : null;
  }

  /** Attempts to load a class by name using the given preferred loader, then TCCL, then system. */
  public static Class<?> load(String className, ClassLoader preferred) throws ClassNotFoundException {
    ClassNotFoundException last = null;
    if (preferred != null) {
      try {
        return Class.forName(className, false, preferred);
      } catch (ClassNotFoundException e) {
        last = e;
      }
    }
    ClassLoader c = tccl();
    if (c != null) {
      try {
        return Class.forName(className, false, c);
      } catch (ClassNotFoundException e) {
        last = e;
      }
    }
    return Class.forName(className, false, ClassLoader.getSystemClassLoader());
  }

  /** Convenience: attempt to load using the defining loader of a context object. */
  public static Class<?> loadFromContext(String className, Object context) throws ClassNotFoundException {
    return load(className, definingLoader(context));
  }

  /** Optional-like load that does not throw; returns Optional.empty() when not found. */
  public static Optional<Class<?>> tryLoad(String className, ClassLoader preferred) {
    try {
      return Optional.of(load(className, preferred));
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  /** Runs the supplier with the given TCCL set, restoring the original after execution. */
  public static <T> T withTCCL(ClassLoader loader, Supplier<T> action) {
    ClassLoader orig = tccl();
    try {
      Thread.currentThread().setContextClassLoader(loader);
      return action.get();
    } finally {
      Thread.currentThread().setContextClassLoader(orig);
    }
  }

  /** Runnable variant of withTCCL. */
  public static void withTCCL(ClassLoader loader, Runnable action) {
    ClassLoader orig = tccl();
    try {
      Thread.currentThread().setContextClassLoader(loader);
      action.run();
    } finally {
      Thread.currentThread().setContextClassLoader(orig);
    }
  }

  /** Runs the supplier under the defining loader of the given context object. */
  public static <T> T withDefiningLoader(Object context, Supplier<T> action) {
    return withTCCL(definingLoader(context), action);
  }

  /** Loads the first available service implementation using the given loader (or TCCL if null). */
  public static <S> S loadService(Class<S> service, ClassLoader loader) {
    ClassLoader cl = (loader != null ? loader : tccl());
    ServiceLoader<S> sl = (cl != null ? ServiceLoader.load(service, cl) : ServiceLoader.load(service));
    Iterator<S> it = sl.iterator();
    return it.hasNext() ? it.next() : null;
  }

  /** Loads all available service implementations using the given loader (or TCCL if null). */
  public static <S> List<S> loadServices(Class<S> service, ClassLoader loader) {
    List<S> out = new ArrayList<>();
    ClassLoader cl = (loader != null ? loader : tccl());
    ServiceLoader<S> sl = (cl != null ? ServiceLoader.load(service, cl) : ServiceLoader.load(service));
    for (S s : sl) {
      out.add(s);
    }
    return out;
  }

  /** Returns a resource URL using the given loader (or TCCL if null). */
  public static URL getResource(String name, ClassLoader loader) {
    ClassLoader cl = (loader != null ? loader : tccl());
    return cl != null ? cl.getResource(name) : ClassLoader.getSystemResource(name);
  }

  /** Opens a resource as stream using the given loader (or TCCL if null). */
  public static InputStream openResource(String name, ClassLoader loader) {
    ClassLoader cl = (loader != null ? loader : tccl());
    return cl != null ? cl.getResourceAsStream(name) : ClassLoader.getSystemResourceAsStream(name);
  }

  /** Create a child URLClassLoader from a list of jar/file paths. */
  public static URLClassLoader newChildURLClassLoader(List<Path> entries, ClassLoader parent)
      throws MalformedURLException {
    URL[] urls = new URL[entries.size()];
    for (int i = 0; i < entries.size(); i++) {
      urls[i] = entries.get(i).toUri().toURL();
    }
    return new URLClassLoader(urls, parent);
  }

  /** Safely close a URLClassLoader (no-op for other loaders). */
  public static void safeClose(ClassLoader cl) {
    if (cl instanceof URLClassLoader) {
      try {
        ((URLClassLoader) cl).close();
      } catch (IOException ignored) {
      }
    }
  }
}
