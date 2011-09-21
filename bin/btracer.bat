@echo off

rem %~dp0 is expanded pathname of the current script under NT
set DEFAULT_BTRACE_HOME=%~dp0..

if "%BTRACE_HOME%"=="" set BTRACE_HOME=%DEFAULT_BTRACE_HOME%
set DEFAULT_BTRACE_HOME=

if not exist "%BTRACE_HOME%\build\btrace-agent.jar" goto noBTraceHome

echo Output sent to C:\Temp\%~n1.txt

java -Xshare:off "-javaagent:%BTRACE_HOME%/build/btrace-agent.jar=dumpClasses=false,debug=true,unsafe=false,probeDescPath=.,noServer=true,scriptOutputFile=C:\Temp\%~n1.txt,script=%~1" %2 %3 %4 %5 %6 %7 %8 %9
goto end

:noBTraceHome
  echo Please set BTRACE_HOME before running this script
:end
