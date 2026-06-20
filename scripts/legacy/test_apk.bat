@echo off
REM ============================================================================
REM  SMARTDISPLAY AI - TEST APK EN DISPOSITIVO
REM  Compila, instala y monitorea logs en tiempo real
REM ============================================================================

setlocal enabledelayedexpansion

color 0B
title SmartDisplay AI - Test APK

set "PROJECT_DIR=C:\Users\emman\Desktop\Proyectos\LCD EXTERNA"
set "SOURCE_DIR=%PROJECT_DIR%\moonlight-android-master"
set "REPO_DIR=C:\Users\emman\moonlight_build\moonlight-android"
set "APK_OUT=%REPO_DIR%\app\build\outputs\apk\nonRoot\debug\app-nonRoot-debug.apk"
set "FINAL_APK=%PROJECT_DIR%\SmartDisplayAI.apk"
set "ADB=C:\Users\emman\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set "PACKAGE=com.limelight.smartdisplay.debug"
set "ACT=com.limelight.PcView"

echo.
echo ============================================================================
echo   SMARTDISPLAY AI -- COMPILAR + INSTALAR + PRUEBA
echo ============================================================================
echo.

REM ── 1. VERIFICAR ADB ────────────────────────────────────────────────────────
echo [1/6] Verificando ADB y dispositivo conectado...
"%ADB%" devices
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
    echo       AVISO - No se detectaron dispositivos especificos. Usando fallback.
)
echo.

REM ── 2. SINCRONIZAR FUENTES ──────────────────────────────────────────────────
echo [2/6] Sincronizando archivos fuente al repositorio de compilacion...
xcopy /E /I /Y "%SOURCE_DIR%" "%REPO_DIR%" >nul 2>&1
if errorlevel 1 (
    color 0C
    echo [ERROR] Fallo al sincronizar fuentes.
    pause
    exit /b 1
)
echo       OK - Fuentes sincronizadas

REM ── 3. COMPILAR ─────────────────────────────────────────────────────────────
echo.
echo [3/6] Compilando APK (assembleNonRootDebug)...
cd /d "%REPO_DIR%"
call gradlew.bat assembleNonRootDebug 2>&1

if errorlevel 1 (
    color 0C
    echo.
    echo [ERROR] Fallo la compilacion. Revisa los errores arriba.
    pause
    exit /b 1
)

if not exist "%APK_OUT%" (
    color 0C
    echo [ERROR] APK no encontrado en: %APK_OUT%
    pause
    exit /b 1
)

echo.
echo       OK - APK compilado correctamente

REM ── 4. COPIAR APK AL DIRECTORIO FINAL ───────────────────────────────────────
echo.
echo [4/6] Copiando APK...
copy /Y "%APK_OUT%" "%FINAL_APK%" >nul
echo       OK - APK copiado a: %FINAL_APK%

REM ── 5. INSTALAR EN DISPOSITIVO ──────────────────────────────────────────────
echo.
echo [5/6] Instalando APK en dispositivo Android...
"%ADB%" !ADB_FLAGS! install -r "%FINAL_APK%"

if errorlevel 1 (
    color 0E
    echo.
    echo [AVISO] Error al instalar. Intentando con reinstalacion forzada...
    "%ADB%" !ADB_FLAGS! uninstall "%PACKAGE%" >nul 2>&1
    "%ADB%" !ADB_FLAGS! install "%FINAL_APK%"
    if errorlevel 1 (
        color 0C
        echo [ERROR] No se pudo instalar el APK.
        echo Asegurate de que:
        echo   1. El dispositivo esta conectado por USB
        echo   2. La depuracion USB esta habilitada
        echo   3. Aceptaste la solicitud de depuracion USB en el movil
        pause
        exit /b 1
    )
)

echo.
echo       OK - APK instalado exitosamente

REM ── 6. LANZAR APP Y MONITOREAR LOGS ─────────────────────────────────────────
echo.
echo [6/6] Lanzando SmartDisplay AI...
"%ADB%" !ADB_FLAGS! shell am start -n "%PACKAGE%/%ACT%"
if errorlevel 1 (
    echo Intentando lanzar desde MainActivity...
    "%ADB%" !ADB_FLAGS! shell monkey -p "%PACKAGE%" -c android.intent.category.LAUNCHER 1
)

echo.
color 0A
echo ============================================================================
echo   [OK] APK INSTALADO Y LANZADO EN EL DISPOSITIVO
echo ============================================================================
echo.
echo   Paquete:  %PACKAGE%
echo   Version:  12.1 (build 314)
echo.

REM Limpiar logcat y monitorear
echo.
echo ── LOGS EN TIEMPO REAL (filtrando por PID de la app) ──
echo.
"%ADB%" !ADB_FLAGS! logcat -c

REM Esperar un poco para que la app inicie y asigne PID
timeout /t 2 >nul

for /f "tokens=*" %%i in ('"%ADB%" !ADB_FLAGS! shell pidof -s %PACKAGE%') do set APP_PID=%%i

if defined APP_PID (
    echo [INFO] Monitoreando PID: !APP_PID!
    "%ADB%" !ADB_FLAGS! logcat --pid=!APP_PID! -v time
) else (
    echo [AVISO] No se pudo obtener el PID. Mostrando crash dump...
    "%ADB%" !ADB_FLAGS! logcat -b crash -v time
)

cd /d "%PROJECT_DIR%"
