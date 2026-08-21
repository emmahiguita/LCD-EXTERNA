#!/usr/bin/env python3
"""
SmartDisplay AI — Companion Server v1.0
────────────────────────────────────────
Servidor WebSocket ligero para Windows que monitoriza el foco de UI
usando UIAutomation y notifica a la app Android en tiempo real.

DEPENDENCIAS:
  pip install websockets comtypes pywin32

USO:
  python companion_server.py [--port 8765] [--host 0.0.0.0]

PROTOCOLO (JSON sobre WebSocket):
  Envía al cliente Android:
  {
    "type": "focus_changed",
    "is_text_field": true,
    "rect": {"x": 100, "y": 200, "w": 800, "h": 40},
    "control_name": "TextBox",
    "app": "Code.exe"
  }
  {
    "type": "focus_cleared"
  }
  {
    "type": "ping",
    "ts": 1234567890
  }
"""

import win32gui
import pystray
from PIL import Image, ImageDraw

# Evitar que PyAudio imprima warnings al importar os
import asyncio
import json
import time
import argparse
import threading
import logging
import os
import re
import socket
import urllib.request
import hashlib
import random
import atexit
import struct

# ── Audio DSP imports (todos opcionales) ────────────────────────────────────
try:
    import numpy as np
    HAS_NP = True
except ImportError:
    HAS_NP = False
    log.warning("numpy no instalado — procesamiento de audio desactivado")

try:
    import scipy.signal as signal
    HAS_SCIPY = True
except ImportError:
    HAS_SCIPY = False
    log.info("scipy no disponible — usando filtros IIR manuales")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s"
)
log = logging.getLogger("SmartDisplay")

# ── Imports opcionales de Windows UI Automation ───────────────────────────
try:
    import comtypes
    import comtypes.client
    comtypes.client.GetModule("UIAutomationCore.dll")
    from comtypes.gen import UIAutomationClient as UIA
    HAS_UIA = True
    log.info("UIAutomation disponible")
except Exception as e:
    HAS_UIA = False
    log.warning(f"UIAutomation no disponible: {e}. Usando fallback win32.")

try:
    import win32gui
    import win32process
    import psutil
    HAS_WIN32 = True
except ImportError:
    HAS_WIN32 = False
    log.warning("win32gui no disponible. Instala pywin32 y psutil.")


# ── Tipos de controles de texto en UIA ───────────────────────────────────
TEXT_CONTROL_TYPES = {
    50004,  # UIA_EditControlTypeId
    50020,  # UIA_DocumentControlTypeId
    50025,  # UIA_TextControlTypeId (lectura)
}

POLL_INTERVAL = 0.15  # segundos
FOREGROUND_POLL_INTERVAL = 0.3  # segundos – ventana activa (más barato que el foco UIA)

# ── Puertos de los canales adicionales ────────────────────────────────────
BUS_PORT     = 47991   # SmartDisplayBus (archivos / comandos) — WebSocket
VOICE_PORT   = 48999   # Voz móvil→PC (PCM mono, 16-bit) — UDP
                       # (NO 47998: ese es el puerto de vídeo de Sunshine)
# DEBE coincidir EXACTAMENTE con VoiceCaptureManager.SAMPLE_RATE en Android.
# Si difieren, el PC reproduce el PCM al ritmo equivocado (voz acelerada/aguda).
VOICE_SAMPLE_RATE = 48000

# ── Parámetros del pipeline DSP de audio ───────────────────────────────────
AUDIO_HPF_CUTOFF  = 80       # Hz — elimina rumble de ventiladores/AC
AUDIO_NOISE_GATE  = -42      # dBFS — threshold del noise gate
AUDIO_GATE_HYST   = 3        # dB — histéresis para evitar flapping
AUDIO_AGC_TARGET  = -16      # dBFS — nivel objetivo del AGC
AUDIO_AGC_MAX_GAIN = 12      # dB — ganancia máxima del AGC
AUDIO_VAD_THRESH  = -36      # dBFS — threshold de actividad vocal
AUDIO_VAD_HANGOVER = 12      # frames (240ms a 48kHz/100frames) — mantiene VAD activo tras speech
AUDIO_AEC_TAIL_MS = 50       # ms — longitud del filtro adaptativo AEC
AUDIO_AEC_MU      = 0.05     # paso de adaptación NLMS
AUDIO_FRAME_MS    = 10       # ms por frame de procesamiento
AUDIO_FRAME_SIZE  = int(VOICE_SAMPLE_RATE * AUDIO_FRAME_MS / 1000)  # samples
AUDIO_FFT_SIZE    = 512      # tamaño FFT para supresión espectral
AUDIO_NOISE_FLOOR_ALPHA = 0.02  # tasa de aprendizaje del noise floor
DOWNLOAD_DIR = os.path.join(os.path.expanduser("~"), "Downloads", "SmartDisplay")
FILE_PORT    = 8766    # Explorador de archivos del PC (HTTP): el móvil lista y descarga
                       # archivos del PC. Distinto del 8080 (servidor del móvil).

# ── Autenticación P0 (PIN de emparejamiento) ──────────────────────────────
# PIN numérico de 6 dígitos autogenerado y persistido en config.json (junto al
# script). La app Android lo envía: header "Authorization: Bearer <PIN>" en los
# WebSocket (8765/47991) y un prefijo de 8 bytes (SHA-256 del PIN) en cada
# datagrama UDP de voz (48999). Sin config.json → auth desactivada (no rompe).
CONFIG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.json")
PIN = ""               # vacío = auth desactivada
VOICE_PREFIX = b""     # 8 bytes; vacío = no se exige prefijo en la voz

# ── Identidad del agente (persistente entre reinicios) ──────────────────────
# Cada PC gestionado tiene un UUID único que se genera en primera ejecución
# y se almacena en agent-identity.json junto al script.
AGENT_IDENTITY_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "agent-identity.json"
)
AGENT_IDENTITY = None   # se carga en main()

