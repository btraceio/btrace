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
package btrace;

import static io.btrace.core.BTraceUtils.*;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;

/** CATCH and THROW handlers taking the exception in a bare {@code Throwable} parameter. */
@BTrace
public class BareThrowableCatchThrowTest {
  @OnMethod(clazz = "resources.FailingTarget", method = "validate", location = @Location(Kind.THROW))
  public static void onThrow(Throwable t) {
    println(strcat("thrown ", str(t)));
  }

  @OnMethod(clazz = "resources.FailingTarget", method = "startWork", location = @Location(Kind.CATCH))
  public static void onCatch(Throwable t) {
    println(strcat("caught ", str(t)));
  }
}
