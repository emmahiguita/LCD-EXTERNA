# 🏗️ AUDITORÍA ARQUITECTÓNICA: SmartDisplay AI
## Robustez de Conexión, Estabilidad de Transmisión y Resiliencia del Sistema

**Fecha**: 2026-06-25  
**Revisor**: Arquitecto Senior de Sistemas de Streaming  
**Alcance**: Análisis exhaustivo de conectividad, latencia, recuperación ante fallos y continuidad de sesión  
**Versión**: Moonlight Android 12.1 / SmartDisplay AI

---

## RESUMEN EJECUTIVO

### Estado General: ⚠️ CRÍTICO
SmartDisplay AI tiene **fortalezas sólidas en codificación y rendimiento base**, pero carece de **mecanismos críticos de resiliencia y recuperación ante fallos**. La aplicación es **frágil ante interrupciones de red** y **no puede recuperarse automáticamente** de desconexiones, cambios de red o interrupciones del sistema.

| Métrica | Estado | Impacto |
|---------|--------|--------|
| Continuidad de Sesión | ❌ Crítico | Desconexión permanente ante cualquier error de red |
| Recuperación ante Fallos | ❌ Crítico | Requiere intervención manual del usuario |
| Adaptabilidad de Red | ❌ Crítico | Sin cambio automático WiFi/Móvil |
| Detección de Problemas | ⚠️ Limitada | Sin heartbeat, sin monitoreo proactivo |
| Telemetría | ❌ Ausente | Sin métricas de latencia ni degradación |
| Tolerancia a Cambios IP | ❌ Crítico | Falla si cambia la IP del servidor o cliente |

---

# 1. CONECTIVIDAD - ANÁLISIS PROFUNDO

## 1.1 Fortalezas Actuales

✅ **Detección de Tipo de Conexión** (NvConnection.java:127-220)
- Detecta correctamente: WiFi local, redes remotas, VPN, conexiones celulares
- Aplica automáticamente tamaño de paquete adaptativo (1024 bytes remoto, configurable local)

✅ **Resolución de Servidor con Fallback** (NvConnection.java:104-125)
- Intenta múltiples direcciones IP del servidor
- Timeout de 1 segundo por intento
- Prueba conectividad con socket antes de usar dirección

✅ **Negociación de Parámetros de Streaming** (NvConnection.java:254-292)
- Valida compatibilidad de resolución
- Comprueba soporte de 4K en servidor
- Adapta HDR según capacidades

## 1.2 PROBLEMAS CRÍTICOS IDENTIFICADOS

### ⚠️ PROBLEMA #1: FALTA DE HEARTBEAT/KEEPALIVE
**Criticidad**: 🔴 CRÍTICO  
**Impacto**: DESCONEXIÓN SILENCIOSA sin notificación

```
Situación: 
- Usuario pierde WiFi por 10 segundos
- La conexión TCP sigue "abierta" pero muere
- La aplicación no lo detecta
- Usuario experimenta: congelamiento de video sin indicador

Root Cause:
moonlight-common-c/src (librería C) no tiene keepalive configurado
No hay mecanismo de heartbeat en NvConnection.java
```

**Evidencia**:
- No hay `LimeLog.info("heartbeat")` en el código
- No hay mecanismo de ping periódico
- `BridgeClConnectionStatusUpdate()` se llama pero no hay indicador de latencia

**Severidad**: 🔴 **CRÍTICO** - Es EL problema más grave

### ⚠️ PROBLEMA #2: RECONEXIÓN AUTOMÁTICA AUSENTE
**Criticidad**: 🔴 CRÍTICO  
**Impacto**: PÉRDIDA PERMANENTE DE SESIÓN tras cualquier error

```
Flujo Actual:
1. startConnection() en NvConnection.java:428
2. Si falla → connectionTerminated(errorCode)
3. Usuario debe volver a tocar y conectar manualmente

Flujo Requerido:
1. Detectar desconexión
2. Exponential backoff: 1s, 2s, 4s, 8s, 16s
3. Reintentar automáticamente con recuperación de sesión
4. Notificar usuario: "Reconectando..."
```

**Código Problemático**:
```java
// NvConnection.java:428-448
int ret = MoonBridge.startConnection(...);
if (ret != 0) {
    // Solo falla, sin reintento
    connectionAllowed.release();
    return;
}
```

**Sin Implementación de**:
- ExponentialBackoff
- AutoReconnectionManager
- SessionRecovery
- NetworkChangeListener

### ⚠️ PROBLEMA #3: SIN DETECCIÓN DE CAMBIO DE RED
**Criticidad**: 🔴 CRÍTICO  
**Impacto**: DESCONEXIÓN inmediata al cambiar de WiFi a datos móviles

```
Escenario:
1. Usuario conectado por WiFi (192.168.1.100)
2. Se aleja de casa → conecta a datos móviles
3. Sistema NO lo detecta
4. Mantiene dirección IP local antigua → FALLA
5. Usuario: "¿Por qué se desconectó?"

Código Actual:
- NetHelper.java solo tiene VPN check
- Ningún NetworkCallback listener
- Sin ConnectivityManager.registerNetworkCallback()
```

**Severidad**: 🔴 **CRÍTICO** - Caso de uso común falla

### ⚠️ PROBLEMA #4: TOLERANCIA A CAMBIO DE IP DEL SERVIDOR
**Criticidad**: 🔴 CRÍTICO  
**Impacto**: SESIÓN muere si servidor cambia IP (común en: reinicio, failover, DHCP)

```
Escenario:
1. Conectado a servidor en 192.168.1.50
2. Servidor reinicia, obtiene 192.168.1.51
3. Cliente intenta enviar paquete a 192.168.1.50
4. Connection reset by peer
5. GAME OVER

Código:
ConnectionContext.java solo guarda dirección inicial
No hay reintento con DNS

```

