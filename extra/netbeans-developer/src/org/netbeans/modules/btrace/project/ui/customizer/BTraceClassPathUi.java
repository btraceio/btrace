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

package org.netbeans.modules.btrace.project.ui.customizer;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.BeanInfo;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.ButtonModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.ant.FileChooser;
import org.netbeans.api.project.libraries.Library;
import org.netbeans.api.project.libraries.LibraryChooser;
import org.netbeans.api.project.libraries.LibraryManager;
import org.netbeans.modules.btrace.project.BTraceProject;
import org.netbeans.modules.btrace.project.classpath.ClassPathSupport;
import org.netbeans.modules.btrace.project.classpath.ClassPathSupport.Item;
import org.netbeans.modules.btrace.project.ui.FoldersListSettings;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.Repository;
import org.openide.loaders.DataFolder;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;

/** Classes containing code speciic for handling UI of J2SE project classpath 
 *
 * @author Petr Hrebejk
 */
public class BTraceClassPathUi {
    
    private BTraceClassPathUi() {}
           
    // Innerclasses ------------------------------------------------------------
            
    /** Renderer which can be used to render the classpath in lists
     */    
    public static class ClassPathListCellRenderer extends DefaultListCellRenderer {
        
        private static final Pattern FOREIGN_PLAIN_FILE_REFERENCE = Pattern.compile("\\$\\{file\\.reference\\.([^${}]+)\\}"); // NOI18N
        private static final Pattern UNKNOWN_FILE_REFERENCE = Pattern.compile("\\$\\{([^${}]+)\\}"); // NOI18N
        
        private static String RESOURCE_ICON_JAR = "org/netbeans/modules/btrace/project/ui/resources/jar.gif"; //NOI18N
        private static String RESOURCE_ICON_LIBRARY = "org/netbeans/modules/btrace/project/ui/resources/libraries.gif"; //NOI18N
        private static String RESOURCE_ICON_ARTIFACT = "org/netbeans/modules/btrace/project/ui/resources/projectDependencies.gif"; //NOI18N
        private static String RESOURCE_ICON_CLASSPATH = "org/netbeans/modules/btrace/project/ui/resources/referencedClasspath.gif"; //NOI18N
        private static String RESOURCE_ICON_BROKEN_BADGE = "org/netbeans/modules/btrace/project/ui/resources/brokenProjectBadge.gif"; //NOI18N
        private static String RESOURCE_ICON_SOURCE_BADGE = "org/netbeans/modules/btrace/project/ui/resources/jarSourceBadge.png"; //NOI18N
        private static String RESOURCE_ICON_JAVADOC_BADGE = "org/netbeans/modules/btrace/project/ui/resources/jarJavadocBadge.png"; //NOI18N
        
        
        private static ImageIcon ICON_JAR = new ImageIcon( Utilities.loadImage( RESOURCE_ICON_JAR ) );
        private static ImageIcon ICON_FOLDER = null; 
        private static ImageIcon ICON_LIBRARY = new ImageIcon( Utilities.loadImage( RESOURCE_ICON_LIBRARY ) );
        private static ImageIcon ICON_ARTIFACT  = new ImageIcon( Utilities.loadImage( RESOURCE_ICON_ARTIFACT ) );
        private static ImageIcon ICON_CLASSPATH  = new ImageIcon( Utilities.loadImage( RESOURCE_ICON_CLASSPATH ) );
        private static ImageIcon ICON_BROKEN_BADGE  = new ImageIcon( Utilities.loadImage( RESOURCE_ICON_BROKEN_BADGE ) );
        private static ImageIcon ICON_JAVADOC_BADGE  = new ImageIcon( Utilities.loadImage( RESOURCE_ICON_JAVADOC_BADGE ) );
        private static ImageIcon ICON_SOURCE_BADGE  = new ImageIcon( Utilities.loadImage( RESOURCE_ICON_SOURCE_BADGE ) );
        
        private static ImageIcon ICON_BROKEN_JAR;
        private static ImageIcon ICON_BROKEN_LIBRARY;
        private static ImageIcon ICON_BROKEN_ARTIFACT;
                
