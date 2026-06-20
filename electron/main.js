const { app, BrowserWindow, shell, clipboard, Tray, Menu, nativeImage, powerSaveBlocker, ipcMain } = require('electron');
const { spawn, execFile, exec } = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');
const crypto = require('crypto');
const { WebSocketServer } = require('ws');
const { autoUpdater } = require('electron-updater');

// ─── Environment Configuration ─────────────────────────────────────────────────
try {
  const envPath = path.join(__dirname, '..', '.env');
  if (fs.existsSync(envPath)) {
    const envContent = fs.readFileSync(envPath, 'utf8');
    envContent.split('\n').forEach(line => {
      const parts = line.trim().split('=');
      if (parts.length >= 2 && !line.startsWith('#')) {
        const key = parts[0].trim();
        const value = parts.slice(1).join('=').trim().replace(/^['"]|['"]$/g, '');
        process.env[key] = value;
      }
    });
  }
} catch (_) {}

// ─── Persistent session token ─────────────────────────────────────────────────
// Persists across restarts so Android doesn't need re-configuration after
// each Electron restart. Token rotates every 24 hours for security.
const TOKEN_FILE = path.join(__dirname, '..', '.smartdisplay-token');
const TOKEN_EXPIRY_FILE = path.join(__dirname, '..', '.smartdisplay-token-expiry');
const TOKEN_ROTATION_INTERVAL = 24 * 60 * 60 * 1000; // 24 hours
let sessionToken;
let previousToken = null; // grace-window token: stays valid for one rotation
let tokenExpiry;

// Validates a token against the current OR previous token.
// The grace window means a client that connected before a rotation can still
// reconnect afterward without being locked out (fixes 4001 reconnect loop).
function isValidToken(t) {
  if (!t || typeof t !== 'string' || t.length < 32) return false;
  return t === sessionToken || t === previousToken;
}

function generateToken() {
  // Demote the current token to "previous" so existing clients keep working
  // through the rotation. Only tokens older than 2 rotation windows are invalidated.
  if (sessionToken) previousToken = sessionToken;
  sessionToken = crypto.randomBytes(16).toString('hex');
  tokenExpiry = Date.now() + TOKEN_ROTATION_INTERVAL;
  try {
    fs.writeFileSync(TOKEN_FILE, sessionToken, 'utf8');
    fs.writeFileSync(TOKEN_EXPIRY_FILE, String(tokenExpiry), 'utf8');
    console.log('[Token] New token generated, expires at:', new Date(tokenExpiry).toISOString());
  } catch (err) {
    console.error('[Token] Failed to save token:', err);
  }
}

function isTokenExpired() {
  return Date.now() > tokenExpiry;
}

// Load or generate token
try {
  if (fs.existsSync(TOKEN_FILE) && fs.existsSync(TOKEN_EXPIRY_FILE)) {
    const stored = fs.readFileSync(TOKEN_FILE, 'utf8').trim();
    const storedExpiry = parseInt(fs.readFileSync(TOKEN_EXPIRY_FILE, 'utf8').trim(), 10);
    if (stored.length >= 32 && !isNaN(storedExpiry)) {
      sessionToken = stored;
      tokenExpiry = storedExpiry;
      // Rotate if expired
      if (isTokenExpired()) {
        console.log('[Token] Token expired, rotating...');
        generateToken();
      } else {
        console.log('[Token] Loaded existing token, expires at:', new Date(tokenExpiry).toISOString());
      }
    }
  }
} catch (_) {}

if (!sessionToken) {
  generateToken();
}

// Schedule periodic token rotation
setInterval(() => {
  if (isTokenExpired()) {
    console.log('[Token] Token expired, rotating...');
    generateToken();
    // Notify all connected WS clients that the token has rotated.
    // They will receive a 'token-rotated' message and should fetch
    // the new token from /api/connect or /api/token-refresh.
    // Note: old token remains valid for one grace window (previousToken),
    // so clients can reconnect seamlessly without being locked out.
    notifyTokenRotation();
  }
}, 60 * 60 * 1000); // Check every hour

function notifyTokenRotation() {
  const msg = JSON.stringify({ type: 'token-rotated' });
  for (const client of [...(global._allWsClients ?? [])]) {
    try {
      if (client.readyState === 1) client.send(msg);
    } catch (_) {}
  }
}

const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
  return;
}

app.on('second-instance', () => {
  if (mainWindow) {
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.show();
    mainWindow.focus();
  }
});

let mainWindow;
let wss;
let streamClients = new Set();
let mobileClients = new Set();
let androidStreamClients = new Set();
let activeClients = new Set();

// ─── Lifecycle state ─────────────────────────────────────────────────────────
let tray = null;
let isQuitting = false;
let minToTray = true;
let powerBlockId = null;

// ─── Power management ────────────────────────────────────────────────────────

function updatePowerBlock() {
  const hasClients = streamClients.size > 0 || mobileClients.size > 0;
  if (hasClients && powerBlockId === null) {
    try { powerBlockId = powerSaveBlocker.start('prevent-display-sleep'); } catch (_) {}
  } else if (!hasClients && powerBlockId !== null) {
    try { powerSaveBlocker.stop(powerBlockId); } catch (_) {}
    powerBlockId = null;
  }
}

// ─── System tray ─────────────────────────────────────────────────────────────

function createTray() {
  try {
    const iconPath = path.join(__dirname, '..', 'public', 'icon.png');
    const icon = fs.existsSync(iconPath)
      ? nativeImage.createFromPath(iconPath).resize({ width: 16, height: 16 })
      : nativeImage.createEmpty();
    tray = new Tray(icon);
    tray.setToolTip('SmartDisplay AI');
    updateTrayMenu();
    tray.on('double-click', () => {
      if (mainWindow) { mainWindow.show(); mainWindow.focus(); }
    });
  } catch (e) {
    console.warn('[Tray] No se pudo crear icono de sistema:', e.message);
  }
}

function updateTrayMenu() {
  if (!tray) return;
  const clientCount = streamClients.size + mobileClients.size;
  const menu = Menu.buildFromTemplate([
    { label: 'SmartDisplay AI', enabled: false },
    { label: `Clientes conectados: ${clientCount}`, enabled: false },
    { type: 'separator' },
    {
      label: 'Mostrar ventana',
      click: () => { if (mainWindow) { mainWindow.show(); mainWindow.focus(); } },
    },
    { type: 'separator' },
    {
      label: 'Salir completamente',
      click: () => { isQuitting = true; app.quit(); },
    },
  ]);
  tray.setContextMenu(menu);
}

// ─── Network helpers ─────────────────────────────────────────────────────────

function getLocalIP() {
  const host = process.env.SUNSHINE_HOST;
  if (host && host !== 'localhost' && host !== '127.0.0.1' && !host.includes('localhost')) {
    return host;
  }
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name]) {
      if (net.family === 'IPv4' && !net.internal) {
        const parts = net.address.split('.').map(Number);
        if (parts[0] === 100 && parts[1] >= 64 && parts[1] <= 127) {
          return net.address;
        }
      }
    }
  }
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name]) {
      if (net.family === 'IPv4' && !net.internal) {
        return net.address;
      }
    }
  }
  return '127.0.0.1';
}

function getLocalIPs() {
  let lanIp = null;
  let tailscaleIp = process.env.SUNSHINE_HOST;
  if (tailscaleIp === 'localhost' || tailscaleIp === '127.0.0.1') {
    tailscaleIp = null;
  }

  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name]) {
      if (net.family === 'IPv4' && !net.internal) {
        const addr = net.address;
        const parts = addr.split('.').map(Number);
        if (parts[0] === 100 && parts[1] >= 64 && parts[1] <= 127) {
          tailscaleIp = addr;
        } else if (parts[0] !== 169) {
          lanIp = addr;
        }
      }
    }
  }

  if (!lanIp) {
    for (const name of Object.keys(interfaces)) {
      for (const net of interfaces[name]) {
        if (net.family === 'IPv4' && !net.internal) {
          lanIp = net.address;
          break;
        }
      }
      if (lanIp) break;
    }
  }

  if (!lanIp) lanIp = '127.0.0.1';
  if (!tailscaleIp) tailscaleIp = lanIp;

  return { lanIp, tailscaleIp };
}