def load_or_create_identity() -> dict:
    """Carga o genera la identidad persistente del agente."""
    global AGENT_IDENTITY
    if AGENT_IDENTITY:
        return AGENT_IDENTITY
    try:
        if os.path.exists(AGENT_IDENTITY_PATH):
            with open(AGENT_IDENTITY_PATH, "r") as f:
                AGENT_IDENTITY = json.load(f)
            log.info(f"Identidad cargada: {AGENT_IDENTITY.get('name', '?')} ({AGENT_IDENTITY.get('agentId', '?')[:8]}…)")
            return AGENT_IDENTITY
    except Exception as e:
        log.warning(f"No se pudo cargar identidad: {e}")

    # Generar nueva identidad
    import uuid
    AGENT_IDENTITY = {
        "agentId": str(uuid.uuid4()),
        "name": os.environ.get("COMPUTERNAME", os.environ.get("HOSTNAME", "PC-Unknown")),
        "hostname": socket.gethostname(),
        "os": f"Windows {os.environ.get('OS', 'Unknown')}" if os.name == "nt" else os.uname().sysname,
        "createdAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    try:
        with open(AGENT_IDENTITY_PATH, "w") as f:
            json.dump(AGENT_IDENTITY, f, indent=2)
        log.info(f"Identidad generada: {AGENT_IDENTITY['name']} ({AGENT_IDENTITY['agentId'][:8]}…)")
    except Exception as e:
        log.warning(f"No se pudo guardar identidad: {e}")
    return AGENT_IDENTITY


def load_pin() -> str:
    """Carga el PIN de config.json SI existe. Por defecto (sin config.json) la
    auth queda DESACTIVADA: así el uso remoto funciona sin tocar el PC. La
    seguridad es opt-in: para activarla, crea config.json con {"pin": "123456"}
    (puedes generarlo con --set-pin)."""
    try:
        if os.path.exists(CONFIG_PATH):
            with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                cfg = json.load(f)
            return str(cfg.get("pin", "")).strip()
    except Exception as e:
        log.warning(f"No se pudo leer config.json ({e}); auth desactivada")
    return ""


def _ws_auth_ok(websocket) -> bool:
    """Valida el header Authorization: Bearer <PIN> en el handshake del WebSocket.
    Robusto entre versiones de 'websockets' (request_headers vs request.headers)."""
    if not PIN:
        return True  # auth desactivada
    try:
        headers = getattr(websocket, "request_headers", None)
        if headers is None:
            req = getattr(websocket, "request", None)
            headers = getattr(req, "headers", None) if req is not None else None
        if headers is None:
            return False
        return headers.get("Authorization", "") == f"Bearer {PIN}"
    except Exception:
        return False

# Estado más reciente de la ventana activa del PC. Lo guardamos para poder
# enviar un snapshot a clientes del bus que se conecten después del cambio.
_last_foreground = {"process": None, "title": None, "hwnd": None}


class FocusMonitor:
    """Monitoriza el foco de Windows y detecta campos de texto."""

    def __init__(self, on_text_field, on_cleared):
        self.on_text_field = on_text_field
        self.on_cleared    = on_cleared
        self._last_hwnd    = None
        self._in_text      = False
        self._running      = False

    def start(self):
        self._running = True
        t = threading.Thread(target=self._poll_loop, daemon=True)
        t.start()

    def stop(self):
        self._running = False

    def _poll_loop(self):
        while self._running:
            try:
                self._check_focus()
            except Exception as e:
                log.debug(f"Focus check error: {e}")
            time.sleep(POLL_INTERVAL)

    def _check_focus(self):
        if HAS_UIA:
            self._check_focus_uia()
        elif HAS_WIN32:
            self._check_focus_win32()

    def _check_focus_uia(self):
        """Método principal: usa UIAutomation para precisión máxima."""
        try:
            automation = comtypes.client.CreateObject(
                "{ff48dba4-60ef-4201-aa87-54103eef594e}",
                interface=UIA.IUIAutomation
            )
            focused = automation.GetFocusedElement()
            if focused is None:
                self._signal_cleared()
                return

            ctrl_type = focused.CurrentControlType
            is_text   = ctrl_type in TEXT_CONTROL_TYPES

            if is_text:
                rect = focused.CurrentBoundingRectangle
                pid  = focused.CurrentProcessId
                try:
                    import psutil
                    app = psutil.Process(pid).name()
                except Exception:
                    app = str(pid)
                self._signal_text(rect.left, rect.top,
                                  rect.right - rect.left,
                                  rect.bottom - rect.top, app)
            else:
                self._signal_cleared()
        except Exception as e:
            log.debug(f"UIA error: {e}")
            self._signal_cleared()

    def _check_focus_win32(self):
        """Fallback: usa win32gui para hwnd + GetCaretPos."""
        hwnd = win32gui.GetForegroundWindow()
        if hwnd == self._last_hwnd:
            return
        self._last_hwnd = hwnd

        # Heurística: si la ventana activa es un editor conocido
        cls = win32gui.GetClassName(hwnd)
        text_classes = {"Edit", "RichEdit20W", "Scintilla", "Chrome_RenderWidgetHostHWND"}
        if cls in text_classes:
            rect = win32gui.GetWindowRect(hwnd)
            x, y = rect[0], rect[1]
            w, h = rect[2] - x, rect[3] - y
            try:
                _, pid = win32process.GetWindowThreadProcessId(hwnd)
                import psutil
                app = psutil.Process(pid).name()
            except Exception:
                app = "unknown"
            self._signal_text(x, y, w, h, app)
        else:
            self._signal_cleared()

    def _signal_text(self, x, y, w, h, app):
        if not self._in_text:
            self._in_text = True
            self.on_text_field(x, y, w, h, app)

    def _signal_cleared(self):
        if self._in_text:
            self._in_text = False
            self.on_cleared()


# ── Servidor WebSocket ────────────────────────────────────────────────────
connected_clients = set()


async def handler(websocket, path="/"):
    if not _ws_auth_ok(websocket):
        log.warning("Foco: conexión rechazada (PIN inválido)")
        await websocket.close(1008, "unauthorized")
        return
    connected_clients.add(websocket)
    log.info(f"Cliente conectado: {websocket.remote_address}")
    try:
        # Ping heartbeat loop
        async for message in websocket:
            # Procesar mensajes del cliente (reservado para futuras extensiones)
            pass
    except Exception:
        pass
    finally:
        connected_clients.discard(websocket)
        log.info(f"Cliente desconectado: {websocket.remote_address}")


async def broadcast(msg: dict):
    """Envía un mensaje JSON a todos los clientes conectados."""
    global connected_clients
    if not connected_clients:
        return
    payload = json.dumps(msg)
    dead = set()
    for ws in connected_clients:
        try:
            await ws.send(payload)
        except Exception:
            dead.add(ws)
    connected_clients -= dead


def on_text_field(x, y, w, h, app):
    msg = {
        "type": "focus_changed",
        "is_text_field": True,
        "rect": {"x": x, "y": y, "w": w, "h": h},
        "app": app,
    }
    log.info(f"Campo de texto detectado: {app} @ ({x},{y},{w},{h})")
    asyncio.run_coroutine_threadsafe(broadcast(msg), loop)


def on_cleared():
    msg = {"type": "focus_cleared"}
    log.info("Foco fuera de campo de texto")
    asyncio.run_coroutine_threadsafe(broadcast(msg), loop)


async def ping_loop():
    """Envía un ping cada 15s para mantener la conexión viva."""
    while True:
        await asyncio.sleep(15)
        await broadcast({"type": "ping", "ts": int(time.time())})


# ── Canal BUS (47991): recepción automática de archivos (Móvil → PC) ───────

def _sanitize(name: str) -> str:
    return re.sub(r'[\\/:*?"<>|\r\n]', "_", name or "archivo")


def _unique_path(path: str) -> str:
    if not os.path.exists(path):
        return path
    base, ext = os.path.splitext(path)
    i = 1
    while os.path.exists(f"{base}({i}){ext}"):
        i += 1
    return f"{base}({i}){ext}"


def _download(url: str, dest: str):
    with urllib.request.urlopen(url, timeout=30) as r, open(dest, "wb") as out:
        while True:
            chunk = r.read(65536)
            if not chunk:
                break
            out.write(chunk)


async def _handle_files_offer(data: dict):
    files = data.get("files", [])
    if not files:
        return
    os.makedirs(DOWNLOAD_DIR, exist_ok=True)
    for f in files:
        url = f.get("url")
        if not url:
            continue
        dest = _unique_path(os.path.join(DOWNLOAD_DIR, _sanitize(f.get("name", "archivo"))))
        try:
            await asyncio.to_thread(_download, url, dest)
            log.info(f"Archivo recibido del móvil: {dest}")
        except Exception as e:
            log.warning(f"No se pudo descargar {url}: {e}")


def _do_power(action: str):
    """Ejecuta un comando de energía en el PC (suspend/restart/shutdown).

    Enviado por la app desde la pantalla de detalle del PC como
    {"type":"power","action":"suspend|restart|shutdown"}. Solo Windows.
    """
    if os.name != "nt":
        log.warning(f"Comando de energía '{action}' ignorado: no es Windows")
        return
    try:
        if action == "shutdown":
            os.system("shutdown /s /t 0")
        elif action == "restart":
            os.system("shutdown /r /t 0")
        elif action == "suspend":
            import ctypes
            # SetSuspendState(Hibernate=0, ForceCritical=1, DisableWakeEvent=0) → suspender
            ctypes.windll.PowrProf.SetSuspendState(0, 1, 0)
        else:
            log.warning(f"Acción de energía desconocida: {action}")
    except Exception as e:
        log.warning(f"No se pudo ejecutar '{action}': {e}")


def _ram_percent() -> int:
    """% de RAM en uso vía GlobalMemoryStatusEx (sin dependencias externas)."""
    try:
        import ctypes

        class MEMORYSTATUSEX(ctypes.Structure):
            _fields_ = [
                ("dwLength", ctypes.c_ulong),
                ("dwMemoryLoad", ctypes.c_ulong),
                ("ullTotalPhys", ctypes.c_ulonglong),
                ("ullAvailPhys", ctypes.c_ulonglong),
                ("ullTotalPageFile", ctypes.c_ulonglong),
                ("ullAvailPageFile", ctypes.c_ulonglong),
                ("ullTotalVirtual", ctypes.c_ulonglong),
                ("ullAvailVirtual", ctypes.c_ulonglong),
                ("ullAvailExtendedVirtual", ctypes.c_ulonglong),
            ]

        stat = MEMORYSTATUSEX()
        stat.dwLength = ctypes.sizeof(MEMORYSTATUSEX)
        ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(stat))
        return int(stat.dwMemoryLoad)
    except Exception:
        return -1


def _cpu_percent() -> int:
    """% de CPU midiendo GetSystemTimes en dos muestras (idle vs. total)."""
    try:
        import ctypes

        class FILETIME(ctypes.Structure):
            _fields_ = [("low", ctypes.c_uint32), ("high", ctypes.c_uint32)]

        def sample():
            idle, kernel, user = FILETIME(), FILETIME(), FILETIME()
            ctypes.windll.kernel32.GetSystemTimes(
                ctypes.byref(idle), ctypes.byref(kernel), ctypes.byref(user))
            to64 = lambda ft: (ft.high << 32) | ft.low
            return to64(idle), to64(kernel), to64(user)

        i0, k0, u0 = sample()
        time.sleep(0.25)
        i1, k1, u1 = sample()
        idle_delta = i1 - i0
        total_delta = (k1 - k0) + (u1 - u0)  # kernel ya incluye idle en Windows
        if total_delta <= 0:
            return 0
        return max(0, min(100, int(round((1 - idle_delta / total_delta) * 100))))
    except Exception:
        return -1


