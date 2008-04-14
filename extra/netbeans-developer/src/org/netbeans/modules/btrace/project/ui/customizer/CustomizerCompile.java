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

public class CustomizerCompile extends JPanel implements HelpCtx.Provider {

    public CustomizerCompile( BTraceProjectProperties uiProperties ) {
        initComponents();

        uiProperties.JAVAC_DEPRECATION_MODEL.setMnemonic( deprecationCheckBox.getMnemonic() );
        deprecationCheckBox.setModel( uiProperties.JAVAC_DEPRECATION_MODEL );

        uiProperties.JAVAC_DEBUG_MODEL.setMnemonic( debugInfoCheckBox.getMnemonic() );
        debugInfoCheckBox.setModel( uiProperties.JAVAC_DEBUG_MODEL );

        uiProperties.DO_DEPEND_MODEL.setMnemonic(doDependCheckBox.getMnemonic());
        doDependCheckBox.setModel(uiProperties.DO_DEPEND_MODEL);

        additionalJavacParamsField.setDocument( uiProperties.JAVAC_COMPILER_ARG_MODEL );
    }

    public HelpCtx getHelpCtx() {
        return new HelpCtx( CustomizerCompile.class );
    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        debugInfoCheckBox = new javax.swing.JCheckBox();
        deprecationCheckBox = new javax.swing.JCheckBox();
        doDependCheckBox = new javax.swing.JCheckBox();
        additionalJavacParamsLabel = new javax.swing.JLabel();
        additionalJavacParamsField = new javax.swing.JTextField();
        additionalJavacParamsExample = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(debugInfoCheckBox, org.openide.util.NbBundle.getMessage(CustomizerCompile.class, "LBL_CustomizeCompile_Compiler_DebugInfo_JCheckBox")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(deprecationCheckBox, org.openide.util.NbBundle.getBundle(CustomizerCompile.class).getString("LBL_CustomizeCompile_Compiler_Deprecation_JCheckBox")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(doDependCheckBox, org.openide.util.NbBundle.getMessage(CustomizerCompile.class, "CustomizerCompile.doDependCheckBox")); // NOI18N

        additionalJavacParamsLabel.setLabelFor(additionalJavacParamsField);
        org.openide.awt.Mnemonics.setLocalizedText(additionalJavacParamsLabel, org.openide.util.NbBundle.getMessage(CustomizerCompile.class, "LBL_AdditionalCompilerOptions")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(additionalJavacParamsExample, org.openide.util.NbBundle.getMessage(CustomizerCompile.class, "LBL_AdditionalCompilerOptionsExample")); // NOI18N

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(debugInfoCheckBox)
                    .add(deprecationCheckBox)
                    .add(doDependCheckBox)
                    .add(layout.createSequentialGroup()
                        .add(additionalJavacParamsLabel)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(additionalJavacParamsExample)
                            .add(additionalJavacParamsField, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 324, Short.MAX_VALUE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(debugInfoCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(deprecationCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(doDependCheckBox)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(additionalJavacParamsLabel)
                    .add(additionalJavacParamsField, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(additionalJavacParamsExample)
                .add(353, 353, 353))
        );

        debugInfoCheckBox.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(CustomizerCompile.class, "ACSD_CustomizerCompile_jCheckBoxDebugInfo")); // NOI18N
        deprecationCheckBox.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(CustomizerCompile.class, "ACSD_CustomizerCompile_jCheckBoxDeprecation")); // NOI18N
        doDependCheckBox.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(CustomizerCompile.class, "ACSD_doDependCheckBox")); // NOI18N
        additionalJavacParamsField.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage (CustomizerCompile.class,"AD_AdditionalCompilerOptions"));
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel additionalJavacParamsExample;
    private javax.swing.JTextField additionalJavacParamsField;
    private javax.swing.JLabel additionalJavacParamsLabel;
    private javax.swing.JCheckBox debugInfoCheckBox;
    private javax.swing.JCheckBox deprecationCheckBox;
    private javax.swing.JCheckBox doDependCheckBox;
    // End of variables declaration//GEN-END:variables

}
