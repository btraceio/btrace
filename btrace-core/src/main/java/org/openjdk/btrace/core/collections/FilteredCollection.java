package org.openjdk.btrace.core.collections;

import java.lang.invoke.MethodHandle;
import java.util.Collection;
import java.util.Iterator;

final class FilteredCollection<T> implements Collection<T> {
    private final Collection<T> delegate;
    private final MethodHandle filter;

    private int size = -1;

    public FilteredCollection(Collection<T> delegate, MethodHandle filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public int size() {
        if (size == -1) {
            int sizeVal = 0;
            for (T elem : delegate) {
                try {
                    if ((boolean) filter.invoke(elem)) {
                        sizeVal++;
                    }
                } catch (Throwable ignored) {}
            }
            size = sizeVal;
        }
        return size;
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty() || size() <= 0;
    }

    @Override
    public boolean contains(Object o) {
        try {
            if ((boolean) filter.invoke(o)) {
                return delegate.contains(o);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private final Iterator<T> delegated = delegate.iterator();
            private T lastVal = null;

            private void firstMatching() {
                while (delegated.hasNext()) {
                    lastVal = delegated.next();
                    try {
                        if ((boolean) filter.invoke(lastVal)) {
                            return;
                        }
                    } catch (Throwable ignored) {}
                }
                lastVal = null;
            }

            @Override
            public boolean hasNext() {
                if (lastVal == null) {
                    firstMatching();
                }
                return lastVal != null;
            }

            @Override
            public T next() {
                T v = lastVal;
                lastVal = null;
                if (v == null) {
                    firstMatching();
                }
                return v;
            }
        };
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(T t) {
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
    public boolean addAll(Collection<? extends T> c) {
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