def _get_system_stats() -> dict:
    if os.name != "nt":
        return {"cpu": -1, "ram": -1}
    return {"cpu": _cpu_percent(), "ram": _ram_percent()}


def _run_gradle(action: str) -> dict:
    """Ejecuta la MISMA tarea Gradle que los botones del IDE (sdClean/sdBuild/sdRun).

    El companion vive en <proyecto>/companion_server/, asi que el gradlew esta un
    nivel arriba. Solo se permiten 3 tareas fijas (no comandos arbitrarios).
    """
    import subprocess
    task = {"clean": "sdClean", "build": "sdBuild", "run": "sdRun"}.get(action)
    if not task:
        return {"ok": False, "tail": f"accion invalida: {action}"}
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    gradlew = os.path.join(root, "gradlew.bat" if os.name == "nt" else "gradlew")
    if not os.path.exists(gradlew):
        return {"ok": False, "tail": f"no se encontro gradlew en {root}"}
    try:
        p = subprocess.run([gradlew, task], cwd=root,
                           capture_output=True, text=True, timeout=1800)
        out = ((p.stdout or "") + "\n" + (p.stderr or "")).strip()
        return {"ok": p.returncode == 0, "tail": out[-600:]}
    except Exception as e:
        return {"ok": False, "tail": str(e)}


def _list_windows() -> list:
    """Lista las ventanas visibles de nivel superior (la 'barra de tareas' real).

    Base de la Smart Taskbar: el móvil recibe las apps/ventanas abiertas de verdad.
    Solo Windows. Devuelve [{hwnd, pid, title, minimized}].
    """
    if os.name != "nt":
        return []
    try:
        import ctypes
        from ctypes import wintypes
        user32 = ctypes.windll.user32
        GWL_EXSTYLE = -20
        WS_EX_TOOLWINDOW = 0x00000080
        WS_EX_APPWINDOW = 0x00040000
        result = []

        EnumProc = ctypes.WINFUNCTYPE(ctypes.c_bool, wintypes.HWND, wintypes.LPARAM)

        def cb(hwnd, _lparam):
            if not user32.IsWindowVisible(hwnd):
                return True
            length = user32.GetWindowTextLengthW(hwnd)
            if length == 0:
                return True
            exstyle = user32.GetWindowLongW(hwnd, GWL_EXSTYLE)
            # Ventanas que aparecen en la barra de tareas (excluye tool windows).
            if (exstyle & WS_EX_TOOLWINDOW) and not (exstyle & WS_EX_APPWINDOW):
                return True
            buff = ctypes.create_unicode_buffer(length + 1)
            user32.GetWindowTextW(hwnd, buff, length + 1)
            title = buff.value
            if not title:
                return True
            pid = wintypes.DWORD()
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            result.append({
                "hwnd": int(hwnd),
                "pid": int(pid.value),
                "title": title,
                "minimized": bool(user32.IsIconic(hwnd)),
            })
            return True

        user32.EnumWindows(EnumProc(cb), 0)
        return result
    except Exception as e:
        log.warning(f"No se pudo listar ventanas: {e}")
        return []


def _focus_window(hwnd: int):
    if os.name != "nt" or not hwnd:
        return
    try:
        import ctypes
        user32 = ctypes.windll.user32
        SW_RESTORE = 9
        user32.ShowWindow(hwnd, SW_RESTORE)
        user32.SetForegroundWindow(hwnd)
    except Exception as e:
        log.warning(f"focus_window error: {e}")


def _close_window(hwnd: int):
    if os.name != "nt" or not hwnd:
        return
    try:
        import ctypes
        WM_CLOSE = 0x0010
        ctypes.windll.user32.PostMessageW(hwnd, WM_CLOSE, 0, 0)
    except Exception as e:
        log.warning(f"close_window error: {e}")


def _minimize_window(hwnd: int):
    if os.name != "nt" or not hwnd:
        return
    try:
        import ctypes
        SW_MINIMIZE = 6
        ctypes.windll.user32.ShowWindow(hwnd, SW_MINIMIZE)
    except Exception as e:
        log.warning(f"minimize_window error: {e}")


bus_clients = set()
_last_clip = {"text": None}


async def bus_broadcast(msg: dict):
    """Envía un mensaje a los clientes del bus (móvil)."""
    if not bus_clients:
        return
    payload = json.dumps(msg)
    dead = set()
    for ws in bus_clients:
        try:
            await ws.send(payload)
        except Exception:
            dead.add(ws)
    bus_clients.difference_update(dead)


def _get_pc_clipboard():
    try:
        import win32clipboard
        import win32con
        win32clipboard.OpenClipboard()
        try:
            if win32clipboard.IsClipboardFormatAvailable(win32con.CF_UNICODETEXT):
                return win32clipboard.GetClipboardData(win32con.CF_UNICODETEXT)
        finally:
            win32clipboard.CloseClipboard()
    except Exception:
        pass
    return None


_pc_muted = False
# Seguridad: restaurar audio y cursor del PC al salir. Los atexit.register se
# registran al final del módulo (cuando las funciones ya están definidas).


def _restore_pc_audio():
    """Fuerza a Windows a re-establecer el dispositivo de audio por defecto.

    Cuando Sunshine termina una sesión, a veces Windows no restaura la salida
    de audio a los altavoces/auriculares originales.  Esta función ejecuta un
    script PowerShell que re-enumera los endpoints de audio, forzando al
    mezclador de Windows a reconectarse al dispositivo por defecto.
    """
    import subprocess
    try:
        ps_script = (
            'Add-Type -TypeDefinition @\"'
            'using System;'
            'using System.Runtime.InteropServices;'
            'public class AudioRestore {'
            '  [DllImport("winmm.dll")]'
            '  public static extern int waveOutGetNumDevs();'
            '}'
            '\"@; '
            '[AudioRestore]::waveOutGetNumDevs() | Out-Null; '
            'exit 0'
        )
        subprocess.run(
            ["powershell", "-NoProfile", "-NonInteractive", "-Command", ps_script],
            capture_output=True, timeout=8, check=False
        )
        # Alternativa más agresiva: reiniciar el servicio de audio de Windows
        # solo si lo anterior falló.  Descomentar si el problema persiste:
        #
        # subprocess.run(
        #     ["net", "stop", "Audiosrv", "/y"],
        #     capture_output=True, timeout=10, check=False
        # )
        # subprocess.run(
        #     ["net", "start", "Audiosrv"],
        #     capture_output=True, timeout=10, check=False
        # )
        log.info("Audio del PC restaurado")
    except Exception as e:
        log.warning(f"No se pudo restaurar el audio del PC: {e}")


# ── Cursor del PC (ocultar cursor en el stream de Sunshine) ───────────────────
_cursor_hidden = False
_CURSOR_IDS = [
    32512,  # IDC_ARROW
    32513,  # IDC_IBEAM
    32514,  # IDC_WAIT
    32515,  # IDC_CROSS
    32516,  # IDC_UPARROW
    32640,  # IDC_SIZE (obsoleto)
    32641,  # IDC_ICON (obsoleto)
    32642,  # IDC_SIZENWSE
    32643,  # IDC_SIZENESW
    32644,  # IDC_SIZEWE
    32645,  # IDC_SIZENS
    32646,  # IDC_SIZEALL
    32648,  # IDC_NO
    32649,  # IDC_HAND
    32650,  # IDC_APPSTARTING
    32651,  # IDC_HELP
]


