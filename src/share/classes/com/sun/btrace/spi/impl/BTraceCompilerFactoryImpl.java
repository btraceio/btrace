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
                URL url = getClass().getClassLoader().getResource(clz);
                File f = new File(url.getPath());
                String jar = f.getPath();
                int lastPos = jar.lastIndexOf(".jar!");
                jar = jar.substring(5, lastPos + 4);
                f = new File(jar);
                while (jar.length() > 0 && !f.exists()) {
                    jar.substring(1);
                    f = new File(jar);
                }
                if (f.exists()) {
                    return f;
                }
                return null;
            }
        };
    }

}