        private PropertyEvaluator evaluator;
        private FileObject projectFolder;
        
        // Contains well known paths in the BTraceProject
        private static final Map WELL_KNOWN_PATHS_NAMES = new HashMap();
        static {
            WELL_KNOWN_PATHS_NAMES.put( BTraceProjectProperties.JAVAC_CLASSPATH, NbBundle.getMessage( BTraceProjectProperties.class, "LBL_JavacClasspath_DisplayName" ) );
            WELL_KNOWN_PATHS_NAMES.put( BTraceProjectProperties.JAVAC_TEST_CLASSPATH, NbBundle.getMessage( BTraceProjectProperties.class,"LBL_JavacTestClasspath_DisplayName") );
            WELL_KNOWN_PATHS_NAMES.put( BTraceProjectProperties.RUN_CLASSPATH, NbBundle.getMessage( BTraceProjectProperties.class, "LBL_RunClasspath_DisplayName" ) );
            WELL_KNOWN_PATHS_NAMES.put( BTraceProjectProperties.RUN_TEST_CLASSPATH, NbBundle.getMessage( BTraceProjectProperties.class, "LBL_RunTestClasspath_DisplayName" ) );
            WELL_KNOWN_PATHS_NAMES.put( BTraceProjectProperties.BUILD_CLASSES_DIR, NbBundle.getMessage( BTraceProjectProperties.class, "LBL_BuildClassesDir_DisplayName" ) );            
            WELL_KNOWN_PATHS_NAMES.put( BTraceProjectProperties.BUILD_TEST_CLASSES_DIR, NbBundle.getMessage (BTraceProjectProperties.class,"LBL_BuildTestClassesDir_DisplayName") );
        };
                
        public ClassPathListCellRenderer( PropertyEvaluator evaluator, FileObject projectFolder) {
            super();
            this.evaluator = evaluator;
            this.projectFolder = projectFolder;
        }
        
        @Override
        public Component getListCellRendererComponent( JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            
            ClassPathSupport.Item item = (ClassPathSupport.Item)value;
            
            super.getListCellRendererComponent( list, getDisplayName( item ), index, isSelected, cellHasFocus );
            setIcon( getIcon( item ) );
            setToolTipText( getToolTipText( item ) );
            
            return this;
        }
        
        
        private String getDisplayName( ClassPathSupport.Item item ) {
            
            switch ( item.getType() ) {
                
                case ClassPathSupport.Item.TYPE_LIBRARY:
                    if ( item.isBroken() ) {
                        return NbBundle.getMessage( BTraceClassPathUi.class, "LBL_MISSING_LIBRARY", getLibraryName( item )  );
                    }
                    else { 
                        return item.getLibrary().getDisplayName();
                    }
                case ClassPathSupport.Item.TYPE_CLASSPATH:
                    String name = (String)WELL_KNOWN_PATHS_NAMES.get( ClassPathSupport.getAntPropertyName( item.getReference() ) );
                    return name == null ? item.getReference() : name;
                case ClassPathSupport.Item.TYPE_ARTIFACT:
                    if ( item.isBroken() ) {
                        return NbBundle.getMessage( BTraceClassPathUi.class, "LBL_MISSING_PROJECT", getProjectName( item )  );
                    }
                    else {
                        Project p = item.getArtifact().getProject();
                        ProjectInformation pi = ProjectUtils.getInformation(p);
                        String projectName = pi.getDisplayName();
                        return MessageFormat.format (NbBundle.getMessage(BTraceClassPathUi.class,"MSG_ProjectArtifactFormat"), new Object[] {
                            projectName,
                            item.getArtifactURI().toString()
                        });
                    }
                case ClassPathSupport.Item.TYPE_JAR:
                    if ( item.isBroken() ) {
                        return NbBundle.getMessage( BTraceClassPathUi.class, "LBL_MISSING_FILE", getFileRefName( item )  );
                    }
                    else {
                        return item.getFilePath();
                    }
            }
            
            return item.getReference(); // XXX            
        }
        
