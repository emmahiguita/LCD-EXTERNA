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

console.log('[*] Reiniciando Sunshine para aplicar las nuevas credenciales...');

// 1. Detener procesos activos
exec('taskkill /f /im sunshine.exe', () => {
  exec('net stop sunshineservice', () => {
    console.log('[+] Sunshine detenido.');

    // 2. Iniciar de nuevo
    setTimeout(() => {
      exec('net start sunshineservice', (err, stdout, stderr) => {
        if (!err) {
          console.log('[+] Servicio de Sunshine reiniciado correctamente.');
          process.exit(0);
        }

        if (sunshineExe) {
          console.log('[*] Iniciando ejecutable directamente...');
          const proc = exec(`"${sunshineExe}"`);
          proc.unref();
          console.log('[+] Sunshine reiniciado en segundo plano.');
          process.exit(0);
        } else {
          console.error('ERROR: No se pudo iniciar Sunshine.');
          process.exit(1);
        }
      });
    }, 1500);
  });
});
