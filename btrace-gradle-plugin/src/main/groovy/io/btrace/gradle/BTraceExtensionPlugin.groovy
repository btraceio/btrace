package io.btrace.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.Copy
import org.gradle.api.file.DuplicatesStrategy
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeAnnotationNode
import org.objectweb.asm.TypeReference
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.Opcodes

class BTraceExtensionPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // Apply required plugins
        project.plugins.apply('java')
        // Optionally apply maven-publish when available so we can publish artifacts easily
        try { project.plugins.apply('maven-publish') } catch (Throwable ignore) {}

        // Try to ensure Shadow is available; if resolution is blocked, we will emit a clear
        // error later with guidance. This is best-effort and safe when already applied.
        try {
            if (!project.pluginManager.hasPlugin('com.gradleup.shadow')) {
                // Respect opt-out
                def ext = project.extensions.findByType(BTraceExtensionMetadata)
                boolean shouldAutoApply = (ext == null) ? true : (ext.autoApplyShadow != false)
                if (shouldAutoApply) {
                    project.logger.lifecycle("[BTRACE-EXT] Applying Shadow plugin automatically (com.gradleup.shadow) for ${project.path}")
                    project.pluginManager.apply('com.gradleup.shadow')
                } else {
                    project.logger.lifecycle("[BTRACE-EXT] Shadow auto-apply disabled (btraceExtension.autoApplyShadow=false) for ${project.path}")
                }
            }
        } catch (Throwable t) {
            project.logger.warn("[BTRACE-EXT] Unable to auto-apply Shadow plugin: ${t.message}. Apply it explicitly via plugins { id 'com.gradleup.shadow' } or alias(libs.plugins.shadow), or set btraceExtension.autoApplyShadow=true.")
        }

        // Create extension for metadata
        def extension = project.extensions.create('btraceExtension', BTraceExtensionMetadata)
        extension.version = project.version
        def authoredSourceSet = {
            project.sourceSets.main
        }
        def authoredOutput = {
            authoredSourceSet().output
        }
        def authoredClassesDirs = {
            authoredOutput().classesDirs.files
        }
        def authoredResourcesDir = {
            authoredSourceSet().output.resourcesDir
        }
        def authoredCompileClasspath = {
            authoredSourceSet().compileClasspath.files
        }
        def authoredCompileTask = {
            project.tasks.named('compileJava')
        }
        def explicitServiceTypes = {
            def services = extension.services as List<String>
            if (services != null && !services.isEmpty()) {
                return services
            }
            return SingleSourceApiPartition.detectServiceTypes(authoredClassesDirs()) as List<String>
        }
        def apiIncludes = {
            return SingleSourceApiPartition.classFileIncludes(
                authoredClassesDirs(),
                explicitServiceTypes(),
                extension.additionalExports,
                extension.excludedExports)
        }
        def apiTypes = {
            return SingleSourceApiPartition.computeExportedTypes(
                authoredClassesDirs(),
                explicitServiceTypes(),
                extension.additionalExports,
                extension.excludedExports)
        }
        def isExportedApiType = { String fqcn ->
            apiTypes().contains(fqcn)
        }
        def implTypes = {
            def allTypes = SingleSourceApiPartition.collectAllTypes(authoredClassesDirs())
            allTypes.removeAll(apiTypes())
            return allTypes
        }
        def apiSourceIncludes = {
            def includes = [] as LinkedHashSet<String>
            def packages = [] as LinkedHashSet<String>
            apiTypes().each { String fqcn ->
                includes.add(fqcn.replace('.', '/') + '.java')
                int idx = fqcn.lastIndexOf('.')
                packages.add(idx >= 0 ? fqcn.substring(0, idx) : '')
            }
            packages.each { String pkg ->
                if (pkg == null || pkg.isEmpty()) {
                    includes.add('package-info.java')
                } else {
                    includes.add(pkg.replace('.', '/') + '/package-info.java')
                }
            }
            includes.add('module-info.java')
            return includes
        }
        def apiSourceTree = {
            project.fileTree(dir: project.file('src/main/java'), includes: apiSourceIncludes() as List<String>)
        }

        // Configure dependency configurations
        project.configurations {
            apiImplementation.extendsFrom implementation
            apiCompileOnly.extendsFrom compileOnly

            implImplementation.extendsFrom implementation
            implImplementation.extendsFrom apiImplementation
            implCompileOnly.extendsFrom compileOnly
            implRuntimeClasspath {
                canBeConsumed = false
                canBeResolved = true
                extendsFrom implImplementation
            }
        }
        project.configurations.compileClasspath.extendsFrom(
            project.configurations.apiCompileOnly,
            project.configurations.implCompileOnly,
            project.configurations.implImplementation,
            project.configurations.apiImplementation)
        project.configurations.runtimeClasspath.extendsFrom(
            project.configurations.implImplementation,
            project.configurations.apiImplementation)
        project.configurations.testCompileClasspath.extendsFrom(
            project.configurations.apiCompileOnly,
            project.configurations.implCompileOnly,
            project.configurations.implImplementation,
            project.configurations.apiImplementation)
        project.configurations.testRuntimeClasspath.extendsFrom(
            project.configurations.apiCompileOnly,
            project.configurations.implCompileOnly,
            project.configurations.implImplementation,
            project.configurations.apiImplementation)

        // Auto-register @ExternalType annotation processor on the main source set.
        // In-tree builds reference the sibling subproject directly; external consumers resolve
        // the published artifact by version.
        def processorProject = project.rootProject.findProject(':btrace-core')
        if (processorProject != null) {
            project.dependencies.add('annotationProcessor', processorProject)
        } else {
            project.dependencies.add('annotationProcessor',
                "io.btrace:btrace-core:${project.version}")
        }

        // Configure duplicate handling for resource tasks
        project.tasks.withType(Copy).configureEach {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        // Task: Build API JAR
        def buildApiJar = project.tasks.register('buildApiJar', Jar) {
            dependsOn(authoredCompileTask, project.tasks.named('processResources'))
            from({
                return project.files(authoredClassesDirs()).asFileTree.matching {
                    include apiIncludes()
                }
            })
            from({
                def resourcesDir = authoredResourcesDir()
                return resourcesDir != null ? project.files(resourcesDir) : project.files()
            })
            archiveClassifier = 'api'
            archiveBaseName = project.name
        }

        // Task: Validate API service interfaces (practical subset of rules)
        def validateServiceApis = project.tasks.register('validateServiceApis') {
            dependsOn(authoredCompileTask)
            doLast {
                // Lint: API classes with public constructors (discouraged; use service-provided builders)
                def apiCtorIssues = [] as List<String>
                authoredClassesDirs().each { File dir ->
                    if (!dir.exists()) return
                    dir.eachFileRecurse { f ->
                        if (!f.name.endsWith('.class')) return
                        def rel = dir.toPath().relativize(f.toPath()).toString()
                        def internalName = rel.replace(File.separatorChar, (char)'/').replaceAll(/\.class$/, '')
                        if (internalName.endsWith('package-info') || internalName.endsWith('module-info')) return
                        def fqcn = internalName.replace('/', '.')
                        if (!isExportedApiType(fqcn)) return
                        FileInputStream is = new FileInputStream(f)
                        byte[] bytes
                        try { bytes = is.bytes } finally { try { is.close() } catch (Throwable ignore) {} }
                        def cr = new ClassReader(bytes)
                        def cn = new ClassNode()
                        cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG)
                        int acc = cn.access
                        boolean isInterface = (acc & Opcodes.ACC_INTERFACE) != 0
                        boolean isAnnotation = (acc & Opcodes.ACC_ANNOTATION) != 0
                        boolean isPublic = (acc & Opcodes.ACC_PUBLIC) != 0
                        if (isPublic && !isInterface && !isAnnotation) {
                            boolean hasPublicCtor = false
                            (cn.methods ?: []).each { MethodNode mn ->
                                if ("<init>".equals(mn.name) && (mn.access & Opcodes.ACC_PUBLIC) != 0) {
                                    hasPublicCtor = true
                                }
                            }
                            if (hasPublicCtor) {
                                apiCtorIssues << internalName.replace('/', '.')
                            }
                        }
                    }
                }
                if (!apiCtorIssues.isEmpty()) {
                    def sev = (project.extensions.findByType(BTraceExtensionMetadata)?.apiCtorSeverity ?: 'warn')
                    def msg = "[BTRACE-EXT] API classes with public constructors (avoid 'new' in probes; provide service builders/factories instead): ${apiCtorIssues}"
                    if ("error".equalsIgnoreCase(sev)) {
                        throw new GradleException(msg)
                    } else if (!"off".equalsIgnoreCase(sev)) {
                        project.logger.warn(msg)
                    }
                }
                // Enforce: at most one package-level @ExtensionDescriptor across the project
                int extDescCount = 0
                def scanForPkgDescriptors = { File classesDir ->
                    if (classesDir == null || !classesDir.exists()) return
                    classesDir.eachFileRecurse { f ->
                        if (!f.name.endsWith('.class')) return
                        if (!f.name.equals('package-info.class')) return
                        FileInputStream is = new FileInputStream(f)
                        byte[] bytes
                        try { bytes = is.bytes } finally { try { is.close() } catch (Throwable ignore) {} }
                        def cr = new ClassReader(bytes)
                        def cn = new ClassNode()
                        cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG)
                        boolean has = false
                        (cn.visibleAnnotations ?: []).each { has |= BTraceDescriptors.isExtensionDescriptor(it.desc) }
                        (cn.invisibleAnnotations ?: []).each { has |= BTraceDescriptors.isExtensionDescriptor(it.desc) }
                        if (has) extDescCount++
                    }
                }
                authoredClassesDirs().each { scanForPkgDescriptors(it) }
                if (extDescCount > 1) {
                    throw new GradleException("[BTRACE-EXT] Found ${extDescCount} package-level @ExtensionDescriptor annotations. Only one is allowed per extension project (place it in API package-info.java).")
                }

                // Prefer explicitly configured services; otherwise, detect via @ServiceDescriptor annotations
                def services = extension.services as List<String>
                if (!services || services.isEmpty()) {
                    def detected = new LinkedHashSet<String>()
                    authoredClassesDirs().each { File dir ->
                        if (!dir.exists()) return
                        dir.eachFileRecurse { f ->
                            if (!f.name.endsWith('.class')) return
                            def rel = dir.toPath().relativize(f.toPath()).toString()
                            def fq = rel.replace(File.separatorChar, (char)'.').replaceAll(/\.class$/, '')
                            if (!isExportedApiType(fq)) return
                            FileInputStream is = new FileInputStream(f)
                            byte[] bytes
                            try { bytes = is.bytes } finally { try { is.close() } catch (Throwable ignore) {} }
                            def cr = new ClassReader(bytes)
                            def cn = new ClassNode()
                            cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG)
                            def isInterface = (cn.access & Opcodes.ACC_INTERFACE) != 0
                            def hasMarker = false
                            (cn.visibleAnnotations ?: []).each { hasMarker |= BTraceDescriptors.isServiceDescriptor(it.desc) }
                            (cn.invisibleAnnotations ?: []).each { hasMarker |= BTraceDescriptors.isServiceDescriptor(it.desc) }
                            if (hasMarker && isInterface) detected.add(fq)
                        }
                    }
                    services = detected as List<String>
                    if (!services.isEmpty()) {
                        project.logger.lifecycle("[BTRACE-EXT] detected services via @ServiceDescriptor: ${services}")
                    } else {
                        project.logger.warn('[BTRACE-EXT] No services declared or detected; skipping validation')
                        return
                    }
                }
                def cp = new LinkedHashSet<File>()
                cp.addAll(authoredClassesDirs())
                try { cp.addAll(authoredCompileClasspath()) } catch (Throwable ignore) {}
                URLClassLoader cl = new URLClassLoader(cp.collect { it.toURI().toURL() } as URL[], (ClassLoader) null)
                def errors = []
                def warnings = []
                def perService = new LinkedHashMap<String, List<String>>()
                def perServiceWarn = new LinkedHashMap<String, List<String>>()

                // Build set of impl class names for purity checks
                def implClassNames = new HashSet<String>(implTypes())

                // Nullability annotation descriptors (configurable)
                def toDesc = { String fqcn -> 'L' + fqcn.replace('.', '/') + ';' }
                def defaultNullable = ['javax.annotation.Nullable','org.jspecify.annotations.Nullable','org.jetbrains.annotations.Nullable','jakarta.annotation.Nullable']
                def defaultNonnull = ['javax.annotation.Nonnull','org.jspecify.annotations.NonNull','org.jetbrains.annotations.NotNull','jakarta.annotation.Nonnull']
                def NULLABLE = ((extension.nullableAnnotations ?: defaultNullable).collect(toDesc)) as Set
                def NONNULL = ((extension.nonnullAnnotations ?: defaultNonnull).collect(toDesc)) as Set
                int countW020 = 0
                int countW021 = 0
                int count022  = 0

                services.each { String fqcn ->
                    try {
                        Class<?> c = Class.forName(fqcn, false, cl)
                        def errs = perService.computeIfAbsent(fqcn) { [] }
                        if (!c.isInterface()) {
                            def msg = "Service '${fqcn}' must be a public top-level interface; found ${c.modifiers.toString()}\n  Fix: Convert to a public top-level interface."
                            errors << msg; errs << msg
                        }
                        if (!Modifier.isPublic(c.getModifiers())) {
                            def msg = "Service '${fqcn}' must be public\n  Fix: Add 'public' modifier."
                            errors << msg; errs << msg
                        }
                        if (c.getDeclaringClass() != null) {
                            def msg = "Service '${fqcn}' must be top-level (not inner)\n  Fix: Move to a top-level interface."
                            errors << msg; errs << msg
                        }
                        // No fields except constants (public static final primitives/Strings)
                        c.getDeclaredFields().each { f ->
                            int m = f.modifiers
                            if (!(Modifier.isPublic(m) && Modifier.isStatic(m) && Modifier.isFinal(m))) {
                                def msg = "Field '${fqcn}.${f.name}' not allowed; only compile-time constants permitted\n  Fix: Remove mutable/stateful fields from interfaces."
                                errors << msg; errs << msg
                            }
                        }
                        // Prepare ASM method annotation info for this class
                        def resName = fqcn.replace('.', '/') + '.class'
                        def is = cl.getResourceAsStream(resName)
                        if (is == null) {
                            def msg = "Declared service '${fqcn}' not found in the exported API output\n  Fix: Keep the interface under src/main/java, ensure it is reachable from the export set, or list it in btraceExtension.services."
                            errors << msg; perService.computeIfAbsent(fqcn) { [] } << msg
                            return
                        }
                        byte[] classBytes
                        try { classBytes = is.bytes } finally { try { is.close() } catch (Throwable ignore) {} }
                        def cr = new ClassReader(classBytes)
                        def cn = new ClassNode()
                        cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG)
                        def methodAnnoInfo = [:] // key=name+desc -> [retNullable, retNonNull, paramAnno: idx->[nullable, nonnull]]
                        def permAnnoSet = new HashSet<String>()
                        // Collect ServiceDescriptor.permissions
                        def collectServicePerms = { AnnotationNode an ->
                            if (an == null) return
                            if (BTraceDescriptors.isServiceDescriptor(an.desc)) {
                                def vals = an.values ?: []
                                for (int i = 0; i < vals.size(); i += 2) {
                                    if (vals[i] == 'permissions') {
                                        def arr = vals[i+1] as List
                                        arr?.each { ev ->
                                            def permission = BTraceDescriptors.enumConstantName(ev)
                                            if (permission != null) permAnnoSet.add(permission)
                                        }
                                    }
                                }
                            }
                        }
                        (cn.visibleAnnotations ?: []).each(collectServicePerms)
                        (cn.invisibleAnnotations ?: []).each(collectServicePerms)
                        cn.methods.each { MethodNode mn ->
                            def key = mn.name + mn.desc
                            def info = [retNullable:false, retNonNull:false, paramAnno:[:]]
                            (mn.visibleAnnotations ?: []).each { AnnotationNode an ->
                                if (NULLABLE.contains(an.desc)) info.retNullable = true
                                if (NONNULL.contains(an.desc)) info.retNonNull = true
                                collectServicePerms(an)
                            }
                            (mn.invisibleAnnotations ?: []).each { AnnotationNode an ->
                                if (NULLABLE.contains(an.desc)) info.retNullable = true
                                if (NONNULL.contains(an.desc)) info.retNonNull = true
                                collectServicePerms(an)
                            }
                            (mn.visibleTypeAnnotations ?: []).each { TypeAnnotationNode tan ->
                                def tr = new TypeReference(tan.typeRef)
                                if (tr.sort == TypeReference.METHOD_RETURN) {
                                    if (NULLABLE.contains(tan.desc)) info.retNullable = true
                                    if (NONNULL.contains(tan.desc)) info.retNonNull = true
                                } else if (tr.sort == TypeReference.METHOD_FORMAL_PARAMETER) {
                                    int pidx = tr.formalParameterIndex
                                    def pa = info.paramAnno.computeIfAbsent(pidx) { [nullable:false, nonnull:false] }
                                    if (NULLABLE.contains(tan.desc)) pa.nullable = true
                                    if (NONNULL.contains(tan.desc)) pa.nonnull = true
                                }
                            }
                            (mn.invisibleTypeAnnotations ?: []).each { TypeAnnotationNode tan ->
                                def tr = new TypeReference(tan.typeRef)
                                if (tr.sort == TypeReference.METHOD_RETURN) {
                                    if (NULLABLE.contains(tan.desc)) info.retNullable = true
                                    if (NONNULL.contains(tan.desc)) info.retNonNull = true
                                } else if (tr.sort == TypeReference.METHOD_FORMAL_PARAMETER) {
                                    int pidx = tr.formalParameterIndex
                                    def pa = info.paramAnno.computeIfAbsent(pidx) { [nullable:false, nonnull:false] }
                                    if (NULLABLE.contains(tan.desc)) pa.nullable = true
                                    if (NONNULL.contains(tan.desc)) pa.nonnull = true
                                }
                            }
                            // Parameter annotations (non type-use)
                            int paramCount = (mn.visibleParameterAnnotations ?: new List[0]).length
                            for (int i = 0; i < paramCount; i++) {
                                def lst = mn.visibleParameterAnnotations[i]
                                if (lst == null) continue
                                def pa = info.paramAnno.computeIfAbsent(i) { [nullable:false, nonnull:false] }
                                lst.each { AnnotationNode an ->
                                    if (NULLABLE.contains(an.desc)) pa.nullable = true
                                    if (NONNULL.contains(an.desc)) pa.nonnull = true
                                }
                            }
                            paramCount = (mn.invisibleParameterAnnotations ?: new List[0]).length
                            for (int i = 0; i < paramCount; i++) {
                                def lst = mn.invisibleParameterAnnotations[i]
                                if (lst == null) continue
                                def pa = info.paramAnno.computeIfAbsent(i) { [nullable:false, nonnull:false] }
                                lst.each { AnnotationNode an ->
                                    if (NULLABLE.contains(an.desc)) pa.nullable = true
                                    if (NONNULL.contains(an.desc)) pa.nonnull = true
                                }
                            }
                            methodAnnoInfo[key] = info
                        }

                        // Permission alignment: if permissions annotated in API, ensure either scanning is on
                        // or declared requiredPermissions include at least those.
                        if (!permAnnoSet.isEmpty()) {
                            if (!extension.scanPermissions) {
                                def declared = (extension.requiredPermissions ?: [])*.toString()*.toUpperCase(java.util.Locale.ROOT) as Set
                                def missing = permAnnoSet.findAll { !declared.contains(String.valueOf(it).toUpperCase(java.util.Locale.ROOT)) }
                                if (!missing.isEmpty()) {
                                    errors << "Service '${fqcn}' requires permissions ${missing} not covered by plugin requiredPermissions; enable scanPermissions or add them."
                                }
                            }
                        }

                        // Methods: no default methods, no checked throws, no forbidden signature types
                        c.getMethods().each { m ->
                            // Only methods declared on this interface (avoid Object methods)
                            if (m.declaringClass != c) return
                            if (m.isDefault()) {
                                def msg = "Default method '${fqcn}#${m.name}' not allowed (Java 8 shim-compat)\n  Fix: Move behavior to implementation; keep API pure."
                                errors << msg; errs << msg
                            }
                            // Checked exceptions
                            m.exceptionTypes.each { ex ->
                                if (!(RuntimeException.isAssignableFrom(ex) || Error.isAssignableFrom(ex))) {
                                    def msg = "Method '${fqcn}#${m.name}' declares checked exception: ${ex.name}\n  Fix: Remove checked exceptions from API signatures."
                                    errors << msg; errs << msg
                                }
                            }
                            // Forbidden signature packages
                            def forbid = [
                                'java.io.', 'java.net.', 'java.nio.channels.', 'java.lang.reflect.'
                            ]
                            def allTypes = []
                            allTypes << m.returnType
                            allTypes.addAll(m.parameterTypes)
                            allTypes.each { t ->
                                String n = t.name
                                if (forbid.any { n.startsWith(it) }) {
                                    def msg = "Method '${fqcn}#${m.name}' uses forbidden type in signature: ${n}\n  Fix: Restrict signatures to JDK types and API interfaces."
                                    errors << msg; errs << msg
                                }
                                if (implClassNames.contains(n)) {
                                    def msg = "API surface leaks implementation type in signature: ${n}\n  Fix: Do not expose implementation-only types in API signatures."
                                    errors << msg; errs << msg
                                }
                            }

                            // Nullability + shimability
                            def md = Type.getMethodDescriptor(Type.getType(m.returnType), m.parameterTypes.collect { Type.getType(it) } as Type[])
                            def key = m.name + md
                            def info = methodAnnoInfo.get(key) ?: [retNullable:false, retNonNull:false, paramAnno:[:]]
                            // Return nullability: only relevant for reference types (not void/primitives)
                            if (m.returnType != Void.TYPE && !m.returnType.isPrimitive()) {
                                if (!(info.retNullable || info.retNonNull)) {
                                    def msgWarn = "Missing nullability on return of '${fqcn}#${m.name}'\n  Fix: Annotate with @NonNull or @Nullable."
                                    warnings << msgWarn; perServiceWarn.computeIfAbsent(fqcn) { [] } << msgWarn
                                    countW020++
                                }
                                // Shimability: if return is interface, require @Nullable unless configured otherwise
                                if (m.returnType.isInterface() && !info.retNullable) {
                                    def msg22 = "Return type '${m.returnType.name}' of '${fqcn}#${m.name}' must be @Nullable for shim compatibility\n  Fix: Mark interface returns @Nullable or provide a documented default."
                                    if ((extension.shimabilitySeverity ?: 'error').equalsIgnoreCase('warn')) {
                                        warnings << msg22; perServiceWarn.computeIfAbsent(fqcn) { [] } << msg22
                                    } else {
                                        errors << msg22; errs << msg22
                                    }
                                    count022++
                                }
                            }
                            // Parameter nullability: recommend but do not enforce by default
                            m.parameterTypes.eachWithIndex { pt, idx ->
                                if (!(pt.isPrimitive())) {
                                    def pinfo = info.paramAnno.get(idx) ?: [nullable:false, nonnull:false]
                                    if (!(pinfo.nullable || pinfo.nonnull)) {
                                        def msg21 = "Missing nullability on parameter ${idx} of '${fqcn}#${m.name}'\n  Fix: Annotate each parameter with @NonNull or @Nullable."
                                        if ((extension.nullabilitySeverity ?: 'warn').equalsIgnoreCase('error')) {
                                            def emsg = "Missing nullability on parameter ${idx} of '${fqcn}#${m.name}'\n  Fix: Annotate each parameter with @NonNull or @Nullable."
                                            errors << emsg; errs << emsg
                                        } else if (!'off'.equalsIgnoreCase(extension.nullabilitySeverity ?: 'warn')) {
                                            warnings << msg21; perServiceWarn.computeIfAbsent(fqcn) { [] } << msg21
                                        }
                                        countW021++
                                    }
                                }
                            }
                        }

                        // Note: we intentionally avoid scanning raw class bytes for impl internal
                        // names to prevent false positives (e.g., substring matches in class names).
                        // Purity is enforced via explicit signature/generic parsing above.
                    } catch (ClassNotFoundException cnf) {
                        def msg = "Declared service '${fqcn}' not found in the exported API output\n  Fix: Keep the interface under src/main/java, ensure it is reachable from the export set, or list it in btraceExtension.services."
                        errors << msg; perService.computeIfAbsent(fqcn) { [] } << msg
                    } catch (Throwable t) {
                        def msg = "Failed to analyze service '${fqcn}': ${t.message}"
                        errors << msg; perService.computeIfAbsent(fqcn) { [] } << msg
                    }
                }
                // Only warn when there is no way to obtain permissions: both scanning disabled
                // and no explicit requiredPermissions provided. If scanning is enabled (default),
                // permissions will be generated later when building the API JAR.
                if (!extension.scanPermissions && (!extension.requiredPermissions || extension.requiredPermissions.isEmpty())) {
                    project.logger.warn("[BTRACE-EXT] No permissions source configured; enable scanPermissions or set requiredPermissions")
                }
                // Write grouped report
                def reportDir = new File(project.buildDir, 'reports/btrace')
                reportDir.mkdirs()
                def report = new File(reportDir, 'service-api-validation.txt')
                report.withWriter('UTF-8') { w ->
                    w.println("BTrace Service API Validation Report")
                    w.println("Project: ${project.path}")
                    w.println()
                    perService.each { k, v ->
                        w.println("Service: ${k}")
                        if (v.isEmpty()) {
                            w.println("  OK")
                        } else {
                            v.each { w.println("  - ${it}") }
                        }
                        w.println()
                    }
                    if (!perServiceWarn.isEmpty()) {
                        w.println("Warnings:")
                        perServiceWarn.each { k, v ->
                            if (v.isEmpty()) return
                            w.println("Service: ${k}")
                            v.each { w.println("  - ${it}") }
                            w.println()
                        }
                    }
                    // Summary
                    w.println("Summary:")
                    w.println("  Missing @Nullable on interface returns (shimability): ${count022}")
                    if (!'off'.equalsIgnoreCase(extension.nullabilitySeverity ?: 'warn')) {
                        w.println("  Missing return nullability annotations: ${countW020}")
                        w.println("  Missing parameter nullability annotations: ${countW021}")
                    }
                    w.println()
                    w.println("Total errors: ${errors.size()}")
                }
                project.logger.lifecycle("[BTRACE-EXT] validation report: ${project.relativePath(report)}")
                if (!errors.isEmpty()) {
                    errors.each { project.logger.error("[BTRACE-EXT] ${it}") }
                    throw new GradleException("BTrace extension API validation failed with ${errors.size()} error(s)")
                }
                if (!warnings.isEmpty()) {
                    warnings.each { project.logger.warn("[BTRACE-EXT] ${it}") }
                }
                project.logger.lifecycle("[BTRACE-EXT] validateServiceApis: OK for ${services.size()} service(s)")
            }
        }

        // Summary report task that never fails; emits grouped violations with fixes
        def serviceApiValidationReport = project.tasks.register('serviceApiValidationReport') {
            dependsOn(authoredCompileTask)
            doLast {
                // Reuse the validation task to produce the report; do not fail build
                try {
                    project.tasks.named('validateServiceApis').get().actions.each { it.execute(project.tasks.named('validateServiceApis').get()) }
                } catch (Throwable t) {
                    // ignore failures; report was already written by validate task
                }
                def report = new File(project.buildDir, 'reports/btrace/service-api-validation.txt')
                if (report.exists()) {
                    project.logger.lifecycle("[BTRACE-EXT] validation report available at: ${project.relativePath(report)}")
                    project.logger.quiet(report.getText('UTF-8'))
                } else {
                    project.logger.lifecycle('[BTRACE-EXT] No report generated (no services or validation skipped)')
                }
            }
        }

        // Task: Build Implementation JAR (requires Shadow plugin applied by the consumer project)
        def implJarProviderRef = new Object[1]
        project.pluginManager.withPlugin('com.gradleup.shadow') {
            def shadowJarProvider = project.tasks.named('shadowJar', Jar)
            project.afterEvaluate {
                shadowJarProvider.configure {
                    dependsOn(authoredCompileTask)
                    from({
                        return project.files(authoredClassesDirs()).asFileTree.matching {
                            exclude apiIncludes()
                        }
                    })
                    configurations = [project.configurations.implRuntimeClasspath]
                    archiveClassifier = 'impl'
                    archiveBaseName = project.name

                    // Apply relocations from extension metadata
                    extension.shadedPackages.each { from, to ->
                        relocate from, to
                    }

                    // Minimize to remove unused classes
                    minimize()
                }
            }
            implJarProviderRef[0] = shadowJarProvider
        }

        // Wrapper task to build implementation jar via selected strategy
        def buildImplJar = project.tasks.register('buildImplJar') {
            doFirst {
                if (implJarProviderRef[0] == null) {
                    throw new IllegalStateException("Shadow plugin ('com.gradleup.shadow') must be applied in the project using the BTrace extension plugin.")
                }
            }
            dependsOn { implJarProviderRef[0] }
        }

        // Configure API JAR manifest with extension metadata
        project.afterEvaluate {
            if (implJarProviderRef[0] == null) {
                def ext = project.extensions.findByType(BTraceExtensionMetadata)
                boolean autoApplied = (ext == null) ? true : (ext.autoApplyShadow != false)
                String hint = autoApplied ? "Ensure the Shadow plugin is resolvable/available." : "Enable auto-apply (btraceExtension.autoApplyShadow=true) or apply Shadow explicitly."
                throw new GradleException("[BTRACE-EXT] Shadow plugin is required for ${project.path}. Apply id 'com.gradleup.shadow' (or alias(libs.plugins.shadow)). ${hint}")
            }
            def implArchiveProvider = ((org.gradle.api.tasks.TaskProvider) implJarProviderRef[0]).flatMap { it.archiveFile }
            buildApiJar.configure {
                // Ensure we consider impl jar and classpath as inputs and we run when they change
                dependsOn { implJarProviderRef[0] }
                inputs.files(implArchiveProvider)
                inputs.files(project.configurations.implRuntimeClasspath)
                inputs.property('btraceExtension.services', { explicitServiceTypes().join(',') })
                inputs.property('btraceExtension.additionalExports', { extension.additionalExports.join(',') })
                inputs.property('btraceExtension.excludedExports', { extension.excludedExports.join(',') })
                doFirst {
                    // Compute permissions: scan impl jar and transitive classpath, then merge with overrides
                    def implJar = implArchiveProvider.get().asFile
                    Set<String> scannedPerms = [] as Set
                    if (extension.scanPermissions) {
                        Set<File> cp = [] as Set
                        try {
                            cp = project.configurations.implRuntimeClasspath.resolve() as Set<File>
                        } catch (Throwable ignore) { }
                        scannedPerms = PermissionScanner.scan(implJar, cp)
                    }
                    Set<String> mergedPerms = [] as Set
                    mergedPerms.addAll(scannedPerms)
                    mergedPerms.addAll(extension.requiredPermissions)

                    // Discover descriptor permissions and ensure the manifest covers them.
                    try {
                        def cp = new LinkedHashSet<File>()
                        cp.addAll(authoredClassesDirs())
                        try { cp.addAll(authoredCompileClasspath()) } catch (Throwable ignore) {}
                        URLClassLoader cl = new URLClassLoader(cp.collect { it.toURI().toURL() } as URL[], (ClassLoader) null)
                        def enumConstName = { Object enumConst ->
                            return BTraceDescriptors.enumConstantName(enumConst)
                        }
                        Set<String> annotatedPerms = [] as Set
                        explicitServiceTypes().each { String fqcn ->
                            try {
                                def res = fqcn.replace('.', '/') + '.class'
                                def is = cl.getResourceAsStream(res)
                                if (is == null) return
                                def cr = new ClassReader(is)
                                def cn = new ClassNode()
                                cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG)
                                (cn.visibleAnnotations ?: []).each { an ->
                                    if (BTraceDescriptors.isServiceDescriptor(an.desc)) {
                                        def vals = an.values ?: []
                                        for (int i = 0; i < vals.size(); i += 2) {
                                            if (vals[i] == 'permissions') {
                                                def arr = vals[i+1] as List
                                                arr?.each { ev ->
                                                    def n = enumConstName(ev)
                                                    if (n != null) annotatedPerms.add(n)
                                                }
                                            }
                                        }
                                    }
                                }
                                (cn.invisibleAnnotations ?: []).each { an ->
                                    if (BTraceDescriptors.isServiceDescriptor(an.desc)) {
                                        def vals = an.values ?: []
                                        for (int i = 0; i < vals.size(); i += 2) {
                                            if (vals[i] == 'permissions') {
                                                def arr = vals[i+1] as List
                                                arr?.each { ev ->
                                                    def n = enumConstName(ev)
                                                    if (n != null) annotatedPerms.add(n)
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignore) { }
                        }
                        // Also include package-level ExtensionDescriptor.permissions()
                        authoredClassesDirs().each { File dir ->
                            if (!dir.exists()) return
                            dir.eachFileRecurse { f ->
                                if (!f.name.equals('package-info.class')) return
                                FileInputStream is2 = new FileInputStream(f)
                                byte[] bytes2
                                try { bytes2 = is2.bytes } finally { try { is2.close() } catch (Throwable ignore) {} }
                                def cr2 = new ClassReader(bytes2)
                                def cn2 = new ClassNode(); cr2.accept(cn2, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG)
                                (cn2.visibleAnnotations ?: []).each { an ->
                                    if (BTraceDescriptors.isExtensionDescriptor(an.desc)) {
                                        def vals = an.values ?: []
                                        for (int i = 0; i < vals.size(); i += 2) {
                                            if (vals[i] == 'permissions') {
                                                def arr = vals[i+1] as List
                                                arr?.each { ev ->
                                                    def n = enumConstName(ev)
                                                    if (n != null) annotatedPerms.add(n)
                                                }
                                            }
                                        }
                                    }
                                }
                                (cn2.invisibleAnnotations ?: []).each { an ->
                                    if (BTraceDescriptors.isExtensionDescriptor(an.desc)) {
                                        def vals = an.values ?: []
                                        for (int i = 0; i < vals.size(); i += 2) {
                                            if (vals[i] == 'permissions') {
                                                def arr = vals[i+1] as List
                                                arr?.each { ev ->
                                                    def n = enumConstName(ev)
                                                    if (n != null) annotatedPerms.add(n)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (extension.scanPermissions) {
                            mergedPerms.addAll(annotatedPerms)
                        }
                        Set<String> manifestPerms = mergedPerms.collect { it.toString().toUpperCase(Locale.ROOT) } as Set
                        Set<String> missing = annotatedPerms.findAll { !manifestPerms.contains(it) } as Set
                        if (!missing.isEmpty()) {
                            throw new GradleException("[BTRACE-EXT] Manifest permissions missing annotated requirements: ${missing}. Fix: enable scanPermissions or add to requiredPermissions.")
                        }
                    } catch (GradleException ge) {
                        throw ge
                    } catch (Throwable t) {
                        project.logger.warn("[BTRACE-EXT] permission alignment check failed to run: ${t.message}")
                    }

                    // Emit concise info about permissions for traceability after descriptor merging.
                    if (extension.scanPermissions) {
                        project.logger.lifecycle("[BTRACE-EXT] permissions: scanned=${scannedPerms} merged=${mergedPerms}")
                    } else {
                        project.logger.lifecycle("[BTRACE-EXT] permissions: merged=${mergedPerms} (scan disabled)")
                    }

                    def shadedPkgs = extension.shadedPackages.collect { k, v -> "$k->$v" }.join(',')
                    def servicesStr = extension.services.join(',')
                    def requiresStr = extension.requiresExtensions.join(',')
                    def permsStr = mergedPerms.join(',')

                    manifest {
                        attributes(
                            'BTrace-Extension-Id': extension.id,
                            'BTrace-Extension-Version': extension.version,
                            'BTrace-Extension-Name': extension.name,
                            'BTrace-Extension-Description': extension.description,
                            'BTrace-API-Version': '2.3+',
                            'BTrace-Java-Version': '8+',
                            'BTrace-Extension-Services': {
                                if (servicesStr && !servicesStr.isEmpty()) return servicesStr
                                // If not explicitly configured, detect services via @ServiceDescriptor in API output
                                return explicitServiceTypes().join(',')
                            }.call(),
                            'BTrace-Extension-Requires': requiresStr,
                            'BTrace-Shaded-Packages': shadedPkgs,
                            'BTrace-Extension-Impl': "${project.name}-${extension.version}-impl.jar",
                            'BTrace-Extension-Permissions': permsStr,
                            // Read exports index (if present) and add a short summary attribute
                            'BTrace-Extension-Exports': {
                                try {
                                    def idxFile = new File(project.buildDir, 'generated/resources/btraceExports/META-INF/btrace/exports.index')
                                    List<String> lines = []
                                    if (idxFile.exists()) {
                                        lines = idxFile.readLines('UTF-8')
                                    } else {
                                        lines = extension.services as List<String>
                                    }
                                    int limit = 20
                                    def head = lines.take(limit)
                                    def extra = Math.max(0, lines.size() - limit)
                                    return head.join(',') + (extra > 0 ? ", +${extra} more" : '')
                                } catch (Throwable ignore) {
                                    return servicesStr
                                }
                            }.call()
                        )
                    }
                }
            }
        }

        // Generate shims Java sources for API interfaces
        def shimsSrcDir = new File(project.buildDir, 'generated/sources/btraceShims/api')
        def shimsClsDir = new File(project.buildDir, 'generated/classes/btraceShims/api')
        def shimsResDir = new File(project.buildDir, 'generated/resources/btraceShims')

        def generateServiceShims = project.tasks.register('generateServiceShims') {
            dependsOn(authoredCompileTask)
            outputs.dir(shimsSrcDir)
            doLast {
                project.delete(shimsSrcDir)
                shimsSrcDir.mkdirs()
                // Build ClassLoader over API outputs to reflect methods
                def cp = new LinkedHashSet<File>()
                cp.addAll(authoredClassesDirs())
                try { cp.addAll(authoredCompileClasspath()) } catch (Throwable ignore) {}
                URLClassLoader cl = new URLClassLoader(cp.collect { it.toURI().toURL() } as URL[], (ClassLoader) null)
                // Discover ALL API interfaces in the api output
                def apiInterfaces = new LinkedHashSet<String>()
                authoredClassesDirs().each { File dir ->
                    if (!dir.exists()) return
                    dir.eachFileRecurse { f ->
                        if (f.name.endsWith('.class')) {
                            def rel = dir.toPath().relativize(f.toPath()).toString()
                            def fq = rel.replace(File.separatorChar, (char)'.').replaceAll(/\.class$/, '')
                            if (!isExportedApiType(fq)) return
                            // Use ASM to filter interfaces (skip annotations)
                            def is = new FileInputStream(f)
                            byte[] bytes
                            try { bytes = is.bytes } finally { try { is.close() } catch (Throwable ignore) {} }
                            def cr = new ClassReader(bytes)
                            int acc = cr.access
                            boolean isInterface = (acc & Opcodes.ACC_INTERFACE) != 0
                            boolean isAnnotation = (acc & Opcodes.ACC_ANNOTATION) != 0
                            if (isInterface && !isAnnotation) {
                                apiInterfaces.add(fq)
                            }
                        }
                    }
                }
                if (apiInterfaces.isEmpty()) {
                    project.logger.warn('[BTRACE-EXT] No API interfaces found; skipping shim generation')
                    return
                }
                // Optionally restrict to interfaces reachable from services via descriptors + generics
                def targetInterfaces = new LinkedHashSet<String>()
                if (extension.generateShimsReachableOnly) {
                    def services = explicitServiceTypes() ?: []
                    // BFS closure over referenced API interface types
                    def visited = new HashSet<String>()
                    def queue = new ArrayDeque<String>(services)
                    while (!queue.isEmpty()) {
                        def cur = queue.removeFirst()
                        if (cur == null || visited.contains(cur)) continue
                        visited.add(cur)
                        if (apiInterfaces.contains(cur)) targetInterfaces.add(cur)
                        try {
                            def res = cur.replace('.', '/') + '.class'
                            def is = cl.getResourceAsStream(res)
                            if (is == null) continue
                            byte[] bytes; try { bytes = is.bytes } finally { try { is.close() } catch (Throwable ignore) {} }
                            def cr = new ClassReader(bytes)
                            def cn = new ClassNode(); cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG)
                            def addType = { String fq -> if (fq!=null && apiInterfaces.contains(fq) && !visited.contains(fq)) queue.addLast(fq) }
                            // class generics
                            if (cn.signature != null) {
                                try {
                                    new SignatureReader(cn.signature).accept(new SignatureVisitor(Opcodes.ASM9){
                                        void visitClassType(String name){ addType(name?.replace('/', '.')) }
                                        void visitInnerClassType(String name){ addType(name?.replace('/', '.')) }
                                        SignatureVisitor visitSuperclass(){ return this }
                                        SignatureVisitor visitInterface(){ return this }
                                        SignatureVisitor visitTypeArgument(char w){ return this }
                                        SignatureVisitor visitClassBound(){ return this }
                                        SignatureVisitor visitInterfaceBound(){ return this }
                                    })
                                } catch(Throwable ignore){}
                            }
                            // method descriptors + generics
                            cn.methods?.each { MethodNode mn ->
                                def mt = Type.getMethodType(mn.desc)
                                [mt.returnType].each { t -> if (t.sort==Type.OBJECT) addType(t.className) }
                                mt.argumentTypes?.each { t -> if (t.sort==Type.OBJECT) addType(t.className) }
                                if (mn.signature != null) {
                                    try {
                                        new SignatureReader(mn.signature).accept(new SignatureVisitor(Opcodes.ASM9){
                                            void visitClassType(String name){ addType(name?.replace('/', '.')) }
                                            void visitInnerClassType(String name){ addType(name?.replace('/', '.')) }
                                            SignatureVisitor visitParameterType(){ return this }
                                            SignatureVisitor visitReturnType(){ return this }
                                            SignatureVisitor visitExceptionType(){ return this }
                                            SignatureVisitor visitArrayType(){ return this }
                                            SignatureVisitor visitTypeArgument(char w){ return this }
                                        })
                                    } catch(Throwable ignore){}
                                }
                            }
                        } catch (Throwable ignore) {}
                    }
                } else {
                    targetInterfaces.addAll(apiInterfaces)
                }

                // Generate shims for selected API interfaces
                targetInterfaces.each { String fqcn ->
                    try {
                        Class<?> c = Class.forName(fqcn, false, cl)
                        if (!c.isInterface()) return
                        def pkg = c.package?.name ?: ''
                        def simple = c.simpleName
                        def shimPkg = (pkg ? pkg + '.btrace.shim' : 'btrace.shim')
                        def dir = new File(shimsSrcDir, shimPkg.replace('.', '/'))
                        dir.mkdirs()
                        def noopName = "NoOp${simple}"
                        def throwName = "Throwing${simple}"
                        def methods = c.getMethods().findAll { it.declaringClass == c }
                        // Emit NoOp
                        new File(dir, noopName + '.java').withWriter('UTF-8') { w ->
                            w.println("package ${shimPkg};")
                            w.println()
                            w.println("public final class ${noopName} implements ${fqcn} {")
                            w.println("  public static final ${fqcn} INSTANCE = new ${noopName}();")
                            methods.each { m ->
                                def ret = m.returnType
                                def params = []
                                m.parameterTypes.eachWithIndex { pt, idx -> params << (pt.name + " p" + idx) }
                                w.println("  public ${ret.name} ${m.name}(${params.join(', ')}) {")
                                if (ret == Void.TYPE) {
                                    w.println("    return;")
                                } else if (ret.isPrimitive()) {
                                    if (ret == Boolean.TYPE) w.println("    return false;")
                                    else if (ret == Character.TYPE) w.println("    return (char)0;")
                                    else if (ret == Byte.TYPE) w.println("    return (byte)0;")
                                    else if (ret == Short.TYPE) w.println("    return (short)0;")
                                    else if (ret == Integer.TYPE) w.println("    return 0;")
                                    else if (ret == Long.TYPE) w.println("    return 0L;")
                                    else if (ret == Float.TYPE) w.println("    return 0f;")
                                    else if (ret == Double.TYPE) w.println("    return 0d;")
                                } else if (ret.isArray() && ret.componentType.isInterface() && apiInterfaces.contains(ret.componentType.name)) {
                                    // Return empty array of API interface type
                                    w.println("    return new ${ret.componentType.name}[0];")
                                } else if (ret.isInterface() && apiInterfaces.contains(ret.name)) {
                                    // Return nested shim INSTANCE (or this if fluent self-return)
                                    if (ret.name == fqcn) {
                                        w.println("    return this;")
                                    } else {
                                        def retSimple = ret.simpleName
                                        def retShimFqcn = (ret.package?.name ? ret.package.name + '.btrace.shim.NoOp' + retSimple : 'btrace.shim.NoOp' + retSimple)
                                        w.println("    return ${retShimFqcn}.INSTANCE;")
                                    }
                                } else if (m.genericReturnType instanceof java.lang.reflect.ParameterizedType) {
                                    def pt = (java.lang.reflect.ParameterizedType) m.genericReturnType
                                    def raw = (pt.rawType instanceof Class) ? (Class) pt.rawType : null
                                    if (raw != null) {
                                        def rawName = raw.name
                                        if (rawName == 'java.util.List' || rawName == 'java.util.Collection' || rawName == 'java.lang.Iterable') {
                                            w.println("    return java.util.Collections.emptyList();")
                                        } else if (rawName == 'java.util.Set') {
                                            w.println("    return java.util.Collections.emptySet();")
                                        } else if (rawName == 'java.util.Queue' || rawName == 'java.util.Deque') {
                                            w.println("    return new java.util.ArrayDeque<>();")
                                        } else if (rawName == 'java.util.Map') {
                                            w.println("    return java.util.Collections.emptyMap();")
                                        } else if (rawName == 'java.util.Optional') {
                                            w.println("    return java.util.Optional.empty();")
                                        } else if (rawName == 'java.util.stream.Stream') {
                                            w.println("    return java.util.stream.Stream.empty();")
                                        } else if (rawName == 'java.util.stream.IntStream') {
                                            w.println("    return java.util.stream.IntStream.empty();")
                                        } else if (rawName == 'java.util.stream.LongStream') {
                                            w.println("    return java.util.stream.LongStream.empty();")
                                        } else if (rawName == 'java.util.stream.DoubleStream') {
                                            w.println("    return java.util.stream.DoubleStream.empty();")
                                        } else {
                                            w.println("    return null;")
                                        }
                                    }
                                } else {
                                    w.println("    return null;")
                                }
                                w.println("  }")
                            }
                            // equals/hashCode/toString
                            w.println("  public boolean equals(Object o) { return this == o; }")
                            w.println("  public int hashCode() { return System.identityHashCode(this); }")
                            w.println("  public String toString() { return \"NoOp${simple}\"; }")
                            w.println("}")
                        }
                        // Emit Throwing
                        new File(dir, throwName + '.java').withWriter('UTF-8') { w ->
                            w.println("package ${shimPkg};")
                            w.println()
                            w.println("public final class ${throwName} implements ${fqcn} {")
                            w.println("  public static final ${fqcn} INSTANCE = new ${throwName}();")
                            w.println("  private static RuntimeException unavailable() { return new IllegalStateException(\"BTrace optional service unavailable: ${fqcn}\"); }")
                            methods.each { m ->
                                def ret = m.returnType
                                def params = []
                                m.parameterTypes.eachWithIndex { pt, idx -> params << (pt.name + " p" + idx) }
                                w.println("  public ${ret.name} ${m.name}(${params.join(', ')}) {")
                                w.println("    throw unavailable();")
                                w.println("  }")
                            }
                            // equals/hashCode/toString
                            w.println("  public boolean equals(Object o) { return this == o; }")
                            w.println("  public int hashCode() { return System.identityHashCode(this); }")
                            w.println("  public String toString() { return \"Throwing${simple}\"; }")
                            w.println("}")
                        }
                    } catch (Throwable t) {
                        project.logger.warn("[BTRACE-EXT] Skipping shim generation for ${fqcn}: ${t.message}")
                    }
                }
            }
        }

        def compileServiceShims = project.tasks.register('compileServiceShims', JavaCompile) { t ->
            dependsOn generateServiceShims
            source = project.fileTree(shimsSrcDir)
            destinationDirectory.set(shimsClsDir)
            classpath = project.files(authoredClassesDirs(), authoredCompileClasspath())
            options.release = 8
            doFirst {
                project.delete(shimsClsDir)
            }
        }

        def generateShimIndex = project.tasks.register('generateShimIndex') {
            dependsOn generateServiceShims
            doLast {
                // Recompute the same target set as generateServiceShims (respect generateShimsReachableOnly)
                def apiInterfaces = new LinkedHashSet<String>()
                authoredClassesDirs().each { File dir ->
                    if (!dir.exists()) return
                    dir.eachFileRecurse { f ->
                        if (f.name.endsWith('.class')) {
                            def rel = dir.toPath().relativize(f.toPath()).toString()
                            def fq = rel.replace(File.separatorChar, (char)'.').replaceAll(/\.class$/, '')
                            if (!isExportedApiType(fq)) return
                            def is = new FileInputStream(f)
                            byte[] bytes
                            try { bytes = is.bytes } finally { try { is.close() } catch (Throwable ignore) {} }
                            def cr = new ClassReader(bytes)
                            int acc = cr.access
                            boolean isInterface = (acc & Opcodes.ACC_INTERFACE) != 0
                            boolean isAnnotation = (acc & Opcodes.ACC_ANNOTATION) != 0
                            if (isInterface && !isAnnotation) apiInterfaces.add(fq)
                        }
                    }
                }
                if (apiInterfaces.isEmpty()) return
                def cp = new LinkedHashSet<File>()
                cp.addAll(authoredClassesDirs())
                try { cp.addAll(authoredCompileClasspath()) } catch (Throwable ignore) {}
                URLClassLoader cl = new URLClassLoader(cp.collect { it.toURI().toURL() } as URL[], (ClassLoader) null)
                def targetInterfaces = new LinkedHashSet<String>()
                if (extension.generateShimsReachableOnly) {
                    def services = explicitServiceTypes() ?: []
                    if (services.isEmpty()) {
                        // fall back to detected annotated services
                        authoredClassesDirs().each { File dir ->
                            if (!dir.exists()) return
                            dir.eachFileRecurse { f ->
                                if (!f.name.endsWith('.class')) return
                                def rel = dir.toPath().relativize(f.toPath()).toString()
                                def fq = rel.replace(File.separatorChar, (char)'.').replaceAll(/\.class$/, '')
                                FileInputStream is = new FileInputStream(f)
                                byte[] bytes
                                try { bytes = is.bytes } finally { try { is.close() } catch (Throwable ignore) {} }
                                def cr = new ClassReader(bytes)
                                def cn = new ClassNode(); cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG)
                                boolean isInterface = (cn.access & Opcodes.ACC_INTERFACE) != 0
                                boolean hasMarker = false
                                (cn.visibleAnnotations ?: []).each { hasMarker |= BTraceDescriptors.isServiceDescriptor(it.desc) }
                                (cn.invisibleAnnotations ?: []).each { hasMarker |= BTraceDescriptors.isServiceDescriptor(it.desc) }
                                if (hasMarker && isInterface) services.add(fq)
                            }
                        }
                    }
                    def visited = new HashSet<String>()
                    def queue = new ArrayDeque<String>(services)
                    while (!queue.isEmpty()) {
                        def cur = queue.removeFirst()
                        if (cur == null || visited.contains(cur)) continue
                        visited.add(cur)
                        if (apiInterfaces.contains(cur)) targetInterfaces.add(cur)
                        try {
                            def res = cur.replace('.', '/') + '.class'
                            def is = cl.getResourceAsStream(res)
                            if (is == null) continue
                            byte[] bytes; try { bytes = is.bytes } finally { try { is.close() } catch (Throwable ignore) {} }
                            def cr = new ClassReader(bytes)
                            def cn = new ClassNode(); cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG)
                            def addType = { String fq -> if (fq!=null && apiInterfaces.contains(fq) && !visited.contains(fq)) queue.addLast(fq) }
                            if (cn.signature != null) {
                                try { new SignatureReader(cn.signature).accept(new SignatureVisitor(Opcodes.ASM9){
                                    void visitClassType(String name){ addType(name?.replace('/', '.')) }
                                    void visitInnerClassType(String name){ addType(name?.replace('/', '.')) }
                                    SignatureVisitor visitSuperclass(){ return this }
                                    SignatureVisitor visitInterface(){ return this }
                                    SignatureVisitor visitTypeArgument(char w){ return this }
                                    SignatureVisitor visitClassBound(){ return this }
                                    SignatureVisitor visitInterfaceBound(){ return this }
                                }) } catch(Throwable ignore){}
                            }
                            cn.methods?.each { MethodNode mn ->
                                def mt = Type.getMethodType(mn.desc)
                                [mt.returnType].each { t -> if (t.sort==Type.OBJECT) addType(t.className) }
                                mt.argumentTypes?.each { t -> if (t.sort==Type.OBJECT) addType(t.className) }
                                if (mn.signature != null) {
                                    try { new SignatureReader(mn.signature).accept(new SignatureVisitor(Opcodes.ASM9){
                                        void visitClassType(String name){ addType(name?.replace('/', '.')) }
                                        void visitInnerClassType(String name){ addType(name?.replace('/', '.')) }
                                        SignatureVisitor visitParameterType(){ return this }
                                        SignatureVisitor visitReturnType(){ return this }
                                        SignatureVisitor visitExceptionType(){ return this }
                                        SignatureVisitor visitArrayType(){ return this }
                                        SignatureVisitor visitTypeArgument(char w){ return this }
                                    }) } catch(Throwable ignore){}
                                }
                            }
                        } catch (Throwable ignore) {}
                    }
                } else {
                    targetInterfaces.addAll(apiInterfaces)
                }
                def idxFile = new File(shimsResDir, 'META-INF/btrace/shims.index')
                idxFile.parentFile.mkdirs()
                idxFile.withWriter('UTF-8') { w ->
                    targetInterfaces.each { String fqcn ->
                        def pkg = fqcn.contains('.') ? fqcn.substring(0, fqcn.lastIndexOf('.')) : ''
                        def simple = fqcn.substring(fqcn.lastIndexOf('.')+1)
                        def shimPkg = (pkg ? pkg + '.btrace.shim' : 'btrace.shim')
                        def noop = shimPkg + '.NoOp' + simple
                        def thrw = shimPkg + '.Throwing' + simple
                        w.println("${fqcn} = ${noop}, ${thrw}")
                    }
                }
            }
        }

        // Exports index: collect all API types referenced in service method signatures (return and params)
        def exportsResDir = new File(project.buildDir, 'generated/resources/btraceExports')
        def generateExportsIndex = project.tasks.register('generateExportsIndex') {
            dependsOn(authoredCompileTask)
            doLast {
                def services = explicitServiceTypes()
                if (!services || services.isEmpty()) return
                def cp = new LinkedHashSet<File>()
                cp.addAll(authoredClassesDirs())
                try { cp.addAll(authoredCompileClasspath()) } catch (Throwable ignore) {}
                URLClassLoader cl = new URLClassLoader(cp.collect { it.toURI().toURL() } as URL[], (ClassLoader) null)

                // Collect known API/impl class names to validate placement
                def apiClassNames = new HashSet<String>(apiTypes())
                def implClassNames = new HashSet<String>(implTypes())

                def exports = new LinkedHashSet<String>()
                services.each { String fqcn ->
                    try {
                        exports.add(fqcn)
                        def res = fqcn.replace('.', '/') + '.class'
                        def is = cl.getResourceAsStream(res)
                        if (is == null) return
                        byte[] bytes
                        try { bytes = is.bytes } finally { try { is.close() } catch (Throwable ignore) {} }
                        def cr = new ClassReader(bytes)
                        def cn = new ClassNode()
                        cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG)
                        // Class-level generic signature: capture bounds/supertypes/interface types
                        if (cn.signature != null) {
                            try {
                                def collector = new SignatureVisitor(Opcodes.ASM9) {
                                    @Override
                                    void visitClassType(String name) { addType(name?.replace('/', '.')) }
                                    @Override
                                    void visitInnerClassType(String name) { addType(name?.replace('/', '.')) }
                                    @Override
                                    SignatureVisitor visitSuperclass() { return this }
                                    @Override
                                    SignatureVisitor visitInterface() { return this }
                                    @Override
                                    SignatureVisitor visitTypeArgument(char wildcard) { return this }
                                    @Override
                                    SignatureVisitor visitClassBound() { return this }
                                    @Override
                                    SignatureVisitor visitInterfaceBound() { return this }
                                }
                                new SignatureReader(cn.signature).accept(collector)
                            } catch (Throwable ignored) { }
                        }
                        // Helper to add a referenced type found in signatures
                        def addType = { String fq ->
                            if (fq == null) return
                            if (fq.startsWith('java.') || fq.startsWith('javax.') || fq.startsWith('jakarta.')) return
                            if (apiClassNames.contains(fq)) {
                                exports.add(fq)
                            } else if (implClassNames.contains(fq)) {
                                throw new GradleException("[BTRACE-EXT] API method references implementation type '${fq}'. Fix: move type to API module so probes can use it.")
                            }
                        }

                        cn.methods?.each { MethodNode mn ->
                            def mt = Type.getMethodType(mn.desc)
                            def all = [] as List<Type>
                            all << mt.returnType
                            all.addAll(mt.argumentTypes.toList())
                            all.each { Type t ->
                                def fq = null
                                if (t.getSort() == Type.OBJECT) {
                                    fq = t.getClassName()
                                } else if (t.getSort() == Type.ARRAY && t.getElementType().getSort() == Type.OBJECT) {
                                    fq = t.getElementType().getClassName()
                                }
                                addType(fq)
                            }
                            // Parse generic signature to capture type arguments
                            if (mn.signature != null) {
                                try {
                                    def collector = new SignatureVisitor(Opcodes.ASM9) {
                                        @Override
                                        SignatureVisitor visitParameterType() { return this }
                                        @Override
                                        SignatureVisitor visitReturnType() { return this }
                                        @Override
                                        SignatureVisitor visitExceptionType() { return this }
                                        @Override
                                        SignatureVisitor visitArrayType() { return this }
                                        @Override
                                        void visitClassType(String name) { addType(name?.replace('/', '.')) }
                                        @Override
                                        void visitInnerClassType(String name) { addType(name?.replace('/', '.')) }
                                        @Override
                                        void visitTypeVariable(String name) { }
                                        @Override
                                        void visitBaseType(char descriptor) { }
                                        @Override
                                        SignatureVisitor visitTypeArgument(char wildcard) { return this }
                                    }
                                    new SignatureReader(mn.signature).accept(collector)
                                } catch (Throwable ignored) { }
                            }
                        }
                    } catch (GradleException ge) {
                        throw ge
                    } catch (Throwable t) {
                        project.logger.warn("[BTRACE-EXT] export scan skipped for ${fqcn}: ${t.message}")
                    }
                }

                if (!exports.isEmpty()) {
                    def idxFile = new File(exportsResDir, 'META-INF/btrace/exports.index')
                    idxFile.parentFile.mkdirs()
                    idxFile.withWriter('UTF-8') { w ->
                        exports.each { w.println(it) }
                    }
                    project.logger.lifecycle("[BTRACE-EXT] exports: ${exports.size()} types")
                }
            }
        }
        def packageExtension = project.tasks.register('packageExtension', Zip) {
            dependsOn buildApiJar, buildImplJar, compileServiceShims, generateShimIndex, generateExportsIndex, validateServiceApis
            archiveBaseName = project.name
            archiveClassifier = 'extension'

            from(buildApiJar.map { it.archiveFile })
            from(((org.gradle.api.tasks.TaskProvider) implJarProviderRef[0]).flatMap { it.archiveFile })
        }

        // Disable default jar task (use packageExtension instead)
        project.tasks.named('jar') {
            enabled = false
        }

        // Configure artifacts for consumption
        project.configurations {
            apiElements.outgoing.artifacts.clear()
            apiElements.outgoing.artifact(buildApiJar)

            runtimeElements.outgoing.artifacts.clear()
            runtimeElements.outgoing.artifact(buildApiJar)
            runtimeElements.outgoing.artifact(packageExtension)
        }

        // Make extension ZIP the default archive artifact
        project.artifacts { archives packageExtension }

        // Include shims classes and index in API JAR
        buildApiJar.configure {
            dependsOn compileServiceShims, generateShimIndex, generateExportsIndex
            from(shimsClsDir)
            from(shimsResDir)
            from(exportsResDir)
        }

        // Aux tasks: API sources and javadoc jars
        def apiSourcesJar = project.tasks.register('apiSourcesJar', Jar) {
            dependsOn(authoredCompileTask)
            archiveBaseName = project.name
            archiveClassifier = 'api-sources'
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            from({ apiSourceTree() })
            from(project.sourceSets.main.resources)
        }
        def apiJavadoc = project.tasks.register('apiJavadoc', Javadoc) {
            dependsOn(authoredCompileTask)
            source = project.files({ apiSourceTree() })
            classpath = project.files(authoredClassesDirs(), authoredCompileClasspath())
            destinationDir = new File(project.buildDir, 'docs/api')
            options.encoding = 'UTF-8'
        }
        def apiJavadocJar = project.tasks.register('apiJavadocJar', Jar) {
            dependsOn apiJavadoc
            archiveBaseName = project.name
            archiveClassifier = 'api-javadoc'
            from apiJavadoc.map { it.destinationDir }
        }

        // Configure publishing if maven-publish is present
        if (project.plugins.hasPlugin('maven-publish')) {
            project.publishing {
                publications {
                    mavenExtension(org.gradle.api.publish.maven.MavenPublication) {
                        groupId = project.group as String
                        artifactId = project.name
                        version = project.version as String

                        artifact(buildApiJar) { classifier = 'api' }
                        artifact(apiSourcesJar)
                        artifact(apiJavadocJar)
                        artifact(packageExtension) { classifier = 'extension' }

                        pom {
                            name = project.provider { extension.name }
                            description = project.provider { extension.description }
                        }
                    }
                }
            }
        }

        // Tests can see both api and impl outputs
        project.afterEvaluate {
            project.dependencies {
                testImplementation project.sourceSets.main.output
            }
            // Fail build on validation errors via check lifecycle
            project.tasks.matching { it.name == 'check' }.configureEach { it.dependsOn validateServiceApis }
        }
    }

}

class BTraceExtensionMetadata {
    String id
    String version
    String name
    String description
    List<String> services = []
    List<String> additionalExports = []
    List<String> excludedExports = []
    List<String> requiresExtensions = []
    Map<String, String> shadedPackages = [:]
    // Whether to auto-apply the Shadow plugin. Default: true
    boolean autoApplyShadow = true
    boolean scanPermissions = true
    List<String> requiredPermissions = []
    // Configurable nullability annotations (FQCN). Defaults cover javax/jspecify/jetbrains/jakarta
    List<String> nullableAnnotations = []
    List<String> nonnullAnnotations = []
    // Validation severity knobs
    // nullabilitySeverity: 'off' | 'warn' | 'error' (default: 'warn')
    String nullabilitySeverity = 'warn'
    // shimabilitySeverity: 'warn' | 'error' (default: 'error')
    String shimabilitySeverity = 'error'
    // apiCtorSeverity: 'off' | 'warn' | 'error' (default: 'error')
    String apiCtorSeverity = 'error'
    // Generate shims only for interfaces reachable from declared services (via signatures + generics)
    // Set to false to generate shims for all API interfaces.
    boolean generateShimsReachableOnly = true
}
