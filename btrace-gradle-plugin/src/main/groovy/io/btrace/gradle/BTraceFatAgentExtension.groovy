package io.btrace.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.FileCollection

/**
 * DSL extension for configuring fat agent JAR builds.
 *
 * <p>Usage:
 * <pre>
 * btraceFatAgent {
 *     baseName = 'my-btrace-agent'
 *
 *     embedExtensions {
 *         project(':btrace-spark')
 *         maven('io.btrace:btrace-kafka:2.3.0')
 *         file('/path/to/extension.zip')
 *     }
 *
 *     bundledProbes {
 *         from 'src/probes'
 *     }
 * }
 * </pre>
 */
class BTraceFatAgentExtension {
    private final Project project
    private final List<ExtensionSource> extensionSources = []
    private final ProbeBundleSpec probeBundle

    /** Output JAR base name (default: 'btrace-agent-fat') */
    String baseName = 'btrace-agent-fat'

    /** Output directory (default: project.layout.buildDirectory.dir('libs')) */
    File outputDir

    /** Additional manifest attributes */
    Map<String, String> manifestAttributes = [:]

    /** Package relocations (from -> to) */
    Map<String, String> relocations = [:]

    /** Reference to agent JAR task (required for standalone builds) */
    Object agentJarTask

    /** Reference to boot JAR task (required for standalone builds) */
    Object bootJarTask

    /** Whether to auto-discover extensions from subprojects (default: false) */
    boolean autoDiscover = false

    /** Property name for filtering extensions when autoDiscover is true */
    String filterProperty = 'embedExtensions'

    BTraceFatAgentExtension(Project project) {
        this.project = project
        this.outputDir = project.layout.buildDirectory.dir('libs').get().asFile
        this.probeBundle = new ProbeBundleSpec(project)
    }

    /**
     * Configure embedded extensions.
     */
    void embedExtensions(Action<ExtensionSourceSpec> action) {
        def spec = new ExtensionSourceSpec(project)
        action.execute(spec)
        extensionSources.addAll(spec.sources)
    }

    /**
     * Configure bundled probes.
     */
    void bundledProbes(Action<ProbeBundleSpec> action) {
        action.execute(probeBundle)
    }

    /**
     * Configure manifest attributes.
     */
    void manifest(Action<ManifestSpec> action) {
        def spec = new ManifestSpec()
        action.execute(spec)
        manifestAttributes.putAll(spec.attributes)
    }

    /**
     * Add a package relocation.
     */
    void relocate(String from, String to) {
        relocations[from] = to
    }

    /**
     * Get all configured extension sources.
     */
    List<ExtensionSource> getExtensionSources() {
        return new ArrayList<>(extensionSources)
    }

    /**
     * Add an extension source directly to the internal list.
     * Used by auto-discovery to add sources after configuration.
     */
    void addExtensionSource(ExtensionSource source) {
        extensionSources.add(source)
    }

    /**
     * Get probe bundle configuration.
     */
    ProbeBundleSpec getProbeBundle() {
        return probeBundle
    }
}

/**
 * DSL spec for configuring extension sources.
 */
class ExtensionSourceSpec {
    private final Project project
    final List<ExtensionSource> sources = []

    ExtensionSourceSpec(Project project) {
        this.project = project
    }

    /**
     * Add extension from a project reference.
     */
    void project(String projectPath) {
        sources << new ProjectExtensionSource(project, projectPath)
    }

    /**
     * Add extensions from multiple project references.
     */
    void projects(String... projectPaths) {
        projectPaths.each { path ->
            sources << new ProjectExtensionSource(project, path)
        }
    }

    /**
     * Add extension from Maven coordinates.
     * @param coords Maven coordinates in format 'group:artifact:version'
     */
    void maven(String coords) {
        sources << new MavenExtensionSource(project, coords)
    }

    /**
     * Add extension from Maven coordinates with named parameters.
     */
    void maven(Map<String, String> coords) {
        def coordStr = "${coords.group}:${coords.name}:${coords.version}"
        sources << new MavenExtensionSource(project, coordStr)
    }

