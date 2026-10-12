@echo off
setlocal
call "%~dp0env.bat"
set "EXE=%LGTM_SYS%\prometheus\prometheus.exe"
set "DATA=%LGTM_DATA%\prometheus"

powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action status -Exe "%EXE%" >nul
if not errorlevel 1 (
    echo [prometheus] already running.
    exit /b 0
)
if not exist "%DATA%" mkdir "%DATA%"

echo [prometheus] starting ... http://127.0.0.1:9090
start "LGTM - prometheus" /min /D "%DATA%" "%EXE%" --config.file="%LGTM_CONF%\prometheus\prometheus.yml" --storage.tsdb.path="%DATA%" --web.listen-address=0.0.0.0:9090 --web.enable-remote-write-receiver --web.enable-otlp-receiver --web.enable-lifecycle --enable-feature=exemplar-storage
exit /b 0
