/**
 * SmartDisplay AI — Renderer / UI Logic
 *
 * Communicates with the main process via window.api (exposed by preload.js).
 * No direct Node.js or Electron access.
 */

/* ── DOM references ─────────────────────────────────────────────────── */
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

const $log = $('#logOutput');
const $statusDot = $('#statusIndicator');
const $statusText = $('#statusText');

/* ── Log buffer — prevents DOM blowup ───────────────────────────────── */
const MAX_LOG_LINES = 500;
let logLineCount = 0;

function log(message, level = 'info') {
  const line = document.createElement('div');
  line.className = `log-line ${level}`;
  const now = new Date();
  const ts = now.toLocaleTimeString('es-MX', { hour12: false });
  line.textContent = `[${ts}] ${message}`;
  $log.appendChild(line);
  logLineCount++;

  // Trim oldest lines to prevent memory leak / DOM overflow
  if (logLineCount > MAX_LOG_LINES) {
    const toRemove = logLineCount - MAX_LOG_LINES;
    for (let i = 0; i < toRemove; i++) {
      if ($log.firstChild) $log.removeChild($log.firstChild);
    }
    logLineCount = MAX_LOG_LINES;
  }

  $log.scrollTop = $log.scrollHeight;
}

/* ── Status indicator ───────────────────────────────────────────────── */
function setStatus(state, text) {
  $statusDot.className = 'status-dot ' + state;
  $statusText.textContent = text;
}

/* ── Navigation ─────────────────────────────────────────────────────── */
$$('.nav-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    $$('.nav-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    $$('.panel').forEach(p => p.classList.remove('active'));
    const panel = document.getElementById('panel-' + btn.dataset.panel);
    if (panel) panel.classList.add('active');
  });
});

/* ── Title bar buttons ──────────────────────────────────────────────── */
$('#btnQuit').addEventListener('click', () => window.api.quit());
$('#btnMinimize').addEventListener('click', () => window.api.minimize());

/* ── Full token storage ─────────────────────────────────────────────── */
let _fullToken = null;

