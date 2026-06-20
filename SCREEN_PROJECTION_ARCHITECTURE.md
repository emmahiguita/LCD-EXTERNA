# ARQUITECTURA DE PROYECCIÓN DE PANTALLA
## SmartDisplay AI - Sistema de Streaming de Video

**Fecha:** 18 de junio de 2026  
**Estado:** Documentación de arquitectura actual

---

## RESUMEN EJECUTIVO

SmartDisplay AI implementa un sistema de proyección de pantalla bidireccional que permite:
1. **PC → Android:** Streaming de pantalla del PC al dispositivo móvil vía Sunshine
2. **Android → PC:** Captura de pantalla del celular y transmisión vía ADB + WebSocket
3. **Modo Proyección Extendido:** Uso de Virtual Display Driver para segunda pantalla virtual

---

## 1. ARQUITECTURA GENERAL

### Flujo de Datos PC → Android (Sunshine)

```
PC (Sunshine) → Red (LAN/Tailscale) → Android (Moonlight)
    ↓
Captura de pantalla (NVENC/H.264)
    ↓
Codificación de video
    ↓
Streaming UDP/TCP
    ↓
Decodificación en Android
    ↓
Renderizado en Canvas
```

### Flujo de Datos Android → PC (ADB + WebSocket)

```
Android (Screen Capture) → ADB → PC (Electron) → WebSocket → Frontend (JMuxer)
    ↓
Captura de pantalla (H.264)
    ↓
Transmisión vía ADB
    ↓
Recepción en Electron
    ↓
Reenvío por WebSocket
    ↓
Decodificación JMuxer
    ↓
Renderizado en <video>
```

---

## 2. COMPONENTES PRINCIPALES

### 2.1 Sunshine (Servidor de Streaming PC)

**Ubicación:** `C:\Program Files\Sunshine\`  
**Puerto:** 47990 (HTTPS)  
**Configuración:** `C:\Program Files\Sunshine\config\apps.json`

**Funciones:**
- Captura de pantalla del PC usando NVENC (NVIDIA) o software encoding
- Codificación H.264/H.265 con aceleración de hardware
- Streaming de video vía UDP/TCP
- Manejo de input (teclado, mouse) desde el cliente
- Integración con Virtual Display Driver para modo extendido

**Configuración de Modo Proyección:**
```javascript
// scratch_setup_projection.js
const appDefinition = {
  "name": "Modo Proyeccion (Extendido)",
  "prep-cmd": [
    {
      "do": "C:\\VirtualDisplayDriver\\MultiMonitorTool.exe /SetPrimary 2",
      "undo": "C:\\VirtualDisplayDriver\\MultiMonitorTool.exe /SetPrimary 1"
    }
  ],
  "image-path": "desktop.png"
};
```

**Puntos de Extensión:**
1. **apps.json:** Agregar nuevas aplicaciones con comandos personalizados
2. **prep-cmd:** Ejecutar scripts antes de iniciar streaming
3. **env-vars:** Variables de entorno para configuración dinámica

---

### 2.2 Moonlight Android (Cliente de Streaming)

**Ubicación:** `moonlight-android-master/`  
**Lenguaje:** Kotlin + C (JNI)  
**Protocolo:** Moonlight (basado en NVIDIA GameStream)

**Funciones:**
- Decodificación de video H.264/H.265
- Renderizado en Canvas OpenGL
- Input táctil y de teclado
- Detección de red y conexión automática
- Integración con Android Bridge para comunicación con PC

**Puntos de Extensión:**
1. **Android Bridge:** Interfaz JavaScript para comunicación bidireccional
2. **Custom Video Decoder:** Implementar decodificadores personalizados
3. **Input Injection:** Agregar nuevos tipos de input (gestos, macros)
4. **Network Detection:** Lógica personalizada de selección de red

---

### 2.3 Electron Main Process (Orquestador PC)

**Ubicación:** `electron/main.js`  
**Lenguaje:** JavaScript (Node.js)

**Funciones:**
- Gestión de WebSocket server (puerto 3002)
- Manejo de ADB para comunicación con Android
- Captura de pantalla del celular vía ADB broadcast
- Sincronización de portapapeles
- Input agent nativo (C#) para inyección de input en Windows
- Power management (prevenir sleep del PC)

**Funciones Clave:**

```javascript
// Captura de pantalla del celular
function startCapture() {
  const localIp = getLocalIP();
  adb(['shell', 'am', 'broadcast', '-a', 'com.limelight.smartdisplay.START_SCREEN_CAPTURE', 
       '--es', 'PC_IP', localIp, '--es', 'TOKEN', sessionToken])
}

// Streaming delegado a Sunshine
function startPCScreenStreaming() {
  console.log('[WS] Screen streaming delegated to Sunshine.');
}

