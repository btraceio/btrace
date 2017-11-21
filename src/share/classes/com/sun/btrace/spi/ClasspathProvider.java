/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.spi;

import com.sun.btrace.api.BTraceTask;
import java.util.Collection;
import java.util.Collections;

/**
 *
 * @author Jaroslav Bachorik <yardus@netbeans.org>
 */
public interface ClasspathProvider {
    public static final ClasspathProvider EMPTY = new ClasspathProvider() {

        @Override
        public Collection<String> getClasspath(BTraceTask task) {
            return Collections.EMPTY_LIST;
        }
    };

    Collection<String> getClasspath(BTraceTask task);
}