let captureProc = null;
let frameBuffer = Buffer.alloc(0);
let selectedSerial = null;
let lastClipboardText = '';
let pcStreamInterval = null;

const WS_PORT = 3002;
const ADB = fs.existsSync('C:\\AndroProject\\adb.exe')
  ? 'C:\\AndroProject\\adb.exe'
  : (fs.existsSync('C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe')
      ? 'C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe'
      : 'adb');

autoUpdater.logger = console;
autoUpdater.autoDownload = true;
autoUpdater.autoInstallOnAppQuit = true;

// ─── ADB helpers ─────────────────────────────────────────────────────────────

async function getDevice() {
  return new Promise((resolve) => {
    execFile(ADB, ['devices'], (err, stdout) => {
      if (!err && stdout) {
        const lines = stdout.split('\n').slice(1);
        const onlineDevices = [];
        for (const line of lines) {
          if (line.includes('\tdevice')) {
            onlineDevices.push(line.split('\t')[0].trim());
          }
        }
        if (selectedSerial && onlineDevices.includes(selectedSerial)) {
          resolve(selectedSerial);
        } else {
          resolve(onlineDevices.length > 0 ? onlineDevices[0] : null);
        }
      } else {
        resolve(null);
      }
    });
  });
}

function adb(args) {
  return new Promise((resolve, reject) => {
    getDevice().then(dev => {
      const cmdArgs = dev ? ['-s', dev, ...args] : args;
      try {
        execFile(ADB, cmdArgs, { timeout: 8000, maxBuffer: 10 * 1024 * 1024 }, (err, stdout) => {
          if (err) reject(err); else resolve(stdout);
        });
      } catch (err) {
        reject(err);
      }
    }).catch(reject);
  });
}

// ─── Clipboard Sync ──────────────────────────────────────────────────────────

