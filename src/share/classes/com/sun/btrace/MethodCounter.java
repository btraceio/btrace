/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.btrace;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author jbachorik
 */
public class MethodCounter {
    private static AtomicLong[] counters = new AtomicLong[50];
    private static ThreadLocal<Long>[] tsArray = new ThreadLocal[50];
    private static Object[] rLocks = new Object[50];
    private static int[] rates = new int[50];
    private static int[] samplers = new int[50];

    public static synchronized void registerCounter(int methodId, int rate) {
        if (counters.length <= methodId) {
            int newLen = methodId * 2;
            counters = Arrays.copyOf(counters, newLen);
            rLocks = Arrays.copyOf(rLocks, newLen);
            rates = Arrays.copyOf(rates, newLen);
            samplers = Arrays.copyOf(samplers, newLen);
            tsArray = Arrays.copyOf(tsArray, newLen);
        }
        if (counters[methodId] == null) {
            counters[methodId] = new AtomicLong(0);
            rLocks[methodId] = new Object();
            rates[methodId] = rate;
            tsArray[methodId] = new ThreadLocal<Long>() {
                @Override
                protected Long initialValue() {
                    return 0L;
                }
            };
            samplers[methodId] = 0;
        }
    }

    public static boolean hit(int methodId) {
        if (rates[methodId] == 0) {
            return true;
        }
        AtomicLong l = counters[methodId];
        if (l.getAndDecrement() == 0) {
            int inc = ThreadLocalRandom.current().nextInt(0, rates[methodId] * 2) + 1;
            l.addAndGet(inc);
            return true;
        }
        return false;
    }

    public static long hitTimed(int methodId) {
        if (rates[methodId] == 0) {
            long ts = System.nanoTime();
            tsArray[methodId].set(ts);
            return ts;
        }
        AtomicLong l = counters[methodId];
        if (l.getAndDecrement() == 0) {
            long ts = System.nanoTime();
            int inc = ThreadLocalRandom.current().nextInt(0, rates[methodId] * 2) + 1;
            l.addAndGet(inc);
            tsArray[methodId].set(ts);
            return ts;
        }
        return 0L;
    }

    public static long hitTimed(int methodId, long ts) {
        if (ts == 0) {
            return hitTimed(methodId);
        } else {
            tsArray[methodId].set(ts);
            return ts;
        }
    }

    public static boolean hitAdaptive(int methodId) {
        AtomicLong cntr = counters[methodId];
        int rate = rates[methodId];
        if (rate == 0 || cntr.getAndDecrement() == 0) {
            long ts = System.nanoTime();
            ThreadLocal<Long> tsRef = (ThreadLocal<Long>)tsArray[methodId];
            long ts1 = tsRef.get();
            if (ts1 != 0) {
                long diff = ts - ts1;
                if (rate < 5000 && diff < 500) {
                    synchronized(rLocks[methodId]) {
                        rates[methodId] = rate + 1;
                    }
                } else if (rate > 0 && diff > 1500) {
                    synchronized(rLocks[methodId]) {
                        rates[methodId] = rate - 1;
                    }
                }
            }
            tsRef.set(ts);

            if (rates[methodId] != 0) {
                int inc = ThreadLocalRandom.current().nextInt(0, rates[methodId] * 2) + 1;
                cntr.addAndGet(inc);
            }
            return true;
        }
        return false;
    }

    public static long hitTimedAdaptive(int methodId) {
        AtomicLong cntr = counters[methodId];
        int rate = rates[methodId];
        if (rate == 0 || cntr.getAndDecrement() == 0) {
            long ts = System.nanoTime();
            ThreadLocal<Long> tsRef = (ThreadLocal<Long>)tsArray[methodId];
            long ts1 = tsRef.get();
            if (ts1 != 0) {
                long diff = ts - ts1;
                if (rate < 5000 && diff < 500) {
                    synchronized(rLocks[methodId]) {
                        rates[methodId] = rate + 1;
                    }
                } else if (rate > 0 && diff > 1500) {
                    synchronized(rLocks[methodId]) {
                        rates[methodId] = rate - 1;
                    }
                }
            }
            tsRef.set(ts);

            if (rates[methodId] != 0) {
                int inc = ThreadLocalRandom.current().nextInt(0, rates[methodId] * 2) + 1;
                cntr.addAndGet(inc);
            }
            return ts;
        }
        return 0L;
    }

    public static long updateEndTs(int methodId) {
        long ts = System.nanoTime();
        tsArray[methodId].set(ts);
        return ts;
    }
}
