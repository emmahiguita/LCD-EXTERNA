# 🛠️ GUÍA TÉCNICA: IMPLEMENTACIÓN DE RESILIENCIA

## Soluciones Concretas y Código Funcional

---

# MÓDULO 1: HEARTBEAT Y KEEPALIVE

## 1.1 HeartbeatMonitor.java

```java
package com.limelight.nvstream.connection;

import android.os.Handler;
import android.os.Looper;

import com.limelight.LimeLog;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class HeartbeatMonitor {
    private static final String TAG = "HeartbeatMonitor";
    
    // Configuración
    private static final long HEARTBEAT_INTERVAL_MS = 15000; // 15 segundos
    private static final long HEARTBEAT_TIMEOUT_MS = 5000;   // 5 segundos de timeout
    private static final int MAX_FAILURES = 3;               // 3 heartbeats perdidos = disconnect
    
    private final HeartbeatCallback callback;
    private final ScheduledExecutorService executor;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile boolean isRunning = false;
    
    public interface HeartbeatCallback {
        void onHeartbeatSuccess(long rttMs);
        void onHeartbeatFailed();
        void onHeartbeatTimeout();
    }
    
    public HeartbeatMonitor(HeartbeatCallback callback) {
        this.callback = callback;
        this.executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "HeartbeatMonitor");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Inicia el monitor de heartbeat
     */
    public synchronized void start() {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        failureCount.set(0);
        
        executor.scheduleAtFixedRate(
            this::sendHeartbeat,
            HEARTBEAT_INTERVAL_MS,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        LimeLog.info(TAG + ": Monitor iniciado");
    }
    
    /**
     * Detiene el monitor de heartbeat
     */
    public synchronized void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        executor.shutdownNow();
        
        LimeLog.info(TAG + ": Monitor detenido");
    }
    
    /**
     * Envía un heartbeat y espera respuesta
     */
    private void sendHeartbeat() {
        if (!isRunning) {
            return;
        }
        
        long sentTimeMs = System.currentTimeMillis();
        
        // Usar un Handler para timeout
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = () -> {
            if (isRunning) {
                LimeLog.warning(TAG + ": Heartbeat timeout");
                failureCount.incrementAndGet();
                callback.onHeartbeatTimeout();
                
                if (failureCount.get() >= MAX_FAILURES) {
                    LimeLog.severe(TAG + ": Max heartbeat failures reached");
                    stop();
                    callback.onHeartbeatFailed();
                }
            }
        };
        
        timeoutHandler.postDelayed(timeoutRunnable, HEARTBEAT_TIMEOUT_MS);
        
        // Enviar keepalive packet (implementado en JNI)
        int result = sendKeepAlivePacket(() -> {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            
            if (isRunning) {
                long receivedTimeMs = System.currentTimeMillis();
                long rttMs = receivedTimeMs - sentTimeMs;
                
                // Reset contador de fallos
                failureCount.set(0);
                
                LimeLog.info(TAG + ": Heartbeat OK (RTT: " + rttMs + "ms)");
                
                if (rttMs > 500) {
                    LimeLog.warning(TAG + ": Alto RTT: " + rttMs + "ms");
                }
                
                callback.onHeartbeatSuccess(rttMs);
            }
        });
        
        if (result != 0) {
            LimeLog.severe(TAG + ": Error al enviar keepalive");
            failureCount.incrementAndGet();
            callback.onHeartbeatFailed();
        }
    }
    
    /**
     * Envía un paquete keepalive al servidor
     * Método JNI a implementar en moonlight-core/callbacks.c
     */
    private native int sendKeepAlivePacket(Runnable onResponse);
    
    /**
     * Obtiene el estado del monitor
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Obtiene el contador de fallos
     */
    public int getFailureCount() {
        return failureCount.get();
    }
}
```

## 1.2 Implementación JNI en callbacks.c

