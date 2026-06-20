const fs = require('fs');
try {
  const data = fs.readFileSync('android_logs.txt', 'utf16le');
  fs.writeFileSync('android_logs_utf8.txt', data, 'utf8');
  console.log('Converted');
} catch (e) {
  console.error(e);
}
