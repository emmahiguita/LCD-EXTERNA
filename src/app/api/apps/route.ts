import { NextResponse } from 'next/server';
import { exec } from 'child_process';
import { promisify } from 'util';
import fs from 'fs';

const execAsync = promisify(exec);

const ADB = fs.existsSync('C:\\AndroProject\\adb.exe')
  ? 'C:\\AndroProject\\adb.exe'
  : (fs.existsSync('C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe')
      ? 'C:\\Users\\emman\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe'
      : 'adb');

const BLOATWARE_PACKAGES = new Set([
  'com.google.android.apps.tachyon', 'com.google.android.music', 'com.google.android.videos',
  'com.google.android.apps.youtube.music', 'com.google.android.feedback',
  'com.google.android.googlequicksearchbox', 'com.google.android.apps.wellbeing',
  'com.samsung.android.bixby.agent', 'com.samsung.android.bixby.es.globalaction',
  'com.samsung.android.bixby.wakeup', 'com.samsung.android.app.spage',
  'com.samsung.android.game.gamehome', 'com.samsung.android.game.gametools',
  'com.sec.android.app.sbrowser', 'com.samsung.android.email.provider',
  'com.samsung.android.kidsinstaller', 'com.samsung.android.app.watchmanagerstub',
  'com.sec.android.easyMover.Agent', 'com.miui.analytics', 'com.miui.msa.global',
  'com.xiaomi.mipicks', 'com.miui.hybrid', 'com.miui.bugreport', 'com.miui.yellowpage',
  'com.xiaomi.glance.app', 'com.xiaomi.midrop', 'com.miui.videoplayer',
  'com.mi.android.globalminusscreen', 'com.heytap.browser', 'com.heytap.mcs',
  'com.heytap.cloud', 'com.heytap.market', 'com.heytap.pictorial', 'com.oplus.member',
  'com.oplus.pay', 'com.coloros.note', 'com.oplus.appdetail', 'com.oplus.games',
  'com.nearme.atlas', 'com.coloros.phonemanager', 'com.oplus.olc', 'com.oplus.sau',
  'com.huawei.android.hsad', 'com.huawei.appmarket', 'com.huawei.hms', 'com.huawei.browser',
  'com.huawei.music', 'com.huawei.video', 'com.facebook.katana', 'com.facebook.system',
  'com.facebook.appmanager', 'com.facebook.services', 'com.netflix.mediaclient',
  'com.netflix.partner.activation', 'com.amazon.mShop.android.shopping', 'com.spotify.music',
  'com.ebay.mobile', 'com.zhiliaoapp.musically', 'com.bytecreative.promoresources'
]);

function isBloatwarePackage(pkg: string, isSystem: boolean): boolean {
  if (!isSystem) return false;
  if (BLOATWARE_PACKAGES.has(pkg)) return true;
  
  const bloatPrefixes = [
    'com.heytap.', 'com.miui.', 'com.xiaomi.', 'com.oplus.', 'com.coloros.',
    'com.facebook.', 'com.samsung.android.bixby', 'com.huawei.android.hsad',
    'com.huawei.appmarket', 'com.carrier.'
  ];
  return bloatPrefixes.some(prefix => pkg.startsWith(prefix));
}

