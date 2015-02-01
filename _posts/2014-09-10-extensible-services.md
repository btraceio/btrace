---
layout: post
title: "Extensible Services for BTrace"
date: 2014-09-10 20:00:00 +0100
comments: false
---

# History

The ability to extend **BTrace** with plugins to overcome the limits imposed by its safety guarantees while still not allowing to inject any arbitrary code was requested a long time ago and is tracked as [BTRACE-64](https://github.com/jbachorik/btrace/issues/29)

# Solution, round 1

The first attempt to address this problem brought many problems that were supposed to be solved by a more-or-less complete engine rewrite in **BTrace 2.0**. In order to make the system really extensible the modularity was baked-in to all its parts - it was possible to even extend the internal communication protocol with custom messages. 

All of this came at a price, though. It was paid by extra nano- and micro-seconds spent in the BTrace runtime jumping through all those indirection levels.

Despite the availability of the [**BTrace 2.0 Preview** builds](https://kenai.com/projects/btrace/forums/forum/topics/523919-BTrace-2) for quite a long time there was almost no reaction from the community. This would indicate that the *“rewrite from scratch”* approach is a deal-breaker for the majority of the users.

# Rewind, Round 2

## Sketchup

Instead of rewriting the engine from scratch it seems to be possible to go the way of gradual changes and improvements.

The main goal is to allow the users to write application specific extensions for **BTrace** which can be easily used just by having them on the classpath.

Eg. the following invocation will allow to use any **BTrace** extensions available in the *path_to_my_extension.jar* archive. A class is considered to be an extension if it extends a **BTrace** extension base class (eg. *com.sun.btrace.services.spi.SimpleService*)

## How to Use

The following command would instruct **BTrace** to pickup the extensions jar.

`btrace -cp ...:<path_to_my_extension.jar> <pid> <tracing script>`

In order to use an extension one would do 


``` java
@OnMethod(....)
public static void handler(...) {  
  // the following statement will inject the runtime  
  // aware service "Printer"  
  @Injected Printer p = Service.runtime(Printer.class);    
  p.println("hello");
}

```


As one can see it is not that different to what one was used to do before. 

## Why All This Fuzz

### DSL(?) Mess Cleanup

Let’s face it. **BTraceUtils** is a dump right now. It contains any and all functions one might think as being useful when writing BTrace scripts. As the result it is rather difficult to locate the method one actually wants to use.

The extensions will allow for off-loading specific features of **BTraceUtils** leaving there only methods comprising the actual **DSL** (well, I might even rename the class to something more meaningful)

### Integration with External Systems

Right now **BTrace** is pretty limited in how it can export the collected data - it can either write it to file or stdout. While this is sufficient for one-off scripts it is not the first choice when one wants to include **BTrace** in more complex systems (eg. acting as a data provider for [Riemann](http://riemann.io/)).

### Performance

This implementation, in addition to not causing any performance regression, can even provide slight improvements for the runtime aware services like **Printer**. Each invocation of *print*/*println* will use the cached runtime as opposed to looking it up when using **BTraceUtils** (not using services). 