    /**
     * Add extension from a local file.
     */
    void file(String path) {
        sources << new FileExtensionSource(project, project.file(path))
    }

    /**
     * Add extension from a local file.
     */
    void file(File file) {
        sources << new FileExtensionSource(project, file)
    }

    /**
     * Add extensions from multiple files matching a pattern.
     */
    void files(String pattern) {
        project.fileTree(dir: project.projectDir, includes: [pattern]).files.each { f ->
            sources << new FileExtensionSource(project, f)
        }
    }
}

/**
 * DSL spec for configuring bundled probes.
 */
class ProbeBundleSpec {
    private final Project project
    final List<File> probeDirs = []
    final List<File> sourceRoots = []
    final List<String> includes = []
    final List<String> excludes = []

    ProbeBundleSpec(Project project) {
        this.project = project
    }

    /**
     * Add pre-compiled probe classes from a directory.
     */
    void from(String path) {
        probeDirs << project.file(path)
    }

    /**
     * Add pre-compiled probe classes from a directory.
     */
    void from(File dir) {
        probeDirs << dir
    }

    /**
     * Add probe sources to compile.
     */
    void fromSource(String path) {
        sourceRoots << project.file(path)
    }

    /**
     * Add probe sources to compile.
     */
    void fromSource(File dir) {
        sourceRoots << dir
    }

    /**
     * Include only specific probe classes by simple name.
     */
    void include(String... probeNames) {
        includes.addAll(probeNames)
    }

    /**
     * Exclude specific probe classes by simple name.
     */
    void exclude(String... probeNames) {
        excludes.addAll(probeNames)
    }

    boolean hasProbes() {
        return !probeDirs.isEmpty() || !sourceRoots.isEmpty()
    }

    boolean hasSourceProbes() {
        return !sourceRoots.isEmpty()
    }
}

/**
 * DSL spec for manifest configuration.
 */
class ManifestSpec {
    final Map<String, String> attributes = [:]

    void attributes(Map<String, String> attrs) {
        attributes.putAll(attrs)
    }
}

/**
 * Base class for extension sources.
 */
abstract class ExtensionSource {
    protected final Project project

    ExtensionSource(Project project) {
        this.project = project
    }

    /**
     * Resolve this source to a concrete extension descriptor.
     */
    abstract ResolvedExtension resolve()
}

/**
 * Extension source from a project reference.
 */
class ProjectExtensionSource extends ExtensionSource {
    final String projectPath

    ProjectExtensionSource(Project project, String projectPath) {
        super(project)
        this.projectPath = projectPath
    }

    @Override
    ResolvedExtension resolve() {
        def extProject = project.project(projectPath)

        // Check if the project has the BTrace extension plugin
        if (!extProject.plugins.hasPlugin('org.openjdk.btrace.extension')) {
            throw new IllegalStateException(
                "Project '${projectPath}' does not have the 'org.openjdk.btrace.extension' plugin applied")
        }

        def buildApiJar = extProject.tasks.findByName('buildApiJar')
        def shadowJar = extProject.tasks.findByName('shadowJar')

        if (buildApiJar == null || shadowJar == null) {
            throw new IllegalStateException(
                "Project '${projectPath}' is missing required tasks (buildApiJar, shadowJar)")
        }

        def ext = extProject.extensions.findByType(BTraceExtensionMetadata)
        def extId = ext?.id ?: extProject.name

        return new ResolvedExtension(
            id: extId,
            version: extProject.version?.toString() ?: '0.0.0',
            apiJar: buildApiJar.archiveFile.get().asFile,
            implJar: shadowJar.archiveFile.get().asFile,
            sourceProject: extProject
        )
    }

    @Override
    String toString() {
        return "project(${projectPath})"
    }
}

/**
 * Extension source from Maven coordinates.
 */
class MavenExtensionSource extends ExtensionSource {
    final String coordinates

    MavenExtensionSource(Project project, String coordinates) {
        super(project)
        this.coordinates = coordinates
    }

