@echo off
setlocal
call "%~dp0env.bat"
echo [grafana] stopping ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action stop -Exe "%LGTM_SYS%\grafana\bin\grafana.exe"
exit /b 0
