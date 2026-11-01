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
package io.btrace.runtime;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.extension.ExtensionBridge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Deploy-time view of the link rules for {@code @Injected} fields. */
class ExtensionIndyRequiredServiceTest {
  /** A service interface whose implementation is blocked: the bridge hands back the interface. */
  public interface BlockedService {}

  public static class WorkingService {}

  private ExtensionBridge previousBridge;
  private String previousShimMode;

  @BeforeEach
  void installBridge() {
    previousBridge = ExtensionIndy.bridge;
    previousShimMode = System.getProperty("btrace.extension.shimMode");
    System.clearProperty("btrace.extension.shimMode");
    ExtensionIndy.bridge =
        name -> name.endsWith("BlockedService") ? BlockedService.class : WorkingService.class;
  }

  @AfterEach
  void restore() {
    ExtensionIndy.bridge = previousBridge;
    if (previousShimMode == null) {
      System.clearProperty("btrace.extension.shimMode");
    } else {
      System.setProperty("btrace.extension.shimMode", previousShimMode);
    }
  }

  @Test
  void requiredBlockedServiceIsReported() {
    String problem = ExtensionIndy.requiredServiceProblem("x.BlockedService", 0, "UNSPECIFIED");
    assertTrue(problem != null && problem.contains("No implementation available"), problem);
    assertTrue(ExtensionIndy.requiredServiceProblem("x.BlockedService", 0, "THROW") != null);
  }

  @Test
  void optionalOrShimFieldsFallBackAndAreNotReported() {
    assertNull(ExtensionIndy.requiredServiceProblem("x.BlockedService", 1, "UNSPECIFIED"));
    assertNull(ExtensionIndy.requiredServiceProblem("x.BlockedService", 0, "SHIM"));
    System.setProperty("btrace.extension.shimMode", "shim");
    assertNull(ExtensionIndy.requiredServiceProblem("x.BlockedService", 0, "UNSPECIFIED"));
  }

  @Test
  void availableServiceIsNotReported() {
    assertNull(ExtensionIndy.requiredServiceProblem("x.WorkingService", 0, "UNSPECIFIED"));
  }
}
