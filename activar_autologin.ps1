# ============================================================================
#  SmartDisplay AI - Activar inicio de sesion automatico de Windows
# ----------------------------------------------------------------------------
#  Al encender, el PC entra solo al escritorio (sin escribir contrasena),
#  para que Sunshine capture el escritorio y el celular solo tenga que
#  tocar el PC para proyectar.
#
#  SEGURIDAD: este metodo (AutoAdminLogon) guarda la contrasena en el
#  registro de Windows. Cualquiera con acceso FISICO al PC entrara sin clave.
#  Usalo solo si el PC esta en un lugar de confianza.
# ============================================================================

# Usuario de consola actual (DOMINIO\usuario o EQUIPO\usuario)
$consoleUser = (Get-CimInstance Win32_ComputerSystem).UserName
if ([string]::IsNullOrWhiteSpace($consoleUser)) { $consoleUser = "$env:COMPUTERNAME\$env:USERNAME" }

if ($consoleUser -like "*\*") {
    $domain = $consoleUser.Split('\')[0]
    $user   = $consoleUser.Split('\')[1]
} else {
    $domain = $env:COMPUTERNAME
    $user   = $consoleUser
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Activar inicio de sesion automatico" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ("Usuario detectado : {0}" -f $user)
Write-Host ("Dominio/Equipo    : {0}" -f $domain)
Write-Host ""
Write-Host "ADVERTENCIA: la contrasena se guardara en el registro de Windows." -ForegroundColor Yellow
Write-Host "Solo continua si el PC esta en un lugar de confianza." -ForegroundColor Yellow
Write-Host ""

$confirm = Read-Host "Escribe SI para continuar"
if ($confirm -ne "SI") { Write-Host "Cancelado." -ForegroundColor Red; exit 1 }

$sec = Read-Host -AsSecureString ("Contrasena de Windows de '{0}'" -f $user)
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

if ([string]::IsNullOrEmpty($plain)) { Write-Host "Contrasena vacia. Cancelado." -ForegroundColor Red; exit 1 }

$key = "HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Winlogon"
try {
    Set-ItemProperty -Path $key -Name "AutoAdminLogon"  -Value "1"     -Type String -Force
    Set-ItemProperty -Path $key -Name "DefaultUserName" -Value $user   -Type String -Force
    Set-ItemProperty -Path $key -Name "DefaultDomainName" -Value $domain -Type String -Force
    Set-ItemProperty -Path $key -Name "DefaultPassword" -Value $plain  -Type String -Force
    # Evitar que se quede en la pantalla de bloqueo tras "cambiar de usuario"
    Set-ItemProperty -Path $key -Name "ForceAutoLogon"  -Value "1"     -Type String -Force
    Write-Host ""
    Write-Host "[OK] Inicio de sesion automatico ACTIVADO para '$user'." -ForegroundColor Green
    Write-Host "    Al encender, el PC entrara directo al escritorio." -ForegroundColor Green
}
catch {
    Write-Host "[ERROR] No se pudo escribir el registro: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "    Ejecuta este script como Administrador." -ForegroundColor Red
    exit 1
}
finally {
    $plain = $null
}

Write-Host ""
Write-Host "Para DESACTIVARlo luego: pon AutoAdminLogon en 0 (o ejecuta DESACTIVAR_AUTOLOGIN.bat)." -ForegroundColor DarkGray
