@echo off
rem ------------------------------------------------------------
rem Common environment. Called from every start-*.bat / stop-*.bat.
rem   LGTM_HOME : absolute path of this folder (backslashes, no trailing \)
rem   LGTM_ROOT : same path with forward slashes (used inside YAML / INI)
rem ------------------------------------------------------------
for %%I in ("%~dp0..") do set "LGTM_HOME=%%~fI"
set "LGTM_ROOT=%LGTM_HOME:\=/%"
set "LGTM_SYS=%LGTM_HOME%\system"
set "LGTM_CONF=%LGTM_HOME%\conf"
set "LGTM_DATA=%LGTM_HOME%\data"
set "LGTM_PROC=%~dp0proc.ps1"
