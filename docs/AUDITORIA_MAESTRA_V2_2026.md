# 🏛️ AUDITORÍA MAESTRA SMARTDISPLAY AI v2.0

**Fecha**: 2026-07-01
**Alcance**: Proyecto completo (Android + companion_server + núcleo Moonlight), estático, sin ejecución ni modificación de código.
**Relación con auditorías previas**: Este documento **complementa** `AUDIT_ARCHITECTURE_RESILIENCE.md` / `EXECUTIVE_SUMMARY.md` (2026-06-25), que ya cubren a fondo resiliencia de conexión/streaming (heartbeat, reconexión, cambio de red). Aquí **no se repite** ese contenido — se referencia y se añade todo lo que faltaba: duplicados, calidad Kotlin/Java, arquitectura, UI/Material3, memoria, rendimiento, seguridad ampliada, comparación de producto, roadmap unificado y score.

> ⚠️ **Nota de coherencia con la auditoría previa**: el informe de 2026-06-25 listaba como *crítico* "Sin Reconexión Automática". La exploración actual encontró que **ya existen** `AutoReconnectManager.kt`, `SessionRecoveryManager.kt` y `NetworkMonitor.kt` en `smartdisplay/recovery/`, con backoff exponencial implementado en el cliente (`SmartDisplayBus.kt`). Esto indica que parte del roadmap de la auditoría anterior **ya se ejecutó** entre el 25/06 y hoy. Se recomienda re-validar manualmente qué tareas de esa auditoría siguen pendientes antes de reutilizar sus estimaciones de esfuerzo.

---

## 1. Inventario completo

### 1.1 Árbol de módulos
Proyecto Gradle **mono-módulo** (`settings.gradle` solo declara `include ':app'`). No hay Electron ni Node.js en ningún punto del repositorio.

```
SmartDisplay/Android/
├── app/                                  → módulo Android único (com.limelight)
│   ├── src/main/java/com/limelight/      → núcleo Moonlight (Java) + smartdisplay/ (Kotlin)
│   ├── src/main/jni/moonlight-core/      → submódulo git (moonlight-common-c, upstream, sin modificar)
│   ├── src/main/jni/{libopus,openssl}/   → binarios precompilados por ABI
│   ├── src/main/jni/evdev_reader/        → C, input crudo (root)
│   ├── src/root/ , src/nonRoot/          → variantes de build
│   └── src/main/res/                     → layouts XML, temas, anim/animator
├── companion_server/                     → servidor Python (asyncio + websockets), NO Electron
│   ├── companion_server.py               → single-file, 4 canales de red
│   └── companion_server.spec             → empaquetado PyInstaller
├── LuaScripts/                           → disectores Wireshark (debug de protocolo NV), sin relación con companion
├── fastlane/metadata/android/            → metadatos de store (en, de)
├── store-assets/, DISEÑO/, Saludando/    → solo assets gráficos
├── 00_ÍNDICE_MAESTRO.md, AUDIT_*.md, EXECUTIVE_SUMMARY.md, TECHNICAL_IMPLEMENTATION_GUIDE.md, RESUMEN_*.md  → auditoría previa (resiliencia)
├── MASTER_AUDIT_PROMPT.md                → rúbrica de criterios (SOLID/Clean Code/MD3) reutilizada aquí
└── Archivos sueltos de higiene pendiente: log.txt (26MB), crash*.txt, build_output*.log, patch.py, patch_xml.py
```

### 1.2 Árbol de paquetes (`app/src/main/java/com/limelight/`)
- **Núcleo Moonlight (Java, ~125 archivos)**: `binding/{audio,crypto,input,video}`, `computers`, `discovery`, `grid/assets`, `nvstream/{av,http,input,jni,mdns,wol}`, `preferences`, `ui`, `utils`.
- **Extensión SmartDisplay (Kotlin, ~11 archivos + varias clases Java nuevas)**: `smartdisplay/{bus,capture,context,files,host,profile,recents,recovery,voice}`.

