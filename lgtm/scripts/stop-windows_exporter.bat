@echo off
setlocal
call "%~dp0env.bat"
echo [windows_exporter] stopping ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action stop -Exe "%LGTM_SYS%\windows_exporter\windows_exporter.exe"
exit /b 0
