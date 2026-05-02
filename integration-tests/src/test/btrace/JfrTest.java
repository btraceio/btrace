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


package btrace;

import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.jfr.JfrEvent;
import static org.openjdk.btrace.core.BTraceUtils.*;
import static org.openjdk.btrace.core.BTraceUtils.Jfr.*;

@BTrace
public class JfrTest {
    @Event(name="custom", label="Custom Event", fields=@Event.Field(type = Event.FieldType.STRING, name = "thiz"))
    private static JfrEvent.Factory custom;

    private static int counter = 0;

    @PeriodicEvent(name="periodic", label="Periodic", description="Periodic Event", period="1 s", fields = @Event.Field(type = Event.FieldType.INT, name = "count"))
    public static void periodic(JfrEvent event) {
        if (shouldCommit(event)) {
            setEventField(event, "count", counter++);
            commit(event);
        }
    }

    @OnMethod(clazz = "resources.Main", method = "callA")
    public static void noargs(@Self Object self) {
        println("Main.callA");
        JfrEvent event = prepareEvent(custom);
        setEventField(event, "thiz", str(self));
        commit(event);
    }
}
