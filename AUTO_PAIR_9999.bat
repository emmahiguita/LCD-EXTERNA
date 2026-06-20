@echo off
title SmartDisplay AI - Auto Emparejamiento (PIN 9999)
cd /d "%~dp0"
echo.
echo  Iniciando demonio de auto-emparejamiento (PIN 9999)...
echo  Deja esta ventana abierta mientras conectas el celular.
echo.
powershell -ExecutionPolicy Bypass -NoProfile -File "%~dp0auto_pair_daemon.ps1"
pause
