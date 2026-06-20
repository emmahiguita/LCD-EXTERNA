@echo off
:: Creado para SmartDisplay AI - Configura Sunshine para el Modo Proyección
title Configurar Sunshine Modo Proyeccion - SmartDisplay AI

:: Verificar privilegios de administrador
IF "%PROCESSOR_ARCHITECTURE%" EQU "amd64" (
    >nul 2>&1 "%SYSTEMROOT%\SysWOW64\cacls.exe" "%SYSTEMROOT%\SysWOW64\config\system"
) ELSE (
    >nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
)

if %errorlevel% NEQ 0 (
    echo [*] Solicitando privilegios de administrador...
    powershell -Command "Start-Process -FilePath '%0' -Verb RunAs"
    exit /b
)

cd /d "%~dp0"
echo =======================================================
echo          CONFIGURANDO SUNSHINE PARA MODO PROYECCION
echo =======================================================
echo.

if exist "scratch_setup_projection.js" (
    echo [*] Ejecutando configuracion de Sunshine...
    node scratch_setup_projection.js
) else (
    echo [ERROR] No se encontro scratch_setup_projection.js en el directorio actual.
)

echo.
echo =======================================================
echo                 CONFIGURACION COMPLETADA
echo =======================================================
echo.
pause
exit /b
