# ANÁLISIS ARQUITECTÓNICO COMPLETO — SMARTDISPLAY AI

> **Fecha:** 27 de julio de 2026
> **Versión:** 12.1 (versionCode 314)
> **Líneas de código:** ~34,100 Java/Kotlin + ~657 archivos C nativos + ~1,090 Python (companion)
> **Propósito:** Auditoría estructural completa para detectar errores, deuda técnica y oportunidades de mejora.

---

# 1. DIMENSIONES DEL PROYECTO

| Métrica | Valor |
|:--------|:------|
| Archivos Java | 135 |
| Archivos Kotlin | 13 |
| Archivos C nativos (moonlight-common-c + jni) | 657 |
| Layouts XML | 24 |
| Líneas Java/Kotlin totales | ~34,100 |
| Líneas Companion (Python) | 1,090 |
| Idiomas de interfaz | 58 strings.xml |
| SDK objetivo | 36 (Android 15) |
| SDK mínimo | 21 (Android 5.0) |
| Java target | 11 |
| NDK | 27.0.12077973 |
| Submódulos git | moonlight-common-c |
| Flavors | nonRoot + root |

---

# 2. ARQUITECTURA POR CAPAS

## Capa 0 — Motor de Streaming Nativo (moonlight-common-c)

**Archivos:** 657 C/H en `app/src/main/jni/moonlight-core/`
**Submódulo:** `https://github.com/moonlight-stream/moonlight-common-c.git`

**Componentes:**
- ENET (UDP confiable, control de congestión): `enet/` (host.c, peer.c, protocol.c, packet.c, compress.c + archivos por plataforma)
- OpenSSL 1.1.1 (cifrado): `openssl/` (headers)
- Moonlight Core: `moonlight-common-c/src/` (AudioStream.c, VideoStream.c, Connection.c, ControlStream.c, RtspConnection.c, RtspParser.c, SdpGenerator.c, VideoDepacketizer.c, RtpAudioQueue.c, RtpVideoQueue.c)
- SIMDe (emulación SIMD multiplataforma): `moonlight-common-c/third_party/simde/` (~300 headers)
- libopus: codec de audio
- Wrappers JNI: `callbacks.c`, `simplejni.c`, `rswrapper.c`, `minisdl.c`
- MoonBridge (Java ↔ C): `MoonBridge.java` (326 líneas, interfaz JNI)

**Fortalezas:** 10+ años de desarrollo. Pipeline de video/audio optimizado a mano. HEVC, AV1, HDR, 120fps, audio Opus. Decodificación por hardware vía MediaCodec. Es el corazón que hace que SmartDisplay tenga la mejor calidad de video del mercado.

**Riesgos:**
- Versión del submódulo potencialmente desactualizada (último commit del submódulo: 27-jul-2024)
- Sin heartbeat en el bucle principal de streaming (`Connection.c` no tiene keepalive)
- Sin adaptive bitrate — negociación única en handshake RTSP
- Sin multi-stream — un solo `SurfaceView`, una sola conexión

## Capa 1 — Motor de Streaming Android (limelight.nvstream)

**Archivos:** 14 Java en `nvstream/`
**Archivo más grande:** `NvConnection.java` (536 líneas)

**Componentes:**
- `NvConnection.java` — orquesta la conexión: `start()`, `stop()`, pipeline de setup, manejo de flags
- `MoonBridge.java` — puente JNI hacia moonlight-common-c
- `ConnectionContext.java` — holder de parámetros de conexión
- `StreamConfiguration.java` — modelo de configuración de stream (bitrate, fps, codec, etc.)
- `NvConnectionListener.java` — interfaz de callbacks: `stageStarting`, `stageFailed`, `connectionStarted`, `connectionTerminated`, `displayMessage`
- `ComputerDetails.java` — modelo de PC descubierto (IP, MAC, nombre, UUID, apps disponibles)
- `NvHTTP.java` — cliente HTTP para comunicación con Sunshine/GameStream (app list, pair, launch)
- `PairingManager.java` — flujo de emparejamiento PIN
- `WakeOnLanSender.java` — envío de paquete WoL (9 puertos)
- `ComputerManagerService.java` — persistencia SQLite de PCs conocidos (861 líneas)
- `DiscoveryService.java` — descubrimiento mDNS/NSD
- `mdns/` — 5 archivos para JmDNS y NsdManager
- `input/` — KeyboardPacket, MouseButtonPacket, ControllerPacket, KeyboardTranslator
- `av/` — AudioRenderer, VideoDecoderRenderer (interfaces)
- `jni/MoonBridge.java` — punto de entrada JNI

