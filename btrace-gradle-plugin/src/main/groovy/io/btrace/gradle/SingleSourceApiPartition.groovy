package io.btrace.gradle

import groovy.transform.CompileStatic
import java.io.File
import java.io.FileInputStream
import java.util.ArrayDeque
import java.util.Collection
import java.util.Deque
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.List
import java.util.Map
import java.util.Set
import java.util.regex.Pattern
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InnerClassNode
import org.objectweb.asm.tree.MethodNode

/**
 * Computes the subset of authored classes that form the API surface for a single-source
 * BTrace extension. The algorithm is intentionally conservative: it starts from declared
 * service interfaces and optional manual exports, then walks bytecode signatures and
 * annotations to find other authored classes that must also live in the API JAR.
 */
@CompileStatic
final class SingleSourceApiPartition {
    private static final Pattern EXCLUDED_GENERATED =
        Pattern.compile('.*(\\$Ext|\\.btrace\\.shim\\.).*')

    private SingleSourceApiPartition() {}

    static Set<String> detectServiceTypes(Collection<File> classDirs) {
        Set<String> detected = new LinkedHashSet<>()
        indexClasses(classDirs).each { String fqcn, IndexedClass indexed ->
            if (indexed.interfaceType && indexed.hasAnnotation(BTraceDescriptors.SERVICE_DESCRIPTOR)) {
                detected.add(fqcn)
            }
        }
        return detected
    }

    static Set<String> collectAllTypes(Collection<File> classDirs) {
        return new LinkedHashSet<>(indexClasses(classDirs).keySet())
    }

    static Set<String> computeExportedTypes(
        Collection<File> classDirs,
        Collection<String> explicitServices,
        Collection<String> additionalExports,
        Collection<String> excludedExports
    ) {
        Map<String, IndexedClass> index = indexClasses(classDirs)
        Set<String> roots = new LinkedHashSet<>()
        if (explicitServices != null) {
            explicitServices.each { String fqcn ->
                if (fqcn != null && !fqcn.trim().isEmpty()) {
                    roots.add(fqcn.trim())
                }
            }
        }
        if (roots.isEmpty()) {
            roots.addAll(detectServiceTypes(classDirs))
        }
        if (additionalExports != null) {
            additionalExports.each { String fqcn ->
                if (fqcn != null && !fqcn.trim().isEmpty()) {
                    roots.add(fqcn.trim())
                }
            }
        }
        Set<String> excluded = new LinkedHashSet<>()
        if (excludedExports != null) {
            excludedExports.each { String fqcn ->
                if (fqcn != null && !fqcn.trim().isEmpty()) {
                    excluded.add(fqcn.trim())
                }
            }
        }

        Set<String> exports = new LinkedHashSet<>()
        Set<String> visited = new LinkedHashSet<>()
        Deque<String> queue = new ArrayDeque<>(roots)
        while (!queue.isEmpty()) {
            String current = queue.removeFirst()
            if (current == null || excluded.contains(current) || !visited.add(current)) {
                continue
            }
            IndexedClass indexed = index.get(current)
            if (indexed == null) {
                continue
            }
            if (isGeneratedSupport(current)) {
                continue
            }
            exports.add(current)
            indexed.referencedTypes.each { String ref ->
                if (ref == null || excluded.contains(ref) || isGeneratedSupport(ref)) {
                    return
                }
                if (index.containsKey(ref) && !visited.contains(ref)) {
                    queue.addLast(ref)
                }
            }
        }
        return exports
    }

