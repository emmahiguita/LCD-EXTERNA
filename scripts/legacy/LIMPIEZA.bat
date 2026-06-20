@echo off
title Limpieza de Scripts Antiguos
color 0A

echo ==============================================================
echo   LIMPIANDO ARCHIVOS INNECESARIOS (Organizando la raiz...)
echo ==============================================================
echo.

if not exist "Scripts_Antiguos_Respaldo" (
    mkdir "Scripts_Antiguos_Respaldo"
)

:: Mover todos los .bat, .ps1 y .js sobrantes
move /Y aplicar_fase1.ps1 Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y build-and-install.bat Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y build-and-install.ps1 Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y compilar_apk.bat Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y configurar_modo_proyeccion.bat Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y copy-assets.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y fix_and_install.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y fix_bom.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y iniciar_proyecto.bat Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y instalar_pantalla_virtual.bat Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y instalar_rapido.bat Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y instalar_y_sincronizar_todo.bat Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y modificar_codigo_moonlight.bat Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y restablecer_credenciales.bat Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y restablecer_credenciales_admin.bat Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y SINCRONIZAR_SIN_ERRORES.bat Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y COMPILAR_E_INSTALAR.bat Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y test_apk.bat Scripts_Antiguos_Respaldo\ >nul 2>&1

:: Mover todos los scratch_ exceptuando scratch_start_sunshine.js
move /Y scratch_auto_sync.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y scratch_debug_sunshine.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y scratch_hard_reset.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y scratch_install_moonlight.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y scratch_modify_moonlight.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y scratch_pair.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y scratch_read_log.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y scratch_read_state.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y scratch_reset_creds.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y scratch_restart_sunshine.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y scratch_setup_projection.js Scripts_Antiguos_Respaldo\ >nul 2>&1

move /Y stress_test.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y test_connection.js Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y clone_and_build.js Scripts_Antiguos_Respaldo\ >nul 2>&1

:: Eliminar los logs gigantes de electron o moverlos
move /Y electron_debug.err Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y electron_debug.log Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y electron_debug.txt Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y electron_startup.log Scripts_Antiguos_Respaldo\ >nul 2>&1
move /Y electron_startup.log.err Scripts_Antiguos_Respaldo\ >nul 2>&1

echo [OK] Tu carpeta ha sido limpiada exitosamente.
echo Se guardaron todos los archivos viejos en la carpeta "Scripts_Antiguos_Respaldo" por si acaso.
echo.
echo Los unicos scripts principales que te quedan en la raiz son:
echo - ACTUALIZAR_Y_CONECTAR.bat (El script maestro)
echo - iniciar_todo.bat (El script secundario)
echo.

pause
del "%~f0"
