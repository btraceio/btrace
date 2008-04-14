/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.netbeans.modules.btrace.console;

import java.io.Writer;
import org.openide.windows.IOProvider;

/**
 *
 * @author Jaroslav Bachorik
 */
public class OutputSupport {    
    public static Writer getOut(String scriptName) {
        return IOProvider.getDefault().getIO("BTrace: " + scriptName, false).getOut();
    }
    
    public static Writer getErr(String scriptName) {
        return IOProvider.getDefault().getIO("BTrace: " + scriptName, false).getErr();
    }
}
