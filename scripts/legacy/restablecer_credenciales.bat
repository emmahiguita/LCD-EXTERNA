@echo off
title Restablecer Credenciales de Sunshine
echo =======================================================
echo     RESTABLECIENDO CREDENCIALES DE SUNSHINE
echo =======================================================
echo.

:: Buscar la ruta de instalación de Sunshine
set "SUNSHINE_PATH="
if exist "C:\Program Files\LizardByte\Sunshine\sunshine.exe" (
    set "SUNSHINE_PATH=C:\Program Files\LizardByte\Sunshine"
) else if exist "C:\Program Files\Sunshine\sunshine.exe" (
    set "SUNSHINE_PATH=C:\Program Files\Sunshine"
)

if "%SUNSHINE_PATH%"=="" (
    echo [ERROR] No se encontro la instalacion de Sunshine.
    echo Asegurate de haberlo instalado mediante winget o el instalador oficial.
    pause
    exit /b
)

echo [*] Sunshine detectado en: %SUNSHINE_PATH%
echo [*] Restableciendo credenciales a:
echo     - Usuario: admin
echo     - Contraseña: admin1234
echo.

cd /d "%SUNSHINE_PATH%"
sunshine.exe --creds admin admin1234

echo.
echo =======================================================
echo  ¡CREDENCIALES ACTUALIZADAS CON EXITO!
echo  Usa:
echo  - Usuario: admin
echo  - Contraseña: admin1234
echo  para iniciar sesion en https://localhost:47990
echo =======================================================
pause
