const https = require('https');

const pin = process.argv[2] || '8401';
console.log(`[*] Iniciando emparejamiento definitivo para el PIN: ${pin}...`);

const basicAuth = 'Basic ' + Buffer.from('admin:admin1234').toString('base64');

// 1. Intentar obtener el Token CSRF de forma segura
const csrfOptions = {
  hostname: 'localhost',
  port: 47990,
  path: '/api/csrf-token',
  method: 'GET',
  rejectUnauthorized: false,
  headers: {
    'Authorization': basicAuth
  }
};

const csrfReq = https.request(csrfOptions, (csrfRes) => {
  let bodyData = '';
  csrfRes.on('data', (chunk) => bodyData += chunk);
  csrfRes.on('end', () => {
    let csrfToken = '';
    
    // Si se obtuvo respuesta correcta, intentar parsear el token
    if (csrfRes.statusCode === 200) {
      try {
        const json = JSON.parse(bodyData);
        csrfToken = json.token || json.csrfToken || '';
      } catch (e) {
        // Si no es JSON, buscar en los headers
        if (csrfRes.headers['x-csrf-token']) {
          csrfToken = csrfRes.headers['x-csrf-token'];
        }
      }
    }
    
    console.log('[+] Estado CSRF:', csrfRes.statusCode);
    if (csrfToken) {
      console.log('[+] Token CSRF obtenido:', csrfToken);
    } else {
      console.log('[*] No se requirio o no se detecto token CSRF en formato JSON. Procediendo con Basic Auth directo...');
    }

    // 2. Realizar la peticion de emparejamiento con el PIN
    const postData = JSON.stringify({ pin });
    const postOptions = {
      hostname: 'localhost',
      port: 47990,
      path: '/api/pin',
      method: 'POST',
      rejectUnauthorized: false,
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': postData.length,
        'Authorization': basicAuth
      }
    };

    // Agregar el header de CSRF si fue obtenido
    if (csrfToken) {
      postOptions.headers['X-CSRF-Token'] = csrfToken;
    }

    const postReq = https.request(postOptions, (postRes) => {
      let responseBody = '';
      postRes.on('data', (chunk) => responseBody += chunk);
      postRes.on('end', () => {
        if (postRes.statusCode >= 200 && postRes.statusCode < 300) {
          console.log('\n=======================================================');
          console.log(' ¡ENLAZADO Y SINCRONIZADO AUTOMATICAMENTE CON EXITO!');
          console.log(' Abre Moonlight en tu celular y toca tu PC para transmitir.');
          console.log('=======================================================');
          process.exit(0);
        } else {
          console.error(`\n[ERROR] Sunshine rechazo el PIN (${postRes.statusCode}):`, responseBody);
          process.exit(1);
        }
      });
    });

    postReq.on('error', (err) => {
      console.error('\n[ERROR POST PIN]:', err.message);
      process.exit(1);
    });

    postReq.write(postData);
    postReq.end();
  });
});

csrfReq.on('error', (err) => {
  // Si falla el CSRF (por ejemplo si el endpoint no existe en esta version), probar POST directo
  console.log('[!] Endpoint CSRF no disponible. Intentando emparejamiento directo...');
  
  const postData = JSON.stringify({ pin });
  const postOptions = {
    hostname: 'localhost',
    port: 47990,
    path: '/api/pin',
    method: 'POST',
    rejectUnauthorized: false,
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': postData.length,
      'Authorization': basicAuth
    }
  };

  const postReq = https.request(postOptions, (postRes) => {
    let responseBody = '';
    postRes.on('data', (chunk) => responseBody += chunk);
    postRes.on('end', () => {
      if (postRes.statusCode >= 200 && postRes.statusCode < 300) {
        console.log('\n=======================================================');
        console.log(' ¡ENLAZADO Y SINCRONIZADO AUTOMATICAMENTE CON EXITO!');
        console.log(' Abre Moonlight en tu celular y toca tu PC para transmitir.');
        console.log('=======================================================');
        process.exit(0);
      } else {
        console.error(`\n[ERROR] Sunshine rechazo el PIN (${postRes.statusCode}):`, responseBody);
        process.exit(1);
      }
    });
  });

  postReq.on('error', (postErr) => {
    console.error('\n[ERROR CONEXION]:', postErr.message);
    process.exit(1);
  });

  postReq.write(postData);
  postReq.end();
});

csrfReq.end();
