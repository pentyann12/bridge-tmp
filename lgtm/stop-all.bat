@echo off
setlocal
rem Stop in reverse order of start-all.bat.
call "%~dp0scripts\stop-grafana.bat"
call "%~dp0scripts\stop-tempo.bat"
call "%~dp0scripts\stop-loki.bat"
call "%~dp0scripts\stop-prometheus.bat"
call "%~dp0scripts\stop-windows_exporter.bat"
exit /b 0