    @Override
    ResolvedExtension resolve() {
        // Create a detached configuration to resolve the extension
        def config = project.configurations.detachedConfiguration(
            project.dependencies.create(coordinates)
        )
        config.transitive = false

        def files = config.resolve()
        if (files.isEmpty()) {
            throw new IllegalStateException("Could not resolve extension: ${coordinates}")
        }

        def extensionZip = files.first()

        // Extract extension metadata from ZIP
        return extractFromZip(extensionZip)
    }

    private ResolvedExtension extractFromZip(File zipFile) {
        def tempDir = project.layout.buildDirectory
            .dir("fat-agent-staging/maven/${coordinates.replace(':', '_')}")
            .get()
            .asFile
        tempDir.mkdirs()

        // Extract ZIP
        project.copy {
            from project.zipTree(zipFile)
            into tempDir
        }

        // Find API and impl JARs
        def apiJar = tempDir.listFiles()?.find { it.name.endsWith('-api.jar') }
        def implJar = tempDir.listFiles()?.find { it.name.endsWith('-impl.jar') || it.name.endsWith('.jar') && !it.name.endsWith('-api.jar') }

        // Read metadata
        def propsFile = new File(tempDir, 'extension.properties')
        def props = new Properties()
        if (propsFile.exists()) {
            propsFile.withInputStream { props.load(it) }
        }

        def parts = coordinates.split(':')
        def extId = props.getProperty('id') ?: parts[1]
        def extVersion = props.getProperty('version') ?: (parts.length > 2 ? parts[2] : '0.0.0')

        return new ResolvedExtension(
            id: extId,
            version: extVersion,
            apiJar: apiJar,
            implJar: implJar,
            metadata: props
        )
    }

    @Override
    String toString() {
        return "maven(${coordinates})"
    }
}

/**
 * Extension source from a local file.
 */
class FileExtensionSource extends ExtensionSource {
    final File file

    FileExtensionSource(Project project, File file) {
        super(project)
        this.file = file
    }

    @Override
    ResolvedExtension resolve() {
        if (!file.exists()) {
            throw new IllegalStateException("Extension file does not exist: ${file}")
        }

        if (file.name.endsWith('.zip')) {
            return extractFromZip(file)
        } else if (file.isDirectory()) {
            return extractFromDirectory(file)
        } else {
            throw new IllegalStateException("Unsupported extension file type: ${file}")
        }
    }

    private ResolvedExtension extractFromZip(File zipFile) {
        def tempDir = project.layout.buildDirectory
            .dir("fat-agent-staging/file/${zipFile.name.replace('.zip', '')}")
            .get()
            .asFile
        tempDir.mkdirs()

        project.copy {
            from project.zipTree(zipFile)
            into tempDir
        }

        return extractFromDirectory(tempDir)
    }

    private ResolvedExtension extractFromDirectory(File dir) {
        def apiJar = dir.listFiles()?.find { it.name.endsWith('-api.jar') }
        def implJar = dir.listFiles()?.find { it.name.endsWith('-impl.jar') || (it.name.endsWith('.jar') && !it.name.endsWith('-api.jar')) }

        def propsFile = new File(dir, 'extension.properties')
        def props = new Properties()
        if (propsFile.exists()) {
            propsFile.withInputStream { props.load(it) }
        }

        def extId = props.getProperty('id') ?: dir.name
        def extVersion = props.getProperty('version') ?: '0.0.0'

        return new ResolvedExtension(
            id: extId,
            version: extVersion,
            apiJar: apiJar,
            implJar: implJar,
            metadata: props
        )
    }

    @Override
    String toString() {
        return "file(${file})"
    }
}

/**
 * Resolved extension descriptor with all artifacts located.
 */
class ResolvedExtension {
    String id
    String version
    File apiJar
    File implJar
    Properties metadata = new Properties()
    Project sourceProject  // non-null if from project source
    List<File> probes = []

    @Override
    String toString() {
        return "ResolvedExtension{id=${id}, version=${version}, api=${apiJar?.name}, impl=${implJar?.name}}"
    }
}
