@echo off

if "%~4"=="" goto :Usage
set tgtStore=%1%
set certAlias=%2%
set sigAlg="%3%withRSA"

set dName=%4%

set CertValidDays=2900

SET tmppath=%~dp0
pushd %tmppath%
cd ..
set OPENAS2_BASE_DIR=%CD%
popd

rem Setup the Java Virtual Machine
call "%OPENAS2_BASE_DIR%\bin\find_java.bat"
if %ERRORLEVEL% NEQ 0 EXIT /B 1

echo        Generate a certificate to a PKCS12 key store.
echo        Generating certificate:  using alias %certAlias% to %tgtStore%.p12"

setLocal EnableDelayedExpansion
if /I "!IS_AUTOMATED_EXEC!" == "1" (
  set ksPwd=$KEYSTORE_PASSWORD
)
else (
  set /p ksPwd=Enter password for keystore:%=%
)


"%JAVA_HOME%\bin\keytool" -genkeypair -alias %certAlias% -validity %CertValidDays%  -keyalg RSA -sigalg %sigAlg% -keystore %tgtStore%.p12 -storepass %ksPwd% -storetype pkcs12 -dname %dName%
if errorlevel 1 (
    echo Failed to generate keystore
    EXIT /B 1
)
"%JAVA_HOME%\bin\keytool" -selfcert -alias %certAlias% -validity %CertValidDays%  -sigalg %sigAlg% -keystore %tgtStore%.p12 -storepass %ksPwd% -storetype pkcs12
if errorlevel 1 (
    echo Failed to self certify certificate
    EXIT /B 1
)

"%JAVA_HOME%\bin\keytool" -export -rfc -file %certAlias%.cer -alias %certAlias%  -keystore %tgtStore%.p12 -storepass %ksPwd% -storetype pkcs12
if errorlevel 1 (
    echo Failed to export public key from keystore
    EXIT /B 1
)

echo.
echo Generated files:
echo      PKCS12 keystore: %tgtStore%.p12
echo      Public Key File: %certAlias%.cer
echo. 
goto :END

:Usage
  echo Generate a certificate to a PKCS12 key store.
  echo You must supply a target key store without the extension (extension will be added as .p12) and an alias for generated certificate.
  echo usage: gen_p12_key_par ^<target keystore^> ^<cert alias^> ^<sigalg^> ^<distinguished name^>
  echo             WHERE
  echo                target keystore = name of the target keystore file without .p12 extension
  echo                cert alias = alias name for the digital certificate
  echo                sigalg = signing algorithm for the digital certificate ... SHA256, MD5 etc
  echo                distinguished name = a string in the format:
  echo                                        CN=^<cName^>, OU=^<orgUnit^>, O=^<org^>, L=^<city^>, S=^<state^>, C=^<countryCode^>

  echo. 
  echo        eg. gen_p12_key_par as2_certs partnera SHA256 "CN=PartnerA Testing, OU=QA, O=PartnerA, L=New York, S=New York, C=US"
  echo      Expected OUTPUT: as2_certs.p12 -  keystore containing both public and private key
  echo                      partnera.cer - public key certificate file .

  EXIT /B 1

:END

