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

import java.awt.Dialog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.tools.ant.module.api.support.ActionUtils;
import org.netbeans.api.fileinfo.NonRecursiveFolder;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.java.queries.UnitTestForSourceQuery;
import org.netbeans.api.java.source.ui.ScanDialog;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.support.ant.AntProjectHelper;
import org.netbeans.spi.project.support.ant.EditableProperties;
import org.netbeans.spi.project.ui.support.DefaultProjectOperations;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.ErrorManager;
import org.openide.NotifyDescriptor;
import org.openide.execution.ExecutorTask;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Task;
import org.openide.util.TaskListener;

import org.netbeans.modules.btrace.project.java.api.common.ant.UpdateHelper;
import org.netbeans.modules.btrace.project.ui.customizer.BTraceProjectProperties;

/** Action provider of the J2SE project. This is the place where to do
 * strange things to J2SE actions. E.g. compile-single.
 */
class BTraceActionProvider implements ActionProvider {

    // Commands available from J2SE project
    private static final String[] supportedActions = {
        COMMAND_BUILD,
        COMMAND_CLEAN,
        COMMAND_REBUILD,
        COMMAND_COMPILE_SINGLE,
        COMMAND_RUN,
        COMMAND_RUN_SINGLE,
        COMMAND_DELETE,
        COMMAND_COPY,
        COMMAND_MOVE,
        COMMAND_RENAME
    };


    private static final String[] platformSensitiveActions = {
        COMMAND_BUILD,
        COMMAND_REBUILD,
        COMMAND_COMPILE_SINGLE,
        COMMAND_RUN,
        COMMAND_RUN_SINGLE
    };

    // Project
    final BTraceProject project;

    // Ant project helper of the project
    private UpdateHelper updateHelper;


    /** Map from commands to ant targets */
    Map<String,String[]> commands;

    /**Set of commands which are affected by background scanning*/
    final Set<String> bkgScanSensitiveActions;

    /** Set of Java source files (as relative path from source root) known to have been modified. See issue #104508. */
    private Set<String> dirty = null;

    private Sources src;
    private List<FileObject> roots;
    
    // Used only from unit tests to suppress detection of top level classes. If value
    // is different from null it will be returned instead.
    String unitTestingSupport_fixClasses;

    public BTraceActionProvider(BTraceProject project, UpdateHelper updateHelper) {

        commands = new HashMap<String,String[]>();
        // treated specially: COMMAND_{,RE}BUILD
        commands.put(COMMAND_CLEAN, new String[] {"clean"}); // NOI18N
        commands.put(COMMAND_COMPILE_SINGLE, new String[] {"compile-single"}); // NOI18N
        commands.put(COMMAND_RUN, new String[] {"run"}); // NOI18N
        commands.put(COMMAND_RUN_SINGLE, new String[] {"run-single"}); // NOI18N

        this.bkgScanSensitiveActions = new HashSet<String>(Arrays.asList(
            COMMAND_RUN,
            COMMAND_RUN_SINGLE
        ));

        this.updateHelper = updateHelper;
        this.project = project;        
    }
    
    private final FileChangeListener modificationListener = new FileChangeAdapter() {
        public @Override void fileChanged(FileEvent fe) {
            modification(fe.getFile());
        }
        public @Override void fileDataCreated(FileEvent fe) {
            modification(fe.getFile());
        }
    };

    private final ChangeListener sourcesChangeListener = new ChangeListener() {

        public void stateChanged(ChangeEvent e) {
            synchronized (BTraceActionProvider.this) {
                BTraceActionProvider.this.roots = null;
            }
        }
    };
    
    
    void startFSListener () {
        //Listener has to be started when the project's lookup is initialized
        try {
            FileSystem fs = project.getProjectDirectory().getFileSystem();
            // XXX would be more efficient to only listen while DO_DEPEND=false (though this is the default)
            fs.addFileChangeListener(FileUtil.weakFileChangeListener(modificationListener, fs));
        } catch (FileStateInvalidException x) {
            Exceptions.printStackTrace(x);
        }
    }

