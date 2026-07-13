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
 *         maven('io.btrace:btrace-kafka:2.3.0')
 *     }
 * }
 * </pre>
 *
 * <p>Extension API classes are flattened as {@code .class} files (bootstrap),
 * while impl classes are renamed to {@code .classdata} (loaded at runtime
 * via {@code ClassDataLoader}).
 */
class BTraceFatAgentPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // Apply required plugins
        project.plugins.apply('java')

        // Create DSL extension
        def extension = project.extensions.create('btraceFatAgent', BTraceFatAgentExtension, project)

        // Create staging directory
        def stagingDir = project.layout.buildDirectory.dir('fat-agent-staging').get().asFile

        // Task: Resolve and stage extensions
        def stageExtensions = project.tasks.register('stageExtensions') {
            group = 'BTrace Fat Agent'
            description = 'Resolves and stages extension content for fat agent JAR'

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

            // Configure manifest
            manifest {
                attributes(
                    'Premain-Class': 'io.btrace.boot.Loader',
                    'Agent-Class': 'io.btrace.boot.Loader',
                    'Main-Class': 'io.btrace.boot.Loader',
                    'Can-Redefine-Classes': 'true',
                    'Can-Retransform-Classes': 'true',
                    'Boot-Class-Path': "${extension.baseName}.jar",
                    'BTrace-Version': project.version,
                    'BTrace-Agent-Main': 'io.btrace.agent.Main',
                    'BTrace-Client-Main': 'io.btrace.client.Main'
                )
            }

            doFirst {
                // Read embedded extensions list and add to manifest
                def extListFile = new File(stagingDir, 'embedded-extensions.txt')
                if (extListFile.exists() && extListFile.text.trim()) {
                    manifest.attributes['BTrace-Embedded-Extensions'] = extListFile.text.trim()
                }

                // Add user-specified manifest attributes
                if (!extension.manifestAttributes.isEmpty()) {
                    manifest.attributes.putAll(extension.manifestAttributes)
                }
            }
        }

        // Wire up dependencies after ALL projects are evaluated (important for auto-discovery)
        project.gradle.projectsEvaluated {
            def fatAgentTask = fatAgentJar.get()

            // Auto-discover extensions if enabled (must happen first)
            if (extension.autoDiscover) {
                discoverExtensions(project, extension)
            }

            // Include base agent/boot JARs if specified
            if (extension.agentJarTask != null) {
                def agentTask = project.tasks.named(extension.agentJarTask.toString()).get()
                fatAgentTask.dependsOn(agentTask)
                fatAgentTask.from(project.zipTree(agentTask.archiveFile))
            }

            if (extension.bootJarTask != null) {
                def bootTask = project.tasks.named(extension.bootJarTask.toString()).get()
                fatAgentTask.dependsOn(bootTask)
                fatAgentTask.from(project.zipTree(bootTask.archiveFile))
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

            // Use Gradle's JavaExec to run btracec
            project.javaexec {
                classpath = project.files(compilerJar)
                mainClass = 'io.btrace.compiler.Compiler'
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
     * Find btrace-compiler JAR in project dependencies.
     */
    private File findBTraceCompiler(Project project) {
        // Try to find from btrace-dist or similar project
        def distProject = project.rootProject.subprojects.find { it.name == 'btrace-dist' }
        if (distProject != null) {
            def clientJar = distProject.tasks.findByName('clientJar')?.archiveFile?.get()?.asFile
            if (clientJar?.exists()) {
                return clientJar
            }
        }

        // Try configuration resolution
        try {
            def config = project.configurations.findByName('btracec') ?:
                project.configurations.create('btracec')
            if (config.dependencies.isEmpty()) {
                config.dependencies.add(project.dependencies.create('io.btrace:btrace-client:+'))
            }
            return config.resolve().find { it.name.contains('btrace-client') }
        } catch (Exception e) {
            project.logger.debug("[fat-agent] Could not resolve btrace-client: ${e.message}")
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

        project.rootProject.subprojects.each { sp ->
            if (sp.plugins.hasPlugin('io.btrace.extension')) {
                if (filterList == null || filterList.contains(sp.name)) {
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