### 1.3 Dependencias clave (`app/build.gradle`)
- `com.google.android.material:material:1.13.0` (M3 vigente) — usado con temas correctos pero override agresivo de estilos ("Neon").
- `kotlinx-coroutines-android:1.8.0` — **declarada pero sin un solo uso real** en el código (código muerto de dependencia).
- Submódulo nativo `moonlight-common-c` (upstream, no modificado localmente, confirmado por estructura estándar `CMakeLists.txt`/`enet/`/`nanors/`).
- Sin Dagger/Hilt/Koin → sin contenedor de DI, wiring manual.

### 1.4 Qué existe / no se usa / incompleto / duplicado (resumen)
| Estado | Elemento |
|---|---|
| **Existe pero no se usa** | `kotlinx-coroutines-android` (dependencia sin uso), `AsyncTask` (deprecado, 1 uso residual) |
| **Incompleto** | LeakCanary no configurado; `StrictMode` no configurado; sin capa MVVM (0 ViewModels/Repositories); sin layouts responsive (`layout-sw600dp`/`layout-land` no existen) |
| **Duplicado** | `LegacyDatabaseReader{,2,3}.java`; `SessionRecoveryManager`+`AutoReconnectManager`+`NetworkMonitor` (solapamiento); `PairingManager.java` (Moonlight) vs `SunshinePairHelper.java` (SmartDisplay) |
| **Higiene de repo** | Logs y crash dumps de varios MB versionados en raíz; scripts ad-hoc (`patch.py`, `patch_xml.py`) sin integrar al build |

---

## 2. Grafo de arquitectura

```
Activities                    Services                       Managers/Engines
──────────                    ────────                        ─────────────────
PcView.java  ──discovery──▶  DiscoveryService                IdentityManager
  │                          ComputerManagerService ◀─────    ComputerDatabaseManager
  ▼ (startActivity)          UsbDriverService                 PairingManager (Moonlight)
AppView.java                 ScreenCaptureService             SunshinePairHelper (SmartDisplay) ⚠ duplica pairing
  │
  ▼ (startActivity)
Game.java  ── GOD CLASS (3982 líneas, 45 imports internos) ──▶ instancia manualmente (composition root sin DI):
  ├─ ControllerHandler (input mandos)          ├─ AutoReconnectManager   ⚠ overlap
  ├─ MediaCodecDecoderRenderer (video)         ├─ SessionRecoveryManager ⚠ overlap
  ├─ AndroidAudioRenderer (audio)              ├─ NetworkMonitor         ⚠ overlap
  ├─ NvConnection / NvHTTP (protocolo)         ├─ VoiceCaptureManager
  ├─ OverlayFabController, WindowControlsController, AudioHudController, LogicalKeyboardOverlay,
  │  MouseModeCircle, AdaptiveCursorView, PortraitHybridController (7 componentes UI custom)
  ├─ FileBrowserController / FileTransferServer
  └─ ClipboardManager sync

Adapters: GenericGridAdapter → AppGridAdapter, PcGridAdapter (usan CachedAppAssetLoader/DiskAssetLoader)
```

**Violaciones de capas detectadas**:
- `Game.java` conoce y orquesta directamente casi todos los subsistemas (input, audio, video, red, UI, archivos, voz) — viola Single Responsibility y Dependency Inversion (sin abstracciones/interfaces intermedias, sin inyección).
- Dos rutas de pairing coexistentes (`PairingManager` vs `SunshinePairHelper`) sin una fachada común — riesgo de lógica de emparejamiento divergente.
- Tres clases de reconexión (`AutoReconnectManager`, `SessionRecoveryManager`, `NetworkMonitor`) sin un orquestador único documentado — riesgo de coordinación implícita frágil (posible doble intento de reconexión simultáneo).
- No se detectaron dependencias circulares directas entre paquetes, pero el acoplamiento de `Game.java` hacia 45 clases internas lo convierte de facto en un *hub* central que cualquier cambio en un subsistema puede romper.

---

## 3. Grafo de dependencias (externas)

```
app ──▶ material:1.13.0 (M3)
    ──▶ kotlinx-coroutines-android:1.8.0   ⚠ sin uso real (candidato a eliminar o adoptar)
    ──▶ moonlight-common-c (submódulo git, C, upstream)
    ──▶ libopus, openssl (binarios por ABI)
companion_server (Python) ──▶ asyncio, websockets, pywin32/comtypes, sounddevice, numpy, pystray, PyInstaller
```
Sin gestor de dependencias Python con versiones fijadas verificado más allá de `requirements.txt` (revisar pinning de versiones para reproducibilidad/seguridad de supply chain).

