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
import org.HdrHistogram.SingleWriterRecorder;
import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.BTrace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class provides utility methods for creating and managing HDR Histogram recorders.
 * HDR Histogram recorders are useful for recording values over time and then analyzing
 * them later. They are particularly useful for measuring latency.
 *
 * @author Your Name
 */
@BTrace
public class Recorders {
    private static final Map<String, SingleWriterRecorder> recorders = new ConcurrentHashMap<>();

    /**
     * Creates a new HDR Histogram recorder with the specified name, highest trackable value,
     * and number of significant value digits.
     *
     * @param name the name of the recorder
     * @param highestTrackableValue the highest value to be tracked by the recorder
     * @param numberOfSignificantValueDigits the number of significant decimal digits to which the recorder will maintain value resolution and separation
     * @return the created recorder
     */
    public static SingleWriterRecorder newRecorder(String name, long highestTrackableValue, int numberOfSignificantValueDigits) {
        SingleWriterRecorder recorder = new SingleWriterRecorder(highestTrackableValue, numberOfSignificantValueDigits);
        recorders.put(name, recorder);
        return recorder;
    }

    /**
     * Creates a new HDR Histogram recorder with the specified name, using default values for
     * highest trackable value (3,600,000,000,000) and number of significant value digits (3).
     *
     * @param name the name of the recorder
     * @return the created recorder
     */
    public static SingleWriterRecorder newRecorder(String name) {
        return newRecorder(name, 3600000000000L, 3);
    }

    /**
     * Gets an existing recorder by name.
     *
     * @param name the name of the recorder
     * @return the recorder, or null if no recorder with the specified name exists
     */
    public static SingleWriterRecorder getRecorder(String name) {
        return recorders.get(name);
    }

    /**
     * Records a value in the specified recorder.
     *
     * @param recorder the recorder to record the value in
     * @param value the value to record
     */
    public static void recordValue(SingleWriterRecorder recorder, long value) {
        if (recorder != null) {
            recorder.recordValue(value);
        }
    }

    /**
     * Records a value in the recorder with the specified name.
     *
     * @param name the name of the recorder
     * @param value the value to record
     */
    public static void recordValue(String name, long value) {
        SingleWriterRecorder recorder = getRecorder(name);
        if (recorder != null) {
            recorder.recordValue(value);
        }
    }

    /**
     * Gets the current histogram from the recorder with the specified name.
     * This method gets a snapshot of the current histogram without resetting it.
     *
     * @param name the name of the recorder
     * @return the current histogram, or null if no recorder with the specified name exists
     */
    public static Histogram getHistogram(String name) {
        SingleWriterRecorder recorder = getRecorder(name);
        if (recorder != null) {
            return recorder.getIntervalHistogram();
        }
        return null;
    }

    /**
     * Gets the current histogram from the recorder with the specified name and resets the recorder.
     * This method gets a snapshot of the current histogram and resets the recorder to start fresh.
     *
     * @param name the name of the recorder
     * @return the current histogram, or null if no recorder with the specified name exists
     */
    public static Histogram getHistogramAndReset(String name) {
        SingleWriterRecorder recorder = getRecorder(name);
        if (recorder != null) {
            return recorder.getIntervalHistogram();
        }
        return null;
    }

    /**
     * Prints the current histogram from the recorder with the specified name.
     *
     * @param name the name of the recorder
     */
    public static void printHistogram(String name) {
        Histogram histogram = getHistogram(name);
        if (histogram != null) {
            BTraceUtils.println("=== Recorder Histogram: " + name + " ===");
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
     * Resets the recorder with the specified name.
     *
     * @param name the name of the recorder
     */
    public static void resetRecorder(String name) {
        SingleWriterRecorder recorder = getRecorder(name);
        if (recorder != null) {
            recorder.reset();
        }
    }
}