@echo off
title Auto-Sincronizacion Garantizada sin Errores

:: Verificar permisos de administrador
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if '%errorlevel%' NEQ '0' (
    echo [*] Solicitando privilegios de administrador para evitar errores 401 en Sunshine...
    goto UACPrompt
) else ( goto gotAdmin )

:UACPrompt
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\getadmin.vbs"
    echo UAC.ShellExecute "cmd.exe", "/c \"""%~s0""" %*", "", "runas", 1 >> "%temp%\getadmin.vbs"
    "%temp%\getadmin.vbs"
    del "%temp%\getadmin.vbs"
    exit /B

:gotAdmin
    pushd "%CD%"
    CD /D "%~dp0"
    
    color 0A
    echo =====================================================================
    echo      SISTEMA DE SINCRONIZACION DEFINITIVA (CERO ERRORES)
    echo =====================================================================
    echo.
    
    echo [1/4] Limpiando procesos de sincronizacion anteriores...
    taskkill /f /im node.exe /fi "WINDOWTITLE eq npm*" >nul 2>&1
    
    echo.
    echo [2/4] Reseteando Sunshine a nivel del sistema para evitar Error 401...
    node scratch_hard_reset.js
    
    echo.
    echo [3/4] Abriendo Moonlight en tu celular Android...
    C:\AndroProject\adb.exe shell monkey -p com.limelight -c android.intent.category.LAUNCHER 1 >nul 2>&1
    if '%errorlevel%' NEQ '0' (
        adb shell monkey -p com.limelight -c android.intent.category.LAUNCHER 1 >nul 2>&1
    )
    
    echo.
    echo [4/4] Iniciando escaneo e inyeccion del PIN invisible...
    echo.
    echo *************************************************************
    echo ¡TOCA EL ICONO DE TU PC EN EL CELULAR AHORA!
    echo *************************************************************
    echo.
    node scratch_auto_sync.js
    
    pause
