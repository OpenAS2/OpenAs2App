@echo off
rem Purpose:  runs the OpenAS2 application

REM Set the base directory to the folder this file is located in
SET tmppath=%~dp0
pushd %tmppath%
cd ..
set OPENAS2_BASE_DIR=%CD%
popd
REM If the directory structure was changed from the OpenAS2 standard set path directly
REM set OPENAS2_BASE_DIR=c:\opt\OpenAS2

rem Set some of the base system properties for the Java environment and logging
rem
set EXTRA_PARMS=-Xms32m -Xmx384m
rem For versions of Java that prevent restricted HTTP headers (see documentation for discussion on this)
rem set EXTRA_PARMS=%EXTRA_PARMS% -Dsun.net.http.allowRestrictedHeaders=true

rem When using old (unsecure) certificates (please replace them!) that fail to load from the certificate store.
rem set EXTRA_PARMS=%EXTRA_PARMS% -Dorg.bouncycastle.asn1.allow_unsafe_integer=true

if DEFINED OPENAS2_CONFIG_FILE goto skip_config_file_set
set OPENAS2_CONFIG_FILE=%OPENAS2_BASE_DIR%/config/config.xml
:skip_config_file_set
for %%F in ("%OPENAS2_CONFIG_FILE%") do set OPENAS2_CONFIG_DIR=%%~dpF

if [%OPENAS2_PROPERTIES_FILE%]==[] goto skip_properties_file
set EXTRA_PARMS=%EXTRA_PARMS% -Dopenas2.properties.file="%OPENAS2_PROPERTIES_FILE%"
:skip_properties_file
rem set EXTRA_PARMS=%EXTRA_PARMS% -Dhttps.protocols=TLSv1.2

if NOT [%OPENAS2_LOGGING_BASE%]==[] goto skip_logging_base_set
set OPENAS2_LOGGING_BASE=%OPENAS2_BASE_DIR%\logs
:skip_logging_base_set
set EXTRA_PARMS=%EXTRA_PARMS% DOPENAS2_LOG_DIR="%OPENAS2_LOGGING_BASE%"

rem Uncomment any of the following for enhanced debug
rem set EXTRA_PARMS=%EXTRA_PARMS% -Dmaillogger.debug.enabled=true
rem set EXTRA_PARMS=%EXTRA_PARMS% -DlogRxdMsgMimeBodyParts=true
rem set EXTRA_PARMS=%EXTRA_PARMS% -DlogRxdMdnMimeBodyParts=true
rem set EXTRA_PARMS=%EXTRA_PARMS% -Djavax.net.debug=SSL
rem  set EXTRA_PARMS=%EXTRA_PARMS% -DCmdProcessorSocketCipher=SSL_DH_anon_WITH_RC4_128_MD5

rem Setup the Java Virtual Machine
call "%OPENAS2_BASE_DIR%\bin\find_java.bat"
if %ERRORLEVEL% NEQ 0 exit /B 1

rem Using file globbing via * in classpath causes Mailcap loading issues so build full path
rem set LIB_JARS=%OPENAS2_BASE_DIR%/lib/*
setLocal EnableDelayedExpansion
set LIB_JARS=
for /R %OPENAS2_BASE_DIR%/lib %%a in (*.jar) do (
  set LIB_JARS=!LIB_JARS!;%%a
)
set LIB_JARS=".!LIB_JARS!"
setLocal disableDelayedExpansion
rem  Include the config dir so that logging configuration files are found
set CLASSPATH=.;%LIB_JARS%;%OPENAS2_CONFIG_DIR%
rem echo Running: "%JAVA%" %EXTRA_PARMS%  -cp .;%LIB_JARS% org.openas2.app.OpenAS2Server "%OPENAS2_CONFIG_FILE%"
"%JAVA%" %EXTRA_PARMS%  -cp %CLASSPATH% org.openas2.app.OpenAS2Server "%OPENAS2_CONFIG_FILE%"

:warn
:END
