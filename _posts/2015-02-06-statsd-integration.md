---
layout: post
title: "StatsD Integration"
date: 2015-02-06 20:00:00 +0100
comments: true
---
# StatsD

[StatsD](https://github.com/etsy/statsd) is becoming certainly an ubiquituous standard for delivering monitoring metrics. It defines a very simple, yet extremly extensible textual format for transfering metrics of various kinds. The data is usually transferred in the form of UDP packets (low latency) but other transports might be used as well.

## Metrics  

Metric is defined by its name and type. There are no restrictions on the names.

__StatsD__ defines basic metrics types

* counters - can be incremented or decremented by certain delta
* gauges - reflect the last set value; no increments/decrements
* timers - used for timing data

More details [here](https://github.com/etsy/statsd).

## Servers

There are more than a few server side implementations, varying in the throughput or the amount of extensions they are adding. In general the server side takes care of collecting  the metrics from one or more clients (producers) and aggregating the data to provide eg. averages, variations or smoothing out samples. All of this is happening asynchronously in relation to the metrics producers.

## Clients

There might be even more __StatsD__ client implementations than there is the server ones. In its simplest form a __StatsD__ client will provide convenience methods to manipulate the metrics and take care of preparing the UDP packets to be sent to collectors.

# BTrace Integration

The only way to collect any meaningful data in BTrace is to print it either to _stdout_ or a file. While this is straight-forward and does not require any additional infrastructure it brings a lot of pain when trying to integrate BTrace with monitoring tools.

## BTrace StatsD Client

Good news! Thanks to the nature of the __StatsD__ metrics transfer protocol adding the __StatsD__ client to BTrace is fairly simple and does not require adding any 3rd party libraries. It is just a matter of adding one extension class.

``` java
com.sun.btrace.services.impl.Statsd
```

This client also supports the extensions from [DogStatsD](http://docs.datadoghq.com/guides/dogstatsd/) while keeping the compatibility with the original [StatsD](https://github.com/etsy/statsd)

### Using StatsD Client

``` java
@BTrace class StatsdExample {
  // declare the variable to be injected by Statsd service
  @Injected(factoryMethod = "getInstance") private static Statsd sd;

  @OnMethod(...)
  public static void m(...) {
    sd.increment("my.metric.a"); // simple metric value
    sd.increment("my.metric.b", "regular,distribution:gaussian"); // tagged metric value
  }
}
```

### Configuring StatsD Collector

By default the BTrace StatsD client will forward the metrics UDP packets to __localhost__ and port __8125__ (the defaults for many of the __StatsD__ servers).

In order to configure a different collector one can use the new BTrace launcher argument __-statsd__

``` sh
btrace -statsd host:port ...
```

The BTrace agent also accepts __statsd__ argument directly

``` sh
java -javaagent:btrace-agent.jar=statsd=host:port,...
```

These settings are not dynamic, however. Once set and used they will remain the same. The subsequent attaches using the __btrace__ launcher are not able to modify the settings. This might be addressed in the future if it, in fact, turns out to be a problem.

# Conclusion

Including __StatsD__ in the BTrace tool box will enable very easy integration with various monitoring suites like [graphite](http://graphite.wikidot.com/) or [Datadog](https://www.datadoghq.com/). 
