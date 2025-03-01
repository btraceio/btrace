package org.openjdk.btrace.core;

import java.lang.invoke.MethodHandle;
import java.util.Collection;

public class CollectionUtils {
    public static <T> void forEach(MethodHandle consumer, Collection<T> collection) {
        BTraceRuntime.forEach(collection, consumer);
    }

    public static <T> void forEach(MethodHandle consumer, T[] data) {
        BTraceRuntime.forEach(data, consumer);
    }

    public static <T> Collection<T> filter(MethodHandle test, Collection<T> collection) {
        return BTraceRuntime.filter(collection, test);
    }

    public static <T, R> Collection<R> map(MethodHandle mapper, Collection<T> collection) {
        return BTraceRuntime.map(collection, mapper);
    }

    public static <T, R> R reduce(MethodHandle reducer, Collection<T> collection, R identity) {
        return BTraceRuntime.reduce(collection, reducer, identity);
    }
}
