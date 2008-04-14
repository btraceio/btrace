/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.netbeans.modules.btrace.project.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.netbeans.modules.btrace.jps.ui.PIDSelectPanel;

/**
 *
 * @author Jaroslav Bachorik
 */
public class SelectPIDTask extends Task {
    private String pidPropertyName = "PID";
    
    public void setPidproperty(String ppn) {
        pidPropertyName = ppn;
    }
    
    @Override
    public void execute() throws BuildException {
        int pid = PIDSelectPanel.selectPID();
        if (pid > -1) {
            getProject().setProperty(pidPropertyName, Integer.toString(pid));
        }
    }

}
