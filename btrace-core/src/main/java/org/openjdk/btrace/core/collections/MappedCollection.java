package org.openjdk.btrace.core.collections;

import org.openjdk.btrace.core.BTraceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

final class MappedCollection<T, R> implements Collection<R> {
    private static final Logger log = LoggerFactory.getLogger(MappedCollection.class);

    private final Collection<T> delegate;
    private final MethodHandle mapper;

    public MappedCollection(Collection<T> delegate, MethodHandle mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        for (T elem : delegate) {
            R mapped = null;
            try {
                mapped = (R)mapper.invoke(elem);
            } catch (Throwable ignored) {}
            if (Objects.equals(o, mapped)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<R> iterator() {
        return new Iterator<R>() {
            private final Iterator<T> delegated = delegate.iterator();
            @Override
            public boolean hasNext() {
                return delegated.hasNext();
            }

            @SuppressWarnings("unchecked")
            @Override
            public R next() {
                try {
                    return (R) mapper.invoke(delegated.next());
                } catch (Throwable t) {
                    log.warn("Mapping failed for collection " + BTraceUtils.str(delegate), t);
                }
                return null;
            }
        };
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(R r) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends R> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
}
