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
package org.openjdk.btrace.core.extensions;

/**
 * Exception thrown when extension operations fail.
 *
 * <p>This exception is used for extension-related errors including:
 *
 * <ul>
 *   <li>Missing or invalid extension annotations
 *   <li>Permission violations
 *   <li>Initialization failures
 *   <li>Missing dependencies
 * </ul>
 */
public class ExtensionException extends RuntimeException {
  /**
   * Constructs an exception with the specified message.
   *
   * @param message the error message
   */
  public ExtensionException(String message) {
    super(message);
  }

  /**
   * Constructs an exception with the specified message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public ExtensionException(String message, Throwable cause) {
    super(message, cause);
  }
}