def _set_pc_cursor_visible(visible: bool):
    """Muestra u oculta el cursor de Windows en el stream de Sunshine.
    Cuando 'visible=False', reemplaza todos los cursores del sistema con
    un cursor 1×1 completamente transparente — Sunshine capturará ese
    cursor invisible y el AdaptiveCursorView de Android se convierte en
    el ÚNICO cursor real. Cuando 'visible=True', restaura los cursores
    originales del sistema mediante SPI_SETCURSORS (no requiere guardar
    handles: Windows los relees del registro automáticamente).
    """
    global _cursor_hidden
    if _cursor_hidden == (not visible):
        return
    try:
        import ctypes
        import ctypes.wintypes
        user32 = ctypes.windll.user32
        if not visible:
            # Crear cursor 1×1 completamente transparente
            # AND mask = 0xFF (deja el fondo tal cual), XOR mask = 0x00 (no pinta nada)
            and_mask = ctypes.create_string_buffer(b"\xFF")
            xor_mask = ctypes.create_string_buffer(b"\x00")
            empty = user32.CreateCursor(None, 0, 0, 1, 1, and_mask, xor_mask)
            if empty:
                for cid in _CURSOR_IDS:
                    # CopyCursor porque SetSystemCursor consume (destruye) el handle
                    copy = user32.CopyCursor(empty)
                    if copy:
                        user32.SetSystemCursor(copy, cid)
                user32.DestroyCursor(empty)
            _cursor_hidden = True
            log.info("Cursor del PC ocultado en el stream (AdaptiveCursorView es el cursor real)")
        else:
            # SPI_SETCURSORS (0x0057): restaura todos los cursores desde el registro
            user32.SystemParametersInfoW(0x0057, 0, None, 3)
            _cursor_hidden = False
            log.info("Cursor del PC restaurado")
    except Exception as e:
        log.warning(f"No se pudo {'ocultar' if not visible else 'restaurar'} el cursor del PC: {e}")


def _set_pc_clipboard(text: str):
    try:
        import win32clipboard
        import win32con
        win32clipboard.OpenClipboard()
        try:
            win32clipboard.EmptyClipboard()
            win32clipboard.SetClipboardData(win32con.CF_UNICODETEXT, text)
        finally:
            win32clipboard.CloseClipboard()
    except Exception as e:
        log.debug(f"set clipboard error: {e}")


def foreground_loop(stop_event: threading.Event):
    """Vigila la ventana en primer plano del PC y emite un mensaje al bus
    cuando cambia. Esto permite a Android adaptar SmartBar/FAB a la app activa
    (Chrome, VS Code, etc.) sin que la app tenga que adivinar.
    """
    if not HAS_WIN32:
        log.warning("foreground_app desactivado: pywin32 no disponible")
        return
    while not stop_event.is_set():
        try:
            hwnd = win32gui.GetForegroundWindow()
            if hwnd and hwnd != _last_foreground["hwnd"]:
                try:
                    _, pid = win32process.GetWindowThreadProcessId(hwnd)
                    process = psutil.Process(pid).name()
                except Exception:
                    process = "unknown"
                try:
                    title = win32gui.GetWindowText(hwnd) or ""
                except Exception:
                    title = ""
                # Solo emitir si cambia process o title (evita ruido de hwnd transitorios)
                if process != _last_foreground["process"] or title != _last_foreground["title"]:
                    _last_foreground.update({"process": process, "title": title, "hwnd": hwnd})
                    msg = {"type": "foreground_app", "process": process, "title": title}
                    log.info(f"App en primer plano: {process} — {title[:60]}")
                    asyncio.run_coroutine_threadsafe(bus_broadcast(msg), loop)
                else:
                    _last_foreground["hwnd"] = hwnd
        except Exception as e:
            log.debug(f"foreground_loop: {e}")
        time.sleep(FOREGROUND_POLL_INTERVAL)


def file_http_loop(stop_event: threading.Event):
    """Servidor HTTP que permite al móvil LISTAR y DESCARGAR archivos del PC.
      GET /list?path=<p>   → JSON {path, parent, entries:[{name,dir,size}]}
                             (path vacío = unidades del PC + carpeta de usuario)
      GET /get?path=<p>    → descarga (stream) el archivo del PC
    Auth: si hay PIN, exige ?token=<PIN>. Pensado para LAN de confianza."""
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
    from urllib.parse import urlparse, parse_qs, unquote

    def roots():
        items = []
        home = os.path.expanduser("~")
        items.append({"name": "🏠 " + os.path.basename(home), "dir": True, "size": 0, "path": home})
        if os.name == "nt":
            import string
            for d in string.ascii_uppercase:
                drive = f"{d}:\\"
                if os.path.exists(drive):
                    items.append({"name": drive, "dir": True, "size": 0, "path": drive})
        else:
            items.append({"name": "/", "dir": True, "size": 0, "path": "/"})
        return items

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *a):
            pass

        def _q(self):
            return parse_qs(urlparse(self.path).query)

        def _auth_ok(self):
            if not PIN:
                return True
            return self._q().get("token", [""])[0] == PIN

        def do_GET(self):
            if not self._auth_ok():
                self.send_response(401)
                self.end_headers()
                return
            parsed = urlparse(self.path)
            q = parse_qs(parsed.query)
            path = unquote(q.get("path", [""])[0])
            try:
                if parsed.path == "/list":
                    self._do_list(path)
                elif parsed.path == "/get":
                    self._do_get(path)
                else:
                    self.send_response(404)
                    self.end_headers()
            except Exception as e:
                log.debug(f"file_http: {e}")
                try:
                    self.send_response(500)
                    self.end_headers()
                except Exception:
                    pass

        def _do_list(self, path):
            if not path:
                entries = roots()
                parent = ""
            else:
                entries = []
                try:
                    for name in sorted(os.listdir(path), key=str.lower):
                        full = os.path.join(path, name)
                        try:
                            is_dir = os.path.isdir(full)
                            size = 0 if is_dir else os.path.getsize(full)
                        except Exception:
                            is_dir, size = False, 0
                        entries.append({"name": name, "dir": is_dir, "size": size,
                                        "path": full})
                except Exception:
                    pass
                # carpetas primero, luego por nombre
                entries.sort(key=lambda e: (not e["dir"], e["name"].lower()))
                parent = os.path.dirname(path.rstrip("\\/")) if path not in ("/",) else ""
            body = json.dumps({"path": path, "parent": parent, "entries": entries}).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _do_get(self, path):
            if not path or not os.path.isfile(path):
                self.send_response(404)
                self.end_headers()
                return
            size = os.path.getsize(path)
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(size))
            self.send_header("Content-Disposition",
                             f'attachment; filename="{os.path.basename(path)}"')
            self.end_headers()
            with open(path, "rb") as f:
                while True:
                    chunk = f.read(65536)
                    if not chunk:
                        break
                    self.wfile.write(chunk)

    try:
        httpd = ThreadingHTTPServer(("0.0.0.0", FILE_PORT), Handler)
    except Exception as e:
        log.warning(f"Explorador de archivos del PC desactivado: {e}")
        return
    log.info(f"Explorador de archivos del PC en http://0.0.0.0:{FILE_PORT} (list/get)")
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    stop_event.wait()
    try:
        httpd.shutdown()
    except Exception:
        pass


def cursor_loop(stop_event: threading.Event):
    """Lee la posición REAL del cursor del PC y la envía al móvil (~60 Hz, solo
    cuando cambia) para que el cursor gigante de Android se dibuje exactamente
    donde apunta el del PC. Va por el canal de foco (broadcast), donde Android
    ya mapea coordenadas de Windows."""
    if not HAS_WIN32:
        log.warning("cursor_pos desactivado: pywin32 no disponible")
        return
    try:
        import win32api
    except Exception as e:
        log.warning(f"cursor_pos desactivado (falta win32api): {e}")
        return
    last = (None, None)
    while not stop_event.is_set():
        try:
            x, y = win32api.GetCursorPos()
            if (x, y) != last:
                last = (x, y)
                asyncio.run_coroutine_threadsafe(
                    broadcast({"type": "cursor_pos", "x": x, "y": y}), loop)
        except Exception:
            pass
        time.sleep(0.016)  # ~60 Hz


def clipboard_loop(stop_event: threading.Event):
    """Vigila el portapapeles del PC y, si cambia, lo envía al móvil."""
    while not stop_event.is_set():
        try:
            text = _get_pc_clipboard()
            if text and text != _last_clip["text"]:
                _last_clip["text"] = text
                asyncio.run_coroutine_threadsafe(
                    bus_broadcast({"type": "clipboard", "text": text}), loop)
        except Exception:
            pass
        time.sleep(0.6)


