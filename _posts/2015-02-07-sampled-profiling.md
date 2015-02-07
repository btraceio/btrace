---
layout: post
title: "Sampled Performance Profiling"
date: 2015-02-07 20:00:00 +0100
comments: true
---

Capturing and timing all the method invocations is a great way of obtaining a relatively precise application performance profile. Unless you need to do that for short and frequently called  methods, that is.

# Profile Collection Overhead

It is obvious that capturing the performance profile would incur some additional overhead - getting and processing the timestamps, correlating the events, building up the profile, they all come at a price which must be paid in order to get some meaningful information.

## Short And Frequent Methods

This overhead is usually projected to a time constant. With clever data structures and algorithms it can become a really small constant. It may sound good at first but what it really means is that the effective overhead for shorter methods is higher - eg. a short method usually running for 30ns would suddenly run for 100ns with profiling turned on (and this would be a pretty fast profiling runtime, too) meaning that the execution speed has just increased by more than 200%.

Adding insult to injury such short methods have the bad habit of being run really frequently and they performance profile is not that interesting at all - they are already as fast as they can be.

# Sampling

To summarize - short, frequently repeating methods are causing the biggest relative overhead while contributing almost no additional insight into the application performance profile. In fact, we might get the same information by capturing just each _n_-th invocation. Grabbing just a a sample of all the method invocations.

## Adaptive Sampling

The trickier part of setting up the sampilng is coming up with the appropriate sampling rate. Too high and the overhead reduction might not even be noticeable, too low and you might miss valuable information.

Adaptive sampling tries to address this problem with accepting an overhead target and then dynamically adjusting the sampling rate to meet the target. This approach guarantees that the maximum of information will be collected without exceeding te overhead target. As a bonus the system will react to changing method invocation times due to changing execution environment.

## Sampling In BTrace

In the upcoming version of BTrace (1.3) it is possible to define sampling for __Kind.CALL__ and __Kind.ENTRY__ locations.

_Fig.1 Adaptive Sampling_

``` java
@BTrace public class AllCalls1Sampled {
  @OnMethod(clazz="javax.swing.JTextField", method="/.*/",
            location=@Location(value=Kind.CALL, clazz="/.*/", method="/.*/"))
  @Sampled(kind = Sampled.Sampler.Adaptive)
  public static void m(@Self Object self, @TargetMethodOrField String method, @ProbeMethodName   String probeMethod) { // all calls to the methods with signature "()"
    println(method + " in " + probeMethod);
  }
}
```

_Fig.2 Standard Sampling

``` java
@BTrace public class AllMethodsSampled {
  @OnMethod(
      clazz="/javax\\.swing\\..*/",
      method="/.*/"
  )
  // standard sampling; capture each 20-th invocation
  @Sampled(kind = Sampled.Sampler.Const, mean = 20)
  public static void m(@Self Object o, @ProbeClassName String probeClass, @ProbeMethodName String probeMethod) {
      println("this = " + o);
      print("entered " + probeClass);
      println("." + probeMethod);
  }
}
```

# Wrapup

Sampling seems to be a good way to achieve a balance between the amount of information a profiler is able to collect and the overhead it incurs to the profiled application. The drawback is the necessity to specify the sampling rate but it can very well be mitigated by using the adaptive variety.
