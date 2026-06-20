const fs = require('fs');
const { execSync } = require('child_process');
const path = require('path');
const os = require('os');

console.log("=========================================");
console.log("   RECONSTRUCCIÓN DESDE CERO (Fase 1)");
console.log("=========================================");

// El ZIP de GitHub que descargaste viene sin los sub-módulos C++ exactos.
// Al intentar descargar el último, choca con versiones viejas del código en Android.mk.
// La única forma 100% oficial de compilar Moonlight es clonar el repositorio recursivamente.

const buildDir = path.join(os.homedir(), 'moonlight_build');
const repoDir = path.join(buildDir, 'moonlight-android');

// 1. Limpiar e inicializar clonación oficial
console.log("\n[*] Preparando entorno limpio en: " + buildDir);
if (fs.existsSync(repoDir)) {
    console.log("[*] Eliminando clonación anterior...");
    fs.rmSync(repoDir, { recursive: true, force: true });
} else if (!fs.existsSync(buildDir)) {
    fs.mkdirSync(buildDir, { recursive: true });
}

console.log("[*] Descargando código fuente oficial completo (con todos los submódulos)...");
try {
    execSync('git clone --recursive https://github.com/moonlight-stream/moonlight-android.git', { cwd: buildDir, stdio: 'inherit' });
} catch (e) {
    console.error("[-] Error al clonar el repositorio con Git.");
    process.exit(1);
}

// 2. Aplicar las modificaciones de Fase 1 (Branding + Mouse)
console.log("\n[*] Aplicando modificaciones de SmartDisplay AI...");

// 2.1. build.gradle
const gradleFile = path.join(repoDir, 'app/build.gradle');
let gradleContent = fs.readFileSync(gradleFile, 'utf8');
gradleContent = gradleContent.replace(/applicationIdSuffix "\.unofficial"/g, 'applicationIdSuffix ".smartdisplay"');
gradleContent = gradleContent.replace(/applicationIdSuffix "\.debug"/g, 'applicationIdSuffix ".smartdisplay.debug"');
gradleContent = gradleContent.replace(/resValue "string", "app_label", ".*?"/g, 'resValue "string", "app_label", "SmartDisplay AI"');
fs.writeFileSync(gradleFile, gradleContent, 'utf8');

