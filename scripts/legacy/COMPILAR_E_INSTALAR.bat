@echo off
REM ============================================================================
REM  SMARTDISPLAY AI - COMPILAR + INSTALAR + LOGS
REM  Ejecuta este script con doble clic o desde CMD como administrador
REM ============================================================================

setlocal enabledelayedexpansion

color 0B
title SmartDisplay AI - Compilar e Instalar

set "PROJECT_DIR=C:\Users\emman\Desktop\Proyectos\LCD EXTERNA"
set "SOURCE_DIR=%PROJECT_DIR%\moonlight-android-master"
set "REPO_DIR=C:\Users\emman\moonlight_build\moonlight-android"
set "APK_OUT=%REPO_DIR%\app\build\outputs\apk\nonRoot\debug\app-nonRoot-debug.apk"
set "FINAL_APK=%PROJECT_DIR%\SmartDisplayAI.apk"
set "ADB=C:\Users\emman\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set "PACKAGE=com.limelight.smartdisplay.debug"
set "LOG=%PROJECT_DIR%\build_log.txt"

echo.
echo ============================================================================
echo   SMARTDISPLAY AI -- COMPILAR + INSTALAR + PROBAR
echo   KeyboardOverlay / ControlsMenuOverlay / InputMode
echo ============================================================================
echo.

REM ── Verificar ADB y dispositivo ─────────────────────────────────────────────
echo [*] Verificando dispositivo Android...
"%ADB%" devices
echo.

REM ── Sincronizar fuentes modificadas ─────────────────────────────────────────
echo [*] Sincronizando fuentes al repositorio de compilacion...
xcopy /E /I /Y "%SOURCE_DIR%\app" "%REPO_DIR%\app" >nul 2>&1
echo     OK - Archivos sincronizados

REM ── Eliminar archivos obsoletos (xcopy no borra lo quitado del origen) ───────
REM  Si vuelven, reaparece el crash del teclado (NPE en SmartDisplayOverlay).
del /F /Q "%REPO_DIR%\app\src\main\res\layout-port\overlay_keyboard.xml" >nul 2>&1
del /F /Q "%REPO_DIR%\app\src\main\res\layout-land\overlay_keyboard.xml" >nul 2>&1
del /F /Q "%REPO_DIR%\app\src\main\java\com\limelight\ui\KeyboardOverlay.java" >nul 2>&1
echo     OK - Archivos obsoletos eliminados

REM ── Compilar APK ────────────────────────────────────────────────────────────
echo.
echo [*] Compilando APK (puede tardar 1-3 minutos)...
echo     Log completo en: %LOG%
echo.

cd /d "%REPO_DIR%"
call gradlew.bat assembleNonRootDebug > "%LOG%" 2>&1

if errorlevel 1 (
    color 0C
    echo.
    echo [ERROR] La compilacion fallo.
    echo Ver detalles en: %LOG%
    echo.
    echo Ultimas lineas del log:
    powershell -Command "Get-Content '%LOG%' | Select-Object -Last 30"
    pause
    exit /b 1
)

REM ── Verificar APK generado ──────────────────────────────────────────────────
if not exist "%APK_OUT%" (
    color 0C
    echo [ERROR] APK no encontrado despues de compilar.
    echo Ruta esperada: %APK_OUT%
    pause
    exit /b 1
)

echo.
color 0A
echo [OK] Compilacion exitosa!
color 0B

REM ── Copiar APK al directorio principal ──────────────────────────────────────
copy /Y "%APK_OUT%" "%FINAL_APK%" >nul
echo [*] APK copiado a: %FINAL_APK%

REM ── Instalar en el dispositivo ───────────────────────────────────────────────
echo.
set "DEVICE_SERIAL="
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" (
        set "TEMP_VAL=%%A"
        if "!TEMP_VAL!"=="!TEMP_VAL::=!" (
            set "DEVICE_SERIAL=%%A"
        )
    )
)
if not defined DEVICE_SERIAL (
    for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
        if "%%B"=="device" (
            set "DEVICE_SERIAL=%%A"
        )
    )
)

if defined DEVICE_SERIAL (
    set "ADB_FLAGS=-s !DEVICE_SERIAL!"
    echo       INFO - Usando dispositivo: !DEVICE_SERIAL!
) else (
    set "ADB_FLAGS="
    echo       AVISO - No se detectaron dispositivos especificos. Usando fallback general.
)
echo.

echo [*] Instalando en dispositivo Android...
"%ADB%" !ADB_FLAGS! install -r "%FINAL_APK%"

if errorlevel 1 (
    echo.
    echo [!] Intentando desinstalar version anterior...
    "%ADB%" !ADB_FLAGS! uninstall "%PACKAGE%" >nul 2>&1
    echo [*] Reinstalandose...
    "%ADB%" !ADB_FLAGS! install "%FINAL_APK%"
    if errorlevel 1 (
        color 0C
        echo.
        echo [ERROR] No se pudo instalar. Verifica:
        echo   1. Dispositivo conectado por USB
        echo   2. Depuracion USB habilitada
        echo   3. Aceptar la huella de confianza en el movil
        pause
        exit /b 1
    )
)

REM ── Lanzar la aplicacion ─────────────────────────────────────────────────────
echo.
echo [*] Lanzando SmartDisplay AI en el dispositivo...
"%ADB%" !ADB_FLAGS! shell am start -n "%PACKAGE%/com.limelight.PcView" 2>nul
if errorlevel 1 (
    "%ADB%" !ADB_FLAGS! shell monkey -p "%PACKAGE%" -c android.intent.category.LAUNCHER 1 2>nul
)

echo.
color 0A
echo ============================================================================
echo   [SUCCESS] APK INSTALADO Y LANZADO!
echo   Version: 12.1 (nonRoot debug)
echo   Paquete: %PACKAGE%
echo ============================================================================
echo.
color 0B

echo Presiona cualquier tecla para monitorear logs de conexion...
echo (Ctrl+C para salir del monitoreo)
echo.
pause

REM ── Monitoreo de logs de conexion ─────────────────────────────────────────────
echo.
echo ── LOGS EN TIEMPO REAL ─────────────────────────────────────────────────────
echo Filtrando: SmartDisplay, NvConnection, NvHTTP, errores...
echo.
"%ADB%" !ADB_FLAGS! logcat -c
"%ADB%" !ADB_FLAGS! logcat -v time SmartDisplay:V Moonlight:V NvConnection:V NvHTTP:V com.limelight:V *:E

cd /d "%PROJECT_DIR%"
