@echo off
title SmartDisplay AI - Lanzador
cd /d "%~dp0"
setlocal enabledelayedexpansion
color 0B

:: ══════════════════════════════════════════════════════════════════════
echo.
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║           SmartDisplay AI — Lanzador de Produccion          ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.

:: ── 1. Firewall Rules (only if Admin) ────────────────────────────────
openfiles >nul 2>&1
if %errorlevel% equ 0 (
    echo [*] Aplicando reglas de Firewall...
    netsh advfirewall firewall delete rule name="Sunshine UDP" >nul 2>&1
    netsh advfirewall firewall delete rule name="Sunshine TCP" >nul 2>&1
    netsh advfirewall firewall add rule name="Sunshine UDP" dir=in protocol=udp localport=5353,47998-48010 action=allow >nul 2>&1
    netsh advfirewall firewall add rule name="Sunshine TCP" dir=in protocol=tcp localport=47984,47989,48010,47990 action=allow >nul 2>&1
    netsh advfirewall firewall add rule name="SmartDisplay WS" dir=in protocol=tcp localport=3001,3002 action=allow >nul 2>&1
    echo     OK - Reglas de Firewall aplicadas.
) else (
    echo [AVISO] Ejecuta como Administrador para aplicar reglas de Firewall automaticamente.
)
echo.

:: ── 2. Kill stale processes & free ports ─────────────────────────────
echo [*] Liberando puertos 3001, 3002 y procesos previos...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":3001 "') do ( taskkill /PID %%a /F >nul 2>&1 )
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":3002 "') do ( taskkill /PID %%a /F >nul 2>&1 )
taskkill /F /IM sunshine.exe >nul 2>&1
taskkill /F /IM electron.exe >nul 2>&1
echo     OK - Puertos y procesos liberados.
echo.

:: ── 3. Start Sunshine (screen capture server) ────────────────────────
echo [*] Iniciando Sunshine (servidor de captura de pantalla)...
node scripts/scratch_start_sunshine.js
echo.

:: ── 4. Start Electron app (WebSocket server + UI) ────────────────────
echo [*] Iniciando SmartDisplay AI (Electron + WebSocket)...
start "SmartDisplay-Electron" /b cmd /c "npm start"
echo     OK - Electron iniciado (servidor WebSocket en puerto 3002, API en 3001).
echo.

:: ── 4b. Start auto-pair daemon (PIN 9999 en bucle) ───────────────────
echo [*] Iniciando demonio de auto-emparejamiento (PIN 9999)...
start "SmartDisplay-AutoPair" /min cmd /c "powershell -ExecutionPolicy Bypass -NoProfile -File \"%~dp0auto_pair_daemon.ps1\""
echo     OK - El emparejamiento del celular se completara solo (sin tocar Sunshine).
echo.

:: ── 5. Wait for servers to come up ───────────────────────────────────
echo [*] Esperando 4 segundos para que los servidores esten listos...
timeout /t 4 >nul
echo     OK - Servidores listos.
echo.

:: ══════════════════════════════════════════════════════════════════════
echo  ╔══════════════════════════════════════════════════════════════╗
echo  ║  LISTO — SmartDisplay AI corriendo en:                      ║
echo  ║    WebSocket: ws://[TU_IP]:3002                             ║
echo  ║    API Token: http://[TU_IP]:3001/api/connect               ║
echo  ╚══════════════════════════════════════════════════════════════╝
echo.

pause
