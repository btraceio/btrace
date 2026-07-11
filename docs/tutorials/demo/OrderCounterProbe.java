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

import io.btrace.core.annotations.*;
import com.example.orderstats.OrderCounterService;

/**
 * Companion script for docs/tutorials/06-write-your-own-extension.md.
 *
 * <p>Deploy against the shared demo app ({@code docs/tutorials/demo/DemoApp.java}) once the
 * {@code order-counter} extension built in that tutorial is installed and its {@code THREADS}
 * permission is granted:
 *
 * <pre>{@code btrace <PID> OrderCounterProbe.java}</pre>
 *
 * <p>Unlike Tutorial 4's {@code LatencyHistogram.java}, the injected field here is a *required*
 * (non-optional) {@code @Injected} - if the extension's permission were denied, BTrace would
 * refuse to link this script at all rather than hand back a throwing stub. See the tutorial's
 * Step 6 for why the permission must be granted before this is deployed.
 */
@BTrace
public class OrderCounterProbe {

  @Injected
  private static OrderCounterService counter;

  @OnMethod(clazz = "OrderService", method = "processOrder", location = @Location(Kind.RETURN))
  public static void onOrderSucceeded() {
    counter.increment("succeeded");
  }

  @OnMethod(clazz = "OrderService", method = "processOrder", location = @Location(Kind.ERROR))
  public static void onOrderFailed(Throwable t) {
    counter.increment("failed");
  }

  @OnTimer(5000)
  public static void report() {
    println("orders: succeeded=" + str(counter.count("succeeded"))
        + " failed=" + str(counter.count("failed")));
  }
}