---

## 4. Lista de duplicados

| Archivo A | Archivo B | Similitud estimada | Propuesta de consolidación |
|---|---|---|---|
| `computers/LegacyDatabaseReader.java` | `LegacyDatabaseReader2.java` / `LegacyDatabaseReader3.java` | Alta (nombres y propósito casi idénticos) | Unificar en una sola clase con parámetro de versión de esquema; eliminar las 2 sobrantes |
| `smartdisplay/recovery/AutoReconnectManager.kt` | `SessionRecoveryManager.kt` + `NetworkMonitor.kt` | Media (responsabilidad solapada: "reaccionar a pérdida de conexión") | Definir un único `ConnectionResilienceCoordinator` que orqueste los tres, o fusionar en una máquina de estados única |
| `nvstream/http/PairingManager.java` | `smartdisplay/host/SunshinePairHelper.java` | Media-baja (dominios distintos: pairing Moonlight nativo vs pairing Sunshine automatizado con PIN fijo) | Extraer interfaz común `PairingStrategy` y que `SunshinePairHelper` la implemente en vez de coexistir como ruta paralela |
| `ui/*Dialog` legacy (`AlertDialog` en `VirtualControllerElement.java`, `FileTransferActivity.java`, `SeekBarPreference.java`, `utils/Dialog.java`, `utils/UiHelper.java`) | 1 uso de `MaterialAlertDialogBuilder` | Baja similitud de código, alta similitud de propósito | Migrar todos a `MaterialAlertDialogBuilder` para consistencia M3 |

---

## 5. Lista de bugs / riesgos

| # | Riesgo | Archivo:línea | Severidad |
|---|---|---|---|
| B1 | PIN de emparejamiento fijo `"9999"` hardcodeado | `SunshinePairHelper.java:174-181` (`FIXED_PIN`) | 🔴 Crítico |
| B2 | Trust-all TLS (acepta cualquier certificado) contra la API Sunshine | `SunshinePairHelper.java:37,151-161` | 🔴 Crítico |
| B3 | Credenciales por defecto `admin/admin1234` no forzadas a cambiar | `SunshinePairHelper.java:48-49` | 🔴 Crítico |
| B4 | 4 canales del companion (`8765`,`47991`,`48999`,`8766`) escuchan en `0.0.0.0` con auth **desactivada por defecto** (`install.ps1` nunca la activa) | `companion_server.py:106,110-122` | 🔴 Crítico |
| B5 | `ws://` sin TLS; token de auth viaja en query string en claro | `SmartDisplayBus.kt:70`, `Game.java:714`, `ScreenCaptureService.java:338`, `companion_server.py:821,847-850` | 🔴 Crítico |
| B6 | El PC descarga URLs arbitrarias enviadas por el cliente (`files_offer`) | `companion_server.py:332-346` | 🟠 Alto |
| B7 | Exploración/descarga de archivos del PC sin autenticación por defecto (`/list`,`/get`) | `companion_server.py` (canal 8766) | 🟠 Alto |
| B8 | PIN persistido en texto plano sin permisos restringidos | `companion_server.py:863-868` (`config.json`) | 🟠 Alto |
| B9 | `FileTransferServer.acceptLoop` no interrumpe el thread bloqueado en `accept()` al llamar `stop()` — posible thread colgado | `smartdisplay/files/FileTransferServer.java:95,103` | 🟠 Alto |
| B10 | Dependencia de coroutines declarada sin uso — falso sentido de "modernidad" del código | `app/build.gradle:172` | 🟡 Medio |
| B11 | `onCreate()` de `Game.java` de ~805 líneas — alto riesgo de regresión en cada cambio | `Game.java:270-1075` | 🟡 Medio |

---

## 6. Lista de memory leaks

