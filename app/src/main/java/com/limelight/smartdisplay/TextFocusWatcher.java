package com.limelight.smartdisplay;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * TextFocusWatcher — SmartDisplay AI Premium · Componente 3
 * ──────────────────────────────────────────────────────────
 * Cliente WebSocket que se conecta al Companion Server de Windows.
 * El servidor (Python/Node.js) monitoriza via UI Automation qué
 * control tiene el foco y si es un campo de texto.
 *
 * PROTOCOLO (JSON sobre WebSocket):
 *
 *  Servidor → App:
 *  {
 *    "type": "focus_changed",
 *    "is_text_field": true,
 *    "rect": { "x": 100, "y": 200, "w": 800, "h": 40 },
 *    "control_name": "TextBox1",
 *    "app": "Code.exe"
 *  }
 *
 *  {
 *    "type": "focus_cleared"
 *  }
 *
 * COMPORTAMIENTO AL DETECTAR CAMPO DE TEXTO:
 *  • La app llama al callback onTextFieldFocused() con las coordenadas
 *  • Game.java mostrará el teclado lógico automáticamente
 *  • StreamViewTransformController hará zoom al área del campo
 *
 * USO:
 *  TextFocusWatcher watcher = new TextFocusWatcher("ws://192.168.1.10:8765", callback);
 *  watcher.connect();
 *  // ...
 *  watcher.disconnect();
 */
public class TextFocusWatcher {

    // ── Puerto por defecto del Companion Server ────────────────────────────
    public static final int DEFAULT_PORT = 8765;

    // ── Reintentos de conexión ─────────────────────────────────────────────
    private static final long RECONNECT_DELAY_MS = 5000;
    private static final int  MAX_RETRIES        = 10;

    // ── Interfaz de callbacks ──────────────────────────────────────────────
    public interface Callback {
        /** Se llama cuando el foco de Windows cae en un campo de texto */
        void onTextFieldFocused(float x, float y, float w, float h, String app);
        /** Se llama cuando el foco sale del campo de texto */
        void onTextFieldCleared();
        /** Estado de la conexión WebSocket */
        void onConnectionStatus(boolean connected);
        /**
         * Hover sobre un elemento genérico de la UI remota (botón, link, IDE…).
         * {@code element}: none | text_input | button | link | ide_workspace.
         * {@code snapX/snapY} solo válidos si {@code hasSnap} es true.
         * Default vacío: no rompe implementaciones existentes.
         */
        default void onHoverElement(String element, float x, float y, float w, float h,
                                    float snapX, float snapY, boolean hasSnap, String app) {}
        /**
         * Posición real del cursor del PC (coordenadas de Windows). Permite dibujar
         * el cursor gigante de Android exactamente donde apunta el del PC.
         * Default vacío: no rompe implementaciones existentes.
         */
        default void onCursorPos(float x, float y) {}
    }

    // ── Estado ────────────────────────────────────────────────────────────
    private final String   serverUrl;
    private final Callback callback;
    private final String   pin;
    private final Handler  mainHandler = new Handler(Looper.getMainLooper());

    private OkHttpClient client;
    private WebSocket    webSocket;
    private boolean      isConnected   = false;
    private boolean      shouldConnect = false;
    private int          retryCount    = 0;

    public TextFocusWatcher(String serverUrl, Callback callback) {
        this(serverUrl, callback, "");
    }

    public TextFocusWatcher(String serverUrl, Callback callback, String pin) {
        this.serverUrl = serverUrl;
        this.callback  = callback;
        this.pin       = pin != null ? pin : "";
    }

    // ── API Pública ───────────────────────────────────────────────────────

    public void connect() {
        shouldConnect = true;
        retryCount    = 0;
        doConnect();
    }

    public void disconnect() {
        shouldConnect = false;
        if (webSocket != null) {
            webSocket.close(1000, "App closing");
            webSocket = null;
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client = null;
        }
        isConnected = false;
    }

