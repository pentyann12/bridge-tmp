@echo off
setlocal
rem Start order: exporters, storage backends, then Grafana last.
call "%~dp0scripts\start-windows_exporter.bat"
call "%~dp0scripts\start-prometheus.bat"
call "%~dp0scripts\start-loki.bat"
call "%~dp0scripts\start-tempo.bat"
call "%~dp0scripts\start-grafana.bat"

echo.
echo Waiting for Grafana to become ready ...
powershell -NoProfile -Command "$d=(Get-Date).AddSeconds(90); while((Get-Date) -lt $d){ try { $r=Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 http://127.0.0.1:3000/api/health; if($r.StatusCode -eq 200){ exit 0 } } catch {}; Start-Sleep 2 }; exit 1"
if errorlevel 1 (
    echo Grafana did not respond within 90s. Check the "LGTM - grafana" window.
    exit /b 1
)
echo Grafana is ready: http://127.0.0.1:3000  (no login required)
start "" http://127.0.0.1:3000
exit /b 0
