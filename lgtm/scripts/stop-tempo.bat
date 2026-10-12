@echo off
setlocal
call "%~dp0env.bat"
echo [tempo] stopping ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%LGTM_PROC%" -Action stop -Exe "%LGTM_SYS%\tempo\tempo.exe"
exit /b 0
