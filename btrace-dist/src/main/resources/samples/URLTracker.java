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
import io.btrace.core.annotations.DTraceRef;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.TLS;

import java.net.Proxy;
import java.net.URL;

import static io.btrace.core.BTraceUtils.*;

/*
 * This sample prints every Java URL openURL and
 * openConnection (successful) attempts. In addition,
 * on platforms where DTrace is available, it runs
 * the D-script jurls.d -- which collects a histogram
 * of URL accesses by a btrace:::event probe. From this
 * BTrace program we raise that DTrace probe (dtraceProbe
 * call). Note that it is possible to do similar histogram
 * in BTrace itself (see Histogram.java). But, this sample
 * shows DTrace/BTrace integration as well. On exit, all
 * DTrace aggregates are printed by BTrace (i.e., the ones
 * that are not explicitly printed by DTrace printa call).
 */
@DTraceRef("jurls.d")
@BTrace
public class URLTracker {
    @TLS
    private static URL url;

    @OnMethod(
            clazz = "java.net.URL",
            method = "openConnection"
    )
    public static void openURL(URL self) {
        url = self;
    }

    @OnMethod(
            clazz = "java.net.URL",
            method = "openConnection"
    )
    public static void openURL(URL self, Proxy p) {
        url = self;
    }

    @OnMethod(
            clazz = "java.net.URL",
            method = "openConnection",
            location = @Location(Kind.RETURN)
    )
    public static void openURL() {
        if (url != null) {
            println("open " + url);
            D.probe("java-url-open", Strings.str(url));
            url = null;
        }
    }
}
