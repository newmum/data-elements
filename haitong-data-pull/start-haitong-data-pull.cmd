@echo off
setlocal
set "TASK_POWERSHELL=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
set "HAITONG_START_SCRIPT=%~dp0..\tools\start-local-haitong.ps1"
if not exist "%HAITONG_START_SCRIPT%" (
    echo Local startup script not found: "%HAITONG_START_SCRIPT%" 1>&2
    exit /b 1
)
"%TASK_POWERSHELL%" -NoProfile -ExecutionPolicy Bypass -File "%HAITONG_START_SCRIPT%" %*
exit /b %errorlevel%