const APP_NAME_DICT: Record<string, string> = {
  'com.android.chrome': 'Google Chrome',
  'com.google.android.youtube': 'YouTube',
  'com.whatsapp': 'WhatsApp',
  'com.instagram.android': 'Instagram',
  'com.facebook.orca': 'Messenger',
  'com.facebook.katana': 'Facebook',
  'com.facebook.lite': 'Facebook Lite',
  'com.spotify.music': 'Spotify',
  'com.netflix.mediaclient': 'Netflix',
  'com.android.vending': 'Google Play Store',
  'com.google.android.apps.docs': 'Google Drive',
  'com.google.android.apps.docs.editors.sheets': 'Google Sheets',
  'com.google.android.apps.docs.editors.slides': 'Google Slides',
  'com.google.android.apps.docs.editors.docs': 'Google Docs',
  'com.google.android.apps.maps': 'Google Maps',
  'com.google.android.gm': 'Gmail',
  'com.google.android.apps.photos': 'Google Photos',
  'com.google.android.googlequicksearchbox': 'Google App',
  'com.google.android.apps.messaging': 'Mensajes de Google',
  'com.google.android.contacts': 'Contactos de Google',
  'com.google.android.calendar': 'Google Calendar',
  'com.google.android.apps.walletnfcrel': 'Google Wallet',
  'com.google.android.apps.healthdata': 'Health Connect',
  'com.google.android.apps.tachyon': 'Google Meet',
  'com.google.android.apps.wellbeing': 'Bienestar Digital',
  'com.microsoft.office.excel': 'Microsoft Excel',
  'com.microsoft.office.word': 'Microsoft Word',
  'com.microsoft.office.powerpoint': 'Microsoft PowerPoint',
  'com.microsoft.bing': 'Microsoft Bing',
  'com.touchtype.swiftkey': 'Teclado SwiftKey',
  'com.coloros.note': 'Notas (OPPO)',
  'com.oplus.member': 'Mi OPPO',
  'com.oplus.games': 'Espacio de Juegos',
  'com.coloros.phonemanager': 'Gestor del Teléfono',
  'com.oplus.account': 'Cuenta OPPO',
  'com.adobe.psmobile': 'Photoshop Express',
  'com.aomei.anyviewer': 'AnyViewer',
  'com.deepseek.chat': 'DeepSeek',
  'easynotes.notes.notepad.notebook.privatenotes.note': 'Easy Notes',
  'com.google.android.apps.authenticator2': 'Google Authenticator',
  'com.nequi.MobileApp': 'Nequi',
  'com.davivienda.daviviendaapp': 'Davivienda',
  'com.bancolombia.appbilletera': 'Bancolombia A la Mano',
  'com.badoo.mobile': 'Badoo',
  'com.jaumo': 'Jaumo',
  'sinet.startup.inDriver': 'inDrive',
  'com.snaptube.premium': 'SnapTube',
  'com.example.hablalopues': 'Háblalo Pues',
  'com.google.android.photopicker': 'Selector de Fotos',
  'com.google.android.apps.healthconnect': 'Health Connect'
};

