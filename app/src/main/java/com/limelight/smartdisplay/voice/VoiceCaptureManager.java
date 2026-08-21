package com.limelight.smartdisplay.voice;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * SmartDisplay AI - Capa 2: VoiceCaptureManager
 * Captura el audio del micrófono del dispositivo y lo envía vía UDP (Out-Of-Band)
 * al PC Host sin interferir con el motor principal de Moonlight.
 */
public class VoiceCaptureManager {
    private static final String TAG = "SD_VoiceCapture";

    // Standard audio configuration.
    // DEBE coincidir con VOICE_SAMPLE_RATE en companion_server.py. Si difieren,
    // el PC reproduce el PCM al ritmo equivocado (voz acelerada/aguda).
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    // OOB port on the host. NO usar 47998/47999/48000/48010: son los puertos de
    // vídeo/control/audio/RTSP de Sunshine y colisionarían con el streaming.
    private static final int HOST_PORT = 48999;

        private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread = null;

    // Retorno de voz (PC→móvil): reutiliza el MISMO socket UDP usado para
    // enviar. El companion recuerda el remitente y devuelve su micrófono a
    // esta misma dirección/puerto, así que basta con escuchar (recibir) en
    // el socket que ya tenemos abierto. Llamada bidireccional real.
    private AudioTrack audioTrack;
    private volatile boolean isPlaying = false;
    private Thread playbackThread = null;

    private NoiseSuppressor noiseSuppressor = null;
    private AcousticEchoCanceler echoCanceler = null;
    private AutomaticGainControl gainControl = null;
    
    private final String hostIp;
    private final String pin;
    private byte[] authPrefix = new byte[0]; // 8 bytes (SHA-256 del PIN) o vacío
    private DatagramSocket socket;
    private InetAddress hostAddress;

    public VoiceCaptureManager(String hostIp) {
        this(hostIp, "");
    }

    public VoiceCaptureManager(String hostIp, String pin) {
        this.hostIp = hostIp;
        this.pin = pin != null ? pin : "";
    }

