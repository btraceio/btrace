package io.btrace.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.file.DuplicatesStrategy
import javax.lang.model.SourceVersion
import java.util.jar.JarFile
import java.util.jar.Manifest

/**
 * Gradle plugin for building fat agent JARs with embedded extensions.
 *
 * <p>This plugin provides a DSL to configure which extensions to embed
 * and produces a self-contained agent JAR for single-JAR deployment
 * scenarios (e.g., Spark, Hadoop, Kubernetes).
 *
 * <p>Usage:
 * <pre>
 * plugins {
 *     id 'io.btrace.fat-agent'
 * }
 *
 * btraceFatAgent {
 *     embedExtensions {
 *         project(':btrace-spark')
 *         file('/path/to/btrace-kafka-extension.zip')
 *     }
 * }
 * </pre>
 *
 * <p>Extension API classes are flattened as {@code .class} files (bootstrap),
 * while impl classes are renamed to {@code .classdata} (loaded at runtime
 * via {@code ClassDataLoader}).
 */
class BTraceFatAgentPlugin implements Plugin<Project> {
    private static final String LOADER_CLASS = 'io.btrace.boot.Loader'
    private static final String AGENT_MAIN_CLASS = 'io.btrace.agent.Main'
    private static final String LOADER_ENTRY = 'io/btrace/boot/Loader.class'
    private static final String MASKED_AGENT_MAIN_ENTRY =
        'META-INF/btrace/agent/io/btrace/agent/Main.classdata'

