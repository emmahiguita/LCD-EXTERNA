const { exec } = require('child_process');
const fs = require('fs');
const path = require('path');

const paths = [
  'C:\\Program Files\\LizardByte\\Sunshine\\sunshine.exe',
  'C:\\Program Files\\Sunshine\\sunshine.exe',
  path.join(process.env.LOCALAPPDATA || '', 'Sunshine', 'sunshine.exe'),
  path.join(process.env.PROGRAMFILES || '', 'Sunshine', 'sunshine.exe'),
  path.join(process.env.PROGRAMFILES || '', 'LizardByte', 'Sunshine', 'sunshine.exe')
];

let sunshineExe = null;
for (const p of paths) {
  if (fs.existsSync(p)) {
    sunshineExe = p;
    break;
  }
}

console.log('[*] Intentando iniciar Sunshine...');

// Método 1: Intentar iniciar como Servicio de Windows
exec('net start sunshineservice', (err, stdout, stderr) => {
  if (!err) {
    console.log('[+] Servicio de Sunshine iniciado correctamente.');
    console.log('Ingresa a: https://localhost:47990');
    process.exit(0);
  }

  // Método 2: Si falla como servicio, ejecutar directamente el binario
  if (sunshineExe) {
    console.log('[*] Servicio de Windows no disponible. Iniciando ejecutable directamente...');
    const proc = exec(`"${sunshineExe}"`, (errBin) => {
      if (errBin) {
        console.error('ERROR al ejecutar Sunshine:', errBin.message);
      }
    });
    
    // Dejar correr en segundo plano
    proc.unref();
    console.log('[+] Sunshine iniciado en segundo plano.');
    console.log('Ingresa a: https://localhost:47990');
    process.exit(0);
  } else {
    console.error('ERROR: No se encontro el ejecutable de Sunshine para iniciarlo.');
    process.exit(1);
  }
});
