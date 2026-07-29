/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package tests.harness;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides how long {@link StallWatchdog} waits before deciding a test has stopped making
 * progress.
 *
 * <p>An annotation rather than a system property because all integration test classes share one
 * worker JVM: a property set by one test would leak into every class that follows it. Its main use
 * is the watchdog's own tests, which must fire in seconds rather than minutes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Inherited
public @interface StallTimeout {
  /** How long a test may run before the first dump is taken. */
  long millis();

  /** How long after the first dump the second one is taken. */
  long gapMillis() default 30_000L;
}