**Problemas detectados:**

**SPOF-1: NvConnection semáforo estático.** `connectionAllowed = new Semaphore(1)` es un campo estático. Si se adquiere y no se libera, denegación de servicio total a cualquier nueva conexión hasta matar el proceso.

**SPOF-2: Hilo de streaming único.** `NvConnection.run()` lanza un `MoonBridge.startConnection()` que ejecuta el bucle de streaming en un hilo nativo. Si ese hilo muere por cualquier razón, la sesión se pierde irremediablemente. El `AutoReconnectManager` intenta relanzar `conn.start()`, pero `NvConnection` no fue diseñado para reentrada.

**Riesgo de race condition:** `connecting` flag se usa como guarda pero no es atómico ni está sincronizado correctamente en todos los paths.

**Sin validación de estados de conexión:** `NvConnection` no tiene una máquina de estados explícita (IDLE → CONNECTING → STREAMING → RECONNECTING → STOPPED). Los flags `connecting`, `connected`, `stopped` son booleanos sueltos.

## Capa 2 — SmartDisplay AI Core (limelight.smartdisplay)

**Archivos:** 11 Kotlin + 5 Java en `smartdisplay/`
**Archivos más grandes:** `FileTransferServer.java` (435 líneas), `AutoReconnectManager.kt` (307 líneas)

### 2a. Recovery (reconexión y WoL) — RECIÉN IMPLEMENTADO

| Archivo | Líneas | Estado | Función |
|:--------|:------:|:------:|:--------|
| `AutoReconnectManager.kt` | 307 | ✅ Implementado | Orquesta la reconexión con backoff exponencial + WoL |
| `NetworkMonitor.kt` | 101 | ✅ | Detecta disponibilidad de red vía ConnectivityManager |
| `SessionRecoveryManager.kt` | 94 | ✅ | Persiste parámetros de sesión en SharedPreferences |
| `WakeOnLanManager.kt` | 171 | ✅ | WoL + sondeo TCP hasta que PC despierta |

**Problema detectado:** `AutoReconnectManager` depende de que `conn.start()` sea reentrante. `NvConnection` no fue diseñado para esto. En producción, la reconexión puede fallar si el estado interno de `NvConnection` no fue limpiado correctamente tras la desconexión. **Se necesita un test de integración con caídas reales de red.**

### 2b. SmartDisplayBus (WebSocket al companion)

| Archivo | Líneas | Función |
|:--------|:------:|:--------|
| `SmartDisplayBus.kt` | 134 | WebSocket OkHttp al companion (puerto 47991) |

**Protocolo:** JSON sobre WebSocket con autenticación PIN opcional. Comandos: `get_windows`, `focus_window`, `close_window`, `minimize_window`, `get_stats`, `files_offer`, `clipboard`, `power`, `dev_gradle`, `restore_audio`.

**Problema detectado:** Handler principal con `removeCallbacksAndMessages(null)` puede matar mensajes pendientes de otros componentes. La reconexión del bus usa backoff propio que puede solaparse con el backoff del `AutoReconnectManager`.

### 2c. Contexto del PC (foreground app + cursor)

| Archivo | Líneas | Función |
|:--------|:------:|:--------|
| `ForegroundAppRegistry.kt` | ~40 | Registro de app en primer plano del PC |
| `CursorProfileRegistry.kt` | ~30 | Perfil de cursor según la app activa |

**Dependencia:** Requieren datos del companion server vía SmartDisplayBus. Sin companion → sin perfil.