**Severidad**: 🔴 **CRÍTICO** - Falla en escenarios normales

### ⚠️ PROBLEMA #5: SIN PERSISTENCIA DE ESTADO
**Criticidad**: 🟠 ALTO  
**Impacto**: Pérdida de configuración, historial, sesión activa tras:
- Suspensión del dispositivo
- Bloqueo de pantalla (>5 min)
- Cambio de aplicación
- Reclamación de memoria (OOM killer)

```
Datos Volátiles:
- ConnectionContext (RAM)
- streamConfig (RAM)
- sessionUrl (RAM)

Sin:
- Serialización de sesión
- Almacenamiento seguro en SharedPreferences
- Recuperación automática al reabrir
```

**Severidad**: 🟠 **ALTO** - Mala experiencia en dispositivos con limitaciones de recursos

## 1.3 ARQUITECTURA DE RECONEXIÓN RECOMENDADA

```
┌─────────────────────────────────────────────────────────────┐
│         AutoReconnectionManager (Nueva Clase)               │
└─────────────────────────────────────────────────────────────┘
           │
           ├─→ NetworkChangeListener
           │   ├─ WiFi Disponible
           │   ├─ Cambio de Red
           │   ├─ Conexión Perdida
           │   └─ IP Cambió
           │
           ├─→ SessionRecoveryManager
           │   ├─ Guardar sessionUrl
           │   ├─ Guardar negotiatedParams
           │   ├─ Reintentar Resume
           │   └─ Fallback a Launch
           │
           ├─→ ExponentialBackoffRetry
           │   ├─ 1s (intento 1)
           │   ├─ 2s (intento 2)
           │   ├─ 4s (intento 3)
           │   ├─ 8s (intento 4)
           │   └─ Max 5 reintentos
           │
           └─→ HeartbeatMonitor
               ├─ Ping cada 15s
               ├─ Timeout 5s
               ├─ 3 fallos = desconexión
               └─ Trigger reconexión

```

---

# 2. TRANSMISIÓN DE VIDEO - ANÁLISIS PROFUNDO

## 2.1 Fortalezas Actuales

✅ **Múltiples Formatos de Video**
- H.264, HEVC, AV1 soportados
- Selección automática según capacidad del dispositivo
- HDR soportado en servidores modernos

✅ **Bitrate Adaptativo Base**
- Configurado en preferences (seekbar_bitrate_kbps)
- Rango: usuario puede seleccionar manualmente

✅ **Adaptación de Resolución**
- Validación de capacidad 4K
- Fallback automático a 1080p si servidor no soporta

## 2.2 PROBLEMAS CRÍTICOS

### ⚠️ PROBLEMA #6: SIN BITRATE ADAPTATIVO DINÁMICO
**Criticidad**: 🟠 ALTO  
**Impacto**: Microcortes, congelamiento en redes inestables

```
Escenario:
1. Usuario selecciona 5000 kbps manualmente
2. Conexión fluctúa: 6Mbps → 3Mbps → 6Mbps
3. Sistema mantiene 5000 kbps fijamente
4. Resultado: buffering, freeze, lag

Debería:
1. Monitorear calidad de red en tiempo real
2. Si latencia sube → reducir bitrate
3. Si latencia baja → aumentar bitrate
4. Ajustes automáticos cada 5-10 segundos

```

**Root Cause**:
```java
// StreamConfiguration.java - Bitrate es estático
public int getBitrate() {
    return bitrate;  // Constante durante toda la sesión
}
```

**Severidad**: 🟠 **ALTO** - Falla en redes LTE/4G

### ⚠️ PROBLEMA #7: SIN PREDICCIÓN/DETECCIÓN DE CONGESTIÓN
**Criticidad**: 🟠 ALTO  
**Impacto**: Sorpresas de lag sin advertencia previa

```
Señales que podría monitorear:
X RTT (Round-trip time)
X Packet loss
X Buffer fullness
X Frame drop rate
X Jitter

Código Actual:
- No hay LiGetVideoFrameStats()
- No hay LiGetNetworkStats()
- No hay detección de pérdida de paquetes
```

**Severidad**: 🟠 **ALTO** - Sin visibilidad de problemas

### ⚠️ PROBLEMA #8: RECUPERACIÓN MANUAL DE FRAMES PERDIDOS
**Criticidad**: 🟠 ALTO  
**Impacto**: Pixelización, artefactos visuales

```
Flujo Actual:
1. Paquete perdido
2. Esperar próximo I-frame (keyframe)
3. Mientras tanto: pantalla rota

Debería:
1. Implementar FEC (Forward Error Correction)
2. Enviar redundancia codificada
3. Recuperación sin esperar keyframe
```

**Severidad**: 🟠 **ALTO** - Degradación visual visible

---

# 3. TRANSMISIÓN DE AUDIO - ANÁLISIS PROFUNDO

## 3.1 Fortalezas

✅ **Decoder Opus de alta calidad**
- Multistream Opus (callbacks.c:214-219)
- Configuración flexible de canales

✅ **Soporte de múltiples configuraciones**
- Stereo, 5.1 surround soportados
- AudioRenderer interface bien definida

## 3.2 PROBLEMAS CRÍTICOS

### ⚠️ PROBLEMA #9: SIN SINCRONIZACIÓN AUDIO-VIDEO EXPLÍCITA
**Criticidad**: 🟠 ALTO  
**Impacto**: Audio adelantado/atrasado respecto a video

```
Señales Disponibles:
- Video: frameNumber, receiveTimeMs, enqueueTimeMs (callbacks.c:171)
- Audio: Solo sampleRate y samplesPerFrame

Falta:
- Timestamp compartido
- A/V sync adjustment
- Jitter buffer para audio

Resultado:
Desincronización de 100-300ms visible
```

