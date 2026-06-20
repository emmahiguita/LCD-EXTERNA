const fs = require('fs');
const path = require('path');

const configDirs = [
  'C:\\Program Files\\LizardByte\\Sunshine\\config',
  'C:\\Program Files\\Sunshine\\config',
  path.join(process.env.LOCALAPPDATA || '', 'Sunshine', 'config'),
  path.join(process.env.PROGRAMDATA || '', 'Sunshine', 'config')
];

console.log('=== DEBÚG DE CONFIGURACION DE SUNSHINE ===');

for (const dir of configDirs) {
  console.log(`\nRevisando directorio: ${dir}`);
  if (fs.existsSync(dir)) {
    console.log('[+] El directorio existe.');
    try {
      const files = fs.readdirSync(dir);
      console.log('    Archivos encontrados:', files);
      
      for (const file of files) {
        const filePath = path.join(dir, file);
        const stats = fs.statSync(filePath);
        console.log(`    - ${file} (${stats.size} bytes) - Modificado: ${stats.mtime.toLocaleString()}`);
        
        if (file === 'sunshine.conf') {
          const content = fs.readFileSync(filePath, 'utf8');
          console.log('--- Contenido de sunshine.conf ---');
          console.log(content);
          console.log('----------------------------------');
        }
      }
    } catch (e) {
      console.error('    [ERROR] No se pudo leer el directorio:', e.message);
    }
  } else {
    console.log('[-] El directorio no existe.');
  }
}
