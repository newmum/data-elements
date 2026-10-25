@echo off
setlocal
cd /d "%~dp0.."
if not exist target\haitong-data-pull.jar (echo Please run scripts\build.cmd first. & exit /b 1)
set "JAVA_BIN=java"
if not "%HAITONG_JAVA_BIN%"=="" set "JAVA_BIN=%HAITONG_JAVA_BIN%"
rem Use a short writable socket directory for Windows Java 21 HTTP selectors.
if "%HAITONG_UDS_DIR%"=="" set "HAITONG_UDS_DIR=%SystemDrive%\Temp\haitong-uds"
if not exist "%HAITONG_UDS_DIR%\" mkdir "%HAITONG_UDS_DIR%"
if not exist "%HAITONG_UDS_DIR%\" (echo Configure HAITONG_UDS_DIR as a short writable directory. & exit /b 1)
"%JAVA_BIN%" "-Djdk.net.unixdomain.tmpdir=%HAITONG_UDS_DIR%" -Duser.timezone=UTC -Dloader.path=lib -jar target\haitong-data-pull.jar --spring.config.additional-location=file:./config/ %*
