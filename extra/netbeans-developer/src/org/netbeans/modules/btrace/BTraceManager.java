/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.btrace;

import com.sun.btrace.CommandListener;
import com.sun.btrace.client.Client;
import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.MessageCommand;
import com.sun.btrace.comm.StringMapDataCommand;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceManager {
    private final static String PROP_ENABLED = "enabled"; // NOI18N
    private final static BTraceManager INSTANCE = new BTraceManager();
    private final Map<Integer, Integer> portCache = new HashMap<Integer, Integer>();
    private Random rnd;
    
    private BTraceManager() {
        rnd = new Random(System.currentTimeMillis());
    }

    public static BTraceManager sharedInstance() {
        return INSTANCE;
    }
       
    public void deploy(String scriptPath, final int pid) throws IOException {
        File locatedFile = InstalledFileLocator.getDefault().locate("modules/ext/btrace-agent.jar", "org.netbeans.modules.btrace.project", false);
        final String agentPath = locatedFile.getAbsolutePath();
        
        final int port[] = new int[]{-1};
        synchronized(portCache) {
            if (portCache.containsKey(pid)) {
                port[0] = portCache.get(pid);
            } else {
                do {
                    port[0] = rnd.nextInt(60000) + 2000;
                } while (portCache.containsValue(port[0]));
                portCache.put(pid, port[0]);
            }
        }
        
        final Client client = new Client(port[0], ".", true, false, null);
//        final Client client = new Client(port[0]);
        final AtomicBoolean isStopEnabled = new AtomicBoolean(false);
        final AtomicBoolean isRerunEnabled = new AtomicBoolean(false);
        
        final byte[] bytecode = loadCompiledScript(scriptPath);
        
        final InputOutput io[] = new InputOutput[1];
        
        final AbstractAction[] actions = new AbstractAction[2];
        actions[0] = new AbstractAction("Rerun", new ImageIcon(Utilities.loadImage("org/netbeans/modules/btrace/ui/resources/rerun.png"))) {
            public void actionPerformed(ActionEvent e) {
                try {
                    deployScript(client, port[0], Integer.toString(pid), agentPath, bytecode, actions, isStopEnabled, isRerunEnabled, io[0]);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }

            @Override
            public boolean isEnabled() {
                return isRerunEnabled.get();
            }
        };
        actions[1] = new AbstractAction("Stop", new ImageIcon(Utilities.loadImage("org/netbeans/modules/btrace/ui/resources/stop.png"))) {
            public void actionPerformed(ActionEvent e) {
                try {
                    client.sendExit();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public boolean isEnabled() {
                return isStopEnabled.get();
            }
        };
        
        io[0] = IOProvider.getDefault().getIO("BTrace: " + scriptPath, actions);
        deployScript(client, port[0], Integer.toString(pid), agentPath, bytecode, actions, isStopEnabled, isRerunEnabled, io[0]);

    }
    
    private void deployScript(final Client client, final int port, final String pid, final String agentPath, final byte[] bytecode, final AbstractAction[] actions, final AtomicBoolean isStopEnabled, final AtomicBoolean isRerunEnabled, final InputOutput io) throws IOException {
        client.attach(pid, agentPath, null, null);

        try {
            Thread.sleep(300); // takes some time to initialize the attach agent
            RequestProcessor.getDefault().post(new Runnable() {
                public void run() {
                    try {
                    client.submit(bytecode, new String[]{}, new CommandListener() {
                        private ProgressHandle ph = ProgressHandleFactory.createHandle("BTrace", new Cancellable() {
                            public boolean cancel() {
                                try {
                                    client.sendExit();
                                } catch (Exception e) {}
                                return true;
                            }
                        });
                        public void onCommand(Command cmd) throws IOException {
                            switch (cmd.getType()) {
                                case Command.MESSAGE: {
                                    MessageCommand msg = (MessageCommand)cmd;
                                    io.getOut().println(msg.getTime() + " " + msg.getMessage());
                                    break;
                                }
                                case Command.STRING_MAP: {
                                    StringMapDataCommand msg = (StringMapDataCommand)cmd;
                                    if (msg.getData().size() > 0) {
                                        io.getOut().println("Contents of map " + msg.getName());
                                        for(Map.Entry<String, String> entry : msg.getData().entrySet()) {
                                            io.getOut().println(entry.getKey() + " : " + entry.getValue());
                                        }
                                    }
                                    break;
                                }
                                case Command.EXIT: {
                                    portCache.remove(port);
                                    isStopEnabled.set(false);
                                    isRerunEnabled.set(true);
                                    for(AbstractAction action : actions) {
                                        for(PropertyChangeListener pcl :action.getPropertyChangeListeners()) {
                                            pcl.propertyChange(new PropertyChangeEvent(action, PROP_ENABLED, null, null));
                                        }
                                    }
                                    io.getOut().println("*** Stopped ***");
                                    ph.finish();
                                    break;
                                }
                                case Command.SUCCESS: {
                                    io.getOut().reset();
                                    io.getOut().println("*** Started ***");
                                    io.getOut().println("PID: " + pid);
                                    io.getOut().println("Port: " + port);
                                    io.getOut().println("Agent path: " + agentPath);
                                    io.getOut().println("***************");
                                    io.select();
                                    isStopEnabled.set(true);
                                    isRerunEnabled.set(false);
                                    for(AbstractAction action : actions) {
                                        for(PropertyChangeListener pcl :action.getPropertyChangeListeners()) {
                                            pcl.propertyChange(new PropertyChangeEvent(action, PROP_ENABLED, null, null));
                                        }
                                    }
                                    ph.start();
                                    break;
                                }
                            }
                        }
                    });
                    } catch (IOException e) {
                        portCache.remove(port);
                        isStopEnabled.set(false);
                        isRerunEnabled.set(true);
                        for(AbstractAction action : actions) {
                            for(PropertyChangeListener pcl :action.getPropertyChangeListeners()) {
                                pcl.propertyChange(new PropertyChangeEvent(action, PROP_ENABLED, null, null));
                            }
                        }
                        io.getOut().println("*** Stopped ***");
                    } finally {
                        try {
                        client.close();
                        } catch (IOException e1) {}
                    }
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static byte[] loadCompiledScript(String scriptPath) throws IOException {
        byte[] bytecode = new byte[0];
        byte[] buffer = new byte[2048];
        
        File sriptFile = new File(scriptPath);
        FileInputStream fis = null;
        try {
        fis = new FileInputStream(sriptFile);
        int loadedBytes = 0;
        do {
            loadedBytes = fis.read(buffer);
            if (loadedBytes > 0) {
                byte[] newBytecode = new byte[bytecode.length + loadedBytes];
                System.arraycopy(bytecode, 0, newBytecode, 0, bytecode.length);
                System.arraycopy(buffer, 0, newBytecode, bytecode.length, loadedBytes);
                bytecode = newBytecode;
            }
        } while (loadedBytes > 0);
        } finally {
            try {
            fis.close();
            } catch (Exception e) {}
        }
        return bytecode;
    }
}
