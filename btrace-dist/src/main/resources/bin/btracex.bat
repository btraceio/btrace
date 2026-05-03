@echo off
setlocal enableextensions

if not defined BTRACE_HOME (
  set SCRIPT=%~f0
  for %%i in ("%SCRIPT%") do set BTRACE_HOME=%%~dpi..\
)

if not defined JAVA_HOME (
  echo Please set JAVA_HOME before running this script
  exit /b 1
)

set "JAR=%BTRACE_HOME%\libs\btrace.jar"
if not exist "%JAR%" (
  echo Could not find %JAR%
  exit /b 1
)

set "JAVA_ARGS=-XX:+IgnoreUnrecognizedVMOptions"
if exist "%JAVA_HOME%/jmods/" (
  set "JAVA_ARGS=%JAVA_ARGS% -XX:+AllowRedefinitionToAddDeleteMethods"
  set "JAVA_ARGS=%JAVA_ARGS% --add-exports jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED"
)

"%JAVA_HOME%\bin\java" %JAVA_ARGS% -Dbtrace.client.main=io.btrace.extcli.Main -cp "%JAR%" io.btrace.boot.Loader %*

endlocal
