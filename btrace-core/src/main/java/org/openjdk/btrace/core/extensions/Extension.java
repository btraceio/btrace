package org.openjdk.btrace.core.extensions;

/**
 * Base class for all BTrace extensions.
 *
 * <p>Extensions provide additional functionality to BTrace scripts beyond the core BTraceUtils API.
 * They must declare required permissions via {@link RequiresPermission} annotations and implement
 * proper lifecycle management.
 *
 * <p><b>Lifecycle:</b>
 *
 * <ol>
 *   <li>Extension is instantiated via no-arg constructor
 *   <li>{@link #initialize(ExtensionContext)} is called once
 *   <li>Extension methods are called by BTrace script
 *   <li>{@link #close()} is called when script detaches
 * </ol>
 *
 * <p><b>Thread Safety:</b> Extensions must be thread-safe as they may be called concurrently from
 * multiple instrumented threads.
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * &#64;ExtensionDescriptor(name = "example", version = "1.0")
 * &#64;RequiresPermission(Permission.NETWORK)
 * public class ExampleExtension extends Extension {
 *   private Socket socket;
 *
 *   &#64;Override
 *   public void initialize(ExtensionContext ctx) {
 *     super.initialize(ctx);
 *     socket = new Socket(...);
 *   }
 *
 *   public void sendData(String data) {
 *     // Extension functionality
 *   }
 *
 *   &#64;Override
 *   public void close() {
 *     if (socket != null) {
 *       socket.close();
 *     }
 *   }
 * }
 * </pre>
 */
public abstract class Extension implements AutoCloseable {
  private ExtensionContext context;

  /**
   * Initializes this extension with the provided context.
   *
   * <p>Called once after construction, before any extension methods are invoked. Implementations
   * should perform initialization that requires access to the runtime context (e.g., acquiring
   * resources, starting threads).
   *
   * <p>This method is called by the BTrace runtime and should not be invoked directly.
   *
   * @param ctx the extension context
   * @throws ExtensionException if initialization fails
   */
  public void initialize(ExtensionContext ctx) throws ExtensionException {
    this.context = ctx;
  }

  /**
   * Releases resources held by this extension.
   *
   * <p>Called when the BTrace script detaches. Implementations must clean up all resources (close
   * sockets, stop threads, release buffers, etc.).
   *
   * <p>This method is called by the BTrace runtime and should not be invoked directly.
   *
   * <p>Implementations should not throw exceptions. If cleanup fails, errors should be logged but
   * not propagated.
   */
  @Override
  public void close() {
    // Default: no cleanup needed
  }

  /**
   * Returns the extension context.
   *
   * @return the context provided at initialization
   * @throws IllegalStateException if called before {@link #initialize(ExtensionContext)}
   */
  protected final ExtensionContext getContext() {
    if (context == null) {
      throw new IllegalStateException("Extension not initialized");
    }
    return context;
  }
}
