@echo off
setlocal
cd /d "%~dp0.."
if not exist target\haitong-data-pull.jar (echo Please run scripts\build.cmd first. & exit /b 1)
set "JAVA_BIN=java"
if not "%HAITONG_JAVA_BIN%"=="" set "JAVA_BIN=%HAITONG_JAVA_BIN%"
"%JAVA_BIN%" -Duser.timezone=UTC -Dloader.path=lib -jar target\haitong-data-pull.jar --spring.config.additional-location=file:./config/ %*
