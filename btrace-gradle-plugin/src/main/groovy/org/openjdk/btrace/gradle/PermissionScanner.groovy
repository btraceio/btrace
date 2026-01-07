package org.openjdk.btrace.gradle

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.ArrayDeque
import java.util.Collection
import java.util.Collections
import java.util.Deque
import java.util.List
import java.util.Set
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.LdcInsnNode

/**
 * Scans a compiled extension implementation JAR to infer required permissions
 * based on referenced JDK/runtime APIs.
 */
final class PermissionScanner {

    static Set<String> scan(File jarFile, Collection<File> classpath = Collections.emptyList()) {
        Set<String> perms = [] as Set
        if (jarFile == null || !jarFile.exists()) return perms

        List<File> sources = []
        sources.add(jarFile)
        classpath?.each { File f ->
            if (f == null || !f.exists()) return
            def path = f.absolutePath
            if (path.contains('/btrace-core') || path.contains('/btrace-runtime') || path.contains('/btrace-instr') || path.contains('/btrace-agent')) return
            sources.add(f)
        }

        Set<String> visited = [] as Set
        Deque<String> queue = new ArrayDeque<>()
        new ZipFile(jarFile).withCloseable { zip ->
            zip.entries().each { e ->
                if (!e.isDirectory() && e.name.endsWith('.class')) {
                    queue.add(e.name.substring(0, e.name.length() - '.class'.length()))
                }
            }
        }

        while (!queue.isEmpty()) {
            String cn = queue.removeFirst()
            if (!visited.add(cn)) continue
            InputStream is = openClassStream(cn, sources)
            if (is == null) continue
            try {
                ClassReader cr = new ClassReader(is)
                ClassNode node = new ClassNode()
                cr.accept(node, 0)
                node.methods.each { m ->
                    for (AbstractInsnNode insn = m.instructions.first; insn != null; insn = insn.next) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn
                            handleMethod(min, perms, m, insn)
                            String owner = min.owner
                            if (owner != null && !owner.startsWith('java/') && !owner.startsWith('javax/')) {
                                queue.add(owner)
                            }
                        } else if (insn instanceof FieldInsnNode) {
                            handleField(((FieldInsnNode) insn), perms)
                        }
                    }
                }
            } finally {
                is.close()
            }
        }
        return perms
    }

    private static void scanClass(InputStream in, Set<String> perms) {
        ClassReader cr = new ClassReader(in)
        ClassNode cn = new ClassNode()
        cr.accept(cn, 0)

        cn.methods.each { m ->
            for (AbstractInsnNode insn = m.instructions.first; insn != null; insn = insn.next) {
                if (insn instanceof MethodInsnNode) {
                    handleMethod(((MethodInsnNode) insn), perms, m, insn)
                } else if (insn instanceof FieldInsnNode) {
                    handleField(((FieldInsnNode) insn), perms)
                } else if (insn instanceof InvokeDynamicInsnNode) {
                    // no-op for now
                }
            }
        }
    }

    private static void handleMethod(MethodInsnNode min, Set<String> perms, def methodNode, AbstractInsnNode current) {
        String owner = min.owner
        String name = min.name

        if (owner.startsWith('java/net/') || owner.startsWith('javax/net/')) {
            perms.add('NETWORK')
        }
        if (owner.startsWith('java/io/') || owner.startsWith('java/nio/file/')) {
            if (owner == 'java/io/FileOutputStream' || owner == 'java/io/FileWriter' || owner == 'java/io/PrintWriter') {
                perms.add('FILE_WRITE'); perms.add('FILE_READ')
            } else if (owner == 'java/io/RandomAccessFile') {
                String mode = findPreviousLdcString(methodNode, current, 8)
                if ('r'.equals(mode)) {
                    perms.add('FILE_READ')
                } else {
                    perms.add('FILE_WRITE'); perms.add('FILE_READ')
                }
            } else if (owner.startsWith('java/nio/file/Files')) {
                if (name.startsWith('newOutputStream') || name.startsWith('write') || name.startsWith('create') || name.startsWith('delete')) {
                    perms.add('FILE_WRITE')
                } else if (name.startsWith('newInputStream') || name.startsWith('read')) {
                    perms.add('FILE_READ')
                }
            } else if (owner == 'java/io/FileInputStream' || owner == 'java/io/BufferedInputStream' || owner == 'java/io/InputStreamReader') {
                perms.add('FILE_READ')
            }
        }
        if (owner == 'java/io/File') {
            if (name in ['delete', 'deleteOnExit', 'renameTo', 'mkdir', 'mkdirs', 'createNewFile', 'setReadable', 'setWritable', 'setExecutable']) {
                perms.add('FILE_WRITE')
            }
        }
        if (owner == 'java/nio/file/Files') {
            if (name in ['createFile','createDirectory','createDirectories','delete','deleteIfExists','move','setAttribute','setPosixFilePermissions','copy','write']) {
                perms.add('FILE_WRITE')
            }
        }
        if ((owner == 'java/nio/channels/FileChannel' || owner == 'java/nio/channels/AsynchronousFileChannel') && name == 'open') {
            if (hasOpenOptionWrite(methodNode, current, 20)) {
                perms.add('FILE_WRITE')
            } else {
                perms.add('FILE_READ')
            }
        }
        if (owner == 'java/util/logging/FileHandler' && name == '<init>') {
            perms.add('FILE_WRITE')
        }
        if (owner == 'java/lang/Thread' || owner.startsWith('java/util/concurrent/')) {
            perms.add('THREADS')
        }
        if (owner == 'sun/misc/Unsafe' || owner == 'jdk/internal/misc/Unsafe') {
            perms.add('NATIVE')
        }
        if (owner == 'java/lang/System' && (name == 'load' || name == 'loadLibrary')) {
            perms.add('NATIVE')
        }
        if (owner == 'java/lang/Runtime' && name == 'exec') {
            perms.add('EXEC')
        }
        if (owner == 'java/lang/ProcessBuilder' && name == '<init>') {
            perms.add('EXEC')
        }
        if (owner.startsWith('java/lang/reflect/')) {
            perms.add('REFLECTION')
        }
        if (owner == 'java/lang/ClassLoader' || owner == 'java/lang/Class' && name.startsWith('forName')) {
            perms.add('CLASSLOADER')
        }
        if (owner == 'java/lang/System' && name == 'getProperty') {
            perms.add('SYSTEM_PROPS')
        }
        if (owner == 'java/lang/management/ManagementFactory') {
            perms.add('THREAD_INFO'); perms.add('MEMORY_INFO')
        }
        if (owner.startsWith('com/sun/management/')) {
            perms.add('MEMORY_INFO')
        }
        if (owner.startsWith('jdk/jfr/') || owner.startsWith('org/openjdk/btrace/core/jfr/')) {
            perms.add('JFR_EVENTS')
        }
    }

    private static void handleField(FieldInsnNode fin, Set<String> perms) {
        String owner = fin.owner
        String name = fin.name
        if (owner == 'java/lang/System' && name == 'props') {
            perms.add('SYSTEM_PROPS')
        }
    }

    private static String findPreviousLdcString(def methodNode, AbstractInsnNode from, int window) {
        if (methodNode == null || from == null) return null
        int count = 0
        for (AbstractInsnNode p = from.previous; p != null && count < window; p = p.previous, count++) {
            if (p.opcode == Opcodes.LDC && p instanceof LdcInsnNode) {
                def cst = ((LdcInsnNode) p).cst
                if (cst instanceof String) return (String) cst
            }
        }
        return null
    }

    private static boolean hasOpenOptionWrite(def methodNode, AbstractInsnNode from, int window) {
        if (methodNode == null || from == null) return true
        int count = 0
        for (AbstractInsnNode p = from.previous; p != null && count < window; p = p.previous, count++) {
            if (p instanceof FieldInsnNode) {
                FieldInsnNode fin = (FieldInsnNode) p
                if (fin.owner == 'java/nio/file/StandardOpenOption') {
                    if (['WRITE','APPEND','CREATE','CREATE_NEW','TRUNCATE_EXISTING','DELETE_ON_CLOSE'].contains(fin.name)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private static InputStream openClassStream(String internalName, List<File> sources) {
        String entry = internalName.endsWith('.class') ? internalName : (internalName + '.class')
        for (File f : sources) {
            if (f.isDirectory()) {
                File cf = new File(f, entry)
                if (cf.exists()) return new FileInputStream(cf)
            } else if (f.name.endsWith('.jar')) {
                try {
                    def zip = new ZipFile(f)
                    def ze = zip.getEntry(entry)
                    if (ze != null) {
                        return zip.getInputStream(ze)
                    } else {
                        zip.close()
                    }
                } catch (IOException ignored) { }
            }
        }
        return null
    }
}