async def bus_handler(websocket, path="/"):
    """Recibe comandos del móvil por el SmartDisplayBus (puerto 47991)."""
    if not _ws_auth_ok(websocket):
        log.warning("Bus: conexión rechazada (PIN inválido)")
        await websocket.close(1008, "unauthorized")
        return
    bus_clients.add(websocket)
    log.info(f"Bus conectado: {websocket.remote_address}")
    # Primera sesión móvil → ocultar cursor del PC.
    if len(bus_clients) == 1:
        _set_pc_cursor_visible(False)   # El AdaptiveCursorView será el único cursor
    # Enviar snapshot del estado actual al cliente recién conectado, así no
    # tiene que esperar a que la ventana del PC cambie para saber qué app hay.
    if _last_foreground["process"]:
        try:
            await websocket.send(json.dumps({
                "type": "foreground_app",
                "process": _last_foreground["process"],
                "title": _last_foreground["title"] or "",
            }))
        except Exception:
            pass
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
            except Exception:
                continue
            t = data.get("type")
            if t == "files_offer":
                asyncio.create_task(_handle_files_offer(data))
            elif t == "clipboard":
                text = data.get("text", "")
                _last_clip["text"] = text   # evita reenviarlo de vuelta al móvil
                _set_pc_clipboard(text)
                log.info("Portapapeles actualizado desde el móvil")
            elif t == "power":
                action = data.get("action", "")
                log.info(f"Comando de energía recibido: {action}")
                _do_power(action)
            elif t == "get_stats":
                stats = await asyncio.to_thread(_get_system_stats)
                await websocket.send(json.dumps({"type": "stats", **stats}))
            elif t == "get_windows":
                wins = await asyncio.to_thread(_list_windows)
                await websocket.send(json.dumps({"type": "windows", "windows": wins}))
            elif t == "dev_gradle":
                action = data.get("action", "")
                log.info(f"Dev: ejecutando gradle '{action}'...")
                await websocket.send(json.dumps({"type": "dev_result", "action": action, "running": True}))
                res = await asyncio.to_thread(_run_gradle, action)
                await websocket.send(json.dumps({
                    "type": "dev_result", "action": action,
                    "ok": res["ok"], "tail": res["tail"]}))
            elif t == "focus_window":
                _focus_window(int(data.get("hwnd", 0)))
            elif t == "close_window":
                _close_window(int(data.get("hwnd", 0)))
            elif t == "minimize_window":
                _minimize_window(int(data.get("hwnd", 0)))
            elif t == "restore_audio":
                log.info("Restaurando dispositivo de audio del PC...")
                await asyncio.to_thread(_restore_pc_audio)
    except Exception:
        pass
    finally:
        bus_clients.discard(websocket)
        log.info("Bus desconectado")
        # Sin sesión móvil → restaurar cursor del PC y audio.
        if not bus_clients:
            _set_pc_cursor_visible(True)  # Restaurar cursor del sistema
            _restore_pc_audio()            # Restaurar dispositivo de audio


# ═══════════════════════════════════════════════════════════════════════════
# Pipeline DSP de Audio Profesional
# ═══════════════════════════════════════════════════════════════════════════
# Cadena de procesamiento (orden):
#   1. High-Pass Filter  (80 Hz) — elimina rumble de ventiladores/AC
#   2. Supresión Espectral — noise floor adaptativo (reduce ruido estacionario)
#   3. Voice Activity Detector — energy-based con hangover
#   4. Adaptive Noise Gate — con histéresis
#   5. Acoustic Echo Suppressor — NLMS + half-duplex
#   6. Automatic Gain Control — target -16 dBFS con compresión suave
#   7. Peak Limiter — protección contra clipping
#   8. Jitter Buffer adaptativo — con packet loss concealment
# ═══════════════════════════════════════════════════════════════════════════

