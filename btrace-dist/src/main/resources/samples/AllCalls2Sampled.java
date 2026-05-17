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
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.ProbeClassName;
import io.btrace.core.annotations.ProbeMethodName;
import io.btrace.core.annotations.Sampled;
import io.btrace.core.annotations.Self;
import io.btrace.core.annotations.TargetInstance;
import io.btrace.core.annotations.TargetMethodOrField;

import static io.btrace.BTrace.println;

/**
 * This script demonstrates the possibility to intercept
 * method calls that are about to be executed from the body of
 * a certain method. This is achieved by using the {@linkplain Kind#CALL}
 * location value.
 * <p>
 * Not all instances of the method call are intercepted, however.  Sampling
 * is used to pick only statistically representative ones.
 */
@BTrace
public class AllCalls2Sampled {
    @OnMethod(clazz = "/javax\\.swing\\..*/", method = "/.*/",
            location = @Location(value = Kind.CALL, clazz = "/.*/", method = "/.*/"))
    @Sampled
    public static void n(@Self Object self, @ProbeClassName String pcm, @ProbeMethodName String pmn,
                         @TargetInstance Object instance, @TargetMethodOrField String method, String text) { // all calls to the methods with signature "(String)"
        println("Context: " + pcm + "#" + pmn + method + " " + text);
    }
}
