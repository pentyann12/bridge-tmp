@echo off
setlocal
call "%~dp0env.bat"
set "EXE=%LGTM_SYS%\windows_exporter\windows_exporter.exe"
set "DATA=%LGTM_DATA%\windows_exporter"

powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action status -Exe "%EXE%" >nul
if not errorlevel 1 (
    echo [windows_exporter] already running.
    exit /b 0
)
if not exist "%DATA%" mkdir "%DATA%"

echo [windows_exporter] starting ... http://127.0.0.1:9182/metrics
start "LGTM - windows_exporter" /min /D "%DATA%" "%EXE%" --config.file="%LGTM_CONF%\windows_exporter\config.yml"
exit /b 0
