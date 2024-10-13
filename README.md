[![Dev build](https://github.com/btraceio/btrace/workflows/BTrace%20CI%2FCD/badge.svg?branch=develop)](https://github.com/btraceio/btrace/actions?query=workflow%3A%22BTrace+CI%2FCD%22+branch%3Adevelop) [![Download](https://img.shields.io/github/v/release/btraceio/btrace?sort=semver)](https://github.com/btraceio/btrace/releases/latest) [![codecov.io](https://codecov.io/github/btraceio/btrace/coverage.svg?branch=develop)](https://codecov.io/github/btraceio/btrace?branch=develop) [![huhu](https://img.shields.io/badge/Slack-join%20chat-brightgreen")](http://btrace.slack.com/) [![Join the chat at https://gitter.im/jbachorik/btrace](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/btraceio/btrace?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Project Stats](https://www.openhub.net/p/btrace/widgets/project_thin_badge.gif)](https://www.openhub.net/p/btrace)

# btrace

A safe, dynamic tracing tool for the Java platform

## Version
~missing~

## Quick Summary
BTrace is a safe, dynamic tracing tool for the Java platform.

BTrace can be used to dynamically trace a running Java program (similar to DTrace for OpenSolaris applications and OS). BTrace dynamically instruments the classes of the target application to inject tracing code ("bytecode tracing").

## Credits
* Based on [ASM](http://asm.ow2.org/)
* Powered by [JCTools](https://github.com/JCTools/JCTools)
* Powered by [hppcrt](https://github.com/vsonnier/hppcrt)
* Optimized with [JProfiler Java Profiler](http://www.ej-technologies.com/products/jprofiler/overview.html)
* Build env helper using [SDKMAN!](https://sdkman.io/)

## Building BTrace

### Setup
You will need the following applications installed

* [Git](http://git-scm.com/downloads)
* (optionally, the default launcher is the bundled `gradlew` wrapper) [Gradle](http://gradle.org)


### Build

#### Gradle
```sh
cd <btrace>
./gradlew :btrace-dist:build
```
The binary dist packages can be found in `<btrace>/btrace-dist/build/distributions` as the *.tar.gz, *.zip, *.rpm and *.deb files.
The exploded binary folder which can be used right away is located at `<btrace>/btrace-dist/build/resources/main` which serves as the __BTRACE_HOME__ location.

##### Golden Files
Some of the instrumentor related tests are using golden files. Therefore, it is necessary to update those files
when the injected code is changed. This can be done with the help of passing in `updateTestData` Gradle property.
Eg. running the tests like `./gradlew test -PupdateTestData` will regenerate all golden files which then must be
checked in to the Git repository.


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

## Contributing - !!! Important !!!

Pull requests can be accepted only from the signers of [Oracle Contributor Agreement](https://oca.opensource.oracle.com/)

### Deb Repository

Using the command line, add the following to your /etc/apt/sources.list system config file:

```
echo "deb http://dl.bintray.com/btraceio/deb xenial universe" | sudo tee -a /etc/apt/sources.list
```

Or, add the repository URLs using the "Software Sources" admin UI:

```
deb http://dl.bintray.com/btraceio/deb xenial universe
```

### RPM Repository

Grab the _*.repo_ file `wget https://bintray.com/btraceio/rpm/rpm -O bintray-btraceio-rpm.repo` and use it.