    static Set<String> classFileIncludes(
        Collection<File> classDirs,
        Collection<String> explicitServices,
        Collection<String> additionalExports,
        Collection<String> excludedExports
    ) {
        Map<String, IndexedClass> index = indexClasses(classDirs)
        Set<String> exportedTypes = computeExportedTypes(
            classDirs, explicitServices, additionalExports, excludedExports)
        Set<String> includes = new LinkedHashSet<>()
        index.each { String fqcn, IndexedClass indexed ->
            if (exportedTypes.contains(fqcn)) {
                includes.add(indexed.relativeClassPath)
                if (indexed.hasAnnotation(BTraceDescriptors.EXTERNAL_TYPE)) {
                    IndexedClass adapter = index.get(fqcn + '$Ext')
                    if (adapter != null) {
                        includes.add(adapter.relativeClassPath)
                    }
                }
            }
        }
        // Retain package metadata; it is harmless if additional packages are present.
        classDirs.each { File dir ->
            if (dir == null || !dir.exists()) {
                return
            }
            dir.eachFileRecurse { File f ->
                if (f.name == 'package-info.class' || f.name == 'module-info.class') {
                    includes.add(dir.toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/' as char))
                }
            }
        }
        return includes
    }

    private static boolean isGeneratedSupport(String fqcn) {
        return fqcn != null && EXCLUDED_GENERATED.matcher(fqcn).matches()
    }

    private static Map<String, IndexedClass> indexClasses(Collection<File> classDirs) {
        Map<String, IndexedClass> index = new LinkedHashMap<>()
        classDirs?.each { File dir ->
            if (dir == null || !dir.exists()) {
                return
            }
            dir.eachFileRecurse { File f ->
                if (!f.name.endsWith('.class')) {
                    return
                }
                String rel = dir.toPath().relativize(f.toPath()).toString()
                String classPath = rel.replace(File.separatorChar, '/' as char)
                if (classPath == 'module-info.class' || classPath == 'package-info.class') {
                    return
                }
                FileInputStream is = new FileInputStream(f)
                try {
                    ClassReader cr = new ClassReader(is)
                    ClassNode cn = new ClassNode()
                    cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG)
                    IndexedClass indexed = IndexedClass.fromClassNode(cn, classPath)
                    index.put(indexed.fqcn, indexed)
                } finally {
                    try {
                        is.close()
                    } catch (Throwable ignore) {}
                }
            }
        }
        return index
    }

    @CompileStatic
    private static final class IndexedClass {
        final String fqcn
        final String relativeClassPath
        final boolean interfaceType
        final Set<String> referencedTypes
        final Set<String> annotations

        private IndexedClass(
            String fqcn,
            String relativeClassPath,
            boolean interfaceType,
            Set<String> referencedTypes,
            Set<String> annotations
        ) {
            this.fqcn = fqcn
            this.relativeClassPath = relativeClassPath
            this.interfaceType = interfaceType
            this.referencedTypes = referencedTypes
            this.annotations = annotations
        }

        boolean hasAnnotation(String descriptor) {
            return annotations.contains(descriptor)
        }

        static IndexedClass fromClassNode(ClassNode cn, String relativeClassPath) {
            Set<String> refs = new LinkedHashSet<>()
            Set<String> annos = new LinkedHashSet<>()
            addInternalName(refs, cn.superName)
            (cn.interfaces ?: Collections.emptyList()).each { Object name ->
                addInternalName(refs, String.valueOf(name))
            }
            collectSignatureTypes(cn.signature, refs)
            collectAnnotationNodes(cn.visibleAnnotations, refs, annos)
            collectAnnotationNodes(cn.invisibleAnnotations, refs, annos)
            (cn.fields ?: Collections.emptyList()).each { Object field ->
                FieldNode fn = (FieldNode) field
                addDescriptorTypes(refs, fn.desc)
                collectSignatureTypes(fn.signature, refs)
                collectAnnotationNodes(fn.visibleAnnotations, refs, annos)
                collectAnnotationNodes(fn.invisibleAnnotations, refs, annos)
            }
            (cn.methods ?: Collections.emptyList()).each { Object method ->
                MethodNode mn = (MethodNode) method
                addMethodDescriptorTypes(refs, mn.desc)
                collectSignatureTypes(mn.signature, refs)
                (mn.exceptions ?: Collections.emptyList()).each { Object ex ->
                    addInternalName(refs, String.valueOf(ex))
                }
                collectAnnotationNodes(mn.visibleAnnotations, refs, annos)
                collectAnnotationNodes(mn.invisibleAnnotations, refs, annos)
                collectParameterAnnotations(mn.visibleParameterAnnotations, refs, annos)
                collectParameterAnnotations(mn.invisibleParameterAnnotations, refs, annos)
            }
            (cn.innerClasses ?: Collections.emptyList()).each { Object inner ->
                InnerClassNode icn = (InnerClassNode) inner
                addInternalName(refs, icn.name)
            }
            return new IndexedClass(
                cn.name.replace('/', '.'),
                relativeClassPath,
                (cn.access & Opcodes.ACC_INTERFACE) != 0,
                refs,
                annos
            )
        }

