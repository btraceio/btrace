btrace
======

BTrace - a safe, dynamic tracing tool for the Java platform

## Version
1

## Quick Summary
BTrace is a safe, dynamic tracing tool for the Java platform. 

BTrace can be used to dynamically trace a running Java program (similar to DTrace for OpenSolaris applications and OS). BTrace dynamically instruments the classes of the target application to inject tracing code ("bytecode tracing").

## Building BTrace

### Setup
You will need the following applications installed

* [JDK](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) (preferrably JDK8)
* [Git](http://git-scm.com/downloads) 
* [Ant](http://ant.apache.org/bindownload.cgi)
* (optionally) [Maven3](http://maven.apache.org/download.cgi)

### Build

```
cd <btrace>/make
ant dist
```

The binary dist packages can be found in `<btrace>/dist` as the *.tar.gz and *.zip files


## Using BTrace
### Installation
Explode a binary distribution file (either *.tar.gz or *.zip) to a directory of your choice

You may set the system environment variable __BTRACE_HOME__ to point to the directory containing the exploded distribution.

You may enhance the system environment variable __PATH__ with __$BTRACE_HOME/bin__ for your convenience.

### Running
* `<btrace>/bin/btrace <PID> <trace_script>` will attach to the __java__ application with the given __PID__ and compile and submit the trace script
* `<btrace>/bin/btracec <trace_script>` will compile the provided trace script
* `<btrace>/bin/btracer <compiled_script> <args to launch a java app>` will start the specified java application with the btrace agent running and the script previously compiled by *btracec* loaded

For the detailed user guide, please, check the [Wiki](https://bitbucket.org/jbachorik/btrace2/wiki/Home).
