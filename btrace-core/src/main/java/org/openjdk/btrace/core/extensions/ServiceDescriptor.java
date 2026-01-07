package org.openjdk.btrace.core.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as an injectable BTrace service API.
 *
 * <p>Annotate public top-level interfaces in an extension's API module that are intended to be
 * injected into BTrace scripts via {@code @Injected}.
 *
 * <p>The BTrace Gradle extension plugin scans for this annotation in the API output and generates
 * the appropriate manifest entries (BTrace-Extension-Services). The compiler/verifier framework can
 * rely on the agent-provided service declaration registry (populated from these manifests) to
 * enforce that only declared service APIs are injected.</p>
 *
 * <p>You may also declare service-level permissions here. These combine with extension-level
 * permissions from {@link ExtensionDescriptor#permissions()} to form the effective permission set.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceDescriptor {
  Permission[] permissions() default {};
}
