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


import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.OnTimer;
import io.btrace.core.annotations.Self;

import java.awt.*;

import static io.btrace.BTrace.println;

/**
 * A BTrace program that can be run against a GUI
 * program. This program prints (monotonic) count of
 * number of java.awt.Components created once every
 * 2 seconds (2000 milliseconds).
 */

@BTrace
public class NewComponent {
    // component count
    private static volatile long count;

    @OnMethod(
            clazz = "java.awt.Component",
            method = "<init>"
    )
    public static void onnew(@Self Component c) {
        // increment counter on constructor entry
        count++;
    }

    @OnTimer(2000)
    public static void print() {
        // print the counter
        println("component count = " + count);
    }
}
