# SmartDisplay AI — Companion Server

Servidor ligero para Windows que permite a la app Android detectar automáticamente cuándo el foco de la interfaz está en un campo de texto, y abrir el teclado automáticamente.

## Instalación automática (recomendada, un solo comando)

Instala dependencias, abre el firewall, deja el companion **arrancando solo al
iniciar sesión** y lo ejecuta ya. No tienes que volver a lanzarlo a mano:

```powershell
powershell -ExecutionPolicy Bypass -File install.ps1
```

Tras esto, combinado con **Tailscale** (instalado una vez en PC y móvil) y el
auto-descubrimiento de IP de la app, los archivos / voz / portapapeles funcionan
entre cualquier red sin configuración manual.

## Instalación manual (alternativa)

```powershell
# 1. Instalar Python 3.10+ si no lo tienes
# 2. Instalar dependencias
pip install -r requirements.txt

# 3. Ejecutar el servidor
python companion_server.py
```

## Configuración en la App Android

En la configuración de SmartDisplay AI:
- **Companion Server IP**: la IP local de tu PC (ej. `192.168.1.10`)
- **Puerto**: `8765` (por defecto)

La app se conectará automáticamente al iniciar una sesión de streaming.

## Cómo funciona

El servidor usa **Windows UI Automation** para monitorizar qué control tiene el foco activo:

1. Detecta si es un campo de texto (`Edit`, `Document`, `RichEdit`, etc.)
2. Obtiene las coordenadas exactas del control en pantalla
3. Notifica a la app Android via WebSocket
4. La app abre el teclado lógico automáticamente y hace zoom al campo

## Protocolo WebSocket

### Mensaje de campo de texto detectado:
```json
{
  "type": "focus_changed",
  "is_text_field": true,
  "rect": { "x": 100, "y": 200, "w": 800, "h": 40 },
  "app": "Code.exe"
}
```

### Mensaje de foco liberado:
```json
{ "type": "focus_cleared" }
```

### Ping de heartbeat (cada 15s):
```json
{ "type": "ping", "ts": 1234567890 }
```

## Recepción automática de archivos (Móvil → PC)

Al tocar **Archivos** en el FAB, el móvil comparte los archivos y avisa por el
**bus (puerto 47991)** con URLs de descarga directas. El companion las descarga
automáticamente a `~/Downloads/SmartDisplay` — **sin abrir ningún navegador**.

```json
{ "type": "files_offer", "url": "http://IP-MOVIL:8080",
  "files": [ { "name": "video.mp4", "size": 524288000, "url": "http://IP-MOVIL:8080/dl?i=0" } ] }
```

## Voz móvil → PC ("hablar")

El móvil envía el micrófono como PCM (44100 Hz, mono, 16-bit) por **UDP 48999**.
El companion lo reproduce en el PC. Requiere `sounddevice` + `numpy`:
```powershell
pip install sounddevice numpy
```
> El sentido inverso (**escuchar el PC en el móvil**) ya lo entrega Moonlight de
> forma nativa al transmitir el audio del PC; no necesita el companion.
> ⚠️ Si el PC reproduce la voz por el **mismo dispositivo** que Sunshine captura,
> habrá eco; usa auriculares o un dispositivo de salida distinto.

## Firewall

Si Windows Firewall bloquea la conexión, abre los tres puertos:
```powershell
netsh advfirewall firewall add rule name="SmartDisplay Foco"  dir=in action=allow protocol=TCP localport=8765
netsh advfirewall firewall add rule name="SmartDisplay Bus"   dir=in action=allow protocol=TCP localport=47991
netsh advfirewall firewall add rule name="SmartDisplay Voz"   dir=in action=allow protocol=UDP localport=48999
```

## Inicio automático con Windows

Para que arranque al iniciar Windows, crea un acceso directo a `companion_server.py` en:
```
%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\
```

O usa el Task Scheduler para más control.
