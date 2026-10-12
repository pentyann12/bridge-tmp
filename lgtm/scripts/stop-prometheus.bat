@echo off
setlocal
call "%~dp0env.bat"
echo [prometheus] stopping ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action stop -Exe "%LGTM_SYS%\prometheus\prometheus.exe"
exit /b 0
