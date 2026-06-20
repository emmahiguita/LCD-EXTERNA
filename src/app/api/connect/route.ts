// src/app/api/connect/route.ts
// Returns session token + connection URLs for trusted LAN/Tailscale clients.
// Android opens this endpoint once (on LAN) to get the token, then reuses it
// for future connections — even via Tailscale on mobile data.
//
// Security: only responds to private/Tailscale IPs (RFC1918 + 100.64/10).
// The token is also validated by the WebSocket server on every connection.

import { NextRequest, NextResponse } from 'next/server';
import os from 'os';
import fs from 'fs';
import path from 'path';

const WS_PORT = 3002;
const HTTP_PORT = 3000;

function isAllowedIp(ip: string): boolean {
  const clean = ip.replace(/^::ffff:/, ''); // strip IPv4-in-IPv6 prefix
  if (clean === '127.0.0.1' || clean === '::1') return true;
  // RFC1918 private ranges
  if (/^10\./.test(clean)) return true;
  if (/^172\.(1[6-9]|2\d|3[01])\./.test(clean)) return true;
  if (/^192\.168\./.test(clean)) return true;
  // Tailscale CGNAT range (100.64.0.0/10)
  if (/^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\./.test(clean)) return true;
  return false;
}

function readToken(): string | null {
  try {
    const tokenPath = path.join(process.cwd(), '.smartdisplay-token');
    if (fs.existsSync(tokenPath)) {
      const t = fs.readFileSync(tokenPath, 'utf8').trim();
      return t.length >= 32 ? t : null;
    }
  } catch { /* ignore */ }
  return null;
}

export async function GET(req: NextRequest) {
  // Resolve client IP — works with and without reverse proxy
  const forwarded = req.headers.get('x-forwarded-for');
  const realIp = req.headers.get('x-real-ip');
  const clientIp = (forwarded?.split(',')[0]?.trim() ?? realIp ?? '127.0.0.1');

  if (!isAllowedIp(clientIp)) {
    return NextResponse.json(
      { error: 'Forbidden: only LAN or Tailscale access allowed' },
      { status: 403 }
    );
  }

  const token = readToken();
  if (!token) {
    return NextResponse.json(
      { error: 'Service not ready: SmartDisplay Agent may not be running' },
      { status: 503 }
    );
  }

  // Enumerate all network interfaces
  const interfaces = os.networkInterfaces();
  const lanIps: string[] = [];
  let tailscaleIp: string | null = null;

  for (const [name, nets] of Object.entries(interfaces)) {
    if (!nets) continue;
    for (const net of nets) {
      if (net.family !== 'IPv4' || net.internal) continue;
      const addr = net.address;
      if (name.toLowerCase().includes('tailscale') || /^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\./.test(addr)) {
        tailscaleIp = addr;
      } else if (/^(10\.|172\.(1[6-9]|2\d|3[01])\.|192\.168\.)/.test(addr)) {
        lanIps.push(addr);
      }
    }
  }

  const primaryLan = lanIps[0] ?? null;

  return NextResponse.json({
    token,
    hostname: os.hostname(),
    lan: primaryLan ? {
      wsUrl: `ws://${primaryLan}:${WS_PORT}`,
      appUrl: `http://${primaryLan}:${HTTP_PORT}?token=${token}`,
    } : null,
    tailscale: tailscaleIp ? {
      wsUrl: `ws://${tailscaleIp}:${WS_PORT}`,
      appUrl: `http://${tailscaleIp}:${HTTP_PORT}?token=${token}`,
    } : null,
  });
}
