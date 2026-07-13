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
package io.btrace.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Fails explicitly because Maven fat-agent packaging is not supported by the 3.0 artifact layout.
 *
 * <p>The pre-3.0 implementation resolved artifacts that are no longer published and staged
 * implementation classes outside the namespace consumed by the runtime. Keeping that behavior would
 * produce an agent that builds successfully but cannot load its embedded services.
 */
@Mojo(name = "fat-agent", defaultPhase = LifecyclePhase.PACKAGE)
public class FatAgentMojo extends AbstractMojo {
  static final String UNSUPPORTED_MESSAGE =
      "BTrace Maven fat-agent packaging is not supported in 3.0.0: the goal cannot consume the "
          + "published 3.0 extension artifact safely. Use the io.btrace.fat-agent Gradle plugin "
          + "or build the standard io.btrace:btrace distribution instead.";

  /** Skip execution. */
  @Parameter(property = "btrace.skip", defaultValue = "false")
  private boolean skip;

  @Override
  public void execute() throws MojoFailureException {
    if (skip) {
      getLog().info("Skipping unsupported BTrace Maven fat-agent goal");
      return;
    }
    throw new MojoFailureException(UNSUPPORTED_MESSAGE);
  }
}
