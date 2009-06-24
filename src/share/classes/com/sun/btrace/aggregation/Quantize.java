/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.btrace.aggregation;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregation function that calculates a power-of-two frequency distribution of the values.
 * <p>
 * 
 * @author Christian Glencross
 */
class Quantize implements AggregationValue {

    private static final int ZERO_INDEX = 32;

    // Array of buckets, where each bucket contains a count of the number of
    // occurrences in a certain range determined by a base 2 logarithmic function.
    // For example:
    // buckets[30] counts numbers in the range -4 to -7 inclusive
    // buckets[31] counts -2s and -3s,
    // buckets[33] counts the number of -1s
    // buckets[32] (the mid point of the array) counts the number of zeroes
    // buckets[33] counts the number of 1s
    // buckets[34] counts 2s and 3s,
    // buckets[35] counts numbers in the range 4 to 7
    private AtomicLong[] buckets = new AtomicLong[64];

    public Quantize() {
        super();
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new AtomicLong();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.btrace.aggregation.Aggregation#add(int)
     */
    @Override
    public void add(long data) {
        int pos = getBucketIndex(data);
        buckets[pos].incrementAndGet();
    }

    /**
     * Computes log to base to of the value.
     * 
     * @param the
     *            value for which to calculate the log, must be positive
     */
    private static int logBase2(long value) {
        int pos = 0;
        if (value >= 1 << 16) {
            value >>= 16;
            pos += 16;
        }
        if (value >= 1 << 8) {
            value >>= 8;
            pos += 8;
        }
        if (value >= 1 << 4) {
            value >>= 4;
            pos += 4;
        }
        if (value >= 1 << 2) {
            value >>= 2;
            pos += 2;
        }
        if (value >= 1 << 1) {
            pos += 1;
        }
        return pos;
    }

    /**
     * This implementation of get value returns the label of the bucket containing the largest value. This is used by
     * the {@link Aggregation#truncate(int)} method to sort values in the aggregation when determining which elements to
     * delete.
     */
    @Override
    public long getValue() {
        for (int i = buckets.length - 1; i >= 0; i--) {
            long value = buckets[i].get();
            if (value > 0) {
                return getBucketLabel(i);
            }
        }
        return 0;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.btrace.aggregation.Aggregation#clear()
     */
    @Override
    public void clear() {
        for (int i = 0; i < buckets.length; i++) {
            buckets[i].set(0);
        }
    }

    public HistogramData getData() {
        int minIndex = buckets.length;
        int maxIndex = -1;
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i].get() != 0) {
                minIndex = Math.min(i, minIndex);
                maxIndex = Math.max(i, maxIndex);
            }
        }
        if (minIndex > maxIndex) {
            // No data points
            return null;
        }
        if (maxIndex < buckets.length - 1) {
            maxIndex++;
        }
        if (minIndex > 0) {
            minIndex--;
        }
        int rows = maxIndex - minIndex + 1;
        long[] values = new long[rows];
        long[] counts = new long[rows];
        for (int i = 0; i < rows; i++) {
            values[i] = getBucketLabel(minIndex + i);
            counts[i] = buckets[minIndex + i].get();
        }
        return new HistogramData(values, counts);
    }

    private static int getBucketIndex(long data) {
        if (data == 0) {
            return ZERO_INDEX;
        } else if (data > 0) {
            return ZERO_INDEX + 1 + logBase2(data);
        } else if (data == Integer.MIN_VALUE) {
            // Special case since 0 - MIN_VALUE overflows
            return 0;
        } else {
            return ZERO_INDEX - 1 - logBase2(0 - data);
        }
    }

    private static long getBucketLabel(int index) {
        if (index == ZERO_INDEX) {
            return 0;
        } else if (index == 0) {
            return Long.MIN_VALUE;
        } else if (index > ZERO_INDEX) {
            index = index - ZERO_INDEX - 1;
            return 1 << index;
        } else {
            index = ZERO_INDEX - index - 1;
            return 0 - (1 << index);
        }
    }
}
