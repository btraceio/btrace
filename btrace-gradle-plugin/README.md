BTrace Gradle Extension Plugin

Overview
- Gradle plugin to build and package BTrace extensions with sane defaults.
- Scans implementation bytecode to infer minimal required permissions and writes them into the API JAR manifest.
- Produces three artifacts: API JAR (`classifier=api`), Impl JAR (`classifier=impl`, shadowed), and a distributable ZIP (`classifier=extension`).

Apply The Plugin
```
plugins {
  id("org.openjdk.btrace.extension") version "${btraceVersion}"
}

repositories {
  mavenCentral()
  mavenLocal() // if you published locally for testing
}
```

Project Layout
- `src/api/java`, `src/api/resources`: public API package visible to BTrace scripts and the agent (ends up on bootstrap).
- `src/impl/java`, `src/impl/resources`: implementation package shadowed and isolated behind the API manifest.

DSL (extension `btraceExtension`)
```
btraceExtension {
  id = "com.example.myext"         // required: globally unique extension ID
  name = "My Extension"            // optional
  description = "Does things"      // optional

  // Optional: omit to auto-detect from @ServiceDescriptor annotations in API classes
  services = [
    "com.example.myext.api.MyService"
  ]
  requiresExtensions = [
    // other extension IDs if you depend on them
  ]

  shadedPackages = [
    // from : to (relocations applied to impl JAR)
    "com.example.dep" : "com.example.myext.shaded.dep"
  ]

  // Permissions
  scanPermissions = true           // default: scan impl JAR + classpath
  requiredPermissions = [          // optional overrides/additions
    // "NETWORK", "FILE_READ", "FILE_WRITE", "THREADS", "EXEC", "NATIVE",
    // "REFLECTION", "CLASSLOADER", "SYSTEM_PROPS", "THREAD_INFO", "MEMORY_INFO", "JFR_EVENTS"
  ]
}
```

Outputs
- API JAR: `build/libs/<name>-<version>-api.jar` with manifest entries:
  - `BTrace-Extension-Id`, `BTrace-Extension-Services`, `BTrace-Extension-Permissions`, `BTrace-Extension-Requires`, `BTrace-Shaded-Packages`, `BTrace-Extension-Impl`.
- Impl JAR: `build/libs/<name>-<version>-impl.jar` (shadowed, minimized) containing impl classes and shaded deps.
- Distribution ZIP: `build/distributions/<name>-<version>-extension.zip` bundling both JARs.

Tasks
- `buildApiJar`: builds the API JAR and writes extension metadata into the manifest.
- `shadowJar`: builds the impl JAR from `impl` source set, applying relocations and minimization.
- `packageExtension`: bundles API + Impl into a ZIP.

Tips
- Keep API small and stable; only types used by BTrace scripts or injected services belong in API.
- Put all runtime dependencies into `impl` and shade them to avoid leaking into the target JVM.
- If scanning is too conservative, add `requiredPermissions` explicitly.

Package-level metadata (optional)
- You may add `@ExtensionDescriptor` to `package-info.java` in your API package to document name/version/description.
- The plugin relies on manifest attributes as the canonical metadata; `@ExtensionDescriptor` is for documentation only.

Local Publish For Testing
```
./gradlew :btrace-gradle-plugin:publishToMavenLocal
```
Then in your extension project, add `mavenLocal()` to repositories or pluginManagement and use the same plugin version.
  // Lints
  apiCtorSeverity = 'error'        // 'off' | 'warn' | 'error' (default: 'error'). Warn/fail on public API classes with public constructors.
