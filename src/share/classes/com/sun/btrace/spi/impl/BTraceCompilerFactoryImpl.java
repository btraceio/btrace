/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.spi.impl;

import com.sun.btrace.api.BTraceCompiler;
import com.sun.btrace.api.BTraceTask;
import com.sun.btrace.spi.BTraceCompilerFactory;
import com.sun.btrace.spi.BaseBTraceCompiler;

/**
 *
 * @author Jaroslav Bachorik yardus@netbeans.org
 */
final public class BTraceCompilerFactoryImpl implements BTraceCompilerFactory {
    @Override
    public BTraceCompiler newCompiler(final BTraceTask task) {
        return new BaseBTraceCompiler(task);
    }
}
