const fs = require('fs');
const path = require('path');

const filePath = 'C:\\Program Files\\Sunshine\\config\\sunshine_state.json';
console.log('--- LEYENDO SUNSHINE STATE ---');
if (fs.existsSync(filePath)) {
  const content = fs.readFileSync(filePath, 'utf8');
  console.log(content);
} else {
  console.log('El archivo no existe.');
}
console.log('------------------------------');
