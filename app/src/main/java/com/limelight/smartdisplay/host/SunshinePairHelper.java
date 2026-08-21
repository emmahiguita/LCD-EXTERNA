package com.limelight.smartdisplay.host;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.limelight.LimeLog;

import java.io.OutputStream;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * SmartDisplay AI – Host Layer: emparejamiento automático con Sunshine.
 *
 * En lugar de obligar al usuario a abrir la web de Sunshine y teclear el PIN,
 * la app lo envía sola a la API de Sunshine:
 *   POST https://<host>:47990/api/pin   (Basic Auth: admin / clave)
 *   body {"pin":"9999","name":"SmartDisplay"}
 *
 * Se ejecuta en paralelo al handshake de Moonlight: la petición getservercert
 * queda esperando el PIN, y este POST lo entrega. Reintenta unas veces para
 * ganar la carrera con el registro de la sesión de emparejamiento.
 *
 * NO toca el core de streaming: solo habla con el endpoint de configuración web.
 *
 * Credenciales: por defecto admin/admin1234, configurables vía SharedPreferences
 * ("sd_sunshine"), para no quedar fijas en el código.
 *
 * NOTA DE SEGURIDAD: usa trust-all TLS (cert autofirmado de Sunshine en 47990)
 * y credenciales de admin; pensado para red local de confianza.
 */
public class SunshinePairHelper {

    private static final String TAG = "SD_SunshinePair";
    public static final int WEB_PORT = 47990;

    private static final String PREFS = "sd_sunshine";
    private static final String KEY_USER = "web_user";
    private static final String KEY_PASS = "web_pass";
    private static final String DEFAULT_USER = "admin";
    private static final String DEFAULT_PASS = "admin1234";

    private static final int MAX_ATTEMPTS = 6;
    private static final long FIRST_DELAY_MS = 700;
    private static final long RETRY_DELAY_MS = 800;

    /** Lee las credenciales web de Sunshine (con valores por defecto). */
    public static String getUser(Context ctx) {
        return prefs(ctx).getString(KEY_USER, DEFAULT_USER);
    }
    public static String getPass(Context ctx) {
        return prefs(ctx).getString(KEY_PASS, DEFAULT_PASS);
    }
    public static void setCredentials(Context ctx, String user, String pass) {
        prefs(ctx).edit().putString(KEY_USER, user).putString(KEY_PASS, pass).apply();
    }
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Envía el PIN a Sunshine de forma asíncrona (en su propio hilo), con
     * reintentos. Llamar JUSTO al iniciar el emparejamiento.
     */
    public static void submitPinAsync(final Context ctx, final String host, final String pin,
                                      final String deviceName) {
        final String user = getUser(ctx);
        final String pass = getPass(ctx);
        new Thread() {
            @Override
            public void run() {
                for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                    try {
                        Thread.sleep(attempt == 0 ? FIRST_DELAY_MS : RETRY_DELAY_MS);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (postPin(host, pin, user, pass, deviceName)) {
                        LimeLog.info(TAG + ": PIN enviado a Sunshine automáticamente (intento " + (attempt + 1) + ")");
                        return;
                    }
                }
                LimeLog.warning(TAG + ": no se pudo auto-enviar el PIN; el usuario puede teclearlo en Sunshine");
            }
        }.start();
    }

    private static boolean postPin(String host, String pin, String user, String pass, String deviceName) {
        HttpsURLConnection conn = null;
        try {
            URL url = new URL("https://" + host + ":" + WEB_PORT + "/api/pin");
            conn = (HttpsURLConnection) url.openConnection();
            conn.setSSLSocketFactory(trustAllFactory());
            conn.setHostnameVerifier((h, s) -> true);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);

            String credentials = user + ":" + pass;
            String auth = Base64.encodeToString(credentials.getBytes("UTF-8"), Base64.NO_WRAP);
            conn.setRequestProperty("Authorization", "Basic " + auth);
            conn.setRequestProperty("Content-Type", "application/json");

            String safeName = deviceName == null ? "SmartDisplay" : deviceName.replace("\"", "");
            String body = "{\"pin\":\"" + pin + "\",\"name\":\"" + safeName + "\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            String resp = readBody(conn);
            // Sunshine responde {"status":"true"} al aceptar el PIN
            boolean ok = (code >= 200 && code < 300) && resp != null && resp.contains("true");
            if (!ok) {
                LimeLog.info(TAG + ": respuesta /api/pin code=" + code + " body=" + resp);
            }
            return ok;
        } catch (Exception e) {
            // Conexión aún no lista / cert / red: se reintenta
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readBody(HttpsURLConnection conn) {
        try {
            java.io.InputStream is = (conn.getResponseCode() >= 400)
                    ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) return null;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) != -1) bo.write(buf, 0, n);
            is.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static SSLSocketFactory trustAllFactory() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx.getSocketFactory();
    }
}
