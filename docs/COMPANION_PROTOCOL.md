# SmartDisplay AI — Contrato del Companion Server (PC → Móvil)

Define el JSON que el **companion server de Windows** debe emitir para alimentar las
funciones inteligentes del cliente Android. Todo es WebSocket, JSON por mensaje (un
objeto por frame de texto).

> Estado de cada bloque marcado como: **[IMPLEMENTADO]** (la app ya lo consume) o
> **[PROPUESTO]** (requiere cambios en la app para consumirse; aún no activo).

## Canales

| Canal | URL | Puerto | Dirección | Uso |
|---|---|---|---|---|
| `TextFocusWatcher` | `ws://<ip-pc>:8765` | 8765 | PC → Móvil | Foco de campos de texto (y, propuesto, hover de elementos) |
| `SmartDisplayBus`  | `ws://<ip-pc>:47991` | 47991 | bidireccional | Comandos generales (IA, portapapeles, telemetría) |

### Sistema de coordenadas
Los `rect` van en **píxeles de la resolución del PC remoto** (la del stream). El móvil
los escala con `streamView.getWidth()/prefConfig.width` y `.../prefConfig.height` antes
de pintarlos. El PC debe enviar coordenadas absolutas del escritorio remoto.

---

## Canal A — TextFocusWatcher (`:8765`)

### A.1 Foco en campo de texto — **[IMPLEMENTADO]**
```json
{
  "type": "focus_changed",
  "is_text_field": true,
  "rect": { "x": 100, "y": 200, "w": 800, "h": 40 },
  "control_name": "TextBox1",
  "app": "Code.exe"
}
```
| Campo | Tipo | Req. | Notas |
|---|---|---|---|
| `type` | string | sí | debe ser `"focus_changed"` |
| `is_text_field` | bool | sí | si es `false`, la app ignora el mensaje hoy |
| `rect.x/y/w/h` | number | sí | px del escritorio remoto |
| `control_name` | string | no | el cliente lo ignora actualmente |
| `app` | string | no | nombre del ejecutable (p.ej. `Code.exe`) |

**Qué hace la app al recibirlo (hoy):** muestra el teclado lógico, hace *smart zoom*
al `rect`, dibuja el recuadro de foco y pone el cursor en estado **TEXT** (I-beam)
vía `SmartCursorEngine`.

### A.2 Pérdida de foco — **[IMPLEMENTADO]**
```json
{ "type": "focus_cleared" }
```
La app oculta el recuadro, revierte el zoom y vuelve el cursor a **NORMAL**.

### A.3 Hover de elemento genérico — **[IMPLEMENTADO]**
Habilita los estados BUTTON/LINK y el Smart Snap sin depender solo de campos de texto.
La app ya parsea este mensaje (`TextFocusWatcher` + `Game` → `SmartCursorEngine`).
```json
{
  "type": "hover_element",
  "element": "button",            // none | text_input | button | link | ide_workspace
  "rect": { "x": 540, "y": 700, "w": 120, "h": 36 },
  "snap": { "x": 600, "y": 718 }, // centro objetivo para Smart Snap (opcional)
  "app": "chrome.exe"
}
```
| `element` | Mapea a `SmartCursorEngine.ElementType` | Efecto en el cursor |
|---|---|---|
| `none` | `NONE` | estado NORMAL |
| `text_input` | `TEXT_INPUT` | I-beam + halo |
| `button` | `BUTTON` | escala 1.1x + halo 35% |
| `link` | `LINK` | estilo botón |
| `ide_workspace` | `IDE_WORKSPACE` | NORMAL (gestión por popup, futuro) |

**Smart Snap:** si llega `snap{x,y}` y el cursor está a 5–100 px, se aplicaría
`AdaptiveCursorView.smartSnapTo(...)`. ⚠️ Requiere además una **fuente real de la
posición del cursor** (hoy el halo no la expone); ver "Pendientes".

---

## Canal B — SmartDisplayBus (`:47991`)

