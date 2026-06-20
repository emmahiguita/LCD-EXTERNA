const fs = require('fs');
const { execSync } = require('child_process');
const path = require('path');
const os = require('os');

console.log("=========================================");
console.log("   COMPILANDO SIN ERRORES DE NDK (ESPACIOS)");
console.log("=========================================");

const originalProjectDir = path.join(__dirname, 'moonlight-android-master');

// El NDK de Android tiene un bug crítico: Falla si la ruta tiene espacios (ej. "LCD EXTERNA").
// Solución: Copiamos el proyecto a una ruta segura sin espacios (C:\moonlight_build),
// lo compilamos ahí y luego traemos el APK generado de vuelta.

const safeBuildDir = path.join(os.homedir(), 'moonlight_build');

console.log("[*] Copiando el código fuente a una ruta segura sin espacios: " + safeBuildDir);
try {
    if (!fs.existsSync(safeBuildDir)) {
        fs.mkdirSync(safeBuildDir, { recursive: true });
    }
    // Usamos xcopy para copiar todo el proyecto rápidamente
    execSync(`xcopy "${originalProjectDir}" "${safeBuildDir}" /E /I /H /Y /Q`, { stdio: 'ignore' });
} catch (e) {
    console.error("[-] Error copiando archivos.");
    process.exit(1);
}

// 2. Compilar la aplicación en la ruta segura
console.log("\n[*] Compilando el proyecto (esto puede tardar unos minutos)...");
try {
    execSync('gradlew.bat assembleDebug', { cwd: safeBuildDir, stdio: 'inherit' });
    console.log("[+] Compilación exitosa.");
} catch (e) {
    console.error("[-] Error crítico durante la compilación nativa (C++).");
    process.exit(1);
}

// 3. Traer el APK e instalar
console.log("\n[*] Recuperando APK...");
const compiledApkPath = path.join(safeBuildDir, 'app/build/outputs/apk/debug/app-debug.apk');
const localApkPath = path.join(originalProjectDir, 'app-debug.apk');

if (fs.existsSync(compiledApkPath)) {
    fs.copyFileSync(compiledApkPath, localApkPath);
    console.log("[+] APK guardado en: " + localApkPath);

    try {
        console.log("[*] Instalando SmartDisplay AI en el celular...");
        const adbPath = fs.existsSync('C:\\AndroProject\\adb.exe') ? 'C:\\AndroProject\\adb.exe' : 'adb';
        execSync(`"${adbPath}" install -r -d "${localApkPath}"`, { stdio: 'inherit' });
        
        console.log("\n[+] ¡Instalación exitosa! Abriendo la aplicación...");
        execSync(`"${adbPath}" shell monkey -p com.limelight -c android.intent.category.LAUNCHER 1`, { stdio: 'ignore' });
        
        console.log("\n=========================================");
        console.log("   SMARTDISPLAY AI LISTO PARA USAR       ");
        console.log("=========================================");
    } catch (e) {
        console.error("[-] Error al instalar por ADB.");
    }
} else {
    console.error("[-] No se encontró el archivo APK generado.");
}
