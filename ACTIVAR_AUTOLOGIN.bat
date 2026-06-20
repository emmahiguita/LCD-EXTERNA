@echo off
title SmartDisplay AI - Activar Inicio de Sesion Automatico
cd /d "%~dp0"

:: Auto-elevar a Administrador
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if '%errorlevel%' NEQ '0' (
    echo [*] Solicitando permisos de Administrador...
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\_elev.vbs"
    echo UAC.ShellExecute "%~s0", "", "", "runas", 1 >> "%temp%\_elev.vbs"
    "%temp%\_elev.vbs"
    del "%temp%\_elev.vbs" >nul 2>&1
    exit /B
)

powershell -ExecutionPolicy Bypass -NoProfile -File "%~dp0activar_autologin.ps1"
echo.
pause