function setupClipboardSync() {
  setInterval(async () => {
    try {
      const currentText = clipboard.readText();
      if (currentText && currentText !== lastClipboardText) {
        lastClipboardText = currentText;
        // Notify renderer about clipboard change
        sendToRenderer('clipboard-change', currentText);

        // Truncate to prevent command line ENAMETOOLONG errors when copying large texts/logs
        let syncText = currentText;
        if (syncText.length > 2000) {
          syncText = syncText.substring(0, 2000) + '... [Truncated due to size]';
        }

        const safeText = syncText.replace(/[ '"\\&|;<>()$`!]/g, '\\$&');
        await adb(['shell', 'am', 'broadcast', '-a', 'shem.youtube.url.SET_CLIPBOARD', '--es', 'text', safeText]).catch(() => {});
        await adb(['shell', 'content', 'insert', '--uri', 'content://clipboard/class', '--bind', `text:s:${safeText}`]).catch(() => {});
      }

      const deviceClip = await adb(['shell', 'content', 'query', '--uri', 'content://clipboard/class', '--projection', 'text']).catch(() => '');
      if (deviceClip && deviceClip.includes('text=')) {
        const match = deviceClip.match(/text=(.*)/);
        if (match && match[1]) {
          const deviceText = match[1].trim();
          if (deviceText && deviceText !== currentText) {
            clipboard.writeText(deviceText);
            lastClipboardText = deviceText;
            // Notify renderer about device clipboard sync
            sendToRenderer('clipboard-change', deviceText);
          }
        }
      }
    } catch (_) {}
  }, 3000);
}

// Screen streaming is delegated to Sunshine Host.
function startPCScreenStreaming() {
  console.log('[WS] Screen streaming delegated to Sunshine.');
}

function stopPCScreenStreaming() {}

let inputAgentProcess = null;

function compileAndStartInputAgent() {
  if (inputAgentProcess) return;

  const agentCsPath = path.join(__dirname, 'input_agent.cs');
  const agentExePath = path.join(__dirname, 'input_agent.exe');

  let needCompile = true;
  if (fs.existsSync(agentExePath) && fs.existsSync(agentCsPath)) {
    const exeStats = fs.statSync(agentExePath);
    const csStats = fs.statSync(agentCsPath);
    if (exeStats.mtimeMs > csStats.mtimeMs) needCompile = false;
  }

  if (needCompile && fs.existsSync(agentCsPath)) {
    console.log('[Agent] Compilando agente nativo C#...');
    const windir = process.env.windir || 'C:\\Windows';
    const cscPaths = [
      path.join(windir, 'Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe'),
      path.join(windir, 'Microsoft.NET\\Framework\\v4.0.30319\\csc.exe'),
    ];
    let cscPath = null;
    for (const p of cscPaths) {
      if (fs.existsSync(p)) { cscPath = p; break; }
    }

    if (cscPath) {
      try {
        const { execSync } = require('child_process');
        const wpfDir = path.join(path.dirname(cscPath), 'WPF');
        const clientDll = fs.existsSync(path.join(wpfDir, 'UIAutomationClient.dll'))
          ? path.join(wpfDir, 'UIAutomationClient.dll')
          : 'UIAutomationClient.dll';
        const typesDll = fs.existsSync(path.join(wpfDir, 'UIAutomationTypes.dll'))
          ? path.join(wpfDir, 'UIAutomationTypes.dll')
          : 'UIAutomationTypes.dll';
        const cmd = `"${cscPath}" /out:"${agentExePath}" /target:exe /optimize+ /r:"${clientDll}" /r:"${typesDll}" "${agentCsPath}"`;
        execSync(cmd, { stdio: 'ignore' });
        console.log('[Agent] Compilación exitosa del agente nativo.');
      } catch (err) {
        console.error('[Agent] Error compilando el agente con csc.exe:', err.message);
      }
    } else {
      console.error('[Agent] No se encontró csc.exe en el sistema.');
    }
  }

  if (fs.existsSync(agentExePath)) {
    try {
      console.log('[Agent] Iniciando agente nativo de inyección...');
      inputAgentProcess = spawn(agentExePath, [], { stdio: ['pipe', 'ignore', 'ignore'] });
      inputAgentProcess.on('error', (err) => console.error('[Agent] Error:', err));
      inputAgentProcess.on('close', (code) => {
        console.log(`[Agent] Proceso cerrado con código: ${code}`);
        inputAgentProcess = null;
      });
    } catch (err) {
      console.error('[Agent] No se pudo lanzar el agente nativo:', err);
    }
  } else {
    console.warn('[Agent] No se pudo iniciar el agente. Continuando sin soporte nativo.');
  }
}

// ─── WebSocket Server ─────────────────────────────────────────────────────────

function startWebSocketServer() {
  try {
    compileAndStartInputAgent();
    wss = new WebSocketServer({ 
      port: WS_PORT, 
      host: '0.0.0.0',
      perMessageDeflate: false, // Disables compression for pre-compressed video frames (reduces CPU & lag)
      maxPayload: 10 * 1024 * 1024 // 10MB limit per frame
    });

    wss.on('connection', (ws, req) => {
      // Disable Nagle's Algorithm for immediate packet transmission (eliminates ~40ms delay per packet)
      req.socket.setNoDelay(true);
      req.socket.setKeepAlive(true, 5000);

      const parsed = require('url').parse(req.url, true);
      const token = parsed.query.token;

      // Validate against current OR previous token (grace window).
      // Prevents rotation from locking out already-connected clients.
      if (!isValidToken(token)) {
        console.warn('[WS] Conexión rechazada: token inválido o ausente.');
        ws.close(4001, 'Unauthorized: Invalid token');
        return;
      }

      // Track ALL connected clients for token rotation notifications
      if (!global._allWsClients) global._allWsClients = new Set();
      global._allWsClients.add(ws);

      const clientIp = req.socket.remoteAddress.replace(/^::ffff:/, '');
      const clientInfo = {
        ws,
        ip: clientIp,
        type: 'unknown',
        connectedAt: new Date().toLocaleTimeString('es-MX', { hour12: false })
      };

      // ── Server → Client heartbeat ──────────────────────────────────────────
      // Sends ping every 25s. If client doesn't pong within 10s, close connection.
      // This ensures the server detects dead clients (not just dead servers).
      let clientAlive = true;
      const serverHeartbeat = setInterval(() => {
        if (!clientAlive) {
          console.warn('[WS] Cliente sin respuesta — cerrando conexión zombie');
          clearInterval(serverHeartbeat);
          try { ws.terminate?.() ?? ws.close(); } catch (_) {}
          return;
        }
        clientAlive = false;
        try {
          if (ws.readyState === 1) ws.send(JSON.stringify({ type: 'ping' }));
        } catch (_) {}
      }, 25_000);

      ws.on('message', async (data, isBinary) => {
        try {
          if (isBinary) {
            for (const client of streamClients) {
              if (client.readyState === 1) {
                // Evitar bufferbloat: si la cola del cliente tiene más de 512 KB, omitir el frame
                if (client.bufferedAmount < 512 * 1024) {
                  client.send(data);
                }
              }
            }
            return;
          }

          const msg = JSON.parse(data.toString());

          // ── WebRTC Signaling ───────────────────────────────────────────────
          if (msg.type === 'webrtc_signal') {
            const rawMsg = data.toString();
            for (const client of [...(global._allWsClients ?? [])]) {
              if (client !== ws && client.readyState === 1) {
                try {
                  client.send(rawMsg);
                } catch (_) {}
              }
            }
            return;
          }

          // ── Heartbeat ──────────────────────────────────────────────────────
          if (msg.type === 'ping') {
            if (ws.readyState === 1) ws.send(JSON.stringify({ type: 'pong' }));
            return;
          }
          // Client responded to server's ping — mark alive
          if (msg.type === 'pong') {
            clientAlive = true;
            return;
          }

          // ── Client registration ────────────────────────────────────────────
          if (msg.type === 'register') {
            clientInfo.type = msg.client || 'dashboard';
            if (msg.client === 'mobile') {
              mobileClients.add(ws);
              console.log(`[WS] Mobile client registered from ${clientIp}.`);
              startPCScreenStreaming();
            } else if (msg.client === 'android-stream') {
              androidStreamClients.add(ws);
              console.log(`[WS] Android H.264 stream client registered from ${clientIp}.`);
            } else {
              streamClients.add(ws);
              if (streamClients.size === 1) startCapture();
            }
            activeClients.add(clientInfo);
            updatePowerBlock();
            updateTrayMenu();
            broadcastStatus();
            broadcastClientConnected(clientInfo.type, streamClients.size + mobileClients.size);
            return;
          }

          // ── PC input (native agent) ────────────────────────────────────────
          if (msg.type === 'pc_click') {
            if (inputAgentProcess && inputAgentProcess.stdin.writable)
              inputAgentProcess.stdin.write(`CLICK ${msg.x} ${msg.y}\n`);
            return;
          }
          if (msg.type === 'smart_click') {
            if (inputAgentProcess && inputAgentProcess.stdin.writable)
              inputAgentProcess.stdin.write(`SMARTCLICK ${msg.x} ${msg.y}\n`);
            return;
          }
          if (msg.type === 'run_app') {
            if (inputAgentProcess && inputAgentProcess.stdin.writable)
              inputAgentProcess.stdin.write(`RUN ${msg.name}\n`);
            return;
          }
          if (msg.type === 'relative_move') {
            if (inputAgentProcess && inputAgentProcess.stdin.writable)
              inputAgentProcess.stdin.write(`RELMOVE ${msg.dx} ${msg.dy}\n`);
            return;
          }
          if (msg.type === 'right_click') {
            if (inputAgentProcess && inputAgentProcess.stdin.writable)
              inputAgentProcess.stdin.write('RCLICK -1 -1\n');
            return;
          }
          if (msg.type === 'macro') {
            if (inputAgentProcess && inputAgentProcess.stdin.writable)
              inputAgentProcess.stdin.write(`CMD ${msg.cmd}\n`);
            return;
          }

          // ── ADB interactions (PC → phone) ──────────────────────────────────
          switch (msg.type) {
            case 'select_device': {
              const prevSerial = selectedSerial;
              selectedSerial = msg.serial || null;
              if (prevSerial !== selectedSerial && captureProc) captureProc.kill();
              break;
            }
            case 'tap':
              await adb(['shell', 'input', 'tap', String(msg.x), String(msg.y)]);
              break;
            case 'swipe':
              await adb(['shell', 'input', 'swipe',
                String(msg.x1), String(msg.y1),
                String(msg.x2), String(msg.y2),
                String(msg.duration || 200)]);
              break;
            case 'keyevent':
              await adb(['shell', 'input', 'keyevent', String(msg.keycode)]);
              break;
            case 'text': {
              let textVal = msg.text || '';
              if (textVal.length > 1000) textVal = textVal.substring(0, 1000);
              const safe = textVal.replace(/[ '"\\&|;<>()$`!]/g, '\\$&');
              await adb(['shell', 'input', 'text', safe]).catch(() => {});
              break;
            }
            case 'scroll': {
              const dy = msg.delta > 0 ? -200 : 200;
              await adb(['shell', 'input', 'swipe',
                String(msg.x), String(msg.y),
                String(msg.x), String(msg.y + dy), '150']);
              break;
            }
          }
        } catch (_) {}
      });

      ws.on('close', () => {
        clearInterval(serverHeartbeat);
        global._allWsClients?.delete(ws);
        streamClients.delete(ws);
        mobileClients.delete(ws);
        androidStreamClients.delete(ws);
        activeClients.delete(clientInfo);
        if (streamClients.size === 0) stopCapture();
        if (mobileClients.size === 0) stopPCScreenStreaming();
        updatePowerBlock();
        updateTrayMenu();
        broadcastStatus();
      });

      ws.on('error', () => {
        clearInterval(serverHeartbeat);
        global._allWsClients?.delete(ws);
        streamClients.delete(ws);
        mobileClients.delete(ws);
        androidStreamClients.delete(ws);
        activeClients.delete(clientInfo);
        updatePowerBlock();
        updateTrayMenu();
        broadcastStatus();
      });
    });

    console.log(`[WS] Server started on ws://0.0.0.0:${WS_PORT}`);

    // ── /api/token-refresh HTTP endpoint ────────────────────────────────────
    // Allows authenticated clients to fetch the current token after rotation.
    // Returns 200 { token } if valid, 401 if old token expired.
    const http = require('http');
    const TOKEN_REFRESH_PORT = 3001;

    // Sunshine API constants ported from Next.js route
    const ANDROPROJECT_BIN = 'C:\\AndroProject\\AndroProject.exe';
    const FASTBOOT = 'C:\\AndroProject\\fastboot.exe';
    const SCREENSHOTS_DIR = 'C:\\AndroProject\\Capturas';
    const SUNSHINE_HOST = process.env.SUNSHINE_HOST || 'localhost';
    const SUNSHINE_PORT = parseInt(process.env.SUNSHINE_PORT || '47990', 10);
    const SUNSHINE_USER = process.env.SUNSHINE_USER || 'admin';
    const SUNSHINE_PASS = process.env.SUNSHINE_PASS || 'admin1234';

    const getJsonBody = (req) => new Promise((resolve) => {
      let body = '';
      req.on('data', chunk => body += chunk);
      req.on('end', () => {
        try {
          resolve(JSON.parse(body || '{}'));
        } catch (e) {
          resolve({});
        }
      });
    });

    const refreshServer = http.createServer((req, res) => {
      const urlParsed = require('url').parse(req.url, true);
      const corsHeaders = {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Authorization, Content-Type, x-session-token',
      };

      if (req.method === 'OPTIONS') {
        res.writeHead(204, corsHeaders);
        res.end();
        return;
      }

      res.setHeader('Content-Type', 'application/json');
      Object.entries(corsHeaders).forEach(([k, v]) => res.setHeader(k, v));

      if (req.url === '/api/connect' || urlParsed.pathname === '/api/connect') {
        res.writeHead(200);
        res.end(JSON.stringify({ token: sessionToken, ip: getLocalIP(), wsPort: WS_PORT }));
        return;
      }

      if (urlParsed.pathname === '/api/token-refresh') {
        const clientToken = urlParsed.query.token || req.headers['x-session-token'];
        if (isValidToken(clientToken)) {
          res.writeHead(200);
          res.end(JSON.stringify({ token: sessionToken, rotated: clientToken !== sessionToken }));
        } else {
          res.writeHead(401);
          res.end(JSON.stringify({ error: 'Token expired. Re-pair required.' }));
        }
        return;
      }

      if (urlParsed.pathname === '/api/ip') {
        try {
          const interfaces = os.networkInterfaces();
          const addresses = [];
          for (const [name, nets] of Object.entries(interfaces)) {
            if (!nets) continue;
            for (const net of nets) {
              if (net.family === 'IPv4' && !net.internal) {
                let type = 'unknown';
                const lower = name.toLowerCase();
                if (lower.includes('wi-fi') || lower.includes('wlan') || lower.includes('wireless')) {
                  type = 'wifi';
                } else if (lower.includes('eth') || lower.includes('ethernet') || lower.includes('lan')) {
                  type = 'ethernet';
                } else if (lower.includes('tailscale')) {
                  type = 'tailscale';
                } else if (lower.includes('vpn') || lower.includes('tun')) {
                  type = 'vpn';
                }
                addresses.push({
                  name,
                  ip: net.address,
                  mac: net.mac,
                  type,
                });
              }
            }
          }
          const typePriority = { ethernet: 0, wifi: 1, tailscale: 2, vpn: 3, unknown: 4 };
          addresses.sort((a, b) => {
            const pa = typePriority[a.type] ?? 99;
            const pb = typePriority[b.type] ?? 99;
            if (pa !== pb) return pa - pb;
            return a.ip.localeCompare(b.ip);
          });
          res.writeHead(200);
          res.end(JSON.stringify({
            success: true,
            addresses,
            primaryIp: addresses.length > 0 ? addresses[0].ip : '127.0.0.1',
            interfaceCount: addresses.length,
            hostname: os.hostname(),
          }));
        } catch (error) {
          res.writeHead(500);
          res.end(JSON.stringify({ success: false, error: error.message, primaryIp: '127.0.0.1' }));
        }
        return;
      }

      if (urlParsed.pathname === '/api/device') {
        const requestedSerial = urlParsed.query.serial;
        const { exec } = require('child_process');
        exec(`"${ADB}" devices -l`, { timeout: 3000 }, (err, stdout) => {
          if (err) {
            res.writeHead(500);
            res.end(JSON.stringify({ connected: false, error: err.message }));
            return;
          }
          try {
            const lines = stdout.trim().split('\n').slice(1);
            const devicesList = [];
            for (const line of lines) {
              const parts = line.trim().split(/\s+/);
              if (parts.length >= 2) {
                const serial = parts[0];
                const state = parts[1];
                if (['device', 'offline', 'unauthorized', 'recovery', 'sideload'].includes(state)) {
                  let model = 'Dispositivo';
                  const modelMatch = line.match(/model:(\S+)/);
                  if (modelMatch) {
                    model = modelMatch[1].replace(/_/g, ' ');
                  } else {
                    model = `Dispositivo (${state})`;
                  }
                  const isWifi = !!serial.match(/\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d+\b/);
                  const connectionType = isWifi ? 'Wi-Fi' : 'USB';
                  devicesList.push({ serial, model, connectionType, state });
                }
              }
            }

            if (devicesList.length === 0) {
              const psScript = `Get-PnpDevice -PresentOnly | Where-Object FriendlyName -match 'Android|Fastboot|SAMSUNG Mobile|ADB' | Where-Object Class -match 'USB|Modem|AndroidUsbDeviceClass' | Select-Object -ExpandProperty FriendlyName -First 1`;
              exec(`powershell -NoProfile -Command "${psScript}"`, { timeout: 4000 }, (errPs, stdoutPs) => {
                const hwName = (stdoutPs || '').trim();
                if (hwName) {
                  const cleanName = hwName.replace(/Mobile|USB|CDC|Composite|Device|Modem|#\\d+/ig, '').trim() || 'Dispositivo Hardware';
                  const fallbackDev = {
                    connected: true,
                    connectionType: 'USB (Bootloader / Cargando)',
                    model: cleanName,
                    androidVersion: 'Modo Offline Especial',
                    serial: 'Hardware Level',
                    ram: '--',
                    storage: '--',
                    battery: 0,
                    isCharging: true,
                    temperature: '--',
                    state: 'offline',
                    oemUnlockAllowed: false,
                    bootloaderLocked: true,
                    verifiedBootState: 'unknown',
                    vbmetaState: 'unknown'
                  };
                  res.writeHead(200);
                  res.end(JSON.stringify({
                    connected: true,
                    devices: [{ serial: 'Hardware Level', model: cleanName, connectionType: 'USB', state: 'offline' }],
                    activeDevice: fallbackDev
                  }));
                } else {
                  res.writeHead(200);
                  res.end(JSON.stringify({ connected: false, devices: [], activeDevice: null }));
                }
              });
              return;
            }

            let targetDevice = devicesList[0];
            if (requestedSerial) {
              const found = devicesList.find(d => d.serial === requestedSerial);
              if (found) targetDevice = found;
            }

            if (targetDevice.state !== 'device') {
              let friendlyModel = targetDevice.model;
              if (targetDevice.state === 'unauthorized') {
                friendlyModel = `${targetDevice.model} (No Autorizado)`;
              } else if (targetDevice.state === 'offline') {
                friendlyModel = `${targetDevice.model} (Offline)`;
              }
              res.writeHead(200);
              res.end(JSON.stringify({
                connected: true,
                devices: devicesList,
                activeDevice: {
                  connected: true,
                  connectionType: targetDevice.connectionType,
                  model: friendlyModel,
                  androidVersion: 'Requiere Autorización',
                  serial: targetDevice.serial,
                  ram: '--',
                  storage: '--',
                  battery: 0,
                  isCharging: false,
                  temperature: '--',
                  state: targetDevice.state,
                  oemUnlockAllowed: false,
                  bootloaderLocked: true,
                  verifiedBootState: 'unknown',
                  vbmetaState: 'unknown'
                }
              }));
              return;
            }

            const adbTarget = `"${ADB}" -s ${targetDevice.serial}`;
            exec(`${adbTarget} shell getprop`, { timeout: 3000 }, (errProp, propOut) => {
              if (errProp) {
                res.writeHead(200);
                res.end(JSON.stringify({ connected: true, devices: devicesList, activeDevice: null }));
                return;
              }
              const props = {};
              (propOut || '').split('\n').forEach(line => {
                const match = line.match(/^\[(.*?)\]: \[(.*?)\]/);
                if (match) props[match[1]] = match[2];
              });

              exec(`${adbTarget} shell dumpsys battery`, { timeout: 2000 }, (errBat, batteryOut) => {
                let batteryLevel = 0;
                let tempRaw = 0;
                let isCharging = false;
                (batteryOut || '').split('\n').forEach(line => {
                  if (line.includes('level:')) batteryLevel = parseInt(line.split(':')[1].trim());
                  if (line.includes('temperature:')) tempRaw = parseInt(line.split(':')[1].trim());
                  if (line.includes('status:')) {
                    const statusStr = line.split(':')[1].trim();
                    if (statusStr === '2' || statusStr === '5') isCharging = true;
                  }
                });
                const temperature = (tempRaw / 10).toFixed(1);

                exec(`${adbTarget} shell cat /proc/meminfo`, { timeout: 2000 }, (errMem, memOut) => {
                  let totalMemKB = 0;
                  (memOut || '').split('\n').forEach(line => {
                    if (line.startsWith('MemTotal:')) {
                      totalMemKB = parseInt(line.replace(/[^0-9]/g, ''));
                    }
                  });
                  const ramGB = Math.round(totalMemKB / 1024 / 1024);

                  exec(`${adbTarget} shell df -h /data`, { timeout: 2000 }, (errDf, dfOut) => {
                    let storageCap = '128 GB';
                    const dfLines = (dfOut || '').trim().split('\n');
                    if (dfLines.length > 1) {
                      const columns = dfLines[1].trim().split(/\s+/);
                      if (columns.length >= 2) {
                        const rawSize = parseFloat(columns[1].replace(/[^0-9.]/g, ''));
                        const standards = [8, 16, 32, 64, 128, 256, 512, 1024];
                        let bestFit = standards[0];
                        for (const s of standards) {
                          if (rawSize <= s * 0.98) { bestFit = s; break; }
                        }
                        if (rawSize > 1000) bestFit = Math.ceil(rawSize);
                        storageCap = `${bestFit} GB`;
                      }
                    }

                    exec(`${adbTarget} shell wm size`, { timeout: 2000 }, (errSize, sizeOut) => {
                      let resolution = 'Desconocida';
                      const sizeMatch = (sizeOut || '').match(/Physical size:\s*(\d+x\d+)/);
                      if (sizeMatch) resolution = sizeMatch[1].replace('x', ' × ');

                      const rawModel = props['ro.product.model'] || targetDevice.model;
                      const marketName = props['ro.product.marketname'] || props['ro.vendor.oplus.market.name'] || props['bluetooth.device.default_name'] || rawModel;
                      const preciseModel = (marketName !== rawModel && rawModel !== 'Dispositivo') ? `${marketName} (${rawModel})` : marketName;

                      res.writeHead(200);
                      res.end(JSON.stringify({
                        connected: true,
                        devices: devicesList,
                        activeDevice: {
                          connected: true,
                          connectionType: targetDevice.connectionType,
                          model: preciseModel,
                          androidVersion: props['ro.build.version.release'] || 'Unknown',
                          serial: targetDevice.serial,
                          resolution,
                          ram: `${ramGB} GB`,
                          storage: storageCap,
                          battery: batteryLevel,
                          isCharging,
                          temperature,
                          state: 'device',
                          oemUnlockAllowed: props['sys.oem_unlock_allowed'] === '1',
                          bootloaderLocked: props['ro.boot.flash.locked'] !== '0',
                          verifiedBootState: props['ro.boot.verifiedbootstate'] || 'unknown',
                          vbmetaState: props['ro.boot.vbmeta.device_state'] || 'unknown'
                        }
                      }));
                    });
                  });
                });
              });
            });
          } catch (e) {
            res.writeHead(500);
            res.end(JSON.stringify({ connected: false, error: e.message }));
          }
        });
        return;
      }

      if (urlParsed.pathname === '/api/actions' && req.method === 'POST') {
        getJsonBody(req).then(async (body) => {
          const { action, ip, serial, force, pin, keycode } = body;
          try {
            const execAsync = (cmd) => new Promise((resolve, reject) => {
              exec(cmd, (err, stdout) => { if (err) reject(err); else resolve(stdout); });
            });
            const { stdout: devicesOut } = await execAsync(`"${ADB}" devices`);
            const id = devicesOut.split('\n').slice(1).find(l => l.includes('\tdevice'))?.split(/\s+/)[0];
            const targetSerial = serial || ip || id;
            const adbTarget = targetSerial ? `"${ADB}" -s ${targetSerial}` : `"${ADB}"`;

            if (action === 'open_screen') {
              try {
                const iconSrc = 'C:\\AndroProject\\AndroProject.png';
                const iconDst = 'C:\\AndroProject\\scrcpy.png';
                if (!fs.existsSync(iconDst) && fs.existsSync(iconSrc)) {
                  fs.copyFileSync(iconSrc, iconDst);
                }
              } catch (_) {}

              const serialClean = targetSerial ? targetSerial.replace(/[:.]/g, '_') : 'default';
              const lockFile = `C:\\AndroProject\\.androidproject_active_${serialClean}`;
              const psCheck = `Get-CimInstance Win32_Process -Filter "Name='AndroProject.exe' AND CommandLine LIKE '%${targetSerial}%'" | Select-Object -ExpandProperty ProcessId`;
              try {
                const checkOut = await execAsync(`powershell -NoProfile -Command "${psCheck}"`);
                if (checkOut.trim()) {
                  if (force) {
                    const psKill = `Get-CimInstance Win32_Process -Filter "Name='AndroProject.exe' AND CommandLine LIKE '%${targetSerial}%'" | Invoke-CimMethod -MethodName Terminate`;
                    await execAsync(`powershell -NoProfile -Command "${psKill}"`);
                  } else {
                    res.writeHead(200);
                    res.end(JSON.stringify({ success: true, message: 'La pantalla para este dispositivo ya está abierta' }));
                    return;
                  }
                }
              } catch (_) {}

              const isWifi = ip || (targetSerial && targetSerial.includes('.'));
              const bitrate = isWifi ? '4M' : '12M';
              const launchArgs = [];
              if (targetSerial) launchArgs.push('-s', String(targetSerial));
              launchArgs.push('-b', bitrate, '--max-size', '1920', '--max-fps', '60', '--no-audio', '--stay-awake');

              const proc = spawn(ANDROPROJECT_BIN, launchArgs, {
                cwd: 'C:\\AndroProject',
                env: {
                  ...process.env,
                  ADB: ADB,
                  SCRCPY_SERVER_PATH: 'AndroProject-server',
                  SCRCPY_ICON_PATH: 'AndroProject.png',
                },
                windowsHide: false,
              });

              let errorOutput = '';
              proc.stderr?.on('data', (d) => { errorOutput += d.toString(); });
              proc.stdout?.on('data', (d) => { errorOutput += d.toString(); });
              proc.on('error', (err) => { errorOutput += `spawn error: ${err.message}\n`; });

              if (proc.pid) fs.writeFileSync(lockFile, String(proc.pid));

              proc.on('exit', (code) => {
                try { fs.writeFileSync('C:\\AndroProject\\scrcpy_debug.log', `Exit code: ${code}\nOutput:\n${errorOutput}`); } catch (_) {}
                try {
                  if (fs.existsSync(lockFile)) {
                    const currentPid = parseInt(fs.readFileSync(lockFile, 'utf8').trim(), 10);
                    if (currentPid === proc.pid) fs.unlinkSync(lockFile);
                  }
                } catch (_) {}
              });
              
              res.writeHead(200);
              res.end(JSON.stringify({ success: true, message: 'Transmisión de pantalla iniciada' }));
              return;
            }

            if (action === 'restart_adb') {
              await execAsync(`"${ADB}" kill-server`).catch(() => {});
              await execAsync(`"${ADB}" start-server`).catch(() => {});
              res.writeHead(200);
              res.end(JSON.stringify({ success: true, message: 'ADB reiniciado correctamente' }));
              return;
            }

            if (action === 'enable_wifi') {
              try {
                const ipOut = await execAsync(`${adbTarget} shell ip route`);
                const ipLine = ipOut.split('\n').find(line => (line.includes('wlan') || line.includes('eth')) && line.includes('src'));
                if (!ipLine) throw new Error('No se pudo encontrar la IP del celular.');
                const match = ipLine.match(/src\s+([0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3})/);
                if (!match) throw new Error('No se pudo descifrar la IP.');
                const deviceIp = match[1];

                if (devicesOut.includes(`${deviceIp}:5555`)) {
                  res.writeHead(200);
                  res.end(JSON.stringify({ success: true, message: `Wi-Fi silencioso ya activo (${deviceIp}).`, ip: deviceIp }));
                  return;
                }

                await execAsync(`${adbTarget} tcpip 5555`);
                await new Promise(r => setTimeout(r, 2000));
                await execAsync(`"${ADB}" connect ${deviceIp}:5555`);
                
                res.writeHead(200);
                res.end(JSON.stringify({ success: true, message: `Conexión Wi-Fi establecida (${deviceIp}).`, ip: deviceIp }));
              } catch (err) {
                res.writeHead(200);
                res.end(JSON.stringify({ success: false, error: err.message }));
              }
              return;
            }

            if (action === 'reboot') {
              await execAsync(`${adbTarget} reboot`);
              res.writeHead(200); res.end(JSON.stringify({ success: true, message: 'Reiniciando dispositivo...' }));
              return;
            }
            if (action === 'reboot_bootloader') {
              await execAsync(`${adbTarget} reboot bootloader`);
              res.writeHead(200); res.end(JSON.stringify({ success: true, message: 'Reiniciando en modo Bootloader...' }));
              return;
            }
            if (action === 'reboot_recovery') {
              await execAsync(`${adbTarget} reboot recovery`);
              res.writeHead(200); res.end(JSON.stringify({ success: true, message: 'Reiniciando en modo Recovery...' }));
              return;
            }
            if (action === 'power_off') {
              await execAsync(`${adbTarget} shell reboot -p`);
              res.writeHead(200); res.end(JSON.stringify({ success: true, message: 'Apagando dispositivo...' }));
              return;
            }

            if (action === 'screenshot') {
              if (!fs.existsSync(SCREENSHOTS_DIR)) fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
              const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
              const devicePath = `/sdcard/screenshot_${timestamp}.png`;
              const localPath = path.join(SCREENSHOTS_DIR, `screenshot_${timestamp}.png`);
              
              await execAsync(`${adbTarget} shell screencap -p "${devicePath}"`);
              await execAsync(`${adbTarget} pull "${devicePath}" "${localPath}"`);
              await execAsync(`${adbTarget} shell rm "${devicePath}"`);
              
              exec(`explorer "${SCREENSHOTS_DIR}"`);
              res.writeHead(200); res.end(JSON.stringify({ success: true, message: 'Captura guardada' }));
              return;
            }

            if (action === 'keyevent') {
              await execAsync(`${adbTarget} shell input keyevent ${keycode}`);
              res.writeHead(200); res.end(JSON.stringify({ success: true, message: `Tecla enviada: ${keycode}` }));
              return;
            }

            if (action === 'pair_pin') {
              const pair = () => new Promise((resolvePair, rejectPair) => {
                const https = require('https');
                const basicAuth = 'Basic ' + Buffer.from(`${SUNSHINE_USER}:${SUNSHINE_PASS}`).toString('base64');
                const csrfReq = https.request({
                  hostname: SUNSHINE_HOST,
                  port: SUNSHINE_PORT,
                  path: '/api/csrf-token',
                  method: 'GET',
                  rejectUnauthorized: false,
                  headers: { 'Authorization': basicAuth }
                }, (csrfRes) => {
                  let bodyData = '';
                  csrfRes.on('data', chunk => bodyData += chunk);
                  csrfRes.on('end', () => {
                    let csrfToken = '';
                    if (csrfRes.statusCode === 200) {
                      try {
                        const json = JSON.parse(bodyData);
                        csrfToken = json.token || json.csrfToken || '';
                      } catch (e) {
                        if (csrfRes.headers['x-csrf-token']) csrfToken = csrfRes.headers['x-csrf-token'];
                      }
                    }
                    const postData = JSON.stringify({ pin });
                    const postOptions = {
                      hostname: SUNSHINE_HOST,
                      port: SUNSHINE_PORT,
                      path: '/api/pin',
                      method: 'POST',
                      rejectUnauthorized: false,
                      headers: {
                        'Content-Type': 'application/json',
                        'Content-Length': String(postData.length),
                        'Authorization': basicAuth
                      }
                    };
                    if (csrfToken) postOptions.headers['X-CSRF-Token'] = csrfToken;
                    const postReq = https.request(postOptions, (postRes) => {
                      let responseBody = '';
                      postRes.on('data', chunk => responseBody += chunk);
                      postRes.on('end', () => {
                        if (postRes.statusCode >= 200 && postRes.statusCode < 300) {
                          resolvePair({ success: true, message: 'Enlazado exitosamente en Sunshine' });
                        } else {
                          rejectPair(new Error(`Sunshine code ${postRes.statusCode}: ${responseBody}`));
                        }
                      });
                    });
                    postReq.write(postData);
                    postReq.end();
                  });
                });
                csrfReq.on('error', () => {
                  const postData = JSON.stringify({ pin });
                  const postReq = https.request({
                    hostname: SUNSHINE_HOST,
                    port: SUNSHINE_PORT,
                    path: '/api/pin',
                    method: 'POST',
                    rejectUnauthorized: false,
                    headers: {
                      'Content-Type': 'application/json',
                      'Content-Length': postData.length,
                      'Authorization': basicAuth
                    }
                  }, (postRes) => {
                    let responseBody = '';
                    postRes.on('data', chunk => responseBody += chunk);
                    postRes.on('end', () => {
                      if (postRes.statusCode >= 200 && postRes.statusCode < 300) {
                        resolvePair({ success: true, message: 'Enlazado exitosamente' });
                      } else {
                        rejectPair(new Error(`Sunshine code ${postRes.statusCode}`));
                      }
                    });
                  });
                  postReq.write(postData);
                  postReq.end();
                });
                csrfReq.end();
              });
              const pairRes = await pair();
              res.writeHead(200); res.end(JSON.stringify(pairRes));
              return;
            }

            if (action === 'auto_detect') {
              const { lanIp, tailscaleIp } = getLocalIPs();
              const activeIp = (lanIp && lanIp !== '127.0.0.1') ? lanIp : tailscaleIp;
              if (!activeIp || activeIp === '127.0.0.1') {
                res.writeHead(200); res.end(JSON.stringify({ success: false, error: 'No IP found' }));
                return;
              }
              const { uuid: finalUuid, hostname: finalHostname } = await getSunshineServerInfo();
              const broadcastCmd = `${adbTarget} shell am broadcast -a com.limelight.smartdisplay.ADD_PC -p com.limelight.smartdisplay.debug --es IP ${lanIp} --es TAILSCALE_IP ${tailscaleIp} --es NAME "${finalHostname || 'SmartDisplay_PC'}" --es UUID ${finalUuid || 'default'}`;
              await execAsync(broadcastCmd).catch(() => {});
              res.writeHead(200); res.end(JSON.stringify({ success: true, message: `PC detectado. IP inyectada.`, ip: lanIp }));
              return;
            }

            if (action === 'launch_stream') {
              const { lanIp, tailscaleIp } = getLocalIPs();
              const activeIp = (lanIp && lanIp !== '127.0.0.1') ? lanIp : tailscaleIp;
              const { uuid: finalUuid, hostname: finalHostname } = await getSunshineServerInfo();
              const packageId = "com.limelight.smartdisplay.debug";
              const activityClass = "com.limelight.ShortcutTrampoline";
              const launchCmd = `${adbTarget} shell am start -n ${packageId}/${activityClass} -a android.intent.action.DEFAULT --es uuid ${finalUuid || 'default'} --es name "${finalHostname || 'SmartDisplay_PC'}" --ez AUTO_CONNECT true`;
              await execAsync(launchCmd);
              res.writeHead(200); res.end(JSON.stringify({ success: true, message: 'Proyección iniciada en el celular.' }));
              return;
            }

            res.writeHead(400);
            res.end(JSON.stringify({ success: false, error: 'Acción no reconocida' }));
          } catch (e) {
            res.writeHead(500);
            res.end(JSON.stringify({ success: false, error: e.message }));
          }
        });
        return;
      }

      if (urlParsed.pathname === '/api/status') {
        res.writeHead(200);
        res.end(JSON.stringify({
          clients: streamClients.size + mobileClients.size,
          stream: isCapturing,
          uptime: process.uptime(),
        }));
        return;
      }

      res.writeHead(404);
      res.end(JSON.stringify({ error: 'Not found' }));
    });

    // Try to start on 3001; if port is busy, log warning (non-fatal)
    refreshServer.listen(TOKEN_REFRESH_PORT, '0.0.0.0', () => {
      console.log(`[HTTP] API server on http://0.0.0.0:${TOKEN_REFRESH_PORT}`);
    });
    refreshServer.on('error', (e) => {
      if (e.code === 'EADDRINUSE') {
        console.warn(`[HTTP] Port ${TOKEN_REFRESH_PORT} in use — API server not started`);
      }
    });
  } catch (e) {
    console.error('[WS] Failed to initialize WebSocket Server:', e);
  }
}

// ─── ADB network radar ────────────────────────────────────────────────────────

function isNativeWindowAlive(lockFile) {
  return new Promise((resolve) => {
    let pid;
    try {
      pid = parseInt(fs.readFileSync(lockFile, 'utf8').trim(), 10);
    } catch (_) { return resolve(false); }
    if (!pid || Number.isNaN(pid)) return resolve(false);
    execFile(
      'tasklist',
      ['/FI', `PID eq ${pid}`, '/FI', 'IMAGENAME eq AndroProject.exe', '/NH', '/FO', 'CSV'],
      { windowsHide: true },
      (err, stdout) => {
        if (err) return resolve(false);
        resolve(/AndroProject\.exe/i.test(stdout));
      }
    );
  });
}

let isCapturing = false;

function startCapture() {
  if (isCapturing) return;
  isCapturing = true;
  console.log('[Capture] Solicitando inicio de captura al celular...');
  const { lanIp, tailscaleIp } = getLocalIPs();
  const localIp = (lanIp && lanIp !== '127.0.0.1') ? lanIp : tailscaleIp;
  adb(['shell', 'am', 'broadcast', '-a', 'com.limelight.smartdisplay.START_SCREEN_CAPTURE', '-p', 'com.limelight.smartdisplay.debug', '--es', 'PC_IP', localIp, '--es', 'TOKEN', sessionToken])
    .then((out) => console.log('[Capture] Broadcast enviado:', out.trim()))
    .catch((err) => console.error('[Capture] Error enviando broadcast:', err));
  broadcastStreamStatus(true);
}

function stopCapture() {
  if (!isCapturing) return;
  isCapturing = false;
  console.log('[Capture] Deteniendo captura en el celular...');
  adb(['shell', 'am', 'broadcast', '-a', 'com.limelight.smartdisplay.STOP_SCREEN_CAPTURE', '-p', 'com.limelight.smartdisplay.debug']).catch(() => {});
  broadcastStreamStatus(false);
}

// ─── (Next.js server removed — replaced by native UI renderer) ────────────

// ─── IPC Handlers (renderer ↔ main process) ────────────────────────────────

function setupIPC() {
  // Session
  ipcMain.handle('get-token', () => sessionToken);
  ipcMain.handle('get-local-ip', () => getLocalIP());

  // Status
  ipcMain.handle('get-status', () => ({
    clients: streamClients.size + mobileClients.size,
    device: selectedSerial || null,
    stream: isCapturing,
  }));

  // Devices
  ipcMain.handle('get-devices', async () => {
    try {
      const out = await new Promise((resolve, reject) => {
        execFile(ADB, ['devices'], (err, stdout) => {
          if (err) reject(err); else resolve(stdout);
        });
      });
      const lines = out.split('\n').slice(1).filter(l => l.trim());
      return lines.map(line => {
        const parts = line.split('\t');
        const serial = parts[0].trim();
        const status = parts[1] ? parts[1].trim() : 'unknown';
        return { serial, online: status === 'device', name: serial };
      });
    } catch (e) {
      return [];
    }
  });
  ipcMain.handle('select-device', (_e, serial) => {
    selectedSerial = serial || null;
    broadcastStatus();
  });

  // Streaming
  ipcMain.handle('start-stream', () => { startCapture(); return true; });
  ipcMain.handle('stop-stream', () => { stopCapture(); return true; });

  // PC Input (native agent)
  ipcMain.handle('input-click', (_e, x, y) => {
    if (inputAgentProcess && inputAgentProcess.stdin.writable)
      inputAgentProcess.stdin.write(`CLICK ${x} ${y}\n`);
  });
  ipcMain.handle('input-right-click', () => {
    if (inputAgentProcess && inputAgentProcess.stdin.writable)
      inputAgentProcess.stdin.write('RCLICK -1 -1\n');
  });
  ipcMain.handle('input-move', (_e, dx, dy) => {
    if (inputAgentProcess && inputAgentProcess.stdin.writable)
      inputAgentProcess.stdin.write(`RELMOVE ${dx} ${dy}\n`);
  });
  ipcMain.handle('input-key', (_e, key) => {
    if (inputAgentProcess && inputAgentProcess.stdin.writable)
      inputAgentProcess.stdin.write(`CMD ${key}\n`);
  });
  ipcMain.handle('input-text', (_e, text) => {
    if (inputAgentProcess && inputAgentProcess.stdin.writable)
      inputAgentProcess.stdin.write(`TYPE ${text}\n`);
  });

  // Android ADB commands
  ipcMain.handle('adb-tap', async (_e, x, y) => {
    await adb(['shell', 'input', 'tap', String(x), String(y)]);
  });
  ipcMain.handle('adb-swipe', async (_e, x1, y1, x2, y2, duration) => {
    await adb(['shell', 'input', 'swipe', String(x1), String(y1), String(x2), String(y2), String(duration || 200)]);
  });
  ipcMain.handle('adb-text', async (_e, text) => {
    let textVal = text || '';
    if (textVal.length > 1000) textVal = textVal.substring(0, 1000);
    const safe = textVal.replace(/[ '"\\&|;<>()$`!]/g, '\\$&');
    await adb(['shell', 'input', 'text', safe]).catch(() => {});
  });
  ipcMain.handle('adb-keyevent', async (_e, keycode) => {
    await adb(['shell', 'input', 'keyevent', String(keycode)]);
  });

  // App control
  ipcMain.handle('app-quit', () => { isQuitting = true; app.quit(); });
  ipcMain.handle('app-minimize', () => { if (mainWindow) mainWindow.minimize(); });
  ipcMain.handle('get-version', () => app.getVersion());

  // Settings
  ipcMain.handle('set-auto-start', (_e, enabled) => {
    try { app.setLoginItemSettings({ openAtLogin: enabled, openAsHidden: true }); } catch (_) {}
  });
  ipcMain.handle('set-min-to-tray', (_e, enabled) => {
    // Persisted in memory for this session
    minToTray = enabled;
  });
  ipcMain.handle('get-settings', () => ({
    autoStart: app.getLoginItemSettings().openAtLogin || false,
    minToTray: minToTray,
  }));
}

function broadcastStatus() {
  const clientsList = Array.from(activeClients).map(c => ({
    ip: c.ip,
    type: c.type,
    connectedAt: c.connectedAt
  }));
  const data = {
    clients: streamClients.size + mobileClients.size,
    clientsList: clientsList,
    device: selectedSerial || null,
    stream: isCapturing,
  };
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('status-update', data);
  }
}

function sendToRenderer(channel, ...args) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, ...args);
  }
}

function broadcastStreamStatus(active) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('stream-status', { active });
  }
}

function broadcastClientConnected(type, count) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('client-connected', { type, count });
  }
}

