package org.openjdk.btrace.extension;

import org.openjdk.btrace.extension.impl.EmbeddedExtensionRepository;
import org.openjdk.btrace.extension.impl.ExtensionConfig;
import org.openjdk.btrace.extension.impl.ExtensionLoaderImpl;
import org.openjdk.btrace.extension.impl.FileSystemExtensionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ExtensionLoader {
    private static Logger log = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final AtomicReference<ExtensionLoaderImpl> implRef = new AtomicReference<>(null);

    public static ExtensionLoader instance() {
        return implRef.get();
    }

    public static ExtensionLoader initialize(String btraceHome, ClassLoader parentClassLoader, Instrumentation instrumentation) {
        // Create extension repositories in priority order
        List<ExtensionRepository> repositories = new ArrayList<>();

        // 0. Embedded extensions (lowest priority — filesystem extensions override)
        repositories.add(new EmbeddedExtensionRepository(parentClassLoader));

        if (btraceHome != null) {
            // 1. Built-in extensions
            Path builtinExtPath = new File(btraceHome, "extensions").toPath();
            repositories.add(
                    new FileSystemExtensionRepository(builtinExtPath, ExtensionRepository.Priority.BUILTIN));

            // 2. User extensions (~/.btrace/extensions/)
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                Path userExtPath = new File(userHome, ".btrace/extensions").toPath();
                repositories.add(
                        new FileSystemExtensionRepository(userExtPath, ExtensionRepository.Priority.USER));
            }

            // 3. Environment variable BTRACE_EXT_PATH
            String extPath = System.getenv("BTRACE_EXT_PATH");
            if (extPath != null && !extPath.isEmpty()) {
                String[] paths = extPath.split(File.pathSeparator);
                for (String path : paths) {
                    repositories.add(
                            new FileSystemExtensionRepository(
                                    new File(path).toPath(), ExtensionRepository.Priority.ENVIRONMENT));
                }
            }
        } else {
            log.info("BTRACE_HOME not set — running in embedded-only extension mode");
        }

        // Load extension configuration
        if (log.isDebugEnabled()) {
            log.debug("Loading extension config from: {}", btraceHome != null ? btraceHome : "(embedded-only)");
        }
        ExtensionConfig config = ExtensionConfig.load(btraceHome);

        ExtensionLoader instance =  new ExtensionLoaderImpl(repositories, parentClassLoader, config, instrumentation);
        // Register service declaration resolver for bytecode-level validation.
        // Bytecode verifier (instr) uses this to check @Injected fields without loading classes.
        // Runtime reflection in Client#validateDeclaredServices complements this by checking
        // actual loadability and module/classloader access in the target JVM.
        ServiceDeclarationRegistry.setResolver(
                fqcn -> instance.findExtensionForService(fqcn) != null);

        // Discover all available extensions
        if (log.isDebugEnabled()) {
            log.debug("Discovering extensions...");
        }
        instance.discoverExtensions();

        if (log.isDebugEnabled()) {
            log.debug("Extension system initialized with {} available extension(s)",
                    instance.getAvailableExtensions().size());
        }
        log.info("Extension system initialized with {} available extension(s)",
                instance.getAvailableExtensions().size());
        return instance;
    }

    /**
     * Find an extension that provides the given service class.
     *
     * @param serviceClassName fully qualified service class name
     * @return extension descriptor, or null if not found
     */
    public abstract ExtensionDescriptorDTO findExtensionForService(String serviceClassName);

    /**
     * Discover all available extensions from configured repositories.
     * This should be called once during agent startup.
     *
     * @return list of discovered extensions
     */
    public abstract List<ExtensionDescriptorDTO> discoverExtensions();

    /**
     * Get all available (discovered) extensions.
     *
     * @return collection of available extension descriptors
     */
    public abstract Collection<ExtensionDescriptorDTO> getAvailableExtensions();

    /**
     * Ensure the extension API JAR is appended to the bootstrap classpath without
     * attempting to load the implementation JAR. This enables BTrace to generate
     * shims against the API when implementation use is blocked (e.g., permissions).
     *
     * @param descriptor the extension descriptor
     * @return true if the API JAR was found and appended; false otherwise
     */
    public abstract boolean ensureApiOnBootstrap(ExtensionDescriptorDTO descriptor);

    /**
     * Load an extension and make its classes available.
     * This is idempotent - loading an already-loaded extension is a no-op.
     *
     * @param descriptor extension to load
     * @return true if loaded successfully, false otherwise
     */
    public abstract boolean load(ExtensionDescriptorDTO descriptor);
}
