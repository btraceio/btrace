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
 * The Original Software is NetBeans. The Initial Deve1loper of the Original
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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.IllegalCharsetNameException;
import java.util.logging.Logger;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.plaf.UIResource;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import org.netbeans.spi.java.project.support.ui.IncludeExcludeVisualizer;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

/**
 * Customizer panel "Sources": source roots, level, includes/excludes.
 * @author  Tomas Zezula
 */
public class CustomizerSources extends javax.swing.JPanel implements HelpCtx.Provider {
    
    
    private String originalEncoding;

    private final BTraceProjectProperties uiProperties;

    public CustomizerSources( BTraceProjectProperties uiProperties ) {
        this.uiProperties = uiProperties;
        initComponents();
        jScrollPane1.getViewport().setBackground( sourceRoots.getBackground() );
        
        sourceRoots.setModel( uiProperties.SOURCE_ROOTS_MODEL );
        sourceRoots.getTableHeader().setReorderingAllowed(false);
        
        FileObject projectFolder = uiProperties.getProject().getProjectDirectory();
        File pf = FileUtil.toFile( projectFolder );
        this.projectLocation.setText( pf == null ? "" : pf.getPath() ); // NOI18N
        
        
        BTraceSourceRootsUi.EditMediator emSR = BTraceSourceRootsUi.registerEditMediator(
            uiProperties.getProject(),
            uiProperties.getProject().getSourceRoots(),
            sourceRoots,
            addSourceRoot,
            removeSourceRoot, 
            upSourceRoot, 
            downSourceRoot,
            new LabelCellEditor(sourceRoots));
                
        this.originalEncoding = this.uiProperties.getProject().evaluator().getProperty(BTraceProjectProperties.SOURCE_ENCODING);
        if (this.originalEncoding == null) {
            this.originalEncoding = Charset.defaultCharset().name();
        }
        
        this.encoding.setModel(new EncodingModel(this.originalEncoding));
        this.encoding.setRenderer(new EncodingRenderer());
        

        this.encoding.addActionListener(new ActionListener () {
            public void actionPerformed(ActionEvent arg0) {
                handleEncodingChange();
            }            
        });
        initTableVisualProperties(sourceRoots);
    }
    
    private class TableColumnSizeComponentAdapter extends ComponentAdapter {
        private JTable table = null;
        
        public TableColumnSizeComponentAdapter(JTable table){
            this.table = table;
        }
        
        public void componentResized(ComponentEvent evt){
            double pw = table.getParent().getParent().getSize().getWidth();
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            TableColumn column = table.getColumnModel().getColumn(0);
            column.setWidth( ((int)pw/2) - 1 );
            column.setPreferredWidth( ((int)pw/2) - 1 );
            column = table.getColumnModel().getColumn(1);
            column.setWidth( ((int)pw/2) - 1 );
            column.setPreferredWidth( ((int)pw/2) - 1 );
        }
    }
    
    private void initTableVisualProperties(JTable table) {

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setIntercellSpacing(new java.awt.Dimension(0, 0));
        // set the color of the table's JViewport
        table.getParent().setBackground(table.getBackground());
        
        //we'll get the parents width so we can use that to set the column sizes.
        double pw = table.getParent().getParent().getPreferredSize().getWidth();
        
        //#88174 - Need horizontal scrollbar for library names
        //ugly but I didn't find a better way how to do it
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        TableColumn column = table.getColumnModel().getColumn(0);
        column.setMinWidth(226);
        column.setWidth( ((int)pw/2) - 1 );
        column.setPreferredWidth( ((int)pw/2) - 1 );
        column.setMinWidth(75);
        column = table.getColumnModel().getColumn(1);
        column.setMinWidth(226);
        column.setWidth( ((int)pw/2) - 1 );
        column.setPreferredWidth( ((int)pw/2) - 1 );
        column.setMinWidth(75);
        this.addComponentListener(new TableColumnSizeComponentAdapter(table));
    }
    
    private void handleEncodingChange () {
            Charset enc = (Charset) encoding.getSelectedItem();
            String encName;
            if (enc != null) {
                encName = enc.name();
            }
            else {
                encName = originalEncoding;
            }
            this.uiProperties.putAdditionalProperty(BTraceProjectProperties.SOURCE_ENCODING, encName);
    }

    public HelpCtx getHelpCtx() {
        return new HelpCtx (CustomizerSources.class);
    }
    
    
    
    private static class EncodingRenderer extends JLabel implements ListCellRenderer, UIResource {
        