// ─── Window creation ──────────────────────────────────────────────────────────

async function createWindow() {
  const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged;
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 850,
    minWidth: 950,
    minHeight: 650,
    icon: path.join(__dirname, '../public/icon.png'),
    backgroundColor: '#0a0c17',
    title: 'SmartDisplay AI',
    show: false,
    frame: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, 'preload.js'),
    },
  });
  mainWindow.setMenuBarVisibility(false);
  mainWindow.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });

  // Instead of running a background HTTP server, we now serve the statically exported Next.js UI
  const indexPath = path.join(__dirname, '..', 'out', 'index.html');
  
  try {
    if (fs.existsSync(indexPath)) {
      await mainWindow.loadFile(indexPath);
    } else {
      console.error('[Electron] Next.js out directory not found. Make sure to run "npm run build" first.');
      // Fallback empty UI or error message could go here if needed
    }
  } catch (err) {
    console.error('[Electron] Failed to load UI:', err);
  }

  mainWindow.show();
  mainWindow.focus();

  // Minimize to tray instead of closing
  mainWindow.on('close', (event) => {
    if (!isQuitting && minToTray) {
      event.preventDefault();
      mainWindow.hide();
      if (tray) {
        try {
          tray.displayBalloon({
            title: 'SmartDisplay AI',
            content: 'Sigue activo en la bandeja del sistema. Haz doble clic para restaurar.',
            iconType: 'info',
          });
        } catch (_) {}
      }
    }
  });
}

