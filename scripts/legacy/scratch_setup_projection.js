const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const appsJsonPath = 'C:\\Program Files\\Sunshine\\config\\apps.json';

try {
  console.log('=== CONFIGURANDO BACKEND SUNSHINE PARA MODO PROYECCION ===');

  if (!fs.existsSync(appsJsonPath)) {
    console.error('No se encontro apps.json en:', appsJsonPath);
    process.exit(1);
  }

  const raw = fs.readFileSync(appsJsonPath, 'utf8');
  const data = JSON.parse(raw);

  // Verificar si ya existe o reemplazar
  const index = data.apps.findIndex(a => a.name === 'Modo Proyeccion (Extendido)');
  const appDefinition = {
    "name": "Modo Proyeccion (Extendido)",
    "prep-cmd": [
      {
        "do": "C:\\VirtualDisplayDriver\\MultiMonitorTool.exe /SetPrimary 2",
        "undo": "C:\\VirtualDisplayDriver\\MultiMonitorTool.exe /SetPrimary 1"
      }
    ],
    "image-path": "desktop.png"
  };

  if (index !== -1) {
    console.log('[+] Actualizando aplicacion "Modo Proyeccion (Extendido)" existente...');
    data.apps[index] = appDefinition;
  } else {
    console.log('[+] Agregando aplicacion "Modo Proyeccion (Extendido)" a Sunshine...');
    data.apps.push(appDefinition);
  }

  fs.writeFileSync(appsJsonPath, JSON.stringify(data, null, 2), 'utf8');
  console.log('[+] apps.json actualizado.');
    
  console.log('[+] Reiniciando servicio Sunshine...');
  try {
    execSync('net stop sunshineservice', { stdio: 'ignore' });
    execSync('net start sunshineservice', { stdio: 'ignore' });
  } catch(e) {
    console.log('    (Se requiere reiniciar Sunshine manualmente si no hay privilegios de administrador)');
  }

  console.log('Proceso completado.');

} catch (e) {
  console.error('[ERROR]', e.message);
}
