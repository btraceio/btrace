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

package org.netbeans.modules.btrace.ui;

import java.awt.Dialog;
import org.openide.awt.Mnemonics;
import org.openide.awt.MouseUtils;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.filesystems.FileUtil;


/**
 * Browses and allows to choose a BTrace script
 *
 * @author Tomas Hurka
 * @author Jiri Rechtacek
 * @author Jaroslav Bachorik
 */
public class ScriptChooser extends JPanel {
    //~ Static fields/initializers -----------------------------------------------------------------------------------------------

    // Used only from unit tests to suppress check of main method. If value
    // is different from null it will be returned instead.
    public static Boolean unitTestingSupport_hasMainMethodResult = null;

    //~ Instance fields ----------------------------------------------------------------------------------------------------------

    private javax.swing.JLabel jLabel1;
    private javax.swing.JList jMainClassList;
    private javax.swing.JScrollPane jScrollPane1;
    private ChangeListener changeListener;
    private Collection<String> allScripts;
    private String dialogSubtitle = null;

    //~ Constructors -------------------------------------------------------------------------------------------------------------

    /**
     * Creates new form ScriptChooser
     */
    private ScriptChooser(FileObject[] sourcesRoots) {
        this(sourcesRoots, null);
    }

    private ScriptChooser(FileObject[] sourcesRoots, String subtitle) {
        dialogSubtitle = subtitle;
        initComponents();
        initClassesView(sourcesRoots);
    }

    //~ Methods ------------------------------------------------------------------------------------------------------------------

    public static String selectScript(String sourcePath) {
        JButton okButton = new JButton("Select");
        
        ScriptChooser chooser = new ScriptChooser(new FileObject[] {FileUtil.toFileObject(new File(sourcePath))});

        DialogDescriptor dd = new DialogDescriptor(chooser, "Select a BTrace script", true,
                                                   new Object[] { okButton, DialogDescriptor.CANCEL_OPTION }, okButton,
                                                   DialogDescriptor.BOTTOM_ALIGN, null, null);
        Dialog d = DialogDisplayer.getDefault().createDialog(dd);
        d.setVisible(true);

        if (dd.getValue() == okButton) {
            return chooser.getSelectedScript();
        } else {
            return null;
        }
    }
    
    /**
     * Returns the selected script.
     *
     * @return name of class or null if no BTrace script is selected
     */
    public String getSelectedScript() {
        return (String) jMainClassList.getSelectedValue();
    }

    public void addChangeListener(ChangeListener l) {
        changeListener = l;
    }

    public void removeChangeListener(ChangeListener l) {
        changeListener = null;
    }

    private Object[] getWarmupList() {
        return new Object[] { NbBundle.getMessage(ScriptChooser.class, "LBL_ChooseMainClass_WARMUP_MESSAGE")    }; // NOI18N
    }

    private void initClassesView(final FileObject[] sourcesRoots) {
        allScripts = null;
        jMainClassList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jMainClassList.setListData(getWarmupList());
        RequestProcessor.getDefault().post(new Runnable() {
                public void run() {
                    allScripts = findScripts(sourcesRoots);

                    if (allScripts.isEmpty()) {
                        jMainClassList.setListData(new String[] {
                                                       NbBundle.getMessage(ScriptChooser.class,
                                                                           "LBL_ChooseMainClass_NO_CLASSES_NODE")
                                                   
                                                   }); // NOI18N
                    } else {
                        Object[] arr = allScripts.toArray();
                        // #46861, sort name of classes
                        Arrays.sort(arr);
                        jMainClassList.setListData(arr);
                        jMainClassList.setSelectedIndex(0);
                    }
                }
            });
        jMainClassList.addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent evt) {
                    if (changeListener != null) {
                        changeListener.stateChanged(new ChangeEvent(evt));
                    }
                }
            });
        // support for double click to finish dialog with selected class
        jMainClassList.addMouseListener(new MouseListener() {
                public void mouseClicked(MouseEvent e) {
                    if (MouseUtils.isDoubleClick(e)) {
                        if (getSelectedScript() != null) {
                            if (changeListener != null) {
                                changeListener.stateChanged(new ChangeEvent(e));
                            }
                        }
                    }
                }

                public void mousePressed(MouseEvent e) {
                }

                public void mouseReleased(MouseEvent e) {
                }

                public void mouseEntered(MouseEvent e) {
                }

                public void mouseExited(MouseEvent e) {
                }
            });

        if (dialogSubtitle != null) {
            Mnemonics.setLocalizedText(jLabel1, dialogSubtitle);
        }
    }

    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jMainClassList = new javax.swing.JList();

        setLayout(new java.awt.GridBagLayout());

        setPreferredSize(new java.awt.Dimension(380, 300));
        getAccessibleContext()
            .setAccessibleDescription(NbBundle.getBundle(ScriptChooser.class).getString("AD_MainClassChooser")); // NOI18N
        jLabel1.setLabelFor(jMainClassList);
        Mnemonics.setLocalizedText(jLabel1, NbBundle.getBundle(ScriptChooser.class).getString("CTL_AvaialableMainClasses")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(12, 12, 2, 12);
        add(jLabel1, gridBagConstraints);

        jScrollPane1.setMinimumSize(new java.awt.Dimension(100, 200));
        jScrollPane1.setViewportView(jMainClassList);
        jMainClassList.getAccessibleContext()
                      .setAccessibleDescription(NbBundle.getBundle(ScriptChooser.class).getString("AD_jMainClassList")); // NOI18N

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.gridheight = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 12, 0, 12);
        add(jScrollPane1, gridBagConstraints);
    }
    
    private static Collection<String> findScripts(FileObject[] sourceRoots) {
        final Collection<String> classNames = new ArrayList<String>();
        
        for(FileObject root : sourceRoots) {
            classNames.addAll(findScripts(root, root));
        }
        return classNames;
    }
    
    private static Collection<String> findScripts(FileObject root, FileObject baseDir) {
        final Collection<String> classNames = new ArrayList<String>();

        if (root.isData()) {
            classNames.add(getClassName(root, baseDir));
        } else {
            for(FileObject child : root.getChildren()) {
                classNames.addAll(findScripts(child, baseDir));
            }
        }

        return classNames;        
    }
    
    private static String getClassName(FileObject script, FileObject baseDir) {
        String pkg = FileUtil.getRelativePath(baseDir, script.getParent());
        if (pkg.length() > 0) {
            pkg = pkg.replace(File.separatorChar, '.');
            return pkg + "." + script.getName();
        } else {
            return script.getName();
        }
    }

}
