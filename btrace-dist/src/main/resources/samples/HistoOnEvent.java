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


import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.OnEvent;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Self;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.openjdk.btrace.core.BTraceUtils.*;

/**
 * This sample collects histogram of javax.swing.JComponets
 * created by traced app. But, the histogram is printed only
 * on event (from client).
 */
@BTrace
public class HistoOnEvent {
    private static Map<String, AtomicInteger> histo = newHashMap();

    @OnMethod(
            clazz = "javax.swing.JComponent",
            method = "<init>"
    )
    public static void onnewObject(@Self Object obj) {
        String cn = name(classOf(obj));
        AtomicInteger ai = get(histo, cn);
        if (ai == null) {
            ai = newAtomicInteger(1);
            put(histo, cn, ai);
        } else {
            incrementAndGet(ai);
        }
    }

    @OnEvent
    public static void print() {
        if (size(histo) != 0) {
            printNumberMap("Component Histogram", histo);
        }
    }
}
