@echo off
setlocal
call "%~dp0env.bat"
set "JAVA_HOME=%LGTM_HOME%\server\jdk"
set "JRE_HOME="
set "CATALINA_HOME=%LGTM_HOME%\server\tomcat"
set "CATALINA_BASE=%CATALINA_HOME%"
set "EXE=%JAVA_HOME%\bin\java.exe"

powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action status -Exe "%EXE%" >nul
if not errorlevel 1 (
    echo [tomcat] already running.
    exit /b 0
)

set "CATALINA_OPTS="-javaagent:%LGTM_HOME%\server\jar\jmx_prometheus_javaagent-1.7.0.jar=9404:%LGTM_CONF%\jmx-exporter\tomcat.yml""

echo [tomcat] starting ... http://127.0.0.1:8080  (JMX exporter http://127.0.0.1:9404/metrics)
start "LGTM - tomcat" /min /D "%CATALINA_BASE%" cmd /c ""%CATALINA_HOME%\bin\catalina.bat" run"
exit /b 0
