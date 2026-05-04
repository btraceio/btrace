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
import io.btrace.core.annotations.ProbeClassName;
import io.btrace.core.annotations.ProbeMethodName;
import io.btrace.core.annotations.Self;

import static io.btrace.BTrace.*;

/**
 * This script traces method entry into every method of
 * every class in javax.swing package! Think before using
 * this script -- this will slow down your app significantly!!
 */
@BTrace
public class AllMethods1 {
    @OnMethod(
            clazz = "/javax\\.swing\\..*/",
            method = "/.*/"
    )
    public static void m(@Self Object o, @ProbeClassName String probeClass, @ProbeMethodName String probeMethod) {
        println("this = " + o);
        print("entered " + probeClass);
        println("." + probeMethod);
    }
}
