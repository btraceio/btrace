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


import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Level;
import org.openjdk.btrace.core.annotations.OnEvent;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.ProbeMethodName;

import static org.openjdk.btrace.core.BTraceUtils.println;

/**
 * This script traces method entry into every method of
 * every class in javax.swing package! Think before using
 * this script -- this will slow down your app significantly!!
 */
@BTrace
public class AllMethodsLevels {
    /**
     * Capturing only methods invoked from javax.swing.JComponent class.
     */
    @OnMethod(
            clazz = "javax.swing.JComponent",
            method = "/.*/",
            enableAt = @Level("=0")
    )
    public static void l0(@ProbeMethodName(fqn = true) String probeMethod) {
        println("# " + probeMethod);
    }

    /**
     * This will intercept all the methods from javax.swing.* classes.
     */
    @OnMethod(
            clazz = "/javax\\.swing\\.*/",
            method = "/.*/",
            enableAt = @Level(">=1")
    )
    public static void l1(@ProbeMethodName(fqn = true) String probeMethod) {
        println("## " + probeMethod);
    }

    /**
     * Switch to level 0.
     */
    @OnEvent("l0")
    public static void setL0() {
        BTraceUtils.setInstrumentationLevel(0);
    }

    /**
     * Swtitch to level 1.
     */
    @OnEvent("l1")
    public static void setL1() {
        BTraceUtils.setInstrumentationLevel(1);
    }
}
