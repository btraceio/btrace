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

package org.netbeans.modules.btrace.project.classpath;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.api.project.ant.AntArtifact;
import org.netbeans.api.project.libraries.Library;
import org.netbeans.modules.btrace.project.BTraceProject;
import org.netbeans.modules.btrace.project.java.api.common.ant.UpdateHelper;
import org.netbeans.modules.btrace.project.ui.customizer.BTraceProjectProperties;
import org.netbeans.spi.java.project.classpath.ProjectClassPathModifierImplementation;
import org.netbeans.spi.project.libraries.support.LibrariesSupport;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.project.support.ant.EditableProperties;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.netbeans.spi.project.support.ant.ReferenceHelper;
import org.openide.ErrorManager;
import org.openide.util.Mutex;
import org.openide.util.MutexException;

/**
 *@author Tomas Zezula
 *
 */
public class BTraceProjectClassPathModifier extends ProjectClassPathModifierImplementation {
    
    public static final int ADD = 1;
    public static final int REMOVE = 2;
    
    private final BTraceProject project;
    private final UpdateHelper helper;
    private final ReferenceHelper refHelper;
    private final PropertyEvaluator eval;    
    private final ClassPathSupport cs;    
    
    private static final Logger LOG = Logger.getLogger(BTraceProjectClassPathModifier.class.getName());
    
    /** Creates a new instance of BTraceProjectClassPathModifier */
    public BTraceProjectClassPathModifier(final BTraceProject project, final UpdateHelper helper, final PropertyEvaluator eval, final ReferenceHelper refHelper) {
        assert project != null;
        assert helper != null;
        assert eval != null;
        assert refHelper != null;
        this.project = project;
        this.helper = helper;
        this.eval = eval;
        this.refHelper = refHelper;        
        this.cs = new ClassPathSupport( eval, refHelper, helper.getAntProjectHelper(), helper,
                                        BTraceProjectProperties.WELL_KNOWN_PATHS, 
                                        BTraceProjectProperties.ANT_ARTIFACT_PREFIX );
    }
    
