[![Download](https://api.bintray.com/packages/btraceio/releases/btrace/images/download.svg) ](https://bintray.com/btraceio/releases/btrace/_latestVersion) [![Build Status](https://travis-ci.org/btraceio/btrace.svg?branch=master)](https://travis-ci.org/btraceio/btrace) [![codecov.io](https://codecov.io/github/btraceio/btrace/coverage.svg?branch=master)](https://codecov.io/github/btraceio/btrace?branch=master) [![Join the chat at https://gitter.im/jbachorik/btrace](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/btraceio/btrace?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Project Stats](https://www.openhub.net/p/btrace/widgets/project_thin_badge.gif)](https://www.openhub.net/p/btrace)

# btrace

A safe, dynamic tracing tool for the Java platform

## Version
1.3.10.1 ([Release Page](https://github.com/jbachorik/btrace/releases/latest))

## Quick Summary
BTrace is a safe, dynamic tracing tool for the Java platform.

BTrace can be used to dynamically trace a running Java program (similar to DTrace for OpenSolaris applications and OS). BTrace dynamically instruments the classes of the target application to inject tracing code ("bytecode tracing").

## Credits
* Based on [ASM](http://asm.ow2.org/)
* Powered by [JCTools](https://github.com/JCTools/JCTools)
* Powered by [hppcrt](https://github.com/vsonnier/hppcrt)
* Optimized with [JProfiler Java Profiler](http://www.ej-technologies.com/products/jprofiler/overview.html)

## Building BTrace

### Setup
You will need the following applications installed

* [JDK](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) (preferrably JDK8)
* [Git](http://git-scm.com/downloads)
* [Gradle](http://gradle.org)
* (optionally) [Ant](http://ant.apache.org/bindownload.cgi)
* (optionally) [Maven3](http://maven.apache.org/download.cgi)

### Build

#### Gradle
```sh
cd <btrace>
./gradlew build
./gradlew buildDistributions
```
The binary dist packages can be found in `<btrace>/build/distributions` as the *.tar.gz, *.zip, *.rpm and *.deb files.

#### Ant (legacy build)
```sh
cd <btrace>/make
ant dist
```
The binary dist packages can be found in `<btrace>/dist` as the *.tar.gz and *.zip files


## Using BTrace
### Installation
Download a distribution file from the [release page](https://github.com/btraceio/btrace/releases/latest). Explode the binary distribution file (either *.tar.gz or *.zip) to a directory of your choice.

You may set the system environment variable __BTRACE_HOME__ to point to the directory containing the exploded distribution.

You may enhance the system environment variable __PATH__ with __$BTRACE_HOME/bin__ for your convenience.

Or, alternatively, you may install one of the *.rpm or *.deb packages

### Running
* `<btrace>/bin/btrace <PID> <trace_script>` will attach to the __java__ application with the given __PID__ and compile and submit the trace script
* `<btrace>/bin/btracec <trace_script>` will compile the provided trace script
* `<btrace>/bin/btracer <compiled_script> <args to launch a java app>` will start the specified java application with the btrace agent running and the script previously compiled by *btracec* loaded

For the detailed user guide, please, check the [Wiki](https://github.com/btraceio/btrace/wiki/Home).

### Maven Integration
The [maven plugin](https://github.com/btraceio/btrace-maven) is providing easy compilation of __BTrace__ scripts as a part of the build process. As a bonus you can utilize the _BTrace Project Archetype_ to bootstrap developing __BTrace__ scripts.

## Mailing lists

These mailing lists are hosted at **http://librelist.com**

* **btrace.users@librelist.com**
* **btrace.dev@librelist.com**
* **btrace.commits@librelist.com**

## Contributing - !!! Important !!!

Pull requests can be accepted only from the signers of [Oracle Contributor Agreement](http://www.oracle.com/technetwork/community/oca-486395.html)

### Deb Repository

Using the command line, add the following to your /etc/apt/sources.list system config file:

```
echo "deb http://dl.bintray.com/btraceio/deb trusty universe" | sudo tee -a /etc/apt/sources.list
```

Or, add the repository URLs using the "Software Sources" admin UI:

```
deb http://dl.bintray.com/btraceio/deb trusty universe
```

### RPM Repository

Grab the _*.repo_ file `wget https://bintray.com/btraceio/rpm/rpm -O bintray-btraceio-rpm.repo` and use it.