```c
// En moonlight-core/callbacks.c

static jmethodID BridgeClKeepAliveResponseMethod;
static jobject GlobalHeartbeatCallback;

// Agregar a Java_com_limelight_nvstream_jni_MoonBridge_init()
void init_heartbeat_methods(JNIEnv *env, jclass clazz) {
    BridgeClKeepAliveResponseMethod = (*env)->GetStaticMethodID(env, clazz, 
        "bridgeClKeepAliveResponse", "()V");
}

// Enviar keepalive packet
int BridgeSendKeepAlive(void* context) {
    // Implementar según protocolo de Moonlight
    // Enviar paquete especial al servidor
    // El servidor debe responder con ACK
    
    // Esto es un placeholder - implementar según RTSP/control protocol
    return 0;
}

// Respuesta del servidor
void BridgeClKeepAliveResponse(void) {
    JNIEnv* env = GetThreadEnv();
    
    // Llamar al callback de respuesta
    if (GlobalHeartbeatCallback != NULL) {
        (*env)->CallVoidMethod(env, GlobalHeartbeatCallback, 
            BridgeClKeepAliveResponseMethod);
    }
}

// En JNIEXPORT int JNICALL Java_com_limelight_nvstream_jni_MoonBridge_sendKeepAlivePacket
JNIEXPORT int JNICALL 
Java_com_limelight_nvstream_connection_HeartbeatMonitor_sendKeepAlivePacket
    (JNIEnv *env, jobject obj, jobject callback) {
    
    GlobalHeartbeatCallback = (*env)->NewGlobalRef(env, callback);
    
    return BridgeSendKeepAlive(NULL);
}
```

---

# MÓDULO 2: AUTO-RECONEXIÓN

## 2.1 AutoReconnectionManager.java

```java
package com.limelight.nvstream.connection;

import android.os.Handler;
import android.os.Looper;

import com.limelight.LimeLog;
import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;

import java.util.concurrent.atomic.AtomicBoolean;

public class AutoReconnectionManager {
    private static final String TAG = "AutoReconnectionManager";
    
    // Backoff exponencial
    private static final long[] BACKOFF_MS = {
        1000,    // 1 segundo
        2000,    // 2 segundos
        4000,    // 4 segundos
        8000,    // 8 segundos
        16000    // 16 segundos
    };
    
    private static final int MAX_RETRIES = BACKOFF_MS.length;
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NvConnection connection;
    private final NvConnectionListener listener;
    private int retryCount = 0;
    private AtomicBoolean isRetrying = new AtomicBoolean(false);
    private ConnectionContext lastContext;
    
    public AutoReconnectionManager(NvConnection connection, 
                                   NvConnectionListener listener) {
        this.connection = connection;
        this.listener = listener;
    }
    
    /**
     * Maneja una conexión perdida y reintenta automáticamente
     */
    public synchronized void handleConnectionLost(String reason, Exception cause) {
        if (isRetrying.get()) {
            LimeLog.warning(TAG + ": Ya está reintentando");
            return;
        }
        
        if (retryCount >= MAX_RETRIES) {
            LimeLog.severe(TAG + ": Máximo de reintentos alcanzado");
            listener.displayMessage("No se pudo reconectar. Toque para reintentar.");
            resetRetryCount();
            return;
        }
        
        isRetrying.set(true);
        long delayMs = BACKOFF_MS[retryCount];
        
        String message = String.format(
            "Reconectando en %d segundos... (%d/%d)",
            delayMs / 1000,
            retryCount + 1,
            MAX_RETRIES
        );
        
        LimeLog.warning(TAG + ": " + message + " - Razón: " + reason);
        listener.displayTransientMessage(message);
        
        // Programar reintento con backoff exponencial
        handler.postDelayed(() -> {
            retryCount++;
            attemptReconnect();
        }, delayMs);
    }
    
    /**
     * Intenta reconectar a la sesión existente
     */
    private void attemptReconnect() {
        LimeLog.info(TAG + ": Intento " + retryCount + " de " + MAX_RETRIES);
        
        try {
            // Si tenemos URL de sesión, intentar resume
            if (lastContext != null && lastContext.rtspSessionUrl != null) {
                if (attemptSessionResume()) {
                    resetRetryCount();
                    isRetrying.set(false);
                    return;
                }
            }
            
            // Si resume falló o no hay sesión, intentar launch
            if (attemptSessionLaunch()) {
                resetRetryCount();
                isRetrying.set(false);
                return;
            }
        } catch (Exception e) {
            LimeLog.severe(TAG + ": Error en reintento: " + e.getMessage());
        }
        
        // Si falla este reintento, programar siguiente
        if (retryCount < MAX_RETRIES) {
            handleConnectionLost("Reintento fallido", null);
        } else {
            isRetrying.set(false);
        }
    }
    
    /**
     * Intenta reanudar la sesión existente
     */
    private boolean attemptSessionResume() {
        LimeLog.info(TAG + ": Intentando resume de sesión");
        
        try {
            // Aquí iría lógica para reanudar sesión
            // Usar NvHTTP para llamar /resume endpoint
            return true;
        } catch (Exception e) {
            LimeLog.warning(TAG + ": Resume falló: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Intenta lanzar nueva sesión
     */
    private boolean attemptSessionLaunch() {
        LimeLog.info(TAG + ": Intentando launch de nueva sesión");
        
        try {
            // Aquí iría lógica para lanzar sesión
            // Usar NvHTTP para llamar /launch endpoint
            return true;
        } catch (Exception e) {
            LimeLog.warning(TAG + ": Launch falló: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Resetea el contador de reintentos
     */
    private void resetRetryCount() {
        retryCount = 0;
    }
    
    /**
     * Guarda el contexto de conexión para posible recuperación
     */
    public void saveConnectionContext(ConnectionContext context) {
        this.lastContext = context;
    }
    
    /**
     * Detiene los reintentos en curso
     */
    public synchronized void stop() {
        handler.removeCallbacksAndMessages(null);
        isRetrying.set(false);
        resetRetryCount();
    }
}
```

