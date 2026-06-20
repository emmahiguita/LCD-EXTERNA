# ============================================================================
# SMARTDISPLAY WORKSTATION - BUILD & INSTALL SCRIPT
# Actualiza dependencias, compila APK e instala con ADB
# ============================================================================

# Colores para output
$Green = [System.ConsoleColor]::Green
$Yellow = [System.ConsoleColor]::Yellow
$Red = [System.ConsoleColor]::Red
$Cyan = [System.ConsoleColor]::Cyan

function Write-Status($message, $color = $Green) {
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $message" -ForegroundColor $color
}

function Write-Error-Message($message) {
    Write-Host "[ERROR] $message" -ForegroundColor $Red
}

# ============================================================================
# 1. VERIFICAR DEPENDENCIAS
# ============================================================================
Write-Status "=== VERIFICANDO DEPENDENCIAS ===" $Cyan
Write-Status "Buscando ADB..." $Yellow

# Verificar si ADB está instalado
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Error-Message "ADB no encontrado. Instalando Android SDK Platform Tools..."
    # Descargar e instalar ADB (requiere manual o mediante Android SDK)
    Write-Status "Por favor, descargar desde: https://developer.android.com/studio/releases/platform-tools"
    exit 1
}

Write-Status "✓ ADB encontrado: $($adb.Source)" $Green

# Verificar Gradle
$gradle = Get-Command gradle -ErrorAction SilentlyContinue
if (-not $gradle) {
    Write-Status "Usando Gradle Wrapper (./gradlew)" $Yellow
    $gradleCmd = ".\gradlew"
} else {
    Write-Status "✓ Gradle encontrado: $($gradle.Source)" $Green
    $gradleCmd = "gradle"
}

# ============================================================================
# 2. ACTUALIZAR DEPENDENCIAS
# ============================================================================
Write-Status "=== ACTUALIZANDO DEPENDENCIAS ===" $Cyan
Push-Location "C:\Users\emman\Desktop\Proyectos\LCD EXTERNA\moonlight-android-master"

Write-Status "Ejecutando: $gradleCmd clean" $Yellow
& $gradleCmd clean

if ($LASTEXITCODE -ne 0) {
    Write-Error-Message "Error en gradle clean"
    Pop-Location
    exit 1
}

Write-Status "✓ Dependencias actualizadas" $Green

# ============================================================================
# 3. COMPILAR APK (RELEASE MODE)
# ============================================================================
Write-Status "=== COMPILANDO APK ===" $Cyan
Write-Status "Ejecutando: $gradleCmd build -x test" $Yellow

& $gradleCmd build -x test

if ($LASTEXITCODE -ne 0) {
    Write-Error-Message "Error en compilación de gradle"
    Pop-Location
    exit 1
}

Write-Status "✓ APK compilado exitosamente" $Green

# ============================================================================
# 4. BUSCAR APK GENERADO
# ============================================================================
Write-Status "=== BUSCANDO APK ===" $Cyan

$apkPath = Get-ChildItem -Path "." -Filter "*.apk" -Recurse |
    Where-Object { $_.FullName -match "build.*apk$" } |
    Select-Object -First 1

if (-not $apkPath) {
    Write-Error-Message "No se encontró APK compilado"
    Pop-Location
    exit 1
}

Write-Status "✓ APK encontrado: $($apkPath.FullName)" $Green

# ============================================================================
# 5. CONECTAR DISPOSITIVO ADB
# ============================================================================
Write-Status "=== CONECTANDO DISPOSITIVO ===" $Cyan

# Listar dispositivos conectados
$devices = adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() }

if ($devices.Count -eq 0) {
    Write-Status "No hay dispositivos USB conectados. ¿Conectar por WiFi? (s/n)" $Yellow
    $wifi = Read-Host "¿Conectar por WiFi?"

    if ($wifi -eq "s" -or $wifi -eq "S") {
        $deviceIp = Read-Host "Ingresa IP del dispositivo (ej: 192.168.0.100)"
        Write-Status "Conectando a $deviceIp:5555..." $Yellow
        adb connect "$deviceIp:5555"

        if ($LASTEXITCODE -ne 0) {
            Write-Error-Message "No se pudo conectar por WiFi"
            Pop-Location
            exit 1
        }
    } else {
        Write-Error-Message "Conecta un dispositivo Android por USB"
        Pop-Location
        exit 1
    }
}

Write-Status "✓ Dispositivo conectado" $Green

# ============================================================================
# 6. DESINSTALAR VERSIÓN ANTERIOR (OPCIONAL)
# ============================================================================
Write-Status "=== PREPARANDO INSTALACIÓN ===" $Cyan
Write-Status "Desinstalando versión anterior (si existe)..." $Yellow

adb uninstall com.limelight.smartdisplay 2>$null
adb uninstall com.limelight.smartdisplay.debug 2>$null

Write-Status "✓ Listo para instalar" $Green

# ============================================================================
# 7. INSTALAR APK EN DISPOSITIVO
# ============================================================================
Write-Status "=== INSTALANDO APK ===" $Cyan
Write-Status "Ejecutando: adb install -r $($apkPath.FullName)" $Yellow

adb install -r $apkPath.FullName

if ($LASTEXITCODE -ne 0) {
    Write-Error-Message "Error al instalar APK"
    Pop-Location
    exit 1
}

Write-Status "✓ APK instalado exitosamente" $Green

# ============================================================================
# 8. LANZAR APLICACIÓN
# ============================================================================
Write-Status "=== LANZANDO APLICACIÓN ===" $Cyan
Write-Status "Iniciando SmartDisplay..." $Yellow

adb shell am start -n com.limelight.smartdisplay/.MainActivity

Write-Status "✓ SmartDisplay iniciado en el dispositivo" $Green

Pop-Location

# ============================================================================
# RESUMEN
# ============================================================================
Write-Status "=== PROCESO COMPLETADO ===" $Cyan
Write-Status "APK instalado: $($apkPath.Name)"
Write-Status "Dispositivo: $(adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() } | Select-Object -First 1)"
Write-Status ""
Write-Status "Próximos pasos:" $Yellow
Write-Status "1. Abre la aplicación en tu dispositivo Android"
Write-Status "2. Conéctate a tu PC con Moonlight"
Write-Status "3. ¡Disfruta de la experiencia workstation mobile!"
Write-Status ""
Write-Status "Para monitorear: adb logcat | Select-String 'SmartDisplay'" $Cyan