| # | Hallazgo | Archivo:línea | Clasificación |
|---|---|---|---|
| M1 | `BitmapFactory.decodeFile` sin `recycle()` en pósters de apps/PCs (potencialmente numerosos y grandes) | `grid/assets/DiskAssetLoader.java:83,104` | 🔴 Alto (riesgo OOM real) |
| M2 | Ausencia total de `StrictMode` — sin red de seguridad contra I/O en UI thread | proyecto completo | 🔴 Alto |
| M3 | `android:largeHeap` no declarado, pese al uso de bitmaps + streaming de video | `AndroidManifest.xml` | 🔴 Alto |
| M4 | Thread de `FileTransferServer` puede quedar bloqueado en `accept()` tras `stop()` | `FileTransferServer.java:95` | 🟠 Alto |
| M5 | `placeholderBitmap` de `CachedAppAssetLoader` nunca reciclado; `shutdown()` no lo libera | `grid/assets/CachedAppAssetLoader.java:37,74`, `AppGridAdapter.java:243` | 🟡 Medio |
| M6 | `new Handler()` sin Looper explícito ni cancelación en `Game.java`, `StreamSettings.java`, `AndroidNativePointerCaptureProvider.java`, `UsbDriverService.java` | `Game.java:3280`, `StreamSettings.java:564`, `AndroidNativePointerCaptureProvider.java:101`, `UsbDriverService.java:89` | 🟡 Medio |
| M7 | `MediaCodecDecoderRenderer.rendererThread` — verificar orden de `stopping=true` vs `join()` en `release()` | `MediaCodecDecoderRenderer.java:1049` | 🟡 Medio |
| M8 | Overdraw por 3 fondos semitransparentes anidados en overlays de `activity_game.xml` (mitigado si están `GONE` la mayoría del tiempo) | `res/layout/activity_game.xml` | 🟢 Bajo |
| M9 | LeakCanary no integrado — sin detección automatizada continua | build.gradle | 🟡 Medio |

**Positivo a destacar**: `ComputerManagerService`, `VoiceCaptureManager` y `AbstractXboxController` manejan correctamente `interrupt()`/flags de parada; `connStatsHandler` y `autoHideHandler` sí cancelan sus `Handler` en `onDestroy`. No todo el proyecto tiene el mismo nivel de riesgo — los problemas se concentran en componentes nuevos (companion/files) y en patrones legacy puntuales.

---

## 7. Lista de problemas de rendimiento

- **Sin `largeHeap`** combinado con bitmaps sin `recycle()` → riesgo de `OutOfMemoryError` con listas grandes de PCs/apps (M1+M3).
- **Sin `StrictMode`** → violaciones de I/O/disco en UI thread pueden pasar desapercibidas indefinidamente (M2).
- **Un solo proceso** para toda la app (`ComputerManagerService`, `ScreenCaptureService`, `UsbDriverService`, `DiscoveryService` sin `android:process` separado) → concentra el riesgo de memoria y de que un crash de un subsistema tumbe toda la app.
- **`onCreate()` de 805 líneas en `Game.java`** → tiempo de arranque de la pantalla de streaming difícil de perfilar/optimizar por estar todo secuencial y no instrumentado por fases.
- **Positivo**: ya existe HUD de rendimiento in-app (`performance_hud_bg`, `VideoStats.java`, heurísticas en `MediaCodecHelper.java`) — base útil para medir FPS/latencia/decode real en dispositivo (no medible de forma estática en esta auditoría).
- Medición dinámica de FPS/jitter/bitrate/render time real **requiere pruebas en dispositivo** — no es posible confirmarla por análisis estático; se recomienda usar el HUD ya existente + `adb shell dumpsys gfxinfo` como siguiente paso.

---

## 8. Lista de problemas de red

*(Complementa, no repite, `AUDIT_ARCHITECTURE_RESILIENCE.md`)*