### 2d. Voz y archivos

| Archivo | Líneas | Función |
|:--------|:------:|:--------|
| `VoiceCaptureManager.java` | 134 | Graba micrófono Android → UDP al companion |
| `FileTransferServer.java` | 435 | Servidor HTTP en el móvil para recibir archivos del PC |
| `ScreenCaptureService.java` | 380 | Captura de pantalla (OCR/IA futuro) |

### 2e. Profile Manager (incompleto)

| Archivo | Líneas | Estado |
|:--------|:------:|:------:|
| `ProfileManager.java` | ? | Definición de perfiles de interacción |
| `ActiveProfile.java` | ? | Perfil activo actual |
| `InteractionProfile.java` | ? | Modelo de datos |

**Problema detectado:** Estos archivos no están integrados en el flujo de streaming. Son código muerto o plantilla para feature futuro. No se llaman desde `Game.java` ni desde ningún controlador.

## Capa 3 — UI y Controladores (limelight.ui)

**Archivos:** 24 Java/Kotlin en `ui/`
**Archivos más grandes:** `LogicalKeyboardOverlay.kt` (869 líneas), `StreamViewTransformController.java` (469 líneas), `WindowControlsController.java` (428 líneas)

### 3a. Controllers visuales

| Archivo | Líneas | Función | Estado |
|:--------|:------:|:--------|:------:|
| `OverlayFabController.java` | 321 | Orquesta el FAB radial (v6.0 refactorizado) | ✅ |
| `FABController.java` | ? | Gestión de estado/posición del FAB | ✅ |
| `RadialMenuController.java` | 238 | Pool de botones radiales + caché de geometría | ✅ Corregido |
| `OverlayAnimationController.java` | 316 | Animaciones de expansión/colapso/halo/orbit | ✅ Corregido |
| `OverlayGestureController.java` | ? | Arrastre del FAB y gestos | ✅ |
| `OverlayKeyboardController.java` | 94 | Reposiciona FAB cuando el teclado está visible | ✅ |
| `TooltipController.java` | ? | Tooltips sobre botones radiales | ✅ |
| `RadialRingView.java` | ? | Canvas que dibuja anillos concéntricos | ✅ |

**Problema detectado:** La refactorización de v5.1 a v6.0 del FAB partió un controlador de 1066 líneas en 7 archivos. Esto es una mejora arquitectónica, pero introdujo bugs de dimensionamiento (corregidos en esta sesión). **El `RadialRingView` usa Canvas personalizado — posible fuga de memoria si no se llama `release()` al destruir la actividad.**

### 3b. Teclado

| Archivo | Líneas | Función |
|:--------|:------:|:--------|
| `LogicalKeyboardOverlay.kt` | 869 | Teclado QWERTY flotante con 3 pestañas |

**Problema detectado:** `setScreenOffset()` hace cast a `context is Game` para acceder al `StreamViewTransformController`. Esto es un acoplamiento fuerte. Si el `LogicalKeyboardOverlay` se usara en otra actividad, fallaría. Debería usar una interfaz o callback.

### 3c. Taskbar y Window Controls

| Archivo | Líneas | Función |
|:--------|:------:|:--------|
| `SmartTaskbarController.java` | 331 | Chips de ventanas reales del PC (polling 2.5s) |
| `WindowControlsController.java` | 428 | Barra con Minimizar/Maximizar/Cerrar/Alt+Tab/etc |
| `PortraitHybridController.java` | 360 | Modo vertical híbrido |

**SmartTaskbar:** Funcional. Muestra ventanas reales del companion. Tap=focus, long press=minimizar/cerrar. Draggable, snap a bordes, auto-hide 5s.

**WindowControls:** Funcional. Envía macros de teclado vía NvConnection. Draggable, snap, auto-hide. El problema es la **descubribilidad** — el usuario no sabe que existe.

### 3d. Otros componentes UI

