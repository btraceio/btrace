package org.openjdk.btrace.extension.util;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Classloader utilities for "provided-style" extensions that need to access application classes
 * without import-time coupling.
 *
 * <p>Extensions running inside the BTrace agent cannot directly import application types (Spark,
 * Hadoop, etc.) because they are loaded by a different classloader. This utility provides safe,
 * multi-loader class resolution with fallback strategies.
 */
public final class ClassLoadingUtil {

  private ClassLoadingUtil() {}

  /** Returns the thread context classloader. */
  public static ClassLoader tccl() {
    return Thread.currentThread().getContextClassLoader();
  }

  /** Returns the classloader that defined the given object's class. */
  public static ClassLoader definingLoader(Object o) {
    return o == null ? null : o.getClass().getClassLoader();
  }

  /**
   * Load a class using a multi-loader fallback strategy.
   *
   * <p>Tries in order: {@code preferred} loader, thread context classloader, system classloader.
   *
   * @param className fully qualified class name
   * @param preferred preferred classloader to try first (may be {@code null})
   * @return the loaded class
   * @throws ClassNotFoundException if no loader can find the class
   */
  public static Class<?> load(String className, ClassLoader preferred)
      throws ClassNotFoundException {
    if (preferred != null) {
      try {
        return Class.forName(className, false, preferred);
      } catch (ClassNotFoundException ignored) {
      }
    }

    ClassLoader tcl = tccl();
    if (tcl != null && tcl != preferred) {
      try {
        return Class.forName(className, false, tcl);
      } catch (ClassNotFoundException ignored) {
      }
    }

    return Class.forName(className, false, ClassLoader.getSystemClassLoader());
  }

  /**
   * Load a class from the defining classloader of the given context object.
   *
   * @param className fully qualified class name
   * @param context object whose classloader to use
   * @return the loaded class
   * @throws ClassNotFoundException if the class cannot be found
   */
  public static Class<?> loadFromContext(String className, Object context)
      throws ClassNotFoundException {
    return load(className, definingLoader(context));
  }

  /**
   * Try to load a class without throwing. Returns {@link Optional#empty()} if not found.
   *
   * @param className fully qualified class name
   * @param preferred preferred classloader (may be {@code null})
   * @return optional containing the class, or empty
   */
  public static Optional<Class<?>> tryLoad(String className, ClassLoader preferred) {
    try {
      return Optional.of(load(className, preferred));
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  /**
   * Execute a block of code with a temporary thread context classloader, restoring the original
   * afterwards.
   *
   * @param loader classloader to set as TCCL
   * @param action code to execute
   * @param <T> return type
   * @return result of the action
   */
  public static <T> T withTCCL(ClassLoader loader, Supplier<T> action) {
    Thread t = Thread.currentThread();
    ClassLoader original = t.getContextClassLoader();
    t.setContextClassLoader(loader);
    try {
      return action.get();
    } finally {
      t.setContextClassLoader(original);
    }
  }

  /**
   * Load services of the given type using the specified classloader.
   *
   * @param service service interface class
   * @param loader classloader to use for service discovery
   * @param <S> service type
   * @return first service implementation found, or {@code null}
   */
  public static <S> S loadService(Class<S> service, ClassLoader loader) {
    ServiceLoader<S> sl = ServiceLoader.load(service, loader);
    Iterator<S> it = sl.iterator();
    return it.hasNext() ? it.next() : null;
  }

  /**
   * Create a child URLClassLoader from the given JAR paths.
   *
   * @param jars paths to JAR files
   * @param parent parent classloader
   * @return new URLClassLoader
   */
  public static URLClassLoader newChildURLClassLoader(List<Path> jars, ClassLoader parent) {
    URL[] urls = new URL[jars.size()];
    for (int i = 0; i < jars.size(); i++) {
      try {
        urls[i] = jars.get(i).toUri().toURL();
      } catch (java.net.MalformedURLException e) {
        throw new IllegalArgumentException("Invalid JAR path: " + jars.get(i), e);
      }
    }
    return new URLClassLoader(urls, parent);
  }

  /**
   * Safely close a classloader if it is a {@link URLClassLoader}.
   *
   * @param cl classloader to close (may be {@code null})
   */
  public static void safeClose(ClassLoader cl) {
    if (cl instanceof URLClassLoader) {
      try {
        ((URLClassLoader) cl).close();
      } catch (java.io.IOException ignored) {
      }
    }
  }
}
