/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.java.btrace.btrace.statsd.impl;

import com.sun.btrace.util.RingBuffer;

/**
 *
 * @author jbachorik
 */
public class StatsdClientImpl {
    private final RingBuffer rb = new RingBuffer();
    private final RingBuffer.Consumer c = rb.getConsumer();
    private final ThreadLocal<RingBuffer.Producer> p = new ThreadLocal<RingBuffer.Producer>() {
        @Override
        protected RingBuffer.Producer initialValue() {
            return rb.addProducer();
        }
    };


}
