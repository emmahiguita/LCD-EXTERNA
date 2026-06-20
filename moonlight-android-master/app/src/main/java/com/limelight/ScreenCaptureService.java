package com.limelight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class ScreenCaptureService extends Service {
    private static final String CHANNEL_ID = "ScreenCaptureChannel";
    private static final int NOTIFICATION_ID = 9912;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaCodec mediaCodec;
    private WebSocket webSocket;
    private boolean isCapturing = false;
    private Thread codecThread;
    private boolean isWebSocketConnected = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra("RESULT_CODE", -1);
        Intent resultData = intent.getParcelableExtra("RESULT_DATA");
        String pcIp = intent.getStringExtra("PC_IP");
        String token = intent.getStringExtra("TOKEN");

        if (resultCode == -1 || resultData == null || pcIp == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        startScreenCapture(resultCode, resultData, pcIp, token);

        return START_STICKY;
    }

    private void startScreenCapture(int resultCode, Intent resultData, String pcIp, String token) {
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData);

        if (mediaProjection == null) {
            stopSelf();
            return;
        }

        // Obtener dimensiones de la pantalla real del celular
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        display.getRealMetrics(metrics);

        // Escalar a 720p para rendimiento de red óptimo conservando aspect ratio
        int targetWidth = 720;
        int targetHeight = 1280;
        if (metrics.widthPixels > metrics.heightPixels) {
            // Horizontal
            targetWidth = 1280;
            targetHeight = 720;
        }

        try {
            // Configurar MediaCodec para codificación H.264
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, targetWidth, targetHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000); // 2 Mbps
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 60); // 60 FPS
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2); // I-Frame cada 2 segundos (reduce ráfagas de red)

            // Optimizaciones de latencia y estabilidad de red
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                format.setInteger(MediaFormat.KEY_LATENCY, 1); // Modo de latencia ultra baja
            }
            format.setInteger(MediaFormat.KEY_PRIORITY, 0); // Prioridad de tiempo real en el SO
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR); // CBR para evitar picos de ancho de banda

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface inputSurface = mediaCodec.createInputSurface();
            mediaCodec.start();

            // Enlazar buffer de GPU al encoder
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    targetWidth, targetHeight, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface, null, null
            );

            isCapturing = true;
            setupWebSocketConnection(pcIp, token);
            startCodecLoop();

        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    private void setupWebSocketConnection(String pcIp, String token) {
        OkHttpClient client = new OkHttpClient();
        String urlString = "ws://" + pcIp + ":3002";
        if (token != null && !token.isEmpty()) {
            urlString += "/?token=" + token;
        }
        Request request = new Request.Builder().url(urlString).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                // Registrar este cliente como android-stream
                JSONObject regMsg = new JSONObject();
                try {
                    regMsg.put("type", "register");
                    regMsg.put("client", "android-stream");
                    webSocket.send(regMsg.toString());
                    isWebSocketConnected = true;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                isWebSocketConnected = false;
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                isWebSocketConnected = false;
            }
        });
    }

    private void startCodecLoop() {
        codecThread = new Thread(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                while (isCapturing) {
                    int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000); // 10ms timeout
                    if (outputBufferIndex >= 0) {
                        ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                            byte[] outData = new byte[bufferInfo.size];
                            outputBuffer.get(outData);

                            // Enviar flujo H.264 binario si hay conexión WebSocket establecida
                            if (isWebSocketConnected && webSocket != null) {
                                // Prevenir bufferbloat: si la cola del socket supera los 256 KB, descartamos el frame
                                if (webSocket.queueSize() < 256 * 1024) {
                                    webSocket.send(ByteString.of(outData));
                                }
                            }
                        }
                        mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // El formato cambió (se obtienen cabeceras SPS/PPS en el arranque)
                        MediaFormat newFormat = mediaCodec.getOutputFormat();
                        ByteBuffer sps = newFormat.getByteBuffer("csd-0");
                        ByteBuffer pps = newFormat.getByteBuffer("csd-1");
                        if (sps != null && pps != null) {
                            byte[] spsData = new byte[sps.remaining()];
                            sps.get(spsData);
                            byte[] ppsData = new byte[pps.remaining()];
                            pps.get(ppsData);

                            byte[] header = new byte[spsData.length + ppsData.length];
                            System.arraycopy(spsData, 0, header, 0, spsData.length);
                            System.arraycopy(ppsData, 0, header, spsData.length, ppsData.length);

                            if (isWebSocketConnected && webSocket != null) {
                                webSocket.send(ByteString.of(header));
                            }
                        }
                    }
                }
            }
        });
        codecThread.start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "SmartDisplay Screen Capture",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("SmartDisplay AI")
                .setContentText("Transmitiendo pantalla del celular al PC en alta fidelidad...")
                .setSmallIcon(android.R.drawable.presence_video_online)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isCapturing = false;
        if (codecThread != null) {
            try {
                codecThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
        if (webSocket != null) {
            webSocket.close(1000, "Service destroyed");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