        static Icon getIcon( ClassPathSupport.Item item ) {
            
            switch ( item.getType() ) {
                
                case ClassPathSupport.Item.TYPE_LIBRARY:
                    if ( item.isBroken() ) {
                        if ( ICON_BROKEN_LIBRARY == null ) {
                            ICON_BROKEN_LIBRARY = new ImageIcon( Utilities.mergeImages( ICON_LIBRARY.getImage(), ICON_BROKEN_BADGE.getImage(), 7, 7 ) );
                        }
                        return ICON_BROKEN_LIBRARY;
                    }
                    else {
                        return ICON_LIBRARY;
                    }
                case ClassPathSupport.Item.TYPE_ARTIFACT:
                    if ( item.isBroken() ) {
                        if ( ICON_BROKEN_ARTIFACT == null ) {
                            ICON_BROKEN_ARTIFACT = new ImageIcon( Utilities.mergeImages( ICON_ARTIFACT.getImage(), ICON_BROKEN_BADGE.getImage(), 7, 7 ) );
                        }
                        return ICON_BROKEN_ARTIFACT;
                    }
                    else {
                        Project p = item.getArtifact().getProject();
                        if (p != null) {
                            ProjectInformation pi = ProjectUtils.getInformation(p);
                            return pi.getIcon();
                        }
                        return ICON_ARTIFACT;
                    }
                case ClassPathSupport.Item.TYPE_JAR:
                    if ( item.isBroken() ) {
                        if ( ICON_BROKEN_JAR == null ) {
                            ICON_BROKEN_JAR = new ImageIcon( Utilities.mergeImages( ICON_JAR.getImage(), ICON_BROKEN_BADGE.getImage(), 7, 7 ) );
                        }
                        return ICON_BROKEN_JAR;
                    }
                    else {
                        String file = item.getFilePath();
//                        File f = PropertyUtils.resolveFile(FileUtil.toFile(item.projectFolder), file);
                        
                        ImageIcon icn = /*TODO f.isDirectory()*/ false ? getFolderIcon() : ICON_JAR;
                        if (item.getSourceFilePath() != null) {
                            icn =  new ImageIcon( Utilities.mergeImages( icn.getImage(), ICON_SOURCE_BADGE.getImage(), 8, 8 ));
                        }
                        if (item.getJavadocFilePath() != null) {
                            icn =  new ImageIcon( Utilities.mergeImages( icn.getImage(), ICON_JAVADOC_BADGE.getImage(), 8, 0 ));
                        }
                        return icn;
                    }
                case ClassPathSupport.Item.TYPE_CLASSPATH:
                    return ICON_CLASSPATH;
                
            }
            
            return null; // XXX 
        }
        
        private String getToolTipText( ClassPathSupport.Item item ) {
            if ( item.isBroken() && 
                 ( item.getType() == ClassPathSupport.Item.TYPE_JAR || 
                   item.getType() == ClassPathSupport.Item.TYPE_ARTIFACT )  ) {
                return evaluator.evaluate( item.getReference() );
            }
            switch ( item.getType() ) {
                case ClassPathSupport.Item.TYPE_JAR:
                    String path = item.getFilePath();
                    File f = new File(path);
                    if (!f.isAbsolute()) {
                        f = PropertyUtils.resolveFile(FileUtil.toFile(projectFolder), path);
                        return f.getAbsolutePath();
                    }
            }
            
            return null;
        }
        
        private static ImageIcon getFolderIcon() {
        
            if ( ICON_FOLDER == null ) {
                FileObject root = Repository.getDefault().getDefaultFileSystem().getRoot();
                DataFolder dataFolder = DataFolder.findFolder( root );
                ICON_FOLDER = new ImageIcon( dataFolder.getNodeDelegate().getIcon( BeanInfo.ICON_COLOR_16x16 ) );            
            }

            return ICON_FOLDER;   
        }
        