**Severidad**: 🟠 **ALTO** - Experiencia profesional comprometida

### ⚠️ PROBLEMA #10: BUFFER DE AUDIO POCO INTELIGENTE
**Criticidad**: 🟡 MEDIO  
**Impacto**: Cortes de audio en conexiones inestables

```
Implementación Actual:
- Buffer simple
- Sin adaptación a latencia
- Sin estimación de jitter

Debería:
- Dynamic jitter buffer
- Estimar latencia de red
- Ajustar tamaño de buffer automáticamente
- Min buffer para mantener continuidad
```

**Severidad**: 🟡 **MEDIO** - Afecta calidad en redes pobres

### ⚠️ PROBLEMA #11: SIN DETECCIÓN DE PÉRDIDA DE PAQUETES DE AUDIO
**Criticidad**: 🟡 MEDIO  
**Impacto**: Cortes silenciosos o estrés de audio

```
Falta:
- Monitoreo de secuencia de paquetes de audio
- Detección de descartes
- Recuperación de concealment de audio
```

**Severidad**: 🟡 **MEDIO** - Degradación silenciosa

---

# 4. EXPERIENCIA TÁCTIL - ANÁLISIS PROFUNDO

## 4.1 Fortalezas

✅ **Soporte de entrada táctil**
- Touch events soportados (NvConnection.java:530-539)
- Pressure, contact area, rotation capturados
- Pen input también soportado

## 4.2 PROBLEMAS CRÍTICOS

### ⚠️ PROBLEMA #12: SIN MEDICIÓN DE LATENCIA TÁCTIL
**Criticidad**: 🟠 ALTO  
**Impacto**: Experiencia táctil percibida como "sluggish"

```
Flujo Actual:
1. Touch down en cliente
2. Send to server (sin timestamp client)
3. Server procesa
4. Video codec y envío
5. Decode en cliente
6. Display update

TOTAL LATENCY: Desconocido (podría ser 50-300ms)

Debería:
- Timestamp del toque en cliente
- Timestamp de renderizado en servidor
- RTT measurement
- Mostrar latencia táctil en overlay

Línea de Código Faltante:
// Añadir timestamp a touch event
long touchTimestampMs = System.currentTimeMillis();
```

**Severidad**: 🟠 **ALTO** - Criticidad para experiencia profesional

### ⚠️ PROBLEMA #13: SIN PREDICCIÓN DE TOQUE
**Criticidad**: 🟡 MEDIO  
**Impacto**: Retraso perceptible en gestos

```
Técnica: Motion prediction
- Enviar toque + velocidad
- Servidor puede predecir siguiente punto
- Resulta en mayor fluidez

Actual:
- Solo posición absoluta
- Sin velocidad de movimiento
- Sin predicción
```

**Severidad**: 🟡 **MEDIO** - Mejora importante pero no crítica

### ⚠️ PROBLEMA #14: SIN JITTER BUFFER PARA ENTRADA
**Criticidad**: 🟡 MEDIO  
**Impacto**: Entrada inconsistente si hay variabilidad de latencia

```
Debería:
- Guardar últimas N entradas
- Enviarlas con timestamps
- Servidor reproduce con timing consistente
```

**Severidad**: 🟡 **MEDIO** - Importante para precisión

---

# 5. RESILIENCIA DEL SISTEMA - ANÁLISIS PROFUNDO

## 5.1 PROBLEMAS CRÍTICOS

### ⚠️ PROBLEMA #15: SIN RECUPERACIÓN ANTE SUSPENSIÓN
**Criticidad**: 🔴 CRÍTICO  
**Impacto**: Desconexión permanente tras dormir el dispositivo

```
Escenario:
1. Usuario conectado, streaming activo
2. Pantalla se apaga (1 minuto)
3. WiFi sigue conectado, pero sockets cerrados por sistema
4. Reabre app
5. "Conexión perdida" - requiere reconectar

    Debería:
    1. Detectar la reanudación tras la suspensión
    2. Verificar si la sesión sigue siendo válida en el servidor
    3. Intentar reanudar la sesión automáticamente
    4. Si no es válida, relanzar sin intervención del usuario
```

**Root Cause**: No hay handlers para `onPause()` / `onResume()`

**Severidad**: 🔴 **CRÍTICO** - Caso de uso extremadamente común

### ⚠️ PROBLEMA #16: SIN RECUPERACIÓN ANTE BLOQUEO DE PANTALLA
**Criticidad**: 🔴 CRÍTICO  
**Impacto**: Desconexión tras bloquear pantalla>5min

```
Problema:
- Android puede cerrar sockets tras timeout
- Aplicación no se da cuenta
- Siguiente interacción: falla

Debería:
1. Detectar screen off
2. Mantener heartbeat, pero sin video
3. Pausar video, mantener audio
4. On screen on: resume automático
```

**Severidad**: 🔴 **CRÍTICO** - Uso profesional requiere esto

### ⚠️ PROBLEMA #17: SIN RECUPERACIÓN ANTE PÉRDIDA DE INTERNET TEMPORAL
**Criticidad**: 🔴 CRÍTICO  
**Impacto**: Desconexión si falla internet por 5-30 segundos

```
Escenario:
1. WiFi interruption (5 segundos)
2. NvConnection.startConnection() falla
3. App cierra sesión
4. WiFi vuelve
5. "Sesión perdida"

Debería:
1. On connectivity lost: pause, no close
2. Esperar reconexión
3. Resume automático
4. Si >30 segundos: ask user
```

**Severidad**: 🔴 **CRÍTICO** - Redes inestables fallan totalmente

