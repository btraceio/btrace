package org.openjdk.btrace.extension.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe cache for {@link MethodHandle} lookups.
 *
 * <p>Used by "provided-style" extensions to efficiently invoke methods on application classes that
 * cannot be imported at compile time. Handles are resolved once via {@link
 * MethodHandles#publicLookup()} and cached for subsequent calls.
 *
 * <pre>{@code
 * MethodHandleCache cache = new MethodHandleCache();
 * MethodHandle mh = cache.findVirtual(sparkContextClass, "appName",
 *     MethodType.methodType(String.class));
 * String name = (String) mh.invoke(sparkContext);
 * }</pre>
 */
public final class MethodHandleCache {

  private final ConcurrentMap<Key, MethodHandle> cache = new ConcurrentHashMap<>();

  /** Lookup and cache a virtual (instance) method handle. */
  public MethodHandle findVirtual(Class<?> owner, String name, MethodType type)
      throws NoSuchMethodException, IllegalAccessException {
    Key key = new Key(owner, name, type, false);
    MethodHandle cached = cache.get(key);
    if (cached != null) {
      return cached;
    }
    MethodHandle mh = MethodHandles.publicLookup().findVirtual(owner, name, type);
    MethodHandle existing = cache.putIfAbsent(key, mh);
    return existing != null ? existing : mh;
  }

  /** Lookup and cache a static method handle. */
  public MethodHandle findStatic(Class<?> owner, String name, MethodType type)
      throws NoSuchMethodException, IllegalAccessException {
    Key key = new Key(owner, name, type, true);
    MethodHandle cached = cache.get(key);
    if (cached != null) {
      return cached;
    }
    MethodHandle mh = MethodHandles.publicLookup().findStatic(owner, name, type);
    MethodHandle existing = cache.putIfAbsent(key, mh);
    return existing != null ? existing : mh;
  }

  /** Invalidate all cached handles. Call when classloaders are recycled. */
  public void clear() {
    cache.clear();
  }

  private static final class Key {
    private final Class<?> owner;
    private final String name;
    private final MethodType type;
    private final boolean isStatic;

    Key(Class<?> owner, String name, MethodType type, boolean isStatic) {
      this.owner = owner;
      this.name = name;
      this.type = type;
      this.isStatic = isStatic;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Key that = (Key) o;
      return isStatic == that.isStatic
          && owner.equals(that.owner)
          && name.equals(that.name)
          && type.equals(that.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(owner, name, type, isStatic);
    }
  }
}
