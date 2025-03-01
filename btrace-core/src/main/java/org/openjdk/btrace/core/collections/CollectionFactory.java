package org.openjdk.btrace.core.collections;

import java.lang.invoke.MethodHandle;
import java.util.Collection;

public final class CollectionFactory {
    public static <T> Collection<T> filteredCollection(Collection<T> src, MethodHandle filter) {
        return new FilteredCollection<>(src, filter);
    }

    public static <T, R> Collection<R> mappedCollection(Collection<T> src, MethodHandle mapper) {
        return new MappedCollection<>(src, mapper);
    }
}
