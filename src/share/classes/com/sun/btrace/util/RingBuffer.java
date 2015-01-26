/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.btrace.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Jaroslav Bachorik
 */
public final class RingBuffer<T extends RingBuffer.Value> {
    public abstract static class Value {
        abstract public <V extends Value> V duplicate();
    }
    public static final class Entry<T extends Value> {
        public T value;
    }

    public static final class Consumer<T extends RingBuffer.Value> {
        private final RingBuffer<T> r;
        private long readPosition = 0, safePosition = -1;
        private volatile long erasePosition;

        private Consumer(RingBuffer r) {
            this.r = r;
            erasePosition = r.BUFFER_SIZE - 1;
        }

        public T consume() {
            if (readPosition > safePosition) {
                long maxPos = Long.MIN_VALUE;
                for(Producer<T> p : r.producers) {
                    if (p.committed < p.allocated) {
                        if (p.allocated <= readPosition) return null;
                    }
                    maxPos = Math.max(maxPos, p.committed);
                }

                if (maxPos < readPosition) return null;
                safePosition = maxPos;
            }

            Entry e = r.getEntry(readPosition);
            T v = e.value.duplicate();

            e.value = null;

            synchronized(this) {
                erasePosition = (readPosition++) + r.BUFFER_SIZE;
                this.notifyAll();
            }
            return v;
        }
    }

    public static final class Producer<T extends Value> {
        private final RingBuffer<T> rb;
        private volatile long committed = -1;
        private volatile long allocated = -1;

        private Producer(RingBuffer<T> rb) {
            this.rb = rb;
        }

        public void write(T value) throws InterruptedException {
            long slot = rb.posCounter.getAndIncrement();
            allocated = slot;

            if (rb.consumer.erasePosition < slot) {
                synchronized(rb.consumer) {
                    while (rb.consumer.erasePosition < slot) {
                        rb.consumer.wait();
                    }
                }
            }

            Entry e = rb.getEntry(slot);
            e.value = value;

            committed = slot;
        }
    }

    private final int BUFFER_SIZE; // must be 2^n
    private final int BUFFER_MODULO; // BUFFER_SIZE - 1

    private final AtomicLong posCounter = new AtomicLong(0);

    public final Entry<T>[] ringBuffer;

    private final Consumer<T> consumer;
    private final List<Producer<T>> producers = new CopyOnWriteArrayList<Producer<T>>();

    public RingBuffer(int bitSize) {
        if (bitSize > 30) {
            throw new IllegalArgumentException("Maximal buffer size is 2^30");
        }
        if (bitSize < 1) {
            throw new IllegalArgumentException("Minimal buffer size is 2^1");
        }
        int size = 1;
        BUFFER_SIZE = size << bitSize;
        BUFFER_MODULO = BUFFER_SIZE - 1;
        ringBuffer = new Entry[BUFFER_SIZE];
        for(int i=0;i<BUFFER_SIZE;i++) {
            ringBuffer[i] = new Entry();
        }
        consumer = new Consumer<T>(this);
    }

    public RingBuffer() {
        this(16);
    }

    public Consumer<T> getConsumer() {
        return consumer;
    }

    public Producer<T> addProducer() {
        Producer<T> p = new Producer<T>(this);
        producers.add(p);
        return p;
    }

    private Entry<T> getEntry(long pos) {
        return ringBuffer[(int)(pos & BUFFER_MODULO)];
    }
}