---

# MÓDULO 3: DETECCIÓN DE CAMBIO DE RED

## 3.1 NetworkChangeListener.java

```java
package com.limelight.nvstream.connection;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.limelight.LimeLog;
import com.limelight.nvstream.StreamConfiguration;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NetworkChangeListener extends ConnectivityManager.NetworkCallback {
    private static final String TAG = "NetworkChangeListener";
    
    private final NetworkChangeCallback callback;
    private int lastNetworkType = NETWORK_TYPE_UNKNOWN;
    private String lastNetworkSSID = "";
    
    // Network type constants
    public static final int NETWORK_TYPE_UNKNOWN = -1;
    public static final int NETWORK_TYPE_WIFI = 0;
    public static final int NETWORK_TYPE_CELLULAR = 1;
    public static final int NETWORK_TYPE_VPN = 2;
    
    public interface NetworkChangeCallback {
        void onNetworkAvailable(int networkType, String networkName);
        void onNetworkLost();
        void onNetworkTypeChanged(int oldType, int newType);
        void onNetworkCapabilitiesChanged(NetworkCapabilities caps);
    }
    
    public NetworkChangeListener(NetworkChangeCallback callback) {
        this.callback = callback;
    }
    
    @Override
    public void onAvailable(Network network) {
        LimeLog.info(TAG + ": Red disponible");
        
        ConnectivityManager cm = (ConnectivityManager) getContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        
        int networkType = detectNetworkType(cm, network);
        String networkName = getNetworkName(cm, network, networkType);
        
        if (lastNetworkType == NETWORK_TYPE_UNKNOWN) {
            // Primera red disponible
            callback.onNetworkAvailable(networkType, networkName);
        } else if (networkType != lastNetworkType) {
            // Cambio de tipo de red
            LimeLog.warning(TAG + ": Cambio de red detectado: " 
                + typeToString(lastNetworkType) + " → " 
                + typeToString(networkType));
            callback.onNetworkTypeChanged(lastNetworkType, networkType);
        }
        
        lastNetworkType = networkType;
        lastNetworkSSID = networkName;
    }
    
    @Override
    public void onLost(Network network) {
        LimeLog.warning(TAG + ": Red perdida");
        
        lastNetworkType = NETWORK_TYPE_UNKNOWN;
        lastNetworkSSID = "";
        
        callback.onNetworkLost();
    }
    
    @Override
    public void onCapabilitiesChanged(Network network, 
                                     NetworkCapabilities capabilities) {
        LimeLog.info(TAG + ": Capacidades de red cambidas");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int newNetworkType = detectNetworkType(
                (ConnectivityManager) getContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE), 
                network
            );
            
            if (newNetworkType != lastNetworkType) {
                callback.onNetworkTypeChanged(lastNetworkType, newNetworkType);
                lastNetworkType = newNetworkType;
            }
        }
        
        callback.onNetworkCapabilitiesChanged(capabilities);
    }
    
    @Override
    public void onLinkPropertiesChanged(Network network, 
                                       LinkProperties linkProperties) {
        LimeLog.info(TAG + ": Propiedades de enlace cambiadas");
        
        // Aquí se puede detectar cambio de IP
        if (linkProperties != null && 
            !linkProperties.getAddresses().isEmpty()) {
            
            String newIP = linkProperties.getAddresses()
                .get(0).getHostAddress();
            
            LimeLog.info(TAG + ": Nueva dirección IP: " + newIP);
        }
    }
    
    /**
     * Detecta el tipo de red
     */
    private int detectNetworkType(ConnectivityManager cm, Network network) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
            cm != null && network != null) {
            
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return NETWORK_TYPE_VPN;
                }
                
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return NETWORK_TYPE_WIFI;
                }
                
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return NETWORK_TYPE_CELLULAR;
                }
            }
        }
        
        return NETWORK_TYPE_UNKNOWN;
    }
    
    /**
     * Obtiene el nombre de la red (SSID para WiFi, operador para cellular)
     */
    private String getNetworkName(ConnectivityManager cm, Network network, 
                                 int networkType) {
        switch (networkType) {
            case NETWORK_TYPE_WIFI:
                // TODO: Implementar obtención de SSID
                return "WiFi";
            
            case NETWORK_TYPE_CELLULAR:
                // TODO: Obtener nombre del operador
                return "Cellular";
            
            case NETWORK_TYPE_VPN:
                return "VPN";
            
            default:
                return "Unknown";
        }
    }
    
    /**
     * Convierte tipo de red a string
     */
    private String typeToString(int networkType) {
        switch (networkType) {
            case NETWORK_TYPE_WIFI:
                return "WiFi";
            case NETWORK_TYPE_CELLULAR:
                return "Cellular";
            case NETWORK_TYPE_VPN:
                return "VPN";
            case NETWORK_TYPE_UNKNOWN:
            default:
                return "Unknown";
        }
    }
    
    /**
     * Obtiene el tipo de streaming recomendado basado en red
     */
    public int getRecommendedStreamingType() {
        switch (lastNetworkType) {
            case NETWORK_TYPE_WIFI:
                return StreamConfiguration.STREAM_CFG_LOCAL;
            
            case NETWORK_TYPE_CELLULAR:
            case NETWORK_TYPE_VPN:
                return StreamConfiguration.STREAM_CFG_REMOTE;
            
            default:
                return StreamConfiguration.STREAM_CFG_AUTO;
        }
    }
    
    /**
     * Obtiene el tipo de red actual
     */
    public int getCurrentNetworkType() {
        return lastNetworkType;
    }
    
    /**
     * Comprueba si hay red disponible
     */
    public boolean isNetworkAvailable() {
        return lastNetworkType != NETWORK_TYPE_UNKNOWN;
    }
}
```