        private static void collectParameterAnnotations(List[] params, Set<String> refs, Set<String> annos) {
            if (params == null) {
                return
            }
            params.each { List annotations ->
                collectAnnotationNodes(annotations, refs, annos)
            }
        }

        private static void collectAnnotationNodes(
            List<AnnotationNode> annotations,
            Set<String> refs,
            Set<String> annos
        ) {
            if (annotations == null) {
                return
            }
            annotations.each { AnnotationNode node ->
                annos.add(node.desc)
                addDescriptorTypes(refs, node.desc)
                collectAnnotationValues(node.values, refs)
            }
        }

        private static void collectAnnotationValues(List<Object> values, Set<String> refs) {
            if (values == null) {
                return
            }
            values.each { Object value ->
                if (value instanceof Type) {
                    addType(refs, (Type) value)
                } else if (value instanceof AnnotationNode) {
                    AnnotationNode nested = (AnnotationNode) value
                    addDescriptorTypes(refs, nested.desc)
                    collectAnnotationValues(nested.values, refs)
                } else if (value instanceof List) {
                    collectAnnotationValues((List<Object>) value, refs)
                }
            }
        }

        private static void addMethodDescriptorTypes(Set<String> refs, String desc) {
            if (desc == null || desc.isEmpty()) {
                return
            }
            Type methodType = Type.getMethodType(desc)
            addType(refs, methodType.returnType)
            methodType.argumentTypes.each { Type t -> addType(refs, t) }
        }

        private static void addDescriptorTypes(Set<String> refs, String desc) {
            if (desc == null || desc.isEmpty()) {
                return
            }
            addType(refs, Type.getType(desc))
        }

        private static void addType(Set<String> refs, Type t) {
            if (t == null) {
                return
            }
            if (t.sort == Type.OBJECT) {
                refs.add(t.className)
            } else if (t.sort == Type.ARRAY) {
                addType(refs, t.elementType)
            }
        }

        private static void addInternalName(Set<String> refs, String internalName) {
            if (internalName != null && !internalName.isEmpty()) {
                refs.add(internalName.replace('/', '.'))
            }
        }

        private static void collectSignatureTypes(String signature, Set<String> refs) {
            if (signature == null || signature.isEmpty()) {
                return
            }
            new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM9) {
                @Override
                void visitClassType(String name) {
                    addInternalName(refs, name)
                }

                @Override
                void visitInnerClassType(String name) {
                    if (name != null && !name.isEmpty()) {
                        String normalized = name.contains('/') ? name : name.replace('.', '/')
                        addInternalName(refs, normalized)
                    }
                }

                @Override
                SignatureVisitor visitSuperclass() { return this }

                @Override
                SignatureVisitor visitInterface() { return this }

                @Override
                SignatureVisitor visitParameterType() { return this }

                @Override
                SignatureVisitor visitReturnType() { return this }

                @Override
                SignatureVisitor visitExceptionType() { return this }

                @Override
                SignatureVisitor visitArrayType() { return this }

                @Override
                SignatureVisitor visitTypeArgument(char wildcard) { return this }

                @Override
                SignatureVisitor visitClassBound() { return this }

                @Override
                SignatureVisitor visitInterfaceBound() { return this }
            })
        }
    }
}
