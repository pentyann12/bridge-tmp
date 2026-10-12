@echo off
setlocal
call "%~dp0env.bat"
set "JAVA_HOME=%LGTM_HOME%\server\jdk"
set "JRE_HOME="
set "CATALINA_HOME=%LGTM_HOME%\server\tomcat"
set "CATALINA_BASE=%CATALINA_HOME%"
set "CATALINA_OPTS="
set "EXE=%JAVA_HOME%\bin\java.exe"

echo [tomcat] stopping ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action status -Exe "%EXE%" >nul
if errorlevel 1 (
    echo   tomcat is not running.
    exit /b 0
)
call "%CATALINA_HOME%\bin\catalina.bat" stop >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action stop -Exe "%EXE%" -TimeoutSec 30
exit /b 0
