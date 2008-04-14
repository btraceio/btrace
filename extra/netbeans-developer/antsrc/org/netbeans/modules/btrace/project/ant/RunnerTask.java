/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.btrace.project.ant;

import java.io.File;
import java.io.IOException;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.netbeans.modules.btrace.BTraceManager;
import org.netbeans.modules.btrace.ScriptValidator;

/**
 *
 * @author Jaroslav Bachorik
 */
public class RunnerTask extends Task {

    private String scriptName = "";
    private int pid = -1;
    private String ifProperty = "";
    
    public void setScriptname(String sn) {
        scriptName = sn;
    }

    public void setPid(String pid) {
        try {
            this.pid = Integer.parseInt(pid);
        } catch (Exception e) {}
    }
    
    public void setIf(String property) {
        System.out.println("If property: " + property);
        ifProperty = property;
    }

    @Override
    public void execute() throws BuildException {
        if (ifProperty != null && ifProperty.length() > 0) {
            String prop = getProject().getProperty(ifProperty);
            System.out.println(prop != null ? prop : "<NULL>");
            if (prop == null) {
                return;
            }
        }
        System.out.println("Executing");
        if (pid < 0) {
            throw new BuildException("Invalid PID: " + pid);
        }
        try {
            String scriptPath = getProject().getBaseDir().getAbsolutePath() + File.separator + getProject().getProperty("src.dir");
            String classesPath = getProject().getBaseDir().getAbsolutePath() + File.separator + getProject().getProperty("build.classes.dir");
            scriptName = ScriptValidator.sharedInstance().validateScript(scriptName, scriptPath);
            BTraceManager.sharedInstance().deploy(classesPath + File.separator + scriptName, pid);
        } catch (IOException e) {
            throw new BuildException(e);
        }
    }
}
