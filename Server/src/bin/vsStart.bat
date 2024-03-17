@echo off
rem Purpose: runs the OpenAS2 application with Visual Studio Code debugger attached

REM Set the base directory to the folder this file is located in
SET tmppath=%~dp0
pushd %tmppath%
cd ..
set OPENAS2_BASE_DIR=%CD%
popd

REM If the directory structure was changed from the OpenAS2 standard set path directly
REM set OPENAS2_BASE_DIR=c:\opt\OpenAS2

rem Set some of the base system properties for the Java environment and logging
set EXTRA_PARMS=-Xms32m -Xmx384m -Dorg.apache.commons.logging.Log=org.openas2.logging.Log

rem Add Java Virtual Machine options for remote debugging
set EXTRA_PARMS=%EXTRA_PARMS% -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005

rem ... (other existing configurations)

rem Setup the Java Virtual Machine
call "%OPENAS2_BASE_DIR%\bin\find_java.bat"
if %ERRORLEVEL% NEQ 0 exit /B 1

rem Using file globbing via * in classpath causes Mailcap loading issues so build the full path
setLocal EnableDelayedExpansion
set LIB_JARS=
for /R %OPENAS2_BASE_DIR%/lib %%a in (*.jar) do (
  set LIB_JARS=!LIB_JARS!;%%a
)
set LIB_JARS=".!LIB_JARS!"
setLocal disableDelayedExpansion

rem Include the bin dir so that commons-logging.properties is found
set CLASSPATH=.;%LIB_JARS%;%OPENAS2_BASE_DIR%/bin

rem Start the Java application with Visual Studio Code debugger attached
"%JAVA%" %EXTRA_PARMS% -cp .;%LIB_JARS% org.openas2.app.OpenAS2Server "%OPENAS2_BASE_DIR%/config/config.xml"

:warn
:END
