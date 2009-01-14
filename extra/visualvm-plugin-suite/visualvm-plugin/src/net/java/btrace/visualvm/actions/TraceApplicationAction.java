/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.java.btrace.visualvm.actions;

import com.sun.tools.visualvm.application.Application;
import com.sun.tools.visualvm.application.jvm.Jvm;
import com.sun.tools.visualvm.application.jvm.JvmFactory;
import com.sun.tools.visualvm.core.ui.DataSourceWindowManager;
import com.sun.tools.visualvm.core.ui.actions.SingleDataSourceAction;
import java.awt.event.ActionEvent;
import java.util.Set;
import net.java.btrace.visualvm.api.BTraceEngine;
import net.java.btrace.visualvm.api.BTraceTask;
import org.openide.util.NbBundle;

/**
 *
 * @author Jaroslav Bachorik
 */
public class TraceApplicationAction extends SingleDataSourceAction<Application> {
    @Override
    protected void actionPerformed(Application dataSource, ActionEvent actionEvent) {
        BTraceTask task = BTraceEngine.sharedInstance().createTask(dataSource);
        Set<BTraceTask> registered = dataSource.getRepository().getDataSources(BTraceTask.class);
        if (!registered.contains(task)) {
            dataSource.getRepository().addDataSource(task);
        } else {
            task = registered.iterator().next();
        }
        DataSourceWindowManager.sharedInstance().openDataSource(task);
    }

    @Override
    protected boolean isEnabled(Application app) {
        Jvm jvm = JvmFactory.getJVMFor(app);
        return app.isLocalApplication() && jvm.isAttachable();
    }

    public static synchronized TraceApplicationAction newInstance() {
        return new TraceApplicationAction();
    }

    private TraceApplicationAction() {
        super(Application.class);
        putValue(NAME, NbBundle.getMessage(TraceApplicationAction.class, "TraceApplicationAction.title")); // NOI18N
    }
}
