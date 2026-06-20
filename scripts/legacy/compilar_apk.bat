@echo off
title Compilar APK SmartDisplay AI
color 0B
echo =====================================================================
echo          COMPILADOR DE APK SMARTDISPLAY AI (FASE 3)
echo =====================================================================
echo.
echo Este script realizara las siguientes operaciones:
echo 1. Sincronizara tus archivos modificados (PcView.java, etc.) en
echo    la carpeta de compilacion de Moonlight.
echo 2. Compilara el codigo C++ nativo y Java usando Gradle.
echo 3. Generara SmartDisplayAI.apk y lo instalara en tu celular.
echo.
echo Asegurate de tener tu dispositivo Android conectado por USB
echo con la depuracion USB habilitada.
echo.
echo.
echo [*] Iniciando compilacion...
node build_only.js
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Hubo un problema durante la compilacion.
    exit /b %errorlevel%
)
echo.
echo [EXITO] Compilacion e instalacion completadas.
