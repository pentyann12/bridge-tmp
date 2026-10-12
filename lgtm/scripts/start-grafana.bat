@echo off
setlocal
call "%~dp0env.bat"
set "EXE=%LGTM_SYS%\grafana\bin\grafana.exe"
set "DATA=%LGTM_DATA%\grafana"

powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action status -Exe "%EXE%" >nul
if not errorlevel 1 (
    echo [grafana] already running.
    exit /b 0
)
if not exist "%DATA%" mkdir "%DATA%"

echo [grafana] starting ... http://127.0.0.1:3000  (no login required)
start "LGTM - grafana" /min /D "%DATA%" "%EXE%" server --homepath "%LGTM_SYS%\grafana" --config "%LGTM_CONF%\grafana\grafana.ini"
exit /b 0