- Companion server escucha en `0.0.0.0` en sus 4 canales sin autenticación por defecto (ver B4, B7) — expuesto a toda la LAN/VPN, no solo al dispositivo emparejado.
- `ws://` sin TLS en 3 puntos del cliente Android y en el propio servidor Python (B5) — tokens y comandos (incluido control de portapapeles/archivos) viajan en claro.
- Reconexión con backoff exponencial **ya implementada en el cliente** (`SmartDisplayBus.kt:113-127`, 2s→30s, sin límite de intentos) pero **sin timeout de desconexión explícito del lado servidor** — el servidor solo emite `ping` cada 15s sin detectar clientes zombis.
- WOL y discovery mDNS/NSD del núcleo Moonlight están completos y correctamente implementados (`WakeOnLanSender.java`, `DiscoveryService.java`, `mdns/*`) — no requieren trabajo adicional.
- Companion server (8765/47991/8766) **no tiene su propio discovery** — la IP se configura manualmente o vía Tailscale, inconsistente con el discovery automático que sí tiene el streaming principal.

---

## 9. Lista de problemas de audio

> Limitación metodológica: esta auditoría es estática (lectura de código); jitter/eco/distorsión reales solo pueden confirmarse con pruebas en dispositivo. Los hallazgos aquí son de **configuración y diseño**, no de medición en vivo.

- `VoiceCaptureManager.java` (voz móvil→PC) maneja correctamente su loop de grabación con `interrupt()`/flag de parada — buena base técnica.
- El canal de voz (`UDP 48999`) transmite **PCM crudo** sin cifrado y con autenticación opcional (prefijo SHA-256 del PIN) desactivada por defecto — mismo problema de seguridad que el resto del companion (ver B4).
- No se encontró evidencia explícita en el código de **AEC (cancelación de eco), NS (supresión de ruido) o AGC (control automático de ganancia)** configurados en `AudioRecord`/`AudioTrack` — recomendar verificar si se usa `AcousticEchoCanceler`/`NoiseSuppressor`/`AutomaticGainControl` de la API de Android, o si se depende únicamente del hardware del dispositivo.
- `AndroidAudioRenderer` (playback del audio del PC) no fue auditado en profundidad de buffers/latencia en esta pasada — pendiente para una auditoría de audio dedicada con medición en dispositivo real (RTT, jitter buffer, underruns).

---

## 10. Lista de problemas UI

| # | Hallazgo | Evidencia | Severidad |
|---|---|---|---|
| U1 | Cero layouts responsive (`layout-sw600dp`, `layout-land`, `layout-large` no existen) | solo `res/layout/` único | 🟠 Alto |
| U2 | Accesibilidad tratada como parche, no como requisito: `activity_pc_view.xml` con 0 `contentDescription`; `activity_game.xml` con 1 sola en toda la pantalla | `activity_pc_view.xml`, `activity_game.xml:218` | 🟠 Alto |
| U3 | Diálogos inconsistentes: 5 archivos usan `AlertDialog` legacy vs 1 `MaterialAlertDialogBuilder` | `VirtualControllerElement.java`, `FileTransferActivity.java`, `SeekBarPreference.java`, `utils/Dialog.java`, `utils/UiHelper.java` | 🟡 Medio |
| U4 | Colores hardcodeados repetidos en overlays en vez de referenciar tokens M3 ya definidos | `#FFFFFF` ×16 en `overlay_fab_menu.xml`, ×11 en `overlay_window_controls.xml`; `#6D28D9` duplica `md_theme_primary` en `overlay_file_browser.xml` | 🟡 Medio |
| U5 | Override global agresivo de `materialCardViewStyle`/`materialButtonStyle` con estilos "Neon" custom sobre el sistema M3 estándar | `themes.xml:90-91` | 🟢 Bajo (decisión de marca, pero reduce coherencia con M3 puro) |
| U6 | `LogicalKeyboardOverlay.kt` (893 líneas) y `MouseModeCircle.kt` (407 líneas) mezclan construcción de vista + gestos + persistencia + protocolo de red (`NvConnection`) en una sola clase | `LogicalKeyboardOverlay.kt`, `MouseModeCircle.kt` | 🟡 Medio (mantenibilidad) |
| U7 | Sin Lottie ni MotionLayout; animaciones dispersas entre XML (`res/anim`, `res/animator`) y código imperativo (`ObjectAnimator` por clase, cada una con su propia duración) sin sistema centralizado de curvas/duraciones | ver sección 10 del reporte de animaciones | 🟢 Bajo |

