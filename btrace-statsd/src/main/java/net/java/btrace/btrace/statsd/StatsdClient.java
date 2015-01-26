/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.java.btrace.btrace.statsd;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.UnknownHostException;

/**
 *
 * @author jbachorik
 */
public class StatsdClient {
    private final DatagramSocket s;
    private final ThreadLocal<DatagramPacket> p = new ThreadLocal<DatagramPacket>() {
        @Override
        protected DatagramPacket initialValue() {
            DatagramPacket dp = new DatagramPacket(new byte[0], 0);
            try {
                dp.setAddress(Inet4Address.getLocalHost());
                dp.setPort(8125);
            } catch (UnknownHostException e) {
                return null;
            }
            return dp;
        }
    };

    public StatsdClient() {
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket();
        } catch (IOException e) {

        }
        s = ds;
    }

    public void gauge(String metric, int value) {
        StringBuilder sb = new StringBuilder(metric);
        sb.append(':').append(value).append("|g");
        byte[] buf = sb.toString().getBytes();
        try {
            DatagramPacket dp = p.get();
            if (dp != null) {
                dp.setData(buf);
                s.send(dp);
            } else {
                throw new IOException();
            }
        } catch (IOException e) {
            // report
        }
    }
}
