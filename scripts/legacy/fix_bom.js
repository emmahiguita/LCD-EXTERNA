const fs = require('fs');
const path = require('path');

const files = [
    'moonlight-android-master/app/build.gradle',
    'moonlight-android-master/app/src/main/res/values/strings.xml',
    'moonlight-android-master/app/src/main/res/values/styles.xml'
];

files.forEach(file => {
    const filePath = path.join(__dirname, file);
    if (fs.existsSync(filePath)) {
        let buffer = fs.readFileSync(filePath);
        // Comprobar si tiene UTF-8 BOM (0xEF, 0xBB, 0xBF)
        if (buffer[0] === 0xEF && buffer[1] === 0xBB && buffer[2] === 0xBF) {
            console.log('Reparando archivo (Eliminando BOM): ' + file);
            buffer = buffer.slice(3);
            fs.writeFileSync(filePath, buffer);
        } else {
            console.log('El archivo ya esta correcto: ' + file);
        }
    }
});

console.log('\n¡Listo! Ahora puedes compilar sin errores de "Unexpected character".');
