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


package test;

import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.jfr.JfrEvent;
import static org.openjdk.btrace.core.BTraceUtils.*;
import static org.openjdk.btrace.core.BTraceUtils.Jfr.*;

@BTrace public class JfrEventsProbe {
    @Event(
        name = "CustomEvent",
        label = "Custom Event",
        fields = {
            @Event.Field(type = Event.FieldType.INT, name = "a"),
            @Event.Field(type = Event.FieldType.STRING, name = "b")
        }
    )
    private static JfrEvent.Factory customEventFactory;

    @OnMethod(clazz = "/.*/", method = "/.*/")
    public static void onMethod() {
        JfrEvent event = prepareEvent(customEventFactory);
        setEventField(event, "a", 10);
        setEventField(event, "b", "hello");
        commit(event);
    }

    @PeriodicEvent(name = "PeriodicEvent", fields = @Event.Field(type = Event.FieldType.INT, name = "cnt", kind = @Event.Field.Kind(name = Event.FieldKind.TIMESTAMP)), period = "1 s")
    public static void onPeriod(JfrEvent event) {
        if (shouldCommit(event)) {
            setEventField(event, "cnt", 1);
            commit(event);
        }
    }

}