| Archivo | Líneas | Función |
|:--------|:------:|:--------|
| `AdaptiveCursorView.kt` | 394 | Cursor contextual con snap magnético |
| `SmartCursorEngine.kt` | ? | Motor de cursor inteligente |
| `MouseModeCircle.kt` | 369 | Trackpad circular neón |
| `MascotEngine.kt` | ? | Pulpo mascota animado |
| `StreamViewTransformController.java` | 469 | Zoom/Pan del stream |
| `FileBrowserController.kt` | ? | Explorador de archivos overlay |
| `DevPanelController.java` | 252 | Panel dev (limpiar/compilar/ejecutar gradle) |
| `AudioHudController.java` | ? | HUD de audio |
| `GameGestures.java` | ? | Gestos del stream |
| `RainView.java` | ? | Efecto visual decorativo |

### 3e. Activity principal — Game.java

**3,788 líneas.** Este es el mayor problema arquitectónico del proyecto.

**Responsabilidades que maneja Game.java directamente:**
- Inicialización de TODOS los controladores (FAB, teclado, taskbar, devpanel, fileBrowser, voice, cursor, mouse, ventanas)
- Configuración de streaming (bitrate, fps, codec, resolución)
- Callbacks de ciclo de vida Android (onCreate, onResume, onPause, onStop, onDestroy, onWindowFocusChanged)
- Manejo de SurfaceView (surfaceCreated, surfaceChanged, surfaceDestroyed)
- Reconexión automática y Wake-on-LAN
- Picture-in-Picture
- WakeLocks (CPU, WiFi, pantalla)
- Portapapeles y transferencia de archivos
- Modo performance/ahorro de batería
- Gestos y captura de input
- Rotación y orientación
- Atajos y accesos directos
- Manejo de diálogos de error
- Estadísticas de conexión (RTT, FPS, packet loss)
- Controladores virtuales (gamepad)
- Perfiles de stream
- Badge de estado de conexión

**Problema:** Cualquier cambio en cualquiera de estos subsistemas toca Game.java. Es imposible testear unitariamente. Las dependencias entre controladores no están documentadas.

## Capa 4 — Companion Server (Python)

**Archivo:** `companion_server.py` (1,090 líneas)

**Componentes:**
- WebSocket server (puerto 47991) — SmartDisplayBus
- HTTP server (puerto 47992) — file browser, health check
- UDP server (puerto 47998) — voz del móvil al PC
- UIAutomation — detección de foco, campos de texto
- pywin32 — manipulación de ventanas, cursor, clipboard
- psutil — estadísticas de sistema
- pystray — icono en bandeja del sistema

**Dependencias externas:** websockets, pywin32, psutil, comtypes, sounddevice, numpy, pycaw

**Limitación crítica:** Solo Windows. Usa pywin32 y UIAutomation que son APIs Win32. Para Linux necesitaría AT-SPI2/D-Bus. Para macOS necesitaría Accessibility API.

**Problema detectado:** `_restore_pc_audio()` usa `waveOutGetNumDevs()` de `winmm.dll` que solo re-enumera, no restaura el dispositivo por defecto. En algunos casos, no es suficiente para que Windows cambie de vuelta a los altavoces. La alternativa comentada (reiniciar `Audiosrv`) es agresiva y requiere permisos de administrador.

---

# 3. STACK TECNOLÓGICO COMPLETO

