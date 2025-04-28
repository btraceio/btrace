/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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
package org.openjdk.btrace.extensions.hdrhistogram;

import org.HdrHistogram.Histogram;
import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.BTrace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class provides utility methods for creating and managing HDR Histograms.
 * HDR Histogram is a high dynamic range histogram implementation that supports
 * recording and analyzing sampled data value counts across a configurable integer
 * value range with configurable value precision.
 *
 * @author Your Name
 */
@BTrace
public class Histograms {
    private static final Map<String, Histogram> histograms = new ConcurrentHashMap<>();

    /**
     * Creates a new HDR Histogram with the specified name, highest trackable value,
     * and number of significant value digits.
     *
     * @param name the name of the histogram
     * @param highestTrackableValue the highest value to be tracked by the histogram
     * @param numberOfSignificantValueDigits the number of significant decimal digits to which the histogram will maintain value resolution and separation
     * @return the created histogram
     */
    public static Histogram newHistogram(String name, long highestTrackableValue, int numberOfSignificantValueDigits) {
        Histogram histogram = new Histogram(highestTrackableValue, numberOfSignificantValueDigits);
        histograms.put(name, histogram);
        return histogram;
    }

    /**
     * Creates a new HDR Histogram with the specified name, using default values for
     * highest trackable value (3,600,000,000,000) and number of significant value digits (3).
     *
     * @param name the name of the histogram
     * @return the created histogram
     */
    public static Histogram newHistogram(String name) {
        return newHistogram(name, 3600000000000L, 3);
    }

    /**
     * Gets an existing histogram by name.
     *
     * @param name the name of the histogram
     * @return the histogram, or null if no histogram with the specified name exists
     */
    public static Histogram getHistogram(String name) {
        return histograms.get(name);
    }

    /**
     * Records a value in the specified histogram.
     *
     * @param histogram the histogram to record the value in
     * @param value the value to record
     */
    public static void recordValue(Histogram histogram, long value) {
        if (histogram != null) {
            histogram.recordValue(value);
        }
    }

    /**
     * Records a value in the histogram with the specified name.
     *
     * @param name the name of the histogram
     * @param value the value to record
     */
    public static void recordValue(String name, long value) {
        Histogram histogram = getHistogram(name);
        if (histogram != null) {
            histogram.recordValue(value);
        }
    }

    /**
     * Prints the histogram with the specified name.
     *
     * @param name the name of the histogram
     */
    public static void printHistogram(String name) {
        Histogram histogram = getHistogram(name);
        if (histogram != null) {
            BTraceUtils.println("=== Histogram: " + name + " ===");
            BTraceUtils.println("Min value: " + histogram.getMinValue());
            BTraceUtils.println("Max value: " + histogram.getMaxValue());
            BTraceUtils.println("Mean value: " + histogram.getMean());
            BTraceUtils.println("StdDeviation: " + histogram.getStdDeviation());
            BTraceUtils.println("50th percentile: " + histogram.getValueAtPercentile(50.0));
            BTraceUtils.println("90th percentile: " + histogram.getValueAtPercentile(90.0));
            BTraceUtils.println("99th percentile: " + histogram.getValueAtPercentile(99.0));
            BTraceUtils.println("99.9th percentile: " + histogram.getValueAtPercentile(99.9));
            BTraceUtils.println("99.99th percentile: " + histogram.getValueAtPercentile(99.99));
            BTraceUtils.println("99.999th percentile: " + histogram.getValueAtPercentile(99.999));
            BTraceUtils.println("Total count: " + histogram.getTotalCount());
        }
    }

    /**
     * Resets the histogram with the specified name.
     *
     * @param name the name of the histogram
     */
    public static void resetHistogram(String name) {
        Histogram histogram = getHistogram(name);
        if (histogram != null) {
            histogram.reset();
        }
    }
}