        private String getProjectName( ClassPathSupport.Item item ) {
            String ID = item.getReference();
            // something in the form of "${reference.project-name.id}"
            return ID.substring(12, ID.indexOf(".", 12)); // NOI18N
        }

        private String getLibraryName( ClassPathSupport.Item item ) {
            String ID = item.getReference();
            if (ID == null) {
                if (item.getLibrary() != null) {
                    return item.getLibrary().getName();
                }
                //TODO HUH? happens when adding new library, then changing
                // the library location to something that doesn't have a reference yet.
                // why are there items without reference upfront?
                return "XXX";
            }
            // something in the form of "${libs.junit.classpath}"
            return ID.substring(7, ID.indexOf(".classpath")); // NOI18N
        }

        private String getFileRefName( ClassPathSupport.Item item ) {
            String ID = item.getReference();        
            // something in the form of "${file.reference.smth.jar}"
            Matcher m = FOREIGN_PLAIN_FILE_REFERENCE.matcher(ID);
            if (m.matches()) {
                return m.group(1);
            }
            m = UNKNOWN_FILE_REFERENCE.matcher(ID);
            if (m.matches()) {
                return m.group(1);
            }
            return ID;
        }


        
    }
    
    
    public static class EditMediator implements ActionListener, ListSelectionListener {
                
        private final BTraceProject project;
        private final JList list;
        private final DefaultListModel listModel;
        private final ListSelectionModel selectionModel;
        private final ButtonModel addJar;
        private final ButtonModel addLibrary;
        private final ButtonModel addAntArtifact;
        private final ButtonModel remove;
        private final ButtonModel moveUp;
        private final ButtonModel moveDown;
        private final ButtonModel edit;
        private Document libraryPath;
                    
        private EditMediator( BTraceProject project,
                             JList list,
                             DefaultListModel listModel, 
                             ButtonModel addJar,
                             ButtonModel addLibrary, 
                             ButtonModel addAntArtifact,
                             ButtonModel remove, 
                             ButtonModel moveUp,
                             ButtonModel moveDown,
                             ButtonModel edit,
                             Document libPath) {
                             
            // Remember all buttons
            
            this.list = list;
            
            if ( !( list.getModel() instanceof DefaultListModel ) ) {
                throw new IllegalArgumentException( "The list's model has to be of class DefaultListModel" ); // NOI18N
            }
            
            this.listModel = (DefaultListModel)list.getModel();
            this.selectionModel = list.getSelectionModel();
            
            this.addJar = addJar;
            this.addLibrary = addLibrary;
            this.addAntArtifact = addAntArtifact;
            this.remove = remove;
            this.moveUp = moveUp;
            this.moveDown = moveDown;
            this.edit = edit;

            this.project = project;
            this.libraryPath = libPath;
        }

        public static void register(BTraceProject project,
                                    JList list,
                                    DefaultListModel listModel, 
                                    ButtonModel addJar,
                                    ButtonModel addLibrary, 
                                    ButtonModel addAntArtifact,
                                    ButtonModel remove, 
                                    ButtonModel moveUp,
                                    ButtonModel moveDown,
                                    ButtonModel edit, 
                                    Document libraryPath) {    
            
            EditMediator em = new EditMediator( project, 
                                                list,
                                                listModel, 
                                                addJar,
                                                addLibrary, 
                                                addAntArtifact,
                                                remove,    
                                                moveUp,
                                                moveDown, 
                                                edit,
                                                libraryPath);
            
            // Register the listener on all buttons
            addJar.addActionListener( em ); 
            addLibrary.addActionListener( em );
            addAntArtifact.addActionListener( em );
            remove.addActionListener( em );
            moveUp.addActionListener( em );
            moveDown.addActionListener( em );
            edit.addActionListener( em );
            // On list selection
            em.selectionModel.addListSelectionListener( em );
            // Set the initial state of the buttons
            em.valueChanged( null );
        }
    
        // Implementation of ActionListener ------------------------------------
        
