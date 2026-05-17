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
package io.btrace.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@linkplain OnMethod} handler as a sampled one. When a handler is sampled not all events
 * will be traced - only a statistical sample with the given mean.
 *
 * <p>By default an adaptive sampling is used. BTrace will increase or decrease the number of
 * invocations between samples to keep the mean time window, thus decreasing the overall overhead.
 *
 * @author Jaroslav Bachorik
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Sampled {
  int MEAN_DEFAULT = 10;

  /**
   * The sampler kind
   *
   * @return The sampler kind
   */
  Sampler kind() default Sampler.Const;

  /**
   * The sampler mean.
   *
   * <p>For {@code Sampler.Const} it is the average number of events between samples.<br>
   * For {@code Sampler.Adaptive} it is the average time (in ns) between samples<br>
   *
   * @return The sampler mean
   */
  int mean() default MEAN_DEFAULT;

  /**
   * Specifies the sampler kind
   *
   * <ul>
   *   <li>{@code None} - no sampling
   *   <li>{@code Const} - keeps the average number of events between samples
   *   <li>{@code Adaptive} - increases or decreases the average number of events between samples to
   *       lower overhead
   * </ul>
   */
  enum Sampler {
    /** No Sampling */
    None,
    /** Keeps the average number of events between samples */
    Const,
    /** Increases or decreases the average number of events between samples to lower overhead */
    Adaptive
  }
}
