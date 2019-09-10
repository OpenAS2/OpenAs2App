@echo off

if "%~4"=="" goto :Usage
set tgtStore=%1%
set certAlias=%2%
set sigAlg="%3%withRSA"

set dName=%4%

echo DNAM = %dName%

set CertValidDays=3650

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

echo        Generate a certificate to a PKCS12 key store.
echo        Generating certificate:  using alias %certAlias% to %tgtStore%.p12"

set /p ksPwd=Enter password for keystore:%=%

"%JAVA_HOME%\bin\keytool" -genkeypair -alias %certAlias% -validity %CertValidDays%  -keyalg RSA -sigalg %sigAlg% -keystore %tgtStore%.p12 -storepass %ksPwd% -storetype pkcs12 -dname %dName%
if errorlevel 1 (
    echo Failed to generate keystore
    goto END
)
"%JAVA_HOME%\bin\keytool" -selfcert -alias %certAlias% -validity %CertValidDays%  -sigalg %sigAlg% -keystore %tgtStore%.p12 -storepass %ksPwd% -storetype pkcs12
if errorlevel 1 (
    echo Failed to self certify certificate
    goto END
)

"%JAVA_HOME%\bin\keytool" -export -rfc -file %certAlias%.cer -alias %certAlias%  -keystore %tgtStore%.p12 -storepass %ksPwd% -storetype pkcs12
if errorlevel 1 (
    echo Failed to export public key from keystore
    goto END
)

echo.
echo Generated files:
echo      PKCS12 keystore: %tgtStore%.p12
echo      Public Key File: %certAlias%.cer
echo. 
goto :END

:Usage
    echo maGenerate a certificate to a PKCS12 key store.
  echo You must supply a target key store without the extension (extension will be added as .p12) and an alias for generated certificate.
  echo usage: gen_p12_key_par <target keystore> <cert alias> <sigalg> <distinguished name>
  echo             WHERE
  echo                target keystore = name of the target keystore file without .p12 extension
  echo                cert alias = alias name for the digital certificate
  echo                sigalg = signing algorithm for the digital certificate ... SHA256, MD5 etc
  echo                distinguished name = a string in the format:
  echo                                        CN=<cName>, OU=<orgUnit>, O=<org>, L=<city>, S=<state>, C=<countryCode>

  echo. 
  echo        eg. gen_p12_key_par as2_certs partnera SHA256 "CN=PartnerA Testing, OU=QA, O=PartnerA, L=New York, S=New York, C=US"
  echo      Expected OUTPUT: as2_certs.p12 -  keystore containing both public and private key
  echo                      partnera.cer - public key certificate file .

:warn

:END

