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


package traces;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.OnProbe;
import org.openjdk.btrace.core.annotations.Self;

import static org.openjdk.btrace.core.BTraceUtils.*;

/**
 * @author Jaroslav Bachorik
 */
@BTrace
public class OnProbeTest {
    @OnProbe(name = "noargs", namespace = "org.openjdk.btrace")
    public static void noargs(@Self Object self) {
        println("[this, noargs]");
    }

    @OnProbe(name = "withargs", namespace = "org.openjdk.btrace")
    public static void args(@Self Object self, int i, String s) {
        dump("[this, args]");
    }

    private static void dump(String s) {
        println(s);
    }
}
