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

import javax.swing.JPanel;
import org.openide.util.HelpCtx;

/** Customizer for general project attributes.
 */
public class CustomizerJar extends JPanel implements HelpCtx.Provider {

    public CustomizerJar( BTraceProjectProperties uiProperties ) {
        initComponents();

        distDirField.setDocument(uiProperties.DIST_JAR_MODEL);
        excludeField.setDocument(uiProperties.BUILD_CLASSES_EXCLUDES_MODEL);

        uiProperties.JAR_COMPRESS_MODEL.setMnemonic(compressCheckBox.getMnemonic());
        compressCheckBox.setModel(uiProperties.JAR_COMPRESS_MODEL);

        uiProperties.DO_JAR_MODEL.setMnemonic(doJarCheckBox.getMnemonic());
        doJarCheckBox.setModel(uiProperties.DO_JAR_MODEL);
    }

    public HelpCtx getHelpCtx() {
        return new HelpCtx( CustomizerJar.class );
    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        distDirLabel = new javax.swing.JLabel();
        distDirField = new javax.swing.JTextField();
        excludeLabel = new javax.swing.JLabel();
        excludeField = new javax.swing.JTextField();
        excludeMessage = new javax.swing.JLabel();
        compressCheckBox = new javax.swing.JCheckBox();
        doJarCheckBox = new javax.swing.JCheckBox();

        distDirLabel.setLabelFor(distDirField);
        org.openide.awt.Mnemonics.setLocalizedText(distDirLabel, org.openide.util.NbBundle.getMessage(CustomizerJar.class, "LBL_CustomizeJar_DistDir_JTextField")); // NOI18N

        distDirField.setEditable(false);

        excludeLabel.setLabelFor(excludeField);
        org.openide.awt.Mnemonics.setLocalizedText(excludeLabel, org.openide.util.NbBundle.getMessage(CustomizerJar.class, "LBL_CustomizeJar_Excludes_JTextField")); // NOI18N

        excludeMessage.setLabelFor(excludeField);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/netbeans/modules/java/j2seproject/ui/customizer/Bundle"); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(excludeMessage, bundle.getString("LBL_CustomizerJar_ExcludeMessage")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(compressCheckBox, org.openide.util.NbBundle.getMessage(CustomizerJar.class, "LBL_CustomizeJar_Commpres_JCheckBox")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(doJarCheckBox, org.openide.util.NbBundle.getMessage(CustomizerJar.class, "CustomizerJar.doJarCheckBox")); // NOI18N

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(distDirLabel)
                        .add(88, 88, 88)
                        .add(distDirField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 294, Short.MAX_VALUE))
                    .add(compressCheckBox)
                    .add(layout.createSequentialGroup()
                        .add(excludeLabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createSequentialGroup()
                                .add(excludeMessage)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 180, Short.MAX_VALUE))
                            .add(excludeField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 309, Short.MAX_VALUE)))
                    .add(layout.createSequentialGroup()
                        .add(doJarCheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 279, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(distDirLabel)
                    .add(distDirField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(excludeLabel)
                    .add(excludeField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(excludeMessage)
                .add(8, 8, 8)
                .add(compressCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(doJarCheckBox)
                .addContainerGap(186, Short.MAX_VALUE))
        );

        distDirField.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getBundle(CustomizerJar.class).getString("AD_jTextFieldDistDir")); // NOI18N
        excludeField.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getBundle(CustomizerJar.class).getString("AD_jTextFieldExcludes")); // NOI18N
        compressCheckBox.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getBundle(CustomizerJar.class).getString("AD_jCheckBoxCompress")); // NOI18N
        doJarCheckBox.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(CustomizerJar.class, "ACSD_BuildJarAfterCompile")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox compressCheckBox;
    private javax.swing.JTextField distDirField;
    private javax.swing.JLabel distDirLabel;
    private javax.swing.JCheckBox doJarCheckBox;
    private javax.swing.JTextField excludeField;
    private javax.swing.JLabel excludeLabel;
    private javax.swing.JLabel excludeMessage;
    // End of variables declaration//GEN-END:variables

}
