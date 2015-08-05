@echo off
rem Purpose:  runs the OpenAS2 application

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

rem    
rem remove -Dorg.apache.commons.logging.Log=org.openas2.logging.Log if using another logging package
rem
"%JAVA%" -Xms32m -Xmx384m  -Dorg.apache.commons.logging.Log=org.openas2.logging.Log  -cp .;../lib/javax.mail.jar;../lib/bcpkix-jdk15on-152.jar;../lib/bcprov-jdk15on-152.jar;../lib/bcmail-jdk15on-152.jar;../lib/bcprov-jdk15on-152;../lib/commons-logging-1.2.jar;../lib/openas2-server.jar org.openas2.app.OpenAS2Server ../config/config.xml

:warn
:END

