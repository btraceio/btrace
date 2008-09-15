/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package net.java.btrace.visualvm.impl;

import com.sun.tools.visualvm.core.datasource.DataSource;
import com.sun.tools.visualvm.core.datasource.descriptor.DataSourceDescriptor;
import com.sun.tools.visualvm.core.datasource.descriptor.DataSourceDescriptorFactory;
import com.sun.tools.visualvm.core.model.AbstractModelProvider;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceTaskDescriptorProvider extends AbstractModelProvider<DataSourceDescriptor, DataSource> {
    private static class Singleton {

        private static BTraceTaskDescriptorProvider INSTANCE = new BTraceTaskDescriptorProvider();
    }

    public static BTraceTaskDescriptorProvider sharedInstance() {
        return Singleton.INSTANCE;
    }

    public DataSourceDescriptor createModelFor(DataSource ds) {
        if (ds instanceof BTraceTaskImpl) {
            return createDescriptor((BTraceTaskImpl)ds);
        }
        return null;
    }
    
    private DataSourceDescriptor createDescriptor(BTraceTaskImpl ds) {
        return new BTraceTaskDescriptor(ds);
    }

    public static synchronized void initialize() {
        DataSourceDescriptorFactory.getDefault().registerProvider(Singleton.INSTANCE);
    }

    public static synchronized void shutdown() {
        DataSourceDescriptorFactory.getDefault().unregisterProvider(Singleton.INSTANCE);
    }

}