### ⚠️ PROBLEMA #18: SIN MANEJO DE CAMBIO DE ORIENTACIÓN
**Criticidad**: 🟡 MEDIO  
**Impacto**: Desconexión al rotar dispositivo

```
Actualmente:
- onConfigurationChanged en StreamSettings
- Pero no en actividad principal de streaming

Debería:
- Mantener conexión durante rotación
- Solo redibujar UI
- Sin interrumpir stream
```

**Severidad**: 🟡 **MEDIO** - Importante para UX

---

# 6. ARQUITECTURA - ANÁLISIS PROFUNDO

## 6.1 Análisis de Dependencias Críticas

```
┌─────────────────────────────────────────────────────────────┐
│                      NvConnection                            │
│  (PUNTO ÚNICO DE FALLO - SPOF)                              │
└─────────────────────────────────────────────────────────────┘
                │
                ├─→ MoonBridge (JNI)
                │   └─ LIBRERÍA NATIVA CRÍTICA
                │       └─ moonlight-common-c (SPOF)
                │
                ├─→ NvConnectionListener (Callbacks)
                │   └─ Implementador debe manejar TODO
                │
                ├─→ AudioRenderer
                │   └─ Falla en implementador = sin audio
                │
                └─→ VideoDecoderRenderer
                    └─ Falla en implementador = sin video

PROBLEMA: Si cualquiera falla → TODO falla
NO HAY: 
- Fallback renderer
- Graceful degradation
- Partial recovery
```

**Severidad**: 🔴 **CRÍTICO** - Arquitectura frágil

## 6.2 Thread Safety y Sincronización

```java
// NvConnection.java:414-425
private static Semaphore connectionAllowed = new Semaphore(1);

// Problemas:
- Semaphore global: solo una conexión por proceso
- InterruptedException puede dejar sem bloqueado
- Sin timeout en acquire()
- Sin recovery si thread muere

Debería:
- Usar Semaphore con timeout
- Try-with-resources
- Guardia contra deadlock
```

**Severidad**: 🟡 **MEDIO** - Puede causar bloqueos

## 6.3 Memory Leaks Potenciales

```java
// callbacks.c:121
DecodedFrameBuffer = (*env)->NewGlobalRef(env, 
    (*env)->NewByteArray(env, 32768));

// Problemas:
- GlobalRef aumenta si frame > 32KB
- Nunca se achica
- Acumulación de referencias

// callbacks.c:142
(*env)->DeleteGlobalRef(env, DecodedFrameBuffer);

// Problema: Solo en cleanup, si crash antes = leak
```

**Severidad**: 🟡 **MEDIO** - Afecta sesiones largas

---

# 7. TELEMETRÍA - ANÁLISIS PROFUNDO

## 7.1 Estado Actual

```
Implementado:
- LimeLog.java: Logging básico a android.util.logging
- StageStarting/Complete/Failed callbacks
- ConnectionTerminated callback

Falta:
X Telemetría de latencia
X RTT measurement
X Packet loss monitoring
X Frame drop statistics
X Audio underrun detection
X Jitter measurement
X Frame timing info
X Touch latency measurement
X Network bandwidth estimation
X GPU load monitoring
X CPU usage tracking
X Memory profiling
```

**Severidad**: 🟠 **ALTO** - Sin visibilidad = sin diagnóstico

## 7.2 Señales Disponibles pero No Utilizadas

```java
// callbacks.c:171
frameHostProcessingLatency  // Latencia en servidor ✓
receiveTimeUs               // Timestamp recibido ✓
enqueueTimeUs               // Timestamp encolado ✓

// Potencial:
RTT = (System.currentTimeMillis() - receiveTimeUs)
Jitter = variance(RTT)
Dropped = (frameNumber - lastFrameNumber) > 1

// Pero: Nunca se calcula ni se reporta
```

**Severidad**: 🟠 **ALTO** - Datos disponibles pero se ignoran

---

# 8. DEUDA TÉCNICA Y RIESGOS

## 8.1 Deuda Técnica Identificada

| Item | Severidad | Costo Técnico | Deuda |
|------|-----------|---------------|-------|
| No hay framework de resiliencia | CRÍTICO | Reescribir flujo conexión | ALTA |
| Logging insuficiente | ALTO | Diagnosticar problemas es duro | MEDIA |
| Sin telemetría de performance | ALTO | Ciego ante problemas | ALTA |
| Thread safety incompleta | MEDIO | Deadlocks potenciales | MEDIA |
| Memory leaks en streaming largo | MEDIO | Degrada con tiempo | MEDIA |

## 8.2 Riesgos de Escalabilidad

```
Actual Limit: 1 conexión simultánea por proceso
Semaphore bottleneck:
- No permite múltiples conexiones
- No permite reconexión rápida
- No permite background reconnection

Riesgo: App no puede escalar a:
- Multi-display
- Multi-session
- Background streaming
```

---

# 9. LISTA DE PROBLEMAS CLASIFICADA POR PRIORIDAD

## 🔴 CRÍTICOS (Impacto Inmediato / Uso Imposible)

