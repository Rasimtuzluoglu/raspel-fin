@echo off
cd /d "%~dp0"
echo RaspelCardTracker durduruluyor...
docker compose down
echo [OK] Durduruldu.
pause
exit /b 0