// ─── ADB auto-discovery ───────────────────────────────────────────────────────

const http = require('http');

async function getRealSunshineUUID() {
  return new Promise((resolve) => {
    http.get('http://127.0.0.1:47989/serverinfo', (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        const match = data.match(/<uniqueid>(.+?)<\/uniqueid>/);
        if (match) {
          let realUuid = match[1];
          if (realUuid.length === 32) {
            resolve(`${realUuid.slice(0, 8)}-${realUuid.slice(8, 12)}-${realUuid.slice(12, 16)}-${realUuid.slice(16, 20)}-${realUuid.slice(20)}`);
          } else {
            resolve(realUuid);
          }
        } else {
          resolve(null);
        }
      });
    }).on('error', () => resolve(null));
  });
}

async function getSunshineServerInfo() {
  return new Promise((resolve) => {
    http.get('http://127.0.0.1:47989/serverinfo', (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        const uuidMatch   = data.match(/<uniqueid>(.+?)<\/uniqueid>/);
        const hostnameMatch = data.match(/<hostname>(.+?)<\/hostname>/);
        let uuid = null;
        if (uuidMatch) {
          const raw = uuidMatch[1];
          uuid = raw.length === 32
            ? `${raw.slice(0,8)}-${raw.slice(8,12)}-${raw.slice(12,16)}-${raw.slice(16,20)}-${raw.slice(20)}`
            : raw;
        }
        resolve({
          uuid,
          hostname: hostnameMatch ? hostnameMatch[1] : null,
        });
      });
    }).on('error', () => resolve({ uuid: null, hostname: null }));
  });
}