/* ── Initialize ─────────────────────────────────────────────────────── */
async function init() {
  log('Iniciando SmartDisplay AI...', 'info');

  // Token — store full, display truncated
  try {
    const token = await window.api.getToken();
    _fullToken = token || null;
    $('#tokenDisplay').textContent = token ? token.substring(0, 16) + '…' : '—';
    $('#tokenDisplay').title = token || '';
  } catch (e) { log('Error al obtener token: ' + e.message, 'error'); }

  // IP
  try {
    const ip = await window.api.getLocalIP();
    $('#statIP').textContent = ip || '—';
  } catch (e) { /* ignore */ }

  // Version
  try {
    const ver = await window.api.getVersion();
    $('#appVersion').textContent = ver || '—';
  } catch (e) { /* ignore */ }

  // Load saved settings
  try {
    const settings = await window.api.getSettings();
    if ($('#chkAutoStart')) $('#chkAutoStart').checked = settings.autoStart;
    if ($('#chkMinToTray')) $('#chkMinToTray').checked = settings.minToTray;
  } catch (e) { /* ignore */ }

  // Settings handlers
  const chkAuto = $('#chkAutoStart');
  if (chkAuto) chkAuto.addEventListener('change', (e) => {
    window.api.setAutoStart(e.target.checked);
    log(`Inicio automático: ${e.target.checked ? 'activado' : 'desactivado'}`, 'info');
  });

  const chkTray = $('#chkMinToTray');
  if (chkTray) chkTray.addEventListener('change', (e) => {
    window.api.setMinToTray(e.target.checked);
    log(`Minimizar a bandeja: ${e.target.checked ? 'activado' : 'desactivado'}`, 'info');
  });

  // Copy token — copies full token
  const btnCopy = $('#btnCopyToken');
  if (btnCopy) {
    btnCopy.addEventListener('click', async () => {
      try {
        const token = _fullToken || await window.api.getToken();
        if (token) {
          await navigator.clipboard.writeText(token);
          btnCopy.textContent = '✓ Copiado';
          setTimeout(() => { btnCopy.textContent = 'Copiar'; }, 1500);
          log('Token copiado al portapapeles (' + token.length + ' chars)', 'success');
        }
      } catch (e) { log('Error al copiar: ' + e.message, 'error'); }
    });
  }

  // Show full token button
  const btnShowToken = $('#btnShowToken');
  if (btnShowToken) {
    btnShowToken.addEventListener('click', async () => {
      try {
        const token = _fullToken || await window.api.getToken();
        const display = $('#tokenDisplay');
        if (display) {
          if (display.textContent.endsWith('…')) {
            display.textContent = token || '—';
            btnShowToken.textContent = '🙈';
          } else {
            display.textContent = token ? token.substring(0, 16) + '…' : '—';
            btnShowToken.textContent = '👁';
          }
        }
      } catch (e) { /* ignore */ }
    });
  }

  // Subscribe to status updates
  window.api.onStatusUpdate((data) => {
    updateStats(data);
  });

  // Subscribe to stream status
  window.api.onStreamStatus((data) => {
    const statStream = $('#statStream');
    const btnStart = $('#btnStartStream');
    const btnStop = $('#btnStopStream');
    if (statStream) statStream.textContent = data.active ? 'Sí' : 'No';
    if (btnStart) btnStart.disabled = data.active;
    if (btnStop) btnStop.disabled = !data.active;
    log(data.active ? 'Stream iniciado' : 'Stream detenido', data.active ? 'success' : 'info');
  });

  // Subscribe to client connections
  window.api.onClientConnected((data) => {
    log(`Cliente conectado: ${data.type} (${data.count} totales)`, 'info');
    updateStats(data);
  });

  // Subscribe to clipboard
  window.api.onClipboardChange((text) => {
    if (text) log(`Clipboard sync: "${text.substring(0, 60)}${text.length > 60 ? '…' : ''}"`, 'info');
  });

  // Subscribe to logs from main process
  window.api.onLog((line) => {
    const level = line.startsWith('ERROR:') ? 'error' : line.startsWith('WARN:') ? 'warn' : 'info';
    log(line, level);
  });

  // Token rotation notification
  if (window.api.onTokenRotated) {
    window.api.onTokenRotated(async () => {
      log('Token rotado — actualizando...', 'warn');
      try {
        const token = await window.api.getToken();
        _fullToken = token || null;
        const display = $('#tokenDisplay');
        if (display) {
          display.textContent = token ? token.substring(0, 16) + '…' : '—';
          display.title = token || '';
        }
      } catch (_) {}
    });
  }

  // Load devices
  await refreshDevices();
  setStatus('online', 'Conectado');

  log('SmartDisplay AI listo ✓', 'success');
}

/* ── Dashboard stats ────────────────────────────────────────────────── */
function updateStats(data) {
  if (data.clients !== undefined) {
    const el = $('#statClients');
    if (el) el.textContent = data.clients;
  }
  if (data.device !== undefined) {
    const el = $('#statDevice');
    if (el) el.textContent = data.device || '—';
  }
  if (data.stream !== undefined) {
    const el = $('#statStream');
    if (el) el.textContent = data.stream ? 'Sí' : 'No';
  }
  if (data.clientsList !== undefined) {
    updateClientList(data.clientsList);
  } else if (data.clients !== undefined) {
    updateClientListOld(data.clients);
  }
}

function updateClientList(clientsList) {
  const container = $('#streamClientList');
  if (!container) return;
  if (clientsList && clientsList.length > 0) {
    container.innerHTML = clientsList.map((c) => {
      const typeLabel = c.type === 'mobile' ? '📱 Celular (Controles)' :
                        c.type === 'android-stream' ? '🎬 Celular (Stream)' :
                        c.type === 'dashboard' ? '📊 Dashboard Web/PC' : `🔌 Cliente: ${c.type}`;
      return `
        <div class="client-item" style="display:flex;justify-content:space-between;align-items:center;padding:8px 12px;background:rgba(255,255,255,0.03);border:1px solid rgba(255,255,255,0.05);border-radius:8px;margin-bottom:6px;">
          <div>
            <div style="font-weight:bold;font-size:13px;">${typeLabel}</div>
            <div style="font-size:11px;opacity:0.5;font-family:monospace;">IP: ${c.ip}</div>
          </div>
          <div style="font-size:11px;opacity:0.4;">Desde: ${c.connectedAt}</div>
        </div>
      `;
    }).join('');
  } else {
    container.innerHTML = '<div class="empty-state">Sin clientes conectados.</div>';
  }
}

