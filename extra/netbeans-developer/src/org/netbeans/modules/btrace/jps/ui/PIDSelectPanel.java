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

package org.netbeans.modules.btrace.jps.ui;

import org.openide.DialogDescriptor;
import org.openide.util.NbBundle;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import javax.swing.*;
import org.netbeans.modules.btrace.jps.JpsProxy;
import org.netbeans.modules.btrace.jps.RunningVM;
import org.openide.DialogDisplayer;
import org.openide.util.RequestProcessor;


/**
 * A panel that allows to select a process PID from a combo box of all running processes
 *
 * @author Tomas Hurka
 * @author Ian Formanek
 */
public final class PIDSelectPanel extends JPanel implements ActionListener {
    //~ Inner Classes ------------------------------------------------------------------------------------------------------------

    private static class PIDComboRenderer extends DefaultListCellRenderer {
        //~ Methods --------------------------------------------------------------------------------------------------------------

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            if (value instanceof RunningVM) {
                RunningVM vm = (RunningVM) value;
                String args = vm.getMainArgs();

                if (args == null) {
                    args = ""; //NOI18N
                } else {
                    args = " " + args; //NOI18N
                }

                String text = MessageFormat.format(VM_COMBO_ITEM_TEXT, new Object[] { vm.getMainClass(), "" + vm.getPid() }); // NOI18N

                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            } else {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        }
    }

    //~ Static fields/initializers -----------------------------------------------------------------------------------------------

    // -----
    // I18N String constants
    private static final String REFRESH_BUTTON_NAME = NbBundle.getMessage(PIDSelectPanel.class, "PIDSelectPanel_RefreshButtonName"); //NOI18N
    private static final String PID_LABEL_TEXT = NbBundle.getMessage(PIDSelectPanel.class, "PIDSelectPanel_PidLabelText"); //NOI18N
    private static final String MAIN_CLASS_LABEL_TEXT = NbBundle.getMessage(PIDSelectPanel.class,
                                                                            "PIDSelectPanel_MainClassLabelText"); //NOI18N
    private static final String ARGUMENTS_LABEL_TEXT = NbBundle.getMessage(PIDSelectPanel.class,
                                                                           "PIDSelectPanel_ArgumentsLabelText"); //NOI18N
    private static final String VM_ARGUMENTS_LABEL_TEXT = NbBundle.getMessage(PIDSelectPanel.class,
                                                                              "PIDSelectPanel_VmArgumentsLabelText"); //NOI18N
    private static final String VM_FLAGS_LABEL_TEXT = NbBundle.getMessage(PIDSelectPanel.class, "PIDSelectPanel_VmFlagsLabelText"); //NOI18N
    private static final String VM_COMBO_ITEM_TEXT = NbBundle.getMessage(PIDSelectPanel.class, "PIDSelectPanel_VmComboItemText"); //NOI18N
    private static final String PROCESSES_LIST_ITEM_TEXT = NbBundle.getMessage(PIDSelectPanel.class,
                                                                               "PIDSelectPanel_ProcessesListItemText"); //NOI18N
    private static final String ERROR_GETTING_PROCESSES_ITEM_TEXT = NbBundle.getMessage(PIDSelectPanel.class,
                                                                                        "PIDSelectPanel_ErrorGettingProcessesItemText"); //NOI18N
    private static final String NO_PROCESSES_ITEM_TEXT = NbBundle.getMessage(PIDSelectPanel.class,
                                                                             "PIDSelectPanel_NoProcessesItemText"); //NOI18N
    private static final String SELECT_PROCESS_ITEM_TEXT = NbBundle.getMessage(PIDSelectPanel.class,
                                                                               "PIDSelectPanel_SelectProcessItemText"); //NOI18N
    private static final String OK_BUTTON_NAME = NbBundle.getMessage(PIDSelectPanel.class, "PIDSelectPanel_OkButtonName"); //NOI18N
    private static final String SELECT_PROCESS_DIALOG_CAPTION = NbBundle.getMessage(PIDSelectPanel.class,
                                                                                    "PIDSelectPanel_SelectProcessDialogCaption"); //NOI18N
    private static final String COMBO_ACCESS_NAME = NbBundle.getMessage(PIDSelectPanel.class, "PIDSelectPanel_ComboAccessName"); //NOI18N
    private static final String COMBO_ACCESS_DESCR = NbBundle.getMessage(PIDSelectPanel.class, "PIDSelectPanel_ComboAccessDescr"); //NOI18N
    private static final String BUTTON_ACCESS_DESCR = NbBundle.getMessage(PIDSelectPanel.class, "PIDSelectPanel_ButtonAccessDescr"); //NOI18N
                                                                                                                                     // -----
    private static final int MAX_WIDTH = 500;

    //~ Instance fields ----------------------------------------------------------------------------------------------------------

    private JButton button;
    private JButton okButton;
    private JComboBox combo;
    private JLabel argumentsLabel;
    private JLabel mainClassLabel;
    private JLabel pidLabel;
    private JLabel vmArgumentsLabel;
    private JLabel vmFlagsLabel;

    //~ Constructors -------------------------------------------------------------------------------------------------------------

    public PIDSelectPanel(JButton okButton) {
        this.okButton = okButton;

        combo = new JComboBox();
        button = new JButton(REFRESH_BUTTON_NAME);

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new GridBagLayout());

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.insets = new Insets(3, 5, 0, 0);
        labelGbc.anchor = GridBagConstraints.WEST;