async function autoInjectPC(isHeartbeat = false) {
  try {
    const dev = await getDevice();
    if (!dev) return; // Silent: no device connected, skip

    const { lanIp, tailscaleIp } = getLocalIPs();
    const activeIp = (lanIp && lanIp !== '127.0.0.1') ? lanIp : tailscaleIp;
    if (!activeIp || activeIp === '127.0.0.1') return;

    // Get real UUID + hostname from Sunshine so we merge with EMMA, not create a ghost
    const { uuid: sunshineUuid, hostname: sunshineHostname } = await getSunshineServerInfo();

    let finalUuid = sunshineUuid;
    if (!finalUuid) {
      // Fallback: derive deterministic UUID from IP (should almost never happen)
      const md5Hash = crypto.createHash('md5').update(activeIp).digest('hex');
      finalUuid = `${md5Hash.slice(0,8)}-${md5Hash.slice(8,12)}-3${md5Hash.slice(13,16)}-8${md5Hash.slice(17,20)}-${md5Hash.slice(20)}`;
    }

    // Use real hostname (e.g. "emma") — Android will merge this with the mDNS-discovered entry
    const finalName = sunshineHostname || os.hostname();

    const out = await adb([
      'shell', 'am', 'broadcast',
      '-a', 'com.limelight.smartdisplay.ADD_PC',
      '-p', 'com.limelight.smartdisplay.debug',
      '--es', 'IP',           lanIp,
      '--es', 'TAILSCALE_IP', tailscaleIp,
      '--es', 'NAME',         finalName,
      '--es', 'UUID',         finalUuid,
      '--ez', 'HEARTBEAT',    isHeartbeat ? 'true' : 'false'
    ]);
    console.log(`[AutoConnect] PC inyectado (${finalName} / ${lanIp} / Tailscale:${tailscaleIp} / Heartbeat:${isHeartbeat}) en ${dev}:`, out.trim());
  } catch (err) {
    // Silent: don't spam logs on network errors
  }
}

