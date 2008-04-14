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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JPanel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.java.platform.PlatformsCustomizer;
import org.netbeans.api.project.libraries.LibraryManager;
import org.netbeans.modules.btrace.project.java.api.common.ui.PlatformUiSupport;
import org.netbeans.modules.btrace.project.classpath.ClassPathSupport;
import org.netbeans.modules.btrace.project.ui.BTraceLogicalViewProvider;
import org.netbeans.spi.java.project.support.ui.SharableLibrariesUtils;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.openide.awt.Mnemonics;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

/** Customizer for general project attributes.
 *
 * @author  phrebejk
 */
public class CustomizerLibraries extends JPanel implements HelpCtx.Provider, ListDataListener {
    
    public static final String COMPILE = "COMPILE";  //NOI18N
    public static final String RUN = "RUN";          //NOI18N
    public static final String COMPILE_TESTS = "COMPILE_TESTS"; //NOI18N
    public final String RUN_TESTS = "RUN_TESTS";  //NOI18N        
    
    private final BTraceProjectProperties uiProperties;
    private boolean isSharable;
    
    CustomizerLibraries( BTraceProjectProperties uiProps, CustomizerProviderImpl.SubCategoryProvider subcat ) {
        this.uiProperties = uiProps;        
        initComponents();        
        
        this.putClientProperty( "HelpID", "J2SE_CustomizerGeneral" ); // NOI18N

        jListCpC.setModel( uiProperties.JAVAC_CLASSPATH_MODEL );
        jListCpC.setCellRenderer( uiProperties.CLASS_PATH_LIST_RENDERER );
        BTraceClassPathUi.EditMediator.register( uiProperties.getProject(),
                                               jListCpC, 
                                               uiProperties.JAVAC_CLASSPATH_MODEL, 
                                               jButtonAddJarC.getModel(), 
                                               jButtonAddLibraryC.getModel(), 
                                               jButtonAddArtifactC.getModel(), 
                                               jButtonRemoveC.getModel(), 
                                               jButtonMoveUpC.getModel(), 
                                               jButtonMoveDownC.getModel(),
                                               jButtonEditC.getModel(),
                                               uiProperties.SHARED_LIBRARIES_MODEL);
                
        jListCpR.setModel( uiProperties.RUN_CLASSPATH_MODEL );
        jListCpR.setCellRenderer( uiProperties.CLASS_PATH_LIST_RENDERER );
        BTraceClassPathUi.EditMediator.register( uiProperties.getProject(),
                                               jListCpR, 
                                               uiProperties.JAVAC_CLASSPATH_MODEL, 
                                               jButtonAddJarR.getModel(), 
                                               jButtonAddLibraryR.getModel(), 
                                               jButtonAddArtifactR.getModel(), 
                                               jButtonRemoveR.getModel(), 
                                               jButtonMoveUpR.getModel(), 
                                               jButtonMoveDownR.getModel(),
                                               jButtonEditR.getModel(),
                                               uiProperties.SHARED_LIBRARIES_MODEL);
        
       
        uiProperties.NO_DEPENDENCIES_MODEL.setMnemonic( jCheckBoxBuildSubprojects.getMnemonic() );
        jCheckBoxBuildSubprojects.setModel( uiProperties.NO_DEPENDENCIES_MODEL );                        
        jComboBoxTarget.setModel(uiProperties.PLATFORM_MODEL);               
        jComboBoxTarget.setRenderer(uiProperties.PLATFORM_LIST_RENDERER);
        jComboBoxTarget.putClientProperty ("JComboBox.isTableCellEditor", Boolean.TRUE);    //NOI18N
        jComboBoxTarget.addItemListener(new java.awt.event.ItemListener(){ 
            public void itemStateChanged(java.awt.event.ItemEvent e){ 
                javax.swing.JComboBox combo = (javax.swing.JComboBox)e.getSource(); 
                combo.setPopupVisible(false); 
            } 
        });
        testBroken();
        if (BTraceCompositePanelProvider.LIBRARIES.equals(subcat.getCategory())) {
            showSubCategory(subcat.getSubcategory());
        }
        
        uiProperties.JAVAC_CLASSPATH_MODEL.addListDataListener( this );
        uiProperties.JAVAC_TEST_CLASSPATH_MODEL.addListDataListener( this );
        uiProperties.RUN_CLASSPATH_MODEL.addListDataListener( this );
        uiProperties.RUN_TEST_CLASSPATH_MODEL.addListDataListener( this );
        
        //check the sharability status of the project.
        isSharable = false;
    }

