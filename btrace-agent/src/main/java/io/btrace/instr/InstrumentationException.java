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
package io.btrace.instr;

/**
 * Thrown when bytecode instrumentation fails due to invalid variable mapping, corrupted state, or
 * other instrumentation logic errors.
 *
 * <p>This exception indicates a bug in the instrumentation logic and provides detailed debug
 * information to help diagnose the issue. When thrown, instrumentation will fail gracefully,
 * returning the original unmodified class to prevent crashes in the instrumented application.
 *
 * @author BTrace Team
 * @since 2.3
 */
public class InstrumentationException extends RuntimeException {
  public InstrumentationException(String message) {
    super(message);
  }

  public InstrumentationException(String message, Throwable cause) {
    super(message, cause);
  }
}