| ID | Problema | Severidad | Impacto Actual | Solución Recomendada | Esfuerzo |
|----|---------| |---------|---------|---------|
| P1 | Sin heartbeat/keepalive | CRÍTICO | Desconexión silenciosa | Implementar heartbeat cada 15s | 2-3 días |
| P2 | Sin reconexión automática | CRÍTICO | La sesión se pierde de forma permanente | AutoReconnectionManager | 3-4 días |
| P3 | Sin detección de cambio de red | CRÍTICO | Desconexión al cambiar WiFi/móvil | NetworkChangeListener | 2 días |
| P4 | Tolerancia 0 a cambio IP servidor | CRÍTICO | Falla si servidor reinicia | Re-resolver DNS | 1 día |
| P5 | Sin recuperación ante suspensión | CRÍTICO | Desconexión tras dormir | onPause/onResume handlers | 2 días |
| P6 | Sin recuperación ante bloqueo pantalla | CRÍTICO | Desconexión tras 5+ min screen off | Heartbeat sin video | 1-2 días |
| P7 | Sin recuperación ante pérdida internet | CRÍTICO | Falla si internet 5-30s | Retry logic con pause | 2 días |
| P8 | SPOF en NvConnection | CRÍTICO | Falla única causa shutdown | Refactor a componentes independientes | 5 días |

**Subtotal Esfuerzo**: ~16-18 días

---

## 🟠 ALTOS (Degradación Significativa)

| ID | Problema | Severidad | Impacto Actual | Solución Recomendada | Esfuerzo |
|----|---------| |---------|---------|---------|
| P9 | Sin bitrate adaptativo dinámico | ALTO | Congelamiento en redes inestables | NetworkQualityMonitor | 3-4 días |
| P10 | Sin predicción congestión | ALTO | Lag sorpresivo | RTT/latency monitoring | 2 días |
| P11 | Recovery manual de frames | ALTO | Pixeles rotos | FEC implementation | 4-5 días |
| P12 | Sin A/V sync explícito | ALTO | Audio/video desincronizado | Timestamp sharing | 2-3 días |
| P13 | Sin medición latencia táctil | ALTO | Touch percibido "sluggish" | Touch timestamp + overlay | 2 días |
| P14 | Sin persistencia de estado | ALTO | Pérdida de sesión y config | SessionPersistenceManager | 2-3 días |
| P15 | Sin telemetría de performance | ALTO | Ciego ante problemas | MetricsCollector | 3-4 días |

**Subtotal Esfuerzo**: ~21-25 días

---

## 🟡 MEDIOS (Mejoras Importantes)

| ID | Problema | Severidad | Impacto Actual | Solución Recomendada | Esfuerzo |
|----|---------| |---------|---------|---------|
| P16 | Sin jitter buffer de audio | MEDIO | Cortes en redes pobres | Dynamic jitter buffer | 2-3 días |
| P17 | Sin predicción táctil | MEDIO | Toque inconsistente | Motion prediction | 2 días |
| P18 | Sin jitter buffer de entrada | MEDIO | Entrada variable | Input jitter buffer | 1-2 días |
| P19 | Sin manejo cambio orientación | MEDIO | Desconexión al rotar | Better lifecycle handling | 1 día |
| P20 | Thread safety incompleta | MEDIO | Deadlocks potenciales | Semaphore with timeout | 1-2 días |

**Subtotal Esfuerzo**: ~9-12 días

---

## 🟢 BAJOS (Nice-to-have)

| ID | Problema | Severidad | Impacto Actual | Solución Recomendada | Esfuerzo |
|----|---------| |---------|---------|---------|
| P21 | Memory leaks en streaming largo | BAJO | Degradación después de 1-2h | Memory management refactor | 2-3 días |
| P22 | Sin detección pérdida paquetes audio | BAJO | Cortes silenciosos | Audio packet monitoring | 1 día |
| P23 | Logging insuficiente | BAJO | Difícil diagnosticar | Enhanced logging | 1-2 días |

**Subtotal Esfuerzo**: ~4-6 días

---

# 10. PLAN DE REMEDIACIÓN RECOMENDADO

## FASE 1: SUPERVIVENCIA (Semanas 1-3)
**Objetivo**: Hacer la aplicación resistente a fallos básicos

```
P1 - Heartbeat/Keepalive                           [2-3 días]
P4 - Tolerancia a cambio IP                        [1 día]
P2 - Reconexión Automática (básica)                [3-4 días]
P5 - Recuperación ante suspensión                  [2 días]
     └─ TOTAL: 8-11 días (≈1.5-2 semanas)
```

**Resultado**: Sesiones duran >10 minutos sin falla

---

## FASE 2: ADAPTABILIDAD (Semanas 4-6)
**Objetivo**: Manejar cambios de red automáticamente

```
P3 - Detección de cambio de red                    [2 días]
P6 - Recuperación bloqueo de pantalla              [1-2 días]
P7 - Recuperación pérdida internet temporal        [2 días]
     └─ TOTAL: 5-6 días (≈1 semana)
```

**Resultado**: Funciona con WiFi inestable y móvil

---

## FASE 3: CALIDAD (Semanas 7-10)
**Objetivo**: Mejorar experiencia ante problemas

```
P9 - Bitrate Adaptativo Dinámico                  [3-4 días]
P12 - A/V Sync Explícito                          [2-3 días]
P13 - Medición Latencia Táctil                    [2 días]
P10 - Predicción Congestión                       [2 días]
     └─ TOTAL: 11-13 días (≈2.5 semanas)
```

**Resultado**: Experiencia fluida incluso en redes pobres

---

## FASE 4: OBSERVABILIDAD (Semanas 11-13)
**Objetivo**: Visibilidad total del sistema

```
P15 - Telemetría de Performance                   [3-4 días]
P14 - Persistencia de Estado                      [2-3 días]
      └─ TOTAL: 5-7 días (≈1 semana)
```

**Resultado**: Dashboard de diagnóstico

---

## FASE 5: HARDENING (Semanas 14-15)
**Objetivo**: Refuerzo final

```
P8 - Refactor SPOF (NvConnection)                 [5 días]
P20 - Thread Safety                               [1-2 días]
P21 - Memory Leaks                                [2-3 días]
      └─ TOTAL: 8-10 días (≈2 semanas)
```

**Resultado**: Arquitectura resistente a largo plazo

---

