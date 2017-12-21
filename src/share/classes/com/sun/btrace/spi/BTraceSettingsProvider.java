/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.spi;

import com.sun.btrace.api.BTraceSettings;

/**
 *
 * @author Jaroslav Bachorik yardus@netbeans.org
 */
public interface BTraceSettingsProvider {
    BTraceSettings getSettings();
}