// Guard: prevent duplicate concurrent scans
let _radarRunning = false;

async function initializeADBAndRadar() {
  if (_radarRunning) return;
  _radarRunning = true;

  // Start ADB server non-blocking — fire and forget
  exec(`"${ADB}" kill-server`, () => {
    exec(`"${ADB}" start-server`, () => {
      console.log('[ADB] Servidor ADB iniciado.');

      // Network scan to find ADB over WiFi (async, non-blocking)
      const psScript = [
        '$localIp = (Get-NetIPAddress -AddressFamily IPv4',
        '  | Where-Object { $_.InterfaceAlias -match "Wi-Fi|Ethernet" -and $_.IPAddress -notlike "169.254*" }',
        '  | Select-Object -First 1).IPAddress',
        'if (-not $localIp) { exit }',
        '$base = $localIp.Substring(0, $localIp.LastIndexOf("."))',
        '$jobs = 1..254 | ForEach-Object {',
        '  $ip = "$base.$_"',
        '  $tcp = New-Object System.Net.Sockets.TcpClient',
        '  @{ IP = $ip; R = $tcp.BeginConnect($ip, 5555, $null, $null); T = $tcp }',
        '}',
        'Start-Sleep -Milliseconds 700',
        'foreach ($j in $jobs) {',
        '  if ($j.R.IsCompleted -and $j.T.Connected) { Write-Output $j.IP; $j.T.Close(); exit }',
        '  try { $j.T.Close() } catch {}',
        '}',
      ].join('\n');

      const encoded = Buffer.from(psScript, 'utf16le').toString('base64');
      exec(
        `powershell -ExecutionPolicy Bypass -NoProfile -NonInteractive -EncodedCommand ${encoded}`,
        { timeout: 8000 },
        (err, stdout) => {
          _radarRunning = false;
          const ip = (stdout || '').trim();
          if (ip) {
            console.log(`[ADB] Dispositivo WiFi encontrado en ${ip}:5555, conectando...`);
            exec(`"${ADB}" connect ${ip}:5555`, () => {
              setTimeout(autoInjectPC, 2000);
            });
          } else {
            // No WiFi device found — still try autoInject in case USB connected
            setTimeout(autoInjectPC, 2000);
          }
        }
      );
    });
  });
}

