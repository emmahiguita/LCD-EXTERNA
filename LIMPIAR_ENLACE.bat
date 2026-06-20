@echo off
title SmartDisplay AI - Limpiar Todo y Enlazar desde Cero
cd /d "%~dp0"

:: Auto-elevate to Administrator
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if '%errorlevel%' NEQ '0' (
    echo [*] Solicitando permisos de Administrador...
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\_elev.vbs"
    echo UAC.ShellExecute "%~s0", "", "", "runas", 1 >> "%temp%\_elev.vbs"
    "%temp%\_elev.vbs"
    del "%temp%\_elev.vbs" >nul 2>&1
    exit /B
)

echo ==============================================================
echo  LIMPIANDO CONEXIONES PARA EMPAREJAMIENTO DESDE CERO
echo ==============================================================
echo.

echo [1/6] Deteniendo Sunshine (procesos y servicios)...
taskkill /F /IM sunshine.exe >nul 2>&1
net stop sunshineservice >nul 2>&1
echo      OK.
echo.

echo [2/6] Eliminando archivo de estado de Sunshine para olvidar dispositivos...
if exist "C:\Program Files\Sunshine\config\sunshine_state.json" (
    del /F /Q "C:\Program Files\Sunshine\config\sunshine_state.json"
    echo      [+] Estado sunshine_state.json eliminado.
) else (
    echo      (No se encontro sunshine_state.json en la ruta por defecto).
)
echo.

echo [3/6] Recreando credenciales de Sunshine (usuario: admin, clave: admin1234)...
if exist "C:\Program Files\Sunshine\sunshine.exe" (
    "C:\Program Files\Sunshine\sunshine.exe" --creds admin admin1234
    echo      [+] Credenciales restablecidas.
) else (
    echo      [!] ERROR: No se encontro sunshine.exe.
)
echo.

echo [4/6] Limpiando la base de datos de Moonlight en tu celular...
set ADB=C:\Users\emman\AppData\Local\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" set ADB=C:\AndroProject\adb.exe
if not exist "%ADB%" set ADB=adb
echo Usando adb en: %ADB%
"%ADB%" shell pm clear com.limelight.smartdisplay.debug >nul 2>&1
if %errorlevel% EQU 0 (
    echo      [+] Base de datos de la app del celular limpiada exitosamente.
) else (
    echo      [!] AVISO: No se pudo limpiar la DB del celular por ADB.
    echo          Asegurate de que el celular este conectado por USB con Depuracion USB activa.
    echo          Si el PC viejo sigue apareciendo en el movil, desinstala y vuelve a instalar la app.
)
echo.

echo [5/6] Iniciando Sunshine de forma limpia...
net start sunshineservice >nul 2>&1
if %errorlevel% NEQ 0 (
    if exist "C:\Program Files\Sunshine\sunshine.exe" (
        start "" "C:\Program Files\Sunshine\sunshine.exe"
        echo      [+] Sunshine ejecutado en segundo plano.
    ) else (
        echo      [!] No se pudo iniciar Sunshine.
    )
) else (
    echo      [+] Servicio de Sunshine iniciado.
)
echo.

echo [6/6] Reiniciando Electron para aplicar cambios...
taskkill /F /IM electron.exe >nul 2>&1
echo.

echo ==============================================================
echo ¡ENLACES Y DATOS VIEJOS LIMPIADOS CON EXITO!
echo.
echo Siguientes pasos:
echo 1. Abre la aplicacion Moonlight en tu celular.
echo 2. En la pantalla del PC, ejecuta "iniciar_todo.bat" para iniciar Electron.
echo 3. En el panel de control de la laptop, presiona "Proyectar Pantalla".
echo 4. El celular mostrara el PIN "9999".
echo 5. Sunshine en la PC te solicitara el PIN. Ingresa 9999 y confirma.
echo ==============================================================
echo.
pause