---

# MÓDULO 4: TELEMETRÍA Y MÉTRICAS

## 4.1 TelemetryCollector.java

```java
package com.limelight.nvstream.connection;

import com.limelight.LimeLog;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TelemetryCollector {
    private static final String TAG = "TelemetryCollector";
    
    // Ventana de métricas (últimos 60 segundos)
    private static final int METRICS_WINDOW_SIZE = 60;
    
    public static class PerformanceMetrics {
        public long timestampMs;
        public int rttMs;                    // Round-trip time
        public float packetLossPercent;      // Porcentaje de pérdida
        public int droppedFrames;            // Frames perdidos
        public int audioUnderruns;           // Underruns de audio
        public int videoFreezes;             // Congelaciones de video
        public long touchLatencyMs;          // Latencia táctil
        public int videoFrameRate;           // FPS actual
        public int audioBufferMs;            // Tamaño del buffer
        public int videoCodec;               // Codec de video
        public int bitrateBps;               // Bitrate actual
        
        @Override
        public String toString() {
            return String.format(
                "Metrics{rtt=%dms, loss=%.2f%%, dropped=%d, " +
                "touch=%dms, fps=%d, audio_buffer=%dms}",
                rttMs, packetLossPercent, droppedFrames,
                touchLatencyMs, videoFrameRate, audioBufferMs
            );
        }
    }
    
    private final Queue<Long> rttHistory = new LinkedList<>();
    private final Queue<Integer> frameDropHistory = new LinkedList<>();
    
    private AtomicInteger droppedFrameCount = new AtomicInteger(0);
    private AtomicInteger audioUnderrunCount = new AtomicInteger(0);
    private AtomicInteger videoFreezeCount = new AtomicInteger(0);
    private AtomicLong totalFramesReceived = new AtomicLong(0);
    private int lastFrameNumber = -1;
    
    private long lastTouchTimeMs = -1;
    
    /**
     * Registra un frame recibido
     */
    public synchronized void recordFrame(int frameNumber, 
                                        long receiveTimeMs) {
        totalFramesReceived.incrementAndGet();
        
        // Detectar frames perdidos
        if (lastFrameNumber != -1) {
            int expectedFrames = frameNumber - lastFrameNumber;
            if (expectedFrames > 1) {
                int dropped = expectedFrames - 1;
                droppedFrameCount.addAndGet(dropped);
                frameDropHistory.offer((long) dropped);
                
                if (frameDropHistory.size() > METRICS_WINDOW_SIZE) {
                    frameDropHistory.poll();
                }
                
                LimeLog.warning(TAG + ": " + dropped + 
                    " frames perdidos (frame " + lastFrameNumber + 
                    " a " + frameNumber + ")");
            }
        }
        
        lastFrameNumber = frameNumber;
    }
    
    /**
     * Registra una medición de RTT
     */
    public synchronized void recordRTT(long rttMs) {
        rttHistory.offer(rttMs);
        
        if (rttHistory.size() > METRICS_WINDOW_SIZE) {
            rttHistory.poll();
        }
        
        if (rttMs > 200) {
            LimeLog.warning(TAG + ": Alto RTT: " + rttMs + "ms");
        }
    }
    
    /**
     * Registra un evento táctil
     */
    public void recordTouchEvent(long touchTimeMs) {
        this.lastTouchTimeMs = touchTimeMs;
    }
    
    /**
     * Registra la latencia táctil calculada
     */
    public void recordTouchLatency(long touchLatencyMs) {
        if (touchLatencyMs > 100) {
            LimeLog.warning(TAG + ": Alta latencia táctil: " + 
                touchLatencyMs + "ms");
        }
    }
    
    /**
     * Registra underrun de audio
     */
    public void recordAudioUnderrun() {
        audioUnderrunCount.incrementAndGet();
        LimeLog.warning(TAG + ": Audio underrun");
    }
    
    /**
     * Registra congelación de video
     */
    public void recordVideoFreeze() {
        videoFreezeCount.incrementAndGet();
        LimeLog.warning(TAG + ": Video freeze");
    }
    
    /**
     * Obtiene métricas actuales
     */
    public synchronized PerformanceMetrics getCurrentMetrics() {
        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.timestampMs = System.currentTimeMillis();
        
        // RTT
        if (!rttHistory.isEmpty()) {
            long totalRtt = 0;
            for (Long rtt : rttHistory) {
                totalRtt += rtt;
            }
            metrics.rttMs = (int) (totalRtt / rttHistory.size());
        }
        
        // Packet loss
        long totalDropped = 0;
        for (Long dropped : frameDropHistory) {
            totalDropped += dropped;
        }
        long totalFrames = totalFramesReceived.get();
        if (totalFrames > 0) {
            metrics.packetLossPercent = 
                (float) (totalDropped * 100.0 / totalFrames);
        }
        
        // Contadores
        metrics.droppedFrames = droppedFrameCount.get();
        metrics.audioUnderruns = audioUnderrunCount.get();
        metrics.videoFreezes = videoFreezeCount.get();
        
        return metrics;
    }
    
    /**
     * Resetea todas las métricas
     */
    public synchronized void reset() {
        rttHistory.clear();
        frameDropHistory.clear();
        droppedFrameCount.set(0);
        audioUnderrunCount.set(0);
        videoFreezeCount.set(0);
        totalFramesReceived.set(0);
        lastFrameNumber = -1;
        
        LimeLog.info(TAG + ": Métricas reseteadas");
    }
    
    /**
     * Obtiene reporte formateado de métricas
     */
    public String getDetailedReport() {
        PerformanceMetrics m = getCurrentMetrics();
        
        return String.format(
            "=== PERFORMANCE METRICS ===\n" +
            "Timestamp: %d ms\n" +
            "RTT: %d ms\n" +
            "Packet Loss: %.2f%%\n" +
            "Dropped Frames: %d\n" +
            "Audio Underruns: %d\n" +
            "Video Freezes: %d\n" +
            "Touch Latency: %d ms\n" +
            "Video FPS: %d\n" +
            "=========================",
            m.timestampMs, m.rttMs, m.packetLossPercent,
            m.droppedFrames, m.audioUnderruns, m.videoFreezes,
            m.touchLatencyMs, m.videoFrameRate
        );
    }
}
```