function deriveAppName(apkPath: string, pkg: string): string {
  if (APP_NAME_DICT[pkg]) return APP_NAME_DICT[pkg];
  const parts = apkPath.split('/');
  const lastPart = parts[parts.length - 1];
  if (lastPart && lastPart !== 'base.apk' && lastPart.endsWith('.apk')) {
    return lastPart.replace('.apk', '');
  }
  if (parts.length >= 2) {
    const parentDir = parts[parts.length - 2];
    let name = parentDir.split('-')[0];
    if (name.includes('.')) {
      const nameParts = name.split('.');
      name = nameParts[nameParts.length - 1];
    }
    if (name) return name.charAt(0).toUpperCase() + name.slice(1);
  }
  const pkgParts = pkg.split('.');
  const lastPkgPart = pkgParts[pkgParts.length - 1];
  return lastPkgPart.charAt(0).toUpperCase() + lastPkgPart.slice(1);
}

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const { action, packageName, serial } = body;
    
    const { stdout: devicesOut } = await execAsync(`"${ADB}" devices`);
    const id = devicesOut.split('\n').slice(1).find(l => l.includes('\tdevice'))?.split(/\s+/)[0];
    const targetSerial = serial || id;
    
    if (!targetSerial) {
      return NextResponse.json({ success: false, error: 'Dispositivo no detectado' }, { status: 400 });
    }
    
    const adbTarget = (targetSerial && targetSerial !== 'Hardware Level') ? `"${ADB}" -s ${targetSerial}` : `"${ADB}"`;

    if (action === 'list') {
      const launcherCommand = `cmd package query-activities -c android.intent.category.LAUNCHER -a android.intent.action.MAIN | grep packageName= | cut -d= -f2 | sort -u`;
      const statsCommand = `${adbTarget} shell "pm list packages -f | cut -d: -f2- | sed 's/=[^=]*$//' | xargs stat -c '%s|%Y|%n' 2>/dev/null"`;

      const [allPkgsRes, systemPkgsRes, disabledPkgsRes, launcherPkgsRes, appopsRes, statsRes] = await Promise.all([
        execAsync(`${adbTarget} shell pm list packages -f`, { timeout: 6000 }),
        execAsync(`${adbTarget} shell pm list packages -s`, { timeout: 4000 }).catch(() => ({ stdout: '' })),
        execAsync(`${adbTarget} shell pm list packages -d`, { timeout: 4000 }).catch(() => ({ stdout: '' })),
        execAsync(`${adbTarget} shell "${launcherCommand}"`, { timeout: 5000 }).catch(() => ({ stdout: '' })),
        execAsync(`${adbTarget} shell dumpsys appops`, { timeout: 8000 }).catch(() => ({ stdout: '' })),
        execAsync(statsCommand, { timeout: 10000 }).catch(() => ({ stdout: '' }))
      ]);

      const systemSet = new Set(systemPkgsRes.stdout.trim().split('\n').map(l => l.replace('package:', '').trim()).filter(Boolean));
      const disabledSet = new Set(disabledPkgsRes.stdout.trim().split('\n').map(l => l.replace('package:', '').trim()).filter(Boolean));
      const launcherSet = new Set(launcherPkgsRes.stdout.trim().split('\n').map(l => l.trim()).filter(Boolean));

      const overlaySet = new Set<string>();
      try {
        const appopsLines = appopsRes.stdout.split('\n');
        let currentPkg = '';
        for (const line of appopsLines) {
          const pkgMatch = line.match(/^\s*Package\s+(\S+):/);
          if (pkgMatch) currentPkg = pkgMatch[1];
          else if (line.includes('SYSTEM_ALERT_WINDOW') && (line.includes('allow') || line.includes('default'))) {
            if (currentPkg) overlaySet.add(currentPkg);
          }
        }
      } catch (e) {}

      const statsMap = new Map<string, { size: number; date: number }>();
      if (statsRes.stdout) {
        const statsLines = statsRes.stdout.trim().split('\n');
        for (const line of statsLines) {
          const parts = line.trim().split('|');
          if (parts.length === 3) {
            statsMap.set(parts[2], { size: parseInt(parts[0], 10), date: parseInt(parts[1], 10) * 1000 });
          }
        }
      }

      const allPkgsLines = allPkgsRes.stdout.trim().split('\n');
      const apps = [];

      for (const line of allPkgsLines) {
        if (!line.includes('package:') || !line.includes('=')) continue;
        const cleanLine = line.replace('package:', '').trim();
        const equalsIdx = cleanLine.lastIndexOf('=');
        if (equalsIdx === -1) continue;
        
        const apkPath = cleanLine.substring(0, equalsIdx);
        const pkg = cleanLine.substring(equalsIdx + 1);
        
        const isSystem = systemSet.has(pkg);
        const isDisabled = disabledSet.has(pkg);
        const isHidden = !launcherSet.has(pkg);
        const hasOverlay = overlaySet.has(pkg);
        const isBloatware = isBloatwarePackage(pkg, isSystem);
        const friendlyName = deriveAppName(apkPath, pkg);
        const stats = statsMap.get(apkPath) || { size: 0, date: 0 };
        const isGoogle = pkg.startsWith('com.google.android.') || pkg.startsWith('com.google.mainline.') || pkg === 'com.android.vending';

        apps.push({
          packageName: pkg,
          name: friendlyName,
          apkPath,
          isSystem,
          isBloatware,
          isHidden,
          hasOverlay,
          isDisabled,
          size: stats.size,
          date: stats.date,
          isGoogle
        });
      }

      apps.sort((a, b) => {
        if (a.isSystem !== b.isSystem) return a.isSystem ? 1 : -1;
        return a.name.localeCompare(b.name);
      });

      return NextResponse.json({ success: true, apps });
    }

    if (action === 'uninstall') {
      if (!packageName) throw new Error('Nombre de paquete requerido');
      const { stdout: pathCheck } = await execAsync(`${adbTarget} shell pm path ${packageName}`).catch(() => ({ stdout: '' }));
      const isSystem = pathCheck.includes('/system/') || pathCheck.includes('/system_ext/') || pathCheck.includes('/product/') || pathCheck.includes('/vendor/') || pathCheck.includes('/apex/');
                       
      if (isSystem) {
        await execAsync(`${adbTarget} shell pm uninstall -k --user 0 ${packageName}`);
        return NextResponse.json({ success: true, message: `Aplicación de fábrica ${packageName} desinstalada de forma segura.` });
      } else {
        await execAsync(`${adbTarget} shell pm uninstall ${packageName}`);
        return NextResponse.json({ success: true, message: `Aplicación ${packageName} desinstalada correctamente.` });
      }
    }

    if (action === 'disable') {
      if (!packageName) throw new Error('Nombre de paquete requerido');
      await execAsync(`${adbTarget} shell pm disable-user --user 0 ${packageName}`);
      return NextResponse.json({ success: true, message: `Aplicación ${packageName} deshabilitada correctamente.` });
    }

    if (action === 'enable') {
      if (!packageName) throw new Error('Nombre de paquete requerido');
      await execAsync(`${adbTarget} shell pm enable ${packageName}`);
      return NextResponse.json({ success: true, message: `Aplicación ${packageName} habilitada y lista para usar.` });
    }

    return NextResponse.json({ success: false, error: 'Acción no reconocida' }, { status: 400 });

  } catch (error: any) {
    console.error('[Apps API] Error:', error.message);
    return NextResponse.json({ success: false, error: error.message }, { status: 500 });
  }
}
