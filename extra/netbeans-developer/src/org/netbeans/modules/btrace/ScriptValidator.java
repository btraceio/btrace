/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.netbeans.modules.btrace;

import java.io.File;
import org.netbeans.modules.btrace.ui.ScriptChooser;

/**
 *
 * @author Jaroslav Bachorik
 */
public class ScriptValidator {
    private final static ScriptValidator INSTANCE = new ScriptValidator();
    
    private ScriptValidator() {}
    
    public static ScriptValidator sharedInstance() {
        return INSTANCE;
    }
    
    public String validateScript(String scriptName, String scriptBaseDir) {
        String scriptPath = scriptName.replace(".class", "|class").replace('.', File.separatorChar).replace("|class", ".class");
        if (!new File(scriptBaseDir + File.separator + scriptPath.replace(".class", ".java")).exists()) {
            scriptName = ScriptChooser.selectScript(scriptBaseDir);
            scriptName = scriptName.replace('.', File.separatorChar);
            scriptPath = scriptName + ".class";
        }
        return scriptPath;
    }
}