// Input agent nativo
function compileAndStartInputAgent() {
  // Compila input_agent.cs (C#) para inyección de input nativo
}
```

**Puntos de Extensión:**
1. **WebSocket Message Handlers:** Agregar nuevos tipos de mensajes
2. **ADB Commands:** Ejecutar comandos ADB personalizados
3. **Input Agent:** Extender input_agent.cs con nuevas funcionalidades
4. **Clipboard Sync:** Agregar sincronización de otros tipos de datos
5. **Power Management:** Lógica personalizada de prevención de sleep

---

### 2.4 Next.js Frontend (Dashboard)

**Ubicación:** `src/app/page.tsx`  
**Lenguaje:** TypeScript + React

**Funciones:**
- Interfaz de usuario para control de streaming
- Integración con JMuxer para decodificación de video
- Gestión de conexión WebSocket
- Control de dispositivos y configuración
- UI de keyboard y touchpad

**Streaming de Video (JMuxer):**

```typescript
import('jmuxer').then(({ default: JMuxer }) => {
  jmuxerRef.current = new JMuxer({
    node: videoRef.current,
    mode: 'video',
    flushingTime: 10,
    fps: 60,
    debug: false,
  });
});

rws.onmessage = (event) => {
  if (!(event.data instanceof ArrayBuffer) || !jmuxerRef.current) return;
  jmuxerRef.current.feed({ video: new Uint8Array(event.data) });
};
```

**Puntos de Extensión:**
1. **JMuxer Config:** Ajustar parámetros de decodificación
2. **Custom Video Effects:** Agregar filtros o efectos de video
3. **UI Components:** Agregar nuevos controles de streaming
4. **Performance Metrics:** Agregar métricas de performance personalizadas
5. **Keyboard Macros:** Extender sistema de macros

---

### 2.5 API Routes (Backend Next.js)

**Ubicación:** `src/app/api/actions/route.ts`  
**Lenguaje:** TypeScript

**Funciones:**
- Control de dispositivos vía ADB
- Integración con Sunshine API (pairing, control)
- Gestión de screenshots y recording
- Instalación de Moonlight APK
- Ejecución de comandos personalizados

**Integración con Sunshine:**

```typescript
const SUNSHINE_HOST = process.env.SUNSHINE_HOST || 'localhost';
const SUNSHINE_PORT = parseInt(process.env.SUNSHINE_PORT || '47990', 10);
const SUNSHINE_USER = process.env.SUNSHINE_USER || 'admin';
const SUNSHINE_PASS = process.env.SUNSHINE_PASS || 'admin1234';

// Pairing con PIN
const pair = (): Promise<any> => {
  const basicAuth = 'Basic ' + Buffer.from(`${SUNSHINE_USER}:${SUNSHINE_PASS}`).toString('base64');
  // ... lógica de pairing
};
```

**Puntos de Extensión:**
1. **Custom Actions:** Agregar nuevas acciones personalizadas
2. **Sunshine API:** Extender integración con más endpoints de Sunshine
3. **ADB Commands:** Agregar comandos ADB personalizados
4. **File Operations:** Agregar operaciones de archivos personalizadas

---

## 3. FLUJO COMPLETO DE PROYECCIÓN

### Escenario 1: PC → Android (Sunshine)

```
1. Usuario inicia "Modo Proyección" en dashboard
   ↓
2. Frontend llama a /api/actions con action='start_projection'
   ↓
3. API ejecuta scratch_setup_projection.js
   ↓
4. Sunshine apps.json se actualiza con configuración de proyección
   ↓
5. Sunshine ejecuta prep-cmd (MultiMonitorTool.exe /SetPrimary 2)
   ↓
6. Sunshine inicia streaming de pantalla
   ↓
7. Moonlight Android detecta PC y se conecta
   ↓
8. Video se decodifica y renderiza en Android
   ↓
9. Input táctil se envía de vuelta a PC
```

### Escenario 2: Android → PC (ADB + WebSocket)

```
1. Usuario conecta dispositivo Android vía ADB
   ↓
2. Electron detecta dispositivo y registra en WebSocket
   ↓
3. Frontend inicia streaming WebSocket
   ↓
4. Electron envía broadcast START_SCREEN_CAPTURE al celular
   ↓
5. Android inicia captura de pantalla (H.264)
   ↓
6. Video se transmite vía ADB a PC
   ↓
7. Electron reenvía video por WebSocket
   ↓
