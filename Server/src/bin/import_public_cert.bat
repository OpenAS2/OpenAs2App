@echo off

if "%~3"=="" goto :Usage
set srcFile=%1%
set tgtStore=%2%
set certAlias=%3%
set action=%4%


rem Setup the Java Virtual Machine
if not "%JAVA%" == "" goto :Check_JAVA_END
    if not "%JAVA_HOME%" == "" goto :TryJDKEnd
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
       echo Unable to retrieve JAVA_HOME from Registry
       EXIT /B 1
    )
:TryJDKEnd
    if not exist "%JAVA_HOME%" (
        echo JAVA_HOME is not valid: "%JAVA_HOME%"
        EXIT /B 1
    )
    set JAVA=%JAVA_HOME%\bin\java
:Check_JAVA_END

echo "Executing action \"%action%\" on certificate from file \"%srcFile%\" using alias \"%certAlias%\" to: %tgtStore%"

set /p ksPwd=Enter password for keystore:%=%

if "%action%" == "replace" (
    "%JAVA_HOME%\bin\keytool" -delete -alias %certAlias% -keystore %tgtStore% -storepass %ksPwd% -storetype pkcs12
    if errorlevel 1 (
    	echo 
        echo Failed to delete the certificate in the keystore for alias "%certAlias%". See errors above to correct the problem.
        EXIT /B 1
    )
)

"%JAVA_HOME%\bin\keytool" -importcert -file %srcFile% -alias %certAlias% -keystore %tgtStore% -storepass %ksPwd% -storetype pkcs12
if errorlevel 1 (
	echo. 
    echo ***** Failed to import the certificate to the keystore. See errors above to correct the problem.
    echo       If the error shows the certifcate already eists then add the "replace" option to the command line.
    EXIT /B 1
)

echo. 
echo   Sucessfully Imported certificate from file "%srcFile%" using alias "%certAlias%" to: %tgtStore%
echo. 

goto END

:Usage
  echo Import a public certificate to a PKCS12 key store.
  echo You must specify the source file, target key store file name and an alias for imported certificate.
  echo By default the script will attemopt to import the designated certificate.
  echo If you wish to replace an existing certificate then specify "replace" as a 4th argument to the script
  echo usage: %~nx0 ^<src certificate^> ^<target keystore^> ^<cert alias^> [action]
  echo             WHERE
  echo                src certificate = name of the file containg the public key to be imported
  echo                target keystore = name of the target keystore file including .p12 extension
  echo                cert alias = alias name used to store the created digital certificate in the keystore
  echo                action = if not provided this defaults to "import". The only other option is "replace"
  echo                          anything other than "replace" will be interpreted as "import"

  echo.
  echo        eg. %~nx0 partnera.cer as2_certs.p12 partnera
  echo                 OR
  echo        eg. %~nx0 partnera.cer as2_certs.p12 partnera replace
  EXIT /B 1
:END
