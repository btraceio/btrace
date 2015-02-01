---
layout: post
title: "BTrace Services (Final Proposal)"
date: 2014-11-13 20:00:00 +0100
comments: true
---

In my previous [blog post](2014/09/extensible-services) I was sketching up a possible solution to **BTrace** extensibility.

A few experiments with AST and bytecode verifiers later I came up with a relatively easy way to provide the extensibility of the core **BTrace** functionality by the means of external plugins. Very importantly, introducing external plugins doesn’t incur any additional overhead on top of the one caused by the extension functionality itself.

# Creating a New Service

Creating a new service is just a matter of extending one of the following two classes

* com.sun.btrace.services.spi.SimpleService
* com.sun.btrace.services.spi.RuntimeService


## SimpleService

A stateless service which can even be a singleton. It has no access to the **BTrace** runtime and can rely on the provided values.

## RuntimeService

For each **BTraceRuntime** a new service instance will be created. The service can use **BTrace** features exposed via **BTraceRuntime** - eg. send messages over the wire channel.

## Using Services

Services become available by adding the jars containing them to the bootclasspath. (eg. via [agent options](https://github.com/jbachorik/btrace/wiki#starting-application-with-btrace))

The recommended way to use services in the script is to create fields of the appropriate service type and annotate them by the **@Injected** annotation.


```java
@BTrace public class MyTrace {
    // a simple service instantiated via the default noarg constructor
    @Injected private static MyService svc;

    // a simple service instantiated via the given static method
    @Injected(factoryMethod = "singleton") private static MyService svc1;

    // a runtime-aware service instantiated via the default single arg (*BTraceRuntime*) constructor
    @Injected(ServiceType.RUNTIME) private static RtService svc2;

    @OnMethod(....)
    public static void interceptMethod(...) {
        svc.process(...);
        svc1.process(...);
        svc2.process(...);
    }
}

```

There is an alternative approach valid only for locally accessible services.

```java
@OnMethod(...)
public void handler(...) {
    // a simple service instantiated via the default noarg constructor
    MyService svc = Service.simple(MyService.class);
    
    // a simple service instantiated via the given static method
    MyService svc1 = Service.simple("singleton", MyService.class)

    // a runtime-aware service instantiated via the default single arg (*BTraceRuntime*) constructor
    RtService svc2 = Service.runtime(RtService.classs);

    svc.process(...);
    svc1.process(...);
    svc2.process(...);
}
```

These two approaches are functionally equivalent - it really depends on the script author which one she should choose.

# Planning

This functionality is planned for the upcoming version 1.3 of **BTrace**.

Integration is tracked by this [pull request](https://github.com/jbachorik/btrace/pull/98)