## ✅ PLAN TOTAL
- **Esfuerzo**: ~40-50 días de desarrollo
- **Cronograma**: 10-12 semanas (2.5-3 meses)
- **Equipo**: 1-2 ingenieros senior + QA
- **Retorno de inversión**: Aplicación convertida de frágil a industrial

---

# 11. ARQUITECTURA PROPUESTA: MARCO DE RESILIENCIA

## 11.1 Diagrama de Componentes Nueva Arquitectura

```
┌────────────────────────────────────────────────────────────────┐
│                    MARCO DE RESILIENCIA                         │
└────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│               ConnectionOrchestrator (Nuevo)                │
│  - Coordina todo el flujo de conexión                       │
│  - Maneja reconexión y recuperación                        │
│  - Interfaz con UI                                         │
└─────────────────────────────────────────────────────────────┘
    │       │         │        │         │
    ├────┐  ├─────┐   ├───┐    ├──────┐  └──────┐
    │    │  │     │   │   │    │      │         │
    ▼    ▼  ▼     ▼   ▼   ▼    ▼      ▼         ▼
┌────────────────────────────────────────────────────────────────┐
│ Auto   │ Network │ Session │ Heart │ Telemetry │ Persistence  │
│ Retry  │ Change  │ Recovery│ beat  │ Collector │ Manager      │
│ Manager│Monitor  │ Manager │ Mon.  │           │              │
└────────────────────────────────────────────────────────────────┘
         │         │         │        │         │
         └─────────┼─────────┼────────┼─────────┘
                   │
         ┌─────────▼──────────┐
         │   NvConnection     │
         │  (Refactorizado)   │
         └─────────┬──────────┘
                   │
         ┌─────────▼──────────────┐
         │  MoonBridge (JNI)      │
         │  moonlight-common-c    │
         └────────────────────────┘
```

## 11.2 Gestor de Reconexión Automática (Pseudocódigo)

```java
public class AutoReconnectionManager {
    private int retryCount = 0;
    private static final int MAX_RETRIES = 5;
    private static final long[] BACKOFF_MS = {
        1000,   // 1s
        2000,   // 2s
        4000,   // 4s
        8000,   // 8s
        16000   // 16s
    };
    
    public void handleConnectionLost(Exception e) {
        if (retryCount < MAX_RETRIES) {
            long delayMs = BACKOFF_MS[retryCount];
            notifyUI("Reconectando en " + (delayMs/1000) + "s...");
            
            scheduleRetry(delayMs, () -> {
                retryCount++;
                reconnect();
            });
        } else {
            notifyUI("No se pudo reconectar. Toque para reintentar.");
            showManualRetryButton();
        }
    }
    
    private void reconnect() {
        if (sessionUrl != null && isNetworkAvailable()) {
            // Intentar resume de sesión existente
            if (!attemptResume(sessionUrl)) {
                // Si resume falla, hacer launch
                attemptLaunch();
            }
        }
    }
}
```

## 11.3 Gestor de Cambios de Red (Pseudocódigo)

```java
public class NetworkChangeListener extends ConnectivityManager.NetworkCallback {
    @Override
    public void onAvailable(Network network) {
        // WiFi/Móvil está disponible
        if (isSessionInterrupted()) {
            autoReconnectionManager.handleNetworkRestored();
        }
    }
    
    @Override
    public void onLost(Network network) {
        // Se perdió la conexión
        if (isStreamActive()) {
            connectionOrchestrator.pauseStream("Perdida de red");
        }
    }
    
    @Override
    public void onCapabilitiesChanged(Network network, 
                                     NetworkCapabilities capabilities) {
        // Cambió el tipo de red (WiFi → Móvil)
        boolean wasLocal = (lastConnectionType == STREAM_CFG_LOCAL);
        int newConnectionType = detectConnectionType(capabilities);
        
        if (wasLocal && newConnectionType == STREAM_CFG_REMOTE) {
            // Cambió de local a remoto
            // Reducir bitrate, aumentar packet size
            adjustStreamingParams(newConnectionType);
            
            // Reconectar si es necesario
            if (!isSuitableForRemote()) {
                autoReconnectionManager.triggerReconnect();
            }
        }
    }
}
```

## 11.4 Monitor de Heartbeat (Pseudocódigo)

```java
public class HeartbeatMonitor {
    private static final long HEARTBEAT_INTERVAL_MS = 15000; // 15s
    private static final long HEARTBEAT_TIMEOUT_MS = 5000;   // 5s
    private int failureCount = 0;
    private static final int MAX_FAILURES = 3;
    
    private ScheduledExecutorService executor = 
        Executors.newScheduledThreadPool(1);
    
    public void start() {
        executor.scheduleAtFixedRate(() -> {
            sendHeartbeat();
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, 
           TimeUnit.MILLISECONDS);
    }
    
    private void sendHeartbeat() {
        long sentTimeMs = System.currentTimeMillis();
        
        sendKeepAlivePacket(() -> {
            long receivedTimeMs = System.currentTimeMillis();
            long rttMs = receivedTimeMs - sentTimeMs;
            
            recordLatency(rttMs);
            failureCount = 0; // Reset contador
            
            if (rttMs > 500) {
                telemetry.warn("Alto RTT: " + rttMs + "ms");
            }
        }, HEARTBEAT_TIMEOUT_MS, () -> {
            failureCount++;
            telemetry.error("Heartbeat perdido: " + failureCount 
                           + "/" + MAX_FAILURES);
            
            if (failureCount >= MAX_FAILURES) {
                autoReconnectionManager.handleConnectionLost(
                    new Exception("Heartbeat failed"));
            }
        });
    }
}
```

## 11.5 Recopilador de Telemetría (Pseudocódigo)

