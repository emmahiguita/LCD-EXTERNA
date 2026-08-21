# SmartDisplay AI — Companion como "servicio" (Tarea Programada al iniciar sesion)
# ---------------------------------------------------------------------------------
# Por que Tarea Programada y NO un servicio de Windows:
#   El companion usa portapapeles, control de ventanas (SetForegroundWindow),
#   cursor, audio y bandeja. Un servicio corre en Sesion 0 (sin escritorio) y esas
#   funciones fallarian. Una Tarea al iniciar sesion corre EN TU SESION (con
#   escritorio), arranca sola, se oculta y se reinicia si se cae.
#
# Uso RECOMENDADO (como Administrador, para privilegios altos + control de ventanas):
#   Clic derecho en PowerShell -> "Ejecutar como administrador", luego:
#   powershell -ExecutionPolicy Bypass -File install-service.ps1
#
# Sin administrador tambien funciona: cae a un auto-arranque en Startup (sin
# reinicio-automatico ni privilegios altos, pero arranca en cada login).
#
# Desinstalar:
#   powershell -ExecutionPolicy Bypass -File install-service.ps1 -Uninstall

param([switch]$Uninstall)

$ErrorActionPreference = "Stop"
$dir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$task = "SmartDisplayCompanion"
$startupLnk = Join-Path ([Environment]::GetFolderPath("Startup")) "SmartDisplay Companion.lnk"

if ($Uninstall) {
    Unregister-ScheduledTask -TaskName $task -Confirm:$false -ErrorAction SilentlyContinue
    if (Test-Path $startupLnk) { Remove-Item $startupLnk -Force }
    Write-Host "Companion desinstalado (tarea + auto-arranque)." -ForegroundColor Yellow
    return
}

Write-Host "== SmartDisplay Companion: instalando ==" -ForegroundColor Cyan

# 1. Preferir pythonw + script (usa el codigo ACTUAL, con get_windows/get_stats/power).
#    Si no hay Python, cae al .exe empaquetado (puede estar desactualizado).
$pyw    = (Get-Command pythonw.exe -ErrorAction SilentlyContinue).Source
$script = Join-Path $dir "companion_server.py"
$exe    = Join-Path $dir "dist\companion_server.exe"

if ($pyw -and (Test-Path $script)) {
    $py = (Get-Command python.exe -ErrorAction SilentlyContinue).Source
    if ($py) {
        Write-Host "Instalando dependencias de Python..."
        & $py -m pip install --user -r (Join-Path $dir "requirements.txt") | Out-Null
    }
    $exec = $pyw
    $exeArgs = "`"$script`""
} elseif (Test-Path $exe) {
    Write-Host "Python no encontrado; usando el .exe empaquetado." -ForegroundColor Yellow
    $exec = $exe
    $exeArgs = ""
} else {
    Write-Host "ERROR: no hay ni Python ni companion_server.exe." -ForegroundColor Red
    exit 1
}

# 2. Firewall (foco 8765 / bus 47991 / voz 48999). Requiere admin; si falla, se avisa.
foreach ($r in @(
    @{n="SmartDisplay Foco"; p="TCP"; port=8765},
    @{n="SmartDisplay Bus";  p="TCP"; port=47991},
    @{n="SmartDisplay Voz";  p="UDP"; port=48999}
)) {
    netsh advfirewall firewall delete rule name=$($r.n) 2>$null | Out-Null
    netsh advfirewall firewall add rule name=$($r.n) dir=in action=allow protocol=$($r.p) localport=$($r.port) 2>$null | Out-Null
}

function Set-StartupShortcut {
    $ws = New-Object -ComObject WScript.Shell
    $sc = $ws.CreateShortcut($startupLnk)
    $sc.TargetPath       = $exec
    $sc.Arguments        = $exeArgs
    $sc.WorkingDirectory = $dir
    $sc.WindowStyle      = 7
    $sc.Description      = "SmartDisplay AI Companion"
    $sc.Save()
}

# 3. Intentar la Tarea Programada (robusta: reinicio si cae, privilegios altos).
$taskOk = $false
try {
    $action   = New-ScheduledTaskAction -Execute $exec -Argument $exeArgs -WorkingDirectory $dir
    $trigger  = New-ScheduledTaskTrigger -AtLogOn
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
                    -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) `
                    -ExecutionTimeLimit ([TimeSpan]::Zero) -Hidden
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Highest
    Register-ScheduledTask -TaskName $task -Action $action -Trigger $trigger `
        -Settings $settings -Principal $principal -Force -ErrorAction Stop | Out-Null
    $taskOk = $true
} catch {
    $taskOk = $false
}

if ($taskOk) {
    # Exito: usar SOLO la tarea. Quitar el Startup antiguo para no duplicar.
    if (Test-Path $startupLnk) { Remove-Item $startupLnk -Force }
    Start-ScheduledTask -TaskName $task
    Write-Host "OK: instalado como Tarea Programada (arranca y se reinicia solo)." -ForegroundColor Green
} else {
    # Sin admin: respaldo con acceso directo en Startup (sin reinicio-automatico).
    Set-StartupShortcut
    Start-Process -FilePath $exec -ArgumentList $exeArgs -WorkingDirectory $dir -WindowStyle Hidden
    Write-Host "Auto-arranque configurado en Startup (modo sin admin)." -ForegroundColor Green
    Write-Host "Para la version robusta (reinicio-auto + control de ventanas elevadas)," -ForegroundColor Yellow
    Write-Host "ejecuta este script en una PowerShell ABIERTA COMO ADMINISTRADOR." -ForegroundColor Yellow
}

Write-Host "Foco: ws://0.0.0.0:8765  |  Bus/Ventanas/Stats/Power: ws://0.0.0.0:47991  |  Voz: udp/48999" -ForegroundColor Cyan
