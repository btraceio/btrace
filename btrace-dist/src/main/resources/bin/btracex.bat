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

set JAR=%BTRACE_HOME%\libs\btrace.jar
if not exist "%JAR%" (
  echo Could not find %JAR%
  exit /b 1
)

set CP="%JAR%"
"%JAVA_HOME%\bin\java" -cp %CP% org.openjdk.btrace.extcli.Main %*

endlocal
