package io.btrace.gradle

import groovy.transform.CompileStatic
import java.lang.reflect.Array
import java.util.List
import java.util.Locale

/** ASM descriptors used by every BTrace extension annotation discovery path. */
@CompileStatic
final class BTraceDescriptors {
    static final String SERVICE_DESCRIPTOR =
        'Lio/btrace/core/extensions/ServiceDescriptor;'
    static final String EXTENSION_DESCRIPTOR =
        'Lio/btrace/core/extensions/ExtensionDescriptor;'
    static final String EXTERNAL_TYPE =
        'Lio/btrace/core/extensions/ExternalType;'

    private BTraceDescriptors() {}

    static boolean isServiceDescriptor(String descriptor) {
        return SERVICE_DESCRIPTOR == descriptor
    }

    static boolean isExtensionDescriptor(String descriptor) {
        return EXTENSION_DESCRIPTOR == descriptor
    }

    static String enumConstantName(Object value) {
        Object constant = null
        if (value instanceof List && ((List) value).size() >= 2) {
            constant = ((List) value).get(1)
        } else if (value != null && value.getClass().isArray() && Array.getLength(value) >= 2) {
            constant = Array.get(value, 1)
        }
        return constant != null ? String.valueOf(constant).toUpperCase(Locale.ROOT) : null
    }
}
