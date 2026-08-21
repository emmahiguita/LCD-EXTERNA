# Activar Wake-on-Wireless (WoWLAN) en el adaptador Wi-Fi Intel
# Ejecutar como Administrador

Write-Host "=======================================" -ForegroundColor Cyan
Write-Host " ACTIVAR WAKE-ON-WIRELESS (WoWLAN)" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host ""

# 1. Activar DeviceSleepOnDisconnect para WoWLAN
Write-Host "[1/3] Activando WoWLAN (DeviceSleepOnDisconnect)..."
Set-NetAdapterAdvancedProperty -Name "Wi-Fi" -DisplayName "Suspensión al desconectar WoWLAN" -DisplayValue "Activado"
Write-Host "  ✓ WoWLAN activado" -ForegroundColor Green

# 2. Armar el dispositivo Intel Wi-Fi para wake
Write-Host "[2/3] Armando Intel Wi-Fi para despertar..."
powercfg -deviceenablewake "Intel(R) Wi-Fi 6 AX203"
Write-Host "  ✓ Intel Wi-Fi armado" -ForegroundColor Green

# 3. Verificar resultado
Write-Host "[3/3] Verificando configuración..."
Write-Host ""
Write-Host "Dispositivos armados para despertar:" -ForegroundColor Yellow
powercfg -devicequery wake_armed

Write-Host ""
Write-Host "Propiedades WoWLAN del Wi-Fi:" -ForegroundColor Yellow
Get-NetAdapterAdvancedProperty -Name "Wi-Fi" | Where-Object {
    $_.DisplayName -match "Wake|Magic|WoWLAN|Pattern|Suspensión"
} | Format-Table DisplayName, DisplayValue -AutoSize

Write-Host ""
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host " CONFIGURACIÓN COMPLETADA" -ForegroundColor Green
Write-Host "=======================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Ahora podés:"
Write-Host "  1. Suspender la PC desde el móvil"
Write-Host "  2. Despertarla con el botón 'Encender (WOL)'"
Write-Host ""
Write-Host "IMPORTANTE: La PC debe estar en la misma red Wi-Fi que el móvil,"
Write-Host "o conectada vía Tailscale/VPN para que WOL funcione."
Write-Host ""
pause
