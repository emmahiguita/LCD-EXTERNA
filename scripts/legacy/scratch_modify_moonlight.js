const fs = require('fs');
const path = require('path');

function walk(dir, callback) {
    if(!fs.existsSync(dir)) return;
    const files = fs.readdirSync(dir);
    for (const f of files) {
        const p = path.join(dir, f);
        const stat = fs.statSync(p);
        if (stat.isDirectory()) {
            walk(p, callback);
        } else if (p.endsWith('.java') || p.endsWith('.kt')) {
            callback(p);
        }
    }
}

console.log('[*] Buscando la logica de emparejamiento en el codigo fuente...');

let modified = false;
const basePath = path.join(__dirname, 'moonlight-android-master');

walk(basePath, (filePath) => {
    let content = fs.readFileSync(filePath, 'utf8');
    let original = content;
    
    // Intento heuristico de encontrar variables de PIN y forzarlas a "9999"
    content = content.replace(/String\s+([a-zA-Z0-9_]*pin[a-zA-Z0-9_]*)\s*=\s*[^;]+;/gi, 'String $1 = "9999";');
    content = content.replace(/val\s+([a-zA-Z0-9_]*pin[a-zA-Z0-9_]*)\s*=\s*[^\n]+(?:\n|$)/gi, 'val $1 = "9999"\n');
    
    // Forzar metodos generatePin
    if (content.includes('generatePin')) {
        content = content.replace(/(?:public|private|protected|internal)?\s*(?:String|fun)\s+generatePin\s*\([^)]*\)\s*\{[^}]*\}/g, 'public String generatePin() { return "9999"; }');
    }

    if (content !== original) {
        fs.writeFileSync(filePath, content, 'utf8');
        console.log(`[+] PIN congelado en: ${filePath}`);
        modified = true;
    }
});

if (!modified) {
    console.log('[!] No se detecto un generador de PIN en Java puro. Es posible que Moonlight use la libreria C nativa (moonlight-common-c) para el emparejamiento en esta version.');
    console.log('[!] Sin embargo, el codigo fuente ya ha sido descargado para tu analisis.');
} else {
    console.log('[+] Modificacion completada con exito. El PIN ha sido hardcodeado a 9999.');
}