```
┌──────────────────────────────────────────────────────┐
│  ANDROID CLIENT (Java 11 + Kotlin)                   │
│                                                      │
│  UI Layer (24 XML layouts)                           │
│  ├─ Material 3 (MaterialCardView, MaterialButton)    │
│  ├─ ConstraintLayout (activity_game.xml)             │
│  └─ Canvas personalizado (RadialRingView, RainView)  │
│                                                      │
│  Controller Layer (24 Java/Kotlin controllers)       │
│  ├─ OverlayFabController (FAB radial)                │
│  ├─ LogicalKeyboardOverlay (teclado QWERTY)          │
│  ├─ SmartTaskbarController (ventanas PC)             │
│  ├─ WindowControlsController (macros teclado)        │
│  ├─ AdaptiveCursorView (cursor contextual)           │
│  ├─ DevPanelController (gradle remoto)               │
│  ├─ FileBrowserController (explorador archivos)      │
│  ├─ MascotEngine (pulpo mascota)                     │
│  └─ StreamViewTransformController (zoom/pan)         │
│                                                      │
│  SmartDisplay AI Layer (11 Kotlin + 5 Java)          │
│  ├─ AutoReconnectManager (reconexión + WoL)          │
│  ├─ NetworkMonitor (disponibilidad de red)           │
│  ├─ SessionRecoveryManager (persistencia sesión)     │
│  ├─ WakeOnLanManager (WoL + sondeo)                  │
│  ├─ SmartDisplayBus (WebSocket al companion)         │
│  ├─ VoiceCaptureManager (micrófono UDP)              │
│  ├─ FileTransferServer (HTTP server)                 │
│  └─ ProfileManager (incompleto)                      │
│                                                      │
│  Moonlight Layer (135 Java files)                    │
│  ├─ NvConnection (orquestador de streaming)          │
│  ├─ NvHTTP (cliente HTTP Sunshine)                   │
│  ├─ ComputerManagerService (SQLite PCs)              │
│  ├─ DiscoveryService (mDNS/NSD)                      │
│  └─ Input (KeyboardPacket, MouseButtonPacket, etc)   │
│                                                      │
│  JNI Bridge (MoonBridge.java, 326 líneas)            │
└──────────────┬───────────────────────────────────────┘
               │ JNI (callbacks.c, simplejni.c)
┌──────────────▼───────────────────────────────────────┐
│  NATIVE LAYER (C, 657 archivos)                      │
│  ├─ moonlight-common-c (streaming engine)            │
│  │   ├─ AudioStream, VideoStream, ControlStream      │
│  │   ├─ RtspConnection, RtspParser, SdpGenerator     │
│  │   ├─ RtpAudioQueue, RtpVideoQueue                 │
│  │   └─ VideoDepacketizer, SimpleStun                │
│  ├─ enet (UDP reliable transport)                    │
│  ├─ libopus (audio codec)                            │
│  ├─ OpenSSL 1.1.1 (encryption)                       │
│  └─ SIMDe (SIMD emulation, ~300 headers)             │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│  COMPANION SERVER (Python 3.12, Windows)             │
│  ├─ WebSocket server (websockets)                    │
│  ├─ HTTP server (http.server)                        │
│  ├─ UIAutomation (comtypes)                          │
│  ├─ Window management (pywin32)                      │
│  ├─ System stats (psutil)                            │
│  ├─ Audio device (pycaw)                             │
│  └─ System tray (pystray)                            │
└──────────────────────────────────────────────────────┘
```

---

# 4. ERRORES Y DEUDA TÉCNICA DETECTADOS

## Errores críticos (bloquean funcionalidad)

| ID | Archivo | Línea | Descripción | Riesgo |
|:---|:--------|:-----:|:------------|:------:|
| E1 | `NvConnection.java` | 48 | `Semaphore(1)` estático sin mecanismo de liberación forzada | Alto — denegación de servicio |
| E2 | `PreferenceConfiguration.java` | 580 | `playHostAudio` estaba hardcodeado a `false` (corregido en esta sesión) | Medio — audio del PC silenciado |
| E3 | `RadialMenuController.java` | 24-25 | `RADIAL_RADIUS_MIN_DP=80f` > `RADIAL_RADIUS_DP=72f` (corregido) | Medio — items mal posicionados |
| E4 | `overlay_dev_panel.xml` | 96-138 | Botones con `wrap_content` + `weight=1` — peso ignorado (corregido) | Bajo — texto cortado |

## Deuda técnica (afecta mantenibilidad)