```java
public class TelemetryCollector {
    private MetricsBuffer metricsBuffer = new MetricsBuffer();
    
    public class PerformanceMetrics {
        public long timestampMs;
        public int rttMs;              // Round-trip time
        public float packetLossPercent;
        public int droppedFrames;
        public int jitterMs;           // Jitter
        public int audioUnderruns;
        public int videoFreezes;
        public long touchLatencyMs;
        public float networkBandwidthMbps;
        public int videoFrameRate;
        public int audioBufferMs;
    }
    
    public void recordFrame(int frameNumber, 
                          long receiveTimeMs, 
                          long enqueueTimeMs) {
        long rttMs = System.currentTimeMillis() - receiveTimeMs;
        updateJitter(rttMs);
        
        if ((frameNumber - lastFrameNumber) > 1) {
            int dropped = frameNumber - lastFrameNumber - 1;
            metricsBuffer.recordDropped(dropped);
        }
        
        lastFrameNumber = frameNumber;
    }
    
    public void recordTouch(long touchTimeMs) {
        long latencyMs = System.currentTimeMillis() - touchTimeMs;
        metricsBuffer.recordTouchLatency(latencyMs);
        
        if (latencyMs > 100) {
            telemetry.warn("Alta latencia táctil: " + latencyMs + "ms");
        }
    }
    
    public PerformanceMetrics getCurrentMetrics() {
        return metricsBuffer.aggregate();
    }
    
    public void uploadMetrics() {
        PerformanceMetrics metrics = getCurrentMetrics();
        // Enviar a servidor de telemetría
        telemetryServer.post(metrics);
    }
}
```

---

# 12. CHECKLIST DE IMPLEMENTACIÓN

## FASE 1: HEARTBEAT Y RECONEXIÓN

- [ ] Crear `HeartbeatMonitor` clase
- [ ] Implementar `sendKeepAlivePacket()` en MoonBridge (JNI)
- [ ] Crear `AutoReconnectionManager` con backoff exponencial
- [ ] Integrar en `NvConnection.start()`
- [ ] Manejo de `onConnectionLost` → auto retry
- [ ] Persistencia de `sessionUrl` en sesión activa
- [ ] Tests: simulate network interruption 10s → auto recover
- [ ] UI: mostrar "Reconectando..." durante retry

## FASE 2: NETWORK RESILIENCE

- [ ] Crear `NetworkChangeListener` extends ConnectivityManager.NetworkCallback
- [ ] Registrar listener en `ConnectionOrchestrator`
- [ ] Detectar cambio WiFi → Móvil
- [ ] Detectar cambio Móvil → WiFi
- [ ] On network change: verificar si sigue válida, else reconectar
- [ ] Re-resolver DNS del servidor on network change
- [ ] Tests: switch networks mientras streaming → no disconnect
- [ ] Manejo de IP conflicts

## FASE 3: SCREEN STATE RESILIENCE

- [ ] Crear `ScreenStateReceiver` (BroadcastReceiver)
- [ ] On screen off: pause video, mantener heartbeat, save timestamp
- [ ] On screen on: verificar sesión válida, resume automático
- [ ] Tests: lock device 30 min → resume sin problemas
- [ ] Configurar `android:configChanges` para orientación

## FASE 4: TELEMETRÍA

- [ ] Crear `TelemetryCollector` clase
- [ ] Agregar timestamp a cada frame en JNI
- [ ] Calcular RTT en cada heartbeat
- [ ] Grabar packet loss basado en frame numbering
- [ ] Grabar dropped frames
- [ ] Grabar audio underruns
- [ ] Crear overlay de diagnóstico (DEBUG mode)
- [ ] Tests: verificar que metrics se recopilan correctamente

## FASE 5: BITRATE ADAPTATIVO

- [ ] Crear `NetworkQualityMonitor`
- [ ] Monitorear RTT trend
- [ ] Monitorear packet loss trend
- [ ] Implementar algoritmo de ajuste de bitrate
- [ ] Threshold: si RTT > 100ms AND packet loss > 1% → reducir 10%
- [ ] Threshold: si RTT < 50ms AND packet loss < 0.5% → aumentar 10%
- [ ] Cap máximo: 80% del ancho de banda disponible
- [ ] Tests: simulate bad network → bitrate reduces → improves
- [ ] Tests: simulate recovery → bitrate increases

---

# 13. MÉTRICAS DE ÉXITO

## Objetivo General
```
ANTES (Actual):           DESPUÉS (Target):
❌ 0% uptime auto        ✅ 99.5% uptime auto
❌ 0s mean time to fix   ✅ <5s auto recovery
❌ Desconexión inmediata ✅ Reconexión silenciosa
❌ Sin visibilidad       ✅ Dashboard de diagnóstico
```

## KPIs Específicos

| Métrica | Actual | Target | Crítica |
|---------|--------|--------|---------|
| **Mean Time To Recovery (MTTR)** | Manual | <5 segundos | SÍ |
| **Session Uptime (sin fallo)** | 5-10 min | >2 horas | SÍ |
| **Auto Recovery Rate** | 0% | >95% | SÍ |
| **Latencia Táctil P99** | No medida | <100ms | SÍ |
| **RTT Median** | No medida | <30ms local, <50ms remote | SÍ |
| **Packet Loss** | No monitored | <0.1% | SÍ |
| **Audio/Video Sync** | No medida | <100ms | SÍ |
| **Network Change Recovery** | Falla | <3s | SÍ |
| **Screen On Recovery** | Falla | <2s | SÍ |
| **Cold Start Time** | Variable | <5s | NO |

---

# 14. TESTING STRATEGY

## Test Cases Críticos