    /** Prefijo de auth para UDP: primeros 8 bytes del SHA-256 del PIN. */
    private byte[] computeAuthPrefix() {
        if (pin.isEmpty()) return new byte[0];
        try {
            byte[] full = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] prefix = new byte[8];
            System.arraycopy(full, 0, prefix, 0, 8);
            return prefix;
        } catch (Exception e) {
            Log.e(TAG, "No se pudo calcular el prefijo de auth de voz", e);
            return new byte[0];
        }
    }

    public boolean checkPermissions(Activity activity, int requestCode) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    requestCode);
            return false;
        }
        return true;
    }

    public void start() {
        if (isRecording) return;
        
        try {
            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            
            // VOICE_COMMUNICATION activa el pipeline de telecomunicaciones del dispositivo,
            // aplicando cancelación de ruido y eco por hardware de manera mucho más efectiva que MIC.
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize * 2);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Fallo al inicializar AudioRecord");
                return;
            }

            // [CORRECCIÓN CRÍTICA] Habilitar filtros de audio por hardware ANTES de startRecording()
            // Si se hace después, muchos dispositivos Android ignoran los filtros (AEC/NS) 
            // resultando en eco del juego y ruido de fondo estático.
            int sessionId = audioRecord.getAudioSessionId();
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId);
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(true);
                    Log.i(TAG, "NoiseSuppressor activado.");
                }
            } else {
                Log.w(TAG, "NoiseSuppressor NO soportado por este hardware.");
            }
            
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId);
                if (echoCanceler != null) {
                    echoCanceler.setEnabled(true);
                    Log.i(TAG, "AcousticEchoCanceler activado.");
                }
            } else {
                Log.w(TAG, "AcousticEchoCanceler NO soportado por este hardware.");
            }
            
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(sessionId);
                if (gainControl != null) {
                    gainControl.setEnabled(false);
                    Log.i(TAG, "AutomaticGainControl DESACTIVADO para evitar siseo.");
                }
            }

            socket = new DatagramSocket();
            hostAddress = InetAddress.getByName(hostIp);
            authPrefix = computeAuthPrefix();

            audioRecord.startRecording();

            isRecording = true;

            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                    captureAndSend(minBufferSize);
                }
            }, "VoiceCaptureThread");
            recordingThread.start();

            startPlayback();

            Log.i(TAG, "Transmisión de voz iniciada hacia " + hostIp + ":" + HOST_PORT);
        } catch (SecurityException se) {
            Log.e(TAG, "Falta permiso de micrófono", se);
        } catch (Exception e) {
            Log.e(TAG, "Error iniciando captura de voz", e);
            stop();
        }
    }

    private void captureAndSend(int bufferSize) {
        // Reservamos sitio para el prefijo de auth al inicio del datagrama.
        final int pre = authPrefix.length; // 0 u 8
        
        // Tamaño fijo de 960 bytes (10ms a 48kHz 16-bit mono).
        // CRÍTICO para audio profesional: Evita que el paquete supere la MTU (1500 bytes)
        // de la red Wi-Fi. Si enviáramos el bufferSize entero (e.g. 7680 bytes), el router
        // lo fragmentaría, multiplicando la pérdida de paquetes y arruinando la calidad.
        int chunkSize = 960;
        byte[] buffer = new byte[pre + chunkSize];
        if (pre > 0) System.arraycopy(authPrefix, 0, buffer, 0, pre);
        
        while (isRecording && !Thread.interrupted()) {
            int readSize = audioRecord.read(buffer, pre, chunkSize);
            if (readSize > 0) {
                // --- SOFTWARE NOISE GATE ---
                // Calcula la energía (RMS) del frame de audio para detectar si hay voz o solo ruido estático.
                long sum = 0;
                for (int i = pre; i < pre + readSize - 1; i += 2) {
                    short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                    sum += (long) sample * sample;
                }
                double rms = Math.sqrt(sum / (readSize / 2.0));
                
                // Threshold de ruido (reducido a 150 para evitar cortes de voz agresivos). 
                // Si está por debajo de este umbral, silenciamos el frame completamente.
                if (rms < 150) {
                    for (int i = pre; i < pre + readSize; i++) {
                        buffer[i] = 0;
                    }
                }
                // ---------------------------

                try {
                    DatagramPacket packet = new DatagramPacket(buffer, 0, pre + readSize, hostAddress, HOST_PORT);
                    socket.send(packet);
                } catch (Exception e) {
                    Log.e(TAG, "Error enviando paquete de voz", e);
                }
            }
        }
    }

    /** Abre AudioTrack y arranca el hilo que recibe el retorno de voz del PC. */
    private void startPlayback() {
        try {
            int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT);
            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AUDIO_FORMAT)
                            .build())
                    .setBufferSizeInBytes(minBufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            audioTrack.play();
            isPlaying = true;

            playbackThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                    receiveAndPlay();
                }
            }, "VoicePlaybackThread");
            playbackThread.start();
        } catch (Exception e) {
            Log.e(TAG, "No se pudo iniciar la reproducción del retorno de voz", e);
        }
    }

    /**
     * Recibe en el MISMO socket usado para enviar (el companion contesta al
     * remitente) y reproduce el PCM recibido. Sale limpio cuando stop()
     * cierra el socket (receive() lanza excepción y el bucle termina).
     */
    private void receiveAndPlay() {
        final int pre = authPrefix.length;
        byte[] buf = new byte[2048];
        java.net.DatagramPacket packet = new java.net.DatagramPacket(buf, buf.length);
        while (isPlaying && !Thread.interrupted()) {
            try {
                socket.receive(packet);
                int len = packet.getLength();
                if (pre > 0) {
                    if (len <= pre) continue;
                    audioTrack.write(packet.getData(), packet.getOffset() + pre, len - pre);
                } else {
                    audioTrack.write(packet.getData(), packet.getOffset(), len);
                }
            } catch (Exception e) {
                if (isPlaying) {
                    Log.w(TAG, "Retorno de voz interrumpido: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        isRecording = false;
        isPlaying = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                // Ignore
            }
            audioRecord = null;
        }
        
        // Liberar filtros de hardware
        if (noiseSuppressor != null) {
            try { noiseSuppressor.release(); } catch (Exception ignored) {}
            noiseSuppressor = null;
        }
        if (echoCanceler != null) {
            try { echoCanceler.release(); } catch (Exception ignored) {}
            echoCanceler = null;
        }
        if (gainControl != null) {
            try { gainControl.release(); } catch (Exception ignored) {}
            gainControl = null;
        }

        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (recordingThread != null) {
            recordingThread.interrupt();
            recordingThread = null;
        }

        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {}
            audioTrack = null;
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }

        Log.i(TAG, "Transmisión de voz detenida");
    }

    public boolean isRecording() {
        return isRecording;
    }
}
