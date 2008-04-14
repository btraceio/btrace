/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
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
 */

package org.netbeans.modules.btrace.project;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.io.File;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.util.Mutex;
import org.netbeans.api.project.Sources;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.modules.btrace.project.java.api.common.SourceRoots;
import org.netbeans.modules.btrace.project.ui.customizer.BTraceProjectProperties;
import org.netbeans.spi.project.support.GenericSources;
import org.netbeans.spi.project.support.ant.SourcesHelper;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ChangeSupport;
import org.openide.util.NbBundle;

/**
 * Implementation of {@link Sources} interface for BTraceProject.
 */
public class BTraceSources implements Sources, PropertyChangeListener, ChangeListener  {
    
    private static final String BUILD_DIR_PROP = "${" + BTraceProjectProperties.BUILD_DIR + "}";    //NOI18N
    private static final String DIST_DIR_PROP = "${" + BTraceProjectProperties.DIST_DIR + "}";    //NOI18N

    private final AntProjectHelper helper;
    private final PropertyEvaluator evaluator;
    private final SourceRoots sourceRoots;
    private SourcesHelper sourcesHelper;
    private Sources delegate;
    /**
     * Flag to forbid multiple invocation of {@link SourcesHelper#registerExternalRoots} 
     **/
    private boolean externalRootsRegistered;    
    private final ChangeSupport changeSupport = new ChangeSupport(this);

    BTraceSources(AntProjectHelper helper, PropertyEvaluator evaluator,
                SourceRoots sourceRoots, SourceRoots testRoots) {
        this.helper = helper;
        this.evaluator = evaluator;
        this.sourceRoots = sourceRoots;
        this.sourceRoots.addPropertyChangeListener(this); 
        this.evaluator.addPropertyChangeListener(this);
        initSources(); // have to register external build roots eagerly
    }

    /**
     * Returns an array of SourceGroup of given type. It delegates to {@link SourcesHelper}.
     * This method firstly acquire the {@link ProjectManager#mutex} in read mode then it enters
     * into the synchronized block to ensure that just one instance of the {@link SourcesHelper}
     * is created. These instance is cleared also in the synchronized block by the
     * {@link J2SESources#fireChange} method.
     */
    public SourceGroup[] getSourceGroups(final String type) {
        return ProjectManager.mutex().readAccess(new Mutex.Action<SourceGroup[]>() {
            public SourceGroup[] run() {
                Sources _delegate;
                synchronized (BTraceSources.this) {
                    if (delegate == null) {                    
                        delegate = initSources();
                        delegate.addChangeListener(BTraceSources.this);
                    }
                    _delegate = delegate;
                }
                SourceGroup[] groups = _delegate.getSourceGroups(type);
                if (type.equals(Sources.TYPE_GENERIC)) {
                    FileObject libLoc = getSharedLibraryFolderLocation();
                    if (libLoc != null) {
                        SourceGroup[] grps = new SourceGroup[groups.length + 1];
                        System.arraycopy(groups, 0, grps, 0, groups.length);
                        grps[grps.length - 1] = GenericSources.group(null, libLoc, 
                                "sharedlibraries", // NOI18N
                                NbBundle.getMessage(BTraceSources.class, "LibrarySourceGroup_DisplayName"), 
                                null, null);
                        return grps;
                    }
                }
                return groups;
            }
        });
    }
    
    private FileObject getSharedLibraryFolderLocation() {
        String libLoc = helper.getLibrariesLocation();
        if (libLoc != null) {
            String libLocEval = evaluator.evaluate(libLoc);
            File file = null;
            if (libLocEval != null) {
                file = helper.resolveFile(libLocEval);
            }
            FileObject libLocFO = FileUtil.toFileObject(file);
            if (libLocFO != null) {
                //#126366 this can happen when people checkout the project but not the libraries description 
                //that is located outside the project
                FileObject libLocParent = libLocFO.getParent();
                return libLocParent;
            }
        } 
        return null;
    }
    
    private Sources initSources() {
        this.sourcesHelper = new SourcesHelper(helper, evaluator);   //Safe to pass APH        
        register(sourceRoots);
        this.sourcesHelper.addNonSourceRoot(BUILD_DIR_PROP);
        this.sourcesHelper.addNonSourceRoot(DIST_DIR_PROP);
        externalRootsRegistered = false;
        ProjectManager.mutex().postWriteRequest(new Runnable() {
            public void run() {                
                if (!externalRootsRegistered) {
                    sourcesHelper.registerExternalRoots(FileOwnerQuery.EXTERNAL_ALGORITHM_TRANSIENT);
                    externalRootsRegistered = true;
                }
            }
        });
        return this.sourcesHelper.createSources();
    }

    private void register(SourceRoots roots) {
        String[] propNames = roots.getRootProperties();
        String[] rootNames = roots.getRootNames();
        for (int i = 0; i < propNames.length; i++) {
            String prop = propNames[i];
            String displayName = roots.getRootDisplayName(rootNames[i], prop);
            String loc = "${" + prop + "}"; // NOI18N
            String includes = "${" + BTraceProjectProperties.INCLUDES + "}"; // NOI18N
            String excludes = "${" + BTraceProjectProperties.EXCLUDES + "}"; // NOI18N
            sourcesHelper.addPrincipalSourceRoot(loc, includes, excludes, displayName, null, null); // NOI18N
            sourcesHelper.addTypedSourceRoot(loc, includes, excludes, JavaProjectConstants.SOURCES_TYPE_JAVA, displayName, null, null); // NOI18N
        }
    }

    public void addChangeListener(ChangeListener changeListener) {
        changeSupport.addChangeListener(changeListener);
    }

    public void removeChangeListener(ChangeListener changeListener) {
        changeSupport.removeChangeListener(changeListener);
    }

    private void fireChange() {
        synchronized (this) {
            if (delegate != null) {
                delegate.removeChangeListener(this);
                delegate = null;
            }
        }
        changeSupport.fireChange();
    }

    public void propertyChange(PropertyChangeEvent evt) {
        String propName = evt.getPropertyName();
        if (SourceRoots.PROP_ROOT_PROPERTIES.equals(propName) ||
            BTraceProjectProperties.BUILD_DIR.equals(propName)  ||
            BTraceProjectProperties.DIST_DIR.equals(propName)) {
            this.fireChange();
        }
    }
    
    public void stateChanged (ChangeEvent event) {
        this.fireChange();
    }

}
