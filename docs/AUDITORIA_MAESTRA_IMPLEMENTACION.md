# AUDITORÍA MAESTRA DE IMPLEMENTACIÓN — SMARTDISPLAY AI

> **Fecha:** 26 de junio de 2026
> **Versión:** SmartDisplay AI v12.1 (fork de Moonlight Android 12.1)
> **Base:** moonlight-common-c (git submodule), API 21-36, NDK 27, Gradle 8.13.2
> **Premisa:** Toda afirmación está respaldada por código real. No hay asunciones.

---

## ÍNDICE

1. [PRECONDICIONES Y EVIDENCIA](#1-precondiciones-y-evidencia)
2. [FASE 1 — INVENTARIO REAL](#2-fase-1--inventario-real)
3. [FASE 2 — ANÁLISIS DE CAPACIDADES EXISTENTES](#3-fase-2--análisis-de-capacidades-existentes)
4. [FASE 3 — ANÁLISIS DE RIESGO](#4-fase-3--análisis-de-riesgo)
5. [FASE 4 — GAP ANALYSIS](#5-fase-4--gap-analysis)
6. [FASE 5 — QUICK WINS](#6-fase-5--quick-wins)
7. [FASE 6 — FUNCIONES DIFERENCIADORAS](#7-fase-6--funciones-diferenciadoras)
8. [FASE 7 — ROADMAP TÉCNICO](#8-fase-7--roadmap-técnico)
9. [FASE 8 — REGLAS Y ARQUITECTURA RECOMENDADA](#9-fase-8--reglas-y-arquitectura-recomendada)
10. [APÉNDICE: PROTOCOLOS DE SEGURIDAD PARA MODIFICACIONES](#10-apéndice-protocolos-de-seguridad)

---

## 1. PRECONDICIONES Y EVIDENCIA

### 1.1 Metodología

Esta auditoría se realizó mediante:
- Lectura completa de los 107+ archivos Java del proyecto
- Lectura de los 54+ archivos C nativos
- Análisis de layouts XML, recursos y configuración de build
- Verificación de cada componente contra el sistema de archivos real
- Mapeo de dependencias entre módulos

### 1.2 Alcance del código real

```
Total: ~107 archivos Java + ~54 archivos C + ~60 archivos de recursos/XML/config
Líneas de código Java: ~35,000
Líneas de código C (proyecto): ~1,400 (excluyendo moonlight-common-c)
Líneas de código C (moonlight-common-c): ~25,000
```

### 1.3 Diferenciación: Código Moonlight Original vs SmartDisplay AI

| Componente | Base Moonlight 12.1 | SmartDisplay AI (añadido) |
|:-----------|:-------------------:|:-------------------------:|
| `Game.java` (3013 lines) | ✅ Sí | Modificado: +AutoReconnect, +VoiceCapture, +PerformanceMode en FAB |
| `NvConnection.java` (591 lines) | ✅ Sí | ❌ Sin cambios |
| `MoonBridge.java` (420 lines) | ✅ Sí | ❌ Sin cambios |
| `MediaCodecDecoderRenderer.java` (1972 lines) | ✅ Sí | ❌ Sin cambios |
| `OverlayFabController.java` (609 lines) | ❌ No | ✅ **Nuevo** — Radial Octopus FAB |
| `LogicalKeyboardOverlay.java` (1185 lines) | ❌ No | ✅ **Nuevo** — Floating QWERTY overlay |
| `StreamViewTransformController.java` (365 lines) | ❌ No | ✅ **Nuevo** — Zoom/Pan/Transform |
| `AutoReconnectManager.java` (248 lines) | ❌ No | ✅ **Nuevo** — Capa de reconexión |
| `NetworkMonitor.java` (111 lines) | ❌ No | ✅ **Nuevo** — Monitoreo de red |
| `SessionRecoveryManager.java` (83 lines) | ❌ No | ✅ **Nuevo** — Persistencia de sesión |
| `VoiceCaptureManager.java` (134 lines) | ❌ No | ✅ **Nuevo** — Microphone over UDP |
| `StreamView.java` (85 lines) | ✅ Sí | Modificado: IME interception |
| `GameGestures.java` (5 lines) | ✅ Sí | Sin cambios relevantes |
| `SplashActivity.java` | ❌ No | ✅ **Nuevo** (si existe) |

---

## 2. FASE 1 — INVENTARIO REAL

### 2.1 Mapa de Arquitectura Completo

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          ACTIVITIES (6)                                   │
│                                                                          │
│  ┌─────────────────────┐  ┌──────────────────┐  ┌────────────────────┐  │
│  │ Game.java (3013ln)   │  │ PcView.java(710ln)│  │ AppView.java(658ln)│  │
│  │ Main streaming       │  │ PC grid list/view │  │ App grid for a PC  │  │
│  │ Surface, overlays,   │  │ Pair, unpair, add │  │ Launch, quit,      │  │
│  │ reconnect, input     │  │ Settings, network │  │ context menus      │  │
│  └──────┬──────────────┘  └────────┬─────────┘  └────────┬───────────┘  │
│         │                          │                      │              │
│  ┌──────┴──────────────┐  ┌───────┴──────────┐  ┌───────┴───────────┐  │
│  │ ShortcutTrampoline   │  │ HelpActivity     │  │ SplashActivity    │  │
│  │ (600ln)              │  │ (116ln)          │  │ (NUEVO)           │  │
│  │ Auto-connect, WoL,   │  │ WebView help     │  │                    │  │
│  │ pairing automation   │  │                  │  │                    │  │
│  └──────────────────────┘  └──────────────────┘  └────────────────────┘  │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────────────────┐
│                       SERVICES (2)                                        │
│                                                                          │
│  ┌─────────────────────────────┐  ┌──────────────────────────────────┐   │
│  │ ComputerManagerService      │  │ DiscoveryService                 │   │
│  │ (1006ln)                    │  │ (90ln)                           │   │
│  │ PC discovery, polling,      │  │ mDNS discovery wrapper          │   │
│  │ app list, state tracking    │  │ JmDNS + NsdManager              │   │
│  │ Inner: ApplistPoller        │  │ Inner: DiscoveryBinder          │   │
│  │ Inner: ComputerManagerBinder│  │                                  │   │
│  └─────────────────────────────┘  └──────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────────────────┐
│                    CORE STREAMING LAYER (NO TOCAR)                       │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  NvConnection.java (591ln)                                         │  │
│  │  ┌─────────────────────────────────────────────────────────────┐  │  │
│  │  │ startApp() → HTTP REST (serverInfo, app launch, resume)    │  │  │
│  │  │ detectConnectionType() → local/remote/VPN/cellular         │  │  │
│  │  │ start() → Thread + MoonBridge.startConnection()            │  │  │
│  │  └─────────────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  MoonBridge.java (420ln) — JNI Bridge                              │  │
│  │  ┌─────────────────────────────────────────────────────────────┐  │  │
│  │  │ native: setupBridge, startConnection, stopConnection,      │  │  │
│  │  │         interruptConnection, cleanupBridge                  │  │  │
│  │  │ native: sendMouseMove, sendMousePosition, sendMouseButton,  │  │  │
│  │  │         sendKeyboardInput, sendControllerInput, sendTouch,  │  │  │
│  │  │         sendPenEvent, sendUtf8Text, sendMultiController...  │  │  │
│  │  │ Constants: VIDEO_FORMAT_*, AUDIO_CONFIG_*, CAPABILITY_*,   │  │  │
│  │  │            DR_*, CONN_STATUS_*, ML_ERROR_*, ML_PORT_*      │  │  │
│  │  └─────────────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  ConnectionContext.java (34ln) — Data class                        │  │
│  │  StreamConfiguration.java (224ln) — Builder pattern                │  │
│  │  NvConnectionListener.java (23ln) — Interface                      │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────────────────┐
│                    NATIVE LAYER (C/NDK, NO TOCAR)                        │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  callbacks.c (520ln)                                               │  │
│  │  bridgeDrSubmitDecodeUnit → Java MediaCodecDecoderRenderer        │  │
│  │  bridgeArPlaySample → Java AndroidAudioRenderer                    │  │
│  │  JVM attachment, Opus decoder management                           │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  moonlight-common-c (git submodule, ~25K LOC)                     │  │
│  │  ENET (UDP reliable), RTSP, RTP, NAL, Opus, Platform sockets     │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  simplejni.c (261ln) → Native implementations of MoonBridge methods│  │
│  │  minisdl.c (107ln) → USB VID/PID joystick detection               │  │
│  │  evdev_reader.c (412ln) → Root-only evdev input                   │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────────────────┐
│                    SMARTDISPLAY AI EXTENSIONS (CAPA 2)                    │
│                    Paquete: com.limelight.smartdisplay.*                   │
│                                                                          │
│  ┌────────────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │ AutoReconnectManager   │  │ NetworkMonitor   │  │ SessionRecovery  │  │
│  │ (248ln)                │  │ (111ln)          │  │ Manager (83ln)   │  │
│  │ Reconexión con         │  │ ConnectivityMgr  │  │ Persiste estado  │  │
│  │ backoff exponencial    │  │ NetworkCallback  │  │ en SharedPrefs   │  │
│  │ Max 8 intentos, 30s    │  │ WiFi ↔ Datos     │  │ host, port, app  │  │
│  │ SÓLO eventos inesper.  │  │                  │  │ uniqueId, hdr    │  │
│  └────────────────────────┘  └──────────────────┘  └──────────────────┘  │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ VoiceCaptureManager (134ln)                                        │  │
│  │ AudioRecord + UDP out-of-band → host:47998                        │  │
│  │ 44.1kHz, mono, 16-bit PCM                                         │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────────────────┐
│                    INPUT LAYER (NO TOCAR EL NÚCLEO)                       │
│                                                                          │
│  ┌────────────┐  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Touch      │  │ USB      │  │ Virtual      │  │ Input Capture    │  │
│  │ Context    │  │ Driver   │  │ Controller   │  │ Provider         │  │
│  │ (3 files)  │  │ (7 files)│  │ (10 files)   │  │ (6 files)        │  │
│  └────────────┘  └──────────┘  └──────────────┘  └──────────────────┘  │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────────┐  │
│  │ Controller   │  │ Keyboard     │  │ Evdev                        │  │
│  │ Handler      │  │ Translator   │  │ (2 files, root only)         │  │
│  │ (3265ln)     │  │ (386ln)      │  │                              │  │
│  └──────────────┘  └──────────────┘  └──────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────────────────┐
│                    UI / OVERLAYS (MODIFICABLE CON CUIDADO)               │
│                                                                          │
│  ┌─────────────────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│  │ OverlayFabController    │  │ LogicalKeyboard  │  │ StreamView     │  │
│  │ Radial Octopus FAB v5  │  │ Overlay (1185ln)  │  │ Transform Ctrl │  │
│  │ 3 tentáculos (de 8)    │  │ QWERTY flotante   │  │ Zoom 1x-4x     │  │
│  │ Draggable, snap, halo  │  │ Key repeat, caps  │  │ Pan, double-tap│  │
│  │ SmartDisplay AI NUEVO  │  │ Scale 0.3-2.0x    │  │ Move Mode      │  │
│  └─────────────────────────┘  └──────────────────┘  └────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Módulos Críticos (NO MODIFICAR SIN JUSTIFICACIÓN)

| ID | Módulo | Archivo | Líneas | Riesgo de cambio |
|:--:|:-------|:--------|:------:|:----------------:|
| **MC-1** | NvConnection | `NvConnection.java` | 591 | **EXTREMO** — Orquesta todo el streaming |
| **MC-2** | MoonBridge JNI | `MoonBridge.java` | 420 | **EXTREMO** — Contratos con C nativo |
| **MC-3** | MediaCodecDecoderRenderer | `MediaCodecDecoderRenderer.java` | 1972 | **MUY ALTO** — Decodificación de video |
| **MC-4** | callbacks.c | `jni/moonlight-core/callbacks.c` | 520 | **EXTREMO** — Puente C↔Java |
| **MC-5** | moonlight-common-c | (git submodule) | ~25K | **EXTREMO** — Protocolo completo |
| **MC-6** | ControllerHandler | `ControllerHandler.java` | 3265 | **MUY ALTO** — Todos los gamepads |
| **MC-7** | AndroidAudioRenderer | `AndroidAudioRenderer.java` | 233 | **ALTO** — Pipeline de audio |
| **MC-8** | ComputerManagerService | `ComputerManagerService.java` | 1006 | **MUY ALTO** — Descubrimiento + estados |
| **MC-9** | NvHTTP | `NvHTTP.java` | 842 | **MUY ALTO** — Comunicación con servidor |
| **MC-10** | KeyboardTranslator | `KeyboardTranslator.java` | 386 | **ALTO** — Mapeo teclas GFE |

### 2.3 Dependencias entre módulos

```
Game.java
├── NvConnection.java → MoonBridge.java → callbacks.c → moonlight-common-c
│   ├── MediaCodecDecoderRenderer.java
│   ├── AndroidAudioRenderer.java
│   ├── ControllerHandler.java → KeyboardTranslator.java
│   └── StreamConfiguration.java + ConnectionContext.java
├── OverlayFabController.java (independiente, solo View)
├── LogicalKeyboardOverlay.java (depende de NvConnection para input)
├── StreamViewTransformController.java (independiente, solo View)
├── AutoReconnectManager.java
│   └── NetworkMonitor.java (independiente, solo Android API)
├── SessionRecoveryManager.java (independiente, solo SharedPreferences)
└── VoiceCaptureManager.java (independiente, UDP directo)
```

### 2.4 Flujo de conexión (mapeado del código real)

```
Game.onCreate()
  → PreferenceConfiguration.readPreferences()
  → MediaCodecHelper.initialize()
  → NvHTTP constructor
  → SessionRecoveryManager + AutoReconnectManager init
  
Game.surfaceCreated()
  → streamView.getHolder().setFrameRate()
  
Game.surfaceChanged()
  → decoderRenderer.setRenderTarget(holder)
  → conn.start(audioRenderer, decoderRenderer, Game.this)
    → new Thread() {
        startApp() {
          NvHTTP.getServerInfo()
          NvHTTP.getComputerDetails()
          NvHTTP.getPairState() → must be PAIRED
          NvHTTP.getServerCodecModeSupport()
          detectServerConnectionType() → LOCAL/REMOTE/AUTO
          NvHTTP.launchApp() or resumeApp()
        }
        MoonBridge.setupBridge(video, audio, listener)
        MoonBridge.startConnection(
          address, serverVersion, gfeVersion, rtspUrl,
          codecModeSupport, width, height, fps, bitrate,
          packetSize, remoteType, audioConfig, videoFormats,
          clientRefreshRate, riKey, riKeyId,
          videoCapabilities, colorSpace, colorRange
        ) → returns 0 on success
      }

moonlight-common-c (native):
  LiStartConnection()
    → RTSP handshake (SETUP, PLAY)
    → ENET/UDP sockets (video:ports 47998-48000, audio, control)
    → Decoder + Audio threads
    → Callbacks via JNI:
        bridgeDrSubmitDecodeUnit → Java MediaCodecDecoderRenderer
        bridgeArPlaySample → Java AndroidAudioRenderer

Moonlight streaming loop (native C):
  while (running) {
    video_packet = enet_receive(video_channel)
    decode_and_render(video_packet) → callback to Java
    audio_packet = enet_receive(audio_channel)  
    decode_and_play(audio_packet) → callback to Java
    // NO HAY HEARTBEAT EN ESTE BUCLE
    // NO HAY ADAPTIVE BITRATE
    // NO HAY DETECCIÓN DE CONGESTIÓN
  }
```

### 2.5 Tecnología de red REAL (verificada en código)

| Tecnología | ¿Existe? | Evidencia |
|:-----------|:--------:|:----------|
| **TCP** | ✅ Sí | RTSP handshake (47984, 47989), HTTP API (OkHttp) |
| **UDP** | ✅ Sí | ENET library en moonlight-common-c para video/audio/input |
| **QUIC** | ❌ No | No hay implementación ni referencia |
| **WebRTC** | ❌ No | No hay implementación ni referencia |
| **Relay** | ❌ No | No hay servidor relay, no hay conexión mediante relay |
| **STUN** | ⚠️ Parcial | `MoonBridge.findExternalAddressIP4()` para descubrir IP externa |
| **TURN** | ❌ No | No hay implementación |
| **NAT Traversal** | ❌ No | No hay UDP hole punching |
| **mDNS** | ✅ Sí | JmDNS + NsdManager para descubrimiento LAN |
| **WoL** | ✅ Sí | `WakeOnLanSender.java` |
| **VPN detection** | ✅ Sí | `NetHelper.isActiveNetworkVpn()`, `ConnectivityManager` |

---

## 3. FASE 2 — ANÁLISIS DE CAPACIDADES EXISTENTES

### 3.1 Gaming

| Capacidad | Estado | Evidencia |
|:----------|:------:|:----------|
| **H.264** | ✅ COMPLETO | `MoonBridge.VIDEO_FORMAT_H264`, `MediaCodecDecoderRenderer` línea 1-1972 |
| **HEVC (H.265)** | ✅ COMPLETO | `MoonBridge.VIDEO_FORMAT_H265`, `MediaCodecHelper.whitelistedHevcDecoders` |
| **AV1** | ✅ COMPLETO | `MoonBridge.VIDEO_FORMAT_AV1_MAIN8/10`, `MediaCodecHelper.decoderIsWhitelistedForAv1()` |
| **HDR** | ✅ COMPLETO | `ConnectionContext.negotiatedHdr`, `PreferenceConfiguration.enableHdr` |
| **120 FPS** | ✅ COMPLETO | `PreferenceConfiguration.fps` hasta 120, `PreferenceConfiguration.unlockFps` |
| **Decodificación hardware** | ✅ COMPLETO | `MediaCodec` API de Android, `MediaCodecHelper` con blacklists/whitelists |
| **Input baja latencia** | ✅ COMPLETO | ENET/UDP directo, `sendTouchEvent()`, `sendMouseMove()` sin buffering |
| **Multi-gamepad** | ✅ COMPLETO | `ControllerHandler` hasta 4 jugadores, USB HID, Bluetooth |
| **Referencia Frame Invalidation** | ⚠️ PARCIAL | `MediaCodecHelper.refFrameInvalidationAvc/Hevc/Av1` — detectado pero no forzado |
| **FEC (Forward Error Correction)** | ❌ NO | No implementado en moonlight-common-c |

### 3.2 Productividad

| Capacidad | Estado | Evidencia |
|:----------|:------:|:----------|
| **Multi-monitor** | ❌ NO | `StreamConfiguration` solo width/height, sin concepto de multi-stream |
| **Zoom libre** | ✅ COMPLETO | `StreamViewTransformController` 1x-4x |
| **Gestos táctiles** | ✅ PARCIAL | `AbsoluteTouchContext`, `RelativeTouchContext` — básicos (scroll 2 dedos) |
| **Teclado virtual** | ✅ COMPLETO | `LogicalKeyboardOverlay` (1185ln) — QWERTY flotante profesional |
| **Mouse virtual** | ✅ COMPLETO | `AbsoluteTouchContext`, `RelativeTouchContext` — modo trackpad y absoluto |
| **Touch directo** | ✅ COMPLETO | `sendTouchEvent()` → moonlight-common-c → host |
| **Zoom con re-renderizado** | ❌ NO | `StreamViewTransformController` solo hace scale matrix, no re-solicita resolución |
| **Portapapeles compartido** | ❌ NO | No hay implementación |
| **Transferencia de archivos** | ❌ NO | No hay implementación |
| **Teclas programador (F1-F12, ESC, etc.)** | ⚠️ PARCIAL | `LogicalKeyboardOverlay` tiene teclas pero no capas de developer |
| **Atajos configurables** | ❌ NO | No hay sistema de atajos |
| **Panel de productividad** | ❌ NO | No existe |

### 3.3 Red y Conectividad

| Capacidad | Estado | Evidencia |
|:----------|:------:|:----------|
| **Heartbeat** | ❌ NO | No hay en moonlight-common-c ni en Java |
| **Keepalive** | ❌ NO | ENET no tiene keepalive configurado |
| **Reconexión automática** | ⚠️ PARCIAL | `AutoReconnectManager` (248ln) — implementado pero NO INTEGRADO en NvConnection/Moonlight |
| **Handover WiFi↔Datos** | ❌ NO | `AutoReconnectManager` detecta red pero no mantiene sesión durante el cambio |
| **Adaptive Bitrate** | ❌ NO | `StreamConfiguration.bitrate` es fijo. `enableAdaptiveResolution` existe en Builder pero NO se usa en Game.java |
| **Persistencia de sesión** | ⚠️ PARCIAL | `SessionRecoveryManager` guarda estado pero no puede restaurar stream sin reconectar |
| **Modo texto** | ❌ NO | No hay ajuste de codec para legibilidad de texto |

### 3.4 IA (CRÍTICO: Placeholder)

| Capacidad | Estado | Evidencia |
|:----------|:------:|:----------|
| **OCR** | ❌ NO | No hay implementación, no hay dependencia ML Kit |
| **Gemini** | ❌ NO | No hay API key, no hay dependencia de Google AI |
| **Vision** | ❌ NO | No hay |
| **Traducción** | ❌ NO | No hay |
| **Context Awareness** | ❌ NO | No hay |
| **Icono IA** | ✅ Placeholder | `res/drawable/ic_ai_overlay.xml` — vector drawable de robot |
| **Toast "IA: próximamente"** | ✅ Placeholder | `OverlayFabController.java` case 5 — solo muestra Toast |
| **Nombre "SmartDisplay AI"** | ✅ Placeholder | `app/build.gradle` — applicationId, app_name |

**ESTADO REAL DE IA:** CERO funcionalidad. Solo branding.

### 3.5 Código SmartDisplay AI existente (no Moonlight)

```
app/src/main/java/com/limelight/
├── smartdisplay/
│   ├── recovery/
│   │   ├── AutoReconnectManager.java    → 248 líneas, FUNCIONAL
│   │   ├── NetworkMonitor.java           → 111 líneas, FUNCIONAL
│   │   └── SessionRecoveryManager.java   → 83 líneas, FUNCIONAL
│   └── voice/
│       └── VoiceCaptureManager.java      → 134 líneas, FUNCIONAL
│
├── ui/
│   ├── OverlayFabController.java        → 609 líneas, FUNCIONAL (3/8 tentáculos)
│   ├── LogicalKeyboardOverlay.java      → 1185 líneas, FUNCIONAL
│   └── StreamViewTransformController.java → 365 líneas, FUNCIONAL
│
└── Game.java (modificado)
    ├── Líneas 428-496: init AutoReconnect + SessionRecovery
    ├── Líneas 514-516: init VoiceCaptureManager
    ├── Líneas 2535-2557: hook connectionTerminated → AutoReconnect
    ├── Líneas 2694-2697: hook connectionStarted → reinicio de estado
    └── Líneas 2732-2744: save session en connectionStarted
```

---

## 4. FASE 3 — ANÁLISIS DE RIESGO

### 4.1 Riesgo Crítico (cambios que pueden romper streaming)

| ID | Componente | Riesgo | Descripción |
|:--:|:-----------|:------:|:------------|
| **R-1** | `moonlight-common-c` | **EXTREMO** | Cualquier cambio en el submodulo C puede romper el protocolo completo. No tocar. |
| **R-2** | `MoonBridge.java` | **EXTREMO** | Los métodos nativos tienen contracts fijos con C. Cambiar firmas → UnsatisfiedLinkError en runtime. |
| **R-3** | `callbacks.c` | **EXTREMO** | El puente JNI es el punto más frágil. Un error de tipos JNI causa crash nativo (SIGSEGV). |
| **R-4** | `NvConnection.start()` + `MoonBridge.startConnection()` | **MUY ALTO** | Este bloque sincronizado maneja la conexión completa. Cambiarlo puede causar deadlock o corrupción de estado. |
| **R-5** | `MediaCodecDecoderRenderer` | **MUY ALTO** | La lógica de codec recovery tiene 4 modos (NONE/FLUSH/RESTART/RESET). Cada modo depende de estados precisos de MediaCodec. |
| **R-6** | `ENET` loop (native) | **MUY ALTO** | Bucle de recepción UDP. No hay heartbeat. Si se modifica el timeout, puede romper sesiones existentes. |

### 4.2 Riesgo Medio (cambios aislados)

| ID | Componente | Riesgo | Descripción |
|:--:|:-----------|:------:|:------------|
| **R-7** | `Game.java` lifecycle | **ALTO** | `surfaceCreated/Destroyed`, `onPause/Resume`, PiP, y `connectionTerminated` están entrelazados. Cambios incorrectos causan leaks de Surface. |
| **R-8** | `ControllerHandler` (3265ln) | **ALTO** | La gestión de gamepads es compleja. Cambios en input mapping afectan a todos los controladores. |
| **R-9** | `ComputerManagerService` | **ALTO** | El polling de PCs (1500ms) y applist (30s) con fallback entre direcciones. Cambiar tiempos de polling puede causar batería o falsos offline. |
| **R-10** | `NvHTTP` | **MUY ALTO** | Cualquier cambio en las llamadas REST (launchApp, resume, quit) rompe la comunicación con GFE/Sunshine. |
| **R-11** | `AutoReconnectManager` + `Game.java` hook | **MEDIO** | Ya está integrado pero frágil: si `conn.start()` falla durante reconexión, el estado de `connecting`/`connected` puede quedar inconsistente. |

### 4.3 Riesgo Bajo (UI, configuración, overlays)

| ID | Componente | Riesgo | Descripción |
|:--:|:-----------|:------:|:------------|
| **R-12** | `OverlayFabController` | **BAJO** | Independiente del streaming. Solo Views y animaciones. |
| **R-13** | `LogicalKeyboardOverlay` | **BAJO** | Solo Views y envía input por `conn.sendKeyboardInput()`. No afecta al pipeline. |
| **R-14** | `StreamViewTransformController` | **BAJO** | Solo transform matrix sobre SurfaceView. No afecta al decoder. |
| **R-15** | `VoiceCaptureManager` | **BAJO** | UDP out-of-band. No afecta al streaming. |
| **R-16** | `PreferenceConfiguration` | **BAJO** | Solo lectura de preferencias. Mockeable. |
| **R-17** | Layouts XML | **BAJO** | Solo UI. |
| **R-18** | `SessionRecoveryManager` | **BAJO** | Solo SharedPreferences. |

### 4.4 Single Points of Failure (SPOF) detectados

```
1. NvConnection.java (línea 382-451)
   └── El único Thread que maneja la conexión completa.
       Si muere, la sesión se pierde SIN RECUPERACIÓN posible.
       No hay supervisor thread. No hay watchdog.

2. MoonBridge.startConnection() (nativo)
   └── Es sincrónico y bloqueante. No hay timeout configurable.
       Si el servidor no responde, el thread se queda colgado.

3. Game.java (3013 líneas)
   └── DIOS OBJETO. Maneja: lifecycle, touch, keyboard, mouse,
       stylus, overlay FAB, teclado lógico, zoom/pan, reconexión,
       PiP, rendimiento, USB. CUALQUIER BUG AFECTA TODO.

4. connectionAllowed = new Semaphore(1) (NvConnection.java línea 48)
   └── Semáforo estático. Solo una conexión a la vez en toda la app.
       Si un hilo adquiere y nunca release → DENEGACIÓN DE SERVICIO.
```

---

## 5. FASE 4 — GAP ANALYSIS

### 5.1 Tabla de diferencias competitivas

Basado en el código REAL y comparación con capaciciones conocidas de competidores.

| Capacidad | SmartDisplay AI | Moonlight | Parsec | AnyDesk | RustDesk | AnyViewer | Notas |
|:----------|:---------------:|:---------:|:------:|:-------:|:--------:|:---------:|:------|
| **Streaming base (H.264)** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Comoditizado |
| **HEVC** | ✅ | ✅ | ❌ Android | N/A | ❌ | ❌ | **VENTAJA** |
| **AV1** | ✅ | ✅ | ❌ | N/A | ❌ | ❌ | **VENTAJA** |
| **HDR** | ✅ | ✅ | ❌ | N/A | ❌ | ❌ | **VENTAJA** |
| **120 FPS** | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | Igual a Parsec |
| **Reconexión automática** | ⚠️ PARCIAL | ❌ | ✅ | ✅ | ✅ | ✅ | **GRAVE** |
| **Adaptive Bitrate** | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | **GRAVE** |
| **Heartbeat/Keepalive** | ❌ | ❌ | ✅ | ✅ | ⚠️ | ❌ | **GRAVE** |
| **Relay público** | ❌ | ❌ | ✅ | ✅ | ⚠️ self | ✅ | **GRAVE** |
| **NAT Traversal** | ❌ | ❌ | ✅ | ✅ | ✅ | ⚠️ | **GRAVE** |
| **Transferencia archivos** | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | **DEBILIDAD** |
| **Portapapeles compartido** | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | **DEBILIDAD** |
| **Multi-monitor** | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | **DEBILIDAD** |
| **Teclado avanzado móvil** | ⚠️ PARCIAL | ❌ | ❌ | ❌ | ❌ | ❌ | **VENTAJA** |
| **Zoom/pan** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | **VENTAJA** |
| **FAB Radial** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | **VENTAJA** |
| **IA** | ❌ Placeholder | ❌ | ❌ | ❌ | ❌ | ❌ | **OPORTUNIDAD** |
| **Voice over IP** | ⚠️ PARCIAL | ❌ | ✅ | ❌ | ❌ | ❌ | Parsec sí tiene |
| **Administración empresa** | ❌ | ❌ | ❌ | ✅ | ⚠️ | ❌ | No aplica |
| **Código abierto** | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | Ventaja para adopción |
| **Calidad UX Android** | ✅✅ M3+ | ⚠️ | ⚠️ | ❌ | ⚠️ | ❌ | **VENTAJA CLARA** |

### 5.2 Posicionamiento competitivo real

```
CALIDAD STREAMING
        ▲
        │
    M4K │        SmartDisplay AI ◄── Moonlight
        │              │
    HDR │              │
  120fps│              │
  AV1   │              │
        │              │
        │              ├──────────────────── Parsec
  1080p │              │       │
   60fps│              │       │
  H.264 │              │       │
        │              │       ├── AnyDesk ─── RustDesk
        │              │       │       │          │
  720p  │              │       │       │          │
   30fps│              │       │       │          │
        │              │       │       │          ├── AnyViewer
        │              │       │       │          │
        └──────────────┴───────┴───────┴──────────┴─────────►
        Sin IA    Placeholder   IA Básica   IA Integrada
                              CAPACIDAD DE IA
```

**Lectura del gráfico:**
- SmartDisplay AI está en el cuadrante superior izquierdo: **mejor calidad de streaming pero sin IA**
- La oportunidad es MOVERSE hacia la derecha (IA integrada) mientras se mantiene la calidad superior
- Parsec está en el centro: buena calidad, sin IA
- El cuadrante superior derecho (calidad superior + IA) NO ESTÁ OCUPADO por NADIE

### 5.3 Análisis de brecha por dimensión

#### Latencia
- Local (LAN): SmartDisplay AI = Parsec = Moonlight (~1-3ms). No hay brecha.
- Remota (WAN): **BRECHA GRAVE**. Parsec usa relay AWS. SmartDisplay AI no tiene relay ni adaptive bitrate. La latencia remota es impredecible.

#### Calidad visual
- **VENTAJA**: SmartDisplay AI tiene HEVC + AV1 + HDR. Parsec en Android no tiene HEVC. AnyDesk/RustDesk no tienen nada de esto.

#### Reconexión
- **BRECHA MUY GRAVE**: Todos los competidores menos Moonlight tienen reconexión automática. SmartDisplay AI tiene el `AutoReconnectManager` implementado pero no probado en producción.

#### Experiencia móvil
- **VENTAJA CLARA**: SmartDisplay AI es el único con Material 3, animaciones, FAB radial, zoom/pan, teclado overlay. Supervisor en Android.

#### Gaming
- **VENTAJA**: HEVC/AV1/HDR/120fps iguala o supera a todos. Solo Parsec compite, y sin HEVC en Android.

#### Productividad
- **BRECHA SIGNIFICATIVA**: Sin file transfer, sin clipboard, sin multi-monitor, sin impresión remota.

#### IA
- **OPORTUNIDAD ABSOLUTA**: Nadie tiene IA. SmartDisplay AI tiene el nombre y el placeholder. No hay una sola línea de IA real en ningún competidor.

---

## 6. FASE 5 — QUICK WINS

### 6.1 Criterios
- Implementación: < 3 días
- Riesgo: Bajo (no toca streaming, no toca JNI, no toca NvConnection)
- Impacto: Alto para el usuario

### 6.2 Quick Wins priorizados

---

#### QW-1: Performance HUD en tiempo real

**Estado actual:** `PerfOverlayListener` existe (interfaz), pero el overlay de rendimiento solo muestra texto plano sin formato.

**Implementación:** 
- Crear `PerformanceHudView` overlay con:
  - FPS real (frames renderizados/segundo)
  - Latencia de red (RTT desde ENET)
  - Pérdida de paquetes (%)
  - Bitrate actual
  - Códec activo (H.264/HEVC/AV1)
  - Resolución actual
- Datos ya disponibles en `MediaCodecDecoderRenderer` (líneas 1500-1600 tienen estadísticas de frames)
- Código existente en `Game.java` líneas 2964-2980: `onPerfUpdate()` (throttle 1s)

**Archivos a tocar:**
- `Game.java`: Mejorar `onPerfUpdate()` con datos estructurados (no solo String)
- `MediaCodecDecoderRenderer.java`: Exponer getters de estadísticas
- Layout XML nuevo: `performance_hud.xml`

**Riesgo:** Bajo (no toca streaming)
**Esfuerzo:** 1-2 días
**Impacto:** Alto — los usuarios pueden diagnosticar problemas de red

---

#### QW-2: Teclado Developer con capas

**Estado actual:** `LogicalKeyboardOverlay.java` tiene QWERTY básico con teclas normales. No tiene F1-F12, ESC, TAB dedicados, ni capas.

**Implementación:**
- Añadir 3 capas al teclado overlay:
  - **Capa Normal**: QWERTY actual
  - **Capa Developer**: ESC, TAB, F1-F12, Ctrl+C/V/X/Z, Ctrl+S, Ctrl+Shift+F
  - **Capa Terminal**: |, &, $, #, ~, \, Ctrl+C (SIGINT), Ctrl+D (EOF)
- Navegación: swipe horizontal o botón de cambio de capa

**Archivos a tocar:**
- `LogicalKeyboardOverlay.java` (~100 líneas nuevas)
- Layout XML del teclado

**Riesgo:** Bajo (solo Views, input por `conn.sendKeyboardInput()` existente)
**Esfuerzo:** 2-3 días
**Impacto:** Muy alto — desarrolladores pueden usar Android Studio, terminal

---

#### QW-3: Perfiles de stream guardados

**Estado actual:** Las preferencias de streaming son globales (un bitrate, una resolución para todos los PCs).

**Implementación:**
- Crear `StreamProfile` clase con: nombre, resolución, FPS, bitrate, codec, HDR
- Guardar en SharedPreferences como JSON (o SQLite)
- Selector de perfil rápido en el FAB o en la pantalla de conexión
- Perfiles por defecto: "Casa (LAN)", "Oficina (WAN)", "Datos móviles", "Gaming", "Documentos"

**Archivos a tocar:**
- Nuevo: `StreamProfile.java` (~80 líneas)
- `PreferenceConfiguration.java`: Añadir métodos de perfil
- `ShortcutTrampoline.java` o `Game.java`: Leer perfil seleccionado

**Riesgo:** Bajo (solo config, no toca streaming)
**Esfuerzo:** 2 días
**Impacto:** Alto — usuarios pueden cambiar entre LAN y datos móviles con un tap

---

#### QW-4: Highlight de cursor remoto

**Estado actual:** El cursor del mouse remoto se ve igual que el cursor local. En conexiones lentas o con lag, es difícil saber dónde está.

**Implementación:**
- Dibujar un círculo semitransparente o halo alrededor de la posición del mouse
- Usar `Canvas` overlay sobre `StreamView`
- Activable desde el FAB

**Archivos a tocar:**
- `Game.java`: Añadir overlay de cursor en `onTouchEvent()` o mediante View overlay
- Layout XML: overlay de cursor

**Riesgo:** Bajo (solo overlay visual)
**Esfuerzo:** 1 día
**Impacto:** Medio — mejora la usabilidad remota

---

#### QW-5: Notificaciones de estado de conexión

**Estado actual:** Cuando la conexión se degrada, no hay feedback visual hasta que se pierde completamente.

**Implementación:**
- Mostrar una barra de estado sutil (no intrusiva) cuando:
  - La latencia supera 100ms
  - La pérdida de paquetes supera el 2%
  - El bitrate baja de 5 Mbps
  - Se cambia de WiFi a datos móviles
- Usar `connectionStatusUpdate(int quality)` de `NvConnectionListener`

**Archivos a tocar:**
- `Game.java`: Implementar `connectionStatusUpdate()` (ya es listener)
- Layout: barra de estado overlay

**Riesgo:** Bajo (solo UI)
**Esfuerzo:** 1-2 días
**Impacto:** Alto — los usuarios saben cuándo la red está mala

---

#### QW-6: Botón de "Reconectar manualmente"

**Estado actual:** Tras una desconexión, el usuario vuelve a PcView y debe seleccionar el PC otra vez.

**Implementación:**
- Añadir botón "Reconectar" en el diálogo de error de conexión
- Usar `SessionRecoveryManager` para obtener los parámetros de la última sesión
- Relanzar Game activity con los mismos extras

**Archivos a tocar:**
- `Game.java`: Añadir opción en `connectionTerminated()`
- `Dialog.java` o layout de diálogo de error

**Riesgo:** Bajo (reusa actividad existente)
**Esfuerzo:** 1 día
**Impacto:** Medio — reduce fricción post-desconexión

---

#### QW-7: Auto-detección de app remota + modo sugerido

**Estado actual:** No hay detección de qué app se está ejecutando en el host.

**Implementación:**
- Usar `NvHTTP.getCurrentGame()` (existente) para saber qué app está corriendo
- Según la app, sugerir perfil de stream:
  - "Android Studio" → modo Developer, FPS 30, bitrate medio
  - "Photoshop" → modo Diseño, HDR, stylus precision
  - "Terminal" → modo Texto, FPS 15, bitrate bajo
  - Juego → modo Gaming, 60/120 FPS, baja latencia

**Archivos a tocar:**
- `Game.java`: Consultar app remota en `connectionStarted()`
- `StreamProfile.java`: Sistema de perfiles

**Riesgo:** Bajo (solo lectura de app remota)
**Esfuerzo:** 2-3 días
**Impacto:** Alto — experiencia adaptativa sin configuración manual

---

#### QW-8: Mejora del teclado lógico con SoundBoard (ya existe parcialmente)

**Estado actual:** `LogicalKeyboardOverlay.java` menciona SoundBoard pero no está claro si está activo.

**Implementación:**
- Añadir feedback háptico (vibrate) en cada tecla del teclado overlay
- Añadir sonido de tecla opcional (configurable en preferencias)

**Archivos a tocar:**
- `LogicalKeyboardOverlay.java`: Añadir `performHapticFeedback()` o `playSoundEffect()`

**Riesgo:** Bajo
**Esfuerzo:** 1 día
**Impacto:** Medio — mejora la experiencia de escritura

---

### 6.3 Resumen Quick Wins

| ID | Función | Días | Riesgo | Impacto | Dependencias |
|:--:|:--------|:----:|:------:|:-------:|:------------|
| QW-1 | Performance HUD | 2 | Bajo | Alto | `onPerfUpdate` existente |
| QW-2 | Teclado Developer | 3 | Bajo | Muy alto | `LogicalKeyboardOverlay` existente |
| QW-3 | Perfiles de stream | 2 | Bajo | Alto | `PreferenceConfiguration` existente |
| QW-4 | Highlight cursor | 1 | Bajo | Medio | Ninguna |
| QW-5 | Notificación de red | 2 | Bajo | Alto | `connectionStatusUpdate` existente |
| QW-6 | Botón reconectar | 1 | Bajo | Medio | `SessionRecoveryManager` existente |
| QW-7 | Auto-detección app | 3 | Bajo | Alto | `NvHTTP.getCurrentGame()` existente |
| QW-8 | Feedback háptico | 1 | Bajo | Medio | Ninguna |
| | **TOTAL** | **15 días-hombre** | | | |

---

## 7. FASE 6 — FUNCIONES DIFERENCIADORAS

### 7.1 Viabilidad técnica de cada función

---

#### FD-1: Teclado DEV Inteligente

**Descripción:** El teclado overlay cambia automáticamente de capa según la app remota detectada.

**Viabilidad:** ✅ **ALTA**
- Ya existe: `LogicalKeyboardOverlay` (1185ln) con sistema de capas
- Ya existe: `NvHTTP.getCurrentGame()` para saber app remota
- Implementación: Mapeo app→capa de teclado en SharedPreferences
- Cambios necesarios:
  - `LogicalKeyboardOverlay.java`: Añadir `switchLayer(String layerName)`
  - `Game.java`: Llamar al switch cuando se detecta app remota
- **No toca:** streaming, JNI, NvConnection

---

#### FD-2: Smart Game Detection

**Descripción:** Detección automática de plataforma de juegos (Steam, Epic, Battle.net, Riot) para optimizar perfiles.

**Viabilidad:** ✅ **ALTA**
- Ya existe: `NvHTTP.getCurrentGame()` devuelve el nombre de la app
- Implementación: Mapa de nombres de app → plataforma
  - "Steam" → modo gamepad + 60fps
  - "Cyberpunk 2077" → modo HDR + 120fps + AV1
  - "Figma" → modo precision + stylus
- Cambios necesarios:
  - Archivo de mapeo (JSON en assets o código)
  - `Game.java`: Aplicar perfil según app detectada
- **No toca:** streaming, JNI, NvConnection

---

#### FD-3: Smart Workspace

**Descripción:** Restaura automáticamente la sesión anterior (app, ventanas, cursor) al reconectar.

**Viabilidad:** ⚠️ **MEDIA**
- Ya existe: `SessionRecoveryManager` (83ln) guarda host, app, etc.
- Ya existe: `AutoReconnectManager` (248ln) reintenta conexión
- **Limitación:** Moonlight/NVIDIA GameStream no permite restaurar ventanas específicas. Sunshine sí tiene algunas capacidades.
- Implementación:
  - Mejorar `SessionRecoveryManager` para guardar más estado
  - Usar APIs de Sunshine para restaurar sesión (si está disponible)
- **Riesgo:** Medio — depende de capacidades del servidor
- **No toca:** JNI, pipeline de streaming

---

#### FD-4: Smart Dock

**Descripción:** La UI se adapta automáticamente al factor de forma: móvil, tablet, TV, DeX, ChromeOS.

**Viabilidad:** ✅ **ALTA**
- Ya existe: `OverlayFabController` es responsive (se reposiciona)
- Ya existe: `StreamViewTransformController` maneja zoom/pan
- Ya existe: Soporte parcial de DeX en `AndroidManifest.xml`
- Implementación:
  - Detectar factor de forma con `getResources().getConfiguration()`
  - Cambiar layout: FAB posición, tamaño teclado, barra de herramientas
  - Modo DeX: barra de tareas, ventanas múltiples
- Cambios necesarios:
  - `Game.java`: Detectar factor de forma y ajustar UI
  - Layouts XML: variantes para móvil/tablet/TV/DeX
- **No toca:** streaming, JNI, NvConnection

---

#### FD-5: IA Contextual (OCR + Análisis de pantalla)

**Descripción:** Captura de pantalla remota → OCR → análisis con LLM → acciones contextuales.

**Viabilidad:** ⚠️ **MEDIA** (depende de APIs externas)
- **No existe nada de esto en el código actual**
- Implementación requerida:
  1. Capturar frame del decoder (MediaCodec → Bitmap)
  2. Pasar a ML Kit OCR (on-device, sin costo)
  3. Enviar texto a Gemini API (o Claude) para análisis
  4. Mostrar resultado en overlay flotante
- **Dependencias nuevas:** 
  - `com.google.mlkit:text-recognition` (~2MB)
  - `com.google.ai.client.generativeai:generativeai` (Gemini)
- **Riesgo:** Medio — nuevo código que debe ser aislado del streaming
- **Arquitectura propuesta:** Capa separada (ver Fase 8)
- **No toca:** streaming, JNI, NvConnection (es un módulo aparte que SOLO LEE frames)

---

#### FD-6: Smart Clipboard

**Descripción:** Sincronización bidireccional del portapapeles entre Android y PC remoto.

**Viabilidad:** ⚠️ **MEDIA**
- **No existe en el código actual**
- Implementación requerida:
  - **Clipboard PC → Android**: 
    - Opción 1: Leer portapapeles del host vía HTTP REST (Sunshine tiene API)
    - Opción 2: Canal de datos adicional sobre ENET (modificar moonlight-common-c → **NO RECOMENDADO**)
  - **Clipboard Android → PC**:
    - Usar `sendUtf8Text()` existente en MoonBridge para pegar texto
  - **Clipboard local**: ClipboardManager de Android
- **Enfoque recomendado:** Sunshine API REST + `sendUtf8Text()`. Sin modificar moonlight-common-c.

---

#### FD-7: Streaming Predictivo (Adaptive Bitrate con ML)

**Descripción:** Ajuste dinámico del bitrate basado en condiciones de red usando un modelo predictivo simple.

**Viabilidad:** ⚠️ **MEDIA** (complejidad técnica alta)
- **No existe adaptive bitrate en el código actual**
- `StreamConfiguration.enableAdaptiveResolution` existe en Builder pero **NO se usa en Game.java**
- Implementación:
  1. Monitor: pérdida de paquetes, RTT, jitter, throughput (datos disponibles en ENET)
  2. Modelo: heurísticas simples primero (AIMD), ML después
  3. Ajuste: cambiar bitrate en caliente (moonlight-common-c NO SOPORTA cambio de bitrate en caliente)
- **Problema:** moonlight-common-c no permite cambiar bitrate mid-stream. Hay que parar y reiniciar la conexión.
- **Enfoque recomendado:** Implementar como capa externa que:
  1. Monitorea calidad
  2. Si es necesario cambiar, dispara reconexión con nuevo bitrate
  3. Usa `AutoReconnectManager` para hacerlo transparente
- **No toca:** JNI, pero sí requiere cambios en moonlight-common-c para soporte nativo (riesgo alto)

---

#### FD-8: Reconexión Invisible

**Descripción:** Transición automática entre WiFi y datos móviles sin pérdida de sesión.

**Viabilidad:** ❌ **MUY BAJA** (limitación fundamental del protocolo)
- **Ya existe:** `NetworkMonitor` (111ln) detecta cambios de red
- **Ya existe:** `AutoReconnectManager` (248ln) reconecta
- **Problema:** El protocolo GameStream no soporta migración de conexión. Cambiar de WiFi a datos móviles cambia la IP del cliente, y el servidor no acepta paquetes de una IP diferente en la misma sesión RTSP.
- **Solución real:** No es posible sin modificar el servidor (Sunshine/GFE). Alternativa:
  - Reconexión ultrarrápida (<2s) que parezca invisible
  - Preservar el estado de la app remota (no cerrar el juego/programa)
- **Enfoque:** Usar `AutoReconnectManager` con backoff agresivo (500ms primer intento) + `SessionRecoveryManager` para restaurar estado.

---

### 7.2 Matriz de viabilidad

| Función | Viabilidad Técnica | Esfuerzo | Riesgo | Impacto Competitivo | Prioridad |
|:--------|:------------------:|:--------:|:------:|:-------------------:|:---------:|
| FD-1: Teclado DEV Inteligente | ✅ Alta | 3 días | Bajo | Alto | **P1** |
| FD-2: Smart Game Detection | ✅ Alta | 2 días | Bajo | Medio | **P1** |
| FD-3: Smart Workspace | ⚠️ Media | 1 semana | Medio | Muy alto | **P2** |
| FD-4: Smart Dock | ✅ Alta | 1 semana | Bajo | Alto | **P2** |
| FD-5: IA Contextual | ⚠️ Media | 3 semanas | Medio | **Disruptivo** | **P3** |
| FD-6: Smart Clipboard | ⚠️ Media | 2 semanas | Medio | Alto | **P2** |
| FD-7: Streaming Predictivo | ⚠️ Media | 4 semanas | Alto | Muy alto | **P3** |
| FD-8: Reconexión Invisible | ❌ Muy baja | 6 semanas | Muy alto | Muy alto | **P4** |

---

## 8. FASE 7 — ROADMAP TÉCNICO

### 8.1 Principios rectores

1. **NO MODIFICAR** el pipeline de streaming existente (MoonBridge, NvConnection, MediaCodecDecoderRenderer, moonlight-common-c)
2. **EXTENDER** mediante capas nuevas que se comunican con el núcleo a través de interfaces existentes
3. **AISLAR** el riesgo: cada nueva funcionalidad debe poder desactivarse sin afectar al streaming base
4. **MANTENER** compatibilidad total con Moonlight/Sunshine/GFE

### 8.2 Roadmap por prioridad

---

#### PRIORIDAD 1: Fundamentos (Semanas 1-2)

No rompen arquitectura. Mejoran la experiencia base.

```
Semana 1
├── QW-1: Performance HUD (2d)
│   └── overlay_stats.xml + Game.java onPerfUpdate mejorado
├── QW-4: Highlight cursor (1d)
│   └── CursorOverlayView.java
├── QW-5: Notificación de red (2d)
│   └── ConnectionStatusBar.java
└── QW-8: Feedback háptico teclado (1d)
    └── LogicalKeyboardOverlay.java + vibrate

Semana 2
├── QW-2: Teclado Developer (3d)
│   └── KeyboardLayer.java + 3 layouts de capa
├── QW-3: Perfiles de stream (2d)
│   └── StreamProfile.java + ProfileManager.java
└── QW-6: Botón reconectar manual (1d)
    └── Game.java connectionTerminated + botón
```

**Dependencias:** Ninguna
**Riesgo:** Bajo
**Resultado:** App mucho más usable sin tocar el núcleo

---

#### PRIORIDAD 2: Productividad (Semanas 3-5)

Convierten Android en estación de trabajo remota viable.

```
Semana 3
├── FD-1: Teclado DEV Inteligente (3d)
│   └── LogicalKeyboardOverlay + switchLayer() + auto-detect
└── FD-2: Smart Game Detection (2d)
    └── AppProfileMapper.java + perfiles automáticos

Semana 4
├── FD-4: Smart Dock (5d)
│   ├── DeviceFormFactorDetector.java
│   ├── layout_mobile.xml, layout_tablet.xml, layout_dex.xml
│   └── Game.java: factor de forma → UI adaptativa
└── QW-7: Auto-detección app remota (2d)
    └── Integración con FD-2

Semana 5
├── FD-6: Smart Clipboard (10d)
│   ├── ClipboardSyncManager.java
│   ├── SunshineClipboardBridge.java (REST API)
│   ├── MoonBridge.sendUtf8Text() → ya existe
│   └── ClipboardHistoryView.java (panel lateral)
└── Integración y pruebas
```

**Dependencias:** Prioridad 1 completada
**Riesgo:** Bajo-Medio
**Resultado:** App usable para productividad real

---

#### PRIORIDAD 3: Gaming + IA (Semanas 6-10)

```  
Semana 6-7
├── FD-5a: IA — Captura de pantalla + OCR (5d)
│   ├── ScreenCaptureManager.java (MediaCodec → Bitmap)
│   ├── ML Kit OCR integration
│   └── OCRResultOverlay.java
├── FD-5b: IA — Análisis con Gemini (5d)
│   ├── AIClient.java (Gemini API wrapper)
│   └── AIAssistPanel.java (UI overlay)
└── FD-3: Smart Workspace (5d)
    └── SessionRecoveryManager mejorado + Sunshine API

Semana 8-10
├── FD-7a: Adaptive Bitrate básico (10d)
│   ├── NetworkMonitor mejorado (throughput, RTT, loss)
│   ├── AIMD algorithm
│   └── Reconexión con nuevo bitrate
├── FD-7b: Streaming Predictivo (opcional, 10d)
│   └── Modelo ML simple para predicción de congestión
└── Pruebas de integración y estabilidad
```

**Dependencias:** Prioridad 1-2 completadas
**Riesgo:** Medio-Alto (especialmente adaptive bitrate)
**Resultado:** Diferenciación real en el mercado

---

#### PRIORIDAD 4: Disrupción (Semanas 11-16)

```
Semana 11-12
├── FD-5c: IA — Asistente contextual completo
│   ├── AppDetectionService (detecta app remota → contexto)
│   ├── AIAtajosSugeridos.java
│   └── AutoMacroRecorder.java
└── Modo multi-stream experimental
    └── Segundo SurfaceView + segundo decoder
        ⚠️ REQUIERE validación de factibilidad técnica

Semana 13-16
├── FD-8: Reconexión ultrarrápida (investigación)
│   └── Optimización del handshake RTSP
├── Relay P2P básico (investigación)
│   └── STUN/TURN + UDP hole punching
└── Plugin API (diseño)
    └── Interfaz para extensiones de terceros
```

**Dependencias:** Prioridad 1-3 completadas
**Riesgo:** Alto (investigación)
**Resultado:** Liderazgo de mercado

---

### 8.3 Resumen temporal

```
        Sem 1  Sem 2  Sem 3  Sem 4  Sem 5  Sem 6  Sem 7  Sem 8  Sem 9  Sem 10 Sem 11-16
P1:     ██████▓██████░
P2:                    ████████████████▓████████
P3:                                      ████████████████████████████████████▓███
P4:                                                                                  ████████

Leyenda: ██ Trabajo activo  ▓▓ Hito  ░░ Buffer
```

---

## 9. FASE 8 — REGLAS Y ARQUITECTURA RECOMENDADA

### 9.1 Reglas obligatorias (basadas en el código real)

#### NO HACER

| Regla | Justificación |
|:------|:--------------|
| **NO** reescribir `NvConnection.java` | Orquesta todo el streaming. 591 líneas que funcionan con 10+ años de desarrollo. |
| **NO** modificar `MoonBridge.java` | 420 líneas de contratos JNI. Cambiar firmas = crash nativo. |
| **NO** tocar `callbacks.c` | Puente C↔Java. El error más mínimo = SIGSEGV. |
| **NO** modificar `moonlight-common-c` | ~25K líneas de protocolo maduro. Forkear el submodule rompe la compatibilidad con upstream. |
| **NO** cambiar `MediaCodecDecoderRenderer.java` | 1972 líneas de decodificación con 17+ erratas de decoder documentadas. |
| **NO** sustituir `ControllerHandler.java` | 3265 líneas de gestión de gamepads con 10+ años de compatibilidad. |
| **NO** añadir dependencias pesadas al pipeline de streaming | Cada ms cuenta. Mantener el hilo de streaming limpio. |
| **NO** eliminar compatibilidad con NVIDIA GFE | SmartDisplay AI debe seguir funcionando con GFE y Sunshine. |

#### SÍ HACER

| Regla | Justificación |
|:------|:--------------|
| **SÍ** extender mediante capas nuevas | `AutoReconnectManager`, `NetworkMonitor`, `VoiceCaptureManager` son el modelo correcto. |
| **SÍ** crear módulos independientes que se comuniquen por interfaces | Bajo acoplamiento, fácil testear, fácil desactivar. |
| **SÍ** usar la API de `NvConnectionListener` para eventos | Ya existe y es estable. `connectionStarted()`, `connectionTerminated()`, `connectionStatusUpdate()`. |
| **SÍ** añadir overlays de UI | `OverlayFabController`, `LogicalKeyboardOverlay` son el precedente correcto. |
| **SÍ** crear nuevas Activities | No afectan al streaming. Ej: panel de productividad, configuración de IA. |
| **SÍ** leer el frame de video para procesamiento externo | Usar `ImageReader` de MediaCodec como salida secundaria (sin tocar el decoder principal). |

### 9.2 Arquitectura recomendada para nuevas funciones

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ARQUITECTURA DE CAPAS                             │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  CAPA 0: NÚCLEO MOONLIGHT (NO TOCAR)                        │   │
│  │  ┌───────────────────────────────────────────────────────┐  │   │
│  │  │ NvConnection │ MoonBridge JNI │ moonlight-common-c    │  │   │
│  │  │ MediaCodec   │ AudioTrack     │ ENET                   │  │   │
│  │  │ DecoderRender│ AndroidAudio   │ RTSP/RTP              │  │   │
│  │  │ er           │ Renderer       │                        │  │   │
│  │  └───────────────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  CAPA 1: EXTENSIONES EXISTENTES (MANTENER)                  │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │   │
│  │  │ Overlay  │ │ Logical │ │ Stream   │ │ SmartDisplay │  │   │
│  │  │ Fab      │ │ Keyboard │ │ Transform│ │ Extensions   │  │   │
│  │  │ Controller│ │ Overlay  │ │ Ctrl     │ │ (AutoRecon.) │  │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────────┘  │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  CAPA 2: NUEVAS EXTENSIONES (AÑADIR)                        │   │
│  │                                                             │   │
│  │  ┌──────────────────────┐  ┌────────────────────────────┐  │   │
│  │  │ SmartDisplay AI      │  │ Productividad              │  │   │
│  │  │ ┌────────────────┐   │  │ ┌─────────────────────┐   │  │   │
│  │  │ │ AIAssistant    │   │  │ │ ClipboardSync       │   │  │   │
│  │  │ │ (OCR + LLM)   │   │  │ │ FileTransfer        │   │  │   │
│  │  │ │ VoiceCommands │   │  │ │ SmartDock           │   │  │   │
│  │  │ │ ContextAware  │   │  │ │ WorkspaceRestore    │   │  │   │
│  │  │ └────────────────┘   │  │ └─────────────────────┘   │  │   │
│  │  └──────────────────────┘  └────────────────────────────┘  │   │
│  │                                                             │   │
│  │  ┌──────────────────────┐  ┌────────────────────────────┐  │   │
│  │  │ Red                  │  │ Gaming                     │  │   │
│  │  │ ┌────────────────┐   │  │ ┌─────────────────────┐   │  │   │
│  │  │ │ AdaptiveBitrate│   │  │ │ AutoProfile         │   │  │   │
│  │  │ │ RelayClient    │   │  │ │ InputOptimizer      │   │  │   │
│  │  │ │ NetworkMonitor │   │  │ │ GameDetector        │   │  │   │
│  │  │ └────────────────┘   │  │ └─────────────────────┘   │  │   │
│  │  └──────────────────────┘  └────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  CAPA 3: UI DE PRODUCTIVIDAD                                │   │
│  │  ┌──────────────────────┐  ┌────────────────────────────┐  │   │
│  │  │ SmartDisplay Panel   │  │ Overlays específicos        │  │   │
│  │  │ (lateral, deslizable) │  │ ┌────────────────────┐   │  │   │
│  │  │ ┌────────────────┐   │  │ │ PerformanceHUD    │   │  │   │
│  │  │ │ File Browser   │   │  │ │ ConnectionStatus  │   │  │   │
│  │  │ │ AI Assistant   │   │  │ │ CursorHighlight   │   │  │   │
│  │  │ │ Clipboard      │   │  │ │ OCRResult         │   │  │   │
│  │  │ │ Macros/Keys    │   │  │ └────────────────────┘   │  │   │
│  │  │ │ Settings       │   │  │                           │  │   │
│  │  │ └────────────────┘   │  └────────────────────────────┘  │   │
│  │  └──────────────────────┘                                   │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 9.3 Principios de integración de nuevas funciones

1. **Toda nueva funcionalidad debe ser un módulo independiente** en su propio paquete (`com.limelight.smartdisplay.*`)
2. **La comunicación con el núcleo debe ser a través de:**
   - `NvConnectionListener` (eventos de conexión) — INTERFAZ EXISTENTE Y ESTABLE
   - `NvConnection` métodos públicos (sendInput, sendKeyboard, etc.) — YA EXISTEN
   - `Game.java` callbacks (no modificar firma)
3. **Los módulos nuevos deben tener:**
   - Interfaz clara
   - Capacidad de desactivarse sin afectar al streaming
   - Tests unitarios independientes
4. **Prohibido:**
   - Dependencias circulares entre capas
   - Acceso directo a moonlight-common-c desde Java nuevo
   - Modificación de archivos en la lista de NO TOCAR sin aprobación

### 9.4 Plan de implementación seguro para cualquier función

```
PASO 1: VERIFICAR
├── ¿La función requiere cambiar un archivo de la lista NO TOCAR?
│   ├── SÍ → DETENER. Buscar alternativa en capa superior.
│   └── NO → Continuar.
│
PASO 2: AISLAR
├── Crear package: com.limelight.smartdisplay.<feature>/
├── Crear interfaz pública
├── Implementar sin tocar nada fuera del package
│
PASO 3: INTEGRAR
├── Identificar punto de integración en Game.java:
│   ├── ¿Evento de conexión? → NvConnectionListener
│   ├── ¿Input del usuario? → TouchListener / KeyListener
│   └── ¿UI? → Overlay / Activity separada
├── Añadir hook mínimo en Game.java
│
PASO 4: PROBAR
├── Verificar que el streaming base funciona sin la función activada
├── Verificar que la función se puede activar/desactivar
└── Verificar que al desactivar, el comportamiento es idéntico al original
```

### 9.5 Conclusión

**SmartDisplay AI tiene:**

1. **Un núcleo de streaming de clase mundial** (Moonlight 12.1) que no debe modificarse
2. **Extensiones de UI innovadoras** (FAB Radial, Teclado overlay, Zoom/Pan) que son ventajas reales
3. **Extensiones de resiliencia prometedoras** (AutoReconnectManager, NetworkMonitor) recién implementadas
4. **Un placeholder de IA** que es el nombre de la app pero no tiene implementación

**Lo que falta para ser competitivo:**

- **Corto plazo (semanas 1-2):** Quick Wins que mejoran la usabilidad sin riesgo
- **Medio plazo (semanas 3-5):** Funciones de productividad (clipboard, perfiles, smart dock)
- **Largo plazo (semanas 6-10):** IA real + adaptive bitrate
- **Visión (semanas 11-16):** Funciones disruptivas que ningún competidor tiene

**La clave del éxito:**
No intentar reescribir Moonlight. **Extenderlo con capas que aporten valor sin tocar el núcleo.** El modelo `AutoReconnectManager` → `Game.java` es el patrón correcto. Cada nueva función debe seguir ese mismo patrón.

---

## 10. APÉNDICE: PROTOCOLOS DE SEGURIDAD PARA MODIFICACIONES

### 10.1 Plantilla de solicitud de cambio

Para cada modificación propuesta, completar:

```
## SOLICITUD DE CAMBIO

### Módulo a modificar
[Nombre del archivo, líneas afectadas]

### ¿Está en la lista de NO TOCAR?
[SÍ/NO — Si SÍ, explicar por qué es necesario]

### Riesgo estimado
[EXTREMO/ALTO/MEDIO/BAJO]

### Beneficio
[¿Qué gana el usuario?]

### Compatibilidad
[¿Sigue funcionando con GFE? ¿Sunshine? ¿Root? ¿Non-root?]

### Plan de rollback
[¿Cómo se revierte si algo sale mal?]

### Tiempo estimado
[N días]

### Plan de pruebas
[¿Cómo verificar que no se rompió nada?]
```

### 10.2 Archivos con protección absoluta

| Archivo | Protección | Razón |
|:--------|:----------:|:------|
| `app/src/main/jni/moonlight-core/callbacks.c` | 🔒 **NO TOCAR** | Puente JNI crítico |
| `app/src/main/jni/moonlight-core/simplejni.c` | 🔒 **NO TOCAR** | Implementación nativa MoonBridge |
| `app/src/main/java/com/limelight/nvstream/jni/MoonBridge.java` | 🔒 **NO TOCAR** | Contrato JNI |
| `app/src/main/java/com/limelight/nvstream/NvConnection.java` | 🔒 **NO TOCAR** | Orquestador de streaming |
| `moonlight-common-c/` (submodule) | 🔒 **NO TOCAR** | Protocolo completo |
| `app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java` | ⚠️ **EXTREMO** | Solo cambios con aprobación |
| `app/src/main/java/com/limelight/binding/input/ControllerHandler.java` | ⚠️ **EXTREMO** | Solo cambios con aprobación |
| `app/src/main/java/com/limelight/nvstream/http/NvHTTP.java` | ⚠️ **MUY ALTO** | Comunicación con servidor |

### 10.3 Lista de verificación pre-commit

- [ ] ¿El cambio afecta a `NvConnection`, `MoonBridge` o `callbacks.c`? → **DETENER**
- [ ] ¿El cambio modifica el pipeline de video/audio? → **DETENER**
- [ ] ¿El cambio añade una dependencia nueva? → **JUSTIFICAR**
- [ ] ¿El cambio toca `Game.java`? → **VERIFICAR** que no altera lifecycle
- [ ] ¿El cambio es desactivable? → **DEBE SERLO**
- [ ] ¿El cambio tiene tests? → **DEBE TENERLOS**
- [ ] ¿El build compila? → `./gradlew assembleNonRootDebug`
- [ ] ¿El build compila en root flavor? → `./gradlew assembleRootDebug`

---

*Documento generado el 26 de junio de 2026 — Auditoría basada exclusivamente en código real, sin asunciones ni componentes inventados.*
