/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.java.btrace.visualvm.views;

import com.sun.tools.visualvm.core.ui.DataSourceView;
import com.sun.tools.visualvm.core.ui.DataSourceViewProvider;
import com.sun.tools.visualvm.core.ui.DataSourceViewsManager;
import net.java.btrace.visualvm.api.BTraceTask;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceTaskViewProvider extends DataSourceViewProvider<BTraceTask> {
    private static class Singleton {
        private static BTraceTaskViewProvider INSTANCE = new BTraceTaskViewProvider();
    }

    @Override
    protected DataSourceView createView(BTraceTask dataSource) {
        return new BTraceTaskView(dataSource);
    }

    @Override
    protected boolean supportsViewFor(BTraceTask dataSource) {
        return true;
    }

    public static synchronized void initialize() {
        DataSourceViewsManager.sharedInstance().addViewProvider(Singleton.INSTANCE, BTraceTask.class);
    }

    public static synchronized void shutdown() {
        DataSourceViewsManager.sharedInstance().removeViewProvider(Singleton.INSTANCE);
    }

}