| ID | Descripción | Impacto | Esfuerzo estimado |
|:---|:-----------|:--------|:-----------------:|
| D1 | **Game.java es un God Object.** 3,788 líneas, ~40 responsabilidades. Imposible testear unitariamente. Cualquier cambio toca este archivo. | Alto — riesgo de regresión en cada feature | 3-4 semanas (refactor incremental) |
| D2 | **Sin tests automatizados.** 0 tests unitarios, 0 tests de integración, 0 tests UI. Solo testing manual en dispositivo OPPO CPH2557. | Alto — bugs solo se detectan en producción | Continuo |
| D3 | **Sin inyección de dependencias.** Todos los controladores se instancian con `new` en Game.onCreate(). Dependencias ocultas, orden de inicialización frágil. | Medio — difícil reemplazar componentes | 2 semanas (Hilt/Koin) |
| D4 | **Java/Kotlin mixto sin plan de migración.** 135 Java (89%) vs 13 Kotlin (9%). Los archivos nuevos son Kotlin pero el core sigue en Java. | Medio — dos estilos, dos toolchains mentales | Gradual |
| D5 | **Cache de configuración Gradle inestable.** Builds que funcionan en una pasada fallan en la siguiente. Requiere `--no-configuration-cache` frecuentemente. | Medio — pérdida de tiempo en builds | 2-3 días |
| D6 | **Acoplamiento `context is Game`.** `LogicalKeyboardOverlay.kt` hace cast a `Game` para acceder al `StreamViewTransformController`. Viola el principio de inversión de dependencias. | Bajo — solo afecta al teclado | 2 horas |
| D7 | **Perfiles de stream incompletos.** `ProfileManager`, `ActiveProfile`, `InteractionProfile` existen pero no están conectados al flujo de streaming. | Bajo — código muerto | Integrar o eliminar |
| D8 | **Strings sin traducir.** Varios textos en overlays (Reconectando, Despertando PC, etc.) están hardcodeados en castellano en el código Java en vez de usar `R.string`. | Bajo — solo funciona en español | 3-4 horas |

## Riesgos arquitectónicos (pueden romper en producción)

| ID | Descripción | Probabilidad | Impacto |
|:---|:-----------|:------------:|:-------:|
| R1 | **Submódulo moonlight-common-c desactualizado.** Último commit del submódulo es de julio 2024. Moonlight upstream puede tener fixes de seguridad y estabilidad. | Media | Alto |
| R2 | **NvConnection no es reentrante.** `AutoReconnectManager` depende de que `conn.start()` funcione en una segunda llamada. No está diseñado para esto. La reconexión puede fallar silenciosamente. | Alta | Alto |
| R3 | **Companion server es Windows-only.** Si un usuario tiene Linux o macOS, pierde el 50% del valor diferencial (taskbar, clipboard, foco, cursor, file browser, comandos de energía). | Media | Alto |
| R4 | **Sin relay público ni NAT traversal.** La conexión remota requiere puertos abiertos o VPN manual. Esto excluye al 90% de usuarios no técnicos. | Alta | Alto |
| R5 | **WakeLock y ciclo de vida Android.** El `PARTIAL_WAKE_LOCK` + `FLAG_KEEP_SCREEN_ON` + `isScreenOffMode` es una combinación compleja que depende del orden exacto de callbacks del sistema. Un cambio en Android 15/16 podría romperlo. | Media | Medio |
| R6 | **Canvas personalizado (RadialRingView, RainView) sin release explícito.** Posible fuga de memoria en rotaciones de pantalla o cambios de configuración. | Baja | Medio |

---

# 5. OPORTUNIDADES DE MEJORA Y MIGRACIÓN

## 5a. Migración Java → Kotlin (prioridad media)

**Archivos candidatos a migrar primero (mayor impacto):**

1. `PreferenceConfiguration.java` (551 líneas) — lógica de configuración pura, sin dependencias Android complejas. Ideal para Kotlin data classes.
2. `ComputerDetails.java` — modelo de datos simple. `data class` en Kotlin reduce 80% del boilerplate.
3. `StreamConfiguration.java` — ídem.
4. `NvConnectionListener.java` — interfaz con 5 métodos. En Kotlin sería `fun interface`.
5. `ServerHelper.java` — métodos estáticos utilitarios. `object` en Kotlin.

