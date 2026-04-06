package org.openjdk.btrace.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Jar

/**
 * Gradle plugin for assembling a fat BTrace agent JAR that bundles extensions
 * and probes into a single deployable file.
 *
 * <p>Usage in an external project's {@code build.gradle}:
 * <pre>
 * plugins {
 *     id 'org.openjdk.btrace.fat-agent'
 * }
 *
 * btraceFatAgent {
 *     // Base btrace.jar — downloaded from Maven or referenced locally
 *     agentJar = file('libs/btrace.jar')
 *     // or: agentJar = configurations.btraceAgent.singleFile
 *
 *     // Extensions to embed (extension ZIPs from Maven or local projects)
 *     extensions {
 *         // From Maven coordinates
 *         maven 'org.openjdk.btrace:btrace-metrics:2.3.0:extension@zip'
 *         // From local project (if in same multi-project build)
 *         project ':my-btrace-extension'
 *         // From local file
 *         file 'libs/my-extension.zip'
 *     }
 *
 *     // Pre-compiled probe classes to bundle
 *     probes {
 *         from 'src/probes'     // directory of .class files
 *         from 'build/probes'   // another directory
 *     }
 * }
 * </pre>
 *
 * <p>The plugin produces a fat agent JAR that can be used as:
 * {@code -javaagent:my-fat-agent.jar=probes=SparkTracer,stdout=true}
 */
class BTraceFatAgentPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply('java-base')

        def ext = project.extensions.create('btraceFatAgent', BTraceFatAgentExtension, project)

        // Configuration for resolving the base btrace.jar from Maven
        project.configurations.maybeCreate('btraceAgent')

        // Configuration for resolving extension ZIPs from Maven
        project.configurations.maybeCreate('btraceExtensions')

        def stagingDir = project.layout.buildDirectory.dir('btrace-fat-agent-staging')

        // Task: stage extensions into the embedded format
        def stageExtensions = project.tasks.register('stageEmbeddedExtensions') {
            group = 'BTrace'
            description = 'Stage extensions into META-INF/btrace-extensions/ layout'

            // Wire inputs from the extension specs
            inputs.files(project.provider { ext.resolveExtensionFiles() })
            outputs.dir(stagingDir)

            doLast {
                def staging = stagingDir.get().asFile
                staging.deleteDir()
                staging.mkdirs()

                def extensionsDir = new File(staging, 'extensions')
                extensionsDir.mkdirs()

                def embeddedIds = []

                ext.resolveExtensionFiles().each { File zipFile ->
                    if (!zipFile.exists()) {
                        throw new GradleException("Extension file not found: ${zipFile}")
                    }
                    if (!zipFile.name.endsWith('.zip')) {
                        throw new GradleException("Extension file must be a ZIP: ${zipFile}")
                    }

                    // Extract and process the extension ZIP
                    def extId = extractExtensionId(project, zipFile)
                    if (extId == null) {
                        throw new GradleException("Cannot determine extension ID from ${zipFile.name}. " +
                                "Ensure the API JAR contains BTrace-Extension-Id manifest attribute.")
                    }

                    project.logger.lifecycle("[BTRACE-FAT] Processing extension: ${extId} from ${zipFile.name}")

                    def extDir = new File(extensionsDir, extId)
                    extDir.mkdirs()

                    // Extract the ZIP
                    def tempExtract = new File(staging, "temp-${extId}")
                    tempExtract.mkdirs()
                    project.copy {
                        from project.zipTree(zipFile)
                        into tempExtract
                    }

                    // Find API and impl JARs
                    def apiJar = tempExtract.listFiles()?.find { it.name.endsWith('-api.jar') || it.name.contains('-api.') }
                    def implJar = tempExtract.listFiles()?.find {
                        (it.name.endsWith('-impl.jar') || it.name.contains('-impl.')) && it.name != apiJar?.name
                    }

                    // Generate extension.properties from API JAR manifest
                    def props = new Properties()
                    props.setProperty('id', extId)
                    if (apiJar) {
                        def manifest = readManifest(apiJar)
                        if (manifest) {
                            def attrs = manifest.mainAttributes
                            props.setProperty('version', attrs.getValue('BTrace-Extension-Version') ?: '0.0.0')
                            props.setProperty('name', attrs.getValue('BTrace-Extension-Name') ?: extId)
                            props.setProperty('description', attrs.getValue('BTrace-Extension-Description') ?: '')
                            def services = attrs.getValue('BTrace-Extension-Services')
                            if (services) props.setProperty('services', services)
                            def permissions = attrs.getValue('BTrace-Extension-Permissions')
                            if (permissions) props.setProperty('permissions', permissions)

                            // Carry over provided dependency locator config
                            def providedLocator = attrs.getValue('BTrace-Provided-Locator')
                            if (providedLocator) {
                                props.setProperty('provided.locator', providedLocator)
                                def providedRequired = attrs.getValue('BTrace-Provided-Required')
                                if (providedRequired) props.setProperty('provided.required', providedRequired)
                            }
                        }

                        // Also read locator properties from btrace-extension.properties inside the API JAR
                        if (apiJar) {
                            try {
                                def apiJarFile = new java.util.jar.JarFile(apiJar)
                                try {
                                    def propsEntry = apiJarFile.getJarEntry('META-INF/btrace-extension.properties')
                                    if (propsEntry) {
                                        def locatorProps = new Properties()
                                        locatorProps.load(apiJarFile.getInputStream(propsEntry))
                                        locatorProps.each { k, v ->
                                            if (k.toString().startsWith('locator.')) {
                                                props.setProperty("provided.${k}", v.toString())
                                            }
                                        }
                                    }
                                } finally {
                                    apiJarFile.close()
                                }
                            } catch (Exception ignore) {}
                        }
                    } else {
                        props.setProperty('version', '0.0.0')
                    }

                    new File(extDir, 'extension.properties').withWriter { w ->
                        props.store(w, "Generated by btrace-fat-agent plugin")
                    }

                    // Convert impl JAR classes to .classdata in impl/ subdirectory
                    if (implJar) {
                        def implDir = new File(extDir, 'impl')
                        implDir.mkdirs()
                        project.copy {
                            from project.zipTree(implJar)
                            into implDir
                            include '**/*.class'
                            exclude 'META-INF/**'
                            rename { name -> name.endsWith('.class') ? name.replace('.class', '.classdata') : name }
                        }
                        project.logger.lifecycle("[BTRACE-FAT]   impl classes staged as .classdata")
                    }

                    // Clean up temp
                    tempExtract.deleteDir()
                    embeddedIds.add(extId)
                }

                // Store the extension ID list for manifest generation
                new File(staging, 'embedded-extension-ids.txt').text = embeddedIds.join(',')

                // Stage probe classes
                def probesStaging = new File(staging, 'probes')
                ext.probeDirs.each { dir ->
                    if (dir.exists() && dir.isDirectory()) {
                        project.copy {
                            from dir
                            into probesStaging
                            include '**/*.class'
                        }
                        project.logger.lifecycle("[BTRACE-FAT] Staged probes from ${dir}")
                    }
                }

                // If probes are found, associate them with a synthetic embedded extension
                if (probesStaging.exists() && probesStaging.listFiles()?.length > 0) {
                    def probeExtId = ext.probeExtensionId ?: 'bundled-probes'
                    def probeExtDir = new File(extensionsDir, probeExtId)
                    probeExtDir.mkdirs()

                    // Move probe classes into the extension's probes/ dir
                    def probeTargetDir = new File(probeExtDir, 'probes')
                    probeTargetDir.mkdirs()
                    project.copy {
                        from probesStaging
                        into probeTargetDir
                        include '**/*.class'
                    }

                    // Generate extension.properties for the probe container
                    def probeNames = []
                    probeTargetDir.eachFileRecurse { f ->
                        if (f.name.endsWith('.class')) {
                            probeNames.add(f.name.replace('.class', ''))
                        }
                    }

                    def probeProps = new Properties()
                    probeProps.setProperty('id', probeExtId)
                    probeProps.setProperty('version', project.version as String)
                    probeProps.setProperty('name', 'Bundled Probes')
                    probeProps.setProperty('description', 'Pre-compiled probes bundled in fat agent')
                    probeProps.setProperty('probes', probeNames.join(','))

                    new File(probeExtDir, 'extension.properties').withWriter { w ->
                        probeProps.store(w, "Generated by btrace-fat-agent plugin")
                    }

                    embeddedIds.add(probeExtId)
                    // Re-write the IDs file
                    new File(staging, 'embedded-extension-ids.txt').text = embeddedIds.join(',')

                    project.logger.lifecycle("[BTRACE-FAT] Bundled ${probeNames.size()} probe(s) as extension '${probeExtId}'")
                }

                probesStaging.deleteDir()
            }
        }

        // Task: assemble the fat agent JAR
        def fatAgentJar = project.tasks.register('fatAgentJar', Jar) {
            group = 'BTrace'
            description = 'Assemble fat BTrace agent JAR with embedded extensions and probes'
            dependsOn stageExtensions

            archiveBaseName.set(project.provider { ext.outputName ?: "${project.name}-agent" })
            archiveVersion.set('')
            archiveClassifier.set('')
            destinationDirectory.set(project.layout.buildDirectory.dir('libs'))

            // Start from the base btrace.jar
            from(project.provider {
                def agentJar = ext.resolveAgentJar()
                if (agentJar == null || !agentJar.exists()) {
                    throw new GradleException(
                            "Base btrace.jar not found. Set btraceFatAgent.agentJar or add a dependency to 'btraceAgent' configuration.")
                }
                project.zipTree(agentJar)
            })

            // Add embedded extension data
            into('META-INF/btrace-extensions') {
                from(stagingDir.map { new File(it.asFile, 'extensions') }) {
                    include '**/*.classdata'
                    include '**/extension.properties'
                    include '**/probes/*.class'
                }
            }

            // Add extension API classes to bootstrap section (as .class files in JAR root)
            from(project.provider {
                def apiClassDirs = []
                ext.resolveExtensionFiles().each { File zipFile ->
                    if (zipFile.exists()) {
                        // Find API JAR inside extension ZIP
                        project.zipTree(zipFile).matching {
                            include '*-api.jar'
                            include '*-api.*.jar'
                        }.each { apiJar ->
                            apiClassDirs.add(project.zipTree(apiJar).matching {
                                include '**/*.class'
                                exclude 'META-INF/**'
                            })
                        }
                    }
                }
                apiClassDirs
            })

            // Update manifest with embedded extension IDs
            manifest {
                from(project.provider {
                    def agentJar = ext.resolveAgentJar()
                    if (agentJar != null && agentJar.exists()) {
                        def m = readManifest(agentJar)
                        if (m) return m
                    }
                    return new java.util.jar.Manifest()
                })

                attributes(project.provider {
                    def idsFile = stagingDir.get().file('embedded-extension-ids.txt').asFile
                    def ids = idsFile.exists() ? idsFile.text.trim() : ''
                    return ['BTrace-Embedded-Extensions': ids] as Map<String, String>
                })
            }

            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        // Wire into the build lifecycle
        project.tasks.matching { it.name == 'assemble' }.configureEach {
            it.dependsOn fatAgentJar
        }
    }

    private static String extractExtensionId(Project project, File zipFile) {
        // Try to find the API JAR inside the ZIP and read its manifest
        def tempDir = new File(project.buildDir, "btrace-ext-id-${zipFile.name.hashCode()}")
        try {
            tempDir.mkdirs()
            project.copy {
                from project.zipTree(zipFile)
                into tempDir
                include '*-api.jar'
                include '*-api.*.jar'
            }
            def apiJar = tempDir.listFiles()?.find { it.name.contains('-api') && it.name.endsWith('.jar') }
            if (apiJar) {
                def manifest = readManifest(apiJar)
                if (manifest) {
                    def id = manifest.mainAttributes.getValue('BTrace-Extension-Id')
                    if (id) return id
                }
            }
            // Fallback: infer from ZIP filename
            def name = zipFile.name
            def m = name =~ /^(.+?)-\d+/
            if (m.find()) return m.group(1)
            return name.replace('.zip', '').replaceAll(/-extension$/, '')
        } finally {
            tempDir.deleteDir()
        }
    }

    private static java.util.jar.Manifest readManifest(File jarFile) {
        try {
            def jar = new java.util.jar.JarFile(jarFile)
            try {
                return jar.manifest
            } finally {
                jar.close()
            }
        } catch (Exception e) {
            return null
        }
    }
}