    /** split file name into folder and name */
    private static String[] splitPath(String s) {
        int i = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (i == -1) {
            return new String[]{s, null};
        } else {
            return new String[]{s.substring(0, i), s.substring(i+1)};
        }
    }

    private void switchLibrary() {
        String loc = "";
        LibraryManager man;
        if (loc.trim().length() > -1) {
            try {
                File base = FileUtil.toFile(uiProperties.getProject().getProjectDirectory());
                File location = FileUtil.normalizeFile(PropertyUtils.resolveFile(base, loc));
                URL url = location.toURI().toURL();
                man = LibraryManager.forLocation(url);
            } catch (MalformedURLException ex) {
                Exceptions.printStackTrace(ex);
                //TODO show as error in UI
                man = LibraryManager.getDefault();
            }
        } else {
            man = LibraryManager.getDefault();
        }
        
        
        DefaultListModel[] models = new DefaultListModel[]{
            uiProperties.JAVAC_CLASSPATH_MODEL,
            uiProperties.JAVAC_TEST_CLASSPATH_MODEL,
            uiProperties.RUN_CLASSPATH_MODEL,
            uiProperties.RUN_TEST_CLASSPATH_MODEL
           };
        for (int i = 0; i < models.length; i++) {
            for (Iterator it = ClassPathUiSupport.getIterator(models[i]); it.hasNext();) {
                ClassPathSupport.Item itm = (ClassPathSupport.Item) it.next();
                if (itm.getType() == ClassPathSupport.Item.TYPE_LIBRARY) {
                    itm.reassignLibraryManager(man);
                }
            }
        }
        jTabbedPane1.repaint();
        testBroken();
        
    }
        
    private void testBroken() {
        
        DefaultListModel[] models = new DefaultListModel[] {
            uiProperties.JAVAC_CLASSPATH_MODEL,
            uiProperties.JAVAC_TEST_CLASSPATH_MODEL,
            uiProperties.RUN_CLASSPATH_MODEL,
            uiProperties.RUN_TEST_CLASSPATH_MODEL,
        };
        
        boolean broken = false;
        
        for( int i = 0; i < models.length; i++ ) {
            for( Iterator it = ClassPathUiSupport.getIterator( models[i] ); it.hasNext(); ) {
                if ( ((ClassPathSupport.Item)it.next()).isBroken() ) {
                    broken = true;
                    break;
                }
            }
            if ( broken ) {
                break;
            }
        }
        
        if ( broken ) {
            jLabelErrorMessage.setText( NbBundle.getMessage( CustomizerLibraries.class, "LBL_CustomizeLibraries_Libraries_Error" ) ); // NOI18N            
        }
        else {
            jLabelErrorMessage.setText( " " ); // NOI18N
        }
        BTraceLogicalViewProvider viewProvider = (BTraceLogicalViewProvider) uiProperties.getProject().getLookup().lookup(BTraceLogicalViewProvider.class);
        //Update the state of project's node if needed
        viewProvider.testBroken();
    }
    
    // Implementation of HelpCtx.Provider --------------------------------------
    
    public HelpCtx getHelpCtx() {
        return new HelpCtx( CustomizerLibraries.class );
    }        

    
    // Implementation of ListDataListener --------------------------------------
    
    
    public void intervalRemoved( ListDataEvent e ) {
        testBroken(); 
    }

    public void intervalAdded( ListDataEvent e ) {
        // NOP
    }

