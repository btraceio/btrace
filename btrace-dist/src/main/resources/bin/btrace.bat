@echo off

rem %~dp0 is expanded pathname of the current script under NT
set DEFAULT_BTRACE_HOME=%~dp0..

if "%BTRACE_HOME%"=="" set BTRACE_HOME=%DEFAULT_BTRACE_HOME%
set DEFAULT_BTRACE_HOME=

if not exist "%BTRACE_HOME%\libs\btrace-client.jar" goto noBTraceHome

if "%JAVA_HOME%" == "" goto noJavaHome
  set JAVA_ARGS="-XX:+IgnoreUnrecognizedVMOptions"
  if exist "%JAVA_HOME%/jmods/" (
    set JAVA_ARGS="%JAVA_ARGS% -XX:+AllowRedefinitionToAddDeleteMethods"
    set JAVA_ARGS="%JAVA_ARGS% --add-exports jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"
  )
  "%JAVA_HOME%/bin/java" "%JAVA_ARGS%" -cp "%BTRACE_HOME%/libs/btrace-client.jar;%JAVA_HOME%/lib/tools.jar" org.openjdk.btrace.client.Main %*
  goto end
:noJavaHome
  echo Please set JAVA_HOME before running this script
  goto end
:noBTraceHome
  echo Please set BTRACE_HOME before running this script
:end
