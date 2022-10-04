@echo off
setLocal EnableDelayedExpansion
if /I "!IS_AUTOMATED_EXEC!" == "1" goto CheckOk 
goto CheckRun

:CheckOk

REM Set the key config strings
if /I not "!SERVICE_NAME!" == "" goto ServiceNameSet
set SERVICE_NAME=OpenAS2Server
echo No override for service name specified. Using default service name...

:ServiceNameSet
echo Using service name: !SERVICE_NAME!
SET tmppath=%~dp0
pushd %tmppath%
cd ..
set OPENAS2_BASE_DIR=%CD%
popd

REM If the directory structure was changed from the OpenAS2 standard set path directly
REM set OPENAS2_BASE_DIR=c:\opt\OpenAS2
set APACHE_COMMONS_DAEMON=%OPENAS2_BASE_DIR%\bin\commons-daemon
set PR_INSTALL=%APACHE_COMMONS_DAEMON%\amd64\prunsrv.exe
set STARTUP_ARGS=%OPENAS2_BASE_DIR%\config\config.xml
set CUSTOM_SERVICE_PARAMS=
set PR_CLASSPATH=%OPENAS2_BASE_DIR%\lib\*
REM If using a specific JVM then uncomment & set JAVA_HOME below
REM set JAVA_HOME=C:\Program Files\Java\jre1.8.0_171
 
REM JVM configuration
REM MX and MS options are in MB SS in KB
set PR_JVMMS=32
set PR_JVMMX=1024
set PR_JVMSS=4000

REM Service log configuration
set PR_LOGPREFIX=%SERVICE_NAME%
set PR_LOGPATH=%APACHE_COMMONS_DAEMON%\logs
set PR_STDOUTPUT=%APACHE_COMMONS_DAEMON%\logs\stdout.txt
set PR_STDERROR=%APACHE_COMMONS_DAEMON%\logs\stderr.txt
set PR_LOGLEVEL=Error
 
REM Path to java installation
REM If the auto mode does not work then you can explicitly set the path to the Java install DLL 
set PR_JVM=auto
if /I "%CUSTOM_JAVA_HOME%" == "" goto SkipCustomJava
rem remove any enclosing quotes
set CUSTOM_JAVA_HOME=%CUSTOM_JAVA_HOME:"=%
set PR_JVM=%CUSTOM_JAVA_HOME%\bin\server\jvm.dll
:SkipCustomJava

SET PR_JVM_OPTS="-Dorg.apache.commons.logging.Log=org.openas2.logging.Log"
if /I "!OPENAS2_PROPERTIES_FILE!" == "" goto SkipArgsAdd
rem Add the property arg to JVM options
echo Setting custom properties file for service startup: !OPENAS2_PROPERTIES_FILE!
set PR_JVM_OPTS=%PR_JVM_OPTS% ++JvmOptions="-Dopenas2.properties.file=%OPENAS2_PROPERTIES_FILE%"
:SkipArgsAdd
setLocal DisableDelayedExpansion

REM Startup configuration
set PR_STARTUP=auto
set PR_STARTMODE=jvm
set PR_STARTCLASS=org.openas2.app.OpenAS2WindowsService
set PR_STARTMETHOD=start
set PR_STARTPARAMS=%STARTUP_ARGS%
 
REM Shutdown configuration
set PR_STOPMODE=jvm
set PR_STOPCLASS=org.openas2.app.OpenAS2WindowsService
set PR_STOPMETHOD=stop
set PR_STOPPARAMS=stop
 
REM  Add the below line into the install command if using a specific JVM
REM  --JavaHome="%JAVA_HOME%" ^
if /I "!CUSTOM_JAVA_HOME!" == "" goto NoCustomJVM
rem Add the property arg to JVM options
echo Setting custom properties file for service startup: !OPENAS2_PROPERTIES_FILE!
set CUSTOM_SERVICE_PARAMS=%CUSTOM_SERVICE_PARAMS% ++JavaHome="%CUSTOM_JAVA_HOME%"
:NoCustomJVM
REM Make the folder for service creation and control accessible to the "Local Service" user running the servioce
icacls "%APACHE_COMMONS_DAEMON%" /grant *S-1-5-19:(OI)(CI)(M)

REM Install service
"%PR_INSTALL%" //IS/%SERVICE_NAME% ^
  --DisplayName="%SERVICE_NAME%" ^
  --Install="%PR_INSTALL%" ^
  --Startup="%PR_STARTUP%" ^
  --LogPath="%PR_LOGPATH%" ^
  --LogPrefix="%PR_LOGPREFIX%" ^
  --LogLevel="%PR_LOGLEVEL%" ^
  --StdOutput="%PR_STDOUTPUT%" ^
  --StdError="%PR_STDERROR%" ^
  --Jvm="%PR_JVM%" ^
  --JvmMs="%PR_JVMMS%" ^
  --JvmMx="%PR_JVMMX%" ^
  --JvmSs="%PR_JVMSS%" ^
  --JvmOptions=%PR_JVM_OPTS% ^
  --Classpath="%PR_CLASSPATH%" ^
  --StartMode="%PR_STARTMODE%" ^
  --StartMethod="%PR_STARTMETHOD%" ^
  --StartClass="%PR_STARTCLASS%" ^
  --StartParams=%PR_STARTPARAMS% ^
  --StopMode="%PR_STOPMODE%" ^
  --StopClass="%PR_STOPCLASS%" ^
  --StopParams="stop" %CUSTOM_SERVICE_PARAMS%

goto END

:CheckRun
@echo off
echo/
if exist "%SystemRoot%\System32\choice.exe" goto UseChoice

setlocal EnableExtensions EnableDelayedExpansion
:UseSetPrompt
set "UserChoice=N"
set /P "UserChoice=Are you sure you want to install the service [Y/N]? "
set "UserChoice=!UserChoice: =!"
if /I "!UserChoice!" == "N" endlocal & goto END
if /I not "!UserChoice!" == "Y" goto UseSetPrompt
endlocal
goto CheckOk

:UseChoice
%SystemRoot%\System32\choice.exe /C YN /N /M "Are you sure you want to install the service [Y/N]? "
if errorlevel 2 goto END
goto CheckOk

:END