    @Override
    void apply(Project project) {
        // Apply required plugins
        project.plugins.apply('java')

        // Create DSL extension
        def extension = project.extensions.create('btraceFatAgent', BTraceFatAgentExtension, project)

        // External consumers use this single, pinned masked distribution. It deliberately has no
        // relationship to the extension author's project version or to withdrawn module artifacts.
        def btraceEngine = project.configurations.maybeCreate('btraceEngine')
        btraceEngine.canBeConsumed = false
        btraceEngine.canBeResolved = true
        btraceEngine.defaultDependencies { dependencies ->
            String btraceVersion = BTraceVersion.resolve(
                project,
                extension.btraceVersion,
                BTraceFatAgentPlugin,
                'btraceFatAgent.btraceVersion')
            dependencies.add(project.dependencies.create("io.btrace:btrace:${btraceVersion}"))
        }

        // Create staging directory
        def stagingDir = project.layout.buildDirectory.dir('fat-agent-staging').get().asFile
        def engineStagingDir = project.layout.buildDirectory.dir('btrace-engine-staging').get().asFile

        def stageBTraceEngine = project.tasks.register('stageBTraceEngine') {
            group = 'BTrace Fat Agent'
            description = 'Resolves and stages the masked BTrace engine for an external fat agent'
            inputs.files(project.provider { btraceEngine.singleFile })
                .withPropertyName('btraceEngine')
                .withPathSensitivity(PathSensitivity.RELATIVE)
            outputs.dir(engineStagingDir)
            doLast {
                File engineJar = btraceEngine.singleFile
                validateMaskedEngine(engineJar)
                project.delete(engineStagingDir)
                project.copy {
                    from project.zipTree(engineJar)
                    into engineStagingDir
                    exclude 'META-INF/MANIFEST.MF'
                }
                project.logger.lifecycle("[fat-agent] Staged masked BTrace engine: ${engineJar}")
            }
        }

        // Task: Resolve and stage extensions
        def stageExtensions = project.tasks.register('stageExtensions') {
            group = 'BTrace Fat Agent'
            description = 'Resolves and stages extension content for fat agent JAR'

            // Which extensions are staged is determined entirely by configuration - the DSL, the
            // default set and the filter property - and none of it is a file Gradle can watch.
            // Undeclared, this task is considered up to date whenever the staging directory
            // exists, so a build that changes the selection silently reuses the previous
            // extension set and produces a fat agent that does not match what was asked for.
            inputs.property('extensionSources', project.provider {
                extension.extensionSources.collect { it.toString() }.sort()
            })

            outputs.dir(stagingDir)

            doLast {
                stagingDir.deleteDir()
                stagingDir.mkdirs()

                // Get extension using getByName to avoid classloader issues with findByType
                def ext = project.extensions.getByName('btraceFatAgent')
                project.logger.info("[fat-agent] Extension type: ${ext.class.name}")
                project.logger.info("[fat-agent] Extension sources count: ${ext.extensionSources.size()}")
                ext.extensionSources.each { src ->
                    project.logger.info("[fat-agent] Source: ${src}")
                }

                def resolvedExtensions = resolveExtensionsFromExt(project, ext)
                def embeddedExtIds = []

                resolvedExtensions.each { resolved ->
                    embeddedExtIds << resolved.id
                    stageExtension(project, stagingDir, resolved)
                }

                // Write embedded extensions list for manifest
                def extListFile = new File(stagingDir, 'embedded-extensions.txt')
                extListFile.text = embeddedExtIds.join(',')

                project.logger.lifecycle("[fat-agent] Staged ${resolvedExtensions.size()} extension(s): ${embeddedExtIds.join(', ')}")
            }
        }

        // Task: Stage bundled probes
        def stageProbes = project.tasks.register('stageProbes') {
            group = 'BTrace Fat Agent'
            description = 'Stages bundled probes for fat agent JAR'

            inputs.files(project.provider { extension.probeBundle.probeDirs })
                .withPropertyName('probeDirectories')
                .withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.files(project.provider { extension.probeBundle.sourceRoots })
                .withPropertyName('probeSourceRoots')
                .withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.property(
                'probeIncludes',
                project.provider { new ArrayList<>(extension.probeBundle.includes) })
            inputs.property(
                'probeExcludes',
                project.provider { new ArrayList<>(extension.probeBundle.excludes) })
            outputs.dir(new File(stagingDir, 'META-INF/btrace-probes'))

            doLast {
                def probeSpec = extension.probeBundle
                def probesDir = new File(stagingDir, 'META-INF/btrace-probes')
                project.delete(probesDir)
                if (!probeSpec.hasProbes()) {
                    return
                }

                probesDir.mkdirs()

                def toProbePath = { String binaryName ->
                    validateProbeBinaryName(binaryName)
                    return binaryName.replace('.', '/') + '.class'
                }
                def includePaths = probeSpec.includes.collect(toProbePath)
                def excludePaths = probeSpec.excludes.collect(toProbePath)

                // Copy pre-compiled probes
                probeSpec.probeDirs.each { dir ->
                    if (!dir.isDirectory()) {
                        throw new GradleException(
                            "[fat-agent] Bundled probe directory does not exist: ${dir}")
                    }
                    project.copy {
                        from dir
                        into probesDir
                        if (includePaths.isEmpty()) {
                            include '**/*.class'
                        } else {
                            include includePaths
                        }
                        if (!excludePaths.isEmpty()) {
                            exclude excludePaths
                        }
                    }
                    project.logger.info("[fat-agent] Staged probes from: ${dir}")
                }

                // Compile source probes if configured
                if (probeSpec.hasSourceProbes()) {
                    compileProbes(project, probeSpec, probesDir)
                }

                def stagedNames = [] as Set
                probesDir.eachFileRecurse { File probeFile ->
                    if (!probeFile.name.endsWith('.class')) return
                    def relative = probesDir.toPath().relativize(probeFile.toPath()).toString()
                    def binaryName = relative
                        .replace(File.separatorChar, (char) '/')
                        .replaceAll(/\.class$/, '')
                        .replace('/', '.')
                    validateProbeBinaryName(binaryName)
                    stagedNames.add(binaryName)
                }
                probeSpec.includes.each { String binaryName ->
                    if (!stagedNames.contains(binaryName)) {
                        throw new GradleException(
                            "[fat-agent] Named bundled probe '${binaryName}' was not found in configured probe inputs")
                    }
                }
                if (stagedNames.isEmpty()) {
                    throw new GradleException('[fat-agent] Bundled probe inputs produced no .class files')
                }
                project.logger.lifecycle("[fat-agent] Staged bundled probes: ${stagedNames.join(', ')}")
            }
        }
        stageProbes.configure { mustRunAfter stageExtensions }

        // Task: Build fat agent JAR
        // Try to use ShadowJar if available for package relocation support
        def jarTaskClass = Jar
        try {
            jarTaskClass = Class.forName('com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar')
            project.logger.info("[fat-agent] Using ShadowJar for fat agent build")
        } catch (ClassNotFoundException e) {
            project.logger.info("[fat-agent] ShadowJar not available, using standard Jar")
        }

        def fatAgentJar = project.tasks.register('fatAgentJar', jarTaskClass) {
            group = 'BTrace Fat Agent'
            description = 'Creates fat agent JAR with embedded extensions'

            dependsOn stageExtensions, stageProbes

            archiveBaseName.set(extension.baseName)
            archiveVersion.set('')
            archiveClassifier.set('')

            duplicatesStrategy = DuplicatesStrategy.EXCLUDE

            // Declare the output during task configuration so Gradle can track it. The extension
            // remains lazy because consumers commonly set outputDir after applying the plugin.
            destinationDirectory.set(project.layout.dir(project.provider { extension.outputDir }))

            // Include staged extension content
            from(stagingDir) {
                exclude 'embedded-extensions.txt'
            }

            // The source engine manifest is installed lazily in the external path below. This task
            // then changes only the fat-agent-specific Boot-Class-Path and embedded-extension data.
            manifest {
                attributes('Boot-Class-Path': "${extension.baseName}.jar")
            }

            doFirst {
                // Read embedded extensions list and add to manifest.
                //
                // Merged with whatever the engine already declared rather than replacing it. The
                // published BTrace engine embeds the default extensions, and its class data is
                // copied into this jar wholesale; dropping its ids from the manifest would leave
                // those extensions present as files but invisible to the loader, which reads this
                // attribute to decide what exists. The result would be dead weight in every
                // downstream fat agent.
                def embedded = new LinkedHashSet<String>()
                def seeded = manifest.attributes['BTrace-Embedded-Extensions']
                if (seeded) {
                    seeded.toString().split(',').each { id ->
                        if (id.trim()) embedded << id.trim()
                    }
                }
                def extListFile = new File(stagingDir, 'embedded-extensions.txt')
                if (extListFile.exists() && extListFile.text.trim()) {
                    extListFile.text.trim().split(',').each { id ->
                        if (id.trim()) embedded << id.trim()
                    }
                }
                if (!embedded.isEmpty()) {
                    manifest.attributes['BTrace-Embedded-Extensions'] = embedded.join(',')
                }

                // Add user-specified manifest attributes
                if (!extension.manifestAttributes.isEmpty()) {
                    manifest.attributes.putAll(extension.manifestAttributes)
                }
            }

            doLast {
                validateMaskedFatAgent(archiveFile.get().asFile)
            }
        }

        // Wire up dependencies after ALL projects are evaluated (important for auto-discovery)
        project.gradle.projectsEvaluated {
            def fatAgentTask = fatAgentJar.get()

            // Auto-discover extensions if enabled (must happen first)
            if (extension.autoDiscover) {
                discoverExtensions(project, extension)
            }

            // An in-tree producer remains supported when it produces the same masked engine
            // contract as btrace-dist:btraceJar.
            if (extension.agentJarTask != null) {
                def agentTask = project.tasks.named(extension.agentJarTask.toString()).get()
                fatAgentTask.dependsOn(agentTask)
                fatAgentTask.from(project.zipTree(agentTask.archiveFile))
                fatAgentTask.doFirst {
                    seedManifestFromEngine(fatAgentTask, agentTask.archiveFile.get().asFile)
                }
            } else {
                fatAgentTask.dependsOn(stageBTraceEngine)
                fatAgentTask.from(engineStagingDir)
                fatAgentTask.doFirst {
                    seedManifestFromEngine(fatAgentTask, btraceEngine.singleFile)
                }
            }

            if (extension.bootJarTask != null) {
                throw new GradleException(
                    '[fat-agent] bootJarTask is unsupported. Supply one masked agentJarTask or configure btraceFatAgent.btraceVersion.')
            }

            // Wire extension project dependencies
            extension.extensionSources.each { source ->
                if (source instanceof ProjectExtensionSource) {
                    def extProject = project.project(source.projectPath)
                    stageExtensions.get().dependsOn("${source.projectPath}:buildApiJar")
                    stageExtensions.get().dependsOn("${source.projectPath}:shadowJar")
                }
            }

            // Configure relocations if using ShadowJar
            if (fatAgentTask.class.name.contains('ShadowJar') && !extension.relocations.isEmpty()) {
                extension.relocations.each { from, to ->
                    fatAgentTask.relocate(from, to)
                }
            }
        }
    }

