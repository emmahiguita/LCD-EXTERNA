@echo off
title SmartDisplay AI - Conexion Permanente (anti-desconexion)
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

echo ==============================================================
echo   DEJANDO EL PC SIEMPRE ALCANZABLE (salvo apagado)
echo ==============================================================
echo.

echo [1/4] Sunshine arranca AUTOMATICAMENTE al encender...
sc.exe config sunshineservice start= auto >nul 2>&1
if %errorlevel% EQU 0 ( echo      [+] OK ) else ( echo      [!] No se pudo ^(servicio no encontrado?^) )
net start sunshineservice >nul 2>&1
echo.

echo [2/4] Tailscale arranca AUTOMATICAMENTE...
sc.exe config Tailscale start= auto >nul 2>&1
echo      [+] OK
echo.

echo [3/4] Tailscale conectado de forma DESATENDIDA (sin necesidad de iniciar sesion)...
"C:\Program Files\Tailscale\tailscale.exe" up --unattended >nul 2>&1
if %errorlevel% EQU 0 ( echo      [+] OK ) else ( echo      [*] Revisa Tailscale manualmente si falla. )
echo.

echo [4/4] El PC NUNCA se suspende ^(se quedaria invisible aunque encendido^)...
powercfg /change standby-timeout-ac 0
powercfg /change standby-timeout-dc 0
powercfg /change hibernate-timeout-ac 0
powercfg /change hibernate-timeout-dc 0
echo      [+] OK - Suspension e hibernacion desactivadas por inactividad.
echo.

echo ==============================================================
echo  LISTO. El celular reconocera el PC a cualquier distancia
echo  (WiFi o datos moviles) mientras el PC este ENCENDIDO.
echo.
echo  IMPORTANTE - paso manual unico (1 clic en la web):
echo  La clave de Tailscale de este PC expira el 2026-12-15.
echo  Para que NUNCA caduque:
echo   1. Abre https://login.tailscale.com/admin/machines
echo   2. Busca el equipo "emma".
echo   3. Menu (...) -^> "Disable key expiry".
echo ==============================================================
echo.
pause