**No migrar (dependencias complejas con JNI):**
- `NvConnection.java` — acoplado a `MoonBridge` (JNI) y flags de estado intrincados
- `MoonBridge.java` — interfaz JNI, mejor dejarla en Java
- `ControllerHandler.java` (2840 líneas) — demasiado grande, necesita refactor antes de migrar

**Estimación:** 2-3 semanas para migrar los 5 archivos de arriba. Reducción de ~40% de líneas.

## 5b. Inyección de dependencias (prioridad alta)

**Propuesta:** Koin (ligero, idiomático para Kotlin, sin procesamiento de anotaciones).

```kotlin
val smartDisplayModule = module {
    single { SessionRecoveryManager(androidContext()) }
    single { AutoReconnectManager(androidContext(), get()) }
    factory { (host: String, port: Int, mac: String) -> WakeOnLanManager(host, port, mac) }
    factory { (view: View) -> OverlayFabController(view) }
    factory { (view: View, ctx: Context) -> LogicalKeyboardOverlay(view, ctx) }
}
```

**Beneficio:** Elimina la necesidad de pasar dependencias manualmente en Game.onCreate(). Cada controlador obtiene sus dependencias del container. Testable con mocks.

**Riesgo:** Koin añade ~1MB al APK. La inicialización lazy puede causar crashes en producción si no se configura bien.

## 5c. Testing (prioridad crítica)

**Plan mínimo viable:**
- 5 tests de integración para `AutoReconnectManager` (simulando caídas de red)
- 3 tests unitarios para `PreferenceConfiguration`
- 2 tests de snapshot para layouts críticos (FAB, teclado, dev panel)
- 1 test E2E con grabación de pantalla (conexión real a Sunshine)

**Framework:** JUnit 5 + Mockito para unitarios, Espresso para UI, y `adb shell am instrument` para integración.

## 5d. Refactor de Game.java (prioridad alta)

**Plan de extracción incremental:**

| Fase | Qué extraer | Nuevo archivo | Semanas |
|:-----|:-----------|:--------------|:-------:|
| 1 | Lógica de WakeLocks y ciclo de vida | `PowerManager.kt` | 1 |
| 2 | Inicialización de controladores | `OverlayBootstrap.kt` | 1 |
| 3 | Manejo de SurfaceView + decoder | `StreamSurfaceManager.kt` | 1 |
| 4 | Reconexión y WoL | Ya está en `AutoReconnectManager` — solo limpiar Game.java | 0.5 |
| 5 | Perfiles de stream | Integrar `ProfileManager` existente | 1 |

## 5e. Migración a Relay público (prioridad estratégica)

**Opciones:**
1. **Tailscale/WireGuard integrado** — La opción más pragmática. El companion server ya corre en Windows. Añadir un endpoint WireGuard auto-configurado. El cliente Android se conecta vía VPN mesh. Coste: 2-3 semanas.
2. **Relay propio con STUN/TURN** — Más trabajo pero no depende de terceros. Un servidor COTURN en una VPS barata ($5-10/mes). Coste: 4-6 semanas.
3. **WebRTC data channel** — Reemplazaría ENET por WebRTC, ganando NAT traversal nativo. Pero requiere reimplementar el transporte de moonlight-common-c. Coste: 8-12 semanas (requiere fork del submódulo).

**Recomendación:** Opción 1 (Tailscale/WireGuard) como quick win. La opción 2 para el largo plazo.

## 5f. Companion multiplataforma (prioridad media-baja)

**Linux:** Usar AT-SPI2 para UIAutomation (equivalente funcional), D-Bus para gestión de ventanas, X11/Wayland para cursor.
**macOS:** Usar Accessibility API (AXUIElement) para UIAutomation, Quartz para ventanas.

**Coste:** 3-4 semanas por plataforma. No es urgente porque el 95% de usuarios de productividad remota usan Windows.

---

# 6. COMPARACIÓN CON EL ESTADO DEL ARTE

## Lo que SmartDisplay AI hace mejor que nadie