        public EncodingRenderer() {
            setOpaque(true);
        }
        
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            assert value instanceof Charset;
            setName("ComboBox.listRenderer"); // NOI18N
            setText(((Charset) value).displayName());
            setIcon(null);
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());             
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            return this;
        }
        
        @Override
        public String getName() {
            String name = super.getName();
            return name == null ? "ComboBox.renderer" : name; // NOI18N
        }
        
    }
    
    private static class EncodingModel extends DefaultComboBoxModel {
        
        public EncodingModel (String originalEncoding) {
            Charset defEnc = null;
            for (Charset c : Charset.availableCharsets().values()) {
                if (c.name().equals(originalEncoding)) {
                    defEnc = c;
                }
                addElement(c);
            }
            if (defEnc == null) {
                //Create artificial Charset to keep the original value
                //May happen when the project was set up on the platform
                //which supports more encodings
                try {
                    defEnc = new UnknownCharset (originalEncoding);
                    addElement(defEnc);
                } catch (IllegalCharsetNameException e) {
                    //The source.encoding property is completely broken
                    Logger.getLogger(this.getClass().getName()).info("IllegalCharsetName: " + originalEncoding);
                }
            }
            if (defEnc == null) {
                defEnc = Charset.defaultCharset();
            }
            setSelectedItem(defEnc);
        }
    }
    
    private static class UnknownCharset extends Charset {
        
        UnknownCharset (String name) {
            super (name, new String[0]);
        }
    
        public boolean contains(Charset c) {
            throw new UnsupportedOperationException();
        }

        public CharsetDecoder newDecoder() {
            throw new UnsupportedOperationException();
        }

        public CharsetEncoder newEncoder() {
            throw new UnsupportedOperationException();
        }
}
    
    private static class ResizableRowHeightTable extends JTable {

        private boolean needResize = true;
        
        @Override
        public void setFont(Font font) {
            needResize = true;
            super.setFont(font);
        }

        @Override
        public void paint(Graphics g) {
            if(needResize) {
                this.setRowHeight(g.getFontMetrics(this.getFont()).getHeight());
                needResize = false;
            }
            super.paint(g);
        }
        
    }
    
    private static class LabelCellEditor extends DefaultCellEditor {
        
        private JTable sourceRoots;
        
        public LabelCellEditor(JTable sourceRoots) {
            super(new JTextField());
            this.sourceRoots = sourceRoots;
        }
        
        @Override
        public boolean stopCellEditing() {
            JTextField field = (JTextField) getComponent();
            String text = field.getText();
            boolean validCell = true;
            TableModel model = sourceRoots.getModel();
            int rowCount = model.getRowCount();
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                String value = (String) model.getValueAt(rowIndex, 1);
                if (text.equals(value)) {
                    validCell = false;
                }
            }            
            return validCell == false ? validCell : super.stopCellEditing();
        }
        
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        projectLocation = new javax.swing.JTextField();
        includeExcludeButton = new javax.swing.JButton();
        addSourceRoot = new javax.swing.JButton();
        removeSourceRoot = new javax.swing.JButton();
        upSourceRoot = new javax.swing.JButton();
        downSourceRoot = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        sourceRoots = new ResizableRowHeightTable();
        jLabel5 = new javax.swing.JLabel();
        encoding = new javax.swing.JComboBox();

        setPreferredSize(new java.awt.Dimension(560, 450));

        jLabel1.setDisplayedMnemonic(org.openide.util.NbBundle.getMessage(CustomizerSources.class, "AD_CustomizerSources_projectLocation").charAt(0));
        jLabel1.setLabelFor(projectLocation);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/netbeans/modules/btrace/project/ui/customizer/Bundle"); // NOI18N
        jLabel1.setText(bundle.getString("CTL_ProjectFolder")); // NOI18N

        projectLocation.setEditable(false);

        org.openide.awt.Mnemonics.setLocalizedText(includeExcludeButton, org.openide.util.NbBundle.getMessage(CustomizerSources.class, "CustomizerSources.includeExcludeButton")); // NOI18N
        includeExcludeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                includeExcludeButtonActionPerformed(evt);
            }
        });

        addSourceRoot.setMnemonic(org.openide.util.NbBundle.getMessage(CustomizerSources.class, "AD_CustomizerSources_addSourceRoot").charAt(0));
        addSourceRoot.setText(bundle.getString("CTL_AddSourceRoot")); // NOI18N

        removeSourceRoot.setMnemonic(org.openide.util.NbBundle.getMessage(CustomizerSources.class, "AD_CustomizerSources_removeSourceRoot").charAt(0));
        removeSourceRoot.setText(bundle.getString("CTL_RemoveSourceRoot")); // NOI18N

        upSourceRoot.setMnemonic(org.openide.util.NbBundle.getMessage(CustomizerSources.class, "AD_CustomizerSources_upSourceRoot").charAt(0));
        upSourceRoot.setText(bundle.getString("CTL_UpSourceRoot")); // NOI18N

        downSourceRoot.setMnemonic(org.openide.util.NbBundle.getMessage(CustomizerSources.class, "AD_CustomizerSources_removeSourceRoot").charAt(0));
        downSourceRoot.setText(bundle.getString("CTL_DownSourceRoot")); // NOI18N

        jLabel2.setDisplayedMnemonic(org.openide.util.NbBundle.getMessage(CustomizerSources.class, "AD_CustomizerSources_sourceRoots").charAt(0));
        jLabel2.setLabelFor(sourceRoots);
        jLabel2.setText(bundle.getString("CTL_SourceRoots")); // NOI18N

        jScrollPane1.setPreferredSize(new java.awt.Dimension(450, 150));

        sourceRoots.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null},
                {null, null},
                {null, null},
                {null, null}
            },
            new String [] {
                "Package Folder", "Label"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Object.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane1.setViewportView(sourceRoots);
        sourceRoots.getAccessibleContext().setAccessibleDescription("null");

        jLabel5.setLabelFor(encoding);
        org.openide.awt.Mnemonics.setLocalizedText(jLabel5, org.openide.util.NbBundle.getMessage(CustomizerSources.class, "TXT_Encoding")); // NOI18N

        encoding.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(jLabel1)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(projectLocation, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 481, Short.MAX_VALUE))
                    .add(jLabel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 170, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(layout.createSequentialGroup()
                        .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 442, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                                .add(downSourceRoot, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(upSourceRoot, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(removeSourceRoot, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .add(addSourceRoot, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .add(includeExcludeButton))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED))
                    .add(layout.createSequentialGroup()
                        .add(jLabel5)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(encoding, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 188, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );

        layout.linkSize(new java.awt.Component[] {addSourceRoot, downSourceRoot, includeExcludeButton, removeSourceRoot, upSourceRoot}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel1)
                    .add(projectLocation, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .add(18, 18, 18)
                .add(jLabel2)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(layout.createSequentialGroup()
                        .add(addSourceRoot)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(removeSourceRoot)
                        .add(18, 18, 18)
                        .add(upSourceRoot)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(downSourceRoot)
                        .add(18, 18, 18)
                        .add(includeExcludeButton))
                    .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 319, Short.MAX_VALUE))
                .add(18, 18, 18)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel5)
                    .add(encoding, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        projectLocation.getAccessibleContext().setAccessibleDescription("null");
        includeExcludeButton.getAccessibleContext().setAccessibleDescription("null");
        addSourceRoot.getAccessibleContext().setAccessibleDescription("null");
        removeSourceRoot.getAccessibleContext().setAccessibleDescription("null");
        upSourceRoot.getAccessibleContext().setAccessibleDescription("null");
        downSourceRoot.getAccessibleContext().setAccessibleDescription("null");
        jLabel5.getAccessibleContext().setAccessibleDescription("null");
    }// </editor-fold>//GEN-END:initComponents

private void includeExcludeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_includeExcludeButtonActionPerformed
    IncludeExcludeVisualizer v = new IncludeExcludeVisualizer();
    uiProperties.loadIncludesExcludes(v);
    DialogDescriptor dd = new DialogDescriptor(v.getVisualizerPanel(),
            NbBundle.getMessage(CustomizerSources.class, "CustomizerSources.title.includeExclude"));
    dd.setOptionType(NotifyDescriptor.OK_CANCEL_OPTION);
    if (NotifyDescriptor.OK_OPTION.equals(DialogDisplayer.getDefault().notify(dd))) {
        uiProperties.storeIncludesExcludes(v);
    }
}//GEN-LAST:event_includeExcludeButtonActionPerformed
    
   
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addSourceRoot;
    private javax.swing.JButton downSourceRoot;
    private javax.swing.JComboBox encoding;
    private javax.swing.JButton includeExcludeButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextField projectLocation;
    private javax.swing.JButton removeSourceRoot;
    private javax.swing.JTable sourceRoots;
    private javax.swing.JButton upSourceRoot;
    // End of variables declaration//GEN-END:variables
    
}
