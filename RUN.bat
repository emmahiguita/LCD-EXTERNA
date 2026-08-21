@echo off
echo ==========================================
echo   SmartDisplay AI - Instalar y Ejecutar
echo   (complementa a BUILD_APK.bat, que solo compila)
echo ==========================================
echo.

cd /d "%~dp0"

echo Instalando y ejecutando (nonRoot Debug) en el dispositivo conectado...
rem sdRun instala el APK y lanza la app; resuelve adb desde el SDK (no requiere PATH).
call gradlew.bat sdRun

if %errorlevel% NEQ 0 (
    echo.
    echo   FALLO. Revisa que haya un dispositivo/emulador conectado ^(adb devices^)
    echo   y que la depuracion USB este activada.
    echo.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo   LISTO - App instalada y ejecutandose
echo ==========================================
echo.
pause