8. Frontend decodifica con JMuxer y renderiza en <video>
```

---

## 4. PUNTOS DE EXTENSIÓN PARA AGREGAR CÓDIGO

### 4.1 Agregar Nuevo Modo de Proyección

**Archivo:** `scratch_setup_projection.js`

```javascript
// Agregar nueva configuración de proyección
const customProjection = {
  "name": "Modo Personalizado",
  "prep-cmd": [
    {
      "do": "C:\\CustomScript.exe --mode custom",
      "undo": "C:\\CustomScript.exe --cleanup"
    }
  ],
  "image-path": "custom.png"
};
```

### 4.2 Agregar Nuevo Handler de Mensajes WebSocket

**Archivo:** `electron/main.js`

```javascript
ws.on('message', async (data, isBinary) => {
  const msg = JSON.parse(data.toString());
  
  // Agregar nuevo tipo de mensaje
  if (msg.type === 'custom_action') {
    // Lógica personalizada
    await executeCustomAction(msg.params);
  }
});
```

### 4.3 Agregar Nueva Acción en API

**Archivo:** `src/app/api/actions/route.ts`

```typescript
if (action === 'custom_action') {
  // Lógica personalizada
  const result = await executeCustomLogic(body);
  return NextResponse.json({ success: true, result });
}
```

### 4.4 Extender Input Agent

**Archivo:** `electron/input_agent.cs`

```csharp
// Agregar nuevo tipo de input
if (command.StartsWith("CUSTOM")) {
  ExecuteCustomInput(command);
}
```

### 4.5 Agregar Efectos de Video Personalizados

**Archivo:** `src/app/page.tsx`

```typescript
// Extender JMuxer con efectos personalizados
rws.onmessage = (event) => {
  const videoData = new Uint8Array(event.data);
  const processedData = applyCustomEffects(videoData); // Tu código aquí
  jmuxerRef.current.feed({ video: processedData });
};
```

### 4.6 Agregar Comandos ADB Personalizados

**Archivo:** `electron/main.js`

```javascript
// Agregar nuevo comando ADB
if (msg.type === 'custom_adb') {
  await adb(['shell', 'am', 'broadcast', '-a', 'com.custom.ACTION', '--es', 'param', msg.value]);
}
```

---

## 5. SCRIPTS DE CONFIGURACIÓN

### 5.1 scratch_setup_projection.js
**Propósito:** Configura Sunshine para modo proyección extendido  
**Ejecución:** `node scratch_setup_projection.js`  
**Requiere:** Administrador (para reiniciar servicio Sunshine)

### 5.2 configurar_modo_proyeccion.bat
**Propósito:** Wrapper con elevación de privilegios  
**Ejecución:** `configurar_modo_proyeccion.bat`  
**Requiere:** Administrador (solicita UAC si es necesario)

### 5.3 scratch_start_sunshine.js
**Propósito:** Inicia Sunshine como servicio o ejecutable  
**Ejecución:** `node scratch_start_sunshine.js`

### 5.4 scratch_restart_sunshine.js
**Propósito:** Reinicia Sunshine para aplicar cambios  
**Ejecución:** `node scratch_restart_sunshine.js`

---

## 6. VARIABLES DE ENTORNO

```bash
# Sunshine
SUNSHINE_HOST=localhost
SUNSHINE_PORT=47990
SUNSHINE_USER=admin
SUNSHINE_PASS=admin1234

# ADB
ADB_PATH=C:\AndroProject\adb.exe

# Virtual Display Driver
VIRTUAL_DISPLAY_PATH=C:\VirtualDisplayDriver\
```

---

## 7. RECOMENDACIONES PARA AGREGAR CÓDIGO

### 7.1 Para Extender Proyección PC → Android
1. Modificar `scratch_setup_projection.js` para agregar nuevas configuraciones
2. Extender `apps.json` de Sunshine con comandos personalizados
3. Agregar scripts de preparación en `prep-cmd`

### 7.2 Para Extender Proyección Android → PC
1. Agregar nuevos handlers de mensajes en `electron/main.js`
2. Extender lógica de captura en Android (Moonlight)
3. Modificar JMuxer config en `src/app/page.tsx`

### 7.3 Para Agregar Funcionalidades Personalizadas
1. Crear nuevos endpoints en `src/app/api/actions/route.ts`
2. Extender input_agent.cs con nuevos comandos
3. Agregar componentes UI personalizados en `src/app/page.tsx`

---

## 8. DIAGRAMA DE ARQUITECTURA

```
┌─────────────────────────────────────────────────────────────────┐
│                        PC (Windows)                              │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Sunshine   │  │   Electron   │  │  Next.js UI  │         │
│  │  (Streaming) │  │  (Orquestador)│  │  (Dashboard) │         │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
│         │                 │                 │                  │
│         │                 │                 │                  │
│  ┌──────▼───────┐  ┌────▼───────┐  ┌────▼───────┐         │
│  │Virtual Display│  │  ADB Server │  │  WebSocket  │         │
│  │    Driver     │  │  (Puerto 5555)│  (Puerto 3002)│        │
│  └───────────────┘  └────────────┘  └────────────┘         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ LAN / Tailscale / WiFi
                              │
┌─────────────────────────────────────────────────────────────────┐
│                    Android Device                                │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Moonlight  │  │  Android     │  │  Screen      │         │
│  │  (Cliente)   │  │  Bridge     │  │  Capture     │         │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
│         │                 │                 │                  │
│         │                 │                 │                  │
│  ┌──────▼───────┐  ┌────▼───────┐  ┌────▼───────┐         │
│  │  Video       │  │  Input      │  │  Network     │         │
│  │  Decoder     │  │  Injection  │  │  Detection   │         │
│  └───────────────┘  └────────────┘  └────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

---

**Documento generado:** 18 de junio de 2026  
**Versión:** 1.0
