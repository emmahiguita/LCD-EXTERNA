# SmartDisplay AI — Instalador del Companion (un solo comando)
# Deja el companion funcionando y arrancando solo al iniciar sesión.
#   Uso:  powershell -ExecutionPolicy Bypass -File install.ps1
$ErrorActionPreference = "Stop"
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "== SmartDisplay Companion: instalando ==" -ForegroundColor Cyan

# 1. Resolver Python
$py = (Get-Command python.exe -ErrorAction SilentlyContinue).Source
if (-not $py) { $py = (Get-Command py.exe -ErrorAction SilentlyContinue).Source }
if (-not $py) {
    Write-Host "ERROR: Python no esta en el PATH. Instala Python 3.10+ desde python.org." -ForegroundColor Red
    exit 1
}

# 2. Dependencias
Write-Host "Instalando dependencias..."
& $py -m pip install --user -r (Join-Path $dir "requirements.txt")

# 3. pythonw (sin ventana de consola)
$pyw = (Get-Command pythonw.exe -ErrorAction SilentlyContinue).Source
if (-not $pyw) { $pyw = $py }
$script = Join-Path $dir "companion_server.py"

# 4. Auto-arranque al iniciar sesion (acceso directo en Startup, oculto)
$startup = [Environment]::GetFolderPath("Startup")
$lnk = Join-Path $startup "SmartDisplay Companion.lnk"
$ws = New-Object -ComObject WScript.Shell
$sc = $ws.CreateShortcut($lnk)
$sc.TargetPath        = $pyw
$sc.Arguments         = "`"$script`""
$sc.WorkingDirectory  = $dir
$sc.WindowStyle       = 7   # minimizado/oculto
$sc.Description       = "SmartDisplay AI Companion"
$sc.Save()
Write-Host "Auto-arranque configurado: $lnk" -ForegroundColor Green

# 5. Firewall (foco 8765 / bus 47991 / voz 48999)
foreach ($r in @(
    @{n="SmartDisplay Foco"; p="TCP"; port=8765},
    @{n="SmartDisplay Bus";  p="TCP"; port=47991},
    @{n="SmartDisplay Voz";  p="UDP"; port=48999}
)) {
    netsh advfirewall firewall delete rule name=$($r.n) 2>$null | Out-Null
    netsh advfirewall firewall add rule name=$($r.n) dir=in action=allow protocol=$($r.p) localport=$($r.port) | Out-Null
}
Write-Host "Reglas de firewall aplicadas." -ForegroundColor Green

# 6. Arrancar ahora (oculto)
Start-Process -FilePath $pyw -ArgumentList "`"$script`"" -WorkingDirectory $dir -WindowStyle Hidden
Write-Host "== Companion en ejecucion. No necesitas hacer nada mas. ==" -ForegroundColor Cyan
Write-Host "Foco: ws://0.0.0.0:8765  |  Bus/Archivos+Portapapeles: ws://0.0.0.0:47991  |  Voz: udp/48999"
