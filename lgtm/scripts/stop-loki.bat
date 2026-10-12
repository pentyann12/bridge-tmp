@echo off
setlocal
call "%~dp0env.bat"
echo [loki] stopping ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action stop -Exe "%LGTM_SYS%\loki\loki-windows-amd64.exe"
exit /b 0
