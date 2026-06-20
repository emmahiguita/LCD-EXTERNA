const net = require('net');
const os = require('os');
const { exec } = require('child_process');

function checkPort(port, host = '127.0.0.1') {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    socket.setTimeout(1000);
    socket.on('connect', () => {
      socket.destroy();
      resolve(true);
    });
    socket.on('timeout', () => {
      socket.destroy();
      resolve(false);
    });
    socket.on('error', () => {
      socket.destroy();
      resolve(false);
    });
    socket.connect(port, host);
  });
}

function runCmd(cmd) {
  return new Promise((resolve) => {
    exec(cmd, (err, stdout) => {
      if (err) {
        resolve(`Error: ${err.message}`);
      } else {
        resolve(stdout.trim());
      }
    });
  });
}

async function main() {
  console.log('=========================================');
  console.log('   PRUEBA DE CONEXIÓN LOCAL - SMARTDISPLAY');
  console.log('=========================================');

  // 1. Interfaces e IPs
  console.log('\n[*] Interfaces de Red Detectadas:');
  const interfaces = os.networkInterfaces();
  let foundIps = [];
  for (const [name, nets] of Object.entries(interfaces)) {
    for (const net of nets) {
      if (net.family === 'IPv4' && !net.internal) {
        console.log(`  - ${name}: ${net.address} (MAC: ${net.mac})`);
        foundIps.push(net.address);
      }
    }
  }

  // 2. Puertos
  console.log('\n[*] Verificando puertos locales (Listening):');
  const portsToCheck = [
    { port: 3000, name: 'Next.js Dev Server' },
    { port: 3001, name: 'Electron HTTP API' },
    { port: 3002, name: 'Electron WebSocket Server' },
    { port: 47989, name: 'Sunshine HTTPS Port' },
    { port: 47984, name: 'Sunshine HTTP Port' },
    { port: 48010, name: 'Sunshine Stream Port' },
    { port: 5555, name: 'ADB Wireless (if active)' }
  ];

  for (const item of portsToCheck) {
    const active = await checkPort(item.port);
    console.log(`  - Puerto ${item.port} (${item.name}): ${active ? '🟢 ESCUCHANDO' : '🔴 INACTIVO'}`);
  }

  // 3. Reglas de Firewall (Intento de lectura via netsh que no requiere privilegios elevados para listar)
  console.log('\n[*] Consultando reglas del Firewall de Windows (netsh):');
  const rulesResult = await runCmd('netsh advfirewall firewall show rule name=all | findstr /I "Sunshine SmartDisplay"');
  if (rulesResult.includes('Error') || !rulesResult) {
    console.log('  - No se pudieron consultar las reglas de firewall (permisos del sandbox o sin reglas específicas).');
  } else {
    console.log(rulesResult);
  }

  console.log('\n=========================================');
}

main();