Aspectos positivos: adopción M3 real y correcta en `themes.xml`/`colors.xml` (color roles completos, 8 variantes de tema, dark mode nativo vía `values-night/`); `AdaptiveCursorView.kt`/`SmartCursorEngine.kt` están bien separados (vista vs. motor de decisión).

---

## 11. Lista de mejoras Kotlin

1. **Adoptar coroutines de verdad** o eliminar la dependencia: hoy `kotlinx-coroutines-android` está declarada sin un solo uso; todo el threading es `new Thread()`/`AsyncTask`. Migrar los loops de red/archivos/voz (`FileTransferServer`, `VoiceCaptureManager`, `ComputerManagerService`) a `CoroutineScope` con `lifecycleScope`/`viewModelScope` daría cancelación estructurada gratis y resolvería varios de los hallazgos de la sección 6.
2. **Introducir una capa mínima de ViewModel** al menos para las pantallas con estado complejo (`Game`, `PcView`) — hoy toda la lógica vive en la Activity.
3. **Migrar `AsyncTask` residual** (deprecado desde API 30) a coroutines o `ExecutorService`.
4. Formalizar `AutoReconnectManager`/`SessionRecoveryManager`/`NetworkMonitor` como una única máquina de estados en Kotlin (con `StateFlow` del estado de conexión), en vez de tres clases coordinándose implícitamente.

---

## 12. Lista de mejoras Material 3

1. Reemplazar los 5 usos de `AlertDialog` legacy por `MaterialAlertDialogBuilder` (U3).
2. Sustituir colores hardcodeados en overlays por los tokens `@color/md_theme_*` ya definidos (U4) — el sistema de color ya existe, solo falta aplicarlo consistentemente.
3. Revisar si el override global de `materialCardViewStyle`/`materialButtonStyle` ("Neon") debe ser un tema opcional en vez de el default, para no perder la coherencia M3 en el resto de componentes.
4. Añadir `contentDescription` y tamaños táctiles ≥48dp sistemáticos en overlays de juego (U2) — con impacto directo en accesibilidad y en Play Store (política de accesibilidad).
5. Crear variantes de layout para tablet/landscape (U1) — actualmente 0% de cobertura responsive vía recursos.

---

## 13. Lista de mejoras Moonlight (núcleo)

1. **Descomponer `Game.java`** (3982 líneas, God Class confirmada) en controladores por responsabilidad: input, audio, video, red, overlays, archivos, voz — el propio código ya tiene las clases delegadas (`ControllerHandler`, `MediaCodecDecoderRenderer`, etc.), lo que falta es que `Game` deje de instanciarlas y orquestarlas todas manualmente (extraer un composition root/DI ligero).
2. Dividir `onCreate()` (805 líneas) en métodos `initInput()`, `initAudio()`, `initVideo()`, `initOverlays()`, etc.
3. Resolver los TODO/FIXME explícitos en el núcleo: `Game.java:1527,1541,2776`, `NvHTTP.java:322,389,793`, `ControllerHandler.java:104,338,1024,1489`, `MediaCodecDecoderRenderer.java:1324`, `MediaCodecHelper.java:183,766`, `ComputerManagerService.java:807`, `StreamSettings.java:44,562`.
4. Confirmar que `moonlight-common-c` (submódulo) se mantiene sincronizado con upstream — sin evidencia de fork local, lo cual es correcto (facilita recibir mejoras/fixes de seguridad upstream).
5. Unificar las dos rutas de pairing (`PairingManager` vs `SunshinePairHelper`) bajo una interfaz común.

---

## 14. Lista de mejoras "Electron" → No aplica / Companion Python

**No existe ningún componente Electron en este repositorio.** El "desktop companion" es `companion_server.py`, un servidor Python con `asyncio`/`websockets`, empaquetado con PyInstaller. Toda mejora que en el prompt original se pedía para "Electron" se traslada aquí:

1. Empaquetado ya resuelto vía PyInstaller (`companion_server.spec`) — aceptable, pero sin firma de código verificada para el `.exe` (revisar si `install.ps1` o el build firman el binario; si no, Windows SmartScreen puede marcarlo como no confiable).
2. Sin actualización automática (auto-update) del companion — cada mejora requiere reinstalación manual vía `install.ps1`.
3. `pystray` para bandeja de sistema está bien elegido para un servidor "invisible"; falta UI mínima de configuración (hoy el PIN se gestiona por flags de línea de comandos `--gen-pin`/`--set-pin`, no vía la bandeja).