    protected SourceGroup[] getExtensibleSourceGroups() {
        Sources s = project.getLookup().lookup(Sources.class);
        assert s != null;
        return s.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA);
    }
    
    protected String[] getExtensibleClassPathTypes (SourceGroup sg) {
        return new String[] {
            ClassPath.COMPILE,
            ClassPath.EXECUTE
        };
    }

    protected boolean removeRoots(final URL[] classPathRoots, final SourceGroup sourceGroup, final String type) throws IOException {
        return handleRoots (convertURLsToURIs(classPathRoots), getClassPathProperty(sourceGroup, type), REMOVE, true);
    }

    @Override
    protected boolean removeRoots(final URI[] classPathRoots, final SourceGroup sourceGroup, final String type) throws IOException {
        return handleRoots (classPathRoots, getClassPathProperty(sourceGroup, type), REMOVE, true);
    }

    protected boolean addRoots (final URL[] classPathRoots, final SourceGroup sourceGroup, final String type) throws IOException {        
        return handleRoots (convertURLsToURIs(classPathRoots), getClassPathProperty(sourceGroup, type), ADD, true);
    }
    
    @Override
    protected boolean addRoots (final URI[] classPathRoots, final SourceGroup sourceGroup, final String type) throws IOException {        
        return handleRoots (classPathRoots, getClassPathProperty(sourceGroup, type), ADD, true);
    }
    
    public boolean handleRoots (final URI[] classPathRoots, final String classPathProperty, final int operation, final boolean performHeuristics) throws IOException {
        assert classPathRoots != null : "The classPathRoots cannot be null";      //NOI18N        
        assert classPathProperty != null;
        try {
            return ProjectManager.mutex().writeAccess(
                    new Mutex.ExceptionAction<Boolean>() {
                        public Boolean run() throws Exception {
                            EditableProperties props = helper.getProperties (AntProjectHelper.PROJECT_PROPERTIES_PATH);
                            String raw = props.getProperty(classPathProperty);                            
                            List<ClassPathSupport.Item> resources = cs.itemsList(raw);
                            boolean changed = false;
                            for (int i=0; i< classPathRoots.length; i++) {
                                String f;
                                if (performHeuristics) {
                                    f = BTraceProjectClassPathModifier.this.performSharabilityHeuristics(classPathRoots[i], project.getAntProjectHelper());
                                } else {
                                    URI toAdd = LibrariesSupport.getArchiveFile(classPathRoots[i]);
                                    if (toAdd == null) {
                                        toAdd = classPathRoots[i];
                                    }
                                    f =  LibrariesSupport.convertURIToFilePath(toAdd);
                                }
                                ClassPathSupport.Item item = ClassPathSupport.Item.create( f, null );
                                if (operation == ADD && !resources.contains(item)) {
                                    resources.add (item);
                                    changed = true;
                                }                            
                                else if (operation == REMOVE) {
                                    if (resources.remove(item)) {
                                        changed = true;
                                    }
                                    else {
                                        for (Iterator<ClassPathSupport.Item> it = resources.iterator(); it.hasNext();) {
                                            ClassPathSupport.Item _r = it.next();
                                            if (_r.isBroken() && _r.getType() == ClassPathSupport.Item.TYPE_JAR && f.equals(_r.getFilePath())) {
                                                it.remove();
                                                changed = true;
                                            }
                                        }
                                    }
                                }
                            }
                            if (changed) {
                                String itemRefs[] = cs.encodeToStrings( resources.iterator() );
                                props = helper.getProperties (AntProjectHelper.PROJECT_PROPERTIES_PATH);  //PathParser may change the EditableProperties
                                props.setProperty(classPathProperty, itemRefs);
                                helper.putProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH, props);
                                ProjectManager.getDefault().saveProject(project);
                                return true;
                            }
                            return false;
                        }
                    }
            
            );
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            else {
                Exception t = new IOException ();
                throw (IOException) ErrorManager.getDefault().annotate(t,e);
            }
        }
    }

    protected boolean removeAntArtifacts(final AntArtifact[] artifacts, final URI[] artifactElements, final SourceGroup sourceGroup, final String type) throws IOException {
        return handleAntArtifacts (artifacts, artifactElements, getClassPathProperty(sourceGroup, type), REMOVE);
    }

    protected boolean addAntArtifacts(final AntArtifact[] artifacts, final URI[] artifactElements, final SourceGroup sourceGroup, final String type) throws IOException {
        return handleAntArtifacts (artifacts, artifactElements, getClassPathProperty(sourceGroup, type), ADD);
    }
    
    public boolean handleAntArtifacts (final AntArtifact[] artifacts, final URI[] artifactElements, final String classPathProperty, final int operation) throws IOException {
        assert artifacts != null : "Artifacts cannot be null";    //NOI18N
        assert artifactElements != null : "ArtifactElements cannot be null";  //NOI18N
        assert artifacts.length == artifactElements.length : "Each artifact has to have corresponding artifactElement"; //NOI18N
        assert classPathProperty != null;
        try {
            return ProjectManager.mutex().writeAccess(
                    new Mutex.ExceptionAction<Boolean>() {
                        public Boolean run() throws Exception {
                            EditableProperties props = helper.getProperties (AntProjectHelper.PROJECT_PROPERTIES_PATH);
                            String raw = props.getProperty (classPathProperty);
                            List<ClassPathSupport.Item> resources = cs.itemsList(raw);
                            boolean changed = false;
                            for (int i=0; i<artifacts.length; i++) {
                                assert artifacts[i] != null;
                                assert artifactElements[i] != null;
                                ClassPathSupport.Item item = ClassPathSupport.Item.create( artifacts[i], artifactElements[i], null );
                                if (operation == ADD && !resources.contains(item)) {
                                    resources.add (item);
                                    changed = true;
                                }
                                else if (operation == REMOVE && resources.contains(item)) {
                                    resources.remove(item);
                                    changed = true;
                                }
                            }                            
                            if (changed) {
                                String itemRefs[] = cs.encodeToStrings( resources.iterator() );                                
                                props = helper.getProperties (AntProjectHelper.PROJECT_PROPERTIES_PATH);    //Reread the properties, PathParser changes them
                                props.setProperty (classPathProperty, itemRefs);
                                helper.putProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH, props);
                                ProjectManager.getDefault().saveProject(project);
                                return true;
                            }
                            return false;
                        }
                    }
            );
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            else {
                Exception t = new IOException ();
                throw (IOException) ErrorManager.getDefault().annotate(t,e);
            }
        }
    }
    
    
    protected boolean removeLibraries(final Library[] libraries, final SourceGroup sourceGroup, final String type) throws IOException {
        return handleLibraries (libraries, getClassPathProperty(sourceGroup, type), REMOVE);
    }

    protected boolean addLibraries(final Library[] libraries, final SourceGroup sourceGroup, final String type) throws IOException {
        return handleLibraries (libraries, getClassPathProperty(sourceGroup, type), ADD);
    }
    
    public boolean handleLibraries (final Library[] libraries, final String classPathProperty, final int operation) throws IOException {
        assert libraries != null : "Libraries cannot be null";  //NOI18N
        assert classPathProperty != null;
        try {
            return ProjectManager.mutex().writeAccess(
                    new Mutex.ExceptionAction<Boolean>() {
                        public Boolean run() throws IOException {
                            EditableProperties props = helper.getProperties (AntProjectHelper.PROJECT_PROPERTIES_PATH);
                            String raw = props.getProperty(classPathProperty);
                            List<ClassPathSupport.Item> resources = cs.itemsList(raw);
                            List<ClassPathSupport.Item> changed = new ArrayList<ClassPathSupport.Item>(libraries.length);
                            for (int i=0; i< libraries.length; i++) {
                                assert libraries[i] != null;
                                Library lib = libraries[i];
                                if (project.getAntProjectHelper().isSharableProject()) {
                                    if (lib.getManager().getLocation() == null) {

                                        LOG.log(Level.FINE, "Client is adding global library ["+lib+
                                                "] to sharable project.", new Exception());
                                        // For backward compatibility just copy the library to shared one.
                                        Library l = refHelper.getProjectLibraryManager().getLibrary(lib.getName());
                                        if (l != null) {
                                            lib = l;
                                        } else {
                                            lib = refHelper.copyLibrary(lib);
                                        }
                                    } else if (!lib.getManager().getLocation().equals(refHelper.getProjectLibraryManager().getLocation())) {
                                        throw new UnsupportedOperationException("Adding library '"+lib.getName()+ // NOI18N
                                            "' from '"+lib.getManager().getLocation()+"' to project '"+project.getProjectDirectory()+ // NOI18N
                                            "' is not supported because project libraries are defined in '"+refHelper.getProjectLibraryManager().getLocation()+"'"); // NOI18N
                                    }
                                }
                                ClassPathSupport.Item item = ClassPathSupport.Item.create( lib, null );
                                if (operation == ADD && !resources.contains(item)) {
                                    resources.add (item);                                
                                    changed.add(item);
                                }
                                else if (operation == REMOVE && resources.contains(item)) {
                                    resources.remove(item);
                                    changed.add(item);
                                }
                            }
                            if (!changed.isEmpty()) {
                                String itemRefs[] = cs.encodeToStrings( resources.iterator() );                                
                                props = helper.getProperties (AntProjectHelper.PROJECT_PROPERTIES_PATH);    //PathParser may change the EditableProperties                                
                                props.setProperty(classPathProperty, itemRefs);                                
                                helper.putProperties(AntProjectHelper.PROJECT_PROPERTIES_PATH, props);
                                ProjectManager.getDefault().saveProject(project);
                                return true;
                            }
                            return false;
                        }
                    }
            );
        } catch (MutexException e) {
            throw (IOException) e.getException();
        }
    }
    
    private String getClassPathProperty (final SourceGroup sg, final String type) {
        assert sg != null : "SourceGroup cannot be null";  //NOI18N
        assert type != null : "Type cannot be null";  //NOI18N
        final String classPathProperty = project.getClassPathProvider().getPropertyName (sg, type);
        if (classPathProperty == null) {
            throw new UnsupportedOperationException ("Modification of [" + sg.getRootFolder().getPath() +", " + type + "] is not supported"); //NOI8N
        }
        return classPathProperty;
    }
}