    private void modification(FileObject f) {
        final Iterable <? extends FileObject> roots = getRoots();
        assert roots != null;
        for (FileObject root : roots) {
            String path = FileUtil.getRelativePath(root, f);
            if (path != null) {
                synchronized (this) {
                    if (dirty != null) {
                        dirty.add(path);
                    }
                }
                break;
            }
        }
    }

    private Iterable <? extends FileObject> getRoots () {
        Sources _src = null;
        synchronized (this) {
            if (this.roots != null) {
                return this.roots;
            }
            if (this.src == null) {
                this.src = this.project.getLookup().lookup(Sources.class);
                this.src.addChangeListener (sourcesChangeListener);
            }
            _src = this.src;
        }
        assert _src != null;
        final SourceGroup[] sgs = _src.getSourceGroups (JavaProjectConstants.SOURCES_TYPE_JAVA);
        final List<FileObject> _roots = new ArrayList<FileObject>(sgs.length);
        for (SourceGroup sg : sgs) {
            final FileObject root = sg.getRootFolder();
            if (UnitTestForSourceQuery.findSources(root).length == 0) {
                _roots.add (root);
            }
        }
        synchronized (this) {
            if (this.roots == null) {
                this.roots = _roots;
            }
            return this.roots;
        }
    }

    private FileObject findBuildXml() {
        return BTraceProjectUtil.getBuildXml(project);
    }

    public String[] getSupportedActions() {
        return supportedActions;
    }

    public void invokeAction( final String command, final Lookup context ) throws IllegalArgumentException {
        if (COMMAND_DELETE.equals(command)) {
            DefaultProjectOperations.performDefaultDeleteOperation(project);
            return ;
        }

        if (COMMAND_COPY.equals(command)) {
            DefaultProjectOperations.performDefaultCopyOperation(project);
            return ;
        }

        if (COMMAND_MOVE.equals(command)) {
            DefaultProjectOperations.performDefaultMoveOperation(project);
            return ;
        }

        if (COMMAND_RENAME.equals(command)) {
            DefaultProjectOperations.performDefaultRenameOperation(project, null);
            return ;
        }

        final Runnable action = new Runnable () {
            public void run () {
                Properties p = new Properties();
                String[] targetNames;

                targetNames = getTargetNames(command, context, p);
                if (targetNames == null) {
                    return;
                }
                if (targetNames.length == 0) {
                    targetNames = null;
                }
                if (p.keySet().size() == 0) {
                    p = null;
                }
                try {
                    FileObject buildFo = findBuildXml();
                    if (buildFo == null || !buildFo.isValid()) {
                        //The build.xml was deleted after the isActionEnabled was called
                        NotifyDescriptor nd = new NotifyDescriptor.Message(NbBundle.getMessage(BTraceActionProvider.class,
                                "LBL_No_Build_XML_Found"), NotifyDescriptor.WARNING_MESSAGE);
                        DialogDisplayer.getDefault().notify(nd);
                    }
                    else {
                        ActionUtils.runTarget(buildFo, targetNames, p).addTaskListener(new TaskListener() {
                            public void taskFinished(Task task) {
                                if (((ExecutorTask) task).result() != 0) {
                                    synchronized (BTraceActionProvider.this) {
                                        // #120843: if a build fails, disable dirty-list optimization.
                                        dirty = null;
                                    }
                                }
                            }
                        });
                    }
                }
                catch (IOException e) {
                    ErrorManager.getDefault().notify(e);
                }
            }
        };

        if (this.bkgScanSensitiveActions.contains(command)) {
            ScanDialog.runWhenScanFinished(action, NbBundle.getMessage (BTraceActionProvider.class,"ACTION_"+command));   //NOI18N
        }
        else {
            action.run();
        }
    }

