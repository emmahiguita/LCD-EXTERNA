import { NextResponse } from 'next/server';
import { exec } from 'child_process';
import { promisify } from 'util';
import path from 'path';
import fs from 'fs';
import { pipeline } from 'stream/promises';
import { Readable } from 'stream';

const execAsync = promisify(exec);
const ADB = fs.existsSync('C:\\AndroProject\\adb.exe')
  ? 'C:\\AndroProject\\adb.exe'
  : (fs.existsSync('C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe')
      ? 'C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe'
      : 'adb');

export async function POST(req: Request) {
  try {
    const formData = await req.formData();
    const file = formData.get('file') as File;
    const dest = formData.get('dest') as string || '/sdcard/Download';
    const serial = formData.get('serial') as string | null;
    
    if (!file) {
      return NextResponse.json({ success: false, error: 'No se envió ningún archivo' }, { status: 400 });
    }

    const tempDir = path.join(process.cwd(), 'temp');
    if (!fs.existsSync(tempDir)) fs.mkdirSync(tempDir, { recursive: true });
    
    const filePath = path.join(tempDir, file.name);
    
    await pipeline(
      Readable.fromWeb(file.stream() as any),
      fs.createWriteStream(filePath)
    );

    const remotePath = `${dest}/${file.name}`;
    const adbTarget = (serial && serial !== 'Hardware Level') ? `"${ADB}" -s ${serial}` : `"${ADB}"`;

    await execAsync(`${adbTarget} push "${filePath}" "${remotePath}"`);
    fs.unlinkSync(filePath);

    return NextResponse.json({ success: true, message: `Archivo ${file.name} transferido a ${remotePath}.` });

  } catch (error: any) {
    console.error('[Upload API] Error:', error.message);
    return NextResponse.json({ success: false, error: error.message }, { status: 500 });
  }
}
