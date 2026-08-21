package com.limelight.smartdisplay.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.view.Surface;

import androidx.core.app.NotificationCompat;

import com.limelight.LimeLog;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * SmartDisplay — Screen Capture Service
 *
 * Captures the device screen via MediaProjection API, encodes it to H.264
 * using MediaCodec, and streams the encoded frames over a WebSocket connection
 * to the Electron companion (port 3002) for real-time display on the desktop
 * StreamPanel.
 *
 * Lifecycle:
 *   - Started by Game.java after user grants MediaProjection consent.
 *   - Runs as a foreground service (required for MediaProjection on API 34+).
 *   - Connects to ws://<PC_IP>:3002?token=<TOKEN> and registers as "android-stream".
 *   - Stops on STOP_SCREEN_CAPTURE broadcast or WebSocket disconnect.
 */
public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureSvc";
    private static final String CHANNEL_ID = "screen_capture_channel";
    private static final int NOTIFICATION_ID = 1001;

    // Target capture dimensions (720p portrait — sufficient for remote viewing)
    private static final int CAPTURE_WIDTH = 720;
    private static final int CAPTURE_HEIGHT = 1280;
    private static final int CAPTURE_DPI = 320;
    private static final int BITRATE = 2_000_000;       // 2 Mbps
    private static final int FRAME_RATE = 30;
    private static final int I_FRAME_INTERVAL = 1;      // 1 sec

    // Electron WebSocket default port
    private static final int WS_PORT = 3002;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaCodec mediaCodec;
    private Surface inputSurface;

    private WebSocket webSocket;
    private OkHttpClient httpClient;
    private HandlerThread captureThread;
    private Handler captureHandler;

    private volatile boolean isRunning = false;

    // ── Service lifecycle ────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        // API 29+: el FGS de proyección debe declarar su tipo al iniciar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }

        projectionManager = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

        httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        LimeLog.info(TAG + ": Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            LimeLog.warning(TAG + ": null intent — stopping");
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra("code", -1);
        // The data Intent from MediaProjectionManager.createScreenCaptureIntent() result
        Intent data = intent.getParcelableExtra("data");
        String wsHost = intent.getStringExtra("ws_host");
        String token  = intent.getStringExtra("token");

        if (resultCode == -1 || data == null || wsHost == null) {
            LimeLog.warning(TAG + ": Missing required extras (code, data, or ws_host)");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Start foreground and begin capture
        startCapture(resultCode, data, wsHost, token);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCapture();
        LimeLog.info(TAG + ": Service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    // ── Capture start / stop ─────────────────────────────────────────────────

    private void startCapture(int resultCode, Intent data, String wsHost, String token) {
        if (isRunning) return;
        isRunning = true;

        // 1. Obtain MediaProjection
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            LimeLog.warning(TAG + ": Failed to obtain MediaProjection");
            isRunning = false;
            stopSelf();
            return;
        }

        // 2. Start background thread for capture + encoding
        captureThread = new HandlerThread("ScreenCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        // 3. Initialize encoder and virtual display (on capture thread)
        captureHandler.post(() -> {
            if (!initEncoder()) {
                LimeLog.warning(TAG + ": Encoder initialization failed");
                isRunning = false;
                stopSelf();
                return;
            }

            // 4. Connect WebSocket — once opened, start virtual display
            connectWebSocket(wsHost, token);
        });
    }

    private synchronized void stopCapture() {
        if (!isRunning) return;
        isRunning = false;

        LimeLog.info(TAG + ": Stopping capture");

        // Close WebSocket
        if (webSocket != null) {
            webSocket.close(1000, "Capture stopped");
            webSocket = null;
        }

        // Release virtual display
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        // Release MediaCodec
        if (mediaCodec != null) {
            try {
                mediaCodec.stop();
                mediaCodec.release();
            } catch (Exception ignored) {}
            mediaCodec = null;
        }

        // Release MediaProjection
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        // Quit capture thread
        if (captureThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                captureThread.quitSafely();
            } else {
                captureThread.quit();
            }
            captureThread = null;
            captureHandler = null;
        }
    }

    // ── MediaCodec H.264 encoder ─────────────────────────────────────────────

    private boolean initEncoder() {
        try {
            MediaFormat format = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC, CAPTURE_WIDTH, CAPTURE_HEIGHT);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
            // High-quality profile for better compression
            format.setInteger(MediaFormat.KEY_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileMain);
            format.setInteger(MediaFormat.KEY_LEVEL,
                    MediaCodecInfo.CodecProfileLevel.AVCLevel32);

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = mediaCodec.createInputSurface();
            mediaCodec.start();

            LimeLog.info(TAG + ": Encoder initialized (" + CAPTURE_WIDTH + "x" + CAPTURE_HEIGHT + ")");
            return true;
        } catch (Exception e) {
            LimeLog.warning(TAG + ": Encoder init error: " + e.getMessage());
            return false;
        }
    }

    private void startVirtualDisplay() {
        if (mediaProjection == null || inputSurface == null) return;

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "SmartDisplay-ScreenCapture",
                CAPTURE_WIDTH,
                CAPTURE_HEIGHT,
                CAPTURE_DPI,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null
        );

        LimeLog.info(TAG + ": VirtualDisplay created");

        // Start the encoder output polling loop
        captureHandler.post(this::encoderLoop);
    }

    /**
     * Polls encoded output from MediaCodec and sends it over WebSocket.
     * Runs on the capture HandlerThread.
     */
    private void encoderLoop() {
        if (!isRunning || mediaCodec == null) return;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        try {
            int outputIndex = mediaCodec.dequeueOutputBuffer(info, 10_000); // 10ms timeout

            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // No output available yet — poll again
                captureHandler.post(this::encoderLoop);
                return;
            }

            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Codec-specific data (CSD-0 / CSD-1) — contains SPS/PPS.
                // Send the format config to JMuxer first.
                MediaFormat newFormat = mediaCodec.getOutputFormat();
                ByteBuffer csd0 = newFormat.getByteBuffer("csd-0");
                ByteBuffer csd1 = newFormat.getByteBuffer("csd-1");
                if (csd0 != null) sendBinary(csd0);
                if (csd1 != null) sendBinary(csd1);
                LimeLog.info(TAG + ": Codec config sent");
                captureHandler.post(this::encoderLoop);
                return;
            }

            if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // Ignore — deprecated constant on older APIs
                captureHandler.post(this::encoderLoop);
                return;
            }

            if (outputIndex >= 0) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputIndex);
                if (outputBuffer != null && info.size > 0) {
                    // Copy data to a new buffer and send
                    outputBuffer.position(info.offset);
                    outputBuffer.limit(info.offset + info.size);
                    ByteBuffer copy = ByteBuffer.allocate(info.size);
                    copy.put(outputBuffer);
                    copy.flip();
                    sendBinary(copy);
                }
                mediaCodec.releaseOutputBuffer(outputIndex, false);
            }

        } catch (Exception e) {
            LimeLog.warning(TAG + ": Encoder loop error: " + e.getMessage());
        }

        // Continue polling if still running
        if (isRunning) {
            captureHandler.post(this::encoderLoop);
        }
    }

    // ── WebSocket client (OkHttp) ────────────────────────────────────────────

    private void connectWebSocket(String host, String token) {
        String url = "ws://" + host + ":" + WS_PORT + "?token=" + (token != null ? token : "");

        LimeLog.info(TAG + ": Connecting to " + url);

        Request request = new Request.Builder()
                .url(url)
                .build();

        httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                webSocket = ws;
                LimeLog.info(TAG + ": WebSocket connected");

                // Register as android-stream client
                try {
                    JSONObject reg = new JSONObject();
                    reg.put("type", "register");
                    reg.put("client", "android-stream");
                    ws.send(reg.toString());
                    LimeLog.info(TAG + ": Registered as android-stream");
                } catch (Exception e) {
                    LimeLog.warning(TAG + ": Registration error: " + e.getMessage());
                }

                // Now that WS is open, start the VirtualDisplay
                startVirtualDisplay();
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                // Incoming text messages (commands from desktop) — handle future extensions
                try {
                    JSONObject msg = new JSONObject(text);
                    String type = msg.optString("type", "");
                    if ("stop".equals(type)) {
                        LimeLog.info(TAG + ": Received stop command from desktop");
                        stopSelf();
                    }
                } catch (Exception ignored) {}
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                LimeLog.info(TAG + ": WebSocket closing: " + reason);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                LimeLog.info(TAG + ": WebSocket closed: " + reason);
                webSocket = null;
                // If the desktop disconnected, stop capturing
                stopSelf();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                LimeLog.warning(TAG + ": WebSocket failure: " + t.getMessage());
                webSocket = null;
                // Connection failed — retry or stop
                if (isRunning) {
                    // Exponential backoff reconnect
                    captureHandler.postDelayed(() -> {
                        if (isRunning) connectWebSocket(host, token);
                    }, 3000);
                }
            }
        });
    }

    private synchronized void sendBinary(ByteBuffer data) {
        if (webSocket == null || !isRunning) return;
        try {
            // Preserve position/limit
            int pos = data.position();
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            data.position(pos);

            webSocket.send(ByteString.of(bytes));
        } catch (Exception e) {
            LimeLog.warning(TAG + ": Send error: " + e.getMessage());
        }
    }

    // ── Foreground notification ──────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Capture",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Notification for screen capture service");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SmartDisplay")
                .setContentText("Screen capture active — streaming to desktop")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