    /**
     * @return array of targets or null to stop execution; can return empty array
     */
    /*private*/ String[] getTargetNames(String command, Lookup context, Properties p) throws IllegalArgumentException {
        if (Arrays.asList(platformSensitiveActions).contains(command)) {
            final String activePlatformId = this.project.evaluator().getProperty("platform.active");  //NOI18N
            if (BTraceProjectUtil.getActivePlatform (activePlatformId) == null) {
                showPlatformWarning ();
                return null;
            }
        }
        String[] targetNames = new String[0];
        if ( command.equals( COMMAND_COMPILE_SINGLE ) ) {
            FileObject[] sourceRoots = project.getSourceRoots().getRoots();
            FileObject[] files = findSourcesAndPackages( context, sourceRoots);
            boolean recursive = (context.lookup(NonRecursiveFolder.class) == null);
            if (files != null) {
                p.setProperty("javac.includes", ActionUtils.antIncludesList(files, getRoot(sourceRoots,files[0]), recursive)); // NOI18N
                targetNames = commands.get(command);
            }
        }
        else if (command.equals (COMMAND_RUN)) {
            String config = null;
            String path;
            if (config == null || config.length() == 0) {
                path = AntProjectHelper.PROJECT_PROPERTIES_PATH;
            } else {
                // Set main class for a particular config only.
                path = "nbproject/configs/" + config + ".properties"; // NOI18N
            }
            EditableProperties ep = updateHelper.getProperties(path);


            targetNames = commands.get(command);
            if (targetNames == null) {
                throw new IllegalArgumentException(command);
            }
            prepareDirtyList(p, false);
        } else if (command.equals (COMMAND_RUN_SINGLE)) {
            FileObject file = findSources(context)[0];
            String clazz = FileUtil.getRelativePath(getRoot(project.getSourceRoots().getRoots(),file), file);
            p.setProperty("javac.includes", clazz); // NOI18N
            // Convert foo/FooTest.java -> foo.FooTest
            if (clazz.endsWith(".java")) { // NOI18N
                clazz = clazz.substring(0, clazz.length() - 5);
            }
            clazz = clazz.replace('/','.');
                
            p.setProperty("run.class", clazz); // NOI18N
            targetNames = commands.get(COMMAND_RUN_SINGLE);
        } else {
            String[] targets = null; //targetsFromConfig.get(command);
            targetNames = (targets != null) ? targets : commands.get(command);
            if (targetNames == null) {
                String buildTarget = "false".equalsIgnoreCase(project.evaluator().getProperty(BTraceProjectProperties.DO_JAR)) ? "compile" : "jar"; // NOI18N
                if (command.equals(COMMAND_BUILD)) {
                    targetNames = new String[] {buildTarget};
                    prepareDirtyList(p, true);
                } else if (command.equals(COMMAND_REBUILD)) {
                    targetNames = new String[] {"clean", buildTarget}; // NOI18N
                } else {
                    throw new IllegalArgumentException(command);
                }
            }
            if (COMMAND_CLEAN.equals(command)) {
                //After clean, rebuild all
                dirty = null;
            }
        }
        return targetNames;
    }
    private void prepareDirtyList(Properties p, boolean isExplicitBuildTarget) {
        String doDepend = project.evaluator().getProperty(BTraceProjectProperties.DO_DEPEND);
        synchronized (this) {
            if (dirty == null) {
                // #119777: the first time, build everything.
                dirty = new TreeSet<String>();
                return;
            }
            for (DataObject d : DataObject.getRegistry().getModified()) {
                // Treat files modified in memory as dirty as well.
                // (If you make an edit and press F11, the save event happens *after* Ant is launched.)
                modification(d.getPrimaryFile());
            }
            if (!"true".equalsIgnoreCase(doDepend) && !(isExplicitBuildTarget && dirty.isEmpty())) { // NOI18N
                // #104508: if not using <depend>, try to compile just those files known to have been touched since the last build.
                // (In case there are none such, yet the user invoked build anyway, probably they know what they are doing.)
                if (dirty.isEmpty()) {
                    // includes="" apparently is ignored.
                    dirty.add("nothing whatsoever"); // NOI18N
                }
                StringBuilder dirtyList = new StringBuilder();
                for (String f : dirty) {
                    if (dirtyList.length() > 0) {
                        dirtyList.append(',');
                    }
                    dirtyList.append(f);
                }
                p.setProperty(BTraceProjectProperties.INCLUDES, dirtyList.toString());
            }
            dirty.clear();
        }
    }

