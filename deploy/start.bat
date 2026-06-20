@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ===========================================
echo   RaspelCardTracker - Baslatiliyor
echo ===========================================
echo.

where docker >nul 2>&1
if !errorlevel! neq 0 (
    echo [HATA] Docker bulunamadi.
    echo Docker Desktop indirin: https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)

docker ps >nul 2>&1
if !errorlevel! neq 0 (
    echo [HATA] Docker calismiyor. Docker Desktop'i baslatin.
    pause
    exit /b 1
)

if not exist ".env" (
    if exist ".env.example" (
        echo [BILGI] .env dosyasi olusturuluyor...
        copy ".env.example" ".env" >nul
        echo [OK] .env dosyasi otomatik olusturuldu.
        echo.
    ) else (
        echo [HATA] .env.example dosyasi bulunamadi.
        pause
        exit /b 1
    )
)

echo [1/2] Imaj indiriliyor...
docker compose pull
if !errorlevel! neq 0 (
    echo [HATA] Imaj indirilemedi.
    pause
    exit /b 1
)

echo [2/2] Uygulama baslatiliyor...
docker compose up -d
if !errorlevel! neq 0 (
    echo [HATA] Uygulama baslatilamadi.
    pause
    exit /b 1
)

echo.
echo ===========================================
echo   Uygulama baslatildi!
echo   http://localhost:8080
echo.
echo   Kullanici: admin / Sifre: admin
echo.
echo   Loglar: docker compose logs -f app
echo ===========================================
pause
exit /b 0
