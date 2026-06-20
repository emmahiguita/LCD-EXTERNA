const fs = require('fs');
const path = require('path');

const srcDir = 'C:\\androproject-gui\\public';
const destDir = path.join(__dirname, 'public');

if (!fs.existsSync(destDir)) {
  fs.mkdirSync(destDir, { recursive: true });
}

const files = ['file.svg', 'globe.svg', 'icon.ico', 'icon.png', 'next.svg', 'vercel.svg', 'window.svg'];

files.forEach(file => {
  const srcFile = path.join(srcDir, file);
  const destFile = path.join(destDir, file);
  if (fs.existsSync(srcFile)) {
    fs.copyFileSync(srcFile, destFile);
    console.log(`Copied ${file}`);
  }
});