    /**
     * Resolve extensions using duck-typed extension object (avoids classloader issues).
     */
    private List resolveExtensionsFromExt(Project project, def extension) {
        def resolved = []

        extension.extensionSources.each { source ->
            try {
                def ext = source.resolve()
                if (ext != null) {
                    ext.hydrateFromApiManifest()
                    resolved << ext
                    project.logger.info("[fat-agent] Resolved extension: ${ext}")
                }
            } catch (Exception e) {
                project.logger.error("[fat-agent] Failed to resolve ${source}: ${e.message}")
                throw e
            }
        }

        return resolved
    }

    private static void validateProbeBinaryName(String binaryName) {
        boolean valid = binaryName != null &&
            binaryName ==~ /[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*/
        if (valid) {
            valid = binaryName.split(/\./).every { String part ->
                SourceVersion.isIdentifier(part) && !SourceVersion.isKeyword(part)
            }
        }
        if (!valid) {
            throw new GradleException(
                "[fat-agent] Invalid bundled probe binary name: '${binaryName}'")
        }
    }

    private static void validateMaskedFatAgent(File fatJar) {
        new JarFile(fatJar).withCloseable { jar ->
            def attributes = jar.manifest?.mainAttributes
            if (attributes == null) {
                throw invalidFatAgent(fatJar, 'missing META-INF/MANIFEST.MF')
            }

            requireJarEntry(jar, fatJar, LOADER_ENTRY)
            requireJarEntry(jar, fatJar, MASKED_AGENT_MAIN_ENTRY)
            requireManifestAttribute(attributes, fatJar, 'Premain-Class', LOADER_CLASS)
            requireManifestAttribute(attributes, fatJar, 'Agent-Class', LOADER_CLASS)
            requireManifestAttribute(attributes, fatJar, 'Main-Class', LOADER_CLASS)
            requireManifestAttribute(
                attributes, fatJar, 'BTrace-Agent-Main', AGENT_MAIN_CLASS)
            requireManifestAttribute(
                attributes, fatJar, 'Boot-Class-Path', fatJar.name)
        }
    }

    private static void validateMaskedEngine(File engineJar) {
        new JarFile(engineJar).withCloseable { jar ->
            def attributes = jar.manifest?.mainAttributes
            if (attributes == null) {
                throw invalidFatAgent(engineJar, 'missing META-INF/MANIFEST.MF')
            }
            requireJarEntry(jar, engineJar, LOADER_ENTRY)
            requireJarEntry(jar, engineJar, MASKED_AGENT_MAIN_ENTRY)
            requireManifestAttribute(attributes, engineJar, 'Premain-Class', LOADER_CLASS)
            requireManifestAttribute(attributes, engineJar, 'Agent-Class', LOADER_CLASS)
            requireManifestAttribute(attributes, engineJar, 'Main-Class', LOADER_CLASS)
            requireManifestAttribute(attributes, engineJar, 'BTrace-Agent-Main', AGENT_MAIN_CLASS)
        }
    }

    private static void seedManifestFromEngine(def fatAgentTask, File engineJar) {
        validateMaskedEngine(engineJar)
        new JarFile(engineJar).withCloseable { jar ->
            Manifest manifest = jar.manifest
            manifest.mainAttributes.each { key, value ->
                fatAgentTask.manifest.attributes([(key.toString()): value.toString()])
            }
        }
        // A fat agent is self-contained, so this is the one source-manifest attribute adjusted.
        fatAgentTask.manifest.attributes(
            ['Boot-Class-Path': fatAgentTask.archiveFile.get().asFile.name])
    }

    private static void requireJarEntry(JarFile jar, File fatJar, String entry) {
        if (jar.getJarEntry(entry) == null) {
            throw invalidFatAgent(fatJar, "missing ${entry}")
        }
    }

    private static void requireManifestAttribute(
        def attributes, File fatJar, String name, String expected) {
        def actual = attributes.getValue(name)
        if (actual != expected) {
            throw invalidFatAgent(
                fatJar, "manifest ${name} must be '${expected}' but was '${actual}'")
        }
    }

    private static GradleException invalidFatAgent(File fatJar, String problem) {
        return new GradleException(
            "[fat-agent] Invalid masked BTrace fat JAR ${fatJar}: ${problem}. " +
                "Configure agentJarTask to reference the btraceJar task or set btraceFatAgent.btraceVersion for an external build.")
    }

    /**
     * Stage a resolved extension to the staging directory.
     */
    private void stageExtension(Project project, File stagingDir, ResolvedExtension ext) {
        // Stage API classes as .class files (for bootstrap)
        if (ext.apiJar?.exists()) {
            project.copy {
                from project.zipTree(ext.apiJar)
                into stagingDir
                include '**/*.class'
            }
            project.logger.info("[fat-agent] Staged API from: ${ext.apiJar.name}")
        }

        // Stage impl classes as .classdata files (for ClassDataLoader)
        if (ext.implJar?.exists()) {
            project.copy {
                from project.zipTree(ext.implJar)
                into stagingDir
                include '**/*.class'
                rename '(.+)\\.class', '$1.classdata'
            }
            def declaredServices = (ext.metadata?.getProperty('services') ?: '')
                .split(',')
                .collect { it.trim() }
                .findAll { !it.isEmpty() }
            if (!declaredServices.isEmpty()) {
                project.copy {
                    from project.zipTree(ext.implJar)
                    into stagingDir
                    include declaredServices.collect { "META-INF/services/${it}" }
                }
            }
            project.logger.info("[fat-agent] Staged impl (as .classdata) from: ${ext.implJar.name}")
        }

        // Stage extension metadata
        def extMetaDir = new File(stagingDir, "META-INF/btrace-extensions/${ext.id}")
        extMetaDir.mkdirs()

        def properties = new LinkedHashMap<String, String>()
        properties.id = ext.id
        properties.version = ext.version
        properties.name = ext.metadata?.getProperty('name') ?: ext.id
        properties.description = ext.metadata?.getProperty('description') ?: ''
        ['btrace.api.version', 'java.version', 'services', 'requires.extensions', 'permissions',
         'exports', 'configurator', 'probes'].each { key ->
            def value = ext.metadata?.getProperty(key)
            if (value != null) properties[key] = value
        }

        def escapeProperty = { String value ->
            def escaped = new StringBuilder()
            value.toCharArray().eachWithIndex { char character, int index ->
                switch (character) {
                    case '\\': escaped.append('\\\\'); break
                    case '\t': escaped.append('\\t'); break
                    case '\n': escaped.append('\\n'); break
                    case '\r': escaped.append('\\r'); break
                    case '\f': escaped.append('\\f'); break
                    case ' ': escaped.append(index == 0 ? '\\ ' : ' '); break
                    default:
                        int codePoint = (int) character
                        if (codePoint < 0x20 || codePoint > 0x7e) {
                            escaped.append(String.format('\\u%04x', codePoint))
                        } else {
                            escaped.append(character)
                        }
                }
            }
            escaped.toString()
        }
        def propsFile = new File(extMetaDir, 'extension.properties')
        propsFile.withWriter('ISO-8859-1') { writer ->
            properties.each { key, value ->
                writer.write("${key}=${escapeProperty(value)}\n")
            }
        }

        project.logger.info("[fat-agent] Created extension.properties for ${ext.id}")
    }

    /**
     * Compile probe sources using BTrace compiler.
     */
    private void compileProbes(Project project, ProbeBundleSpec probeSpec, File outputDir) {
        // Find btrace-compiler dependency
        def compilerJar = findBTraceCompiler(project)
        if (compilerJar == null) {
            project.logger.warn("[fat-agent] BTrace compiler not found, skipping probe compilation")
            return
        }

        probeSpec.sourceRoots.each { sourceDir ->
            if (!sourceDir.exists()) {
                return
            }

            project.logger.info("[fat-agent] Compiling probes from: ${sourceDir}")

            // The public BTrace artifact is a masked distribution. Run its loader and select the
            // compiler client entry point rather than assuming a conventional btrace-client JAR.
            project.javaexec {
                classpath = project.files(compilerJar)
                mainClass = LOADER_CLASS
                jvmArgs '-Dbtrace.client.main=io.btrace.compiler.Compiler'
                args = ['-d', outputDir.absolutePath]

                // Add all .java files
                sourceDir.eachFileRecurse { f ->
                    if (f.name.endsWith('.java')) {
                        args << f.absolutePath
                    }
                }
            }
        }
    }

    /**
     * Find the public BTrace distribution used to launch the compiler.
     */
    private File findBTraceCompiler(Project project) {
        // Try to find from btrace-dist or similar project
        def distProject = project.rootProject.subprojects.find { it.name == 'btrace-dist' }
        if (distProject != null) {
            def btraceJar = distProject.tasks.findByName('btraceJar')?.archiveFile?.get()?.asFile
            if (btraceJar?.exists()) {
                return btraceJar
            }
        }

        // Try configuration resolution
        try {
            def config = project.configurations.findByName('btracec') ?:
                project.configurations.create('btracec')
            if (config.dependencies.isEmpty()) {
                def extension = project.extensions.getByName('btraceFatAgent') as BTraceFatAgentExtension
                String btraceVersion = BTraceVersion.resolve(
                    project, extension.btraceVersion, BTraceFatAgentPlugin, 'btraceFatAgent.btraceVersion')
                config.dependencies.add(project.dependencies.create("io.btrace:btrace:${btraceVersion}"))
            }
            return config.resolve().find { it.name.startsWith('btrace-') }
        } catch (Exception e) {
            project.logger.debug("[fat-agent] Could not resolve BTrace distribution: ${e.message}")
        }

        return null
    }

    /**
     * Auto-discover extension projects in the build.
     */
    private void discoverExtensions(Project project, BTraceFatAgentExtension extension) {
        // Check for filter property
        def filterList = project.hasProperty(extension.filterProperty)
            ? project.property(extension.filterProperty).toString().split(',').toList()
            : null

        // Spelled out rather than written as a chain of elvis operators: an empty defaultExtensions
        // is falsy, so `filterList ?: extension.defaultExtensions` would select nothing at all and
        // silently produce an extension-free fat agent.
        def selection
        if (filterList != null) {
            selection = filterList
        } else if (extension.defaultExtensions) {
            selection = extension.defaultExtensions
        } else {
            selection = null
        }

        project.rootProject.subprojects.each { sp ->
            if (sp.plugins.hasPlugin('io.btrace.extension')) {
                // Consumers set the filter property using project names, while an in-build default
                // list is naturally written with project paths. Accept either.
                if (selection == null || selection.contains(sp.name) || selection.contains(sp.path)) {
                    // Add as project source if not already present
                    def alreadyAdded = extension.extensionSources.any { source ->
                        source instanceof ProjectExtensionSource && source.projectPath == sp.path
                    }
                    if (!alreadyAdded) {
                        extension.addExtensionSource(new ProjectExtensionSource(project, sp.path))
                        project.logger.info("[fat-agent] Auto-discovered extension: ${sp.path}")
                    }
                }
            }
        }
    }
}
