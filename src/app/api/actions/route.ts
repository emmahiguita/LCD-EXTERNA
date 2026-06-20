import { NextResponse } from 'next/server';
import { exec, spawn } from 'child_process';
import { promisify } from 'util';
import path from 'path';
import fs from 'fs';

const execAsync = promisify(exec);
const ADB = fs.existsSync('C:\\AndroProject\\adb.exe')
  ? 'C:\\AndroProject\\adb.exe'
  : (fs.existsSync('C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe')
      ? 'C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe'
      : 'adb');
const ANDROPROJECT_BIN = 'C:\\AndroProject\\AndroProject.exe';
const FASTBOOT = 'C:\\AndroProject\\fastboot.exe';
const SCREENSHOTS_DIR = 'C:\\AndroProject\\Capturas';

// Sunshine API config — credentials from env vars, falling back to defaults for local dev.
// IMPORTANT: In production, set SUNSHINE_USER and SUNSHINE_PASS as environment variables.
const SUNSHINE_HOST = process.env.SUNSHINE_HOST || 'localhost';
const SUNSHINE_PORT = parseInt(process.env.SUNSHINE_PORT || '47990', 10);
const SUNSHINE_USER = process.env.SUNSHINE_USER || 'admin';
const SUNSHINE_PASS = process.env.SUNSHINE_PASS || 'admin1234';

const getLocalIP = () => {
  if (SUNSHINE_HOST && SUNSHINE_HOST !== 'localhost' && SUNSHINE_HOST !== '127.0.0.1') {
    return SUNSHINE_HOST;
  }
  const interfaces = require('os').networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name] || []) {
      if (net.family === 'IPv4' && !net.internal) {
        const parts = net.address.split('.').map(Number);
        if (parts[0] === 100 && parts[1] >= 64 && parts[1] <= 127) {
          return net.address;
        }
      }
    }
  }
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name] || []) {
      if (net.family === 'IPv4' && !net.internal) {
        return net.address;
      }
    }
  }
  return '127.0.0.1';
};

