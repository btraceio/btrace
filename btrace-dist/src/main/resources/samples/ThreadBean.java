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


/*
 * This sample demonstrates simple preprocessor in BTrace.
 * When you run this sample against a Java process, you have
 * to specify -I . option so that the preprocessor can find
 * the "btracedefs.h" file:
 *
 *    btrace -I . <pid> ThreadBean.java
 *
 * Without -I option in command, BTrace skips preprocessor
 * invocation.
 */
#include"btracedefs.h"

        BTRACE_IMPORT

/**
 * This sample demonstrates that you can expose a BTrace
 * class as a JMX MBean. After connecting BTrace to the
 * target application, connect VisualVM or jconsole or 
 * any other JMX client to the same application.
 */
        BTRACE ThreadBean{

        // PROPERTY makes the count field to be exposed
        // as an attribute of this MBean.
        PROPERTY long count;

@OnMethod(
        clazz = "java.lang.Thread",
        method = "start"
)
    ACTION onnewThread(@Self Thread t){
        count++;
        }

@OnTimer(2000)
    ACTION ontimer(){
            println(count);
            }
            }
