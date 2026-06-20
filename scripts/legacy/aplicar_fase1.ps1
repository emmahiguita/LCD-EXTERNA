# Script para aplicar Fase 1: Branding y Tematizacion de SmartDisplay AI
$ErrorActionPreference = "Stop"
$repoDir = "C:\Users\emman\Desktop\Proyectos\LCD EXTERNA\moonlight-android-master"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " INICIANDO FASE 1: BRANDING SMARTDISPLAY AI" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Modificar el build.gradle (Nombre y sufijo de aplicacion)
$gradleFile = "$repoDir\app\build.gradle"
if (Test-Path $gradleFile) {
    Write-Host "[1/3] Actualizando nombre y paquete en build.gradle..." -ForegroundColor Yellow
    $gradleContent = Get-Content $gradleFile
    
    # Cambiar sufijo para evitar conflictos con Moonlight oficial
    $gradleContent = $gradleContent -replace 'applicationIdSuffix ".unofficial"', 'applicationIdSuffix ".smartdisplay"'
    $gradleContent = $gradleContent -replace 'applicationIdSuffix ".debug"', 'applicationIdSuffix ".smartdisplay.debug"'
    
    # Asegurar nombre correcto
    $gradleContent = $gradleContent -replace 'resValue "string", "app_label", ".*?"', 'resValue "string", "app_label", "SmartDisplay AI"'
    
    Set-Content -Path $gradleFile -Value $gradleContent -Encoding UTF8
    Write-Host "  -> build.gradle actualizado con exito." -ForegroundColor Green
}

# 2. Modificar cadenas de texto (strings.xml)
$stringsFile = "$repoDir\app\src\main\res\values\strings.xml"
if (Test-Path $stringsFile) {
    Write-Host "[2/3] Reemplazando referencias de Moonlight en strings.xml..." -ForegroundColor Yellow
    $stringsContent = Get-Content $stringsFile
    
    $stringsContent = $stringsContent -replace 'Moonlight', 'SmartDisplay'
    $stringsContent = $stringsContent -replace 'moonlight', 'smartdisplay'
    
    Set-Content -Path $stringsFile -Value $stringsContent -Encoding UTF8
    Write-Host "  -> Textos reemplazados con exito." -ForegroundColor Green
}

# 3. Aplicar colores personalizados en styles.xml
$stylesFile = "$repoDir\app\src\main\res\values\styles.xml"
if (Test-Path $stylesFile) {
    Write-Host "[3/3] Aplicando paleta de colores SmartDisplay (Cyan/Emerald)..." -ForegroundColor Yellow
    $stylesContent = Get-Content $stylesFile
    
    # Inyectar colores primarios si no existen
    if (-not ($stylesContent -match "colorPrimary")) {
        $stylesContent = $stylesContent -replace '<style name="AppBaseTheme" parent="android:Theme">', 
            "<style name=`"AppBaseTheme`" parent=`"android:Theme`">`n        <item name=`"android:colorPrimary`">#06b6d4</item>`n        <item name=`"android:colorPrimaryDark`">#0891b2</item>`n        <item name=`"android:colorAccent`">#10b981</item>"
        
        Set-Content -Path $stylesFile -Value $stylesContent -Encoding UTF8
        Write-Host "  -> Colores Cyan y Emerald aplicados." -ForegroundColor Green
    } else {
        Write-Host "  -> Los colores ya estaban definidos." -ForegroundColor Gray
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " FASE 1 COMPLETADA." -ForegroundColor Cyan
Write-Host " Ahora puedes compilar la app usando: .\gradlew.bat assembleDebug" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
