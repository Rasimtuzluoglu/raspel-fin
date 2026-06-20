@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ===========================================
echo   RaspelCardTracker - Durduruluyor
echo ===========================================

docker compose down
if !errorlevel! neq 0 (
    echo [HATA] Durdurma basarisiz oldu.
    pause
    exit /b 1
)

echo [OK] Uygulama durduruldu.
pause
exit /b 0
