package io.btrace.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project

/** Resolves the version of the public BTrace distribution used by this plugin. */
final class BTraceVersion {
    private BTraceVersion() {}

    static String resolve(Project project, String explicitVersion, Class<?> pluginClass, String propertyName) {
        String version = explicitVersion?.trim()
        if (version == null || version.isEmpty()) {
            version = pluginClass?.package?.implementationVersion?.trim()
        }
        if (version == null || version.isEmpty()) {
            throw new GradleException(
                "BTrace version is unavailable. Set ${propertyName} to a concrete BTrace release version " +
                "(for example, btraceVersion = '3.0.0').")
        }
        validate(version, propertyName)
        return version
    }

    static void validate(String version, String propertyName) {
        if (version == null || version.trim().isEmpty() ||
                version ==~ /.*[+\[\]\(\),].*/ ||
                version.equalsIgnoreCase('latest.release') ||
                version.equalsIgnoreCase('latest.integration') ||
                version.equalsIgnoreCase('unspecified')) {
            throw new GradleException(
                "${propertyName} must be a concrete BTrace release version; dynamic versions and ranges are not supported.")
        }
    }
}