function updateClientListOld(count) {
  const container = $('#streamClientList');
  if (!container) return;
  if (count > 0) {
    container.innerHTML = Array.from({ length: count }, (_, i) =>
      `<div class="client-item">Cliente #${i + 1} — Conectado</div>`
    ).join('');
  } else {
    container.innerHTML = '<div class="empty-state">Sin clientes conectados.</div>';
  }
}

/* ── Device management ──────────────────────────────────────────────── */
async function refreshDevices() {
  const list = $('#deviceList');
  if (!list) return;
  try {
    const devices = await window.api.getDevices();
    if (devices && devices.length > 0) {
      list.innerHTML = devices.map(d => `
        <div class="device-item">
          <div>
            <div class="device-name">${d.name || 'Dispositivo'}</div>
            <div class="device-serial">${d.serial}</div>
          </div>
          <div class="device-status">● ${d.online ? 'En línea' : 'Desconectado'}</div>
          <button class="btn-select btn-small" data-serial="${d.serial}">Seleccionar</button>
        </div>
      `).join('');
      list.querySelectorAll('.btn-select').forEach(btn => {
        btn.addEventListener('click', async () => {
          const serial = btn.dataset.serial;
          await window.api.selectDevice(serial);
          const el = $('#statDevice');
          if (el) el.textContent = serial;
          log(`Dispositivo seleccionado: ${serial}`, 'success');
        });
      });
    } else {
      list.innerHTML = '<div class="empty-state">No hay dispositivos conectados.</div>';
    }
  } catch (e) {
    list.innerHTML = '<div class="empty-state">Error al obtener dispositivos.</div>';
    log('Error refreshing devices: ' + e.message, 'error');
  }
}

/* ── Event handlers ─────────────────────────────────────────────────── */

// Scan ADB network
const btnScan = $('#btnScanADB');
if (btnScan) {
  btnScan.addEventListener('click', () => {
    log('Escaneando red para dispositivos ADB...', 'info');
    setStatus('connecting', 'Escaneando…');
    setTimeout(async () => {
      await refreshDevices();
      setStatus('online', 'Conectado');
    }, 3500);
  });
}

const btnRefresh = $('#btnRefreshDevices');
if (btnRefresh) {
  btnRefresh.addEventListener('click', async () => {
    await refreshDevices();
    log('Lista de dispositivos actualizada', 'info');
  });
}

// Stream controls
const btnStart = $('#btnStartStream');
if (btnStart) {
  btnStart.addEventListener('click', async () => {
    try {
      btnStart.disabled = true;
      await window.api.startStream();
      log('Iniciando stream...', 'info');
    } catch (e) {
      btnStart.disabled = false;
      log('Error al iniciar stream: ' + e.message, 'error');
    }
  });
}

const btnStop = $('#btnStopStream');
if (btnStop) {
  btnStop.addEventListener('click', async () => {
    try {
      await window.api.stopStream();
    } catch (e) {
      log('Error al detener stream: ' + e.message, 'error');
    }
  });
}

// Clear log
const btnClearLog = $('#btnClearLog');
if (btnClearLog) {
  btnClearLog.addEventListener('click', () => {
    $log.innerHTML = '';
    logLineCount = 0;
  });
}

// Copy log to clipboard
const btnCopyLog = $('#btnCopyLog');
if (btnCopyLog) {
  btnCopyLog.addEventListener('click', async () => {
    try {
      const lines = Array.from($log.querySelectorAll('.log-line')).map(l => l.textContent);
      await navigator.clipboard.writeText(lines.join('\n'));
      btnCopyLog.textContent = '✓';
      setTimeout(() => { btnCopyLog.textContent = '📋 Copiar'; }, 1500);
    } catch (e) {
      log('Error copiando log: ' + e.message, 'error');
    }
  });
}

/* ── Boot ───────────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  init().catch(e => {
    log('Error de inicialización: ' + e.message, 'error');
    setStatus('offline', 'Error');
  });
});
