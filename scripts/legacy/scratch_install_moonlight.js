const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const https = require('https');

const ADB = fs.existsSync('C:\\AndroProject\\adb.exe')
  ? 'C:\\AndroProject\\adb.exe'
  : (fs.existsSync('C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe')
      ? 'C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe'
      : 'adb');

console.log('[ADB] Usando ejecutable:', ADB);

function runCmd(cmd) {
  return new Promise((resolve, reject) => {
    exec(cmd, (err, stdout, stderr) => {
      if (err) reject(err);
      else resolve(stdout.trim());
    });
  });
}

function downloadFile(url, dest) {
  return new Promise((resolve, reject) => {
    https.get(url, (response) => {
      if (response.statusCode === 302 || response.statusCode === 301) {
        downloadFile(response.headers.location, dest).then(resolve).catch(reject);
        return;
      }
      if (response.statusCode !== 200) {
        reject(new Error(`Servidor retornó código: ${response.statusCode}`));
        return;
      }
      const fileStream = fs.createWriteStream(dest);
      response.pipe(fileStream);
      fileStream.on('finish', () => {
        fileStream.close();
        resolve();
      });
    }).on('error', (err) => {
      fs.unlink(dest, () => {});
      reject(err);
    });
  });
}

async function main() {
  try {
    console.log('[1/4] Buscando dispositivos conectados...');
    const devicesOut = await runCmd(`"${ADB}" devices`);
    const lines = devicesOut.split('\n').slice(1);
    const devices = [];
    for (const line of lines) {
      if (line.includes('\tdevice')) {
        devices.push(line.split('\t')[0].trim());
      }
    }

    if (devices.length === 0) {
      console.error('ERROR: No se detectó ningún dispositivo Android conectado y autorizado vía USB.');
      process.exit(1);
    }

    const target = devices[0];
    console.log(`[2/4] Dispositivo encontrado: ${target}. Descargando Moonlight APK...`);

    const apkUrl = 'https://github.com/moonlight-stream/moonlight-android/releases/download/v12.1/app-nonRoot-release.apk';
    const tempDir = path.join(__dirname, 'temp');
    if (!fs.existsSync(tempDir)) fs.mkdirSync(tempDir, { recursive: true });
    const tempApkPath = path.join(tempDir, 'moonlight.apk');

    await downloadFile(apkUrl, tempApkPath);
    console.log('[3/4] APK descargado con éxito. Instalando en el dispositivo...');

    await runCmd(`"${ADB}" -s ${target} install -r "${tempApkPath}"`);
    console.log('[4/4] ¡Instalación completada con éxito! Iniciando la aplicación...');

    await runCmd(`"${ADB}" -s ${target} shell monkey -p com.limelight -c android.intent.category.LAUNCHER 1`);
    console.log('¡Proceso culminado! Revisa la pantalla de tu celular.');
  } catch (e) {
    console.error('ERROR durante la instalación:', e.message);
    process.exit(1);
  }
}

main();
