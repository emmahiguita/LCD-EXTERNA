@echo off
REM ============================================================================
REM SMARTDISPLAY WORKSTATION - BUILD & INSTALL (BATCH)
REM Actualiza dependencias, compila APK e instala con ADB
REM ============================================================================

setlocal enabledelayedexpansion

color 0B
title SmartDisplay Build & Install

echo.
echo ============================================================================
echo  SMARTDISPLAY WORKSTATION - BUILD ^& INSTALL SCRIPT
echo ============================================================================
echo.

REM Verificar que estamos en el directorio correcto
if not exist "moonlight-android-master" (
    color 0C
    echo [ERROR] Carpeta moonlight-android-master no encontrada
    echo Ejecuta este script desde: C:\Users\emman\Desktop\Proyectos\LCD EXTERNA
    pause
    exit /b 1
)

REM Verificar ADB
where adb >nul 2>nul
if errorlevel 1 (
    color 0C
    echo [ERROR] ADB no encontrado en PATH
    echo.
    echo Solución: Descargar Android SDK Platform Tools
    echo Descargar desde: https://developer.android.com/studio/releases/platform-tools
    echo.
    echo Luego agregar a PATH:
    echo setx PATH "%%PATH%%;C:\path\to\platform-tools"
    pause
    exit /b 1
)

echo [OK] ADB detectado correctamente
echo.

REM Listar dispositivos
echo [INFO] Verificando dispositivos conectados...
adb devices
echo.

REM Preguntar si continuar
set /p continue="¿Continuar con la compilación e instalación? (s/n): "
if /i not "%continue%"=="s" (
    echo Cancelado por el usuario
    pause
    exit /b 0
)

echo.
echo ============================================================================
echo  PASO 1: ACTUALIZANDO DEPENDENCIAS
echo ============================================================================
echo.

cd moonlight-android-master
call gradlew clean

if errorlevel 1 (
    color 0C
    echo [ERROR] Fallo en gradle clean
    pause
    exit /b 1
)

echo [OK] Dependencias actualizadas
echo.

REM Preguntar qué tipo de build
echo ============================================================================
echo  PASO 2: COMPILANDO APK
echo ============================================================================
echo.
echo 1 = Debug (más rápido, recomendado para desarrollo)
echo 2 = Release (optimizado, más pequeño)
echo.
set /p buildtype="Elige tipo de build (1 o 2): "

if "%buildtype%"=="2" (
    echo Compilando versión RELEASE...
    call gradlew assembleRelease
    set "apk_pattern=app\build\outputs\apk\release\*.apk"
    set "package_suffix="
) else (
    echo Compilando versión DEBUG...
    call gradlew assembleDebug
    set "apk_pattern=app\build\outputs\apk\debug\*.apk"
    set "package_suffix=.debug"
)

if errorlevel 1 (
    color 0C
    echo [ERROR] Fallo en compilación
    pause
    exit /b 1
)

echo [OK] APK compilado exitosamente
echo.

REM Buscar APK
echo ============================================================================
echo  PASO 3: BUSCANDO APK GENERADO
echo ============================================================================
echo.

for /f "delims=" %%f in ('dir /b /s "%apk_pattern%" 2^>nul') do (
    set "apk_file=%%f"
    goto found_apk
)

:found_apk
if not defined apk_file (
    color 0C
    echo [ERROR] No se encontró APK compilado
    pause
    exit /b 1
)

echo [OK] APK encontrado: !apk_file!
echo.

REM Desinstalar versión anterior
echo ============================================================================
echo  PASO 4: DESINSTALANDO VERSIÓN ANTERIOR
echo ============================================================================
echo.

adb uninstall com.limelight.smartdisplay 2>nul
adb uninstall com.limelight.smartdisplay.debug 2>nul

echo [OK] Preparado para instalar
echo.

REM Instalar APK
echo ============================================================================
echo  PASO 5: INSTALANDO APK EN DISPOSITIVO
echo ============================================================================
echo.

adb install -r "!apk_file!"

if errorlevel 1 (
    color 0C
    echo [ERROR] Fallo en instalación del APK
    echo.
    echo Soluciones:
    echo - Verifica que el dispositivo esté conectado: adb devices
    echo - Habilita "Depuración USB" en el dispositivo
    echo - Permite permisos cuando se pida
    pause
    exit /b 1
)

echo [OK] APK instalado exitosamente
echo.

REM Lanzar aplicación
echo ============================================================================
echo  PASO 6: LANZANDO APLICACIÓN
echo ============================================================================
echo.

adb shell am start -n "com.limelight.smartdisplay!buildtype!/.MainActivity"

echo [OK] SmartDisplay iniciado en el dispositivo
echo.

REM Monitoreo opcional
set /p monitor="¿Monitorear logs? (s/n): "
if /i "%monitor%"=="s" (
    echo Monitoreando logs (Ctrl+C para salir)...
    adb logcat | find /i "SmartDisplay"
)

color 0A
echo.
echo ============================================================================
echo  [SUCCESS] ¡PROCESO COMPLETADO!
echo ============================================================================
echo.
echo APK instalado: !apk_file!
echo.
echo Próximos pasos:
echo 1. Abre SmartDisplay en tu dispositivo Android
echo 2. Conéctate a tu PC con Moonlight
echo 3. ¡Disfruta de tu workstation móvil!
echo.

cd ..
pause

