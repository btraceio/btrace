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


import org.openjdk.btrace.core.Profiler;
import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Duration;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.OnTimer;
import org.openjdk.btrace.core.annotations.ProbeMethodName;
import org.openjdk.btrace.core.annotations.Property;

/**
 * This script demonstrates new capabilities built into BTrace 1.2
 * <ol>
 * <li>Shortened syntax - when omitting "public" identifier in the class
 * definition one can safely omit all other modifiers when declaring methods
 * and variables</li>
 * <li>Extended syntax for <b>@ProbeMethodName</b> annotation - you can use
 * <b>fqn</b> parameter to request a fully qualified method name instead of
 * the short one</li>
 * <li>Profiling support - you can use {@linkplain Profiler} instance to gather
 * performance data with the smallest overhead possible
 * </ol>
 *
 * @since 1.2
 */
@BTrace
class Profiling {
    @Property
    Profiler swingProfiler = BTraceUtils.Profiling.newProfiler();

    @OnMethod(clazz = "/javax\\.swing\\..*/", method = "/.*/")
    void entry(@ProbeMethodName(fqn = true) String probeMethod) {
        BTraceUtils.Profiling.recordEntry(swingProfiler, probeMethod);
    }

    @OnMethod(clazz = "/javax\\.swing\\..*/", method = "/.*/", location = @Location(value = Kind.RETURN))
    void exit(@ProbeMethodName(fqn = true) String probeMethod, @Duration long duration) {
        BTraceUtils.Profiling.recordExit(swingProfiler, probeMethod, duration);
    }

    @OnTimer(5000)
    void timer() {
        BTraceUtils.Profiling.printSnapshot("Swing performance profile", swingProfiler);
    }
}
