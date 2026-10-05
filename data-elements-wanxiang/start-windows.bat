@echo off
setlocal
cd /d "%~dp0"
where node >nul 2>&1 || (echo Please install Node.js 22.12 or newer. & pause & exit /b 1)
node -e "const [a,b]=process.versions.node.split('.').map(Number);process.exit(a<22||(a===22&&b<12)?1:0)" || (echo Node.js 22.12 or newer is required. & pause & exit /b 1)
node scripts/ensure-dependencies.mjs || (pause & exit /b 1)
echo Wanxiang runs locally. No backend or database is required.
call npm run dev
pause