        /** Handles button events
         */        
        public void actionPerformed( ActionEvent e ) {
            
            Object source = e.getSource();
            
            if ( source == addJar ) { 
                // Let user search for the Jar file
                FileChooser chooser = new FileChooser(project.getAntProjectHelper(), true);
                FileUtil.preventFileChooserSymlinkTraversal(chooser, null);
                chooser.setFileSelectionMode( JFileChooser.FILES_AND_DIRECTORIES );
                chooser.setMultiSelectionEnabled( true );
                chooser.setDialogTitle( NbBundle.getMessage( BTraceClassPathUi.class, "LBL_AddJar_DialogTitle"  )  ); // NOI18N
                //#61789 on old macosx (jdk 1.4.1) these two method need to be called in this order.
                chooser.setAcceptAllFileFilterUsed( false );
                chooser.setFileFilter( new SimpleFileFilter( 
                    NbBundle.getMessage( BTraceClassPathUi.class, "LBL_ZipJarFolderFilter"  ),                  // NOI18N
                    new String[] {"ZIP","JAR"}  )  );                                                                 // NOI18N 
                File curDir = FoldersListSettings.getDefault().getLastUsedClassPathFolder(); 
                chooser.setCurrentDirectory (curDir);
                int option = chooser.showOpenDialog( SwingUtilities.getWindowAncestor( list ) ); // Sow the chooser
                
                if ( option == JFileChooser.APPROVE_OPTION ) {
                    String[] files;
                    try {
                        files = chooser.getSelectedPaths();
                    } catch (IOException ex) {
                        // TODO: add localized message
                        Exceptions.printStackTrace(ex);
                        return;
                    }

                    int[] newSelection = ClassPathUiSupport.addJarFiles( listModel, list.getSelectedIndices(), files  );
                    list.setSelectedIndices( newSelection );
                    curDir = FileUtil.normalizeFile(chooser.getCurrentDirectory());
                    FoldersListSettings.getDefault().setLastUsedClassPathFolder(curDir);
                }
            }
            else if ( source == addLibrary ) {
                //TODO this piece needs to go somewhere else?
                LibraryManager manager = null;
                boolean empty = false;
                try {
                    String path = libraryPath.getText(0, libraryPath.getLength());
                    if (path != null && path.length() > 0) {
                        File fil = PropertyUtils.resolveFile(FileUtil.toFile(project.getProjectDirectory()), path);
                        URL url = FileUtil.normalizeFile(fil).toURI().toURL();
                        manager = LibraryManager.forLocation(url);
                    } else {
                        empty = true;
                    }
                } catch (BadLocationException ex) {
                    empty = true;
                    Exceptions.printStackTrace(ex);
                } catch (MalformedURLException ex2) {
                    Exceptions.printStackTrace(ex2);
                }
                if (manager == null && empty) {
                    manager = LibraryManager.getDefault();
                }
                if (manager == null) {
                    //TODO some error message
                    return;
                }
                
                Set<Library> added = LibraryChooser.showDialog(manager,
                        null, project.getReferenceHelper().getLibraryChooserImportHandler()); // XXX filter to j2se libs only?
                if (added != null) {
                    Set<Library> includedLibraries = new HashSet<Library>();
                    for (int i=0; i< listModel.getSize(); i++) {
                        ClassPathSupport.Item item = (ClassPathSupport.Item) listModel.get(i);
                        if (item.getType() == ClassPathSupport.Item.TYPE_LIBRARY) {
                             if (item.isBroken()) {
                                 //Try to refresh the broken item, it may be already valid or invalid
                                 final String reference = item.getReference();
                                 final String libraryName = ClassPathSupport.getLibraryNameFromReference(reference);
                                 assert libraryName != null : "Not a library reference: "+reference;
                                 final Library library = manager.getLibrary( libraryName );                                 
                                 if ( library != null ) {
                                     listModel.remove(i);                                     
                                     item = ClassPathSupport.Item.create( library, reference );
                                     listModel.add(i, item);
                                 }
                             }
                             else {
                                 final Library origLibrary = item.getLibrary();
                                 final Library newLibrary = manager.getLibrary(origLibrary.getName());
                                 if (newLibrary == null) {
                                     listModel.remove(i);                                     
                                     item = ClassPathSupport.Item.createBroken(ClassPathSupport.Item.TYPE_LIBRARY, item.getReference());
                                     listModel.add(i, item);
                                 }
                             }
                             if (!item.isBroken()) {
                                includedLibraries.add( item.getLibrary() );
                             }
                        }
                   }
                   int[] newSelection = ClassPathUiSupport.addLibraries(listModel, list.getSelectedIndices(), added.toArray(new Library[added.size()]), includedLibraries);
                   list.setSelectedIndices( newSelection );
                }
            }
            else if ( source == addAntArtifact ) { 
                AntArtifactChooser.ArtifactItem artifactItems[] = AntArtifactChooser.showDialog(
                        new String[] { JavaProjectConstants.ARTIFACT_TYPE_JAR, JavaProjectConstants.ARTIFACT_TYPE_FOLDER},
                        project, list.getParent() );
                if (artifactItems != null) {
                    int[] newSelection = ClassPathUiSupport.addArtifacts( listModel, list.getSelectedIndices(), artifactItems);
                    list.setSelectedIndices( newSelection );
                }
            }
            else if ( source == remove ) { 
                int[] newSelection = ClassPathUiSupport.remove( listModel, list.getSelectedIndices() );
                list.setSelectedIndices( newSelection );
            }
            else if ( source == edit ) { 
                ClassPathSupport.Item item = (Item) listModel.get(list.getSelectedIndices()[0]);
                ClassPathUiSupport.edit( listModel, list.getSelectedIndices(),  project.getAntProjectHelper());
                list.repaint(); //need to repaint to get the icon badges right..
            }
            else if ( source == moveUp ) {
                int[] newSelection = ClassPathUiSupport.moveUp( listModel, list.getSelectedIndices() );
                list.setSelectedIndices( newSelection );
            }
            else if ( source == moveDown ) {
                int[] newSelection = ClassPathUiSupport.moveDown( listModel, list.getSelectedIndices() );
                list.setSelectedIndices( newSelection );
            }
        }    
        
        
        /** Handles changes in the selection
         */        
        public void valueChanged( ListSelectionEvent e ) {
            
            // remove enabled only if selection is not empty
            boolean canRemove = false;
            // and when the selection does not contain unremovable item
            if ( selectionModel.getMinSelectionIndex() != -1 ) {
                canRemove = true;
                int iMin = selectionModel.getMinSelectionIndex();
                int iMax = selectionModel.getMinSelectionIndex();
                for ( int i = iMin; i <= iMax; i++ ) {
                    
                    if ( selectionModel.isSelectedIndex( i ) ) {
                        ClassPathSupport.Item item = (ClassPathSupport.Item)listModel.get( i );
                        if ( item.getType() == ClassPathSupport.Item.TYPE_CLASSPATH ) {
                            canRemove = false;
                            break;
                        }
                    }
                }
            }
            
            // addJar allways enabled            
            // addLibrary allways enabled            
            // addArtifact allways enabled            
            edit.setEnabled( ClassPathUiSupport.canEdit (selectionModel, listModel ));
            remove.setEnabled( canRemove );
            moveUp.setEnabled( ClassPathUiSupport.canMoveUp( selectionModel ) );
            moveDown.setEnabled( ClassPathUiSupport.canMoveDown( selectionModel, listModel.getSize() ) );       
            
        }
    }
    
    private static class SimpleFileFilter extends FileFilter {

        private String description;
        private Collection extensions;


        public SimpleFileFilter (String description, String[] extensions) {
            this.description = description;
            this.extensions = Arrays.asList(extensions);
        }

        public boolean accept(File f) {
            if (f.isDirectory())
                return true;
            String name = f.getName();
            int index = name.lastIndexOf('.');   //NOI18N
            if (index <= 0 || index==name.length()-1)
                return false;
            String extension = name.substring (index+1).toUpperCase();
            return this.extensions.contains(extension);
        }

        public String getDescription() {
            return this.description;
        }
    }
        
}
