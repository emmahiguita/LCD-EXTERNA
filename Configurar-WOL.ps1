Write-Host "======================================="
Write-Host " DIAGNOSTICO WAKE ON LAN"
Write-Host "======================================="
Write-Host ""

Write-Host "=== Equipo ==="
Get-CimInstance Win32_ComputerSystem |
Select-Object Manufacturer, Model

Write-Host ""
Write-Host "=== Placa Base ==="
Get-CimInstance Win32_BaseBoard |
Select-Object Manufacturer, Product

Write-Host ""
Write-Host "=== Adaptadores de Red ==="
Get-NetAdapter |
Format-Table Name, InterfaceDescription, Status

Write-Host ""
Write-Host "=== Dispositivos con capacidad de despertar ==="
powercfg -devicequery wake_programmable

Write-Host ""
Write-Host "=== Dispositivos armados para despertar ==="
powercfg -devicequery wake_armed

Write-Host ""
Write-Host "=== Estados de energía soportados ==="
powercfg /a

Write-Host ""
Write-Host "=== Direcciones MAC ==="
Get-NetAdapter |
Select-Object Name, MacAddress

Write-Host ""
Write-Host "=== Propiedades Wake-On-LAN ==="

$adapters = Get-NetAdapter | Where-Object {$_.Status -eq "Up"}

foreach($adapter in $adapters)
{
    Write-Host ""
    Write-Host "Adaptador:" $adapter.Name
    Get-NetAdapterAdvancedProperty -Name $adapter.Name |
    Where-Object {
        $_.DisplayName -match "Wake" -or
        $_.DisplayName -match "Magic" -or
        $_.DisplayName -match "WoL" -or
        $_.DisplayName -match "LAN"
    } |
    Format-Table DisplayName, DisplayValue
}

Write-Host ""
Write-Host "======================================="
Write-Host " FIN DEL DIAGNOSTICO"
Write-Host "======================================="