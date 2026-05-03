@echo off
setlocal enableextensions

rem %~dp0 is expanded pathname of the current script under NT
set DEFAULT_BTRACE_HOME=%~dp0..

if "%BTRACE_HOME%"=="" set BTRACE_HOME=%DEFAULT_BTRACE_HOME%
set DEFAULT_BTRACE_HOME=

set "CLIENT_JAR=%BTRACE_HOME%\libs\btrace.jar"
if not exist "%CLIENT_JAR%" goto noBTraceHome

if "%JAVA_HOME%" == "" goto noJavaHome
  set "JAVA_ARGS=-XX:+IgnoreUnrecognizedVMOptions"
  if exist "%JAVA_HOME%/jmods/" (
    set "JAVA_ARGS=%JAVA_ARGS% -XX:+AllowRedefinitionToAddDeleteMethods"
    set "JAVA_ARGS=%JAVA_ARGS% --add-exports jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"
  )
  if "%1" == "--version" (
    "%JAVA_HOME%\bin\java" %JAVA_ARGS% -cp "%CLIENT_JAR%" io.btrace.boot.Loader --version
    goto end
  )
  "%JAVA_HOME%\bin\java" %JAVA_ARGS% -Dbtrace.client.main=io.btrace.compiler.Compiler -cp "%CLIENT_JAR%;%JAVA_HOME%\lib\tools.jar" io.btrace.boot.Loader %*
  goto end
:noJavaHome
  echo Please set JAVA_HOME before running this script
  goto end
:noBTraceHome
  echo Please set BTRACE_HOME before running this script
:end
endlocal