    public void contentsChanged( ListDataEvent e ) {
        // NOP
    }
    
    
    private void showSubCategory (String name) {
        if (name.equals(COMPILE)) {
            jTabbedPane1.setSelectedIndex (0);
        }        
        else if (name.equals(COMPILE_TESTS)) {
            jTabbedPane1.setSelectedIndex (2);
        }
        else if (name.equals(RUN)) {
            jTabbedPane1.setSelectedIndex (1);
        }
        else if (name.equals(RUN_TESTS)) {
            jTabbedPane1.setSelectedIndex (3);
        }
    }
    
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jLabelTarget = new javax.swing.JLabel();
        jButton1 = new javax.swing.JButton();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanelCompile = new javax.swing.JPanel();
        librariesJLabel1 = new javax.swing.JLabel();
        librariesJScrollPane = new javax.swing.JScrollPane();
        jListCpC = new javax.swing.JList();
        jButtonAddArtifactC = new javax.swing.JButton();
        jButtonAddLibraryC = new javax.swing.JButton();
        jButtonAddJarC = new javax.swing.JButton();
        jButtonEditC = new javax.swing.JButton();
        jButtonRemoveC = new javax.swing.JButton();
        jButtonMoveUpC = new javax.swing.JButton();
        jButtonMoveDownC = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jPanelRun = new javax.swing.JPanel();
        librariesJLabel3 = new javax.swing.JLabel();
        librariesJScrollPane2 = new javax.swing.JScrollPane();
        jListCpR = new javax.swing.JList();
        jButtonAddArtifactR = new javax.swing.JButton();
        jButtonAddLibraryR = new javax.swing.JButton();
        jButtonAddJarR = new javax.swing.JButton();
        jButtonEditR = new javax.swing.JButton();
        jButtonRemoveR = new javax.swing.JButton();
        jButtonMoveUpR = new javax.swing.JButton();
        jButtonMoveDownR = new javax.swing.JButton();
        jCheckBoxBuildSubprojects = new javax.swing.JCheckBox();
        jLabelErrorMessage = new javax.swing.JLabel();
        jComboBoxTarget = new javax.swing.JComboBox();