---

## 15. Lista de mejoras Companion (server Python)

**Prioridad de seguridad — con impacto directo en Fase 13/Sección 5 de bugs:**

1. **Activar autenticación por defecto** en los 4 canales (8765, 47991, 48999, 8766) durante la instalación (`install.ps1` debería forzar `--gen-pin` en primer arranque), no dejarlo "opt-in" (B4).
2. **Migrar `ws://`→`wss://`** y `http://`→`https://` con certificado autofirmado generado en la instalación, o al menos cifrar el payload del token en vez de pasarlo en query string (B5).
3. **Restringir bind a la interfaz de la VPN/Tailscale** en vez de `0.0.0.0`, o añadir allowlist de IPs.
4. **Validar/sandboxear `files_offer`**: no permitir que el cliente ordene al PC descargar URLs arbitrarias sin confirmación explícita del usuario en el PC (B6).
5. **Filtrar path traversal** explícitamente en `/list` y `/get` del explorador de archivos (B7), no depender solo de `os.listdir`/`os.path.isfile`.
6. **Cifrar o restringir permisos** de `config.json` donde se persiste el PIN (B8).
7. Cerrar explícitamente el `ServerSocket` en `stop()` del lado Android (`FileTransferServer`) para no depender solo del flag `running` (B9, ver sección 6).
8. Fijar versiones en `requirements.txt` (pinning) para reproducibilidad y seguridad de supply chain.

---

## 16. Roadmap técnico

*(Prioridad por impacto × riesgo de seguridad, complementario al roadmap de resiliencia ya existente en `EXECUTIVE_SUMMARY.md`; no reordena ese roadmap, se ejecuta en paralelo)*

### 30 días — Seguridad crítica y quick wins
- Activar auth por defecto en companion (4 canales) + eliminar PIN fijo `"9999"` + quitar trust-all TLS (B1-B4).
- Cerrar `ServerSocket` correctamente en `stop()` de `FileTransferServer` (B9).
- `recycle()` de bitmaps en `DiskAssetLoader`/`CachedAppAssetLoader` + evaluar `android:largeHeap` (M1, M3).
- Añadir `StrictMode` en builds debug (M2) — coste bajo, alto valor de detección continua.
- Higiene de repo: mover logs/crash dumps/scripts ad-hoc fuera del control de versiones (`.gitignore`).

### 90 días — Cifrado de red y consolidación
- `wss://`/`https://` en companion server; token fuera de query string (B5).
- Sandboxear `files_offer` y filtrar path traversal en explorador de archivos (B6-B7).
- Consolidar duplicados: `LegacyDatabaseReader{1,2,3}`, unificar `PairingManager`/`SunshinePairHelper`, coordinar `AutoReconnectManager`/`SessionRecoveryManager`/`NetworkMonitor` en una máquina de estados.
- Migrar diálogos legacy a `MaterialAlertDialogBuilder`; reemplazar colores hardcodeados en overlays por tokens M3 (U3-U4).

### 180 días — Arquitectura y calidad
- Descomponer `Game.java` (God Class) en controladores dedicados con composition root explícito; dividir `onCreate()`.
- Adoptar coroutines reales (o eliminar la dependencia muerta) empezando por `FileTransferServer`/`VoiceCaptureManager`.
- Introducir capa mínima de ViewModel en `Game`/`PcView`.
- Añadir accesibilidad sistemática (`contentDescription`, tamaños táctiles ≥48dp) y primeros layouts responsive para tablet/landscape.
- Integrar LeakCanary en builds debug.

### 365 días — Robustez y producto
- Auto-update del companion server; firma de código del `.exe`.
- Cobertura responsive completa (tablet/landscape/TV) y auditoría de accesibilidad formal (screen readers).
- Auditoría de audio dedicada con medición en dispositivo (AEC/NS/AGC, jitter buffer real).
- Evaluar módulo de discovery para el companion server (hoy solo IP manual/Tailscale) para alinear con el discovery ya maduro del streaming principal.

