/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.btrace.project;

import com.sun.btrace.CommandListener;
import com.sun.btrace.client.Client;
import com.sun.btrace.comm.Command;
import com.sun.btrace.comm.DataCommand;
import com.sun.btrace.comm.MessageCommand;
import com.sun.btrace.comm.StringMapDataCommand;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import org.apache.tools.ant.module.api.support.ActionUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.api.project.Project;
import org.netbeans.modules.btrace.project.BTraceActionProvider;
import org.netbeans.modules.btrace.project.BTraceProject;
import org.netbeans.modules.btrace.project.BTraceProjectUtil;
import org.netbeans.modules.btrace.project.ui.actions.RerunBTraceScriptAction;
import org.netbeans.modules.btrace.ui.EventChooser;
import org.netbeans.spi.project.ActionProvider;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Cancellable;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;
import org.openide.util.actions.SystemAction;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceManager {
    private final static class BTraceManagerFactory {
        public static BTraceManager INSTANCE = new BTraceManager();
    }
    
    private final static String PROP_ENABLED = "enabled"; // NOI18N
    private final Map<Integer, Integer> portCache = new HashMap<Integer, Integer>();
    private Random rnd;
    private String agentPath;
    
    private BTraceManager() {
        rnd = new Random(System.currentTimeMillis());
        File locatedFile = InstalledFileLocator.getDefault().locate("modules/ext/btrace-agent.jar", "org.netbeans.modules.btrace.project", false);
        agentPath = locatedFile.getAbsolutePath();
    }

    public static BTraceManager sharedInstance() {
        return BTraceManagerFactory.INSTANCE;
    }
       
    public void deploy(final String scriptPath, final int pid) throws IOException {
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
        
//        final Client client = new Client(port[0], ".", true, false, null);
        final Client client = new Client(port[0]);
        final AtomicBoolean isStopEnabled = new AtomicBoolean(false);
        final AtomicBoolean isRerunEnabled = new AtomicBoolean(false);
        
        final AtomicReference<byte[]> bytecode = new AtomicReference<byte[]>(loadCompiledScript(scriptPath));
                
        final Action[] actions = new Action[3];
//        actions[0] = new AbstractAction("Rerun", new ImageIcon(Utilities.loadImage("org/netbeans/modules/btrace/ui/resources/rerun.png"))) {
//            public void actionPerformed(ActionEvent e) {
//                try {
//                    Project prj = FileOwnerQuery.getOwner(FileUtil.toFileObject(new File(scriptPath)));
//                    BTraceActionProvider ap = prj.getLookup().lookup(BTraceActionProvider.class);
//                    recompile(prj, ap);
//                    
//                    bytecode.set(loadCompiledScript(scriptPath));
//                    deployScript(client, port[0], Integer.toString(pid), agentPath, bytecode.get(), actions, isStopEnabled, isRerunEnabled, IOProvider.getDefault().getIO("BTrace: " + scriptPath, false));
//                } catch (IOException ex) {
//                    Exceptions.printStackTrace(ex);
//                }
//            }
//
//            @Override
//            public boolean isEnabled() {
//                return isRerunEnabled.get();
//            }
//        };
        actions[0] = SystemAction.findObject(RerunBTraceScriptAction.class);
        actions[1] = new AbstractAction("Send Event...", new ImageIcon(Utilities.loadImage("org/netbeans/modules/btrace/ui/resources/event.png"))) {
            private Set<String> eventNames = getUsedEvents(bytecode.get());
            public void actionPerformed(ActionEvent e) {
                EventChooser panel = EventChooser.getChooser(eventNames);
                DialogDescriptor dd = new DialogDescriptor(panel, "Send Event");
                Object result = DialogDisplayer.getDefault().notify(dd);
                if (result == DialogDescriptor.OK_OPTION) {
                    try {
                        client.sendEvent(panel.getSelectedEvent());
                    } catch (IOException ex) {}
                }
                
            }
            
            @Override
            public boolean isEnabled() {
                return isStopEnabled.get() && eventNames.size() > 0;
            }
        };
        actions[2] = new AbstractAction("Stop", new ImageIcon(Utilities.loadImage("org/netbeans/modules/btrace/ui/resources/stop.png"))) {
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
                
        deployScript(client, port[0], Integer.toString(pid), agentPath, bytecode.get(), actions, isStopEnabled, isRerunEnabled, IOProvider.getDefault().getIO("BTrace: " + scriptPath, actions));

    }

    private void recompile(Project project, BTraceActionProvider ap) throws IOException {
        Properties p = new Properties();
        String[] targetNames = ap.getTargetNames(ActionProvider.COMMAND_COMPILE_SINGLE, Lookup.EMPTY, p);
        FileObject buildXML = BTraceProjectUtil.getBuildXml((BTraceProject)project);
        
        assert targetNames != null;
        assert targetNames.length > 0;
        
        ActionUtils.runTarget(buildXML, targetNames, p).waitFinished();
    }
    
    private void deployScript(final Client client, final int port, final String pid, final String agentPath, final byte[] bytecode, final Action[] actions, final AtomicBoolean isStopEnabled, final AtomicBoolean isRerunEnabled, final InputOutput io) throws IOException {
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
                                case Command.MESSAGE:
                                case Command.GRID_DATA:
                                case Command.NUMBER_MAP:
                                case Command.STRING_MAP: {
                                    DataCommand msg = (DataCommand)cmd;
                                    msg.print(io.getOut());
                                    io.getOut().flush();
                                    break;
                                }
                                case Command.EXIT: {
                                    portCache.remove(port);
                                    isStopEnabled.set(false);
                                    isRerunEnabled.set(true);
                                    for(Action action : actions) {
                                        if (action instanceof AbstractAction) {
                                            for(PropertyChangeListener pcl : ((AbstractAction)action).getPropertyChangeListeners()) {
                                                pcl.propertyChange(new PropertyChangeEvent(action, PROP_ENABLED, null, null));
                                            }
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
                                    for(Action action : actions) {
                                        if (action instanceof AbstractAction) {
                                            for(PropertyChangeListener pcl : ((AbstractAction)action).getPropertyChangeListeners()) {
                                                pcl.propertyChange(new PropertyChangeEvent(action, PROP_ENABLED, null, null));
                                            }
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
                        for(Action action : actions) {
                            if (action instanceof AbstractAction) {
                                for(PropertyChangeListener pcl : ((AbstractAction)action).getPropertyChangeListeners()) {
                                    pcl.propertyChange(new PropertyChangeEvent(action, PROP_ENABLED, null, null));
                                }
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
    
    private static Set<String> getUsedEvents(byte[] bytecode) {
        final Set<String> usedEvents = new HashSet<String>();
        ClassReader cr = new ClassReader(bytecode);
        cr.accept(new ClassVisitor() {

            public void visit(int arg0, int arg1, String arg2, String arg3, String arg4, String[] arg5) {
                //
            }

            public void visitSource(String arg0, String arg1) {
                //
            }

            public void visitOuterClass(String arg0, String arg1, String arg2) {
                //
            }

            public AnnotationVisitor visitAnnotation(String name, boolean isVisible) {
                return null;
            }

            public void visitAttribute(Attribute arg0) {
                //
            }

            public void visitInnerClass(String arg0, String arg1, String arg2, int arg3) {
                //
            }

            public FieldVisitor visitField(int arg0, String arg1, String arg2, String arg3, Object arg4) {
                return null;
            }

            public MethodVisitor visitMethod(int arg0, String arg1, String arg2, String arg3, String[] arg4) {
                return new MethodVisitor() {

                    public AnnotationVisitor visitAnnotationDefault() {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }

                    public AnnotationVisitor visitAnnotation(String name, boolean isVisible) {
                        if (name.contains("OnEvent")) {
                            return new AnnotationVisitor() {

                                public void visit(String attrKey, Object attrValue) {
                                    if (attrValue != null && attrKey.equals("value")) {
                                        usedEvents.add(attrValue.toString());
                                    }
                                }

                                public void visitEnum(String arg0, String arg1, String arg2) {
                                    //
                                }

                                public AnnotationVisitor visitAnnotation(String arg0, String arg1) {
                                    return null;
                                }

                                public AnnotationVisitor visitArray(String arg0) {
                                    return null;
                                }

                                public void visitEnd() {
                                    //
                                }
                            };
                        }
                        return null;
                    }

                    public AnnotationVisitor visitParameterAnnotation(int arg0, String arg1, boolean arg2) {
                        return null;
                    }

                    public void visitAttribute(Attribute arg0) {
                        //
                    }

                    public void visitCode() {
                        //
                    }

                    public void visitFrame(int arg0, int arg1, Object[] arg2, int arg3, Object[] arg4) {
                        //
                    }

                    public void visitInsn(int arg0) {
                        //
                    }

                    public void visitIntInsn(int arg0, int arg1) {
                        //
                    }

                    public void visitVarInsn(int arg0, int arg1) {
                        //
                    }

                    public void visitTypeInsn(int arg0, String arg1) {
                        //
                    }

                    public void visitFieldInsn(int arg0, String arg1, String arg2, String arg3) {
                        //
                    }

                    public void visitMethodInsn(int arg0, String arg1, String arg2, String arg3) {
                        //
                    }

                    public void visitJumpInsn(int arg0, Label arg1) {
                        //
                    }

                    public void visitLabel(Label arg0) {
                        //
                    }

                    public void visitLdcInsn(Object arg0) {
                        //
                    }

                    public void visitIincInsn(int arg0, int arg1) {
                        //
                    }

                    public void visitTableSwitchInsn(int arg0, int arg1, Label arg2, Label[] arg3) {
                        //
                    }

                    public void visitLookupSwitchInsn(Label arg0, int[] arg1, Label[] arg2) {
                        //
                    }

                    public void visitMultiANewArrayInsn(String arg0, int arg1) {
                        //
                    }

                    public void visitTryCatchBlock(Label arg0, Label arg1, Label arg2, String arg3) {
                        //
                    }

                    public void visitLocalVariable(String arg0, String arg1, String arg2, Label arg3, Label arg4, int arg5) {
                        //
                    }

                    public void visitLineNumber(int arg0, Label arg1) {
                        //
                    }

                    public void visitMaxs(int arg0, int arg1) {
                        //
                    }

                    public void visitEnd() {
                        //
                    }
                };
            }

            public void visitEnd() {
                //
            }
        }, ClassReader.SKIP_DEBUG + ClassReader.SKIP_CODE + ClassReader.SKIP_FRAMES);
        return usedEvents;
    }
}
