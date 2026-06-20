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

if (!sunshineExe) {
  console.error('ERROR: No se encontro el ejecutable sunshine.exe en las rutas comunes.');
  console.log('Rutas buscadas:');
  paths.forEach(p => console.log(' -', p));
  process.exit(1);
}

console.log('[+] Encontrado Sunshine en:', sunshineExe);
console.log('[*] Intentando restablecer credenciales a admin / admin1234...');

const cmd = `"${sunshineExe}" --creds admin admin1234`;
exec(cmd, (err, stdout, stderr) => {
  if (err) {
    console.error('ERROR al restablecer las credenciales:', err.message);
    if (stderr) console.error('Detalle de error:', stderr);
    console.log('\nPor favor, asegurate de ejecutar este terminal como ADMINISTRADOR.');
    process.exit(1);
  }
  console.log('[+] Credenciales restablecidas con éxito.');
  console.log('Usuario: admin');
  console.log('Contraseña: admin1234');
});