/**
 * DSL extension for configuring the fat agent JAR.
 */
class BTraceFatAgentExtension {
    private final Project project

    /** Base btrace.jar file. If null, resolved from 'btraceAgent' configuration. */
    File agentJar

    /** Output JAR base name. Defaults to "{project-name}-agent". */
    String outputName

    /** Extension ID for the bundled probes container. Defaults to "bundled-probes". */
    String probeExtensionId

    private final List<Object> extensionSpecs = []
    private final List<File> probeDirs = []

    BTraceFatAgentExtension(Project project) {
        this.project = project
    }

    /** Configure extensions to embed. */
    void extensions(@DelegatesTo(ExtensionSpec) Closure cl) {
        def spec = new ExtensionSpec(project)
        cl.delegate = spec
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl.call()
        extensionSpecs.addAll(spec.specs)
    }

    /** Configure probe directories. */
    void probes(@DelegatesTo(ProbeSpec) Closure cl) {
        def spec = new ProbeSpec(project)
        cl.delegate = spec
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl.call()
        probeDirs.addAll(spec.dirs)
    }

    /** Resolve the base agent JAR file. */
    File resolveAgentJar() {
        if (agentJar != null) return agentJar
        def conf = project.configurations.findByName('btraceAgent')
        if (conf && !conf.empty) {
            return conf.singleFile
        }
        return null
    }

