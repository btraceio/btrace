/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.spi.impl;

import com.sun.btrace.api.BTraceCompiler;
import com.sun.btrace.api.BTraceTask;
import com.sun.btrace.spi.BTraceCompilerFactory;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Jaroslav Bachorik <yardus@netbeans.org>
 */
final public class BTraceCompilerFactoryImpl implements BTraceCompilerFactory {
    final private static Logger LOGGER = Logger.getLogger(BTraceCompilerFactory.class.getName());

    final private static Pattern classNamePattern = Pattern.compile("@BTrace\\s*.+?\\s*class\\s*(.*?)\\s+\\{", Pattern.MULTILINE | Pattern.DOTALL | Pattern.UNIX_LINES);

    public BTraceCompiler newCompiler(final BTraceTask task) {
        return new BTraceCompiler() {
            final private com.sun.btrace.compiler.Compiler c = new com.sun.btrace.compiler.Compiler(null, task.isUnsafe());

            @Override
            public byte[] compile(String source, String classPath, Writer errorWriter) {
                try {
                    Matcher matcher = classNamePattern.matcher(source);
                    if (matcher.find()) {
                        if (errorWriter == null) {
                            errorWriter = new PrintWriter(System.out);
                        }
                        String fileName = matcher.group(1) + ".java";
                        String completeCP = getToolsJarPath() + File.pathSeparator + getClientJarPath() + File.pathSeparator + classPath;
                        Map<String, byte[]> compilationMap =c.compile(fileName, source, errorWriter, ".", completeCP);
                        if (compilationMap != null) {
                            return compilationMap.values().iterator().next();
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, null, e);
                }
                return new byte[0];
            }

            @Override
            public String getAgentJarPath() {
                return getJarBaseDir() + File.separatorChar + "btrace-agent.jar";
            }

            @Override
            public String getClientJarPath() {
                return getJarBaseDir() + File.separatorChar + "btrace-client.jar";
            }

            final private Object toolsJarLock = new Object();
            private String toolsJar = null;
            @Override
            public String getToolsJarPath() {
                synchronized(toolsJarLock) {
                    if (toolsJar == null) {
                        File toolsJarFile = getContainingJar("com/sun/tools/javac/Main.class");
                        if (toolsJarFile != null) {
                            toolsJar = toolsJarFile.getAbsolutePath();
                        }
                    }
                    return toolsJar;
                }
            }

            final private Object jarBaseDirLock = new Object();
            private String jarBaseDir = null;

            private String getJarBaseDir() {
                synchronized(jarBaseDirLock) {
                    if (jarBaseDir == null) {
                        File f = getContainingJar("com/sun/btrace/BTraceRuntime.class");
                        while (f != null && (!f.exists() || !f.isDirectory())) {
                            f = f.getParentFile();
                        }
                        if (f != null) {
                            jarBaseDir = f.getAbsolutePath();
                        }
                    }
                    return jarBaseDir;
                }
            }

            private File getContainingJar(String clz) {
                File jarFile;
                URL url = getClass().getClassLoader().getResource(clz);
                if ("jar".equals(url.getProtocol())) { //NOI18N

                    String path = url.getPath();
                    int index = path.indexOf("!/"); //NOI18N

                    if (index >= 0) {
                        try {
                            String jarPath = path.substring(0, index);
                            if (jarPath.indexOf("file://") > -1 && jarPath.indexOf("file:////") == -1) {  //NOI18N
                                /* Replace because JDK application classloader wrongly recognizes UNC paths. */
                                jarPath = jarPath.replaceFirst("file://", "file:////");  //NOI18N
                            }
                            url = new URL(jarPath);

                        } catch (MalformedURLException mue) {
                            throw new RuntimeException(mue);
                        }
                    }
                }
                try {
                    jarFile = new File(url.toURI());
                } catch (URISyntaxException ex) {
                    throw new RuntimeException(ex);
                }
                assert jarFile.exists();
                return jarFile;
            }
        };
    }

}
