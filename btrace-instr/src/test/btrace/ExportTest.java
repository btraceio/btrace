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

import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Export;
import org.openjdk.btrace.core.annotations.OnMethod;

import java.util.Deque;

/**
 * Sanity test to make sure the @Export annotations work as expected.
 *
 * @author Jaroslav Bachorik
 */
@BTrace
public class ExportTest {
    @Export
    public static final long z = 10L;
    @Export
    public static Deque<Long> entryTimes = BTraceUtils.Collections.newDeque();
    @Export
    public static String name;
    @Export
    public static int x = 10;
    @Export
    public static double y;

    @OnMethod(clazz = "resources.OnMethodTest", method = "args")
    public static void testArgs(String a, long b, String[] c, int[] d) {
        BTraceUtils.push(entryTimes, b);
        name = a;
    }
}