        jLabelTarget.setLabelFor(jComboBoxTarget);
        org.openide.awt.Mnemonics.setLocalizedText(jLabelTarget, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeGeneral_Platform_JLabel")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jButton1, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeGeneral_Platform_JButton")); // NOI18N
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createNewPlatform(evt);
            }
        });

        jPanelCompile.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));

        librariesJLabel1.setLabelFor(jListCpC);
        org.openide.awt.Mnemonics.setLocalizedText(librariesJLabel1, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_LibrariesC_JLabel")); // NOI18N

        librariesJScrollPane.setViewportView(jListCpC);
        jListCpC.getAccessibleContext().setAccessibleName("null");
        jListCpC.getAccessibleContext().setAccessibleDescription("null");

        org.openide.awt.Mnemonics.setLocalizedText(jButtonAddArtifactC, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_AddProject_JButton")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jButtonAddLibraryC, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_AddLibary_JButton")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jButtonAddJarC, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_AddJar_JButton")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jButtonEditC, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_Edit_JButton")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jButtonRemoveC, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_Remove_JButton")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jButtonMoveUpC, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_MoveUp_JButton")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jButtonMoveDownC, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_MoveDown_JButton")); // NOI18N

        jLabel1.setLabelFor(jListCpC);
        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "MSG_CustomizerLibraries_CompileCpMessage")); // NOI18N

        org.jdesktop.layout.GroupLayout jPanelCompileLayout = new org.jdesktop.layout.GroupLayout(jPanelCompile);
        jPanelCompile.setLayout(jPanelCompileLayout);
        jPanelCompileLayout.setHorizontalGroup(
            jPanelCompileLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(librariesJLabel1)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanelCompileLayout.createSequentialGroup()
                .add(librariesJScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 344, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanelCompileLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(jButtonAddArtifactC, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 104, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jButtonAddLibraryC, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 104, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jButtonAddJarC)
                    .add(jButtonEditC, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 104, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jButtonRemoveC, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 104, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jButtonMoveUpC, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 104, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jButtonMoveDownC, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 104, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
            .add(jLabel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 454, Short.MAX_VALUE)
        );
        jPanelCompileLayout.setVerticalGroup(
            jPanelCompileLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanelCompileLayout.createSequentialGroup()
                .add(librariesJLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jPanelCompileLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanelCompileLayout.createSequentialGroup()
                        .add(jButtonAddArtifactC)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jButtonAddLibraryC)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jButtonAddJarC)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                        .add(jButtonEditC)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jButtonRemoveC)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                        .add(jButtonMoveUpC)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jButtonMoveDownC))
                    .add(librariesJScrollPane, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 251, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jLabel1)
                .add(0, 0, 0))
        );

        jButtonAddArtifactC.getAccessibleContext().setAccessibleDescription("null");
        jButtonAddLibraryC.getAccessibleContext().setAccessibleDescription("null");
        jButtonAddJarC.getAccessibleContext().setAccessibleDescription("null");
        jButtonEditC.getAccessibleContext().setAccessibleDescription("null");
        jButtonRemoveC.getAccessibleContext().setAccessibleDescription("null");
        jButtonMoveUpC.getAccessibleContext().setAccessibleDescription("null");
        jButtonMoveDownC.getAccessibleContext().setAccessibleDescription("null");

        jTabbedPane1.addTab(org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_LibrariesTab"), jPanelCompile); // NOI18N

        jPanelRun.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        jPanelRun.setLayout(new java.awt.GridBagLayout());

        librariesJLabel3.setLabelFor(jListCpR);
        org.openide.awt.Mnemonics.setLocalizedText(librariesJLabel3, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_LibrariesR_JLabel")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 0);
        jPanelRun.add(librariesJLabel3, gridBagConstraints);

        librariesJScrollPane2.setViewportView(jListCpR);
        jListCpR.getAccessibleContext().setAccessibleName("null");
        jListCpR.getAccessibleContext().setAccessibleDescription("null");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridheight = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 12);
        jPanelRun.add(librariesJScrollPane2, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(jButtonAddArtifactR, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_AddProject_JButton")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        jPanelRun.add(jButtonAddArtifactR, gridBagConstraints);
        jButtonAddArtifactR.getAccessibleContext().setAccessibleDescription("null");

        org.openide.awt.Mnemonics.setLocalizedText(jButtonAddLibraryR, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_AddLibary_JButton")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        jPanelRun.add(jButtonAddLibraryR, gridBagConstraints);
        jButtonAddLibraryR.getAccessibleContext().setAccessibleDescription("null");

        org.openide.awt.Mnemonics.setLocalizedText(jButtonAddJarR, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_AddJar_JButton")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 12, 0);
        jPanelRun.add(jButtonAddJarR, gridBagConstraints);
        jButtonAddJarR.getAccessibleContext().setAccessibleDescription("null");

        org.openide.awt.Mnemonics.setLocalizedText(jButtonEditR, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_Edit_JButton")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 12, 0);
        jPanelRun.add(jButtonEditR, gridBagConstraints);
        jButtonEditR.getAccessibleContext().setAccessibleDescription("null");

        org.openide.awt.Mnemonics.setLocalizedText(jButtonRemoveR, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_Remove_JButton")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 12, 0);
        jPanelRun.add(jButtonRemoveR, gridBagConstraints);
        jButtonRemoveR.getAccessibleContext().setAccessibleDescription("null");

        org.openide.awt.Mnemonics.setLocalizedText(jButtonMoveUpR, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_MoveUp_JButton")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        jPanelRun.add(jButtonMoveUpR, gridBagConstraints);
        jButtonMoveUpR.getAccessibleContext().setAccessibleDescription("null");

        org.openide.awt.Mnemonics.setLocalizedText(jButtonMoveDownR, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_MoveDown_JButton")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 12, 0);
        jPanelRun.add(jButtonMoveDownR, gridBagConstraints);
        jButtonMoveDownR.getAccessibleContext().setAccessibleDescription("null");

        jTabbedPane1.addTab(org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_Run_Tab"), jPanelRun); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jCheckBoxBuildSubprojects, org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "LBL_CustomizeLibraries_Build_Subprojects")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabelErrorMessage, " ");
        jLabelErrorMessage.setOpaque(true);

        jComboBoxTarget.setMinimumSize(this.jComboBoxTarget.getPreferredSize());

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jTabbedPane1)
                    .add(layout.createSequentialGroup()
                        .add(jLabelTarget)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 250, Short.MAX_VALUE)
                        .add(jButton1)))
                .add(6, 6, 6))
            .add(layout.createSequentialGroup()
                .add(jCheckBoxBuildSubprojects)
                .addContainerGap())
            .add(layout.createSequentialGroup()
                .add(jLabelErrorMessage, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 472, Short.MAX_VALUE)
                .addContainerGap())
            .add(layout.createSequentialGroup()
                .add(98, 98, 98)
                .add(jComboBoxTarget, 0, 234, Short.MAX_VALUE)
                .add(152, 152, 152))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(jButton1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED))
                    .add(layout.createSequentialGroup()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                            .add(jLabelTarget)
                            .add(jComboBoxTarget, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 27, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)))
                .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 353, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jCheckBoxBuildSubprojects)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jLabelErrorMessage, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 17, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );

        jLabelTarget.getAccessibleContext().setAccessibleDescription("null");
        jButton1.getAccessibleContext().setAccessibleDescription("null");
        jTabbedPane1.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "ACSN_CustomizerLibraries_JTabbedPane")); // NOI18N
        jTabbedPane1.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(CustomizerLibraries.class, "ACSD_CustomizerLibraries_JTabbedPane")); // NOI18N
        jCheckBoxBuildSubprojects.getAccessibleContext().setAccessibleDescription("null");
    }// </editor-fold>//GEN-END:initComponents

    private void createNewPlatform(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createNewPlatform
        Object selectedItem = this.jComboBoxTarget.getSelectedItem();
        JavaPlatform jp = (selectedItem == null ? null : PlatformUiSupport.getPlatform(selectedItem));
        PlatformsCustomizer.showCustomizer(jp);        
    }//GEN-LAST:event_createNewPlatform
   
    
    
    private void collectLibs(DefaultListModel model, List<String> libs, List<String> jarReferences) {
        for (int i = 0; i < model.size(); i++) {
            ClassPathSupport.Item item = (ClassPathSupport.Item) model.get(i);
            if (item.getType() == ClassPathSupport.Item.TYPE_LIBRARY) {
                if (!item.isBroken()) {
                    libs.add(item.getLibrary().getName());
                }
            }
            if (item.getType() == ClassPathSupport.Item.TYPE_JAR) {
                if (item.getReference() != null) {
                    //TODO reference is null for not yet persisted items.
                    // there seems to be no way to generate a reference string without actually
                    // creating and writing the property..
                    jarReferences.add(item.getReference());
                }
            }
        }

    }
    
    private void updateJars(DefaultListModel model) {
        for (int i = 0; i < model.size(); i++) {
            ClassPathSupport.Item item = (ClassPathSupport.Item) model.get(i);
            if (item.getType() == ClassPathSupport.Item.TYPE_JAR) {
                if (item.getReference() != null) {
                    uiProperties.cs.updateJarReference(item);
                }
            }
        }
        
    }
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButtonAddArtifactC;
    private javax.swing.JButton jButtonAddArtifactR;
    private javax.swing.JButton jButtonAddJarC;
    private javax.swing.JButton jButtonAddJarR;
    private javax.swing.JButton jButtonAddLibraryC;
    private javax.swing.JButton jButtonAddLibraryR;
    private javax.swing.JButton jButtonEditC;
    private javax.swing.JButton jButtonEditR;
    private javax.swing.JButton jButtonMoveDownC;
    private javax.swing.JButton jButtonMoveDownR;
    private javax.swing.JButton jButtonMoveUpC;
    private javax.swing.JButton jButtonMoveUpR;
    private javax.swing.JButton jButtonRemoveC;
    private javax.swing.JButton jButtonRemoveR;
    private javax.swing.JCheckBox jCheckBoxBuildSubprojects;
    private javax.swing.JComboBox jComboBoxTarget;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabelErrorMessage;
    private javax.swing.JLabel jLabelTarget;
    private javax.swing.JList jListCpC;
    private javax.swing.JList jListCpR;
    private javax.swing.JPanel jPanelCompile;
    private javax.swing.JPanel jPanelRun;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JLabel librariesJLabel1;
    private javax.swing.JLabel librariesJLabel3;
    private javax.swing.JScrollPane librariesJScrollPane;
    private javax.swing.JScrollPane librariesJScrollPane2;
    // End of variables declaration//GEN-END:variables
        
        
}
