@echo off
setlocal
call "%~dp0env.bat"
set "EXE=%LGTM_SYS%\loki\loki-windows-amd64.exe"
set "DATA=%LGTM_DATA%\loki"

powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action status -Exe "%EXE%" >nul
if not errorlevel 1 (
    echo [loki] already running.
    exit /b 0
)
if not exist "%DATA%" mkdir "%DATA%"

echo [loki] starting ... http://127.0.0.1:3100
start "LGTM - loki" /min /D "%DATA%" "%EXE%" -config.file="%LGTM_CONF%\loki\loki.yaml" -config.expand-env=true
exit /b 0