// ─── Graceful shutdown ────────────────────────────────────────────────────────

function doQuit() {
  stopCapture();
  stopPCScreenStreaming();
  if (wss) try { wss.close(); } catch (_) {}
  if (inputAgentProcess) {
    try { inputAgentProcess.kill(); } catch (_) {}
    inputAgentProcess = null;
  }
  if (powerBlockId !== null) {
    try { powerSaveBlocker.stop(powerBlockId); } catch (_) {}
    powerBlockId = null;
  }
  app.quit();
}

// ─── App lifecycle ────────────────────────────────────────────────────────────

app.whenReady().then(async () => {
  // Auto-start with Windows (production builds only)
  if (app.isPackaged) {
    app.setLoginItemSettings({ openAtLogin: true, openAsHidden: true });
  }

  setupIPC();
  startWebSocketServer();
  setupClipboardSync();
  createTray();
  initializeADBAndRadar();

  // Periodically inject PC IP to connected devices (Heartbeat)
  setInterval(() => autoInjectPC(true), 15000);

  // Forward console.log/error/warn to renderer log panel with reentrancy guard
  const origLog = console.log.bind(console);
  const origError = console.error.bind(console);
  const origWarn = console.warn.bind(console);
  let isLogging = false;

  console.log = (...args) => {
    origLog(...args);
    if (isLogging) return;
    isLogging = true;
    try {
      sendToRenderer('app-log', args.join(' '));
    } catch (_) {}
    isLogging = false;
  };
  console.error = (...args) => {
    origError(...args);
    if (isLogging) return;
    isLogging = true;
    try {
      sendToRenderer('app-log', 'ERROR: ' + args.join(' '));
    } catch (_) {}
    isLogging = false;
  };
  console.warn = (...args) => {
    origWarn(...args);
    if (isLogging) return;
    isLogging = true;
    try {
      sendToRenderer('app-log', 'WARN: ' + args.join(' '));
    } catch (_) {}
    isLogging = false;
  };

  // Auto sync background process
  spawn('node', ['scratch_auto_sync.js'], {
    cwd: path.join(__dirname, '..'),
    stdio: 'ignore',
    windowsHide: true,
  });

  await createWindow();
});

// Keep running in background when window is closed (tray mode)
app.on('window-all-closed', () => {
  if (isQuitting) doQuit();
  // Otherwise: stays alive via tray
});

// Ensure graceful shutdown on system quit/restart
app.on('before-quit', () => { isQuitting = true; });
