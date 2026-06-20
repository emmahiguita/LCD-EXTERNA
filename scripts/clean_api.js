const fs = require('fs');
const path = require('path');

const apiDir = path.join(__dirname, '..', 'src', 'app', 'api');
const nextDir = path.join(__dirname, '..', '.next');

// 1. Delete Next.js API Routes (fully migrated to Electron)
if (fs.existsSync(apiDir)) {
  console.log('[*] Eliminando directorio de rutas API de Next.js (ya migradas a Electron)...');
  fs.rmSync(apiDir, { recursive: true, force: true });
  console.log('[+] Directorio de API eliminado.');
}

// 2. Clear Next.js Cache & Auto-generated Route Validators
if (fs.existsSync(nextDir)) {
  console.log('[*] Limpiando caché y archivos temporales de Next.js (.next)...');
  fs.rmSync(nextDir, { recursive: true, force: true });
  console.log('[+] Caché (.next) limpiada correctamente.');
}

// 3. Delete Dead and Unused Components / Hooks (Clean Code Audit)
const deadFiles = [
  path.join(__dirname, '..', 'src', 'hooks', 'useConnectionManager.ts'),
  path.join(__dirname, '..', 'src', 'components', 'layout', 'ConnectionStatus.tsx'),
  path.join(__dirname, '..', 'src', 'components', 'layout', 'FloatingWindow.tsx'),
  path.join(__dirname, '..', 'src', 'components', 'settings', 'ConnectionPanel.tsx'),
  path.join(__dirname, '..', 'src', 'components', 'smartworkstation', 'ViewportController.ts'),
];

deadFiles.forEach(file => {
  if (fs.existsSync(file)) {
    console.log(`[*] Eliminando archivo muerto: ${path.relative(path.join(__dirname, '..'), file)}`);
    fs.unlinkSync(file);
    console.log(`[+] Archivo eliminado con éxito.`);
  }
});
