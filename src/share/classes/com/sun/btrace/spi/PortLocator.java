/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.spi;

import com.sun.btrace.api.BTraceTask;

/**
 *
 * @author Jaroslav Bachorik yardus@netbeans.org
 */
public interface PortLocator {
    final public static String PORT_PROPERTY = "btrace.port";
    final public static int DEFAULT_PORT = 2020;
    
    int getTaskPort(BTraceTask task);
}
