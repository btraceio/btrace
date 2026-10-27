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
package btrace;

import static io.btrace.core.BTraceUtils.exit;
import static io.btrace.core.BTraceUtils.println;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Duration;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;
import io.btrace.otel.OTel;

@BTrace
public class OTelExportTest {
  private static int calls;

  @Injected private static OTel otel;

  @OnMethod(clazz = "resources.Main", method = "callB", location = @Location(Kind.RETURN))
  public static void onCallB(@Duration long dur) {
    otel.span("Main.callB", "probe", "OTelExportTest", dur);
    otel.spanInTrace("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", "Main.callB.linked", dur);
    otel.counter("main.callB.calls", 1);
    calls++;
    if (calls == 3) {
      println("OTEL: recorded");
      exit();
    }
  }
}
