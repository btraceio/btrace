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
import java.net.URISyntaxException;
import sun.jvmstat.monitor.IntegerMonitor;
import sun.jvmstat.monitor.LongMonitor;
import sun.jvmstat.monitor.Monitor;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.MonitoredVm;
import sun.jvmstat.monitor.MonitorException;
import sun.jvmstat.monitor.StringMonitor;
import sun.jvmstat.monitor.VmIdentifier;

public class PerfReaderImpl implements PerfReader {
    private volatile MonitoredVm thisVm;

    private MonitoredVm getThisVm() {
        if (thisVm == null) {
            synchronized (this) {
                if (thisVm == null) {
                    try {
                        MonitoredHost localHost = MonitoredHost.getMonitoredHost("localhost");
                        VmIdentifier vmIdent = new VmIdentifier("0");
                        thisVm = localHost.getMonitoredVm(vmIdent);
                    } catch (MonitorException me) {
                        throw new IllegalArgumentException("jvmstat perf counters not available: " + me);
                    } catch (URISyntaxException use) {
                        throw new IllegalArgumentException("jvmstat perf counters not available: " + use);
                    }
                }
            }
        }
        return thisVm;
    }

    private Monitor findByName(String name) {
        try {
            return getThisVm().findByName(name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int perfInt(String name) {
        Monitor mon = findByName(name);
        if (mon == null) {
            throw new IllegalArgumentException("no such counter: " + name);
        }
        if (mon instanceof IntegerMonitor) {
            return ((IntegerMonitor)mon).intValue();
        } else if (mon instanceof LongMonitor) {
            return (int) ((LongMonitor)mon).longValue();
        } else {
            throw new IllegalArgumentException(name + " is not an int");
        }
    }

    public long perfLong(String name) {
        Monitor mon = findByName(name);
        if (mon == null) {
            throw new IllegalArgumentException("no such counter: " + name);
        }
        if (mon instanceof LongMonitor) {
            return ((LongMonitor)mon).longValue();
        } else { 
            throw new IllegalArgumentException(name + " is not a long");
        }
    }

    public String perfString(String name) {
        Monitor mon = findByName(name);
        if (mon == null) {
            throw new IllegalArgumentException("no such counter: " + name);
        }
        if (mon instanceof StringMonitor) {
            return ((StringMonitor)mon).stringValue();
        } else {
            throw new IllegalArgumentException(name + " is not a string");
        }
    }
}
