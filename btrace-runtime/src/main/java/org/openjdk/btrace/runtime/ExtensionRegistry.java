package org.openjdk.btrace.runtime;

import java.util.Collections;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import org.openjdk.btrace.core.extensions.Extension;
import org.openjdk.btrace.core.extensions.ExtensionContext;
import org.openjdk.btrace.core.extensions.ExtensionException;
import org.openjdk.btrace.core.extensions.ExtensionMeta;
import org.openjdk.btrace.core.extensions.Permission;
import org.openjdk.btrace.core.extensions.PermissionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for BTrace extensions.
 *
 * <p>Manages extension discovery via ServiceLoader and per-script extension instances. Each script
 * gets its own extension instances to ensure proper isolation.
 */
public final class ExtensionRegistry {
  private static final Logger log = LoggerFactory.getLogger(ExtensionRegistry.class);

  /** Global registry of discovered extensions (class -> metadata) */
  private static final ConcurrentHashMap<Class<? extends Extension>, ExtensionMeta> registry =
      new ConcurrentHashMap<>();

  /** Registry of failed extensions (className -> error message) */
  private static final ConcurrentHashMap<String, String> failedExtensions =
      new ConcurrentHashMap<>();

  /** Per-script extension instances (scriptClassName -> (extensionClass -> instance)) */
  private final ConcurrentHashMap<String, Map<Class<? extends Extension>, Extension>> instances =
      new ConcurrentHashMap<>();

  /** Context factory for creating extension contexts */
  private final ExtensionContextFactory contextFactory;

  /**
   * Creates a new extension registry.
   *
   * @param contextFactory factory for creating extension contexts
   */
  public ExtensionRegistry(ExtensionContextFactory contextFactory) {
    this.contextFactory = contextFactory;
  }

  /**
   * Discovers extensions on the classpath using ServiceLoader.
   *
   * @param classLoader the classloader to use for discovery
   */
  public static void discover(ClassLoader classLoader) {
    ServiceLoader<Extension> loader = ServiceLoader.load(Extension.class, classLoader);
    for (Extension extension : loader) {
      Class<? extends Extension> clazz = extension.getClass();
      if (!registry.containsKey(clazz)) {
        try {
          ExtensionMeta meta = ExtensionMeta.from(clazz);
          registry.put(clazz, meta);
          log.debug("Discovered extension: {} v{}", meta.getName(), meta.getVersion());
        } catch (ExtensionException e) {
          String errorMsg = e.getMessage();
          failedExtensions.put(clazz.getName(), errorMsg);
          log.warn("Failed to load extension {}: {}", clazz.getName(), errorMsg);
        }
      }
    }
  }

  /**
   * Returns the metadata for a registered extension.
   *
   * @param extensionClass the extension class
   * @return extension metadata, or null if not registered
   */
  public static ExtensionMeta getMeta(Class<? extends Extension> extensionClass) {
    return registry.get(extensionClass);
  }

  /**
   * Checks if an extension class is registered.
   *
   * @param extensionClass the extension class
   * @return true if registered
   */
  public static boolean isRegistered(Class<? extends Extension> extensionClass) {
    return registry.containsKey(extensionClass);
  }

  /**
   * Returns an unmodifiable map of extensions that failed to load during discovery.
   *
   * @return map of extension class names to error messages
   */
  public static Map<String, String> getFailedExtensions() {
    return Collections.unmodifiableMap(failedExtensions);
  }

  /**
   * Checks if an extension failed to load during discovery.
   *
   * @param extensionClass the extension class
   * @return true if the extension failed to load
   */
  public static boolean isFailedExtension(Class<? extends Extension> extensionClass) {
    return failedExtensions.containsKey(extensionClass.getName());
  }

  /**
   * Gets the error message for a failed extension.
   *
   * @param extensionClass the extension class
   * @return the error message, or null if the extension did not fail
   */
  public static String getFailureReason(Class<? extends Extension> extensionClass) {
    return failedExtensions.get(extensionClass.getName());
  }

  /**
   * Gets or creates an extension instance for the specified script.
   *
   * @param extensionClass the extension class
   * @param scriptClassName the script class name
   * @param <T> the extension type
   * @return the extension instance
   * @throws ExtensionException if extension cannot be instantiated or initialized
   */
  @SuppressWarnings("unchecked")
  public <T extends Extension> T getExtension(Class<T> extensionClass, String scriptClassName) {
    // Check if this extension failed to load during discovery
    String failureReason = failedExtensions.get(extensionClass.getName());
    if (failureReason != null) {
      throw new ExtensionException(
          "Cannot use extension "
              + extensionClass.getName()
              + " because it failed to load during discovery: "
              + failureReason);
    }

    Map<Class<? extends Extension>, Extension> scriptExtensions =
        instances.computeIfAbsent(scriptClassName, k -> new ConcurrentHashMap<>());

    Extension extension =
        scriptExtensions.computeIfAbsent(
            extensionClass,
            clazz -> {
              try {
                // Create context first to get granted permissions
                ExtensionContext ctx = contextFactory.createContext(scriptClassName);

                // Check permissions before instantiation
                checkPermissions(extensionClass, ctx.getPermissions());

                T instance = extensionClass.getDeclaredConstructor().newInstance();
                instance.initialize(ctx);
                log.debug(
                    "Initialized extension {} for script {}",
                    extensionClass.getName(),
                    scriptClassName);
                return instance;
              } catch (ExtensionException e) {
                throw e;
              } catch (Exception e) {
                throw new ExtensionException(
                    "Failed to create extension " + extensionClass.getName(), e);
              }
            });

    return (T) extension;
  }

  /**
   * Checks that all required permissions are granted.
   *
   * @param extensionClass the extension class
   * @param grantedPermissions the permissions granted to the script
   * @throws ExtensionException if required permissions are missing
   */
  private void checkPermissions(
      Class<? extends Extension> extensionClass, PermissionSet grantedPermissions) {
    ExtensionMeta meta = registry.get(extensionClass);
    if (meta == null) {
      // Extension not discovered via ServiceLoader, extract meta directly
      meta = ExtensionMeta.from(extensionClass);
    }

    PermissionSet required = meta.getRequiredPermissions();
    for (Permission perm : Permission.values()) {
      if (required.has(perm) && !grantedPermissions.has(perm)) {
        throw new ExtensionException(
            "Extension "
                + meta.getName()
                + " requires permission "
                + perm
                + " which is not granted. Use --grant="
                + perm.name()
                + " to enable.");
      }
    }
  }

  /**
   * Releases all extension instances for a script.
   *
   * <p>Called when a BTrace script detaches. Closes all extensions to allow cleanup.
   *
   * @param scriptClassName the script class name
   */
  public void releaseExtensions(String scriptClassName) {
    Map<Class<? extends Extension>, Extension> scriptExtensions = instances.remove(scriptClassName);
    if (scriptExtensions != null) {
      for (Map.Entry<Class<? extends Extension>, Extension> entry : scriptExtensions.entrySet()) {
        try {
          entry.getValue().close();
          log.debug(
              "Closed extension {} for script {}",
              entry.getKey().getName(),
              scriptClassName);
        } catch (Exception e) {
          log.warn(
              "Error closing extension {} for script {}: {}",
              entry.getKey().getName(),
              scriptClassName,
              e.getMessage());
        }
      }
    }
  }

  /** Factory for creating extension contexts. */
  @FunctionalInterface
  public interface ExtensionContextFactory {
    /**
     * Creates an extension context for the specified script.
     *
     * @param scriptClassName the script class name
     * @return new extension context
     */
    ExtensionContext createContext(String scriptClassName);
  }
}
