# ============================================================================
#  SmartDisplay AI - Demonio de Auto-Emparejamiento (PIN fijo 9999)
# ----------------------------------------------------------------------------
#  Envia el PIN 9999 a Sunshine en bucle. Cuando el celular inicia el pairing
#  (auto-pairing de la app), Sunshine lo completa al instante: sin entrar a la
#  web de Sunshine y sin tener que acertar el momento.
#
#  Sunshine solo crea un dispositivo nuevo cuando hay un cliente esperando, asi
#  que enviar 9999 cuando no hay nadie emparejando es inofensivo (no-op).
#
#  Uso:  powershell -ExecutionPolicy Bypass -File auto_pair_daemon.ps1
#        (o doble clic en AUTO_PAIR_9999.bat)
# ============================================================================

param(
    [string]$Pin   = "9999",
    [string]$Host_ = "localhost",
    [int]   $Port  = 47990,
    [string]$User  = "admin",
    [string]$Pass  = "admin1234",
    [int]   $IntervalSec = 2
)

# Aceptar el certificado autofirmado de Sunshine
[Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

$auth    = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("$User`:$Pass"))
$uri     = "https://$Host_`:$Port/api/pin"
$body    = "{`"pin`":`"$Pin`",`"name`":`"SmartDisplay`"}"
$stateFile = "C:\Program Files\Sunshine\config\sunshine_state.json"

function Get-PairedCount {
    try {
        $json = Get-Content $stateFile -Raw -ErrorAction Stop | ConvertFrom-Json
        return @($json.root.named_devices).Count
    } catch { return -1 }
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  AUTO-PAIR 9999 activo. Deja esta ventana abierta." -ForegroundColor Cyan
Write-Host "  En el celular: abre la app y toca tu PC para conectar." -ForegroundColor Cyan
Write-Host "  El emparejamiento se completara solo (PIN $Pin)." -ForegroundColor Cyan
Write-Host "  Ctrl+C para detener." -ForegroundColor DarkGray
Write-Host "============================================================" -ForegroundColor Cyan

$prevCount = Get-PairedCount
$tick = 0

while ($true) {
    try {
        $r = Invoke-WebRequest -Uri $uri -Headers @{Authorization=$auth} -Method POST `
                 -ContentType 'application/json' -Body $body -TimeoutSec 8 -UseBasicParsing -ErrorAction Stop

        $now = Get-PairedCount
        if ($now -gt $prevCount -and $prevCount -ge 0) {
            Write-Host ("[{0}] EMPAREJADO! Dispositivos: {1}. Ya puedes proyectar." -f (Get-Date -Format HH:mm:ss), $now) -ForegroundColor Green
            $prevCount = $now
        }
        else {
            $tick++
            if ($tick % 15 -eq 0) {
                Write-Host ("[{0}] Esperando que el celular inicie la conexion... (vivo)" -f (Get-Date -Format HH:mm:ss)) -ForegroundColor DarkGray
            }
        }
    }
    catch {
        Write-Host ("[{0}] Sunshine no responde en {1}. Reintentando..." -f (Get-Date -Format HH:mm:ss), $uri) -ForegroundColor Yellow
        Start-Sleep -Seconds 3
    }
    Start-Sleep -Seconds $IntervalSec
}