---

## 17. Score general del proyecto

| Categoría | Score (0-100) | Justificación breve |
|---|---|---|
| Seguridad | **20/100** | PIN fijo, TLS trust-all, credenciales por defecto, auth opt-in desactivada por defecto en 4 canales de red, `ws://` en claro, descarga de archivos remota sin control |
| Arquitectura / SOLID | **35/100** | God Class confirmada (`Game.java`), sin DI, dos rutas de pairing, tres managers de reconexión sin coordinación única |
| Calidad Kotlin/modernización | **30/100** | Coroutines declaradas sin uso real; cero MVVM; threading manual con `Thread`/`AsyncTask` |
| Memoria y rendimiento | **50/100** | Riesgos concretos (bitmaps sin recycle, sin `largeHeap`, sin `StrictMode`) pero con buenas prácticas puntuales (threads con `interrupt()` correcto en la mayoría de casos, HUD de rendimiento ya existente) |
| UI/UX y Material 3 | **55/100** | Base M3 real y bien construida (color roles, dark mode, 8 temas) pero sin responsive, accesibilidad débil y componentes custom que mezclan capas |
| Red / resiliencia de streaming | **50/100** (mejora respecto al 45% de la auditoría previa gracias a los managers de reconexión ya implementados) | Reconexión de cliente ya existe; companion server sigue sin heartbeat/timeout robusto del lado servidor |
| Audio | **N/E** (no evaluable estáticamente) | Requiere prueba en dispositivo; base de código (`VoiceCaptureManager`) parece correcta pero sin AEC/NS/AGC confirmados |

### **Score global ponderado: 38/100**

*(Ponderación: Seguridad 25%, Arquitectura 20%, Kotlin/modernización 10%, Memoria/rendimiento 15%, UI/UX 15%, Red 15%; audio excluido del promedio por falta de datos dinámicos)*

**Lectura del score**: SmartDisplay AI tiene un **núcleo de streaming funcional y una identidad visual M3 bien pensada**, pero **no está en condición de exponerse fuera de una red de confianza total** por el estado actual de seguridad del companion server y del pairing Sunshine. La arquitectura Android acumula deuda técnica concentrada en pocos archivos muy grandes (`Game.java`, `ControllerHandler.java`), lo cual es tratable con refactors dirigidos sin tocar el núcleo Moonlight de terceros.

---

## Comparación de producto (Fase 14) — síntesis

| Frente a | SmartDisplay ya iguala/supera | Le falta |
|---|---|---|
| **Moonlight original** | UI M3 propia, cursor adaptativo, teclado lógico overlay, companion server con clipboard/voz/archivos — funcionalidades que Moonlight vanilla no tiene | Mismo nivel de seguridad y estabilidad de red que el proyecto upstream (que tampoco resuelve pairing con PIN real por UI, pero no tiene el companion expuesto) |
| **Steam Link / Parsec** | Control de mouse/teclado remoto avanzado, integración con Sunshine | Cifrado end-to-end de nivel producción, discovery automático del companion, resiliencia de red robusta en el servidor |
| **Samsung DeX** | N/A (DeX es local, SmartDisplay es remoto) | — |
| **RustDesk / AnyDesk** | Transferencia de archivos y control remoto ya presentes | Autenticación por defecto activada, TLS real (RustDesk/AnyDesk cifran por defecto) — este es el gap más relevante a cerrar para competir en confianza |

---

## Metodología y limitaciones

- Análisis 100% estático (lectura de código vía agentes de exploración), sin ejecución de la app ni del companion server.
- FPS/latencia/jitter/bitrate reales, calidad de audio (eco/distorsión) y comportamiento de UI en rotación/multitarea **no se midieron** — requieren pruebas en dispositivo/red real, fuera del alcance de esta pasada.
- El repositorio local no tiene historial git (working tree sin commits), por lo que no se pudo diferenciar código propio vs. upstream mediante `git blame`/`diff`; la clasificación núcleo-vs-capas se hizo por convención de paquetes (`com.limelight.*` vs `com.limelight.smartdisplay.*`).
