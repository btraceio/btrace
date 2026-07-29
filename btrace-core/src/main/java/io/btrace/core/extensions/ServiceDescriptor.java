/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.core.extensions;

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
 * enforce that only declared service APIs are injected.
 *
 * <p>You may also declare service-level permissions here. The Gradle plugin folds them into the
 * manifest's {@code BTrace-Extension-Permissions}, which is the set the runtime enforces.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceDescriptor {
  Permission[] permissions() default {};
}
