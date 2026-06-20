const fs = require('fs');
const path = require('path');

const outDir = path.join(__dirname, '..', 'out');

function processDirectory(dir) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const fullPath = path.join(dir, file);
    const stat = fs.statSync(fullPath);
    if (stat.isDirectory()) {
      processDirectory(fullPath);
    } else if (file.endsWith('.html')) {
      let content = fs.readFileSync(fullPath, 'utf8');
      
      // Replace absolute next paths with relative paths
      // Replacing "/_next" with "./_next"
      // Replacing 'href="/' with 'href="./' (for other assets if any)
      const original = content;
      content = content.replace(/src="\/_next/g, 'src="./_next');
      content = content.replace(/href="\/_next/g, 'href="./_next');
      content = content.replace(/href="\/favicon/g, 'href="./favicon');
      content = content.replace(/src="\/file/g, 'src="./file');
      content = content.replace(/src="\/globe/g, 'src="./globe');
      
      // Also match potential backslash or single quotes or URL preloads
      content = content.replace(/href="\/_next/g, 'href="./_next');
      content = content.replace(/href='\/_next/g, "href='./_next");
      content = content.replace(/src='\/_next/g, "src='./_next");
      content = content.replace(/"\/_next/g, '"./_next');
      
      // For any assets preloaded or in JS scripts inside HTML
      content = content.replace(/\\\/_next/g, '\\/._next'); // Match escaped JSON paths if any
      
      if (content !== original) {
        fs.writeFileSync(fullPath, content, 'utf8');
        console.log(`[+] Paths fixed in: ${path.relative(outDir, fullPath)}`);
      }
    }
  }
}

if (fs.existsSync(outDir)) {
  console.log('[*] Corrigiendo rutas de Next.js para compatibilidad con Electron (file://)...');
  processDirectory(outDir);
  console.log('[+] Rutas corregidas con éxito.');
} else {
  console.error('[Error] Directorio out/ no encontrado. Ejecuta next build primero.');
}
