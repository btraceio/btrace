/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.util;

import com.sun.btrace.org.objectweb.asm.Type;

/**
 *
 * @author jbachorik
 */
public interface LocalVariableHelper {
    int storeNewLocal(Type type);
}