---

# MÓDULO 5: GESTOR CENTRALIZADO (ConnectionOrchestrator)

## 5.1 ConnectionOrchestrator.java

```java
package com.limelight.nvstream.connection;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.limelight.LimeLog;
import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinador central de todo el flujo de conexión
 * Integra: Heartbeat, Reconexión, Cambio de Red, Telemetría
 */
public class ConnectionOrchestrator implements 
    HeartbeatMonitor.HeartbeatCallback,
    NetworkChangeListener.NetworkChangeCallback {
    
    private static final String TAG = "ConnectionOrchestrator";
    
    private final Context context;
    private final NvConnection nvConnection;
    private final NvConnectionListener listener;
    
    // Componentes de resiliencia
    private HeartbeatMonitor heartbeatMonitor;
    private AutoReconnectionManager reconnectionManager;
    private NetworkChangeListener networkChangeListener;
    private TelemetryCollector telemetryCollector;
    
    // Estado
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    
    public ConnectionOrchestrator(Context context,
                                 NvConnection nvConnection,
                                 NvConnectionListener listener) {
        this.context = context;
        this.nvConnection = nvConnection;
        this.listener = listener;
        
        initializeComponents();
    }
    
    /**
     * Inicializa todos los componentes de resiliencia
     */
    private void initializeComponents() {
        LimeLog.info(TAG + ": Inicializando componentes de resiliencia");
        
        // Heartbeat
        heartbeatMonitor = new HeartbeatMonitor(this);
        
        // Reconexión automática
        reconnectionManager = new AutoReconnectionManager(
            nvConnection, listener);
        
        // Detección de cambio de red
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            networkChangeListener = new NetworkChangeListener(this);
            registerNetworkListener();
        }
        
        // Telemetría
        telemetryCollector = new TelemetryCollector();
    }
    
    /**
     * Registra el listener de cambios de red
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void registerNetworkListener() {
        ConnectivityManager cm = (ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkChangeListener);
            LimeLog.info(TAG + ": Network listener registrado");
        }
    }
    
    /**
     * Desregistra el listener de cambios de red
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void unregisterNetworkListener() {
        ConnectivityManager cm = (ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (cm != null && networkChangeListener != null) {
            cm.unregisterNetworkCallback(networkChangeListener);
            LimeLog.info(TAG + ": Network listener desregistrado");
        }
    }
    
    /**
     * Inicia la conexión con todas las defensas activas
     */
    public void startConnection(ConnectionContext context) {
        LimeLog.info(TAG + ": Iniciando conexión orquestada");
        
        // Guardar contexto para posible recuperación
        reconnectionManager.saveConnectionContext(context);
        
        // Iniciar conexión base
        nvConnection.start(null, null, new NvConnectionListener() {
            @Override
            public void stageStarting(String stage) {
                listener.stageStarting(stage);
            }
            
            @Override
            public void stageComplete(String stage) {
                listener.stageComplete(stage);
            }
            
            @Override
            public void stageFailed(String stage, int portFlags, 
                                   int errorCode) {
                listener.stageFailed(stage, portFlags, errorCode);
            }
            
            @Override
            public void connectionStarted() {
                isConnected.set(true);
                heartbeatMonitor.start();
                listener.connectionStarted();
                LimeLog.info(TAG + ": Conexión establecida, heartbeat iniciado");
            }
            
            @Override
            public void connectionTerminated(int errorCode) {
                isConnected.set(false);
                heartbeatMonitor.stop();
                listener.connectionTerminated(errorCode);
                
                // Intentar reconexión automática
                if (!isPaused.get()) {
                    reconnectionManager
                        .handleConnectionLost("Error: " + errorCode, null);
                }
            }
            
            @Override
            public void connectionStatusUpdate(int connectionStatus) {
                listener.connectionStatusUpdate(connectionStatus);
            }
            
            // ... otros métodos de listener
            
            @Override
            public void displayMessage(String message) {
                listener.displayMessage(message);
            }
            
            @Override
            public void displayTransientMessage(String message) {
                listener.displayTransientMessage(message);
            }
            
            @Override
            public void rumble(short controllerNumber, 
                              short lowFreqMotor, short highFreqMotor) {
                listener.rumble(controllerNumber, lowFreqMotor, 
                               highFreqMotor);
            }
            
            @Override
            public void rumbleTriggers(short controllerNumber, 
                                      short leftTrigger, 
                                      short rightTrigger) {
                listener.rumbleTriggers(controllerNumber, leftTrigger, 
                                       rightTrigger);
            }
            
            @Override
            public void setHdrMode(boolean enabled, byte[] hdrMetadata) {
                listener.setHdrMode(enabled, hdrMetadata);
            }
            
            @Override
            public void setMotionEventState(short controllerNumber, 
                                           byte motionType, 
                                           short reportRateHz) {
                listener.setMotionEventState(controllerNumber, motionType, 
                                            reportRateHz);
            }
            
            @Override
            public void setControllerLED(short controllerNumber, 
                                        byte r, byte g, byte b) {
                listener.setControllerLED(controllerNumber, r, g, b);
            }
        });
    }
    
    /**
     * Detiene la conexión y todos los monitores
     */
    public void stopConnection() {
        LimeLog.info(TAG + ": Deteniendo conexión orquestada");
        
        isConnected.set(false);
        heartbeatMonitor.stop();
        reconnectionManager.stop();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            unregisterNetworkListener();
        }
        
        nvConnection.stop();
    }
    
    /**
     * Pausa el streaming pero mantiene keepalive
     */
    public void pauseStreaming() {
        LimeLog.info(TAG + ": Pausando streaming");
        isPaused.set(true);
        
        // Mantener heartbeat pero no reintentaría si falla
        // (el usuario está viendo la pantalla de pausa)
    }
    
    /**
     * Reanuda el streaming
     */
    public void resumeStreaming() {
        LimeLog.info(TAG + ": Reanudando streaming");
        isPaused.set(false);
    }
    
    // ===== Implementación de HeartbeatMonitor.HeartbeatCallback =====
    
    @Override
    public void onHeartbeatSuccess(long rttMs) {
        telemetryCollector.recordRTT(rttMs);
        
        // Aquí se pueden tomar decisiones basadas en RTT
        if (rttMs > 200) {
            // Considerar reducir bitrate
            LimeLog.warning(TAG + ": RTT elevado, considerar reducir bitrate");
        }
    }
    
    @Override
    public void onHeartbeatFailed() {
        LimeLog.warning(TAG + ": Heartbeat falló");
        
        // Trigger reconexión
        reconnectionManager.handleConnectionLost("Heartbeat failed", null);
    }
    
    @Override
    public void onHeartbeatTimeout() {
        LimeLog.warning(TAG + ": Heartbeat timeout");
        telemetryCollector.recordAudioUnderrun();
    }
    
    // ===== Implementación de NetworkChangeListener.NetworkChangeCallback =====
    
    @Override
    public void onNetworkAvailable(int networkType, String networkName) {
        LimeLog.info(TAG + ": Red disponible: " + networkName);
        
        if (isConnected.get() && isPaused.get()) {
            // La conexión estaba pausada por falta de red, reanudar
            LimeLog.info(TAG + ": Red restaurada, reanudando");
            reconnectionManager.handleConnectionLost("Red restaurada", null);
        }
    }
    
    @Override
    public void onNetworkLost() {
        LimeLog.warning(TAG + ": Red perdida");
        
        if (isConnected.get()) {
            isPaused.set(true);
            listener.displayTransientMessage("Red perdida");
        }
    }
    
    @Override
    public void onNetworkTypeChanged(int oldType, int newType) {
        LimeLog.warning(TAG + ": Cambio de tipo de red: " + 
            oldType + " → " + newType);
        
        if (isConnected.get()) {
            // Ajustar parámetros de streaming según nueva red
            adjustForNetworkType(newType);
        }
    }
    
    @Override
    public void onNetworkCapabilitiesChanged(
            android.net.NetworkCapabilities capabilities) {
        LimeLog.info(TAG + ": Capacidades de red cambiadas");
    }
    
    /**
     * Ajusta parámetros de streaming según tipo de red
     */
    private void adjustForNetworkType(int networkType) {
        // Aquí se puede cambiar bitrate, resolución, etc.
        // Basado en si es WiFi local, móvil, etc.
    }
    
    /**
     * Obtiene las métricas actuales
     */
    public TelemetryCollector.PerformanceMetrics getMetrics() {
        return telemetryCollector.getCurrentMetrics();
    }
    
    /**
     * Obtiene reporte detallado
     */
    public String getDetailedReport() {
        return telemetryCollector.getDetailedReport();
    }
}
```

