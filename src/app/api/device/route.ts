export const dynamic = 'force-dynamic';
import { NextResponse } from 'next/server';
import { exec } from 'child_process';
import { promisify } from 'util';
import fs from 'fs';
import path from 'path';

const execAsync = promisify(exec);
const ADB_PATH = fs.existsSync('C:\\AndroProject\\adb.exe')
  ? 'C:\\AndroProject\\adb.exe'
  : (fs.existsSync('C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe')
      ? 'C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe'
      : 'adb');

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const requestedSerial = searchParams.get('serial');

    const { stdout: devicesOut } = await execAsync(`"${ADB_PATH}" devices -l`, { timeout: 3000 });
    const lines = devicesOut.trim().split('\n').slice(1);
    
    const devicesList: Array<{ serial: string; model: string; connectionType: string; state: string }> = [];

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
      try {
        const { stdout } = await execAsync(`powershell -NoProfile -Command "${psScript}"`, { timeout: 4000 });
        const hwName = stdout.trim();
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
          return NextResponse.json({
            connected: true,
            devices: [{ serial: 'Hardware Level', model: cleanName, connectionType: 'USB', state: 'offline' }],
            activeDevice: fallbackDev
          });
        }
      } catch (e) {}

      return NextResponse.json({ connected: false, devices: [], activeDevice: null });
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

      return NextResponse.json({
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
      });
    }

    const adbTarget = `"${ADB_PATH}" -s ${targetDevice.serial}`;

    const { stdout: getpropOut } = await execAsync(`${adbTarget} shell getprop`, { timeout: 3000 });
    
    const props: Record<string, string> = {};
    const propLines = getpropOut.split('\n');
    for (const line of propLines) {
      const match = line.match(/^\[(.*?)\]: \[(.*?)\]/);
      if (match) {
        props[match[1]] = match[2];
      }
    }

    const { stdout: batteryOut } = await execAsync(`${adbTarget} shell dumpsys battery`, { timeout: 2000 });
    let batteryLevel = 0;
    let tempRaw = 0;
    let isCharging = false;

    batteryOut.split('\n').forEach(line => {
      if (line.includes('level:')) batteryLevel = parseInt(line.split(':')[1].trim());
      if (line.includes('temperature:')) tempRaw = parseInt(line.split(':')[1].trim());
      if (line.includes('status:')) {
        const statusStr = line.split(':')[1].trim();
        if (statusStr === '2' || statusStr === '5') isCharging = true;
      }
    });

    const temperature = (tempRaw / 10).toFixed(1);

    const { stdout: memOut } = await execAsync(`${adbTarget} shell cat /proc/meminfo`, { timeout: 2000 });
    let totalMemKB = 0;
    memOut.split('\n').forEach(line => {
      if (line.startsWith('MemTotal:')) {
        totalMemKB = parseInt(line.replace(/[^0-9]/g, ''));
      }
    });
    const ramGB = Math.round(totalMemKB / 1024 / 1024);

    let storageCap = 'Desconocido';
    try {
      const { stdout: dfOut } = await execAsync(`${adbTarget} shell df -h /data`, { timeout: 2000 });
      const dfLines = dfOut.trim().split('\n');
      if (dfLines.length > 1) {
        const columns = dfLines[1].trim().split(/\s+/);
        if (columns.length >= 2) {
          const rawSize = parseFloat(columns[1].replace(/[^0-9.]/g, ''));
          const standards = [8, 16, 32, 64, 128, 256, 512, 1024];
          let bestFit = standards[0];
          for (const s of standards) {
            if (rawSize <= s * 0.98) {
              bestFit = s;
              break;
            }
          }
          if (rawSize > 1000) bestFit = Math.ceil(rawSize);
          storageCap = `${bestFit} GB`;
        }
      }
    } catch (e) {
      storageCap = '128 GB';
    }

    let resolution = 'Desconocida';
    try {
      const { stdout: sizeOut } = await execAsync(`${adbTarget} shell wm size`, { timeout: 2000 });
      const sizeMatch = sizeOut.match(/Physical size:\s*(\d+x\d+)/);
      if (sizeMatch) {
        resolution = sizeMatch[1].replace('x', ' × ');
      }
    } catch (e) {}

    const rawModel = props['ro.product.model'] || targetDevice.model;
    const marketName = props['ro.product.marketname'] || 
                       props['ro.vendor.oplus.market.name'] || 
                       props['bluetooth.device.default_name'] || 
                       rawModel;
    const preciseModel = (marketName !== rawModel && rawModel !== 'Dispositivo') ? `${marketName} (${rawModel})` : marketName;

    return NextResponse.json({
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
    });

  } catch (error: any) {
    console.error("ADB Error:", error);
    return NextResponse.json({ connected: false, error: error.message }, { status: 500 });
  }
}
