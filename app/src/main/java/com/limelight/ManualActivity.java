package com.limelight;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

/**
 * Manual de uso in-app. Pantalla nativa (no WebView) que explica, paso a paso,
 * cómo conectar SmartDisplay a un servidor y usar sus funciones. Se construye
 * mediante tarjetas temáticas para que respete el tema activo sin excepción.
 */
public class ManualActivity extends Activity {

    // Cada entrada: {título de sección, cuerpo}. El cuerpo admite saltos de línea.
    // Los enlaces http(s) se vuelven tocables automáticamente (Linkify).
    private static final String[][] SECTIONS = {
            {"1 · ¿Qué es SmartDisplay?",
                    "SmartDisplay convierte tu teléfono en una ventana hacia tu PC: transmite en vivo "
                    + "la pantalla del ordenador con baja latencia y te permite controlarlo como si "
                    + "estuvieras frente a él. La PC es la protagonista; la app solo asiste."},

            {"2 · Descargas en el PC",
                    "Instala en tu computadora (Windows) el servidor de streaming y, si quieres jugar "
                    + "fuera de casa, la VPN Tailscale. Toca para descargar:\n\n"
                    + "• Sunshine (servidor de streaming):\n"
                    + "https://github.com/LizardByte/Sunshine/releases/latest/download/sunshine-windows-x64-setup.exe\n\n"
                    + "• Tailscale para PC (opcional, uso remoto):\n"
                    + "https://pkgs.tailscale.com/stable/tailscale-setup-latest.exe\n\n"
                    + "• Tailscale para Android (opcional):\n"
                    + "https://tailscale.com/download/android"},

            {"3 · Instalar Sunshine y su consola",
                    "1. Ejecuta el instalador .exe de Sunshine en el PC.\n"
                    + "2. Abre la consola web escribiendo en el navegador del PC:\n"
                    + "http://localhost:47990\n"
                    + "3. En el primer acceso, Sunshine te obliga a crear un USUARIO y CONTRASEÑA para "
                    + "proteger la consola. Anótalos: los usarás en el emparejamiento automático.\n"
                    + "4. Ya verás el panel con el estado del servidor, la GPU y las sesiones activas."},

            {"4 · Aplicaciones y juegos en Sunshine",
                    "En la pestaña «Applications» (https://localhost:47990/apps) verás lo que aparece "
                    + "en tu teléfono. Sunshine trae dos por defecto:\n"
                    + "• Desktop: transmite el escritorio completo de Windows.\n"
                    + "• Steam Big Picture: abre Steam en modo mando/pantalla táctil.\n\n"
                    + "Para AÑADIR un juego/programa:\n"
                    + "1. «Applications» → «+ Add New».\n"
                    + "2. Application Name: el nombre del juego.\n"
                    + "3. Command: la ruta del .exe (p. ej. C:\\...\\Cyberpunk2077.exe).\n"
                    + "4. Working Directory: la carpeta que contiene ese .exe.\n"
                    + "5. (Opcional) Image Path: una imagen .png de portada.\n"
                    + "6. «Save» y luego, arriba a la derecha, «Apply» para reiniciar el servicio y que "
                    + "tu celular detecte los cambios."},

            {"5 · Conectar el teléfono",
                    "1. Abre el menú lateral (☰) y toca «Agregar equipo».\n"
                    + "2. Si el PC está en tu red, aparecerá automáticamente en «Tus equipos».\n"
                    + "3. Si no aparece, escribe la dirección IP del PC (o su IP de Tailscale 100.x.y.z).\n"
                    + "4. Toca el equipo para iniciar el emparejamiento."},

            {"6 · Emparejamiento por PIN",
                    "Al conectar por primera vez, SmartDisplay genera un PIN.\n\n"
                    + "• Manual: abre la pestaña «PIN» de la consola de Sunshine "
                    + "(https://localhost:47990), escribe el PIN del teléfono y pulsa «Send».\n\n"
                    + "• Automático (recomendado): en Ajustes de SmartDisplay guarda el MISMO usuario y "
                    + "contraseña que creaste en Sunshine. La app enviará el PIN sola en segundo plano "
                    + "(POST https://IP:47990/api/pin) y el enlace será inmediato.\n\n"
                    + "Una vez emparejado, el equipo queda «Listo para conectar» y no vuelve a pedir PIN."},

            {"7 · Jugar fuera de casa (Tailscale)",
                    "Para transmitir con datos móviles (4G/5G) u otra red Wi-Fi:\n"
                    + "1. Instala Tailscale en el PC y en el teléfono (enlaces en la sección Descargas).\n"
                    + "2. Inicia sesión en AMBOS con la MISMA cuenta (Gmail, Microsoft o GitHub).\n"
                    + "3. Cada dispositivo recibe una IP privada 100.x.y.z.\n"
                    + "4. Al abrir SmartDisplay, detectará tu PC por el túnel seguro y podrás transmitir "
                    + "estés donde estés."},

            {"8 · Iniciar el streaming",
                    "Toca el botón de reproducir del equipo o selecciona una app/escritorio de la lista. "
                    + "La transmisión empieza en pantalla completa. Para terminar, usa el menú flotante "
                    + "y elige «Salir»."},

            {"9 · Barra flotante y herramientas",
                    "Durante el streaming verás un botón flotante que puedes ARRASTRAR a cualquier lugar. "
                    + "Al tocarlo se despliega en abanico: Teclado, Modo Mouse, Zoom, Archivos, Voz, "
                    + "Barra PC (ventanas reales del PC), Dev (compilar/ejecutar) y PiP.\n"
                    + "Cuando no lo usas, se encoge para no estorbar."},

            {"10 · Transferencia de archivos",
                    "Desde el menú lateral, abre «Archivos» para enviar o recibir documentos entre el "
                    + "teléfono y el PC mientras están conectados en la misma red."},

            {"11 · Temas y apariencia",
                    "En «Diseño y temas» eliges entre Universo (neón), Pixel, Esmeralda, Ámbar, Grafito, "
                    + "Cristal (glassmorphism) y Lluvia (con animación). Además puedes alternar modo claro "
                    + "u oscuro. El tema se aplica a toda la app al instante."},

            {"12 · Ajustes recomendados",
                    "• Resolución y FPS: ajústalos a tu red. 1080p/60 es un buen punto de partida.\n"
                    + "• Bitrate: súbelo si la imagen se ve borrosa; bájalo si hay cortes.\n"
                    + "• Companion PIN: escríbelo en Ajustes si tu companion del PC usa autenticación."},

            {"13 · Solución de problemas",
                    "• No aparece el PC: verifica que Sunshine esté activo y ambos en la misma red "
                    + "(o Tailscale encendido en los dos).\n"
                    + "• Imagen entrecortada: baja el bitrate o la resolución, o acércate al router.\n"
                    + "• No empareja: revisa el firewall del PC y que el PIN/credenciales sean correctos.\n"
                    + "• Usa «Diagnóstico» en el menú lateral para probar la red."},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.limelight.utils.ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual);

