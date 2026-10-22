@echo off
setlocal
cd /d "%~dp0.."
where java >nul 2>nul
if errorlevel 1 (echo Please install JDK 21. & exit /b 1)
where mvn >nul 2>nul
if errorlevel 1 (echo Please install Maven 3.9+ and add it to PATH. & exit /b 1)
call mvn --batch-mode --no-transfer-progress clean verify %*
if errorlevel 1 exit /b 1
echo Built: target\haitong-data-pull.jar
