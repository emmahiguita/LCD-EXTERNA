const fs = require('fs');
const { execSync } = require('child_process');
const path = require('path');
const os = require('os');

console.log("=========================================");
console.log("   COMPILANDO ACTUALIZACIÓN (Fase 3)");
console.log("=========================================");

const repoDir = path.join(os.homedir(), 'moonlight_build', 'moonlight-android');
const sourceDir = path.join(__dirname, '..', 'moonlight-android-master');

// 0. Copiar archivos modificados al repositorio completo sin borrar submódulos
console.log("\n[*] Preparando entorno de compilación (sincronizando cambios de Java)...");
try {
    if (!fs.existsSync(repoDir)) {
        console.error("[-] Error: No se encontró la carpeta del repositorio completo: " + repoDir);
        process.exit(1);
    }
    // Copiado recursivo usando xcopy para sobreescribir y fusionar archivos (sin borrar existentes como submódulos)
    execSync(`xcopy /E /I /Y "${sourceDir}" "${repoDir}"`, { stdio: 'inherit' });
} catch (e) {
    console.error("[-] Error copiando archivos al repositorio completo.");
    process.exit(1);
}

// 1. Compilar APK
console.log("\n[*] Compilando el nuevo proyecto con controles flotantes...");
try {
    const sdkPath = 'C:\\Users\\emman\\AppData\\Local\\Android\\Sdk';
    fs.writeFileSync(path.join(repoDir, 'local.properties'), `sdk.dir=${sdkPath.replace(/\\/g, '\\\\')}\n`);
    
    const logFile = path.join(__dirname, '..', 'build_log.txt');
    const result = require('child_process').spawnSync(
        'gradlew.bat', ['assembleNonRootDebug', '--info'],
        { cwd: repoDir, encoding: 'utf8', shell: true, maxBuffer: 50 * 1024 * 1024 }
    );
    const fullOutput = (result.stdout || '') + (result.stderr || '');
    fs.writeFileSync(logFile, fullOutput);
    if (result.status !== 0) {
        // Print last 100 lines for console
        const lines = fullOutput.split('\n');
        console.log(lines.slice(-100).join('\n'));
        console.error("[-] Error crítico durante la compilación. Log completo en: " + logFile);
        process.exit(1);
    }
    console.log("[+] Compilación exitosa.");
} catch (e) {
    console.error("[-] Error crítico durante la compilación.");
    process.exit(1);
}

// 2. Instalar
console.log("\n[*] Instalando APK en dispositivo...");
const compiledApkPath = path.join(repoDir, 'app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk');
const finalApkPath = path.join(__dirname, '..', 'SmartDisplayAI.apk');

if (fs.existsSync(compiledApkPath)) {
    fs.copyFileSync(compiledApkPath, finalApkPath);
    console.log("[+] APK guardado en: " + finalApkPath);

    try {
        const adbPath = fs.existsSync('C:\\AndroProject\\adb.exe')
            ? 'C:\\AndroProject\\adb.exe'
            : (fs.existsSync('C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe')
                ? 'C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe'
                : 'adb');
        
        let targetDevice = "-d"; // Default fallback
        try {
            const devicesList = require('child_process').execSync(`"${adbPath}" devices`).toString();
            // Buscar el primer dispositivo que no sea emulator o network si es posible, o simplemente el primero conectado
            const match = devicesList.match(/^([A-Za-z0-9]+)\s+device$/m);
            if (match) {
                targetDevice = `-s ${match[1]}`;
            } else {
                // Fallback si solo encuentra IP
                const matchIp = devicesList.match(/^([0-9\.:]+)\s+device$/m);
                if (matchIp) targetDevice = `-s ${matchIp[1]}`;
            }
        } catch (e) {}

        execSync(`"${adbPath}" ${targetDevice} install -r "${finalApkPath}"`, { stdio: 'inherit' });
        console.log("\n[+] ¡Instalación exitosa! Abriendo la aplicación...");
        execSync(`"${adbPath}" ${targetDevice} shell monkey -p com.limelight.smartdisplay.debug -c android.intent.category.LAUNCHER 1`, { stdio: 'ignore' });
    } catch (e) {
        console.error("[-] Error al abrir la app por ADB. Asegúrate de tener el celular conectado.");
    }
} else {
    console.error("[-] No se encontró el APK compilado.");
}