        GridBagConstraints valueGbc = new GridBagConstraints();
        valueGbc.weightx = 1.0;
        valueGbc.fill = GridBagConstraints.HORIZONTAL;
        valueGbc.insets = new Insets(3, 5, 0, 5);
        valueGbc.gridwidth = GridBagConstraints.REMAINDER;
        valueGbc.anchor = GridBagConstraints.WEST;

        JLabel l;

        l = new JLabel(PID_LABEL_TEXT);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        infoPanel.add(l, labelGbc);
        infoPanel.add(pidLabel = new JLabel(), valueGbc);
        l = new JLabel(MAIN_CLASS_LABEL_TEXT);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        infoPanel.add(l, labelGbc);
        infoPanel.add(mainClassLabel = new JLabel(), valueGbc);
        l = new JLabel(ARGUMENTS_LABEL_TEXT);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        infoPanel.add(l, labelGbc);
        infoPanel.add(argumentsLabel = new JLabel(), valueGbc);
        l = new JLabel(VM_ARGUMENTS_LABEL_TEXT);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        infoPanel.add(l, labelGbc);
        infoPanel.add(vmArgumentsLabel = new JLabel(), valueGbc);
        l = new JLabel(VM_FLAGS_LABEL_TEXT);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        infoPanel.add(l, labelGbc);
        infoPanel.add(vmFlagsLabel = new JLabel(), valueGbc);

        combo.setRenderer(new PIDComboRenderer());
        combo.getAccessibleContext().setAccessibleName(COMBO_ACCESS_NAME);
        combo.getAccessibleContext().setAccessibleDescription(COMBO_ACCESS_DESCR);

        button.getAccessibleContext().setAccessibleDescription(BUTTON_ACCESS_DESCR);

        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setLayout(new BorderLayout(0, 10));

        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BorderLayout(5, 0));

        northPanel.add(combo, BorderLayout.CENTER);
        northPanel.add(button, BorderLayout.EAST);

        add(northPanel, BorderLayout.NORTH);
        add(infoPanel, BorderLayout.CENTER);

        okButton.setEnabled(false);

        refreshCombo();

        button.addActionListener(this);
        combo.addActionListener(this);
    }

    //~ Methods ------------------------------------------------------------------------------------------------------------------

    public int getPID() {
        Object sel = combo.getSelectedItem();

        if ((sel != null) && sel instanceof RunningVM) {
            return ((RunningVM) sel).getPid();
        }

        return -1;
    }

    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();

        return new Dimension(Math.max(d.width, MAX_WIDTH), d.height);
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == button) {
            refreshCombo();
        } else if (e.getSource() == combo) {
            okButton.setEnabled(combo.getSelectedItem() instanceof RunningVM);
            updateInfo();
        }
    }

    public static int selectPID() {
        JButton okButton = new JButton(OK_BUTTON_NAME);
        PIDSelectPanel pidSelect = new PIDSelectPanel(okButton);

        DialogDescriptor dd = new DialogDescriptor(pidSelect, SELECT_PROCESS_DIALOG_CAPTION, true,
                                                   new Object[] { okButton, DialogDescriptor.CANCEL_OPTION }, okButton,
                                                   DialogDescriptor.BOTTOM_ALIGN, null, null);
        Dialog d = DialogDisplayer.getDefault().createDialog(dd);
        d.setVisible(true);

        if (dd.getValue() == okButton) {
            return pidSelect.getPID();
        } else {
            return -1;
        }
    }

    private Runnable comboRefresher = new Runnable() {
            private RunningVM[] vms = JpsProxy.getRunningVMs();
            private Object[] ar = new Object[((vms == null) ? 0 : vms.length) + 1];


            public void run() {
                if (vms == null) {
                    ar[0] = ERROR_GETTING_PROCESSES_ITEM_TEXT;
                } else if (vms.length == 0) {
                    ar[0] = NO_PROCESSES_ITEM_TEXT;
                } else {
                    ar[0] = SELECT_PROCESS_ITEM_TEXT;
                    System.arraycopy(vms, 0, ar, 1, vms.length);
                }
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        combo.setEnabled(true);
                        combo.setModel(new DefaultComboBoxModel(ar));
                        updateInfo();
                    }
            });
        }
    };
    
    private void refreshCombo() {
        okButton.setEnabled(false);
        combo.setEnabled(false);
        combo.setModel(new DefaultComboBoxModel(new Object[] { PROCESSES_LIST_ITEM_TEXT }));
        RequestProcessor.getDefault().post(comboRefresher);
    }

    private void updateInfo() {
        Object sel = combo.getSelectedItem();

        if ((sel != null) && sel instanceof RunningVM) {
            RunningVM vm = (RunningVM) sel;
            pidLabel.setText("" + vm.getPid()); //NOI18N
            mainClassLabel.setText(vm.getMainClass());
            argumentsLabel.setText(vm.getMainArgs());
            vmArgumentsLabel.setText(vm.getVMArgs());
            vmFlagsLabel.setText(vm.getVMFlags());
        } else {
            pidLabel.setText(""); //NOI18N
            mainClassLabel.setText(""); //NOI18N
            argumentsLabel.setText(""); //NOI18N
            vmArgumentsLabel.setText(""); //NOI18N
            vmFlagsLabel.setText(""); //NOI18N
        }
    }
}
