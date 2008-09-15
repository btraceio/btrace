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

package net.java.btrace.visualvm.options;

import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 *
 * @author Jaroslav Bachorik <jaroslav.bachorik@sun.com>
 */
public class BTraceSettings {
    final static private BTraceSettings INSTANCE = new BTraceSettings();
    
    final private static String DEBUG_PROPERTY = "debugFlag";
    final private static String DUMP_PROPERTY = "dumpFlag" ;
    final private static String DUMP_PATH_PROPERTY = "dumpPath";
    final private static String EDITOR_LAST_SCRIPT = "editor.lastOpened";

    private Preferences prefs = NbPreferences.forModule(BTraceSettings.class);

    final static public BTraceSettings sharedInstance() {
        return INSTANCE;
    }

    private BTraceSettings() {}

    public boolean isDebugMode() {
        return prefs.getBoolean(DEBUG_PROPERTY, false);
    }

    public void setDebugMode(boolean debugMode) {
        prefs.putBoolean(DEBUG_PROPERTY, debugMode);
    }

    public String getDumpClassPath() {
        return prefs.get(DUMP_PATH_PROPERTY, System.getProperty("java.io.tmpdir"));
    }

    public void setDumpClassPath(String dumpClassPath) {
        prefs.put(DUMP_PATH_PROPERTY, dumpClassPath);
    }

    public boolean isDumpClasses() {
        return prefs.getBoolean(DUMP_PROPERTY, false);
    }

    public void setDumpClasses(boolean dumpClasses) {
        prefs.putBoolean(DUMP_PROPERTY, dumpClasses);
    }

    public String getLastScriptPath() {
        return prefs.get(EDITOR_LAST_SCRIPT, null);
    }

    public void setLastScriptPath(String path) {
        prefs.put(EDITOR_LAST_SCRIPT, path);
    }

}
