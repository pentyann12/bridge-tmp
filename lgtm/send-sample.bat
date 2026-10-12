@echo off
setlocal
rem Sends sample traces (OTLP -> Tempo) and correlated logs (-> Loki)
rem every few seconds until Ctrl+C. Start the stack first.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\send-sample.ps1" %*
