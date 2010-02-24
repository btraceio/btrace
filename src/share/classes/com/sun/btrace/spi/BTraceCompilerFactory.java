/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.spi;

import com.sun.btrace.api.BTraceCompiler;
import com.sun.btrace.api.BTraceTask;

/**
 *
 * @author Jaroslav Bachorik <yardus@netbeans.org>
 */
public interface BTraceCompilerFactory {
    BTraceCompiler newCompiler(BTraceTask task);
}
