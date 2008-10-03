/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.java.btrace.visualvm.views;

import com.sun.tools.visualvm.core.datasource.descriptor.DataSourceDescriptorFactory;
import com.sun.tools.visualvm.core.ui.DataSourceView;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;
import javax.swing.JSplitPane;
import net.java.btrace.visualvm.api.BTraceTask;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceTaskView extends DataSourceView {
    public BTraceTaskView(BTraceTask task) {
        super(task, "BTrace", DataSourceDescriptorFactory.getDescriptor(task).getIcon(), DataSourceView.POSITION_LAST, true);
    }

    @Override
    protected DataViewComponent createComponent() {
        final BTraceTask task = (BTraceTask)getDataSource();
        BTraceTaskEditorPanel panel = new BTraceTaskEditorPanel(task);
        
        DataViewComponent.MasterView mv = new DataViewComponent.MasterView("BTrace", "", panel);
        DataViewComponent.MasterViewConfiguration mvc = new DataViewComponent.MasterViewConfiguration(true);
        final DataViewComponent dvc = new DataViewComponent(mv, mvc);


        final OutputPane output = new OutputPane();

        dvc.addDetailsView(new DataViewComponent.DetailsView("Console", "Console Output", POSITION_LAST, output, null), DataViewComponent.BOTTOM_LEFT);

        dvc.hideDetailsArea(DataViewComponent.BOTTOM_LEFT);

        // Workaround to force correct initial appearance
        try {
            JSplitPane splitPane = (JSplitPane)panel.getParent().getParent().getParent().getParent(); // get the master/details splitter
            splitPane.getBottomComponent().setVisible(false); // hide details container (the actual workaround - should work automatically)
        } catch (Exception e) {} // DataViewComponent implementation probably changed, no way to apply the workaround

        task.setWriter(output.getWriter());

        task.addStateListener(new BTraceTask.StateListener() {

            public void stateChanged(BTraceTask.State state) {
                if (state == BTraceTask.State.STARTING) {
                    output.clear();
                    dvc.showDetailsArea(DataViewComponent.BOTTOM_LEFT);
                } else if (state == BTraceTask.State.RUNNING) {
                    dvc.showDetailsArea(DataViewComponent.BOTTOM_LEFT);
                }
            }
        });
        
        return dvc;
    }
}