    /** Resolve all extension files (ZIPs). */
    List<File> resolveExtensionFiles() {
        def files = []
        extensionSpecs.each { spec ->
            if (spec instanceof File) {
                files.add(spec)
            } else if (spec instanceof String) {
                // Maven coordinate — resolve from btraceExtensions configuration
                def conf = project.configurations.findByName('btraceExtensions')
                if (conf) {
                    conf.resolvedConfiguration.resolvedArtifacts.each { a ->
                        if (a.file.name.endsWith('.zip')) {
                            files.add(a.file)
                        }
                    }
                }
            } else if (spec instanceof org.gradle.api.Project) {
                // Project dependency — find its packageExtension output
                def task = spec.tasks.findByName('packageExtension')
                if (task instanceof org.gradle.api.tasks.bundling.Zip) {
                    files.add(task.archiveFile.get().asFile)
                }
            }
        }
        return files
    }

    List<File> getProbeDirs() {
        return probeDirs
    }

    static class ExtensionSpec {
        final Project project
        final List<Object> specs = []

        ExtensionSpec(Project project) { this.project = project }

        /** Add extension from Maven coordinates (e.g., 'org.openjdk.btrace:btrace-metrics:2.3.0:extension@zip'). */
        void maven(String coordinates) {
            project.dependencies.add('btraceExtensions', coordinates)
            specs.add(coordinates)
        }

        /** Add extension from a local project. */
        void project(String path) {
            def p = project.rootProject.findProject(path)
            if (p == null) throw new GradleException("Project '${path}' not found")
            specs.add(p)
        }

        /** Add extension from a local file. */
        void file(Object path) {
            specs.add(project.file(path))
        }
    }

    static class ProbeSpec {
        final Project project
        final List<File> dirs = []

        ProbeSpec(Project project) { this.project = project }

        /** Add a directory containing pre-compiled probe .class files. */
        void from(Object path) {
            dirs.add(project.file(path))
        }
    }
}
