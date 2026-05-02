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
package org.openjdk.btrace.extension.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A lightweight cache for reflective MethodHandles to reduce lookup overhead in provided-style
 * extensions that interact with application types via reflection.
 */
public final class MethodHandleCache {
  private final ConcurrentHashMap<Key, MethodHandle> cache = new ConcurrentHashMap<>();
  private final MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();

  public MethodHandleCache() {}

  public MethodHandle findVirtual(
      Class<?> receiver, String name, Class<?> rtype, Class<?>... ptypes)
      throws NoSuchMethodException, IllegalAccessException {
    MethodType mt = MethodType.methodType(rtype, ptypes);
    Key k = Key.of(receiver, name, mt, false);
    return cache.computeIfAbsent(
        k,
        key -> {
          try {
            return publicLookup.findVirtual(receiver, name, mt);
          } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new LookupRuntimeException(e);
          }
        });
  }

  public MethodHandle findStatic(Class<?> owner, String name, Class<?> rtype, Class<?>... ptypes)
      throws NoSuchMethodException, IllegalAccessException {
    MethodType mt = MethodType.methodType(rtype, ptypes);
    Key k = Key.of(owner, name, mt, true);
    return cache.computeIfAbsent(
        k,
        key -> {
          try {
            return publicLookup.findStatic(owner, name, mt);
          } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new LookupRuntimeException(e);
          }
        });
  }

  public static final class LookupRuntimeException extends RuntimeException {
    public LookupRuntimeException(Throwable cause) {
      super(cause);
    }
  }

  private static final class Key {
    private final Class<?> owner;
    private final String name;
    private final MethodType type;
    private final boolean isStatic;

    private Key(Class<?> owner, String name, MethodType type, boolean isStatic) {
      this.owner = owner;
      this.name = name;
      this.type = type;
      this.isStatic = isStatic;
    }

    static Key of(Class<?> owner, String name, MethodType type, boolean isStatic) {
      return new Key(owner, name, type, isStatic);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Key key = (Key) o;
      return isStatic == key.isStatic
          && Objects.equals(owner, key.owner)
          && Objects.equals(name, key.name)
          && Objects.equals(type, key.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(owner, name, type, isStatic);
    }
  }
}
