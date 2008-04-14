/*
 * TestTask.java
 *
 * Created on July 29, 2006, 11:33 PM
 */
package org.netbeans.modules.btrace.project.ant;

// IMPORTANT! You need to compile this class against ant.jar. So add the
// JAR ide5/ant/lib/ant.jar from your IDE installation directory (or any
// other version of Ant you wish to use) to your classpath. Or if
// writing your own build target, use e.g.:
// <classpath>
//     <pathelement location="${ant.home}/lib/ant.jar"/>
// </classpath>
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.resources.FileResource;

/**
 * @author Jaroslav Bachorik
 */
public class CompilerTask extends Task {
    private final Set<FileSet> filesets = new HashSet<FileSet>();
    private String classPath = "";
    private String sourcePath = "";
    private String destDir = "";
    
    public void addFileSet(FileSet set) {
        filesets.add(set);
    }
    
    public void setClasspath(String cp) {
        classPath = cp;
    }
    
    public void setSourcepath(String sp) {
        sourcePath = sp;
    }
    
    public void setDestdir(String dd) {
        destDir = dd;
    }
        
    @Override
    public void execute() throws BuildException {
        com.sun.btrace.compiler.Compiler compiler = new com.sun.btrace.compiler.Compiler();
        List<File> filesToCompile = new ArrayList<File>();
        for(FileSet fileset : filesets) {
            for (Iterator iter = fileset.iterator(); iter.hasNext();) {
                FileResource resource = (FileResource)iter.next();
                filesToCompile.add(resource.getFile());
            }
        }
        try {
            Map<String, byte[]> compiledCode = compiler.compile(filesToCompile.toArray(new File[filesToCompile.size()]), new PrintWriter(System.err), sourcePath, classPath);
            if (compiledCode != null) {
                for(Map.Entry<String, byte[]> entry : compiledCode.entrySet()) {
                    System.out.println(entry.getKey() + " : " + entry.getValue().length + "bytes");
                    writeClass(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(CompilerTask.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    private void writeClass(String name, byte[] contents) throws IOException {
        String targetFileName = getProject().getBaseDir().getAbsolutePath() + File.separator + destDir + File.separator + name.replace('.', File.separatorChar) + ".class";
        int pathIndex = targetFileName.lastIndexOf(File.separator);
        if (pathIndex > -1) {
            String pathName = targetFileName.substring(0, pathIndex);
            File path = new File(pathName);
            if (!path.exists() && !path.mkdirs()) {
                throw new IOException("Can't create output folder " + pathName);
            }
        }
        System.out.println("Going to write to " + targetFileName);
        
        File targetFile = new File(targetFileName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(targetFile);
            fos.write(contents);
        } finally {
            try {
                fos.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