class AudioProcessor:
    """Pipeline DSP completo para voz móvil→PC."""

    def __init__(self, rate=VOICE_SAMPLE_RATE):
        self.rate = rate
        self.frame_size = AUDIO_FRAME_SIZE
        self.fft_size = AUDIO_FFT_SIZE

        # ── Estado HPF: Butterworth IIR de 4to orden, 80 Hz ────────────────
        self._init_hpf()

        # ── Estado Supresión Espectral ─────────────────────────────────────
        self.noise_floor = np.ones(AUDIO_FFT_SIZE // 2 + 1, dtype=np.float32) * 1e-6
        self.noise_alpha = AUDIO_NOISE_FLOOR_ALPHA
        self.spectral_gate_db = -18  # dB por debajo del noise floor → gate

        # ── Estado VAD ─────────────────────────────────────────────────────
        self.vad_prob = 0.0
        self.vad_hangover = 0
        self.vad_active = False

        # ── Estado Noise Gate ──────────────────────────────────────────────
        self.gate_open = False
        self.gate_threshold = AUDIO_NOISE_GATE
        self.gate_hysteresis = AUDIO_GATE_HYST

        # ── Estado AGC ─────────────────────────────────────────────────────
        self.agc_gain_db = 0.0
        self.agc_target = AUDIO_AGC_TARGET
        self.agc_max_gain = AUDIO_AGC_MAX_GAIN
        self.agc_attack = 0.05   # suavizado rápido
        self.agc_release = 0.01  # suavizado lento

        # ── Estado AEC (NLMS adaptativo) ───────────────────────────────────
        aec_taps = int(AUDIO_AEC_TAIL_MS * rate / 1000)
        self.aec_coeffs = np.zeros(aec_taps, dtype=np.float32) if aec_taps > 0 else np.zeros(1)
        self.aec_buf = np.zeros(aec_taps, dtype=np.float32) if aec_taps > 0 else np.zeros(1)
        self.aec_mu = AUDIO_AEC_MU
        self.aec_enabled = aec_taps > 0

        # ── Estado Peak Limiter ────────────────────────────────────────────
        self.limiter_gain = 1.0
        self.limiter_attack = 0.3
        self.limiter_release = 0.05

        log.info(f"AudioProcessor: {rate}Hz, HPF={AUDIO_HPF_CUTOFF}Hz, "
                 f"AEC_taps={aec_taps}, VAD={AUDIO_VAD_THRESH}dBFS")

    # ── High-Pass Filter ──────────────────────────────────────────────────

    def _init_hpf(self):
        """Diseña filtro Butterworth HPF de 4to orden, corte en AUDIO_HPF_CUTOFF Hz."""
        nyq = self.rate / 2
        norm_cut = AUDIO_HPF_CUTOFF / nyq
        if norm_cut <= 0 or norm_cut >= 1:
            self.hpf_b = np.array([1.0], dtype=np.float32)
            self.hpf_a = np.array([1.0], dtype=np.float32)
            self.hpf_zi = np.array([0.0], dtype=np.float32)
            return
        try:
            if HAS_SCIPY:
                sos = signal.butter(4, norm_cut, btype='high', output='sos')
                self.hpf_sos = sos
                self.hpf_zi = np.zeros((sos.shape[0], 2), dtype=np.float32)
                self._hpf_filter = self._hpf_filter_sos
            else:
                # IIR manual: filtro de primer orden en cascada (2 etapas)
                # Etapa 1
                w0 = 2 * np.pi * AUDIO_HPF_CUTOFF / self.rate
                alpha = np.sin(w0) / (2 * 0.707)  # Q=0.707 (Butterworth)
                b0 = (1 + np.cos(w0)) / 2
                b1 = -(1 + np.cos(w0))
                b2 = b0
                a0 = 1 + alpha
                a1 = -2 * np.cos(w0)
                a2 = 1 - alpha
                self.hpf_b1 = np.array([b0/a0, b1/a0, b2/a0], dtype=np.float32)
                self.hpf_a1 = np.array([1.0, a1/a0, a2/a0], dtype=np.float32)
                self.hpf_x1 = np.zeros(2, dtype=np.float32)
                self.hpf_y1 = np.zeros(2, dtype=np.float32)
                self._hpf_filter = self._hpf_filter_iir
        except Exception as e:
            log.warning(f"HPF no disponible ({e}) — saltando")
            self._hpf_filter = lambda x: x

    def _hpf_filter_sos(self, samples: np.ndarray) -> np.ndarray:
        """Filtro SOS (requiere scipy)."""
        out, self.hpf_zi = signal.sosfilt(self.hpf_sos, samples, zi=self.hpf_zi)
        return out.astype(np.float32)

    def _hpf_filter_iir(self, samples: np.ndarray) -> np.ndarray:
        """Filtro IIR manual (2 etapas biquad)."""
        out = np.zeros_like(samples)
        for i in range(len(samples)):
            x = samples[i]
            # Etapa 1
            y = (self.hpf_b1[0] * x + self.hpf_b1[1] * self.hpf_x1[0] +
                 self.hpf_b1[2] * self.hpf_x1[1] - self.hpf_a1[1] * self.hpf_y1[0] -
                 self.hpf_a1[2] * self.hpf_y1[1])
            self.hpf_x1[1] = self.hpf_x1[0]
            self.hpf_x1[0] = x
            self.hpf_y1[1] = self.hpf_y1[0]
            self.hpf_y1[0] = y
            out[i] = y
        return out

    # ── Supresión Espectral (noise floor adaptativo) ─────────────────────

    def _spectral_suppress(self, samples: np.ndarray) -> np.ndarray:
        """Reduce ruido estacionario via spectral gating con noise floor adaptativo."""
        if len(samples) < self.fft_size:
            return samples

        # Ventana Hann y solapamiento 50%
        hop = self.fft_size // 2
        win = np.hanning(self.fft_size).astype(np.float32)
        out = np.zeros(len(samples), dtype=np.float32)
        denorm = np.zeros(len(samples), dtype=np.float32)

        for start in range(0, len(samples) - self.fft_size + 1, hop):
            block = samples[start:start + self.fft_size] * win
            spec = np.fft.rfft(block)
            mag = np.abs(spec).astype(np.float32)
            phase = np.angle(spec)

            # Actualizar noise floor (solo en frames de baja energía)
            energy_db = 20 * np.log10(np.mean(mag) + 1e-10)
            if energy_db < AUDIO_VAD_THRESH + 6:
                self.noise_floor = (1 - self.noise_alpha) * self.noise_floor + self.noise_alpha * mag

            # Spectral gate: atenuar bins por debajo del noise floor
            mask = np.ones_like(mag)
            threshold = self.noise_floor * (10 ** (self.spectral_gate_db / 20))
            mask[mag < threshold] = 0.15  # -16 dB de atenuación parcial (no hard gate)

            # Suavizado espectral (evita artefactos musicales)
            mag_clean = mag * mask

            # Reconstruir con fase original
            block_out = np.fft.irfft(mag_clean * np.exp(1j * phase))
            out[start:start + self.fft_size] += block_out * win
            denorm[start:start + self.fft_size] += win ** 2

        # Normalizar
        denorm = np.maximum(denorm, 1e-10)
        out = out / denorm
        return out

    # ── Voice Activity Detector ───────────────────────────────────────────

    def _vad(self, samples: np.ndarray) -> bool:
        """VAD energy-based con hangover."""
        energy = 20 * np.log10(np.sqrt(np.mean(samples ** 2)) + 1e-10)
        is_voice = energy > AUDIO_VAD_THRESH

        if is_voice:
            self.vad_hangover = AUDIO_VAD_HANGOVER
            self.vad_active = True
        elif self.vad_hangover > 0:
            self.vad_hangover -= 1
        else:
            self.vad_active = False

        return self.vad_active

    # ── Adaptive Noise Gate ───────────────────────────────────────────────

    def _noise_gate(self, samples: np.ndarray) -> np.ndarray:
        """Noise gate con histéresis. Aplica fade suave para evitar clicks."""
        energy_db = 20 * np.log10(np.sqrt(np.mean(samples ** 2)) + 1e-10)

        open_thresh = self.gate_threshold
        close_thresh = self.gate_threshold - self.gate_hysteresis

        if not self.gate_open and energy_db > open_thresh:
            self.gate_open = True
        elif self.gate_open and energy_db < close_thresh:
            self.gate_open = False

        if not self.gate_open:
            # Fade out suave (5ms linear ramp)
            fade_len = min(len(samples), int(self.rate * 0.005))
            if fade_len > 0:
                ramp = np.linspace(1, 0, fade_len, dtype=np.float32)
                out = samples.copy()
                out[:fade_len] *= ramp
                out[fade_len:] = 0
                return out
            return np.zeros_like(samples)
        return samples

    # ── Acoustic Echo Suppressor (NLMS) ───────────────────────────────────

    def _aec_process(self, samples: np.ndarray) -> np.ndarray:
        """NLMS adaptive filter para supresión de eco acústico.
        NOTA: Para AEC completo se necesita la referencia del altavoz (loopback).
        Esta implementación usa half-duplex heuristic + supresión residual."""
        if not self.aec_enabled or len(samples) == 0:
            return samples

        out = np.zeros_like(samples)
        for i in range(len(samples)):
            # Shift delay line
            self.aec_buf[1:] = self.aec_buf[:-1]
            self.aec_buf[0] = samples[i]

            # Filtro adaptativo
            est_echo = np.dot(self.aec_coeffs, self.aec_buf)
            error = samples[i] - est_echo

            # NLMS update (solo cuando hay energía significativa en la referencia)
            norm = np.dot(self.aec_buf, self.aec_buf) + 1e-10
            if norm > 1e-6:
                self.aec_coeffs += (self.aec_mu * error / norm) * self.aec_buf

            out[i] = np.clip(error, -32768, 32767)

        # Supresión residual suave en frecuencia si hay eco residual fuerte
        if np.std(out) > 0.1 * np.std(samples):
            mix = 0.7
            out = mix * out + (1 - mix) * samples

        return out.astype(np.float32)

    # ── Automatic Gain Control ────────────────────────────────────────────

    def _agc(self, samples: np.ndarray) -> np.ndarray:
        """AGC con compresión suave. Mantiene nivel objetivo con attack/release asimétrico."""
        if len(samples) == 0:
            return samples

        rms = np.sqrt(np.mean(samples ** 2)) + 1e-10
        current_db = 20 * np.log10(rms / 32768.0)
        target_db = self.agc_target
        delta_db = target_db - current_db

        # Limitar ganancia máxima
        delta_db = np.clip(delta_db, -6, self.agc_max_gain)

        # Suavizado asimétrico (attack rápido si necesita subir, release lento si bajar)
        if delta_db > self.agc_gain_db:
            alpha = self.agc_attack
        else:
            alpha = self.agc_release
        self.agc_gain_db += alpha * (delta_db - self.agc_gain_db)

        gain_linear = 10 ** (self.agc_gain_db / 20)
        out = samples * gain_linear
        return np.clip(out, -32768, 32767)

    # ── Peak Limiter ──────────────────────────────────────────────────────

    def _peak_limiter(self, samples: np.ndarray) -> np.ndarray:
        """Look-ahead peak limiter con suavizado."""
        peak = np.max(np.abs(samples)) + 1e-10
        if peak > 30000:
            target_gain = 30000.0 / peak
            # Suavizar gain
            self.limiter_gain += self.limiter_attack * (target_gain - self.limiter_gain)
        else:
            self.limiter_gain += self.limiter_release * (1.0 - self.limiter_gain)
        self.limiter_gain = np.clip(self.limiter_gain, 0.1, 1.0)
        return np.clip(samples * self.limiter_gain, -32768, 32767).astype(np.int16)

    # ── Pipeline completo ─────────────────────────────────────────────────

    def process(self, raw_bytes: bytes) -> bytes:
        """Procesa un chunk de PCM 16-bit mono a través de todo el pipeline DSP."""
        if not HAS_NP:
            return raw_bytes

        # Convertir a float32 numpy
        samples = np.frombuffer(raw_bytes, dtype=np.int16).astype(np.float32)
        if len(samples) == 0:
            return raw_bytes

        # 1. High-Pass Filter (elimina rumble)
        samples = self._hpf_filter(samples)

        # 2. Supresión espectral (ruido estacionario)
        samples = self._spectral_suppress(samples)

        # 3. Voice Activity Detector
        is_voice = self._vad(samples)

        # 4. Noise Gate
        samples = self._noise_gate(samples)

        # Si no hay voz y el gate está cerrado, devolver silencio inmediatamente
        if not is_voice and not self.gate_open:
            return b"\x00" * len(raw_bytes)

        # 5. AEC (supresión de eco)
        samples = self._aec_process(samples)

        # 6. AGC (control automático de ganancia)
        samples = self._agc(samples)

        # 7. Peak Limiter
        output = self._peak_limiter(samples)

        return output.tobytes()


# ── Canal VOZ (48999 UDP): reproducir el micrófono del móvil en el PC ──────

def voice_loop(stop_event: threading.Event):
    """Recibe PCM del móvil por UDP y lo reproduce en el PC con pipeline DSP profesional."""
    try:
        import sounddevice as sd
    except Exception as e:
        log.warning(f"Voz móvil→PC desactivada (instala 'sounddevice'): {e}")
        return

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("0.0.0.0", VOICE_PORT))
    sock.settimeout(0.5)

    # Instanciar pipeline DSP
    dsp = AudioProcessor() if HAS_NP else None
    if dsp is None:
        log.warning("numpy no disponible — pipeline DSP desactivado, audio raw")

    # Jitter Buffer adaptativo con PLC (Packet Loss Concealment)
    buffer_lock = threading.Lock()
    audio_buffer = bytearray()

    # 80ms de buffer = latencia baja + protección contra jitter de red
    MAX_BUFFER_SIZE = int(VOICE_SAMPLE_RATE * 2 * 0.08)
    # Prebuffer inicial: 20ms (mínimo para startup suave)
    INITIAL_PREBUFFER = int(VOICE_SAMPLE_RATE * 2 * 0.02)
    has_started_playing = False

    # PLC: último frame válido para fade-out en underruns
    last_good_frame = np.zeros(AUDIO_FRAME_SIZE, dtype=np.int16)
    plc_fade = 0.0  # factor de fade cuando estamos en underrun

    # Estadísticas
    packets = 0
    underruns = 0
    last_log_time = time.monotonic()

    def audio_callback(outdata, frames, time_info, status):
        nonlocal audio_buffer, has_started_playing, last_good_frame, plc_fade, underruns
        bytes_needed = frames * 2  # 16-bit mono
        now = time.monotonic()

        with buffer_lock:
            # ── Prebuffer inicial ──
            if not has_started_playing:
                if len(audio_buffer) >= INITIAL_PREBUFFER:
                    has_started_playing = True
                else:
                    outdata.fill(0)
                    return

            # ── Leer del buffer ──
            if len(audio_buffer) >= bytes_needed:
                chunk = audio_buffer[:bytes_needed]
                del audio_buffer[:bytes_needed]

                # Procesar con DSP si está disponible
                if dsp is not None:
                    chunk = dsp.process(bytes(chunk))

                samples = np.frombuffer(chunk, dtype=np.int16).copy()
                last_good_frame = samples[-AUDIO_FRAME_SIZE:] if len(samples) >= AUDIO_FRAME_SIZE else samples
                plc_fade = 0.0
                outdata[:len(samples)] = samples.reshape(-1, 1)
                if len(samples) < frames:
                    outdata[len(samples):] = 0
            else:
                # ── Underrun: Packet Loss Concealment ──
                underruns += 1
                if len(audio_buffer) > 0:
                    chunk = audio_buffer[:]
                    audio_buffer.clear()
                    if dsp is not None:
                        chunk = dsp.process(bytes(chunk))
                    samples = np.frombuffer(chunk, dtype=np.int16)
                    pad = bytes_needed - len(samples)
                    if pad > 0:
                        # PLC con fade-out del último frame bueno
                        plc_fade = min(1.0, plc_fade + 0.05)
                        fade = np.linspace(1.0 - plc_fade, 0, pad // 2, dtype=np.float32)
                        last_part = (last_good_frame[:pad // 2].astype(np.float32) *
                                     fade).astype(np.int16)
                        samples = np.concatenate([samples, last_part])
                    outdata[:len(samples)] = samples.reshape(-1, 1) if len(samples) > 0 else 0
                    if len(samples) < frames:
                        outdata[len(samples):] = 0
                else:
                    # Silencio total + fade-out
                    plc_fade = min(1.0, plc_fade + 0.1)
                    fade = np.linspace(1.0 - plc_fade, 0, frames, dtype=np.float32)
                    outdata[:, 0] = (last_good_frame[:frames].astype(np.float32) *
                                     fade).astype(np.int16)
                    has_started_playing = False

    # ── Abrir stream de salida ──
    try:
        stream = sd.OutputStream(
            samplerate=VOICE_SAMPLE_RATE,
            channels=1,
            dtype="int16",
            callback=audio_callback,
            blocksize=AUDIO_FRAME_SIZE,
        )
        stream.start()
    except Exception as e:
        log.error(f"Voz móvil→PC: no se pudo abrir dispositivo de salida a "
                  f"{VOICE_SAMPLE_RATE} Hz ({e}). Revisa el dispositivo de reproducción.")
        sock.close()
        return

    log.info(f"Voz móvil→PC activa en UDP {VOICE_PORT} | "
             f"DSP={'SÍ (HPF+NS+VAD+Gate+AEC+AGC+Limiter)' if dsp else 'NO (raw)'} | "
             f"Buffer={MAX_BUFFER_SIZE}bytes | Prebuffer={INITIAL_PREBUFFER}bytes")

    # ── Retorno de voz (PC→móvil): llamada bidireccional ────────────────────
    # Reutiliza el MISMO socket UDP (sin puerto nuevo): cuando llega un paquete
    # del móvil, recordamos su (ip, puerto) y le devolvemos el micrófono del PC.
    # El mic del PC solo se activa mientras hay un peer activo (privacidad: no
    # se escucha nada si no hay llamada en curso) y se apaga solo tras silencio.
    PEER_TIMEOUT = 6.0
    peer_lock = threading.Lock()
    peer_state = {"addr": None, "last_seen": 0.0}
    return_stream_holder = {"stream": None}

    def _mic_callback(indata, frames, time_info, status):
        with peer_lock:
            addr = peer_state["addr"]
            fresh = (time.monotonic() - peer_state["last_seen"]) < PEER_TIMEOUT
        if not addr or not fresh:
            return
        try:
            payload = indata.copy().tobytes()
            if VOICE_PREFIX:
                payload = VOICE_PREFIX + payload
            sock.sendto(payload, addr)
        except Exception:
            pass

    def _ensure_return_stream():
        if return_stream_holder["stream"] is not None:
            return
        try:
            s = sd.InputStream(samplerate=VOICE_SAMPLE_RATE, channels=1, dtype="int16",
                                callback=_mic_callback, blocksize=AUDIO_FRAME_SIZE)
            s.start()
            return_stream_holder["stream"] = s
            log.info("Voz PC→móvil: micrófono del PC activado (llamada en curso).")
        except Exception as e:
            log.warning(f"Voz PC→móvil desactivada: no se pudo abrir el micrófono del PC ({e}).")

    def _maybe_stop_return_stream():
        s = return_stream_holder["stream"]
        if s is None:
            return
        with peer_lock:
            fresh = (time.monotonic() - peer_state["last_seen"]) < PEER_TIMEOUT
        if fresh:
            return
        try:
            s.stop(); s.close()
        except Exception:
            pass
        return_stream_holder["stream"] = None
        log.info("Voz PC→móvil: micrófono del PC desactivado (llamada finalizada).")

    try:
        while not stop_event.is_set():
            try:
                data, addr = sock.recvfrom(65536)
            except socket.timeout:
                _maybe_stop_return_stream()
                continue
            except Exception:
                continue

            if not data:
                continue

            # Auth UDP
            if VOICE_PREFIX:
                if len(data) <= 8 or data[:8] != VOICE_PREFIX:
                    continue
                data = data[8:]

            packets += 1
            if packets == 1:
                log.info("Voz móvil→PC: primer paquete recibido.")

            with peer_lock:
                peer_state["addr"] = addr
                peer_state["last_seen"] = time.monotonic()
            _ensure_return_stream()

            with buffer_lock:
                audio_buffer.extend(data)
                # Limitar latencia máxima
                if len(audio_buffer) > MAX_BUFFER_SIZE:
                    excess = len(audio_buffer) - MAX_BUFFER_SIZE
                    del audio_buffer[:excess]
                    # Reducir el prebuffer si estamos acumulando
                    has_started_playing = True

            # Log periódico de estadísticas
            now = time.monotonic()
            if now - last_log_time > 30:
                buf_ms = (len(audio_buffer) / (VOICE_SAMPLE_RATE * 2)) * 1000
                log.info(f"Voz: {packets} paquetes, buffer={buf_ms:.0f}ms, "
                         f"underruns={underruns}, gate={'OPEN' if hasattr(dsp, 'gate_open') and dsp and dsp.gate_open else '?'}")
                last_log_time = now

    finally:
        try:
            stream.stop()
            stream.close()
        except Exception:
            pass
        try:
            if return_stream_holder["stream"] is not None:
                return_stream_holder["stream"].stop()
                return_stream_holder["stream"].close()
        except Exception:
            pass
        sock.close()
        if packets > 0:
            log.info(f"Voz móvil→PC: {packets} paquetes procesados, {underruns} underruns")


async def hub_agent_loop(hub_url: str, identity: dict):
    """Conecta al Hub (Electron) como agente, se registra y envía telemetría periódica."""
    import websockets
    import platform
    retry = 1
    while True:
        try:
            async with websockets.connect(hub_url) as ws:
                # Registrar como agente
                log.info(f"Agente → Hub: conectando a {hub_url}")
                await ws.send(json.dumps({
                    "type": "register",
                    "client": "agent",
                }))
                await ws.send(json.dumps({
                    "type": "register_agent",
                    "agentId": identity["agentId"],
                    "name": identity["name"],
                    "hostname": identity.get("hostname", platform.node()),
                    "os": identity.get("os", platform.platform()),
                    "version": "1.0.0",
                    "tags": ["companion", "local"],
                    "group": "Casa",
                }))
                log.info(f"Agente → Hub: registrado como {identity['name']}")

                # Bucle de telemetría cada 30s
                while True:
                    await asyncio.sleep(30)
                    try:
                        import psutil
                        cpu = psutil.cpu_percent(interval=0.5)
                        mem = psutil.virtual_memory()
                        disk = psutil.disk_usage("/" if os.name != "nt" else os.environ["SYSTEMDRIVE"])
                        uptime_s = int(time.time() - psutil.boot_time())
                    except ImportError:
                        # Fallback sin psutil
                        cpu = 0
                        mem, disk = None, None
                        uptime_s = 0

                    telemetry = {
                        "type": "agent_telemetry",
                        "agentId": identity["agentId"],
                        "cpu": cpu,
                        "ram": {
                            "usedGb": round(mem.used / (1024**3), 1) if mem else 0,
                            "totalGb": round(mem.total / (1024**3), 1) if mem else 0,
                            "percent": mem.percent if mem else 0,
                        } if mem else None,
                        "disk": {
                            "usedGb": round(disk.used / (1024**3), 1) if disk else 0,
                            "totalGb": round(disk.total / (1024**3), 1) if disk else 0,
                            "percent": disk.percent if disk else 0,
                        } if disk else None,
                        "uptime": uptime_s,
                        "version": "1.0.0",
                    }
                    await ws.send(json.dumps(telemetry))
                    log.debug(f"Telemetría enviada: CPU {cpu}%, RAM {mem.percent if mem else '?'}%")
        except Exception as e:
            log.warning(f"Agente → Hub: error ({e}), reconectando en {retry}s...")
            await asyncio.sleep(retry)
            retry = min(retry * 2, 60)  # backoff hasta 60s


async def main(host, port, hub_url=None):
    import websockets
    global PIN, VOICE_PREFIX
    PIN = load_pin()
    if PIN:
        VOICE_PREFIX = hashlib.sha256(PIN.encode("utf-8")).digest()[:8]
        log.info("=" * 48)
        log.info(f"  PIN de emparejamiento SmartDisplay: {PIN}")
        log.info("  Introdúcelo en la app Android (Ajustes → Companion PIN)")
        log.info("=" * 48)
    else:
        log.info("Auth desactivada (sin config.json). Para activarla: --gen-pin")
    log.info(f"SmartDisplay Companion Server iniciando en ws://{host}:{port}")

    # Identidad del agente
    identity = load_or_create_identity()
    log.info(f"Agente: {identity['name']} ({identity['agentId'][:8]}…)")

    monitor = FocusMonitor(on_text_field, on_cleared)
    monitor.start()
    asyncio.create_task(ping_loop())

    # Conectar al Hub (Electron) como agente gestionado
    if hub_url:
        asyncio.create_task(hub_agent_loop(hub_url, identity))
        log.info(f"Agente registrándose en Hub: {hub_url}")

    # Voz móvil→PC (UDP). El audio PC→móvil ya lo entrega Moonlight de forma nativa.
    stop_voice = threading.Event()
    threading.Thread(target=voice_loop, args=(stop_voice,), daemon=True).start()

    # Portapapeles PC↔móvil (vigila el portapapeles del PC y lo sincroniza).
    stop_clip = threading.Event()
    threading.Thread(target=clipboard_loop, args=(stop_clip,), daemon=True).start()

    # App en primer plano (Chrome, VS Code…) → permite a Android adaptar
    # SmartBar y FAB sin adivinar la aplicación activa.
    stop_fg = threading.Event()
    threading.Thread(target=foreground_loop, args=(stop_fg,), daemon=True).start()

    # Posición del cursor del PC → sincroniza el cursor gigante del móvil.
    stop_cursor = threading.Event()
    threading.Thread(target=cursor_loop, args=(stop_cursor,), daemon=True).start()

    # Explorador de archivos del PC (HTTP) → el móvil lista/descarga archivos del PC.
    stop_files = threading.Event()
    threading.Thread(target=file_http_loop, args=(stop_files,), daemon=True).start()

    async with websockets.serve(handler, host, port), \
               websockets.serve(bus_handler, host, BUS_PORT):
        log.info(f"Foco de texto en ws://{host}:{port}")
        log.info(f"Bus de archivos en ws://{host}:{BUS_PORT} (descargas → {DOWNLOAD_DIR})")
        log.info("Esperando conexiones...")
        log.info("Portapapeles PC↔móvil sincronizándose por el bus")
        try:
            await asyncio.Future()  # run forever
        finally:
            stop_voice.set()
            stop_clip.set()
            stop_fg.set()
            stop_cursor.set()
            stop_files.set()


def _save_pin(pin: str):
    """Guarda el PIN en config.json (activa la auth). Uso una sola vez, en el PC."""
    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump({"pin": pin}, f, indent=2)
    print(f"PIN guardado en {CONFIG_PATH}: {pin}")
    print("Introdúcelo en la app Android (Ajustes → Companion PIN).")


atexit.register(lambda: _set_pc_cursor_visible(True))

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="SmartDisplay Companion Server")
    parser.add_argument("--host", default="0.0.0.0", help="Dirección de escucha")
    parser.add_argument("--port", type=int, default=8765, help="Puerto WebSocket")
    parser.add_argument("--hub-url", default=None,
                        help="URL del Hub (Electron) para registro como agente. Ej: ws://localhost:3002")
    parser.add_argument("--gen-pin", action="store_true",
                        help="Genera y guarda un PIN de 6 dígitos (activa la auth) y sale.")
    parser.add_argument("--set-pin", metavar="PIN",
                        help="Guarda un PIN concreto (activa la auth) y sale.")
    args = parser.parse_args()

    # Configuración de seguridad opt-in (ejecutar una vez, en el PC):

    if args.gen_pin:
        _save_pin(f"{random.randint(0, 999999):06d}")
        raise SystemExit(0)
    if args.set_pin:
        _save_pin(str(args.set_pin).strip())
        raise SystemExit(0)

    # 1. Preparar el bucle asyncio en un hilo separado
    loop = asyncio.new_event_loop()
    
    def run_asyncio_loop():
        asyncio.set_event_loop(loop)
        try:
            loop.run_until_complete(main(args.host, args.port, args.hub_url))
        except Exception as e:
            log.error(f"Error en servidor asyncio: {e}")
            
    t = threading.Thread(target=run_asyncio_loop, daemon=True)
    t.start()

    # 2. Generar un icono simple para la bandeja del sistema (System Tray)
    def create_image():
        width = 64
        height = 64
        color1 = "#22D3EE" # Cyan
        color2 = "#8B5CF6" # Purple
        image = Image.new('RGB', (width, height), color1)
        dc = ImageDraw.Draw(image)
        dc.rectangle([width//4, height//4, width*3//4, height*3//4], fill=color2)
        return image

    # Acción para abrir la interfaz gráfica
    def on_open_gui(icon, item):
        if os.name != "nt":
            return
        try:
            import win32gui
            import ctypes
            hwnd = win32gui.FindWindow(None, "SmartDisplay AI")
            if hwnd:
                user32 = ctypes.windll.user32
                SW_RESTORE = 9
                user32.ShowWindow(hwnd, SW_RESTORE)
                user32.SetForegroundWindow(hwnd)
                log.info("Interfaz de SmartDisplay AI enfocada desde la bandeja.")
            else:
                # Intentar iniciar la app
                root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
                bat = os.path.join(root, "Escritorio", "Iniciar_SmartDisplay.bat")
                if os.path.exists(bat):
                    import subprocess
                    subprocess.Popen([bat], cwd=os.path.dirname(bat), creationflags=subprocess.CREATE_NEW_CONSOLE)
                    log.info("Iniciando interfaz de SmartDisplay AI...")
                else:
                    log.warning(f"No se encontró el iniciador de la interfaz en: {bat}")
        except Exception as e:
            log.warning(f"Error al abrir la interfaz: {e}")

    # 3. Acción para cerrar la aplicación desde la bandeja
    def on_quit(icon, item):
        log.info("Cerrando SmartDisplay Companion Server...")
        icon.stop()
        loop.call_soon_threadsafe(loop.stop)
        # sys.exit provocará que atexit restaure el cursor de Windows
        os._exit(0) 

    # 4. Crear y ejecutar el icono en el System Tray
    menu = pystray.Menu(
        pystray.MenuItem("SmartDisplay Companion", None, enabled=False),
        pystray.MenuItem("Abrir Panel de Control", on_open_gui, default=True),
        pystray.MenuItem("Cerrar servidor", on_quit)
    )
    icon = pystray.Icon("SmartDisplay", create_image(), "SmartDisplay Companion", menu)
    
    log.info("Iniciando aplicación en la Bandeja del Sistema (System Tray)...")
    icon.run()
