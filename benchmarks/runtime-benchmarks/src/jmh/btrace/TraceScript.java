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


import static org.openjdk.btrace.core.BTraceUtils.*;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Duration;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Level;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.ProbeClassName;
import org.openjdk.btrace.core.annotations.ProbeMethodName;
import org.openjdk.btrace.core.annotations.Sampled;

@BTrace
public class TraceScript {
  @OnMethod(clazz = "org.openjdk.btrace.BTraceBench", method = "testInstrumentedMethod")
  public static void onMethodEntryEmpty(@ProbeClassName String pcn, @ProbeMethodName String pmn) {}

  @OnMethod(
      clazz = "org.openjdk.btrace.BTraceBench",
      method = "testInstrumentedMethodLevelNoMatch",
      enableAt = @Level("100"))
  public static void onMethodEntryEmptyLevelNoMatch(
      @ProbeClassName String pcn, @ProbeMethodName String pmn) {}

  @OnMethod(clazz = "org.openjdk.btrace.BTraceBench", method = "testInstrumentedMethodSampled")
  @Sampled(kind = Sampled.Sampler.Const)
  public static void onMethodEntryEmptySampled(
      @ProbeClassName String pcn, @ProbeMethodName String pmn) {}

  @OnMethod(
      clazz = "org.openjdk.btrace.BTraceBench",
      method = "testInstrDuration",
      location = @Location(Kind.RETURN))
  public static void onMethodRetDuration(
      @ProbeClassName String pcn, @ProbeMethodName String pmn, @Duration long dur) {}

  @OnMethod(
      clazz = "org.openjdk.btrace.BTraceBench",
      method = "testInstrDurationSampled",
      location = @Location(Kind.RETURN))
  @Sampled(kind = Sampled.Sampler.Const)
  public static void onMethodRetDurationSampled(
      @ProbeClassName String pcn, @ProbeMethodName String pmn, @Duration long dur) {}

  @OnMethod(
      clazz = "org.openjdk.btrace.BTraceBench",
      method = "testInstrDurationSampledAdaptive",
      location = @Location(Kind.RETURN))
  @Sampled
  public static void onMethodRetDurationSampledAdaptive(
      @ProbeClassName String pcn, @ProbeMethodName String pmn, @Duration long dur) {}

  @OnMethod(clazz = "org.openjdk.btrace.BTraceBench", method = "testInstrumentedMethodPrintln1")
  public static void onMethodEntryPrintln1(
      @ProbeClassName String pcn, @ProbeMethodName String pmn) {
    println(pcn);
  }

  @OnMethod(
      clazz = "org.openjdk.btrace.BTraceBench",
      method = "testInstrumentedMethodPrintln1Sampled")
  @Sampled
  public static void onMethodEntryPrintln1Sampled(
      @ProbeClassName String pcn, @ProbeMethodName String pmn) {
    println(pcn);
  }

  @OnMethod(clazz = "org.openjdk.btrace.BTraceBench", method = "testInstrumentedMethodPrintln2")
  public static void onMethodEntryPrintln2(
      @ProbeClassName String pcn, @ProbeMethodName String pmn) {
    println(pcn);
    println(pmn);
  }

  @OnMethod(clazz = "org.openjdk.btrace.BTraceBench", method = "testInstrumentedMethodPrintln3")
  public static void onMethodEntryPrintln3(
      @ProbeClassName String pcn, @ProbeMethodName String pmn) {
    println(pcn);
    println(pmn);
    println(pmn);
  }

  @OnMethod(clazz = "org.openjdk.btrace.BTraceBench", method = "testInstrumentedMethodPrintln24")
  public static void onMethodEntryPrintln24(
      @ProbeClassName String pcn, @ProbeMethodName String pmn) {
    println(pcn);
    println(pmn);
    println(pmn);
    println(pcn);
    println(pmn);
    println(pmn);
    println(pcn);
    println(pmn);
    println(pmn);
    println(pcn);
    println(pmn);
    println(pmn);
    println(pcn);
    println(pmn);
    println(pmn);
    println(pcn);
    println(pmn);
    println(pmn);
    println(pcn);
    println(pmn);
    println(pmn);
    println(pcn);
    println(pmn);
    println(pmn);
  }
}
