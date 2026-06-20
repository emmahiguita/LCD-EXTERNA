const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const exePath = 'C:\\Program Files\\Sunshine\\sunshine.exe';
const configDir = 'C:\\Program Files\\Sunshine\\config';
const stateFile = path.join(configDir, 'sunshine_state.json');

console.log('=== RESTABLECIMIENTO FORZADO DE SUNSHINE ===');

try {
  // 1. Detener el servicio y los procesos
  console.log('[1/5] Deteniendo servicio y procesos de Sunshine...');
  try {
    execSync('net stop sunshineservice', { stdio: 'inherit' });
  } catch (e) {
    console.log('      (El servicio ya estaba detenido o no se pudo detener vía net stop)');
  }
  try {
    execSync('taskkill /f /im sunshine.exe', { stdio: 'inherit' });
  } catch (e) {
    console.log('      (No había procesos sunshine.exe activos)');
  }

  // 2. Eliminar el archivo de estado si existe
  console.log('[2/5] Eliminando archivo de estado antiguo...');
  if (fs.existsSync(stateFile)) {
    fs.unlinkSync(stateFile);
    console.log('      [+] sunshine_state.json eliminado.');
  } else {
    console.log('      (No existe sunshine_state.json anterior)');
  }

  // 3. Crear las credenciales nuevas
  console.log('[3/5] Ejecutando comando de creación de credenciales...');
  execSync(`"${exePath}" --creds admin admin1234`, { stdio: 'inherit' });
  console.log('      [+] Comando --creds completado.');

  // 4. Verificar si el archivo se creó y no tiene 0 bytes
  if (fs.existsSync(stateFile)) {
    const stats = fs.statSync(stateFile);
    console.log(`      [+] Archivo sunshine_state.json creado exitosamente. Tamaño: ${stats.size} bytes.`);
  } else {
    console.warn('      [ADVERTENCIA] No se encontro sunshine_state.json en la ruta por defecto.');
  }

  // 5. Reiniciar el servicio
  console.log('[5/5] Iniciando servicio de Sunshine...');
  try {
    execSync('net start sunshineservice', { stdio: 'inherit' });
    console.log('[+] ¡Sunshine iniciado como servicio!');
  } catch (e) {
    console.log('      (No se pudo iniciar como servicio. Iniciando en segundo plano...)');
    const { exec } = require('child_process');
    const proc = exec(`"${exePath}"`);
    proc.unref();
    console.log('[+] ¡Sunshine iniciado en segundo plano!');
  }

  console.log('\n=======================================================');
  console.log(' PROCESO COMPLETADO.');
  console.log(' Ahora puedes correr: npm run pair:moonlight 9743');
  console.log('=======================================================');

} catch (err) {
  console.error('\n[ERROR CRITICO]:', err.message);
  console.log('Por favor, asegúrate de correr este terminal como ADMINISTRADOR.');
}
