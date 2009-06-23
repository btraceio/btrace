/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.util;

/**
 * A factory class for shared method ids
 * @author Jaroslav Bachorik
 */
public class MethodID {
    final public static String create(String name, String description) {
        return name + "#" + description;
    }
}
