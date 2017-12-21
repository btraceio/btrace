/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.spi.impl;

import com.sun.btrace.api.BTraceSettings;
import com.sun.btrace.spi.BTraceSettingsProvider;

/**
 *
 * @author Jaroslav Bachorik yardus@netbeans.org
 */
final public class BTraceSettingsProviderImpl implements BTraceSettingsProvider {
    private final BTraceSettings bs = new BTraceSettings() {

        @Override
        public boolean isDebugMode() {
            return false;
        }

        @Override
        public String getDumpClassPath() {
            return "";
        }

        @Override
        public boolean isDumpClasses() {
            return false;
        }
    };

    @Override
    public BTraceSettings getSettings() {
        return bs;
    }

}
