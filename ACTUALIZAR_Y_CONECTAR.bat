@echo off
title SmartDisplay AI - Actualizar y Conectar
color 0B

:: ── Auto-elevate (solicita Administrador) ──────────────────────────────
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if '%errorlevel%' NEQ '0' (
    echo [*] Solicitando permisos de Administrador...
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\_elev.vbs"
    echo UAC.ShellExecute "%~s0", "", "", "runas", 1 >> "%temp%\_elev.vbs"
    "%temp%\_elev.vbs"
    del "%temp%\_elev.vbs" >nul 2>&1
    exit /B
)
pushd "%~dp0"

echo.
echo ================================================================
echo   SmartDisplay AI - Ciclo Completo: Limpiar + Compilar + Enviar
echo ================================================================
echo.

:: ── 1. Firewall (Tailscale necesita estos puertos abiertos) ────────────
echo [1/5] Configurando Firewall para Tailscale y Sunshine...
netsh advfirewall firewall delete rule name="Sunshine UDP" >nul 2>&1
netsh advfirewall firewall delete rule name="Sunshine TCP" >nul 2>&1
netsh advfirewall firewall add rule name="Sunshine UDP" dir=in protocol=udp localport=5353,47998-48010 action=allow >nul 2>&1
netsh advfirewall firewall add rule name="Sunshine TCP" dir=in protocol=tcp localport=47984,47989,48010,47990 action=allow >nul 2>&1
echo      OK - Firewall configurado.
echo.

:: ── 2. Matar procesos viejos ───────────────────────────────────────────
echo [2/5] Liberando recursos y procesos anteriores...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":3001 "') do ( taskkill /PID %%a /F >nul 2>&1 )
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":3002 "') do ( taskkill /PID %%a /F >nul 2>&1 )
taskkill /F /IM electron.exe >nul 2>&1
echo      OK - Procesos liberados.
echo.

:: ── 3. Compilar APK y limpiar DB del celular ──────────────────────────
echo [3/5] Compilando APK actualizada (esto tarda unos minutos)...
call npm run build:apk
if %errorlevel% NEQ 0 (
    echo [ERROR] La compilacion fallo. Revisa build_log.txt para mas detalles.
    pause
    exit /B 1
)
echo      OK - APK compilada e instalada en el celular.
echo.

:: ── 4. Limpiar datos viejos del celular (borra el PC fantasma) ─────────
echo [4/5] Limpiando base de datos de PCs en el celular...
set ADB=C:\Users\emman\AppData\Local\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb
"%ADB%" shell pm clear com.limelight.smartdisplay.debug >nul 2>&1
if %errorlevel% EQU 0 (
    echo      OK - Base de datos del celular limpiada. Solo EMMA aparecera.
) else (
    echo      AVISO - No se pudo limpiar la DB del celular ^(celular no conectado por USB aun?^)
    echo             Si el SmartDisplay_PC fantasma sigue, conect el USB y ejecuta:
    echo             adb shell pm clear com.limelight.smartdisplay.debug
)
echo.

:: ── 5. Arrancar servidores ─────────────────────────────────────────────
echo [5/5] Iniciando Sunshine y Servidor SmartDisplay AI...
node scripts/scratch_start_sunshine.js
start "SmartDisplay-Electron" /b cmd /c "npm start"
echo      OK - Servidores iniciados.
echo.

echo ================================================================
echo   LISTO - SmartDisplay AI corriendo correctamente.
echo   WebSocket: ws://[TU_IP]:3002
echo   API Token: http://[TU_IP]:3001/api/connect
echo ================================================================
echo.

pause
