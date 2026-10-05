@echo off
chcp 65001 >nul
cd /d "%~dp0"
where node >nul 2>nul || (echo 请先安装 Node.js 22 或更高版本。 & pause & exit /b 1)
if not exist node_modules (echo 请先按 README 以 --ignore-scripts 安装依赖，并核对版本清单。 & pause & exit /b 1)
call npm run deps:check || (pause & exit /b 1)
call npm run dev
pause