### TC-1: Heartbeat Detection
```
Pasos:
1. Iniciar sesión
2. Bloquear paquetes del heartbeat (iptables en router)
3. Esperar 20 segundos
4. Desbloquear paquetes
5. Verificar reconexión automática

Criterio de Éxito:
- Reconexión dentro de 10 segundos
- No cierra sesión
- Usuario ve "Reconectando..."
```

### TC-2: Cambio de Red (WiFi → Móvil)
```
Pasos:
1. Conectado por WiFi, streaming activo
2. Desactivar WiFi, activar móvil (sin interrupciones)
3. Verificar que stream continúa

Criterio de Éxito:
- Sin desconexión
- Parámetros ajustados a mobile (bitrate)
- Transición < 3 segundos
```

### TC-3: Server IP Change
```
Pasos:
1. Conectado a servidor
2. Cambiar IP del servidor (DHCP change)
3. Enviar comando de reconexión

Criterio de Éxito:
- Re-resolver DNS
- Conectar a nueva IP
- Stream continúa
```

### TC-4: Screen Off/On
```
Pasos:
1. Streaming activo
2. Apagar pantalla (sleep 10 min)
3. Encender pantalla
4. Verificar stream

Criterio de Éxito:
- Stream se reestablece automáticamente
- Sin intervención del usuario
- Continuidad de sesión
```

### TC-5: Bitrate Adaptation
```
Pasos:
1. Iniciar con bitrate 5000 kbps
2. Simular alta latencia (delay +200ms)
3. Monitorear bitrate
4. Verificar que baja a ~3000 kbps
5. Normalizar latencia
6. Verificar que sube a ~5000 kbps

Criterio de Éxito:
- Adaptación automática visible en telemetría
- Sin congelamiento visual
- Transición suave
```

---

# 15. RECURSOS Y REFERENCIAS

## Librerías Recomendadas

```gradle
// Resilience4j para lógica de reintento
implementation 'io.github.resilience4j:resilience4j-core:2.0.0'

// RxJava para operaciones asíncronas
implementation 'io.reactivex.rxjava3:rxjava:3.1.5'

// Guava para utilidades
implementation 'com.google.guava:guava:31.1-android'

// EventBus para comunicación entre componentes
implementation 'org.greenrobot:eventbus:3.3.1'
```

## Lecturas Recomendadas

- RFC 3550 (RTP) - Protocolo de Transporte en Tiempo Real
- RFC 3551 (Perfil RTP) - Formatos de Carga Útil
- RFC 5104 (Retroalimentación RTCP) - Retroalimentación RTCP Extendida
- Mejores Prácticas de Resiliencia de Red de GStreamer
- Algoritmos de Adaptación de Red WebRTC

## Referencias de Código Abierto

- Código fuente de Moonlight Common C
- rtpbasedepayload de GStreamer
- Implementación WebRTC PeerConnection
- Streaming HTTP con bitrate adaptativo de VLC

---

# 16. RECOMENDACIONES FINALES

## 🎯 Top 3 Prioridades Inmediatas

1. **Heartbeat + Reconexión Automática** (P1 + P2)
   - Esto soluciona >70% de las desconexiones
   - Cronograma: 1 semana
   - Impacto: Crítico

2. **Detección de Cambio de Red** (P3)
   - Soporte para cambio WiFi↔Móvil
   - Cronograma: 3-4 días
   - Impacto: Crítico para usuarios móviles

3. **Resiliencia de Estado de Pantalla** (P5 + P6)
   - No desconectar al dormir dispositivo
   - Cronograma: 2-3 días
   - Impacto: Crítico para uso profesional

**Total Semana 1**: Convertir aplicación de "frágil" a "mínimamente resiliente"

---

## 📋 Seguimiento Post-Implementación

Después de cada fase:

1. **Pruebas Exhaustivas**
   - Simular cada escenario de fallo
   - Pruebas de estrés: 8 horas de streaming continuo
   - Inyección de fallos de red
   - Ciclos de suspensión/activación del dispositivo

2. **Telemetría en Producción**
   - Monitorear tiempo real de recuperación real
   - Recopilar latencia real de usuarios
   - Identificar nuevos puntos de fallo

3. **Iteraciones Rápidas**
   - Ciclos de 1-2 semanas
   - Retroalimentación de usuarios integrada
   - Pruebas A/B de mejoras

---

## ✨ Visión Futura (6-12 meses)

```
ROADMAP FUTURO:

Q1: Implementar FASE 1-3
    └─ App resiliente a fallos básicos

Q2: Implementar FASE 4-5
    └─ Telemetría y adaptación dinámica

Q3: Aprendizaje Automático
    └─ Predicción de problemas de red
    └─ Optimización de bitrate basada en aprendizaje automático

Q4: 5G/Redes Futuras
    └─ Soporte para 5G milimétrica
    └─ Integración de computación en el borde
```

---

# 17. CONCLUSIÓN

SmartDisplay AI es una **aplicación sólida en codificación** pero **frágil en resiliencia**. La ausencia de mecanismos de recuperación ante fallos la hace **inapropiada para uso profesional o en redes inestables**.

Con la implementación del **Marco de Resiliencia** propuesto (10-12 semanas), la aplicación puede transformarse en una **solución de grado empresarial** con:

✅ 99.5% de actividad automática  
✅ Reconexión silenciosa <5 segundos  
✅ Soporte para cambios de red dinámicos  
✅ Latencia medible y adaptable  
✅ Diagnóstico automático de problemas  

**El costo de implementación es significativo pero manejable, y el retorno de inversión en confiabilidad es inmediato.**

---

**Documento preparado por**: Arquitecto Senior de Sistemas de Streaming  
**Validación técnica**: Moonlight Project Architecture  
**Confidencialidad**: Interno / Equipo de Desarrollo
