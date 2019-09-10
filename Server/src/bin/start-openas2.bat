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
if not "%JAVA%" == "" goto :Check_JAVA_END
    if not "%JAVA_HOME%" == "" goto :TryJDKEnd
        call :warn JAVA_HOME not set; results may vary
:TryWOWJRE
    FOR /F "usebackq tokens=3*" %%A IN (`REG QUERY "HKLM\Software\WOW6432NODE\JavaSoft\Java Runtime Environment" /s /v CurrentVersion ^| find "CurrentVersion"`) DO (
       set JAVA_VERSION=%%A
    )
    FOR /F "usebackq tokens=3*" %%A IN (`REG QUERY "HKLM\Software\WOW6432NODE\JavaSoft\Java Runtime Environment\%JAVA_VERSION%" /s /v JavaHome ^| find "JavaHome"`) DO (
       set JAVA_HOME=%%A %%B
    )
    if not exist "%JAVA_HOME%" goto :TryWOWJDK
    goto TryJDKEnd
:TryWOWJDK
    FOR /F "usebackq tokens=3*" %%A IN (`REG QUERY "HKLM\Software\WOW6432NODE\JavaSoft\Java Development Kit" /s /v CurrentVersion  ^| find "CurrentVersion"`) DO (
       set JAVA_VERSION=%%A
    )
    FOR /F "usebackq tokens=3*" %%A IN (`REG QUERY "HKLM\Software\WOW6432NODE\JavaSoft\Java Development Kit\%JAVA_VERSION%" /s /v JavaHome ^| find "JavaHome"`) DO (
       set JAVA_HOME=%%A %%B
    )
    if not exist "%JAVA_HOME%" goto :TryJRE
    goto TryJDKEnd
:TryJRE
    FOR /F "usebackq tokens=3*" %%A IN (`REG QUERY "HKLM\Software\JavaSoft\Java Runtime Environment" /s /v CurrentVersion  ^| find "CurrentVersion"`) DO (
       set JAVA_VERSION=%%A
    )
    FOR /F "usebackq tokens=3*" %%A IN (`REG QUERY "HKLM\Software\JavaSoft\Java Runtime Environment\%JAVA_VERSION%" /s /v JavaHome ^| find "JavaHome"`) DO (
       set JAVA_HOME=%%A %%B
    )
    if not exist "%JAVA_HOME%" goto :TryJDK
    goto TryJDKEnd
:TryJDK
    FOR /F "usebackq tokens=3*" %%A IN (`REG QUERY "HKLM\Software\JavaSoft\Java Development Kit" /s /v CurrentVersion  ^| find "CurrentVersion"`) DO (
       set JAVA_VERSION=%%A
    )
    FOR /F "usebackq tokens=3*" %%A IN (`REG QUERY "HKLM\Software\JavaSoft\Java Development Kit\%JAVA_VERSION%" /s /v JavaHome ^| find "JavaHome"`) DO (
       set JAVA_HOME=%%A %%B
    )
    if not exist "%JAVA_HOME%" (
       call :warn Unable to retrieve JAVA_HOME from Registry
    )
:TryJDKEnd
    if not exist "%JAVA_HOME%" (
        call :warn JAVA_HOME is not valid: "%JAVA_HOME%"
        goto END
    )
    set JAVA=%JAVA_HOME%\bin\java
:Check_JAVA_END
set LIB_JARS=%OPENAS2_BASE_DIR%/lib/*
rem    
"%JAVA%" %EXTRA_PARMS%  -cp .;%LIB_JARS% org.openas2.app.OpenAS2Server ../config/config.xml

:warn
:END

