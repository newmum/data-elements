@echo off
setlocal EnableExtensions
set "SCRIPT_DIR=%~dp0"
set "PS1=%SCRIPT_DIR%start-data-elements.ps1"

rem An explicit PWSH_EXE lets callers use a private PowerShell installation.
rem Discard it only when it does not name an executable.
if defined PWSH_EXE if not exist "%PWSH_EXE%" set "PWSH_EXE="

if not exist "%PS1%" (
  echo startup script not found: %PS1%
  exit /b 1
)

rem Prefer PowerShell 7 from PATH. Use where.exe explicitly: a command named
rem "where" elsewhere on PATH must not prevent the Windows locator from running.
for /f "usebackq delims=" %%I in (`where.exe pwsh.exe 2^>nul`) do if not defined PWSH_EXE set "PWSH_EXE=%%I"

rem PATH can be stale in terminals that were opened before PowerShell 7 was
rem installed. Check the two normal machine-wide installation locations too.
if not defined PWSH_EXE if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" set "PWSH_EXE=%ProgramFiles%\PowerShell\7\pwsh.exe"
if not defined PWSH_EXE if defined ProgramW6432 if exist "%ProgramW6432%\PowerShell\7\pwsh.exe" set "PWSH_EXE=%ProgramW6432%\PowerShell\7\pwsh.exe"

rem Codex Desktop provides a private PowerShell runtime. Its directory is not
rem always inherited by a separately opened Command Prompt or PowerShell window.
if not defined PWSH_EXE if exist "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" set "PWSH_EXE=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe"

rem Finding a file is not sufficient: a bundled .NET runtime may fail before
rem PowerShell starts on Windows systems without the required CET support.
rem Probe without running the application or changing system security settings.
if not defined PWSH_EXE goto windows_powershell
"%PWSH_EXE%" -NoProfile -NonInteractive -Command "exit 0" >nul 2>&1
if "%errorlevel%"=="0" goto run_script
echo Selected PowerShell cannot run on this Windows installation. Trying Windows PowerShell.

:windows_powershell
set "PWSH_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%PWSH_EXE%" goto no_runtime
"%PWSH_EXE%" -NoProfile -NonInteractive -Command "exit 0" >nul 2>&1
if not "%errorlevel%"=="0" goto no_runtime
echo Using compatible Windows PowerShell for the startup script.

:run_script
"%PWSH_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%PS1%" %*
exit /b %errorlevel%

:no_runtime
echo No compatible PowerShell runtime is available.
echo Install a Windows-compatible PowerShell or set PWSH_EXE to a working executable.
exit /b 1
