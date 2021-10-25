@echo off
rem Purpose:  Find a version of Java to run
rem Use a relatively simplistic but highly effective way by assuming the Java install is in the standard location
rem Change the below env var if Java is in a different standard location
set JavaBaseInstallFolder=%ProgramFiles%\Java

rem Find Java Virtual Machine
rem Remove any quotes around the JAVA env var if it already is set to avoid failures in the script
if not "%JAVA%" == "" set JAVA=%JAVA:"=%
if not "%JAVA%" == "" (
  echo The JAVA var is already set to: %JAVA%  Clear the variable if you want this script to find a different Java
  exit /B 0
)
if not "%JAVA_HOME%" == "" (
  echo The JAVA_HOME var is already set to: %JAVA_HOME%  Clear the variable if you want this script to find a different Java home
  goto :JavaHomeFound
)
rem Prevent early expansion of the batch variables so that we can accumulate information in the loop
rem setlocal EnableDelayedExpansion
set java_list=
set found_count=0
rem Find all folders off the Java folder in program files
for /d %%i in ("%JavaBaseInstallFolder%\*") do (
  set /A found_count+=1
  call set "java_list[%%found_count%%]=%%i"
)
if %found_count% GTR 1 (
  echo.
  echo More than 1 Java install found:
  for /L %%n in (1,1,%found_count%) do (
    call echo %%n: %%java_list[%%n]%%
  )
  echo.
  echo Set JAVA_HOME to one of the above.
  exit /B 1
)
if %found_count% EQU 0 goto JavaNotFound
echo Java install found: %java_list[1]%
set JAVA_HOME=%java_list[1]%
:JavaHomeFound
set JAVA=%JAVA_HOME%\bin\java
echo Set JAVA to: %JAVA%
echo Set JAVA_HOME to: %JAVA_HOME%
exit /B 0

:JavaNotFound
echo No Java install found in %JavaBaseInstallFolder%
echo If you are using a 32-bit system you may want to change %ProgramFiles% to "%ProgramFiles(x86)%" and try again.
echo If you have installed Java in a non-standard location then set the JAVA_HOME environment variable before running this script.
exit /B 2
