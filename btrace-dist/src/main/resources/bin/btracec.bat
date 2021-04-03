@echo off

rem %~dp0 is expanded pathname of the current script under NT
set DEFAULT_BTRACE_HOME=%~dp0..

if "%BTRACE_HOME%"=="" set BTRACE_HOME=%DEFAULT_BTRACE_HOME%
set DEFAULT_BTRACE_HOME=

if not exist "%BTRACE_HOME%\libs\btrace-client.jar" goto noBTraceHome

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
  "%JAVA_HOME%/bin/java" "%JAVA_ARGS%" -cp "%BTRACE_HOME%/libs/btrace-client.jar;%JAVA_HOME%/lib/tools.jar" org.openjdk.btrace.compiler.Compiler %*
  goto end
:noJavaHome
  echo Please set JAVA_HOME before running this script
  goto end
:noBTraceHome
  echo Please set BTRACE_HOME before running this script
:end