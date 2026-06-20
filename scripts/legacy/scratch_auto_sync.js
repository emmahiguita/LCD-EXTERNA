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

const basicAuth = 'Basic ' + Buffer.from('admin:admin1234').toString('base64');
let isPairing = false;
let lastPin = null;

function runCmd(cmd) {
  return new Promise((resolve, reject) => {
    exec(cmd, { maxBuffer: 1024 * 1024 * 10 }, (err, stdout, stderr) => {
      // Ignoramos err a veces porque uiautomator dump devuelve error si no hay cambios, pero stdout tiene datos
      resolve(stdout.trim());
    });
  });
}

async function pairWithSunshine(pin) {
  if (isPairing) return;
  isPairing = true;
  console.log(`\n[*] Iniciando emparejamiento INVISIBLE para el PIN detectado: ${pin}...`);

  return new Promise((resolve) => {
    // 1. Intentar obtener el Token CSRF de forma segura
    const csrfOptions = {
      hostname: 'localhost',
      port: 47990,
      path: '/api/csrf-token',
      method: 'GET',
      rejectUnauthorized: false,
      headers: {
        'Authorization': basicAuth
      }
    };

    const csrfReq = https.request(csrfOptions, (csrfRes) => {
      let bodyData = '';
      csrfRes.on('data', (chunk) => bodyData += chunk);
      csrfRes.on('end', () => {
        let csrfToken = '';
        if (csrfRes.statusCode === 200) {
          try {
            const json = JSON.parse(bodyData);
            csrfToken = json.token || json.csrfToken || '';
          } catch (e) {
            if (csrfRes.headers['x-csrf-token']) {
              csrfToken = csrfRes.headers['x-csrf-token'];
            }
          }
        }
        
        // 2. Realizar la peticion de emparejamiento con el PIN
        const postData = JSON.stringify({ pin });
        const postOptions = {
          hostname: 'localhost',
          port: 47990,
          path: '/api/pin',
          method: 'POST',
          rejectUnauthorized: false,
          headers: {
            'Content-Type': 'application/json',
            'Content-Length': postData.length,
            'Authorization': basicAuth
          }
        };

        if (csrfToken) {
          postOptions.headers['X-CSRF-Token'] = csrfToken;
        }

        const postReq = https.request(postOptions, (postRes) => {
          let responseBody = '';
          postRes.on('data', (chunk) => responseBody += chunk);
          postRes.on('end', () => {
            if (postRes.statusCode >= 200 && postRes.statusCode < 300) {
              console.log('\n=======================================================');
              console.log(` [EXITO] ¡PIN ${pin} EMPAREJADO INVISIBLEMENTE!`);
              console.log(' Las aplicaciones ya estan sincronizadas de forma automatica.');
              console.log('=======================================================');
              process.exit(0);
            } else {
              console.error(`\n[ERROR] Sunshine rechazo el PIN (${postRes.statusCode}). Verifica las credenciales.`);
              isPairing = false; // Permitir reintento
              resolve();
            }
          });
        });

        postReq.on('error', (err) => {
          console.error('\n[ERROR POST PIN]:', err.message);
          isPairing = false;
          resolve();
        });

        postReq.write(postData);
        postReq.end();
      });
    });

    csrfReq.on('error', (err) => {
      console.error('[!] Error obteniendo CSRF, el servidor de Sunshine podria estar apagado:', err.message);
      isPairing = false;
      resolve();
    });

    csrfReq.end();
  });
}

async function extractPinFromScreen() {
  try {
    // Volcar la estructura de la interfaz a un archivo XML y leerla
    await runCmd(`"${ADB}" shell uiautomator dump /data/local/tmp/window_dump.xml`);
    const xmlData = await runCmd(`"${ADB}" shell cat /data/local/tmp/window_dump.xml`);
    
    // Buscar patrones comunes en el dialogo de Moonlight
    // Normalmente Moonlight muestra algo como "Por favor, introduzca el siguiente PIN en la PC destino: 1234"
    // O simplemente "PIN: 1234" o un cuadro de texto con 4 digitos.
    
    const pinRegex = /(?:PIN|codigo|código).*?\b(\d{4})\b/i;
    const directPinRegex = /\b(\d{4})\b/; // Solo 4 digitos en un bloque de texto
    
    // Extraer todos los valores "text" de la pantalla usando regex simple
    const textAttributesRegex = /text="([^"]+)"/g;
    let match;
    let possiblePins = [];

    while ((match = textAttributesRegex.exec(xmlData)) !== null) {
      const textNode = match[1];
      
      // Buscar el PIN en el texto del nodo
      let pinMatch = textNode.match(pinRegex);
      if (pinMatch) {
        return pinMatch[1];
      }
      
      // Como alternativa, buscar si el nodo en si es exactamente de 4 digitos o contiene solo 4 digitos
      if (/^\d{4}$/.test(textNode.trim())) {
        possiblePins.push(textNode.trim());
      } else {
        let dpMatch = textNode.match(directPinRegex);
        if (dpMatch) possiblePins.push(dpMatch[1]);
      }
    }

    if (possiblePins.length > 0) {
      // Retornar el primer PIN posible encontrado (usualmente el del cuadro de dialogo)
      return possiblePins[possiblePins.length - 1]; // Tomar el ultimo que suele ser el contenido dinamico
    }
    
    return null;
  } catch (error) {
    return null;
  }
}

async function startAutoSync() {
  console.log('=======================================================');
  console.log(' INICIANDO AUTO-SINCRONIZADOR INVISIBLE PARA MOONLIGHT');
  console.log('=======================================================');
  console.log('[*] Esperando a que abras Moonlight en el dispositivo Android...');
  console.log('[*] Escaneando pantalla en segundo plano. (Presiona Ctrl+C para salir)\n');

  setInterval(async () => {
    if (isPairing) return;
    
    process.stdout.write('.'); // Indicador de actividad
    
    const pin = await extractPinFromScreen();
    
    if (pin && pin !== lastPin) {
      console.log(`\n\n[!!!] PIN DETECTADO EN PANTALLA: ${pin}`);
      lastPin = pin;
      await pairWithSunshine(pin);
    }
  }, 2000); // Escanear cada 2 segundos
}

startAutoSync();
