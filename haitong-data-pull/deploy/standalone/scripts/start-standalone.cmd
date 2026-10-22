@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Resolve the release root from this script's own directory.  Do not depend
rem on the current command-prompt directory or on a trailing slash in its path.
for %%I in ("%~dp0..") do set "APP_HOME=%%~fI"

if not exist "%APP_HOME%\haitong-data-pull.jar" (
  echo [ERROR] haitong-data-pull.jar was not found in "%APP_HOME%".
  echo [HINT] Run this script from the release package's scripts directory.
  exit /b 1
)

if not exist "%APP_HOME%\config\application.yml" (
  echo [ERROR] config\application.yml was not found in "%APP_HOME%".
  exit /b 1
)

pushd "%APP_HOME%" >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Unable to switch to release directory "%APP_HOME%".
  exit /b 1
)

echo [INFO] Starting haitong-data-pull from "%APP_HOME%".
echo [INFO] Configuration source: "%APP_HOME%\config\application.yml"
echo [INFO] No standalone-env.cmd file or HAITONG_* environment variables are required.
echo [INFO] The startup script does not inspect placeholder text in the configuration file.
java -Duser.timezone=UTC -jar "%APP_HOME%\haitong-data-pull.jar" --spring.config.additional-location=file:./config/ %*
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
