@echo off
title Restablecedor de Sunshine (Auto-Elevable)

:: Verificar permisos de administrador
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"

if '%errorlevel%' NEQ '0' (
    echo [*] Solicitando privilegios de administrador (UAC)...
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
    
    echo =======================================================
    echo    EJECUTANDO RESTABLECIMIENTO DE SUNSHINE COMO ADMIN
    echo =======================================================
    echo.
    node scratch_hard_reset.js
    echo.
    pause
