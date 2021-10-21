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
rem remove -Dorg.apache.commons.logging.Log=org.openas2.logging.Log if using another logging package
rem
set EXTRA_PARMS=-Xms32m -Xmx384m  -Dorg.apache.commons.logging.Log=org.openas2.logging.Log
rem For versions of Java that prevent restricted HTTP headers (see documentation for discussion on this)
rem set EXTRA_PARMS=%EXTRA_PARMS% -Dsun.net.http.allowRestrictedHeaders=true

rem When using old (unsecure) certificates (please replace them!) that fail to load from the certificate store.
rem set EXTRA_PARMS=%EXTRA_PARMS% -Dorg.bouncycastle.asn1.allow_unsafe_integer=true

rem set EXTRA_PARMS=%EXTRA_PARMS% -Dhttps.protocols=TLSv1.2

rem Uncomment any of the following for enhanced debug
rem set EXTRA_PARMS=%EXTRA_PARMS% -Dmaillogger.debug.enabled=true
rem set EXTRA_PARMS=%EXTRA_PARMS% -DlogRxdMsgMimeBodyParts=true
rem set EXTRA_PARMS=%EXTRA_PARMS% -DlogRxdMdnMimeBodyParts=true
rem set EXTRA_PARMS=%EXTRA_PARMS% -Djavax.net.debug=SSL
rem  set EXTRA_PARMS=%EXTRA_PARMS% -DCmdProcessorSocketCipher=SSL_DH_anon_WITH_RC4_128_MD5

rem Setup the Java Virtual Machine
rem Remove any quotes around the JAVA env var to avoid failures in the script
if not "%JAVA%" == "" set JAVA=%JAVA:"=%
if not "%JAVA%" == "" goto :Check_JAVA_END
    if not "%JAVA_HOME%" == "" goto :JavaHomeFound

rem Use a relatively simplistic but highly effective way by assuming the Java install is in the standard location

set java_base_install_folder=%ProgramFiles%\Java
%Prevent early expansion of the batch variables so that we can accumulate information in the loop
setlocal EnableDelayedExpansion
set java_list=
set found_count=0
rem Find all folders off the Java folder in program files
for /d %%i in ("%java_base_install_folder%\*") do (
  set /A found_count+=1
  call set "java_list[%%found_count%%]=%%i"
)
if %found_count% GTR 1 (
  echo.
  echo More than 1 Java install found:
  for /L %%n in (1,1,%found_count%) do (
    echo %%n: !java_list[%%n]!
  )
  echo.
  echo Set JAVA_HOME to one of the above.
  goto :END
)
if %found_count% EQU 1 (
  echo Java install found: !java_list[1]!
  set JAVA_HOME=!java_list[1]!
  goto :JavaHomeFound
)
echo No Java install found in %java_base_install_folder%
echo If you are using a 32-bit system you may want to change %ProgramFiles% to "%ProgramFiles(x86)%" and try again.
echo If you have installed Java in a non-standard location then set the JAVA_HOME environment variable before running this script.
goto END
:JavaHomeFound
set JAVA=%JAVA_HOME%\bin\java
:Check_JAVA_END
set LIB_JARS=%OPENAS2_BASE_DIR%/lib/*
rem    
rem echo Running: "%JAVA%" %EXTRA_PARMS%  -cp .;%LIB_JARS% org.openas2.app.OpenAS2Server ../config/config.xml
"%JAVA%" %EXTRA_PARMS%  -cp .;%LIB_JARS% org.openas2.app.OpenAS2Server ../config/config.xml

:warn
:END

