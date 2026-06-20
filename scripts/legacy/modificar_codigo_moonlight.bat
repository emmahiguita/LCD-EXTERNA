@echo off
color 0E
echo =====================================================================
echo          SISTEMA DE DESCARGA Y MODIFICACION DE MOONLIGHT
echo =====================================================================
echo.
echo [1/4] Descargando el codigo fuente oficial de Moonlight Android (puede tardar un poco)...
curl -L -o moonlight.zip https://github.com/moonlight-stream/moonlight-android/archive/refs/heads/master.zip
if %errorlevel% neq 0 (
    echo [ERROR] Fallo la descarga desde GitHub.
    pause
    exit /b %errorlevel%
)

echo [2/4] Extrayendo el codigo fuente...
tar -xf moonlight.zip
if %errorlevel% neq 0 (
    echo [ERROR] Fallo la extraccion del archivo ZIP.
    pause
    exit /b %errorlevel%
)

echo [3/4] Modificando el archivo de generacion de PIN para congelarlo...
node scratch_modify_moonlight.js

echo.
echo [4/4] Proceso finalizado. El codigo modificado esta en la carpeta "moonlight-android-master".
echo.
echo =====================================================================
echo [ATENCION: COMPILACION MANUAL REQUERIDA]
echo Para convertir este codigo fuente en un .apk para tu celular:
echo 1. Abre Android Studio.
echo 2. Ve a File - Open y selecciona la carpeta "moonlight-android-master".
echo 3. Espera a que Android Studio descargue el NDK y dependencias.
echo 4. Ve a Build - Build Bundle(s) / APK(s) - Build APK(s).
echo =====================================================================
pause
