/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.spi.impl;

import com.sun.btrace.api.BTraceTask;
import com.sun.btrace.spi.PortLocator;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Jaroslav Bachorik <yardus@netbeans.org>
 */
final public class PortLocatorImpl implements PortLocator {
    final private static Logger LOGGER = Logger.getLogger(PortLocator.class.getName());

    @Override
    public int getTaskPort(BTraceTask task) {
        VirtualMachine vm = null;
        try {
            vm = VirtualMachine.attach(String.valueOf(task.getPid()));
            String portStr = vm.getSystemProperties().getProperty(PORT_PROPERTY);
            return portStr != null ? Integer.parseInt(portStr) : findFreePort();
        } catch (AttachNotSupportedException ex) {
            Logger.getLogger(PortLocatorImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(PortLocatorImpl.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (vm != null) {
                try {
                    vm.detach();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, null, e);
                }
            }
        }
        return findFreePort();
    }

    private static int findFreePort() {
        ServerSocket server = null;
        int port = 0;
        try {
            server = new ServerSocket(0);
            port = server.getLocalPort();
        } catch (IOException e) {
            port = DEFAULT_PORT;
        } finally {
            try {
                server.close();
            } catch (Exception e) {
                // ignore
            }
        }
        return port;
    }
}
