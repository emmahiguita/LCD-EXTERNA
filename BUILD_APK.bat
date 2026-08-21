@echo off
echo ==========================================
echo   SmartDisplay AI - Compilar APK Debug
echo   Proyecto: LCDExterna (sin espacios)
echo ==========================================
echo.

cd /d "%~dp0"

echo Limpiando proyecto anterior...
call gradlew.bat sdClean --quiet

echo.
echo Compilando APK (nonRoot Debug)...
call gradlew.bat sdBuild

if %errorlevel% EQU 0 (
    echo.
    echo ==========================================
    echo   BUILD EXITOSO
    echo ==========================================
    echo.
    echo APK en:
    echo %~dp0app\build\outputs\apk\nonRoot\debug\
    echo.
    copy /Y "%~dp0app\build\outputs\apk\nonRoot\debug\app-nonRoot-debug.apk" "%USERPROFILE%\Desktop\SmartDisplayAI.apk" >nul 2>&1
    if %errorlevel% EQU 0 (
        echo APK copiado al Escritorio como: SmartDisplayAI.apk
    )
) else (
    echo.
    echo ==========================================
    echo   BUILD FALLIDO
    echo ==========================================
    echo.
    echo Revisa los errores arriba
)
echo.
pause
