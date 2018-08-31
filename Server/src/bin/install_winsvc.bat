goto CheckRun

:CheckOk

REM Set the key config strings

set SERVICE_NAME=OpenAS2Server
SET tmppath=%~dp0
pushd %tmppath%
cd ..
set OPENAS2_BASE_DIR=%CD%
popd

REM If the directory structure was changed from the OpenAS2 standard set path directly
REM set OPENAS2_BASE_DIR=c:\opt\OpenAS2
set APACHE_COMMONS_DAEMON=%OPENAS2_BASE_DIR%\bin\commons-daemon
set PR_INSTALL=%APACHE_COMMONS_DAEMON%\amd64\prunsrv.exe
set config_file=%OPENAS2_BASE_DIR%\config\config.xml
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
rem set PR_JVM=%JAVA_HOME%\bin\server\jvm.dll
 
REM Startup configuration
set PR_STARTUP=auto
set PR_STARTMODE=jvm
set PR_STARTCLASS=org.openas2.app.OpenAS2WindowsService
REM set PR_STARTMETHOD=start
set PR_STARTPARAMS=start ++StartParams=%config_file%
 
REM Shutdown configuration
set PR_STOPMODE=jvm
set PR_STOPCLASS=org.openas2.app.OpenAS2WindowsService
set PR_STOPMETHOD=stop
set PR_STOPPARAMS=stop
 
REM  Add the below line into the install command if using a specific JVM
REM  --JavaHome="%JAVA_HOME%" ^
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
  --JvmOptions="-Dorg.apache.commons.logging.Log=org.openas2.logging.Log" ^
  --Classpath="%PR_CLASSPATH%" ^
  --StartMode="%PR_STARTMODE%" ^
  --StartClass="%PR_STARTCLASS%" ^
  --StartParams=%PR_STARTPARAMS% ^
  --StopMode="%PR_STOPMODE%" ^
  --StopClass="%PR_STOPCLASS%" ^
  --StopParams="stop"

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