Transporte JSON genérico ya operativo (`sendMessage()` móvil→PC, `onMessageReceived()`
PC→móvil). Hoy los mensajes entrantes solo se registran en log. Forma sugerida:

### B.1 Perfil de cursor por aplicación — **[PROPUESTO]**
```json
{
  "type": "cursor_profile",
  "app": "Code.exe",
  "sensitivity": 0.8,   // multiplicador de velocidad
  "precision": true,    // fuerza precision mode
  "magnetism": 0.15     // fuerza de snap 0..1
}
```
Requiere exponer en `AdaptiveCursorView` setters de sensibilidad/precisión/magnetismo
(hoy precision es solo por velocidad) y consumir el mensaje en `Game.onMessageReceived`.

### B.2 Recepción automática de archivos (Móvil → PC) — **[IMPLEMENTADO, app envía]**
Al tocar **Archivos** en el FAB, el móvil abre el selector, arranca su servidor HTTP
y **emite por el bus** este mensaje con URLs de descarga directas. El companion del PC
solo tiene que **descargar cada `url`** y guardarla → sin navegador.
```json
{
  "type": "files_offer",
  "url": "http://192.168.1.20:8080",
  "files": [
    { "name": "video.mp4", "size": 524288000, "url": "http://192.168.1.20:8080/dl?i=0" },
    { "name": "doc.pdf",   "size": 240128,    "url": "http://192.168.1.20:8080/dl?i=1" }
  ]
}
```
| Campo | Tipo | Notas |
|---|---|---|
| `url` | string | base del servidor del móvil (IP Wi-Fi : puerto 8080) |
| `files[].name` | string | nombre original |
| `files[].size` | number | bytes (0 si desconocido) |
| `files[].url` | string | **GET directo** que devuelve el archivo (octet-stream) |

**Receptor mínimo en el PC (pseudo):** al recibir `files_offer`, por cada `f` →
`GET f.url` → escribir a la carpeta de descargas. El servidor del móvil ya hace
streaming, así que vale para archivos grandes.

Fallback sin companion: el `Toast` del móvil muestra la `url` base por si se quiere
abrir manualmente en un navegador del PC.

### B.3 Portapapeles bidireccional — **[IMPLEMENTADO]**
Mismo mensaje en ambos sentidos por el bus (47991):
```json
{ "type": "clipboard", "text": "contenido copiado" }
```
- **Móvil → PC:** al copiar en el móvil (con la app en primer plano), envía el texto;
  el companion lo pone en el portapapeles de Windows.
- **PC → Móvil:** el companion vigila el portapapeles del PC y, al cambiar, envía el
  texto; el móvil lo aplica con `ClipboardManager`.
- **Anti-rebote:** ambos lados recuerdan el último valor para no reenviarlo en bucle.
- Nota Android: leer el portapapeles solo funciona con la app en **primer plano**
  (restricción de Android 10+), que es el caso durante el streaming.

### B.4 Otros comandos sugeridos — **[PROPUESTO]**
`notification`, `app_changed` (cambio de app activa para activar el perfil B.1).

---

## Pendientes del lado app
- ✅ **Hecho** — `TextFocusWatcher` parsea `hover_element` (+ callback `onHoverElement`).
- ✅ **Hecho** — `Game` enruta `hover_element` → `SmartCursorEngine` (estado + Smart Snap con escalado de coordenadas).
- ✅ **Hecho** — `AdaptiveCursorView.getCursorX()/getCursorY()`.
- ⬜ **Pendiente (B.1)** — setters de sensibilidad/precisión/magnetismo en
  `AdaptiveCursorView` + consumo de `cursor_profile` en `Game.onMessageReceived`
  (adaptación por app).

> Nota: el Smart Snap actúa solo sobre el **halo visual** (no sobre el puntero real,
> que lo gobierna el sistema de toques de Moonlight). Es asistencia visual.

## Seguridad
Ambos canales son `ws://` sin cifrar; úsense solo en **red local de confianza**.
Coincide con el hallazgo P0 de la auditoría (bus sin cifrar).