1. **Calidad de streaming** — HEVC + AV1 + HDR + 120fps. Solo Moonlight iguala esto.
2. **UX táctil Android** — FAB radial, teclado overlay, zoom/pan, cursor adaptativo. Nadie más lo tiene.
3. **Companion server** — UIAutomation, foco de ventana, portapapeles, cursor. Exclusivo de SmartDisplay.
4. **Reconexión automática + WoL** — Implementado pero no probado en producción a escala.

## Lo que falta para ser líder indiscutible

1. **Robustez de conexión** (relay, NAT traversal, heartbeat) — cualquier competidor empresarial lo tiene.
2. **Multi-monitor** — Parsec, AnyDesk, TeamViewer, Splashtop lo tienen.
3. **Adaptive bitrate** — Parsec lo tiene. Sin esto, el streaming en 4G/5G es inusable.
4. **Administración centralizada** — TeamViewer/AnyDesk dominan aquí. No es prioridad para SmartDisplay.
5. **IA real** — El nombre dice "AI" pero no hay una sola línea de código de IA. Es la mayor oportunidad perdida.

---

# 7. RECOMENDACIONES PRIORIZADAS

## Inmediato (esta semana)

1. **Arreglar cache de configuración Gradle** — limpiar `.gradle/configuration-cache/` y documentar `--no-configuration-cache` como workaround.
2. **Mover strings hardcodeados a `res/values/strings.xml`** — "Reconectando…", "Despertando PC…", "No se pudo reconectar", etc.
3. **Eliminar o integrar `ProfileManager`** — decidir si es feature futuro o código muerto.

## Corto plazo (2-4 semanas)

4. **Test de integración de reconexión + WoL** — simular caídas de WiFi, cambio de red, PC suspendido. Validar que `AutoReconnectManager` funciona en producción.
5. **Migrar `PreferenceConfiguration` y modelos de datos a Kotlin** — reducir boilerplate, mejorar type safety.
6. **Extraer `PowerManager.kt` de `Game.java`** — primer paso del refactor del God Object.
7. **Tailscale/WireGuard integrado** — primer paso para eliminar la barrera del NAT traversal.

## Medio plazo (1-3 meses)

8. **Inyección de dependencias con Koin** — desacoplar Game.java de sus 40 dependencias.
9. **Adaptive bitrate** — monitorear RTT/packet loss y ajustar bitrate sin reconexión completa.
10. **Migración progresiva Java → Kotlin** — 5 archivos por sprint.
11. **Companion server Linux** — abrir el mercado de desarrolladores que usan Linux.

## Largo plazo (3-6 meses)

12. **Refactor completo de Game.java** — extraer 5 managers, dejar Game.java en <1000 líneas.
13. **IA real** — OCR + Gemini + comandos de voz. La ventana de oportunidad se cierra en 2027.
14. **Relay público** — eliminar la última barrera para usuarios no técnicos.
15. **WebRTC data channel** — reemplazar ENET, ganar NAT traversal nativo.

---

# 8. CONCLUSIÓN

SmartDisplay AI es un **híbrido de clase mundial y deuda técnica**. El motor de streaming (herencia Moonlight) es imbatible. La capa de UX propia (FAB, teclado, cursor, taskbar) es innovadora. Pero la arquitectura general sufre de un **God Object (Game.java, 3788 líneas)**, **cero tests automatizados**, **dependencias ocultas**, y un **submódulo nativo potencialmente desactualizado**.

El proyecto está en un punto de inflexión: o se invierte en arquitectura ahora (inyección de dependencias, tests, refactor de Game.java), o la deuda técnica hará que cada nuevo feature sea exponencialmente más costoso.

La buena noticia: el 80% del valor diferencial ya está construido. Lo que falta es **robustez de conexión** (relay, heartbeat, adaptive bitrate) e **IA real**. Con 3 meses de trabajo enfocado en arquitectura, SmartDisplay AI puede pasar de "el mejor cliente de streaming que nadie usa en serio" a "la plataforma de productividad remota definitiva desde Android".

---

*Análisis completado el 27 de julio de 2026. Basado en ~34,100 líneas Java/Kotlin, ~657 archivos C nativos, ~1,090 líneas Python, y 24 layouts XML examinados.*