// 2.2. strings.xml
const stringsFile = path.join(repoDir, 'app/src/main/res/values/strings.xml');
let stringsContent = fs.readFileSync(stringsFile, 'utf8');
// Cambiamos textos visibles pero respetamos las URLs de los esquemas XML
stringsContent = stringsContent.replace(/>Moonlight</g, '>SmartDisplay<');
stringsContent = stringsContent.replace(/"Moonlight /g, '"SmartDisplay ');
stringsContent = stringsContent.replace(/ Moonlight /g, ' SmartDisplay ');
fs.writeFileSync(stringsFile, stringsContent, 'utf8');

// 2.3. styles.xml
const stylesFile = path.join(repoDir, 'app/src/main/res/values/styles.xml');
let stylesContent = fs.readFileSync(stylesFile, 'utf8');
if (!stylesContent.includes("colorPrimary")) {
    stylesContent = stylesContent.replace('<style name="AppBaseTheme" parent="android:Theme">', 
        '<style name="AppBaseTheme" parent="android:Theme">\n        <item name="android:colorPrimary">#06b6d4</item>\n        <item name="android:colorPrimaryDark">#0891b2</item>\n        <item name="android:colorAccent">#10b981</item>');
    fs.writeFileSync(stylesFile, stylesContent, 'utf8');
}

// 2.4. preferences.xml (Habilitar On-Screen Controls para teclado y mantener Touchpad para moverse)
const prefFile = path.join(repoDir, 'app/src/main/res/xml/preferences.xml');
if (fs.existsSync(prefFile)) {
    let prefContent = fs.readFileSync(prefFile, 'utf8');
    
    // Moverse como trackpad y botones de navegación del mouse: 
    prefContent = prefContent.replace(/android:key="checkbox_absolute_mouse_mode"([\s\S]*?)android:defaultValue="false"/, 'android:key="checkbox_absolute_mouse_mode"$1android:defaultValue="false"');
    prefContent = prefContent.replace(/android:key="checkbox_touchscreen_trackpad"([\s\S]*?)android:defaultValue="true"/, 'android:key="checkbox_touchscreen_trackpad"$1android:defaultValue="true"');
    prefContent = prefContent.replace(/android:key="checkbox_mouse_nav_buttons"([\s\S]*?)android:defaultValue="false"/, 'android:key="checkbox_mouse_nav_buttons"$1android:defaultValue="true"');
    
    // Controles en pantalla por defecto para abrir teclado
    // En el XML original está: android:defaultValue="false"\n android:key="checkbox_show_onscreen_controls"
    prefContent = prefContent.replace(/android:defaultValue="false"([\s\S]*?)android:key="checkbox_show_onscreen_controls"/, 'android:defaultValue="true"$1android:key="checkbox_show_onscreen_controls"');
    
    fs.writeFileSync(prefFile, prefContent, 'utf8');
}

// 2.5. Forzar configuraciones en Java (ignorar SharedPreferences cacheadas por Android)
const prefJavaFile = path.join(repoDir, 'app/src/main/java/com/limelight/preferences/PreferenceConfiguration.java');
if (fs.existsSync(prefJavaFile)) {
    let javaContent = fs.readFileSync(prefJavaFile, 'utf8');
    javaContent = javaContent.replace(/config\.onscreenController = prefs\.getBoolean[^;]+;/g, 'config.onscreenController = true;');
    javaContent = javaContent.replace(/config\.mouseNavButtons = prefs\.getBoolean[^;]+;/g, 'config.mouseNavButtons = true;');
    javaContent = javaContent.replace(/config\.touchscreenTrackpad = prefs\.getBoolean[^;]+;/g, 'config.touchscreenTrackpad = true;');
    javaContent = javaContent.replace(/config\.absoluteMouseMode = prefs\.getBoolean[^;]+;/g, 'config.absoluteMouseMode = false;');
    fs.writeFileSync(prefJavaFile, javaContent, 'utf8');
}

// 3. Compilar APK
console.log("\n[*] Compilando el nuevo proyecto (esto descargará dependencias de Gradle)...");
try {
    // Definimos el path local del SDK para asegurarnos de que lo encuentre
    const sdkPath = 'C:\\Users\\emman\\AppData\\Local\\Android\\Sdk';
    fs.writeFileSync(path.join(repoDir, 'local.properties'), `sdk.dir=${sdkPath.replace(/\\/g, '\\\\')}\n`);
    
    execSync('gradlew.bat assembleDebug', { cwd: repoDir, stdio: 'inherit' });
    console.log("[+] Compilación exitosa.");
} catch (e) {
    console.error("[-] Error crítico durante la compilación.");
    process.exit(1);
}

// 4. Instalar
console.log("\n[*] Instalando APK en dispositivo...");
const compiledApkPath = path.join(repoDir, 'app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk');
const finalApkPath = path.join(__dirname, 'SmartDisplayAI.apk');

if (fs.existsSync(compiledApkPath)) {
    fs.copyFileSync(compiledApkPath, finalApkPath);
    console.log("[+] APK guardado en: " + finalApkPath);

    try {
        const adbPath = fs.existsSync('C:\\AndroProject\\adb.exe') ? 'C:\\AndroProject\\adb.exe' : 'adb';
        
        // Obtener la lista de dispositivos conectados
        const devicesOutput = execSync(`"${adbPath}" devices`).toString();
        const lines = devicesOutput.split('\n').map(line => line.trim());
        const devices = lines.filter(line => line && !line.startsWith('List') && line.includes('device') && !line.includes('unauthorized') && !line.includes('offline'));
        
        if (devices.length > 0) {
            // Usar el primer dispositivo encontrado
            const firstDevice = devices[0].split('\t')[0];
            console.log(`[+] Múltiples o un dispositivo detectado. Instalando en: ${firstDevice}...`);
            execSync(`"${adbPath}" -s ${firstDevice} install -r "${finalApkPath}"`, { stdio: 'inherit' });
            console.log("\n[+] ¡Instalación exitosa! Abriendo la aplicación...");
            execSync(`"${adbPath}" -s ${firstDevice} shell monkey -p com.limelight.smartdisplay.debug -c android.intent.category.LAUNCHER 1`, { stdio: 'ignore' });
        } else {
            console.error("[-] No se detectaron dispositivos válidos o autorizados para instalar.");
        }
    } catch (e) {
        console.error("[-] Error al instalar por ADB: " + e.message);
    }
} else {
    console.error("[-] No se encontró el APK compilado.");
}
