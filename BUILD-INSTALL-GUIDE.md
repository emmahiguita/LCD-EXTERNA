# 🚀 SmartDisplay Workstation - Guía Build & Install

## Opción 1: Script PowerShell Automático (⭐ RECOMENDADO)

```powershell
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process -Force
& "C:\Users\emman\Desktop\Proyectos\LCD EXTERNA\build-and-install.ps1"
```

**Ventajas:**
- ✅ Automático y sin errores
- ✅ Manejo de excepciones completo
- ✅ Muestra progreso en tiempo real
- ✅ Colorizado para fácil lectura

---

## Opción 2: Script Batch (Doble click)

Simplemente haz **doble click** en:
```
C:\Users\emman\Desktop\Proyectos\LCD EXTERNA\build-and-install.bat
```

**Ventajas:**
- ✅ No requiere PowerShell
- ✅ Interfaz interactiva
- ✅ Opción de debug o release

---

## Opción 3: Comandos Manuales (Paso a Paso)

### 1. Abre PowerShell en la carpeta del proyecto

```powershell
cd "C:\Users\emman\Desktop\Proyectos\LCD EXTERNA\moonlight-android-master"
```

### 2. Actualizar dependencias

```powershell
.\gradlew clean
```

### 3. Compilar APK

**Debug (más rápido):**
```powershell
.\gradlew assembleDebug
```

**Release (optimizado):**
```powershell
.\gradlew assembleRelease
```

### 4. Conectar dispositivo por USB o WiFi

**USB:**
```powershell
adb devices
```

**WiFi:**
```powershell
adb connect 192.168.0.100:5555
# Reemplaza la IP con la de tu dispositivo
```

### 5. Desinstalar versión anterior

```powershell
adb uninstall com.limelight.smartdisplay
adb uninstall com.limelight.smartdisplay.debug
```

### 6. Instalar APK

**Debug:**
```powershell
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
```

**Release:**
```powershell
adb install -r "app\build\outputs\apk\release\app-release.apk"
```

### 7. Lanzar aplicación

```powershell
adb shell am start -n com.limelight.smartdisplay/.MainActivity
```

### 8. Ver logs (opcional)

```powershell
adb logcat | Select-String "SmartDisplay"
# Presiona Ctrl+C para salir
```

---

## 🔧 Verificaciones Previas

### ¿Está ADB instalado?

```powershell
adb version
```

Si no funciona:
1. Descargar: https://developer.android.com/studio/releases/platform-tools
2. Agregar a PATH:
```powershell
setx PATH "$env:PATH;C:\ruta\a\platform-tools"
```
3. Reiniciar PowerShell

### ¿Dispositivo conectado?

```powershell
adb devices
```

Debe mostrar tu dispositivo con estado `device`:
```
List of attached devices
VGL7MVFMDYQG8T55        device
```

### Habilitar Depuración USB

1. Abre **Ajustes** en tu Android
2. Ve a **Acerca del teléfono**
3. Toca **Compilación** 7 veces (hasta que aparezca el menú de desarrollador)
4. Regresa y entra a **Opciones para desarrolladores**
5. Habilita **Depuración USB**
6. Conecta por USB y confirma el diálogo

---

## 📊 Archivos Generados

Después de compilar, encontrarás el APK en:

**Debug:**
```
moonlight-android-master/app/build/outputs/apk/debug/app-debug.apk
```

**Release:**
```
moonlight-android-master/app/build/outputs/apk/release/app-release.apk
```

---

## ⚡ One-Liners (Copiar y Pegar)

### Build + Install (Debug)
```powershell
cd "C:\Users\emman\Desktop\Proyectos\LCD EXTERNA\moonlight-android-master"; .\gradlew clean assembleDebug; adb uninstall com.limelight.smartdisplay.debug 2>$null; adb install -r "app\build\outputs\apk\debug\app-debug.apk"; adb shell am start -n com.limelight.smartdisplay/.MainActivity
```

### Build + Install (Release)
```powershell
cd "C:\Users\emman\Desktop\Proyectos\LCD EXTERNA\moonlight-android-master"; .\gradlew clean assembleRelease; adb uninstall com.limelight.smartdisplay 2>$null; adb install -r "app\build\outputs\apk\release\app-release.apk"; adb shell am start -n com.limelight.smartdisplay/.MainActivity
```

### Solo Instalar (sin compilar)
```powershell
adb uninstall com.limelight.smartdisplay.debug 2>$null; adb install -r "C:\Users\emman\Desktop\Proyectos\LCD EXTERNA\moonlight-android-master\app\build\outputs\apk\debug\app-debug.apk"
```

---

## 🐛 Troubleshooting

| Problema | Solución |
|----------|----------|
| **ADB no encontrado** | Descargar Android SDK Platform Tools y agregar a PATH |
| **Dispositivo no aparece** | Habilitar Depuración USB y reconectar |
| **Error de compilación** | Ejecutar `.\gradlew clean` y reintentar |
| **APK no se instala** | Asegurar espacio en el dispositivo y desinstalar versión anterior |
| **Permiso denegado** | Permitir "Depuración USB" en el diálogo del dispositivo |

---

## 📝 Variables de Entorno (Opcional)

Agregar a `$PROFILE` de PowerShell:

```powershell
$PROJECT = "C:\Users\emman\Desktop\Proyectos\LCD EXTERNA"
$ANDROID = "$PROJECT\moonlight-android-master"
$PACKAGE = "com.limelight.smartdisplay"

function build-debug {
    cd $ANDROID
    .\gradlew clean assembleDebug
    adb uninstall "$PACKAGE.debug" 2>$null
    adb install -r "app\build\outputs\apk\debug\app-debug.apk"
    adb shell am start -n "$PACKAGE/.MainActivity"
}

function build-release {
    cd $ANDROID
    .\gradlew clean assembleRelease
    adb uninstall $PACKAGE 2>$null
    adb install -r "app\build\outputs\apk\release\app-release.apk"
    adb shell am start -n "$PACKAGE/.MainActivity"
}
```

Luego solo necesitas ejecutar:
```powershell
build-debug
# o
build-release
```

---

## ✅ Checklist Final

- [ ] ADB está instalado y en PATH
- [ ] Dispositivo está conectado por USB o WiFi
- [ ] Depuración USB está habilitada
- [ ] Espacio suficiente en el dispositivo
- [ ] Gradle está descargado (ejecuta `.\gradlew --version`)
- [ ] Android SDK está instalado

---

**¡Listo! Ahora tienes SmartDisplay en tu dispositivo Android.** 🎉

