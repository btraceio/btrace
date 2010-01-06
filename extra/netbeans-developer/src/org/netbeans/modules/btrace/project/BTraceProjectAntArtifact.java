/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2010 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 * 
 * Contributor(s):
 * 
 * Portions Copyrighted 2008 Sun Microsystems, Inc.
 */

package org.netbeans.modules.btrace.project;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ant.AntArtifact;
import org.openide.ErrorManager;

/**
 * todo: Create API
 * @author Tomas Zezula
 */
public class BTraceProjectAntArtifact extends AntArtifact {
    
    private final BTraceProject project;
    private final String type;
    private final String locationProperty;  
    private final String targetName;
    private final String cleanTargetName;
    
    /**
     * @see AntProjectHelper#createSimpleAntArtifact
     */
    public BTraceProjectAntArtifact(BTraceProject project, String type, String locationProperty, String targetName, String cleanTargetName) {
        this.project = project;
        this.type = type;
        this.locationProperty = locationProperty;
        this.targetName = targetName;
        this.cleanTargetName = cleanTargetName;
    }
    
    private URI getArtifactLocation0() {
        String locationResolved = project.evaluator().getProperty(locationProperty);
        if (locationResolved == null) {
            return URI.create("file:/UNDEFINED"); // NOI18N
        }
        File locF = new File(locationResolved);
        if (locF.isAbsolute()) {
            return locF.toURI();
        } else {
            // Project-relative path.
            try {
                return new URI(null, null, locationResolved.replace(File.separatorChar, '/'), null);
            } catch (URISyntaxException e) {
                ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, e);
                return URI.create("file:/BROKEN"); // NOI18N
            }
        }
    }
    
    public URI[] getArtifactLocations() {
        return new URI[]{getArtifactLocation0()};
    }
    
    public String getCleanTargetName() {
        return cleanTargetName;
    }
    
    public File getScriptLocation() {
        return this.project.getAntProjectHelper().resolveFile(BTraceProjectUtil.getBuildXmlName(project));        
    }
    
    public String getTargetName() {
        return targetName;
    }
    
    public String getType() {
        return type;
    }
    
    public Project getProject() {
        return project;
    }
    
    public String toString() {
        return "SimpleAntArtifact[helper=" + project.getAntProjectHelper() + ",type=" + type + ",locationProperty=" + locationProperty + // NOI18N
            ",targetName=" + targetName + ",cleanTargetName=" + cleanTargetName + /*",props=" + eval.getProperties() +*/ "]"; // NOI18N
    }
}