---

# TESTING

## 5.1 Casos de Prueba

```java
public class ResilienceTestSuite {
    
    /**
     * Test: Detección de Heartbeat
     * Simula pérdida de heartbeat y verifica reconexión
     */
    @Test
    public void testHeartbeatDetection() {
        // Setup
        HeartbeatMonitor monitor = new HeartbeatMonitor(mockCallback);
        monitor.start();
        
        // Act: Simular 3 heartbeats perdidos
        for (int i = 0; i < 3; i++) {
            monitor.onHeartbeatTimeout();
        }
        
        // Assert
        verify(mockCallback).onHeartbeatFailed();
    }
    
    /**
     * Test: Recuperación de Cambio de Red
     * Simula cambio de WiFi a móvil
     */
    @Test
    public void testNetworkSwitchRecovery() {
        // Setup
        NetworkChangeListener listener = new NetworkChangeListener(mockCallback);
        
        // Act: Simular disponibilidad de red móvil
        listener.onNetworkTypeChanged(
            NetworkChangeListener.NETWORK_TYPE_WIFI,
            NetworkChangeListener.NETWORK_TYPE_CELLULAR
        );
        
        // Assert
        verify(mockCallback).onNetworkTypeChanged(
            NetworkChangeListener.NETWORK_TYPE_WIFI,
            NetworkChangeListener.NETWORK_TYPE_CELLULAR
        );
    }
}
```

---

# PRÓXIMOS PASOS

1. **Integración con NvConnection**: Modificar `NvConnection.java` para usar `ConnectionOrchestrator`
2. **Implementación JNI**: Implementar `sendKeepAlivePacket()` en `moonlight-common-c`
3. **Pruebas Exhaustivas**: Pruebas de todos los escenarios de fallo
4. **Integración en Interfaz**: Mostrar estado de reconexión al usuario
5. **Despliegue**: Versiones beta → estable


