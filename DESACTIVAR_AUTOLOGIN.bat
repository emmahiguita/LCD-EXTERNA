@echo off
title SmartDisplay AI - Desactivar Inicio de Sesion Automatico
cd /d "%~dp0"

:: Auto-elevar a Administrador
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if '%errorlevel%' NEQ '0' (
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\_elev.vbs"
    echo UAC.ShellExecute "%~s0", "", "", "runas", 1 >> "%temp%\_elev.vbs"
    "%temp%\_elev.vbs"
    del "%temp%\_elev.vbs" >nul 2>&1
    exit /B
)

set "K=HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon"
reg add "%K%" /v AutoAdminLogon /t REG_SZ /d 0 /f >nul 2>&1
reg add "%K%" /v ForceAutoLogon /t REG_SZ /d 0 /f >nul 2>&1
reg delete "%K%" /v DefaultPassword /f >nul 2>&1
echo [OK] Inicio de sesion automatico DESACTIVADO. Al encender pedira contrasena.
echo.
pause
