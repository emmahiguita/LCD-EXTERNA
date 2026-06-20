// src/app/api/ip/route.ts
// Exposes the PC's LAN IP address(es) for automatic connection discovery.
// Used by the frontend to resolve the correct WebSocket URL when in LAN mode.

import { NextResponse } from 'next/server';
import os from 'os';

export async function GET() {
  try {
    const interfaces = os.networkInterfaces();
    const addresses: { name: string; ip: string; mac: string; type: string }[] = [];

    for (const [name, nets] of Object.entries(interfaces)) {
      if (!nets) continue;
      for (const net of nets) {
        if (net.family === 'IPv4' && !net.internal) {
          // Determine interface type
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

    // Sort: prefer ethernet > wifi > others, then by IP address
    const typePriority: Record<string, number> = {
      ethernet: 0,
      wifi: 1,
      tailscale: 2,
      vpn: 3,
      unknown: 4,
    };
    addresses.sort((a, b) => {
      const pa = typePriority[a.type] ?? 99;
      const pb = typePriority[b.type] ?? 99;
      if (pa !== pb) return pa - pb;
      return a.ip.localeCompare(b.ip);
    });

    return NextResponse.json({
      success: true,
      addresses,
      primaryIp: addresses.length > 0 ? addresses[0].ip : '127.0.0.1',
      interfaceCount: addresses.length,
      hostname: os.hostname(),
    });
  } catch (error: any) {
    return NextResponse.json({
      success: false,
      error: error.message,
      primaryIp: '127.0.0.1',
    }, { status: 500 });
  }
}
