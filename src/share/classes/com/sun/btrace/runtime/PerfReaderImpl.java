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

package com.sun.btrace.runtime;

import com.sun.btrace.PerfReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;


public class PerfReaderImpl implements PerfReader {
    private volatile Object thisVm;
    final private Method findByName;
    final private Class integerMonitorClz, longMonitorClz, stringMonitorClz;
    private Object getThisVm() {
        if (thisVm == null) {
            synchronized (this) {
                if (thisVm == null) {
                    try {
                        Class monitoredHostClz = Class.forName("sun.jvmstat.monitor.MonitoredHost");
                        Class vmIdentifierClz = Class.forName("sun.jvmstat.monitor.VmIdentifier");

                        Method getMonitoredHost = monitoredHostClz.getMethod("getMonitoredHost", String.class);
                        Object monitoredHost = getMonitoredHost.invoke(null, "localhost");

                        Constructor constructor = vmIdentifierClz.getDeclaredConstructor(String.class);
                        Object vmIdentifier = constructor.newInstance("0");
                        Method getMonitoredVm = monitoredHostClz.getMethod("getMonitoredVm", vmIdentifier.getClass());
                        thisVm = getMonitoredVm.invoke(monitoredHost, vmIdentifier);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("jvmstat perf counters not available. probably missing tools.jar");
                    }
                }
            }
        }
        return thisVm;
    }

    private Object findByName(String name) throws RuntimeException {
        try {
            return findByName.invoke(getThisVm(), name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public PerfReaderImpl() {
        try {
            Class monitoredVmClz = Class.forName("sun.jvmstat.monitor.MonitoredVm");
            findByName = monitoredVmClz.getMethod("findByName", String.class);
            integerMonitorClz = Class.forName("sun.jvmstat.monitor.IntegerMonitor");
            longMonitorClz = Class.forName("sun.jvmstat.monitor.LongMonitor");
            stringMonitorClz = Class.forName("sun.jvmstat.monitor.StringMonitor");
        } catch (Exception e) {
            throw new IllegalArgumentException("jvmstat perf counters not available. probably missing tools.jar");
        }
    }

    public int perfInt(String name) {
         try { 
            Object mon = findByName(name);
            if (mon == null) {
                throw new IllegalArgumentException("no such counter: " + name);
            }
            if (integerMonitorClz.isAssignableFrom(mon.getClass())) {
                Method m = mon.getClass().getMethod("intValue");
                Object result = m.invoke(mon);
                return ((Integer)result).intValue();
            } else if (longMonitorClz.isAssignableFrom(mon.getClass())) {
                Method m = mon.getClass().getMethod("longValue");
                Object result = m.invoke(mon);
                return ((Long)result).intValue();
            } else {
                throw new IllegalArgumentException(name + " is not an int");
            }
        } catch (Exception me) {
            throw new RuntimeException(me);
        }
    }

    public long perfLong(String name) {
        try {
            Object mon = findByName(name);
            if (mon == null) {
                throw new IllegalArgumentException("no such counter: " + name);
            }
            if (longMonitorClz.isAssignableFrom(mon.getClass())) {
                Method m = mon.getClass().getMethod("longValue");
                Object result = m.invoke(mon);
                return ((Long)result).intValue();
            } else { 
                throw new IllegalArgumentException(name + " is not a long");
            }
        } catch (Exception me) {
            throw new RuntimeException(me);
        }
    }

    public String perfString(String name) {
        try {
            Object mon = findByName(name);
            if (mon == null) {
                throw new IllegalArgumentException("no such counter: " + name);
            }
            if (stringMonitorClz.isAssignableFrom(mon.getClass())) {
                Method m = mon.getClass().getMethod("stringValue");
                Object result = m.invoke(mon);
                System.err.println("result = " + result);
                return (String)result;
            } else {
                throw new IllegalArgumentException(name + " is not a string");
            }
        } catch (Exception me) {
            throw new RuntimeException(me);
        }
    }
}