    public boolean isActionEnabled( String command, Lookup context ) {
        FileObject buildXml = findBuildXml();
        if (  buildXml == null || !buildXml.isValid()) {
            return false;
        }
        if ( command.equals( COMMAND_COMPILE_SINGLE ) ) {
            return findSourcesAndPackages( context, project.getSourceRoots().getRoots()) != null;
        }
        else if (command.equals(COMMAND_RUN_SINGLE)) {
            FileObject fos[] = findSources(context);
            return fos != null && fos.length == 1;
        } else {
            // other actions are global
            return true;
        }
    }



    // Private methods -----------------------------------------------------

    
    /** Find selected sources, the sources has to be under single source root,
     *  @param context the lookup in which files should be found
     */
    private FileObject[] findSources(Lookup context) {
        FileObject[] srcPath = project.getSourceRoots().getRoots();
        for (int i=0; i< srcPath.length; i++) {
            FileObject[] files = ActionUtils.findSelectedFiles(context, srcPath[i], ".java", true); // NOI18N
            if (files != null) {
                return files;
            }
        }
        return null;
    }

    private FileObject[] findSourcesAndPackages (Lookup context, FileObject srcDir) {
        if (srcDir != null) {
            FileObject[] files = ActionUtils.findSelectedFiles(context, srcDir, null, true); // NOI18N
            //Check if files are either packages of java files
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    if (!files[i].isFolder() && !"java".equals(files[i].getExt())) {
                        return null;
                    }
                }
            }
            return files;
        } else {
            return null;
        }
    }

    private FileObject[] findSourcesAndPackages (Lookup context, FileObject[] srcRoots) {
        for (int i=0; i<srcRoots.length; i++) {
            FileObject[] result = findSourcesAndPackages(context, srcRoots[i]);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private FileObject getRoot (FileObject[] roots, FileObject file) {
        assert file != null : "File can't be null";   //NOI18N
        FileObject srcDir = null;
        for (int i=0; i< roots.length; i++) {
            assert roots[i] != null : "Source Path Root can't be null"; //NOI18N
            if (FileUtil.isParentOf(roots[i],file) || roots[i].equals(file)) {
                srcDir = roots[i];
                break;
            }
        }
        return srcDir;
    }

    private void showPlatformWarning () {
        final JButton closeOption = new JButton (NbBundle.getMessage(BTraceActionProvider.class, "CTL_BrokenPlatform_Close"));
        closeOption.getAccessibleContext().setAccessibleDescription(NbBundle.getMessage(BTraceActionProvider.class, "AD_BrokenPlatform_Close"));
        final ProjectInformation pi = project.getLookup().lookup(ProjectInformation.class);
        final String projectDisplayName = pi == null ?
            NbBundle.getMessage (BTraceActionProvider.class,"TEXT_BrokenPlatform_UnknownProjectName")
            : pi.getDisplayName();
        final DialogDescriptor dd = new DialogDescriptor(
            NbBundle.getMessage(BTraceActionProvider.class, "TEXT_BrokenPlatform", projectDisplayName),
            NbBundle.getMessage(BTraceActionProvider.class, "MSG_BrokenPlatform_Title"),
            true,
            new Object[] {closeOption},
            closeOption,
            DialogDescriptor.DEFAULT_ALIGN,
            null,
            null);
        dd.setMessageType(DialogDescriptor.WARNING_MESSAGE);
        final Dialog dlg = DialogDisplayer.getDefault().createDialog(dd);
        dlg.setVisible(true);
    }
}