        View btnClose = findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        LinearLayout container = findViewById(R.id.manual_container);
        for (String[] section : SECTIONS) {
            container.addView(buildSectionCard(section[0], section[1]));
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.activity_fade_enter, R.anim.activity_slide_out_right);
    }

    private MaterialCardView buildSectionCard(String title, String body) {
        int pad = dp(18);
        int gap = dp(12);

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = gap;
        card.setLayoutParams(cardLp);
        card.setCardBackgroundColor(0xFF0F1826);
        card.setRadius(dp(20));
        card.setStrokeColor(0x3378A6D8);
        card.setStrokeWidth(dp(1));
        card.setCardElevation(dp(2));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(pad, pad, pad, pad);
        inner.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(0xFF7DDAFF);

        TextView bodyView = new TextView(this);
        bodyView.setText(body);
        bodyView.setTextColor(0xFFCBD5E1);
        bodyView.setTextSize(14f);
        bodyView.setLineSpacing(dp(3), 1.15f);
        // Vuelve tocables los enlaces http(s); Linkify instala LinkMovementMethod solo.
        android.text.util.Linkify.addLinks(bodyView, android.text.util.Linkify.WEB_URLS);
        bodyView.setLinkTextColor(0xFF7DDAFF);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = dp(8);
        bodyView.setLayoutParams(bodyLp);

        inner.addView(titleView);
        inner.addView(bodyView);
        card.addView(inner);
        return card;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics()));
    }
}
