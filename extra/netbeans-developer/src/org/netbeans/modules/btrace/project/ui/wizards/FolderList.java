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

package org.netbeans.modules.btrace.project.ui.wizards;


import java.awt.Component;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.modules.btrace.project.ui.customizer.BTraceSourceRootsUi;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;


/**
 * List of source/test roots
 * @author tzezula
 */
public final class FolderList extends javax.swing.JPanel {

    public static final String PROP_FILES = "files";    //NOI18N
    public static final String PROP_LAST_USED_DIR = "lastUsedDir";  //NOI18N

    private String fcMessage;
    private File projectFolder;
    private File lastUsedFolder;
    private FolderList relatedFolderList;

    /** Creates new form FolderList */
    public FolderList (String label, char mnemonic, String accessibleDesc, String fcMessage,
                       char addButtonMnemonic, String addButtonAccessibleDesc,
                       char removeButtonMnemonic,String removeButtonAccessibleDesc) {
        this.fcMessage = fcMessage;
        initComponents();
        this.jLabel1.setText(label);
        this.jLabel1.setDisplayedMnemonic(mnemonic);
        this.roots.getAccessibleContext().setAccessibleDescription(accessibleDesc);
        this.roots.setCellRenderer(new Renderer());
        this.roots.setModel (new DefaultListModel());
        this.roots.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    removeButton.setEnabled(roots.getSelectedIndices().length != 0);
                }
            }
        });
        this.addButton.getAccessibleContext().setAccessibleDescription(addButtonAccessibleDesc);
        this.addButton.setMnemonic (addButtonMnemonic);        
        this.removeButton.getAccessibleContext().setAccessibleDescription(removeButtonAccessibleDesc);
        this.removeButton.setMnemonic (removeButtonMnemonic);
        this.removeButton.setEnabled(false);
    }
    
    public void setProjectFolder (File projectFolder) {
        this.projectFolder = projectFolder;
    }
    
    public void setRelatedFolderList (FolderList relatedFolderList) {
        this.relatedFolderList = relatedFolderList;
    }

    public File[] getFiles () {
        Object[] files = ((DefaultListModel)this.roots.getModel()).toArray();
        File[] result = new File[files.length];
        System.arraycopy(files, 0, result, 0, files.length);
        return result;
    }

    public void setFiles (File[] files) {
        DefaultListModel model = ((DefaultListModel)this.roots.getModel());
        model.clear();
        for (int i=0; i<files.length; i++) {
            model.addElement (files[i]);
        }
        if (files.length>0) {
            this.roots.setSelectedIndex(0);
        }
    }
    
    public void setLastUsedDir (File lastUsedDir) {
        if (this.lastUsedFolder == null ? lastUsedDir != null : !this.lastUsedFolder.equals(lastUsedDir)) {
            File oldValue = this.lastUsedFolder;
            this.lastUsedFolder = lastUsedDir;
            this.firePropertyChange(PROP_LAST_USED_DIR, oldValue, this.lastUsedFolder);
        }
    }
    
    public File getLastUsedDir () {
        return this.lastUsedFolder;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    private void initComponents() {//GEN-BEGIN:initComponents
        java.awt.GridBagConstraints gridBagConstraints;

        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        roots = new javax.swing.JList();
        addButton = new javax.swing.JButton();
        removeButton = new javax.swing.JButton();

        setLayout(new java.awt.GridBagLayout());

        jLabel1.setLabelFor(roots);
        jLabel1.setText("jLabel1");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 0);
        add(jLabel1, gridBagConstraints);

        jScrollPane1.setViewportView(roots);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridheight = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 12);
        add(jScrollPane1, gridBagConstraints);

        addButton.setText(java.util.ResourceBundle.getBundle("org/netbeans/modules/java/j2seproject/ui/wizards/Bundle").getString("CTL_AddFolder"));
        addButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        add(addButton, gridBagConstraints);

        removeButton.setText(java.util.ResourceBundle.getBundle("org/netbeans/modules/java/j2seproject/ui/wizards/Bundle").getString("CTL_RemoveFolder"));
        removeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 0, 0);
        add(removeButton, gridBagConstraints);

    }//GEN-END:initComponents

    private void removeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeButtonActionPerformed
        Object[] selection = this.roots.getSelectedValues ();
        for (int i=0; i<selection.length; i++) {
            ((DefaultListModel)this.roots.getModel()).removeElement (selection[i]);
        }
        this.firePropertyChange(PROP_FILES, null, null);
    }//GEN-LAST:event_removeButtonActionPerformed

    private void addButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addButtonActionPerformed
        JFileChooser chooser = new JFileChooser();        
        chooser.setDialogTitle(this.fcMessage);
        chooser.setFileSelectionMode (JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        File curDir = null;
        if (this.lastUsedFolder != null && this.lastUsedFolder.isDirectory()) {
            curDir = this.lastUsedFolder;
        }        
        else if (this.projectFolder != null && this.projectFolder.isDirectory()) {
            curDir = this.projectFolder;
        }                        
        FileUtil.preventFileChooserSymlinkTraversal(chooser, curDir);
        if (chooser.showOpenDialog(this)== JFileChooser.APPROVE_OPTION) {
            File[] files = chooser.getSelectedFiles();
            int[] indecesToSelect = new int[files.length];
            DefaultListModel model = (DefaultListModel)this.roots.getModel();
            Set invalidRoots = new HashSet ();
            File[] relatedFolders = this.relatedFolderList == null ? 
                new File[0] : this.relatedFolderList.getFiles();
            for (int i=0, index=model.size(); i<files.length; i++, index++) {
                File normalizedFile = FileUtil.normalizeFile(files[i]);
                if (!isValidRoot(normalizedFile, relatedFolders, this.projectFolder)) {
                    invalidRoots.add (normalizedFile);
                }
                else {
                    int pos = model.indexOf (normalizedFile);                
                    if (pos == -1) {
                        model.addElement (normalizedFile);
                        indecesToSelect[i] = index;
                    }
                    else {
                        indecesToSelect[i] = pos;
                    }
                }
            }
            this.roots.setSelectedIndices(indecesToSelect);
            this.firePropertyChange(PROP_FILES, null, null);
            File cd = chooser.getCurrentDirectory();
            if (cd != null) {
                this.setLastUsedDir(FileUtil.normalizeFile(cd));
            }
            if (invalidRoots.size()>0) {
                BTraceSourceRootsUi.showIllegalRootsDialog(invalidRoots);
            }
        }
    }//GEN-LAST:event_addButtonActionPerformed
    
    
    static boolean isValidRoot (File file, File[] relatedRoots, File projectFolder) {
        Project p;
        if ((p = FileOwnerQuery.getOwner(file.toURI()))!=null 
            && !file.getAbsolutePath().startsWith(projectFolder.getAbsolutePath()+File.separatorChar)) {
            final Sources sources = (Sources) p.getLookup().lookup(Sources.class);
            if (sources == null) {
                return false;
            }
            final SourceGroup[] sourceGroups = sources.getSourceGroups(Sources.TYPE_GENERIC);
            final SourceGroup[] javaGroups = sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA);
            final SourceGroup[] groups = new SourceGroup [sourceGroups.length + javaGroups.length];
            System.arraycopy(sourceGroups,0,groups,0,sourceGroups.length);
            System.arraycopy(javaGroups,0,groups,sourceGroups.length,javaGroups.length);
            final FileObject projectDirectory = p.getProjectDirectory();
            final FileObject fileObject = FileUtil.toFileObject(file);
            if (projectDirectory == null || fileObject == null) {
                return false;
            }
            for (int i = 0; i< groups.length; i++) {
                final FileObject sgRoot = groups[i].getRootFolder();
                if (fileObject.equals(sgRoot)) {
                    return false;
                }
                if (!projectDirectory.equals(sgRoot) && FileUtil.isParentOf(sgRoot, fileObject)) {
                    return false;
                }
            }
            return true;
        }
        else if (contains (file, relatedRoots)) {
            return false;
        }
        return true;
    }
    
    private static boolean contains (File folder, File[] roots) {
        String path = folder.getAbsolutePath ();
        for (int i=0; i<roots.length; i++) {
            String rootPath = roots[i].getAbsolutePath();
            if (rootPath.equals (path) || path.startsWith (rootPath + File.separatorChar)) {
                return true;
            }
        }
        return false;
    }
    
    private static class Renderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            File f = (File) value;
            Project p = FileOwnerQuery.getOwner(f.toURI());
            String message = f.getAbsolutePath();            
            Component result = super.getListCellRendererComponent(list, message, index, isSelected, cellHasFocus);
            return result;
        }
        
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton removeButton;
    private javax.swing.JList roots;
    // End of variables declaration//GEN-END:variables
    
}
