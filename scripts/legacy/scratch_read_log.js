const fs = require('fs');
const path = require('path');

const logPath = 'C:\\Program Files\\Sunshine\\config\\sunshine.log';
console.log('=== LEYENDO ULTIMAS LINEAS DE SUNSHINE.LOG ===');

if (fs.existsSync(logPath)) {
  const content = fs.readFileSync(logPath, 'utf8');
  const lines = content.split('\n');
  const last100 = lines.slice(-100).join('\n');
  console.log(last100);
} else {
  console.log('El archivo sunshine.log no existe.');
}
console.log('==============================================');
