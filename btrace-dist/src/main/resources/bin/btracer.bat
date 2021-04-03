@echo off

rem %~dp0 is expanded pathname of the current script under NT
set DEFAULT_BTRACE_HOME=%~dp0..

if "%BTRACE_HOME%"=="" set BTRACE_HOME=%DEFAULT_BTRACE_HOME%
set DEFAULT_BTRACE_HOME=

if not exist "%BTRACE_HOME%\libs \btrace-agent.jar" goto noBTraceHome

set OPTIONS=

if "%1"=="" (
  call:usage
  goto end
)
if "%JAVA_HOME%" == "" goto noJavaHome

if exists "%JAVA_HOME%/jmods/" (
  set JAVA_ARGS="%JAVA_ARGS% -XX:+AllowRedefinitionToAddDeleteMethods"
  set JAVA_ARGS="%JAVA_ARGS% --add-exports jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"
  set JAVA_ARGS="%JAVA_ARGS% --add-exports jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED"
  set JAVA_ARGS="%JAVA_ARGS% --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
  set JAVA_ARGS="%JAVA_ARGS% --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"
  set JAVA_ARGS="%JAVA_ARGS% --add-exports jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED"
)

if "%1" == "--version" (
  %JAVA_HOME%\bin\java "%JAVA_ARGS%" -cp %BTRACE_HOME%/build/btrace-client.jar org.openjdk.btrace.client.Main --version
  goto end
)

set inloop=1
:loop
  IF "%1"=="-v" (
    set OPTIONS="debug=true,%OPTIONS"
    goto next
  )
  IF "%1"=="-u" (
    set OPTIONS="unsafe=true,%OPTIONS"
    goto next
  )
  if "%1"=="-p" (
    set OPTIONS="port=%2,%OPTIONS"
    shift
    goto next
  )
  if "%1"=="-d" (
    set OPTIONS="dumpClasses=true,dumpDir=%2,%OPTIONS"
     shift
    goto next
  )
  if "%1"=="-o" (
    set OPTIONS="scriptOutputFile=%2,%OPTIONS"
    shift
    goto next
  )
  if "%1"=="-pd" (
    set OPTIONS="probeDescPath=%2,%OPTIONS"
    shift
    goto next
  )
  if "%1"=="-bcp" (
    OPTIONS="bootClassPath=%2,%OPTIONS"
    shift
    goto next
  )
  if "%1"=="-scp" (
    set OPTIONS="systemClassPath=%2,%OPTIONS"
    shift
    goto next
  )
  if "%1"=="--noserver" (
    set OPTIONS="noServer=true,%OPTIONS"
    goto next
  )
  if "%1"=="--stdout" (
    set OPTIONS="stdout=true,%OPTIONS"
    goto next
  )
  if "%1"=="-statsd" (
    set OPTIONS="statsd=%2,%OPTIONS"
    goto next
  )
  call :usage
  goto end

  set inloop=0

  :next
  if %inloop==1 (
    shift
    goto loop
  )

%JAVA_HOME%\bin\java -Xshare:off "%JAVA_ARGS%" "-javaagent:%BTRACE_HOME%/libs/btrace-agent.jar=%OPTIONS,script=%~1" %2 %3 %4 %5 %6 %7 %8 %9
goto end

:noJavaHome
  echo Please set JAVA_HOME before running this script
  goto end
:noBTraceHome
  echo Please set BTRACE_HOME before running this script
  goto end

:usage
  echo btracer ^<options^> ^<compiled script^> ^<java args^>
  echo Options:
  echo     -v		          Run in verbose mode
  echo     -u		          Run in unsafe mode
  echo     -p ^<port^>	            BTrace agent server port
  echo     -statsd ^<host[:port]^>  Use this StatsD server
  echo     -o ^<file^>	            The path to a file the btrace agent will store its output
  echo     -d ^<path^>	            Dump modified classes to the provided location
  echo     -pd ^<path^>	            Search for the probe XML descriptors here
  echo     --noserver	          Don't start the socket server
  echo     --stdout	          Redirect the btrace output to stdout instead of writing it to an arbitrary file
  echo     -bcp ^<cp^>	            Append to bootstrap class path
  echo     -scp ^<cp^>	            Append to system class path
  echo     -h		          This message

:end
