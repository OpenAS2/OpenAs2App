@echo off

if "%~4"=="" goto :Usage
set srcStore=%1%
set srcAlias=%2%
set tgtStore=%3%
set tgtAlias=%4%
set action=%5%
if "%action%" == "" set action=insert
rem Setup the Java Virtual Machine
call "%OPENAS2_BASE_DIR%\bin\find_java.bat"
if %ERRORLEVEL% NEQ 0 exit /B 1

echo        Generate a certificate to a PKCS12 key store.
echo        Generating certificate:  using alias %certAlias% to %tgtStore%"

echo Executing action "%action%" on certificate from key "%srcStore%" using alias "%tgtAlias%" to: %tgtStore%"
setlocal
SET /P AREYOUSURE=Do you wish to execute this request (Y/[N])?
IF /I "%AREYOUSURE%" NEQ "Y"  EXIT /B 0
endlocal

set /p srcksPwd=Enter password for source keystore:%=%
set /p tgtksPwd=Enter password for destination keystore:%=%

if "%action%" == "replace" (
  "%JAVA_HOME%\bin\keytool" -delete -alias %tgtAlias% -keystore %tgtStore% -storepass %tgtksPwd% -storetype pkcs12 
  if errorlevel 1 (
    echo The REPLACE option was specified.
    echo Failed to delete the certificate in the keystore for alias "%tgtAlias%". See errors above to correct the problem.
    EXIT /B 1
  )
)
echo "%JAVA_HOME%\bin\keytool" -importkeystore -srckeystore %srcStore% -srcstoretype pkcs12 -srcstorepass "%srcksPwd%" -srcalias %srcAlias% -destalias %tgtAlias% -destkeystore %tgtStore% -deststorepass "%tgtksPwd%" -deststoretype pkcs12
"%JAVA_HOME%\bin\keytool" -importkeystore -srckeystore %srcStore% -srcstoretype pkcs12 -srcstorepass "%srcksPwd%" -srcalias %srcAlias% -destalias %tgtAlias% -destkeystore %tgtStore% -deststorepass "%tgtksPwd%" -deststoretype pkcs12
if errorlevel 1 (
    echo ***** Failed to import the certificate to the keystore. See errors above to correct the problem.
    echo       If the error shows the certificate already exists then add the "replace" option to the command line. 
  EXIT /B 1
)
echo   Successfully Imported certificate from file "%srcStore%" using alias "%tgtAlias%" to: %tgtSttore%

goto :END

:Usage
  echo Import an entry in a source PKCS12 keystore identified by an alias to a target PKCS12 key store.
  echo You must specify the source keystore, source alias entry, target key store file name and an alias for imported certificate.
  echo By default the script will attempt to import the designated entries in the specified alias.
  echo If you wish to replace an existing entry in the target keystore then specify "replace" as a 4th argument to the script
  echo usage: import_alias_from_keystore.bat ^<src keystore^> ^<src alias^> ^<target keystore^> ^<target alias^> [action]s
  echo             WHERE
  echo                src keystore = name of the keystore containing the entry to be imported
  echo                src alias = name of the alias in the source keystore to be imported
  echo                target keystore = name of the target keystore file including .p12 extension
  echo                target alias = alias name used to store the imported entry in the keystore
  echo                action = if not provided this defaults to "import". The only other option is "replace"
  echo                          anything other than "replace" will be interpreted as "import"

  echo 
  echo        eg. import_alias_from_keystore.bat my_cert2.p12 my_cert as2_certs.p12 my_cert_2
  echo                 OR
  echo        eg. import_alias_from_keystore.bat my_cert2.p12 my_cert as2_certs.p12 my_cert_2 replace
 
:warn

:END