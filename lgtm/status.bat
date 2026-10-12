@echo off
setlocal
call "%~dp0scripts\env.bat"
echo LGTM home: %LGTM_HOME%
for %%E in (
    "prometheus\prometheus.exe"
    "loki\loki-windows-amd64.exe"
    "tempo\tempo.exe"
    "grafana\bin\grafana.exe"
    "windows_exporter\windows_exporter.exe"
) do powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action status -Exe "%LGTM_SYS%\%%~E"
exit /b 0
