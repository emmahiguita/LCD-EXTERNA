import { NextResponse } from 'next/server';
import net from 'net';
import os from 'os';
import { exec } from 'child_process';
import fs from 'fs';

const ADB_PATH = fs.existsSync('C:\\AndroProject\\adb.exe')
  ? 'C:\\AndroProject\\adb.exe'
  : (fs.existsSync('C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe')
      ? 'C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe'
      : 'adb');

function checkPort(port: number, host = '127.0.0.1'): Promise<boolean> {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    socket.setTimeout(1000);
    socket.on('connect', () => {
      socket.destroy();
      resolve(true);
    });
    socket.on('timeout', () => {
      socket.destroy();
      resolve(false);
    });
    socket.on('error', () => {
      socket.destroy();
      resolve(false);
    });
    socket.connect(port, host);
  });
}

function runCmd(cmd: string): Promise<string> {
  return new Promise((resolve) => {
    exec(cmd, (err, stdout) => {
      if (err) {
        resolve(`Error: ${err.message}`);
      } else {
        resolve(stdout.trim());
      }
    });
  });
}

export async function GET() {
  const interfaces = os.networkInterfaces();
  const activeInterfaces: any[] = [];
  
  for (const [name, nets] of Object.entries(interfaces)) {
    if (!nets) continue;
    for (const net of nets) {
      if (net.family === 'IPv4' && !net.internal) {
        activeInterfaces.push({
          interface: name,
          ip: net.address,
          mac: net.mac,
        });
      }
    }
  }

  const portsToCheck = [
    { port: 3000, name: 'Next.js Dev Server' },
    { port: 3001, name: 'Electron HTTP API' },
    { port: 3002, name: 'Electron WebSocket Server' },
    { port: 47989, name: 'Sunshine HTTPS Port' },
    { port: 47984, name: 'Sunshine HTTP Port' },
    { port: 48010, name: 'Sunshine Stream Port' },
    { port: 5555, name: 'ADB Wireless (if active)' }
  ];

  const portResults: any[] = [];
  for (const item of portsToCheck) {
    const active = await checkPort(item.port);
    portResults.push({
      port: item.port,
      name: item.name,
      status: active ? '🟢 ESCUCHANDO' : '🔴 INACTIVO',
      active,
    });
  }

  // Check installed packages on device
  let installedPackages = 'No se pudo consultar';
  try {
    const adbBin = ADB_PATH.includes(' ') ? `"${ADB_PATH}"` : ADB_PATH;
    installedPackages = await runCmd(`${adbBin} shell pm list packages limelight`);
  } catch (_) {}

  // Attempt to check firewall rules via netsh
  let firewallRules = 'No se pudo consultar';
  try {
    firewallRules = await runCmd('netsh advfirewall firewall show rule name=all | findstr /I "Sunshine SmartDisplay"');
  } catch (_) {}

  return NextResponse.json({
    success: true,
    timestamp: new Date().toISOString(),
    networkInterfaces: activeInterfaces,
    ports: portResults,
    installedPackages,
    firewall: firewallRules,
  });
}
