@echo off
setlocal

REM Set the name of the Java application to terminate
set "APP_NAME=org.openas2.app.OpenAS2Server"

REM Find the Java process with the specified application name
for /f "tokens=1 delims= " %%a in ('jps -l ^| findstr /i "%APP_NAME%"') do (
    set "JAVA_PROCESS_ID=%%a"
)

REM If the Java process is found, terminate it
if defined JAVA_PROCESS_ID (
    echo Terminating %APP_NAME% with Process ID: %JAVA_PROCESS_ID%
    taskkill /F /PID %JAVA_PROCESS_ID%
) else (
    echo %APP_NAME% is not running.
)

endlocal
