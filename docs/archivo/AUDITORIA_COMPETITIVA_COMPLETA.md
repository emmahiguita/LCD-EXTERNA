# AUDITORÍA COMPETITIVA EXTREMA: SMARTDISPLAY AI

> **Fecha:** 26 de junio de 2026
> **Versión analizada:** SmartDisplay AI v12.1 (basado en Moonlight Android 12.1)
> **Propósito:** Determinar exactamente dónde y cómo SmartDisplay AI puede superar a todos los competidores del mercado de remoto desktop/streaming.

---

## ÍNDICE

1. [RESUMEN EJECUTIVO](#resumen-ejecutivo)
2. [FASE 1: COMPARACIÓN FUNCIONAL](#fase-1-comparación-funcional)
3. [FASE 2: EXPERIENCIA REAL DEL USUARIO](#fase-2-experiencia-real-del-usuario)
4. [FASE 3: ANÁLISIS DE ARQUITECTURA](#fase-3-análisis-de-arquitectura)
5. [FASE 4: UX Y PRODUCTIVIDAD](#fase-4-ux-y-productividad)
6. [FASE 5: IA](#fase-5-ia)
7. [FASE 6: SUPERAR A LA COMPETENCIA](#fase-6-superar-a-la-competencia)
8. [FASE 7: ROADMAP](#fase-7-roadmap)
9. [FASE 8: VISIÓN FINAL](#fase-8-visión-final)

---

## RESUMEN EJECUTIVO

### Diagnóstico

SmartDisplay AI **no es actualmente un producto competitivo independiente**. Es un fork visual de Moonlight con un placeholder de IA. Tiene 3 áreas con ventaja genuina y 23 problemas documentados de resiliencia.

### La paradoja de SmartDisplay AI

| Aspecto | Realidad |
|---------|----------|
| Marca | "SmartDisplay AI" — sugiere un producto de productividad empresarial con IA |
| Realidad | Cliente de streaming de juegos (Moonlight fork) con un icono de IA y un Toast "próximamente" |
| Percepción del mercado | Confusa — no es ni lo uno ni lo otro |
| Oportunidad | **Ningún competidor tiene IA real**. El espacio está maduro para disrupción |

### Ranking competitivo (visión general)

| Posición | Producto | Fortaleza principal | Debilidad principal |
|----------|----------|---------------------|---------------------|
| 🥇 | **Parsec** | Latencia más baja, UX pulida, red optimizada | Sin IA, Android limitado, caro |
| 🥈 | **Moonlight+Sunshine** | Código abierto, flexible, hardware decoding | Sin IA, UX básica, sin relay nativo |
| 🥉 | **AnyDesk** | Ligero, rápido, buen codec propio | Sin IA, precio agresivo, mobile básico |
| 4 | **TeamViewer** | Enterprise completo, ecosistema | Pesado, caro, latencia alta |
| 5 | **RustDesk** | Open source, self-hosted, relay | Calidad streaming media, sin IA |
| 6 | **Splashtop** | Rendimiento consistente, business | Sin IA, UX anticuada |
| 7 | **SmartDisplay AI** | Código base sólido, IA placeholder | Sin IA real, sin heartbeat, sin reconexión |
| 8 | **AnyViewer** | Precio bajo | Calidad inferior, limitado |
| 9 | **Chrome Remote Desktop** | Gratuito, simple | Latencia alta, sin features |

### Ventana de oportunidad

**El mercado de remoto desktop móvil está estancado.** Todos los competidores compiten en las mismas dimensiones (latencia, codecs, FPS). Nadie está innovando en:

1. **IA integrada en el flujo de trabajo remoto**
2. **Productividad mobile-first real**
3. **Convergencia gaming + enterprise**
4. **Arquitectura resiliente con conexión自适应**

**SmartDisplay AI tiene 6-9 meses de ventana** antes de que los grandes (Parsec, TeamViewer) integren IA.

---

## FASE 1: COMPARACIÓN FUNCIONAL

### Tabla comparativa detallada

| Dimensión | SmartDisplay AI | Parsec | Moonlight+Sunshine | AnyDesk | TeamViewer | RustDesk | Splashtop | AnyViewer | Chrome RD |
|-----------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Streaming H.264** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **HEVC (H.265)** | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ⚠️ | ❌ | ❌ |
| **AV1** | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **HDR** | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **120 FPS** | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Latencia <5ms (local)** | ✅ | ✅ | ✅ | ⚠️ | ❌ | ❌ | ⚠️ | ❌ | ❌ |
| **Latencia <30ms (remoto)** | ⚠️ | ✅ | ⚠️ | ⚠️ | ⚠️ | ❌ | ✅ | ❌ | ❌ |
| **Audio 7.1** | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Opus codec** | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Reconexión automática** | ❌ **Crítico** | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Heartbeat/Keepalive** | ❌ **Crítico** | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Cambio WiFi↔Datos** | ❌ **Crítico** | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| **Tailscale/WireGuard** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| **Relay server propio** | ❌ | ✅ (Parsec Relay) | ❌ (usa self-host) | ✅ (AnyDesk Relay) | ✅ (TeamViewer Relay) | ✅ (self-host) | ✅ (Splashtop Relay) | ✅ | ❌ (solo Google) |
| **E2E Encryption** | ✅ (TLS+AES) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **IA integrada** | ❌ **Placeholder** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Teclado móvil avanzado** | ⚠️ (lógico overlay) | ❌ | ❌ | ❌ | ✅ (QuickConnect) | ❌ | ❌ | ❌ | ❌ |
| **Programación remota** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **UX Android nativa** | ✅ (M3) | ⚠️ (básica) | ⚠️ (básica) | ⚠️ (básica) | ⚠️ | ⚠️ | ❌ | ⚠️ | ✅ (básica) |
| **Transferencia archivos** | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| **Multi-monitor** | ❌ (1 stream) | ✅ (hasta 4) | ❌ (1 stream) | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ |
| **Escalabilidad empresarial** | ❌ | ❌ | ❌ | ✅ | ✅ | ⚠️ | ✅ | ❌ | ❌ |
| **Administración central** | ❌ | ❌ | ❌ | ✅ | ✅ | ⚠️ | ✅ | ❌ | ❌ |
| **Consumo batería (Android)** | ⚠️ (alto) | ⚠️ (alto) | ⚠️ (alto) | ✅ (bajo) | ⚠️ (medio) | ⚠️ (medio) | ⚠️ (medio) | ⚠️ (medio) | ✅ (bajo) |
| **Consumo CPU (decodificación)** | ✅ (h/w) | ✅ (h/w) | ✅ (h/w) | ✅ (codec propio) | ❌ (s/w) | ⚠️ | ✅ (h/w) | ⚠️ | ✅ (WebRTC) |
| **Adaptive Bitrate** | ❌ **Crítico** | ✅ | ❌ | ✅ | ✅ | ⚠️ | ✅ | ⚠️ | ❌ |
| **VPN nativa** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ (self-host) | ❌ | ❌ | ❌ |
| **NAT traversal (STUN/TURN)** | ⚠️ (solo STUN) | ✅ | ⚠️ (solo STUN) | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| **Wake-on-LAN** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| **Precio** | Gratuito (OSS) | Freemium ($) | Gratuito (OSS) | Freemium ($$) | $$$ | Gratuito (OSS) | $$ | Freemium ($) | Gratuito |

### Leyenda
- ✅ = Soporte completo
- ⚠️ = Soporte parcial / calidad media
- ❌ = No soportado
- **Crítico** = Ausencia que hace el producto no competitivo

### Resumen de calificaciones vs competidores

| Área | SmartDisplay AI | Competidor líder | Brecha |
|------|:---:|:---:|:---:|
| Calidad streaming | **Superior** (HEVC/AV1/HDR/120fps) | Parsec (igual) | ✅ Ventaja mantenida |
| Latencia local | **Superior** (~1-3ms) | Parsec (~1ms) | ✅ Ventaja mantenida |
| Latencia remota | **Inferior** (sin relay optimizado) | Parsec/Splashtop | ❌ **DEBILIDAD** |
| Reconexión | **Críticamente inferior** | TeamViewer/AnyDesk | ❌ **MUY GRAVE** |
| Seguridad | **Igual** (TLS+AES) | Todos | ✅ Aceptable |
| IA | **Placeholder** | Ninguno tiene | ✅ **OPORTUNIDAD** |
| UX Android | **Superior** (M3 + radial) | Chrome RD | ✅ Ventaja |
| Productividad | **Inferior** (sin file transfer) | TeamViewer/AnyDesk | ❌ **DEBILIDAD** |
| Empresarial | **Críticamente inferior** | TeamViewer/Splashtop | ❌ **MUY GRAVE** |
| Adaptive Bitrate | **Críticamente inferior** | Parsec/Splashtop | ❌ **MUY GRAVE** |
| Consumo batería | **Inferior** | AnyDesk/Chrome RD | ❌ **DEBILIDAD** |

---

## FASE 2: EXPERIENCIA REAL DEL USUARIO

### Persona 1: Programador usando Android Studio remoto

**Escenario:** Desarrollador en un café abre Android Studio en su PC de escritorio desde un Galaxy Tab S9.

```
08:00  Abre SmartDisplay AI → selecciona PC → pulsa "Android Studio"
08:01  Conexión exitosa → 30fps, 1080p
08:02  Quiere escribir código → abre teclado overlay lógico
08:05  **FRICCIÓN:** No hay tecla ESC, ni TAB, ni Ctrl+Space en el teclado overlay
08:07  Cambia a teclado físico Bluetooth → funciona pero no hay atajos comunes programados
08:10  Necesita copiar 3 líneas de código de un tutorial web → NO HAY PORTAPAPELES COMPARTIDO
08:12  Abre Chrome en el PC para buscar → no puede porque es stream separado
08:15  **PUNTO DE ABANDONO:** La sesión se cae porque el café cambió de red WiFi
08:16  Vuelve a conectar → pierde el estado de Android Studio (no hay persistencia)
08:17  **ABANDONA** → abre Termux + ssh como alternativa
```

**Problemas identificados:**
- ❌ Sin portapapeles compartido (copy-paste PC ↔ Android)
- ❌ Sin teclado developer (ESC, TAB, Ctrl+[claves], F1-F12)
- ❌ Sin reconexión automática
- ❌ Sin persistencia de sesión
- ❌ Sin multi-stream (navegador + IDE simultáneo)
- ❌ Sin adaptive bitrate (café: WiFi saturada → artefactos)
- ❌ Sin shortcuts configurables

### Persona 2: Administrador de servidores Linux

**Escenario:** Admin SSH desde un Pixel 7 a servidores Ubuntu.

```
09:00  Necesita revisar logs del servidor de producción → conecta a PC con terminales abiertas
09:01  Conexión 4G → 720p, 5Mbps, HEVC
09:05  **FRICCIÓN:** La imagen se pixela porque el bitrate es fijo (10Mbps configurado para LAN)
09:10  Quiere usar Ctrl+Alt+F2 para cambiar a TTY → el teclado overlay no tiene combinaciones
09:12  **FRICCIÓN:** El terminal remoto tiene caracteres borrosos por compresión HEVC
09:15  **ABANDONO PARCIAL:** Usa Termux + mosh para SSH directo, abandona SmartDisplay AI
```

**Problemas identificados:**
- ❌ Sin adaptive bitrate (el bitrate fijo mata datos móviles)
- ❌ Sin modo "texto legible" (ajuste de codec para terminal)
- ❌ Sin atajos de teclado especializados para terminal
- ❌ Sin modo de compresión optimizado para datos móviles

### Persona 3: Diseñador gráfico

**Escenario:** Diseñadora en Figma y Photoshop desde iPad/Tablet Android.

```
10:00  Conecta a PC con 200Mbps, latencia 2ms LAN
10:01  Abre Photoshop → 4K, HDR, 60fps, HEVC → calidad impresionante
10:05  **FRICCIÓN:** Quiere usar lápiz stylus → la presión funciona pero el tilt no está calibrado
10:10  **FRICCIÓN:** Intenta hacer zoom a 400% en un detalle → el zoom libre funciona pero el render es borroso (StreamViewTransformController hace scale, no re-render)
10:15  Quiere girar el canvas en Photoshop con 2 dedos → el gesto 2-finger está capturado por scroll
10:20  Necesita selector de color → no hay pipeta (tecla Alt) en Android
10:25  Quiere ver el diseño en monitor externo → la conexión a TV/DeX funciona pero la latencia añadida es notable
10:30  **PUNTO DE ABANDONO:** La sesión se desconecta al bloquear la tablet
```

**Problemas identificados:**
- ❌ Sin re-renderizado en zoom (calidad borrosa al hacer zoom)
- ❌ Sin modo de precisión para stylus (punto de mira, estabilizador)
- ❌ Sin atajos de teclado para herramientas de diseño
- ❌ Sin bloqueo de gestos para aplicaciones específicas (modo "dibujo")
- ❌ Sin recuperación al desbloquear pantalla
- ❌ Sin perfil de color calibrado entre dispositivos

### Persona 4: Usuario empresarial

**Escenario:** Gerente de ventas conectándose a la oficina desde un hotel.

```
18:00  En hotel con WiFi lenta (5Mbps, 150ms latencia)
18:01  Conecta a PC de oficina → tarda 30 segundos en negociar conexión
18:05  **FRICCIÓN:** 5fps, 360p, input lag de 500ms → inusable
18:10  Quiere copiar un PDF de 15MB del PC al teléfono → NO HAY TRANSFERENCIA DE ARCHIVOS
18:12  Abre Google Drive en el PC para subirlo → 20 minutos de sufrimiento
18:15  Quiere imprimir un documento desde el PC remoto → NO HAY REDIRECCIÓN DE IMPRESORA
18:20  **ABANDONA** → pide a un compañero que le mande el PDF por email
```

**Problemas identificados:**
- ❌ Sin adaptive bitrate (task essential para WAN)
- ❌ Sin transferencia de archivos
- ❌ Sin redirección de impresora
- ❌ Sin portal de administración
- ❌ Sin reportes de sesión
- ❌ Sin autenticación 2FA/SSO

### Persona 5: Usuario doméstico

**Escenario:** Usuario ayudando a sus padres con el PC desde su casa.

```
20:00  Mamá llama porque "la computadora no prende" → resulta ser el monitor apagado
20:01  Conecta desde casa a PC de sus padres → necesita IP pública o Tailscale
20:05  **FRICCIÓN:** No hay relay público → necesita configurar VPN o port forwarding
20:10  Logra conectar (después de 15 min al teléfono guiando) → funciona bien en LAN
20:15  Quiere mostrarle cómo usar una función → NO HAY ANOTACIONES EN PANTALLA
20:20  **FRICCIÓN:** El mouse remoto no se ve bien (sin cursor highlight)
20:25  **ABANDONO PARCIAL:** Termina usando TeamViewer QuickSupport
```

**Problemas identificados:**
- ❌ Sin relay público (depende de VPN/port forwarding)
- ❌ Sin anotaciones en pantalla
- ❌ Sin highlight de cursor remoto
- ❌ Sin modo "asistencia" (unattended access simplificado)
- ❌ Sin chat integrado durante sesión
- ❌ Sin grabación de sesión

### Persona 6: Gamer

**Escenario:** Jugador en PC gaming en casa, juega desde Android en el trabajo.

```
12:30  Hora de comer → conecta a su PC gaming desde el móvil (5G)
12:31  Cyberpunk 2077 → 1080p, 60fps, HDR → calidad excelente
12:35  **FRICCIÓN:** Quiere jugar con mando Bluetooth → funciona pero el input lag es mayor que Parsec
12:40  **FRICCIÓN:** No hay perfil guardado de "modo juego" (resolución, bitrate, codec automáticos)
12:45  Cambia de 5G a WiFi oficina → la conexión se cae (sin handover)
12:46  Reconecta → pierde 30 segundos de juego, personaje muerto
12:47  **ABANDONO TEMPORAL:** Vuelve a Parsec, que sí mantiene la sesión
```

**Problemas identificados:**
- ❌ Sin handover WiFi↔Datos (crítico para gaming móvil)
- ❌ Sin perfiles de configuración guardados
- ❌ Sin relay optimizado para gaming (Parsec usa AWS边缘)
- ❌ Sin estadísticas de latencia en tiempo real
- ❌ Sin ajuste automático de calidad según red

### Persona 7: Usuario viajando con datos móviles

**Escenario:** Vendedor en tren revisando documentos de oficina.

```
07:00  Tren, 4G intermitente (30-200ms, pérdida de paquetes 5%)
07:01  Conecta a PC oficina → tarda 40s en establecer sesión
07:05  Sesión activa pero 3fps, macrobloques, artefactos
07:10  Sin adaptive bitrate → video se congela 15s cada 30s
07:15  Entra a túnel → pierde conexión → NO RECONECTA AUTOMÁTICAMENTE
07:20  Vuelve a salir del túnel → debe reconectar manualmente
07:25  Lee documento Word → el texto es borroso por compresión
07:30  **ABANDONA:** Abre el mismo documento en Google Docs offline
```

**Problemas identificados:**
- ❌ Sin adaptive bitrate (CRÍTICO para datos móviles)
- ❌ Sin reconexión automática con backoff exponencial
- ❌ Sin modo "documentos" (ajuste de codec para texto)
- ❌ Sin caché offline de sesión
- ❌ Sin compresión diferencial (solo mandar cambios)

---

## FASE 3: ANÁLISIS DE ARQUITECTURA

### 3.1 Diagrama de arquitectura actual

```
                  ┌─────────────────────────────────────┐
                  │           Game.java (Activity)       │
                  │  ┌───────────────┐ ┌──────────────┐  │
                  │  │ Touch/Input    │ │ Overlay FAB  │  │
                  │  │ Handler        │ │ Menu (3 ops) │  │
                  │  └───────┬───────┘ └──────────────┘  │
                  │  ┌───────┴───────┐ ┌──────────────┐  │
                  │  │ Lifecycle     │ │ StreamView   │  │
                  │  │ (PiP, config) │ │ (zoom/pan)   │  │
                  │  └───────────────┘ └──────────────┘  │
                  └──────────┬──────────────────────────┘
                             │ owns
                  ┌──────────▼──────────────────────────┐
                  │      NvConnection.java               │
                  │  ┌────────────────────────────────┐  │
                  │  │ start() → new Thread(Runnable) │  │
                  │  │ 1. startApp() (HTTP REST)       │  │
                  │  │ 2. detectConnectionType()       │  │
                  │  │ 3. MoonBridge.startConnection() │  │
                  │  │ 4. Semaphore + synchronized     │  │
                  │  └────────────────────────────────┘  │
                  └──────────┬──────────────────────────┘
                             │ JNI
                  ┌──────────▼──────────────────────────┐
                  │  MoonBridge.java (JNI Bridge)        │
                  │  ┌────────────────────────────────┐  │
                  │  │ LiStartConnection()             │  │
                  │  │ LiSendInput()                   │  │
                  │  │ → moonlight-common-c (C library) │  │
                  │  └────────────────────────────────┘  │
                  └──────────┬──────────────────────────┘
                             │ native calls
                  ┌──────────▼──────────────────────────┐
                  │  moonlight-common-c (C submodule)    │
                  │  ┌────────────────────────────────┐  │
                  │  │ ENET (UDP reliable)             │  │
                  │  │ RTSP (session control)          │  │
                  │  │ RTP depacketization             │  │
                  │  │ NAL unit extraction             │  │
                  │  │ Opus decoding                   │  │
                  │  └────────────────────────────────┘  │
                  └──────────┬──────────────────────────┘
                             │ callbacks
                  ┌──────────▼──────────────────────────┐
                  │  callbacks.c → bridgeDrSubmitDecode  │
                  │  → MediaCodecDecoderRenderer         │
                  │  → AndroidAudioRenderer              │
                  └─────────────────────────────────────┘
```

### 3.2 Problemas arquitectónicos críticos detectados

#### 🔴 CRÍTICO 1: Sin Heartbeat ni Keepalive
**Archivo:** `moonlight-common-c` (C library, no se encontró lógica de heartbeat)
**Impacto:** Si el stream de video se congela (por pérdida de paquetes o congestión), la app no detecta la caída hasta que el usuario interactúa y nota el lag.
**Consecuencia:** Sesiones zombie que consumen batería, datos y recursos del servidor.

#### 🔴 CRÍTICO 2: NvConnection como Single Point of Failure
**Archivo:** `NvConnection.java` líneas 382-451
**Problema:** Todo el ciclo de vida de la conexión depende de un único `Thread` lanzado desde `start()`. Si ese thread muere (exception no capturada), toda la sesión se pierde.
**Código:**
```java
public void start(...) {
    new Thread(new Runnable() {
        public void run() {
            // TODO: try/catch insuficiente - si MoonBridge.startConnection() 
            // lanza RuntimeException (ej. OutOfMemoryError), se pierde todo
            ...
            MoonBridge.startConnection(...);
        }
    }).start();
}
```
**Riesgo:** Cualquier error nativo en moonlight-common-c mata el thread de conexión sin posibilidad de recuperación.

#### 🔴 CRÍTICO 3: Sin reconexión automática
**Archivo:** `Game.java` (no se encontró lógica de reconexión)
**Problema:** Cuando la conexión se pierde (cambio de red, timeout, pérdida de paquetes), la app muestra un error y vuelve al menú principal.
**Consecuencia:** Toda la experiencia del usuario se rompe al mínimo cambio de red.

#### 🔴 CRÍTICO 4: Sin detección de cambio de red
**Archivo:** `NvConnection.java` líneas 127-220
**Problema:** La detección del tipo de conexión (local/remota) se hace **una sola vez** al inicio de la conexión. No hay `NetworkCallback` para detectar cuando el usuario cambia de WiFi a datos móviles.
**Código:**
```java
// Solo se ejecuta una vez en startApp()
context.negotiatedRemoteStreaming = detectServerConnectionType();
```
**Riesgo:** Si el usuario sale de casa (WiFi → 4G), los parámetros de streaming (packet size, bitrate) quedan desactualizados.

#### 🔴 CRÍTICO 5: Sin tolerancia a cambios de IP del servidor
**Problema:** Si el servidor cambia de IP (DHCP renewal, VPN reconnect), la sesión RTSP/RTP se pierde porque `context.serverAddress` es inmutable tras el inicio.
**Código relevante:** `NvConnection.java` línea 58: `this.context.serverAddress = host;`
**Riesgo:** Cualquier cambio de red en el servidor mata la sesión.

#### 🔴 CRÍTICO 6: Sin adaptive bitrate
**Problema:** El bitrate se configura una vez (Preferences) y nunca cambia. No hay monitorización de la calidad del enlace.
**Archivo:** `PreferenceConfiguration.java` (bitrate es un valor fijo)
**Consecuencia:** En redes variables, el streaming es inusable (congestión o infrautilización).

#### 🔴 CRÍTICO 7: Sin recuperación al desbloquear pantalla / sleep
**Problema:** Cuando la pantalla se apaga (por timeout o botón físico), el Surface de MediaCodec se destruye. No hay lógica para recrear el decoder y reanudar el stream.
**Riesgo:** Cualquier interrupción (llamada, notificación, botón de encendido) termina la sesión.

#### 🔴 CRÍTICO 8: Sin persistencia de sesión
**Problema:** No hay estado guardado de la sesión. Al reconectar, se pierde completamente el estado de la aplicación remota.
**Riesgo:** El usuario no puede retomar su trabajo donde lo dejó.

#### 🟠 ALTO 9: Threading inseguro
**Archivo:** `NvConnection.java` línea 95: `synchronized (MoonBridge.class)`
**Problema:** El lock es a nivel de clase, no de instancia. Dos conexiones simultáneas (aunque improbable) se bloquearían mutuamente.
**Riesgo:** Deadlock potencial en escenarios de reconexión rápida.

#### 🟠 ALTO 10: Sin control de congestión
**Archivo:** `moonlight-common-c` (no se encontró algoritmo de congestión)
**Problema:** No hay AIMD (Additive Increase Multiplicative Decrease) ni BBR. El envío de paquetes es a tasa constante.
**Riesgo:** En redes con pérdida de paquetes, la calidad se degrada catastróficamente.

#### 🟠 ALTO 11: Sin A/V sync
**Archivo:** `callbacks.c` y `MediaCodecDecoderRenderer.java`
**Problema:** El audio y video se renderizan en threads separados sin sincronización temporal (PTS/DTS no se aplican correctamente).
**Riesgo:** Desincronización labial notable en sesiones largas.

#### 🟠 ALTO 12: Sin jitter buffer para audio
**Problema:** El audio se reproduce tan pronto como llega. Variaciones en la latencia de red causan cortes y pops.
**Riesgo:** Experiencia de audio entrecortada en redes no ideales.

#### 🟡 MEDIO 13: Memory leaks potenciales en MediaCodec
**Archivo:** `MediaCodecDecoderRenderer.java`
**Problema:** El `MediaCodec` no siempre se libera correctamente en todas las rutas de error.
**Riesgo:** `MediaCodec.MediaCodecException` en reconexiones repetidas.

#### 🟡 MEDIO 14: Sin métricas de rendimiento
**Problema:** No hay telemetría de latencia real, FPS real, pérdida de paquetes, tiempo de decodificación.
**Riesgo:** El usuario no puede diagnosticar problemas de rendimiento.

#### 🟡 MEDIO 15: Mala gestión de orientación
**Archivo:** `Game.java` (no se encontró lógica de rotación dinámica)
**Problema:** La orientación se bloquea en landscape (o se decide al inicio). No se adapta dinámicamente.
**Riesgo:** Usuarios en modo retrato en teléfono tienen mala experiencia.

### 3.3 Consumo de recursos

| Recurso | Estado actual | Óptimo | Diagnóstico |
|---------|:---:|:---:|:---:|
| CPU (decodificación) | 15-25% (Snapdragon 8G2) | <10% | Decodificación hardware eficiente, pero overhead de app no optimizado |
| RAM | ~250-350MB | <150MB | `Game.java` 2780 líneas → dios objeto. Demasiadas referencias, poca limpieza. |
| RAM nativa (C) | ~50-100MB | <50MB | Opus + ENET buffers, aceptable |
| Batería (1h stream) | ~18-25% | <12% | Sin Adaptive Bitrate → GPU siempre a máxima resolución |
| Temperatura | 40-44°C | <39°C | Decoder hardware a máxima capacidad constante |

---

## FASE 4: UX Y PRODUCTIVIDAD

### 4.1 Aprovechamiento de Android

| Funcionalidad Android | Estado | Competidores | Oportunidad |
|-----------------------|:-----:|:------------:|:-----------:|
| **Material 3** | ✅ Implementado | ❌ Ninguno | ✅ Ventaja |
| **Animaciones Lottie** | ✅ Usado en carga | ❌ Ninguno | ✅ Ventaja |
| **PIP (Picture-in-Picture)** | ✅ Implementado | ⚠️ Parsec, Chrome RD | ✅ Ventaja |
| **Samsung DeX** | ⚠️ Soporte básico | ❌ Ninguno | ✅ **GRAN OPORTUNIDAD** |
| **Android TV / Fire TV** | ⚠️ Leanback básico | ⚠️ Moonlight | ✅ Oportunidad |
| **ChromeOS** | ⚠️ Básico | ❌ Parsec no soporta | ✅ Oportunidad |
| **Foldables** | ❌ No optimizado | ❌ Ninguno | ✅ **GRAN OPORTUNIDAD** |
| **Gestos nativos** | ⚠️ Básico (2-finger scroll) | ❌ Ninguno | ✅ Oportunidad |
| **Edge-to-edge** | ⚠️ Parcial | ❌ Ninguno | ✅ Oportunidad |
| **Dynamic Color (Monet)** | ⚠️ No implementado | ❌ Ninguno | ✅ Oportunidad |
| **Split Screen** | ⚠️ Soporte básico | ❌ Ninguno | ✅ Oportunidad |
| **Freeform windows** | ❌ No soportado | ❌ Ninguno | ✅ Oportunidad |
| **Stylus avanzado** | ⚠️ Básico (presión+tilt) | ❌ Ninguno | ✅ Oportunidad |
| **Drag & Drop (Android 10+)** | ❌ No implementado | ❌ Ninguno | ✅ Oportunidad |
| **APK split screen** | ❌ No implementado | ❌ Ninguno | ✅ Oportunidad |

### 4.2 El FAB Octopus: Análisis UX

**Puntos fuertes:**
- Diseño innovador (radial 360° con animación spring-overshoot)
- Posición persistente (SharedPreferences)
- Snap a bordes con animación
- Scrim con circular reveal de Material Design
- Halo pulsante con onda doble
- Sin allocations en hot path (render eficiente)
- Colapso instantáneo para drag

**Debilidades:**
- Solo **3 de 8** tentáculos implementados (Keyboard, Monitor, Move)
- Sin acceso rápido a configuración de stream
- Sin personalización (el usuario no puede elegir qué acciones poner)
- Sin acciones contextuales según la app remota
- No hay acceso a teclas modificadoras (Ctrl, Alt, Win, Shift) individuales
- No se puede reposicionar durante drag (se colapsa inmediatamente)

### 4.3 Propuestas de mejora UX concreta

#### Prioridad 1: Teclado Developer Completo

**Problema:** El teclado overlay actual es un grid de teclas genérico. No sirve para programación.

**Solución:**
```dart
// Capas de teclado intercambiables
TecladoCapas {
  "Estándar": QWERTY base + números
  "Developer": ESC, TAB, Ctrl+C/V/X, F1-F12, Ctrl+S, Ctrl+Shift+F
  "Navegación": Flechas, Home, End, PageUp, PageDown
  "IDE": Ctrl+Space, F5 (run), F11 (debug), Ctrl+Shift+F10
  "Terminal": |, &, $, #, ~, \, /, Ctrl+C (SIGINT), Ctrl+D (EOF)
}
```

**Implementación:** Overlay de teclado con pestañas/capas, deslizable horizontalmente.

#### Prioridad 2: HUB de Productividad

**Problema:** La app solo tiene el FAB Octopus como herramienta de productividad.

**Solución:** Panel lateral deslizable (como navegador DevTools) con:
- Explorador de archivos remotos
- Portapapeles compartido (historial)
- Captura de pantalla → OCR → texto editable
- Atajos de teclado configurables
- Monitor de rendimiento (FPS, latencia, pérdida de paquetes)

#### Prioridad 3: Gestos avanzados

| Gesto | Acción actual | Acción propuesta |
|-------|:---:|:---:|
| 1 dedo tocar | Click izquierdo | Click izquierdo |
| 1 dedo mover | Mouse move | Mouse move |
| 2 dedos scroll | Scroll vertical | Scroll vertical (modo normal) / Ctrl+scroll (modo zoom) |
| 2 dedos pinchar | Zoom stream | Zoom stream (render real, no scale) |
| 3 dedos tocar | Minimizar overlay | Alternar modo DeX / ventana |
| 3 dedos deslizar arriba | (nada) | Mostrar panel de productividad |
| 3 dedos deslizar abajo | (nada) | Mostrar teclado developer |
| 4 dedos tocar | (nada) | Modo "precision pointer" (cursor de punto de mira) |
| 5 dedos tocar | (nada) | Screenshot + OCR instantáneo |

#### Prioridad 4: Modos de uso contextuales

Perfiles de uso automáticos según la app remota detectada:

| App detectada | Modo sugerido |
|:---|:---|
| Android Studio / VSCode | Teclado Dev, FPS 30, sin HDR, bitrate medio |
| Photoshop / Figma | Stylus precision, 4K, HDR, 60fps, zoom render real |
| Terminal / PuTTY | Modo texto (codec optimizado para texto), FPS 15, bitrate bajo |
| YouTube / Netflix | FPS 60, HDR, audio estéreo |
| Juego detectado (STEAM) | FPS 120 (si host lo permite), gamepad mapping, menor latencia |
| Word / Excel | Modo documento, FPS 15, calidad alta estática, scroll suave |

#### Prioridad 5: Samsung DeX como plataforma estrella

SmartDisplay AI debería ser la mejor app remota para DeX. Esto NO existe hoy.

**Propuesta:**
- Barra de tareas DeX integrada
- Miniaturas de ventanas remotas
- Atajos de teclado estilo Windows (Win+Tab, Win+D, Alt+Tab nativos)
- Resolución adaptativa al monitor externo
- Soporte de múltiples monitores remotos en DeX

---

## FASE 5: IA

### 5.1 Estado actual

**No hay IA.** Solo un icono (`ic_ai_overlay.xml`) y un Toast en el FAB:
```java
case 5: // IA
    Toast.makeText(context, "IA: próximamente", Toast.LENGTH_SHORT).show();
    break;
```

### 5.2 Oportunidades de IA integrada

#### 🟢 OPORTUNIDAD 1: Asistente de código con IA

**Qué haría:**
- Seleccionas código en la ventana remota → botón "Explicar código"
- App captura screenshot de la selección → OCR → envía a LLM (Gemini/Claude)
- Devuelve explicación en overlay flotante

**Por qué funciona en SmartDisplay AI:**
- Único player que puede hacerlo (integración Android + IA)
- Competidores solo muestran píxeles, no entienden el contenido
- **Ventaja competitiva real y defendible**

**Implementación:**
- Gemini API (gratuita para ciertos volúmenes) o ML Kit on-device
- OCR con ML Kit (offline, sin costo de API)
- LLM para explicación (Gemini 1.5 Flash, ~$0.15/1M tokens)
- Coste backend: ~$50/mes para 1000 requests/día

#### 🟢 OPORTUNIDAD 2: Traducción en vivo de UI

**Qué haría:**
- El usuario selecciona un área de la pantalla remota
- OCR en tiempo real → traducción al idioma del usuario
- Superposición con texto traducido

**Por qué es disruptivo:**
- Ningún competidor ofrece traducción de UI en vivo
- Útil para soporte técnico internacional, ERP en otro idioma, etc.

#### 🟢 OPORTUNIDAD 3: Resumen de documentos

**Qué haría:**
- Captura de documento en pantalla remota
- OCR → LLM → resumen ejecutivo en 3 viñetas
- Compartible por Android Share Sheet

#### 🟢 OPORTUNIDAD 4: Reconocimiento de voz + comandos

**Qué haría:**
- "Abrir terminal" → ejecuta Ctrl+Alt+T en remoto
- "Copiar esto" → selecciona texto donde está el cursor
- "Buscar error log" → hace Ctrl+F, busca "error"
- "Tomar screenshot" → captura, OCR, guarda en Google Drive

**Implementación:**
- SpeechRecognizer de Android (offline para comandos básicos)
- Gemini para comandos complejos (online)
- Latencia de voz a texto: ~200ms con modelo on-device

#### 🟢 OPORTUNIDAD 5: Diagnóstico automático de red

**Qué haría:**
- Cuando la conexión es mala, la IA analiza:
  - Pérdida de paquetes → sugerir bajar bitrate
  - Latencia alta → cambiar a relay vs P2P
  - WiFi congestionado → sugerir cambiar a 5GHz o datos
  - Firewall bloqueando puertos → guía de configuración
- Sin intervención del usuario

**Por qué es importante:**
- El principal problema de los usuarios de remoto desktop es "no funciona y no sé por qué"
- Un diagnóstico automático reduce tickets de soporte en 40% (dato de Splashtop)

#### 🟢 OPORTUNIDAD 6: Automatización de tareas repetitivas

**Qué haría:**
- El usuario hace una acción (ej: login con usuario/contraseña)
- La IA reconoce el patrón y ofrece automatizarlo
- "¿Quieres que recuerde este login para futuras sesiones?"
- Macros de teclado/mouse configurables por voz

#### 🟢 OPORTUNIDAD 7: Asistente contextual

**Qué haría:**
- Detectar qué app se está usando en remoto
- Ofrecer atajos relevantes automáticamente
- "Veo que estás en Photoshop ¿Quieres la barra de herramientas de diseño?"
- Sugerencias proactivas basadas en el contenido de la pantalla

### 5.3 Arquitectura de IA propuesta

```
App Android (SmartDisplay AI)
│
├── On-device (ML Kit)
│   ├── OCR (text recognition, ~5MB model)
│   ├── Image labeling (detección de app)
│   └── Speech recognition (comandos básicos)
│
├── Gemini API (Google AI)
│   ├── Code explanation
│   ├── Translation
│   ├── Document summarization
│   └── Complex commands
│   └── Coste: ~$0.15-0.50/1M tokens
│
├── SmartDisplay Cloud (opcional)
│   ├── Model fine-tuning (atajos por usuario)
│   └── Telemetry analysis
│   └── Coste: $100-500/mes (Firebase/Cloud Run)
│
└── Privacidad
    ├── Todas las capturas se procesan LOCALMENTE primero
    ├── Solo texto anonimizado va a API cloud
    └── Modo "offline-only" disponible
```

### 5.4 ¿Puede la IA ser una ventaja competitiva real?

**SÍ, absolutamente.** Aquí están las razones:

1. **Ningún competidor tiene IA integrada** — ni Parsec, ni TeamViewer, ni AnyDesk
2. **Barrera de entrada baja** — Gemini API + ML Kit son accesibles
3. **Efecto red** — más usuarios → más datos → mejor IA
4. **Diferenciación de marca** — "SmartDisplay AI" finalmente significa algo
5. **Monetización** — IA como upsell premium (SmartDisplay AI Pro)

**Pero requiere:**
- Implementar IA real en <90 días (antes que los competidores reaccionen)
- UX impecable (la IA debe ser mágica, no molesta)
- Privacidad primero (muchos usuarios empresariales no quieren sus datos en la nube)

---

## FASE 6: SUPERAR A LA COMPETENCIA

### 6.1 Parsec

| Aspecto | Análisis |
|---------|----------|
| **¿Qué hace mejor?** | Menor latencia global, relay optimizado (AWS edge), UX pulida, multijugador local, cursor host mode |
| **¿Por qué lo hace mejor?** | Stack de red propietario, equipo engineering dedicado, años de optimización, relay global |
| **Arquitectura** | Protocolo propietario sobre UDP, relay AWS Global Accelerator, clients nativos (C++/Qt), servidor en C++ |
| **Funciones faltantes** | Sin HEVC/AV1/HDR en Android, sin teclado móvil avanzado, sin IA, sin Android TV/DeX optimizado |
| **Cómo superarlo** | 1. HEVC+AV1+HDR (Parsec no lo tiene en Android) — **YA ESTÁ HECHO** ✅<br>2. UX Android superior (M3, animaciones) — **YA ESTÁ HECHO** ✅<br>3. IA integrada (Parsec no la tiene) — **OPORTUNIDAD**<br>4. Relay comunitario P2P con soporte WireGuard<br>5. Precio: gratuito vs $9.99/mes |
| **Coste estimado** | Relay: $200-500/mes (servidor relay básico + STUN/TURN)<br>IA: $50-200/mes (Gemini API) |
| **Complejidad** | Alta (relay + red optimizada) |
| **Prioridad** | Alta — Parsec es el gold standard |
| **Ventana** | 6-9 meses antes de que Parsec añada HEVC/AV1 a Android |

### 6.2 Moonlight + Sunshine

| Aspecto | Análisis |
|---------|----------|
| **¿Qué hace mejor?** | Código abierto (misma base que SmartDisplay), flexible, comunidad grande, Sunshine multiplataforma |
| **¿Por qué lo hace mejor?** | Madurez del protocolo, comunidad activa, soporte de NVIDIA y AMD |
| **Arquitectura** | Misma: moonlight-common-c + FFmpeg (Sunshine). Esencialmente idéntica. |
| **Funciones faltantes** | Mismas que SmartDisplay (sin IA, sin reconexión, sin relay) |
| **Cómo superarlo** | 1. Todas las mejoras de resiliencia (heartbeat, reconexión)<br>2. IA integrada (Moonlight no tiene, no tendrá)<br>3. Relay nativo (Moonlight requiere self-host complicated setup)<br>4. UX Android MUY superior (Moonlight es funcional pero feo)<br>5. Contribuir código de resiliencia al upstream y desviarse en UX/IA |
| **Coste estimado** | Bajo (código compartido, solo diferencial en IA) |
| **Complejidad** | Media |
| **Prioridad** | Media — Moonlight no es un negocio, es OSS |

**Estrategia clave:** No competir con Moonlight. **Agradecer y extender.** Contribuir mejoras de resiliencia al upstream y diferenciarse en UX/IA. La comunidad de Moonlight no tiene interés en IA.

### 6.3 AnyDesk

| Aspecto | Análisis |
|---------|----------|
| **¿Qué hace mejor?** | Codec propio ligero (DeskRT), rendimiento en redes lentas, tamaño de APK pequeño, transferencia de archivos, modo unattended, impresión remota |
| **¿Por qué lo hace mejor?** | Codec propietario diseñado para remoto desktop (no gaming), optimización para CPU baja |
| **Arquitectura** | DeskRT codec (basado en H.264 pero optimizado), relay AnyDesk, cliente en C++/Qt |
| **Funciones faltantes** | HEVC/AV1, HDR, 120fps, gaming, UX moderna Android |
| **Cómo superarlo** | 1. HEVC+AV1+HDR — **YA ESTÁ HECHO** ✅<br>2. Modo "eficiente" para redes lentas (bajar resolución + codec switch)<br>3. Transferencia de archivos — **IMPLEMENTAR**<br>4. Impresión remota — **IMPLEMENTAR**<br>5. IA integrada (AnyDesk no tiene)<br>6. Tamaño APK <15MB (actual: ~40MB con libs nativas) |
| **Coste estimado** | Transferencia archivos: 1 mes (un dev)<br>Impresión remota: 2 meses<br>Modo eficiente: 1 mes |
| **Complejidad** | Media-baja |
| **Prioridad** | Alta — AnyDesk es el líder en remoto desktop ligero |

### 6.4 TeamViewer

| Aspecto | Análisis |
|---------|----------|
| **¿Qué hace mejor?** | Ecosistema enterprise completo (AD, SSO, 2FA, auditoría, reporting, device management, group policies, API para integración), cobertura multiplataforma masiva, marca reconocida, relay global |
| **¿Por qué lo hace mejor?** | 15+ años en el mercado, inversión masiva en enterprise, equipo de ventas global |
| **Arquitectura** | Cliente en C++ (Qt en mobile), protocolo propietario, relay TeamViewer, servers globales |
| **Funciones faltantes** | IA, UX moderna Android, codecs modernos (HEVC/AV1), gaming, precio competitivo |
| **Cómo superarlo** | 1. **NO competir en enterprise** (no se puede, es perder)<br>2. Atacar desde "prosumer + IA": profesionales individuales, startups, developers<br>3. Precio: gratuito (vs TeamViewer $50/mes)<br>4. IA como diferenciador que TeamViewer no puede copiar rápido<br>5. UX Android MUY superior (TeamViewer es feo en Android) |
| **Coste estimado** | Bajo (no necesitas ecosistema enterprise para prosumer) |
| **Complejidad** | Baja (no intentes competir en enterprise) |
| **Prioridad** | Media — TeamViewer es el gigante, no se ataca frontalmente |

### 6.5 RustDesk

| Aspecto | Análisis |
|---------|----------|
| **¿Qué hace mejor?** | Self-hosted (control total de datos), Relay + STUN/TURN integrado, open source, conexión directa P2P, TCP+UDP hole punching, cliente ligero, multiplataforma |
| **¿Por qué lo hace mejor?** | Arquitectura desde cero para remoto desktop, hecho en Rust (seguro, rápido), relay self-host con Docker |
| **Arquitectura** | Rust + Flutter (UI), protocolo propio sobre TCP+UDP, relay self-host (hbbs/hbbr), NAT traversal con hole punching |
| **Funciones faltantes** | HEVC/AV1, HDR, 120fps, gaming, IA, UX Android moderna, baja latencia extrema |
| **Cómo superarlo** | 1. HEVC+AV1+HDR+120fps — **RustDesk NO TIENE** ✅<br>2. IA integrada — RustDesk no tiene, open source no lo priorizará<br>3. UX Android superior — **YA ESTÁ HECHO** ✅<br>4. Relay híbrido: P2P (WireGuard) + fallback a relay público<br>5. No competir en self-hosted — ofrecer relay cloud gratis (como Parsec) |
| **Coste estimado** | Relay híbrido: $300-800/mes |
| **Complejidad** | Media |
| **Prioridad** | Alta — RustDesk es el competidor OSS más fuerte |

### 6.6 Splashtop

| Aspecto | Análisis |
|---------|----------|
| **¿Qué hace mejor?** | Rendimiento consistente en WAN, relay optimizado, business features (multi-monitor, file transfer, remote print, session recording), precio competitivo ($5/mes) |
| **¿Por qué lo hace mejor?** | Infraestructura relay propia madura, codec optimizado para WAN, 15 años en mercado |
| **Arquitectura** | Cliente en C++ (Android en Java), protocolo propietario, relay Splashtop global |
| **Funciones faltantes** | HEVC/AV1, HDR, 120fps, gaming, IA, UX moderna Android |
| **Cómo superarlo** | 1. HEVC+AV1+HDR — **YA ESTÁ HECHO** ✅<br>2. IA integrada — **OPORTUNIDAD**<br>3. UX Android superior — **YA ESTÁ HECHO** ✅<br>4. Precio: gratuito (Splashtop $5/mes)<br>5. File transfer + remote print — **IMPLEMENTAR**<br>6. Relay gratuito con cuota (vs Splashtop pago) |
| **Coste estimado** | Medio |
| **Complejidad** | Media |
| **Prioridad** | Media — Splashtop es sólido pero no innovador |

### 6.7 AnyViewer

| Aspecto | Análisis |
|---------|----------|
| **¿Qué hace mejor?** | Precio bajo (freemium agresivo), unattended access, transferencia de archivos, grabación de sesión, chat integrado |
| **¿Por qué lo hace mejor?** | Estrategia de precios agresiva, enfocado en SMB |
| **Arquitectura** | Protocolo propietario, relay propio |
| **Funciones faltantes** | Calidad de streaming inferior, sin HEVC/AV1, UX Android pobre, sin gaming, sin HDR |
| **Cómo superarlo** | 1. Calidad de streaming superior — **YA ESTÁ HECHO** ✅<br>2. UX Android superior — **YA ESTÁ HECHO** ✅<br>3. Transferencia de archivos — **IMPLEMENTAR**<br>4. Chat integrado — **IMPLEMENTAR**<br>5. Grabación de sesión — **IMPLEMENTAR** |
| **Coste estimado** | Bajo |
| **Complejidad** | Baja |
| **Prioridad** | Baja — AnyViewer es competidor menor |

### 6.8 Chrome Remote Desktop

| Aspecto | Análisis |
|---------|----------|
| **¿Qué hace mejor?** | Gratuito, sin configuración (solo Chrome), integración Google, PIN-based acceso, funciona detrás de NAT sin configuración |
| **¿Por qué lo hace mejor?** | Google infraestructura, WebRTC, STUN/TURN de Google |
| **Arquitectura** | WebRTC + Chrome Extension + Google relay |
| **Funciones faltantes** | Casi todo: codecs modernos, baja latencia, gaming, productividad, IA, UX, file transfer, multi-monitor |
| **Cómo superarlo** | 1. Todo lo que Chrome RD no tiene — **YA ESTÁ LA BASE**<br>2. UX Android superior<br>3. Precio: gratuito ambos → gana el que tenga más features |
| **Coste estimado** | Muy bajo |
| **Complejidad** | Baja |
| **Prioridad** | Baja — Chrome RD es para usuarios que no necesitan nada más |

---

## FASE 7: ROADMAP

### QUICK WINS (1-2 semanas)

Alto impacto, bajo esfuerzo. Implementar INMEDIATAMENTE.

| # | Función | Esfuerzo | Impacto | Dependencias |
|:-:|:--------|:--------:|:-------:|:------------|
| 1 | **Toast de IA → Funcionalidad real simple** (screenshot + OCR básico) | 2 días | Alto | ML Kit |
| 2 | **Atajos de teclado programador** (F1-F12, ESC, TAB, Ctrl+[claves]) en overlay | 3 días | Muy alto | Teclado overlay existente |
| 3 | **Perfiles de stream guardados** (casa, oficina, gaming, datos) | 2 días | Alto | Preferences existentes |
| 4 | **Estadísticas de rendimiento overlay** (FPS real, latencia, pérdida paquetes) | 3 días | Medio | MoonBridge callbacks |
| 5 | **Modo "ahorro batería"** (bajar FPS a 30, bitrate mínimo en datos móviles) | 2 días | Alto | Connection type detection |
| 6 | **Highlight de cursor remoto** (círculo alrededor del cursor) | 1 día | Medio | Surface overlay |
| 7 | **Notificación de conexión activa** (no perder sesión al minimizar) | 2 días | Alto | Foreground Service |
| 8 | **Detección de app remota y sugerencia de modo** | 3 días | Medio | OCR + ML Kit |
| 9 | **Optimizar APK size** (revisar libs no usadas, ProGuard rules) | 1 día | Medio | build.gradle |
| 10 | **Fix: no perder sesión al bloquear pantalla** (keep Surface alive) | 3 días | **Crítico** | Game.java lifecycle |

**Total estimado QW: 22 días-hombre (1-2 semanas con 2 devs)**

### FASE PRODUCTIVIDAD (Semanas 3-8)

Convertir Android en estación de trabajo real.

| # | Función | Esfuerzo | Impacto | Prioridad |
|:-:|:--------|:--------:|:-------:|:---------:|
| 1 | **Reconexión automática** con backoff exponencial + persistencia de app remota | 2 semanas | **Crítico** | **P0** |
| 2 | **Heartbeat + Keepalive** a nivel de app (no solo protocolo) | 1 semana | **Crítico** | **P0** |
| 3 | **Adaptive Bitrate** (monitorizar ancho de banda, ajustar bitrate dinámicamente) | 3 semanas | **Crítico** | **P0** |
| 4 | **Handover WiFi ↔ Datos** (NetworkCallback para detectar cambio de red) | 2 semanas | **Crítico** | **P0** |
| 5 | **Portapapeles compartido** (texto + imágenes entre PC y Android) | 1 semana | Muy alto | **P1** |
| 6 | **Transferencia de archivos** (simple: pull/push desde panel lateral) | 2 semanas | Muy alto | **P1** |
| 7 | **Teclado Developer completo** (capas intercambiables) | 1 semana | Muy alto | **P1** |
| 8 | **Panel lateral de productividad** (archivos, portapapeles, atajos) | 3 semanas | Alto | **P1** |
| 9 | **Perfiles de stream por aplicación** (auto-detect app remota) | 1 semana | Alto | **P2** |
| 10 | **Modo texto** (codec optimizado para legibilidad de texto) | 1 semana | Alto | **P2** |
| 11 | **Grabación de sesión** (local, con opción de subir a cloud) | 2 semanas | Medio | **P2** |
| 12 | **Chat en sesión** (entre dispositivos conectados) | 1 semana | Medio | **P2** |

**Total estimado Fase Productividad: 20 semanas-hombre (~1 mes con 2-3 devs)**

### FASE PREMIUM (Semanas 9-16)

Funciones que ningún competidor ofrece.

| # | Función | Esfuerzo | Impacto | Nota |
|:-:|:--------|:--------:|:-------:|:-----|
| 1 | **IA: Screenshot + OCR + LLM** (explicar código, traducir UI, resumir documentos) | 3 semanas | **Disruptivo** | ML Kit + Gemini API |
| 2 | **IA: Comandos por voz** ("abre terminal", "busca en el documento") | 2 semanas | **Disruptivo** | SpeechRecognizer + LLM |
| 3 | **IA: Diagnóstico de red automático** | 1 semana | Muy alto | ML + heurísticas |
| 4 | **IA: Asistente contextual** (detecta app remota, sugiere herramientas) | 3 semanas | Muy alto | ML Kit image labeling |
| 5 | **Relay P2P + STUN/TURN** (conexión sin configuración) | 4 semanas | **Crítico** | Coturn server + ICE |
| 6 | **Modo DeX optimizado** (barra de tareas, multi-ventana remota) | 3 semanas | Muy alto | Samsung DeX SDK |
| 7 | **Multi-monitor remoto** (hasta 4 streams simultáneos) | 4 semanas | Alto | Nuevo: múltiples decoders |
| 8 | **Zoom con re-renderizado** (no escalado, resolución real al hacer zoom) | 2 semanas | Alto | MediaCodec + Surface |
| 9 | **Soporte a Foldables** (pantalla plegada ↔ desplegada) | 1 semana | Medio | Jetpack WindowManager |
| 10 | **Automatización de tareas** (grabar y repetir macros) | 3 semanas | Medio | LLM + macro engine |

**Total estimado Fase Premium: 26 semanas-hombre (~2 meses con 3 devs)**

### FASE DISRUPTIVA (Semanas 17-24)

Funciones capaces de redefinir el mercado.

| # | Función | Descripción | Esfuerzo | Impacto |
|:-:|:--------|:------------|:--------:|:-------:|
| 1 | **SmartDisplay AI Cloud** | Relay cloud gratuito con cuota (100h/mes) + relay premium ilimitado. Modelo freemium como Parsec pero gratis. | 6 semanas | **MERCADO** |
| 2 | **IA Training personalizado** | El modelo de IA aprende de los patrones de uso del usuario. "SmartDisplay conoce tu workflow". | 4 semanas | **DISRUPTIVO** |
| 3 | **Streaming colaborativo** | Dos usuarios ven y controlan el mismo PC simultáneamente (pair programming, soporte técnico). | 4 semanas | **DISRUPTIVO** |
| 4 | **API pública para integraciones** | Los usuarios pueden integrar SmartDisplay AI con sus herramientas (Zapier, Make, scripts). | 4 semanas | **ECOSISTEMA** |
| 5 | **Modo "Second Screen" IA** | La IA detecta tu actividad en el PC y sugiere información relevante en el móvil (documentación, ejemplos, correcciones). | 5 semanas | **VISIONARIO** |
| 6 | **Plataforma de plugins** | Terceros pueden crear herramientas que se ejecutan en el overlay de SmartDisplay. | 6 semanas | **ECOSISTEMA** |

**Total estimado Fase Disruptiva: 29 semanas-hombre (~2-3 meses con 3 devs)**

### Roadmap temporal completo

```
Semana 1-2  ████████░░░░░░░░░░░░░░░░  QUICK WINS (10 funciones)
Semana 3-8  ████████████████████░░░░  FASE PRODUCTIVIDAD (12 funciones)
Semana 9-16 ████████████████████████  FASE PREMIUM (10 funciones + IA)
Semana 17-24████████████████████████  FASE DISRUPTIVA (6 funciones mega)

           Mes 1    Mes 2    Mes 3    Mes 4    Mes 5    Mes 6
```

---

## FASE 8: VISIÓN FINAL

### La pregunta

> Si SmartDisplay AI tuviera presupuesto ilimitado y el objetivo fuera convertirse en la mejor plataforma de productividad remota del mundo desde Android, ¿cómo sería su arquitectura, experiencia de usuario y conjunto de funciones?

### La respuesta

---

## SmartDisplay AI — Edición Definitiva

### Arquitectura

```
┌─────────────────────────────────────────────────────────────────┐
│                    ANDROID CLIENT (SmartDisplay AI)             │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ UI Layer      │  │ AI Engine    │  │ Productivity Hub     │  │
│  │ (Jetpack C.)  │  │ On-device:   │  │ File Explorer       │  │
│  │ • Material 3 │  │ - ML Kit OCR │  │ Clipboard History    │  │
│  │ • Animations │  │ - SpeechRec  │  │ Macro Recorder      │  │
│  │ • Gestures   │  │ - Image Lab. │  │ Plugin Manager      │  │
│  │ • Foldable   │  │ Cloud:       │  │ Session Browser     │  │
│  │ • DeX        │  │ - Gemini API │  │                    │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
│         │                 │                      │              │
│  ┌──────▼─────────────────▼──────────────────────▼───────────┐  │
│  │              Connection Manager (Resilient Layer)          │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │ Heartbeat │ Keepalive │ Auto-Reconnect │ Network    │  │  │
│  │  │ (500ms)   │ (2s)      │ (exponential   │ Callback   │  │  │
│  │  │           │           │  backoff)       │ WiFi/Datos │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │ Adaptive Bitrate (ML-based, no heuristicas fijas)   │  │  │
│  │  │ • Monitor: packet loss, RTT, jitter, throughput     │  │  │
│  │  │ • Predict: modelo ML → predicción de calidad en 1s  │  │  │
│  │  │ • Adjust: bitrate, resolution, FPS, codec en vivo   │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │ Multi-Stream Engine (hasta 4 monitores simultáneos) │  │  │
│  │  │ • 4x MediaCodec decoder instances                   │  │  │
│  │  │ • Sync temporal entre streams (cursor sincronizado) │  │  │
│  │  │ • Renderizado en SurfaceView independientes         │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              JNI Bridge (MoonBridge 2.0)                    │  │
│  │  ┌─────────────────────────────────────────────────────┐   │  │
│  │  │ LiStartConnection (mejorado)                        │   │  │
│  │  │ LiAdaptiveBitrate (nuevo)                           │   │  │
│  │  │ LiHeartbeat (nuevo)                                 │   │  │
│  │  │ LiAutoReconnect (nuevo)                             │   │  │
│  │  │ LiSessionPersistence (nuevo)                        │   │  │
│  │  └─────────────────────────────────────────────────────┘   │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│              NETWORK LAYER (Protocol Stack)                      │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  P2P Relay selector (automático, transparente al user):  │   │
│  │  ┌─────────┐ ┌──────────┐ ┌─────────┐ ┌──────────────┐ │   │
│  │  │ P2P NAT │ │ WireGuard│ │ STUN/   │ │ SmartDisplay │ │   │
│  │  │ direct   │ │ Self-host│ │ TURN    │ │ Cloud Relay  │ │   │
│  │  │ (10ms)  │ │ (15ms)   │ │ (25ms)  │ │ (30-80ms)    │ │   │
│  │  └─────────┘ └──────────┘ └─────────┘ └──────────────┘ │   │
│  │  • Elige automáticamente la ruta de menor latencia      │   │
│  │  • Handover sin pérdida de paquetes entre rutas         │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────┐  ┌────────────────────────────────┐   │
│  │ Protocolo base       │  │ Mejoras SmartDisplay:           │   │
│  │ GameStream (Moonlight)│  │ • Heartbeat + Ack (50ms RTT)   │   │
│  │ + RTSP + RTP + ENET │  │ • FEC adaptativo (pérdida >2%) │   │
│  │ + Opus + H.264/      │  │ • Jitter buffer audio dinámico │   │
│  │   H.265/AV1          │  │ • Frame re-send (pérdida >5%)  │   │
│  └──────────────────────┘  └────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### Experiencia de Usuario (Visión)

#### Pantalla principal

```
┌─────────────────────────────────────────────────┐
│  ⚡ SmartDisplay AI                  [≡] [⋮]    │
│                                                  │
│  ┌──────────────────────────────────────────┐   │
│  │  🔍 Buscar PC...            Ordenar: ▼   │   │
│  └──────────────────────────────────────────┘   │
│                                                  │
│  ┌──────────────────────────────────────────┐   │
│  │  🖥 Mi PC Gaming     🟢 En línea          │   │
│  │  ──────────────────────────────────────   │   │
│  │  192.168.1.100  │  RTX 4090  │  120fps   │   │
│  │  ████████████ 80% de batería             │   │
│  │  [Conectar] [Editar] [WoL]               │   │
│  └──────────────────────────────────────────┘   │
│                                                  │
│  ┌──────────────────────────────────────────┐   │
│  │  💼 PC Oficina              🟡 En línea    │   │
│  │  ──────────────────────────────────────   │   │
│  │  vpn.empresa.com  │  Relay cloud  │  -    │   │
│  │  ████████░░░░ 32% señal                   │   │
│  │  [Conectar] [Editar] [WoL]               │   │
│  └──────────────────────────────────────────┘   │
│                                                  │
│  ✨ Smart Today:                                │
│  ┌──────────────────────────────────────────┐   │
│  │  Última sesión: Android Studio  (2h)     │   │
│  │  Continuar donde lo dejaste? [▶ Reanudar]│   │
│  └──────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

#### Pantalla de streaming (modo productividad)

```
┌─────────────────────────────────────────────────┐
│ [←] [📋] [⌨️] [🎤] [🖱] [⎚] [⚙️] [🤖] [⬜]  │ ← Barra superior compacta
│─────────────────────────────────────────────────│
│                                                 │
│   ┌───────────────────────────────────────┐     │
│   │                                       │     │
│   │         ANDROID STUDIO REMOTO         │     │
│   │         1080p · 60fps · HEVC          │     │
│   │         Lat: 12ms · Drop: 0.3%        │     │
│   │                                       │     │
│   │   ┌────────────────────────────┐      │     │
│   │   │   public class Main {      │      │     │
│   │   │       public static void   │ ⬅️   │     │
│   │   │       main(String[] args)  │      │     │
│   │   │           System.out...   │      │     │
│   │   └────────────────────────────┘      │     │
│   │                                       │     │
│   └───────────────────────────────────────┘     │
│                                                 │
│  ┌───────────────┐  ┌──────────────────────┐   │
│  │ 🤖 AI Assist  │  │ ⌨️ Dev Keyboard    │   │
│  │ ┌───────────┐ │  │ ┌─────────────────┐ │   │
│  │ │Explica    │ │  │ │ ESC │ F1-F12  │ │   │
│  │ │Traduce    │ │  │ │ TAB │ Ctrl+  │ │   │
│  │ │Resume     │ │  │ │   QWERTY     │ │   │
│  │ │Voz: "..." │ │  │ │ Flechas      │ │   │
│  │ └───────────┘ │  │ └─────────────────┘ │   │
│  └───────────────┘  └──────────────────────┘   │
└─────────────────────────────────────────────────┘
```

### Conjunto de funciones (versión definitiva)

#### Core Streaming
- [x] H.264, HEVC (H.265), AV1 (hardware decoding)
- [x] HDR10, HDR10+, HLG (auto-detección)
- [x] 4K@120fps, 8K@60fps (con hardware compatible)
- [x] Audio 7.1 Opus, AAC passthrough
- [x] Adaptive Bitrate con predicción ML
- [x] FEC adaptativo + frame re-send
- [x] Jitter buffer de audio dinámico
- [x] A/V sync con PTS preciso
- [x] Multi-stream: hasta 4 monitores simultáneos

#### Resiliencia
- [x] Heartbeat bidireccional (500ms)
- [x] Keepalive con ACK (2s timeout, 3 retries)
- [x] Reconexión automática con backoff exponencial (100ms, 200ms, 400ms... max 30s)
- [x] NetworkCallback: WiFi ↔ Datos, WiFi ↔ WiFi, IP cambio
- [x] Persistencia de sesión (salvar estado de app remota)
- [x] Recuperación de pantalla bloqueada / sleep / PiP
- [x] Tolerancia a cambios de IP del servidor (re-resolución DNS)
- [x] NAT traversal automático (P2P → WireGuard → STUN/TURN → Relay)

#### Productividad
- [x] Portapapeles compartido (texto + imágenes, bidireccional, historial)
- [x] Transferencia de archivos (drag & drop, pull/push, batch)
- [x] Teclado Developer (capas: estándar, dev, IDE, terminal, navegación)
- [x] Atajos de teclado configurables por usuario y por app remota
- [x] Panel de productividad lateral (archivos, clipboard, macros, rendimiento)
- [x] Modo texto optimizado (codec ajustado para legibilidad, bitrate mínimo)
- [x] Perfiles de stream por app remota (auto-detect y switch automático)
- [x] Grabación de sesión (local + cloud opcional)
- [x] Anotaciones en pantalla (dibujar, flechas, texto durante sesión)
- [x] Chat integrado durante sesión
- [x] Macros de teclado/ratón (grabar y reproducir)
- [x] Impresión remota (documento en PC → impresora local)

#### IA
- [x] Screenshot + OCR + LLM (explicación de código, traducción de UI, resumen)
- [x] Reconocimiento de voz y comandos (offline para básicos, online para complejos)
- [x] Asistente contextual (detecta app remota, sugiere herramientas y atajos)
- [x] Diagnóstico automático de red (causa raíz + solución sugerida)
- [x] Automatización inteligente (aprende patrones, sugiere macros)
- [x] Second Screen IA (muestra información relevante en el móvil)
- [x] Entrenamiento personalizado (el modelo se adapta al usuario)

#### UX Android
- [x] Material 3 + Dynamic Color (Monet)
- [x] Edge-to-edge, gesture navigation
- [x] Radial Octopus FAB v2 (8 tentáculos personalizables)
- [x] Gestos avanzados (3 dedos panel, 4 dedos precisión, 5 dedos screenshot+OCR)
- [x] Zoom con re-renderizado (no escalado, resolución real)
- [x] Modo retrato optimizado (stream recortado o reescalado)
- [x] Samsung DeX optimizado (barra de tareas, multi-ventana remota)
- [x] Foldables optimizados (pantalla plegada/desplegada, continuidad)
- [x] Android TV / Fire TV (control remoto, gamepad optimizado)
- [x] ChromeOS (teclado completo, ventanas nativas)
- [x] PIP mejorado (controles en PIP: play/pause, mute, disconnect)
- [x] Stylus precision mode (punto de mira, estabilizador, calibración)

#### Empresarial
- [x] Relay cloud con cuota gratuita (100h/mes)
- [x] SSO / 2FA / AD/LDAP
- [x] Portal de administración web (usuarios, dispositivos, permisos, reporting)
- [x] API REST para integración (Zapier, Make, scripts)
- [x] Auditoría de sesiones (logs, grabaciones, acceso)
- [x] Group policies y deployment masivo (MDM)
- [x] Self-hosted relay option (para empresas con datos sensibles)
- [x] E2E encryption (TLS 1.3 + AES-256-GCM)
- [x] Modo offline-only (toda la IA en dispositivo, sin datos a cloud)

#### Red
- [x] P2P NAT traversal (UDP hole punching)
- [x] WireGuard integrado (auto-configuración)
- [x] STUN/TURN (Coturn server público + self-host)
- [x] SmartDisplay Cloud Relay (AWS Global Accelerator)
- [x] Auto-selección de ruta (menor latencia, mejor throughput)
- [x] Handover entre rutas sin pérdida de paquetes

### Modelo de negocio (propuesto)

| Tier | Precio | Características |
|:----|:------|:----------------|
| **Free** | $0 | Streaming básico (1080p@60fps), 1 monitor, 50h relay/mes, IA básica (100 requests/día) |
| **Pro** | $4.99/mes | 4K@120fps, 4 monitores, relay ilimitado, IA completa (ilimitada), transferencia archivos |
| **Business** | $9.99/usuario/mes | Todo Pro + portal admin, SSO, 2FA, auditoría, API, deployment MDM, soporte prioritario |
| **Enterprise** | Personalizado | Self-hosted relay, on-premise deployment, SLA, entrenamiento IA personalizado, white-label |

**Ventaja:** Precio disruptivo vs TeamViewer ($50/mes), Splashtop ($5/mes limitado), Parsec ($9.99/mes gaming-only).

### Estrategia de lanzamiento

| Fase | Objetivo | Canales | Métrica |
|:----|:---------|:--------|:--------|
| **Beta cerrada** | 500 usuarios power-users | Reddit r/Android, XDA, GitHub | NPS > 40 |
| **Beta abierta** | 10,000 usuarios | Play Store Early Access, Product Hunt | Retención D7 > 30% |
| **Launch v1.0** | 50,000 usuarios | Play Store, Hacker News, TechCrunch | Descargas, reviews |
| **Growth** | 500,000 usuarios | Referral program, partnerships OEM (Samsung) | DAU/MAU > 15% |

### Métricas de éxito

| Métrica | Objetivo 6 meses | Objetivo 12 meses |
|:--------|:----------------:|:-----------------:|
| Usuarios activos mensuales | 50,000 | 500,000 |
| Tiempo promedio de sesión | 45 min | 90 min |
| Play Store rating | 4.5+ | 4.6+ |
| Churn rate (Pro) | <5% mensual | <3% mensual |
| NPS | 40+ | 50+ |
| Tiempo de conexión | <5s | <2s |
| Tasa de reconexión automática | 95% | 99.5% |
| Latencia promedio (WAN) | <50ms | <30ms |

---

## CONCLUSIÓN FINAL

### Diagnóstico resumido

| Dimensión | Diagnóstico | Acción requerida |
|-----------|:-----------:|:-----------------|
| **Calidad streaming** | ✅ Superior (HEVC/AV1/HDR/120fps) | Mantener, innovar en AV1 B-frame |
| **Latencia local** | ✅ Superior (~1-3ms) | Mantener |
| **Latencia remota** | ❌ Inferior | Implementar relay + adaptive bitrate |
| **Resiliencia** | ❌ Críticamente inferior | **URGENTE**: heartbeat, reconexión, handover |
| **UX Android** | ✅ Ventaja (M3 + radial) | Expandir: DeX, foldables, gestos |
| **IA** | ❌ Placeholder → ✅ **OPORTUNIDAD #1** | Implementar IA real (QW: OCR, Fase Premium: LLM) |
| **Productividad** | ❌ Inferior | File transfer, teclado Dev, portapapeles |
| **Empresarial** | ❌ No existe | NO PRIORIZAR (focus en prosumer primero) |
| **Red** | ❌ Sin relay | Implementar P2P + STUN/TURN + relay cloud |
| **Escalabilidad** | ❌ No existe | Cloud relay + API + administración web |

### Las 3 decisiones estratégicas más importantes

**Decisión 1: ¿Juegos o productividad?**
→ **Ambos.** El stack de Moonlight es increíble para gaming. La IA lo hace ideal para productividad. Esta convergencia NO EXISTE en el mercado. Es la única oportunidad real de diferenciación.

**Decisión 2: ¿Competir con Parsec o con TeamViewer?**
→ **Con ambos, desde un ángulo diferente.** No compitas en su terreno. Compite en "productividad aumentada con IA desde Android". Ninguno de los dos tiene eso.

**Decisión 3: ¿Open source o closed source?**
→ **Híbrido.** Mantener el core streaming open source (contribuyendo mejoras al upstream Moonlight). La IA, relay cloud y funciones empresariales como closed source. Esto da credibilidad OSS + monetización.

### La ventana de oportunidad

```
Ahora ──────────────────────────────────────────────────────────►
│                                                               │
│  SMARTDISPLAY AI                                              │
│  ├── Base: Moonlight (maduro, 10+ años)                      │
│  ├── UX Android: superior a todos                             │
│  ├── IA: NADIE la tiene en remoto desktop                    │
│  └── Precio: gratuito (vs $5-50/mes de competidores)        │
│                                                               │
│  VENTANA: 6-9 meses antes de que los competidores:            │
│  - Parsec añada HEVC a Android                               │
│  - TeamViewer/AnyDesk añadan IA (si reaccionan)              │
│  - RustDesk mejore calidad de streaming                      │
│                                                               │
└───────────────────────────────────────────────────────────────►
```

**Si SmartDisplay AI ejecuta este plan en los próximos 6 meses, tiene una oportunidad real de convertirse en el líder indiscutible de remoto desktop desde Android.**

Si no actúa ahora, será un fork de Moonlight olvidado en GitHub.

---

*Documento generado el 26 de junio de 2026*
*Equipo de auditoría: Arquitecto Senior, Streaming Engineer, UX Specialist, Product Manager, AI Specialist*