    public boolean isConnected() { return isConnected; }

    // ── Implementación interna ────────────────────────────────────────────

    private void doConnect() {
        if (!shouldConnect) return;

        // Reutilizar un único cliente: crear uno nuevo en cada reintento filtraba
        // el OkHttpClient anterior y su threadpool (hasta MAX_RETRIES veces).
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(0,  TimeUnit.MILLISECONDS) // sin timeout para WS persistente
                    .build();
        }

        Request.Builder builder = new Request.Builder().url(serverUrl);
        if (!pin.isEmpty()) {
            builder.header("Authorization", "Bearer " + pin);
        }
        Request request = builder.build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onOpen(WebSocket ws, Response response) {
                isConnected = true;
                retryCount  = 0;
                mainHandler.post(() -> {
                    if (callback != null) callback.onConnectionStatus(true);
                });
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                parseMessage(text);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isConnected = false;
                mainHandler.post(() -> {
                    if (callback != null) callback.onConnectionStatus(false);
                });
                scheduleReconnect();
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                isConnected = false;
                mainHandler.post(() -> {
                    if (callback != null) callback.onConnectionStatus(false);
                });
                if (code != 1000) {
                    scheduleReconnect(); // solo reconectar si no fue cierre intencional
                }
            }
        });
    }

    private void parseMessage(String json) {
        try {
            JSONObject msg  = new JSONObject(json);
            String     type = msg.optString("type", "");

            if ("focus_changed".equals(type) && msg.optBoolean("is_text_field", false)) {
                JSONObject rect = msg.optJSONObject("rect");
                float x = 0, y = 0, w = 0, h = 0;
                if (rect != null) {
                    x = (float) rect.optDouble("x", 0);
                    y = (float) rect.optDouble("y", 0);
                    w = (float) rect.optDouble("w", 0);
                    h = (float) rect.optDouble("h", 0);
                }
                String app = msg.optString("app", "");
                final float fx = x, fy = y, fw = w, fh = h;

                mainHandler.post(() -> {
                    if (callback != null) callback.onTextFieldFocused(fx, fy, fw, fh, app);
                });

            } else if ("cursor_pos".equals(type)) {
                final float cx = (float) msg.optDouble("x", 0);
                final float cy = (float) msg.optDouble("y", 0);
                mainHandler.post(() -> {
                    if (callback != null) callback.onCursorPos(cx, cy);
                });

            } else if ("focus_cleared".equals(type)) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onTextFieldCleared();
                });

            } else if ("hover_element".equals(type)) {
                String element = msg.optString("element", "none");
                JSONObject rect = msg.optJSONObject("rect");
                float x = 0, y = 0, w = 0, h = 0;
                if (rect != null) {
                    x = (float) rect.optDouble("x", 0);
                    y = (float) rect.optDouble("y", 0);
                    w = (float) rect.optDouble("w", 0);
                    h = (float) rect.optDouble("h", 0);
                }
                JSONObject snap = msg.optJSONObject("snap");
                boolean hasSnap = snap != null;
                float sx = hasSnap ? (float) snap.optDouble("x", 0) : 0;
                float sy = hasSnap ? (float) snap.optDouble("y", 0) : 0;
                String app = msg.optString("app", "");

                final String fel = element;
                final float fx = x, fy = y, fw = w, fh = h, fsx = sx, fsy = sy;
                final boolean fhasSnap = hasSnap;
                mainHandler.post(() -> {
                    if (callback != null)
                        callback.onHoverElement(fel, fx, fy, fw, fh, fsx, fsy, fhasSnap, app);
                });
            }

        } catch (Exception ignored) {}
    }

    private void scheduleReconnect() {
        if (!shouldConnect || retryCount >= MAX_RETRIES) return;
        long delay = RECONNECT_DELAY_MS * (1L << retryCount);
        retryCount++;
        mainHandler.postDelayed(this::doConnect, delay);
    }
}
