@echo off
echo ==========================================
echo   SmartDisplay AI - Limpiar proyecto
echo ==========================================
echo.
cd /d "%~dp0"
call gradlew.bat sdClean
if %errorlevel% EQU 0 (
    echo.
    echo   LIMPIEZA COMPLETA
) else (
    echo.
    echo   Fallo la limpieza - revisa los errores arriba
)
echo.
pause
