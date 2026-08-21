# Guía Paso a Paso de Conexión: SmartDisplay AI, Sunshine y Tailscale

Este documento es una guía de implementación real con capturas de pantalla tomadas directamente de los paneles de administración y descarga en tu PC, detallando el flujo de configuración local, remoto y el emparejamiento automatizado.

---

## 1. Descargas Directas de Software (PC)

Para configurar la infraestructura de streaming en tu computadora, haz clic en los siguientes enlaces oficiales de descarga directa:

*   **Sunshine (Servidor de Streaming - Windows):** [Descargar instalador Sunshine (PC - .exe)](https://github.com/LizardByte/Sunshine/releases/latest/download/sunshine-windows-x64-setup.exe)
*   **Tailscale (VPN de Malla Segura - Windows):** [Descargar instalador Tailscale (PC - .exe)](https://pkgs.tailscale.com/stable/tailscale-setup-latest.exe)
*   **Tailscale (VPN de Malla Segura - Android):** [Descargar Tailscale para Android](https://tailscale.com/download/android)

---

## 2. Paso a Paso con Imágenes de PC

### Paso 1: Instalación de Sunshine y Consola de Administración
Una vez completado el instalador `.exe` de Sunshine, ingresa a la consola web local escribiendo `http://localhost:47990` en tu navegador. 

**Creación de Credenciales:** En tu primer acceso, Sunshine te obligará a crear un **Usuario** y una **Contraseña** para proteger la consola. Define el nombre de usuario y la contraseña que prefieras. Tras crearlos, accederás al panel de control general, que luce así:

![Dashboard General de Sunshine](images/sunshine_status_page_1782957738248.png)

Aquí puedes visualizar el estado del servidor de streaming, la tarjeta gráfica activa empleada para la codificación de vídeo y las sesiones activas en tiempo real.

---

### Paso 2: Configuración de Aplicaciones y Juegos
Para verificar o añadir juegos a la lista que será visible en la pantalla móvil de **SmartDisplay AI**, haz clic en la pestaña **Applications** (o navega a `https://localhost:47990/apps`). Verás el siguiente panel:

![Panel de Aplicaciones de Sunshine](images/sunshine_apps_page_1782957744657.png)

Por defecto, Sunshine viene preconfigurado con dos aplicaciones principales:
*   **Desktop:** Permite transmitir e interactuar con el escritorio completo de Windows.
*   **Steam Big Picture:** Lanza directamente la interfaz de Steam adaptada a mandos y pantallas táctiles.

---

### Paso 2.1: ¿Cómo Agregar Nuevos Juegos o Aplicaciones?
Para agregar tus propios juegos o programas a la biblioteca de streaming y verlos en tu celular:

1. Ve a la pestaña **Applications** y haz clic en el botón **+ Add New** (Añadir Nuevo).
2. Se abrirá el formulario de configuración:

![Formulario para agregar aplicación en Sunshine](images/sunshine_add_game_1782958077171.png)

3. Rellena los campos obligatorios:
   *   **Application Name:** Escribe el nombre comercial del juego (ej. *Cyberpunk 2077*).
   *   **Command:** Escribe la ruta completa del archivo ejecutable `.exe` del juego (ej. `C:\Steam\steamapps\common\Cyberpunk 2077\bin\x64\Cyberpunk2077.exe`).
   *   **Working Directory:** Escribe la ruta de la carpeta que contiene el ejecutable (ej. `C:\Steam\steamapps\common\Cyberpunk 2077\bin\x64\`).
   *   *(Opcional)* **Image Path:** Si deseas que se vea premium, agrega la ruta a una imagen local `.png` de portada.
4. Desplázate hasta abajo y haz clic en **Save** (Guardar).
5. ⚠️ **Paso Obligatorio:** En la esquina superior derecha de la consola web aparecerá una notificación. Haz clic en **Apply** para reiniciar el servicio y publicar tus cambios de manera que tu celular los detecte.

---

### Paso 3: Configuración de Red Remota con Tailscale (Fuera de Casa)
Para transmitir juegos desde tu computadora hacia tu teléfono celular cuando estés en la calle con datos móviles (4G/5G) o en otra red Wi-Fi, descarga el instalador oficial de Tailscale PC. La página de descargas luce así:

![Página de Descarga de Tailscale](images/tailscale_download_page_1782957757873.png)

1. Instala el archivo `.exe` en tu PC y la aplicación de Tailscale en tu celular Android.
2. Inicia sesión en ambos usando la **misma cuenta** (ej. Gmail, Microsoft o GitHub).
3. Una vez encendida la VPN en ambos dispositivos, tu PC y tu teléfono recibirán una dirección IP privada de Tailscale (que comienza con <code>100.x.y.z</code>).
4. ¡Y listo! Al abrir SmartDisplay AI en tu teléfono, este detectará tu PC automáticamente a través del túnel seguro de Tailscale y podrás streamear sin importar dónde estés.

---

### Paso 4: Emparejamiento de Dispositivos por PIN

Cuando intentas conectar tu teléfono celular por primera vez, **SmartDisplay AI** genera un código PIN único en la pantalla. 

Para autorizar la conexión, ve a la pestaña **PIN** del panel de control de Sunshine:

![Panel de Enlace PIN de Sunshine](images/sunshine_pin_page_1782957244986.png)

*   **Enlace Manual:** Introduce el PIN del celular en el recuadro y presiona **Send**.
*   **Enlace Automático (SmartDisplay AI):** Si configuras las mismas credenciales que creaste para Sunshine en la sección de Ajustes de **SmartDisplay AI**, la aplicación móvil enviará este PIN al API de Sunshine de forma asíncrona en segundo plano mediante un servicio automático (`POST https://<IP>:47990/api/pin`), haciendo que el enlace sea inmediato y transparente.

---

## 3. Código Fuente del Enlace Automático en Android

La automatización del PIN móvil se controla en el archivo [SunshinePairHelper.java](file:///c:/Users/emman/Desktop/Proyectos/SmartDisplay/Android/app/src/main/java/com/limelight/smartdisplay/host/SunshinePairHelper.java). A continuación se muestra la implementación Java real que gestiona la lectura de tus credenciales desde SharedPreferences y realiza el envío seguro:

```java
package com.limelight.smartdisplay.host;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.limelight.LimeLog;
import java.io.OutputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class SunshinePairHelper {
    private static final String TAG = "SD_SunshinePair";
    public static final int WEB_PORT = 47990;
    
    private static final String PREFS = "sd_sunshine";
    private static final String KEY_USER = "web_user";
    private static final String KEY_PASS = "web_pass";
    private static final String DEFAULT_USER = "admin";
    private static final String DEFAULT_PASS = "admin1234";

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
     * Envía el PIN a Sunshine de forma asíncrona en un hilo de fondo.
     */
    public static void submitPinAsync(final Context ctx, final String host, final String pin, final String deviceName) {
        final String user = getUser(ctx);
        final String pass = getPass(ctx);
        new Thread() {
            @Override
            public void run() {
                for (int attempt = 0; attempt < 6; attempt++) {
                    try {
                        Thread.sleep(attempt == 0 ? 700 : 800);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (postPin(host, pin, user, pass, deviceName)) {
                        LimeLog.info(TAG + ": PIN enviado a Sunshine automáticamente (intento " + (attempt + 1) + ")");
                        return;
                    }
                }
                LimeLog.warning(TAG + ": no se pudo auto-enviar el PIN; el usuario deberá teclearlo manualmente");
            }
        }.start();
    }

    private static boolean postPin(String host, String pin, String user, String pass, String deviceName) {
        HttpsURLConnection conn = null;
        try {
            URL url = new URL("https://" + host + ":" + WEB_PORT + "/api/pin");
            conn = (HttpsURLConnection) url.openConnection();
            
            // Bypass de SSL (Sunshine utiliza certificados autofirmados localmente)
            conn.setSSLSocketFactory(trustAllFactory());
            conn.setHostnameVerifier((h, s) -> true);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setDoOutput(true);

            // Basic Auth Header
            String credentials = user + ":" + pass;
            String auth = Base64.encodeToString(credentials.getBytes("UTF-8"), Base64.NO_WRAP);
            conn.setRequestProperty("Authorization", "Basic " + auth);
            conn.setRequestProperty("Content-Type", "application/json");

            // JSON Body
            String safeName = deviceName == null ? "SmartDisplay" : deviceName.replace("\"", "");
            String body = "{\"pin\":\"" + pin + "\",\"name\":\"" + safeName + "\"}";
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            return (code >= 200 && code < 300);
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static SSLSocketFactory trustAllFactory() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
            }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new java.security.SecureRandom());
        return ctx.getSocketFactory();
    }
}
```
