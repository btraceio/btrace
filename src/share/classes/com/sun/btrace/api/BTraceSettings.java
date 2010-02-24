/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.api;

/**
 *
 * @author Jaroslav Bachorik <yardus@netbeans.org>
 */
abstract public class BTraceSettings {
    abstract public boolean isDebugMode();
    abstract public String getDumpClassPath();
    abstract public boolean isDumpClasses();
}
