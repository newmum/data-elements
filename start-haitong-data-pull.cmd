@echo off
setlocal
set "TASK_POWERSHELL=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
"%TASK_POWERSHELL%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\start-local-haitong.ps1" %*
exit /b %errorlevel%