const getLocalIPs = () => {
  let lanIp: string | null = null;
  let tailscaleIp = SUNSHINE_HOST;
  if (tailscaleIp === 'localhost' || tailscaleIp === '127.0.0.1') {
    tailscaleIp = '';
  }

  const interfaces = require('os').networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name] || []) {
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
      for (const net of interfaces[name] || []) {
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
};

const getSunshineServerDetails = async (activeIp: string) => {
  const http = require('http');
  const getRealInfo = () => new Promise<{ uuid: string | null; hostname: string | null }>((resolve) => {
    const req = http.get('http://127.0.0.1:47989/serverinfo', (res: any) => {
      let data = '';
      res.on('data', (chunk: any) => data += chunk);
      res.on('end', () => {
        const uuidMatch = data.match(/<uniqueid>(.+?)<\/uniqueid>/);
        const hostMatch = data.match(/<hostname>(.+?)<\/hostname>/);
        let realUuid = uuidMatch ? uuidMatch[1] : null;
        let hostname = hostMatch ? hostMatch[1] : null;
        
        if (realUuid) {
          if (realUuid.length === 32) {
            realUuid = `${realUuid.slice(0, 8)}-${realUuid.slice(8, 12)}-${realUuid.slice(12, 16)}-${realUuid.slice(16, 20)}-${realUuid.slice(20)}`;
          }
        }
        resolve({ uuid: realUuid, hostname });
      });
    });
    
    req.on('error', () => resolve({ uuid: null, hostname: null }));
    req.setTimeout(2000, () => {
      req.destroy();
      resolve({ uuid: null, hostname: null });
    });
  });

  const info = await getRealInfo();
  let finalUuid = info.uuid;
  let finalHostname = info.hostname || "SmartDisplay_PC";

  if (!finalUuid) {
    const crypto = require('crypto');
    const md5Hash = crypto.createHash('md5').update(activeIp).digest('hex');
    finalUuid = `${md5Hash.slice(0, 8)}-${md5Hash.slice(8, 12)}-3${md5Hash.slice(13, 16)}-8${md5Hash.slice(17, 20)}-${md5Hash.slice(20)}`;
  }
  return { uuid: finalUuid, hostname: finalHostname };
};

export async function POST(req: Request) {
  const body = await req.json();
  const { action, ip, serial, force } = body;

  try {
    const { stdout: devicesOut } = await execAsync(`"${ADB}" devices`);
    const id = devicesOut.split('\n').slice(1).find(l => l.includes('\tdevice'))?.split(/\s+/)[0];
    const targetSerial = serial || ip || id;
    const adbTarget = targetSerial ? `"${ADB}" -s ${targetSerial}` : `"${ADB}"`;
    const fastbootTarget = (serial && serial !== 'Hardware Level') ? `"${FASTBOOT}" -s ${serial}` : `"${FASTBOOT}"`;

    if (action === 'open_screen') {
      try {
        const iconSrc = 'C:\\AndroProject\\AndroProject.png';
        const iconDst = 'C:\\AndroProject\\scrcpy.png';
        if (!fs.existsSync(iconDst) && fs.existsSync(iconSrc)) {
          fs.copyFileSync(iconSrc, iconDst);
        }
      } catch (e: any) {
        console.error('[Actions API] Failed to auto-copy scrcpy.png:', e.message);
      }

      const serialClean = targetSerial ? targetSerial.replace(/[:.]/g, '_') : 'default';
      const lockFile = `C:\\AndroProject\\.androidproject_active_${serialClean}`;
      const psCheck = `Get-CimInstance Win32_Process -Filter "Name='AndroProject.exe' AND CommandLine LIKE '%${targetSerial}%'" | Select-Object -ExpandProperty ProcessId`;
      try {
        const { stdout: checkOut } = await execAsync(`powershell -NoProfile -Command "${psCheck}"`);
        if (checkOut.trim()) {
          if (force) {
            const psKill = `Get-CimInstance Win32_Process -Filter "Name='AndroProject.exe' AND CommandLine LIKE '%${targetSerial}%'" | Invoke-CimMethod -MethodName Terminate`;
            await execAsync(`powershell -NoProfile -Command "${psKill}"`);
          } else {
            return NextResponse.json({ success: true, message: 'La pantalla para este dispositivo ya está abierta' });
          }
        }
      } catch (e) { /* ignore WMI errors */ }

      const isWifi = ip || (targetSerial && targetSerial.includes('.'));
      const bitrate = isWifi ? '4M' : '12M';

      const launchArgs: string[] = [];
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
      proc.stderr?.on('data', (data) => { errorOutput += data.toString(); });
      proc.stdout?.on('data', (data) => { errorOutput += data.toString(); });
      proc.on('error', (err) => { errorOutput += `spawn error: ${err.message}\n`; });

      if (proc.pid) {
        fs.writeFileSync(lockFile, String(proc.pid));
      }

      proc.on('exit', (code) => {
        try {
          fs.writeFileSync('C:\\AndroProject\\scrcpy_debug.log', `Exit code: ${code}\nOutput:\n${errorOutput}`);
        } catch (_) {}
        if (code !== 0) {
          try {
            const logPath = path.join(process.cwd(), 'temp', 'androproject_error.log');
            const tempDir = path.dirname(logPath);
            if (!fs.existsSync(tempDir)) fs.mkdirSync(tempDir, { recursive: true });
            fs.writeFileSync(logPath, `Exit code: ${code}\nOutput:\n${errorOutput}`);
          } catch (_) {}
        }
        try {
          if (fs.existsSync(lockFile)) {
            const currentPid = parseInt(fs.readFileSync(lockFile, 'utf8').trim(), 10);
            if (currentPid === proc.pid) {
              fs.unlinkSync(lockFile);
            }
          }
        } catch (_) {}
      });
      
      return NextResponse.json({ success: true, message: 'Transmisión de pantalla iniciada' });
    }

    if (action === 'restart_adb') {
      await execAsync(`"${ADB}" kill-server`, { timeout: 3000 }).catch(() => {});
      await execAsync(`"${ADB}" start-server`, { timeout: 5000 }).catch(() => {});
      return NextResponse.json({ success: true, message: 'ADB reiniciado correctamente' });
    }

    if (action === 'enable_wifi') {
      try {
        const { stdout: ipOut } = await execAsync(`${adbTarget} shell ip route`);
        const ipLine = ipOut.split('\n').find(line => (line.includes('wlan') || line.includes('eth')) && line.includes('src'));
        if (!ipLine) throw new Error('No se pudo encontrar la IP del celular en la interfaz inalámbrica.');
        const match = ipLine.match(/src\s+([0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3})/);
        if (!match) throw new Error('No se pudo descifrar la IP del celular.');
        const deviceIp = match[1];

        if (devicesOut.includes(`${deviceIp}:5555`)) {
          return NextResponse.json({ success: true, message: `Wi-Fi silencioso ya activo (${deviceIp}).`, ip: deviceIp });
        }

        await execAsync(`${adbTarget} tcpip 5555`);
        await new Promise(r => setTimeout(r, 2000));
        await execAsync(`"${ADB}" connect ${deviceIp}:5555`);
        
        return NextResponse.json({ success: true, message: `Conexión Wi-Fi establecida en segundo plano (${deviceIp}).`, ip: deviceIp });
      } catch (err: any) {
        return NextResponse.json({ success: false, error: err.message });
      }
    }

    if (action === 'radar') {
      const psScript = `
        $localIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -match "Wi-Fi|Ethernet" -and $_.IPAddress -notlike "169.254*" } | Select-Object -First 1).IPAddress
        if (-not $localIp) { exit }
        $base = $localIp.Substring(0, $localIp.LastIndexOf('.'))
        $ips = 1..254 | ForEach-Object { "$base.$_" }
        $results = @()
        foreach ($ip in $ips) {
            $tcp = New-Object System.Net.Sockets.TcpClient
            $result = $tcp.BeginConnect($ip, 5555, $null, $null)
            $results += [PSCustomObject]@{ IP = $ip; AsyncResult = $result; Tcp = $tcp }
        }
        Start-Sleep -Milliseconds 600
        foreach ($r in $results) {
            if ($r.AsyncResult.IsCompleted -and $r.Tcp.Connected) {
                Write-Output $r.IP
                $r.Tcp.Close()
                exit
            }
            $r.Tcp.Close()
        }
      `;
      try {
        const encoded = Buffer.from(psScript, 'utf16le').toString('base64');
        const { stdout: scanOut } = await execAsync(`powershell -ExecutionPolicy Bypass -NoProfile -EncodedCommand ${encoded}`);
        const ip = scanOut.trim();
        if (ip) {
          await execAsync(`"${ADB}" connect ${ip}:5555`);
          return NextResponse.json({ success: true, message: `Radar exitoso. Conectado a ${ip}` });
        } else {
          throw new Error('No se encontraron celulares con el puerto 5555 abierto.');
        }
      } catch (err: any) {
        return NextResponse.json({ success: false, error: err.message });
      }
    }

    if (action === 'reboot') {
      await execAsync(`${adbTarget} reboot`);
      return NextResponse.json({ success: true, message: 'Reiniciando dispositivo...' });
    }

    if (action === 'reboot_bootloader') {
      await execAsync(`${adbTarget} reboot bootloader`);
      return NextResponse.json({ success: true, message: 'Reiniciando en modo Bootloader...' });
    }

    if (action === 'reboot_recovery') {
      await execAsync(`${adbTarget} reboot recovery`);
      return NextResponse.json({ success: true, message: 'Reiniciando en modo Recovery...' });
    }

    if (action === 'power_off') {
      await execAsync(`${adbTarget} shell reboot -p`);
      return NextResponse.json({ success: true, message: 'Apagando dispositivo...' });
    }

    if (action === 'get_foreground_app') {
      try {
        const { stdout } = await execAsync(`${adbTarget} shell "dumpsys window windows | grep mCurrentFocus"`);
        const match = stdout.match(/u0 ([a-zA-Z0-9._]+)\//);
        if (match && match[1]) {
          return NextResponse.json({ success: true, package: match[1] });
        }
        return NextResponse.json({ success: true, package: 'unknown' });
      } catch (err: any) {
        return NextResponse.json({ success: false, error: err.message });
      }
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
      return NextResponse.json({ success: true, message: `Captura guardada en Capturas\\` });
    }

    if (action === 'start_record') {
      try {
        const iconSrc = 'C:\\AndroProject\\AndroProject.png';
        const iconDst = 'C:\\AndroProject\\scrcpy.png';
        if (!fs.existsSync(iconDst) && fs.existsSync(iconSrc)) {
          fs.copyFileSync(iconSrc, iconDst);
        }
      } catch (e: any) {
        console.error('[Actions API] Failed to auto-copy scrcpy.png:', e.message);
      }

      if (!fs.existsSync(SCREENSHOTS_DIR)) fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
      const recordPath = path.join(SCREENSHOTS_DIR, `grabacion_${timestamp}.mp4`);
      const serialClean = targetSerial ? targetSerial.replace(/[:.]/g, '_') : 'default';
      const recordLockFile = `C:\\AndroProject\\.androidproject_record_${serialClean}`;
      
      const recordArgs: string[] = [];
      if (targetSerial) recordArgs.push('-s', String(targetSerial));
      recordArgs.push('-b', '16M', '--max-size', '1920', '--max-fps', '60', '--no-audio', '--no-window', `--record=${recordPath}`);

      const proc = spawn(ANDROPROJECT_BIN, recordArgs, {
        cwd: 'C:\\AndroProject',
        env: {
          ...process.env,
          ADB: ADB,
          SCRCPY_SERVER_PATH: 'AndroProject-server',
          SCRCPY_ICON_PATH: 'AndroProject.png',
        },
        windowsHide: true,
      });

      if (proc.pid) fs.writeFileSync(recordLockFile, String(proc.pid));
      return NextResponse.json({ success: true, message: 'Grabación silenciosa iniciada' });
    }

    if (action === 'stop_record') {
      const serialClean = targetSerial ? targetSerial.replace(/[:.]/g, '_') : 'default';
      const recordLockFile = `C:\\AndroProject\\.androidproject_record_${serialClean}`;
      
      if (fs.existsSync(recordLockFile)) {
        try {
          const pid = parseInt(fs.readFileSync(recordLockFile, 'utf8').trim(), 10);
          if (pid) {
            await execAsync(`taskkill /PID ${pid}`).catch(() => {});
          }
          fs.unlinkSync(recordLockFile);
        } catch (_) {}
      }
      
      setTimeout(() => exec(`explorer "${SCREENSHOTS_DIR}"`), 1000);
      return NextResponse.json({ success: true, message: 'Grabación detenida y guardada en Capturas\\' });
    }

    if (action === 'install_moonlight') {
      const apkUrl = 'https://github.com/moonlight-stream/moonlight-android/releases/download/v12.1/app-nonRoot-release.apk';
      const tempApkPath = path.join(process.cwd(), 'temp', 'moonlight.apk');
      const tempDir = path.dirname(tempApkPath);
      if (!fs.existsSync(tempDir)) fs.mkdirSync(tempDir, { recursive: true });

      const downloadFile = (url: string, dest: string): Promise<void> => {
        return new Promise((resolve, reject) => {
          const https = require('https');
          https.get(url, (response: any) => {
            if (response.statusCode === 302 || response.statusCode === 301) {
              downloadFile(response.headers.location, dest).then(resolve).catch(reject);
              return;
            }
            if (response.statusCode !== 200) {
              reject(new Error(`Servidor retornó código: ${response.statusCode}`));
              return;
            }
            const fileStream = fs.createWriteStream(dest);
            response.pipe(fileStream);
            fileStream.on('finish', () => {
              fileStream.close();
              resolve();
            });
          }).on('error', (err: any) => {
            fs.unlink(dest, () => {});
            reject(err);
          });
        });
      };

      try {
        await downloadFile(apkUrl, tempApkPath);
        await execAsync(`${adbTarget} install -r "${tempApkPath}"`);
        return NextResponse.json({ success: true, message: 'Moonlight APK instalado exitosamente' });
      } catch (err: any) {
        return NextResponse.json({ success: false, error: err.message });
      }
    }

    if (action === 'pair_pin') {
      const { pin } = body;
      const pair = (): Promise<any> => {
        return new Promise((resolve, reject) => {
          const https = require('https');
          const basicAuth = 'Basic ' + Buffer.from(`${SUNSHINE_USER}:${SUNSHINE_PASS}`).toString('base64');

          // 1. Try to fetch CSRF token first
          const csrfReq = https.request({
            hostname: SUNSHINE_HOST,
            port: SUNSHINE_PORT,
            path: '/api/csrf-token',
            method: 'GET',
            rejectUnauthorized: false,
            headers: { 'Authorization': basicAuth }
          }, (csrfRes: any) => {
            let bodyData = '';
            csrfRes.on('data', (chunk: any) => bodyData += chunk);
            csrfRes.on('end', () => {
              let csrfToken = '';
              if (csrfRes.statusCode === 200) {
                try {
                  const json = JSON.parse(bodyData);
                  csrfToken = json.token || json.csrfToken || '';
                } catch (e) {
                  if (csrfRes.headers['x-csrf-token']) {
                    csrfToken = csrfRes.headers['x-csrf-token'];
                  }
                }
              }

              // 2. Send pairing PIN
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
                } as Record<string, string>
              };

              if (csrfToken) {
                postOptions.headers['X-CSRF-Token'] = csrfToken;
              }

              const postReq = https.request(postOptions, (res: any) => {
                let responseBody = '';
                res.on('data', (chunk: any) => responseBody += chunk);
                res.on('end', () => {
                  if (res.statusCode >= 200 && res.statusCode < 300) {
                    resolve({ success: true, message: 'Dispositivo enlazado exitosamente en Sunshine' });
                  } else {
                    reject(new Error(`Sunshine respondió con código ${res.statusCode}: ${responseBody}`));
                  }
                });
              });

              postReq.on('error', (err: any) => reject(err));
              postReq.write(postData);
              postReq.end();
            });
          });

          csrfReq.on('error', () => {
            // Direct POST fallback if CSRF endpoint doesn't exist
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
            }, (res: any) => {
              let responseBody = '';
              res.on('data', (chunk: any) => responseBody += chunk);
              res.on('end', () => {
                if (res.statusCode >= 200 && res.statusCode < 300) {
                  resolve({ success: true, message: 'Dispositivo enlazado exitosamente en Sunshine' });
                } else {
                  reject(new Error(`Sunshine respondió con código ${res.statusCode}: ${responseBody}`));
                }
              });
            });

            postReq.on('error', (err: any) => reject(err));
            postReq.write(postData);
            postReq.end();
          });

          csrfReq.end();
        });
      };
      
      try {
        const result = await pair();
        return NextResponse.json(result);
      } catch (err: any) {
        return NextResponse.json({ success: false, error: err.message });
      }
    }

    if (action === 'auto_detect') {
      try {
        const { lanIp, tailscaleIp } = getLocalIPs();
        const activeIp = (lanIp && lanIp !== '127.0.0.1') ? lanIp : tailscaleIp;
        if (!activeIp || activeIp === '127.0.0.1') {
          return NextResponse.json({ success: false, error: 'No IP address found' });
        }

        const { uuid: finalUuid, hostname: finalHostname } = await getSunshineServerDetails(activeIp);

        const broadcastCmd = `${adbTarget} shell am broadcast -a com.limelight.smartdisplay.ADD_PC -p com.limelight.smartdisplay.debug --es IP ${lanIp} --es TAILSCALE_IP ${tailscaleIp} --es NAME "${finalHostname}" --es UUID ${finalUuid}`;
        await execAsync(broadcastCmd).catch(() => {});

        return NextResponse.json({ success: true, message: `PC detectado (LAN: ${lanIp}, Tailscale: ${tailscaleIp}). IP inyectada en el celular.`, ip: lanIp });
      } catch (err: any) {
        return NextResponse.json({ success: false, error: err.message });
      }
    }

    if (action === 'launch_stream') {
      try {
        const { lanIp, tailscaleIp } = getLocalIPs();
        const activeIp = (lanIp && lanIp !== '127.0.0.1') ? lanIp : tailscaleIp;
        const { uuid: finalUuid, hostname: finalHostname } = await getSunshineServerDetails(activeIp || getLocalIP());

        const packageId = "com.limelight.smartdisplay.debug";
        const activityClass = "com.limelight.ShortcutTrampoline";
        const launchCmd = `${adbTarget} shell am start -n ${packageId}/${activityClass} -a android.intent.action.DEFAULT --es uuid ${finalUuid} --es name "${finalHostname}" --ez AUTO_CONNECT true`;
        await execAsync(launchCmd);

        return NextResponse.json({ success: true, message: 'Proyección de pantalla iniciada en el celular.' });
      } catch (err: any) {
        return NextResponse.json({ success: false, error: err.message });
      }
    }

    if (action === 'keyevent') {
      const { keycode } = body;
      await execAsync(`${adbTarget} shell input keyevent ${keycode}`);
      return NextResponse.json({ success: true, message: `Tecla enviada: ${keycode}` });
    }

    return NextResponse.json({ success: false, error: 'Acción no reconocida' }, { status: 400 });

  } catch (error: any) {
    console.error('[Actions API] Error:', error.message);
    return NextResponse.json({ success: false, error: error.message }, { status: 500 });
  }
}
