@echo off
setlocal
call "%~dp0env.bat"
set "EXE=%LGTM_SYS%\tempo\tempo.exe"
set "DATA=%LGTM_DATA%\tempo"

powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action status -Exe "%EXE%" >nul
if not errorlevel 1 (
    echo [tempo] already running.
    exit /b 0
)
if not exist "%DATA%" mkdir "%DATA%"

echo [tempo] starting ... http://127.0.0.1:3200  (OTLP grpc 4317 / http 4318)
start "LGTM - tempo" /min /D "%DATA%" "%EXE%" -config.file="%LGTM_CONF%\tempo\tempo.yaml" -config.expand-env=true
exit /b 0
