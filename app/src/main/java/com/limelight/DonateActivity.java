package com.limelight;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

/**
 * Pantalla de donaciones premium (Material 3): explica el apoyo, muestra el QR
 * de PayPal y ofrece Donar / Compartir / Copiar enlace, con animación de entrada,
 * feedback por Snackbar y manejo de sin-conexión. Respeta el tema claro/oscuro.
 */
public class DonateActivity extends Activity {

    private boolean awaitingReturn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.limelight.utils.ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donate);

        View btnClose = findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        // Versión real de la app.
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            ((TextView) findViewById(R.id.donate_version)).setText(v);
        } catch (Exception ignored) {
        }

        findViewById(R.id.donate_paypal_btn).setOnClickListener(v -> donate());
        findViewById(R.id.donate_share_btn).setOnClickListener(v -> share());
        findViewById(R.id.donate_copy_btn).setOnClickListener(v -> copyLink());

        // Animación de entrada: fade + scale (Material 3 Expressive).
        final View content = findViewById(R.id.donate_content);
        if (content != null) {
            content.setAlpha(0f);
            content.setScaleX(0.95f);
            content.setScaleY(0.95f);
            content.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(320)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.activity_fade_enter, R.anim.activity_slide_out_right);
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.limelight.ui.glass.LiquidGlassCircleLayout hero = findViewById(R.id.donateHeroOrb);
        if (hero != null) {
            hero.startBreathing(3f, 2800L);
        }

        // Al volver de PayPal, agradecer con un Snackbar elegante.
        if (awaitingReturn) {
            awaitingReturn = false;
            snack(getString(R.string.donate_thanks));
        }
    }

    @Override
    protected void onPause() {
        com.limelight.ui.glass.LiquidGlassCircleLayout hero = findViewById(R.id.donateHeroOrb);
        if (hero != null) {
            hero.stopBreathing();
        }
        super.onPause();
    }

    private String payPalUrl() {
        return getString(R.string.donate_paypal_url);
    }

    private void donate() {
        if (!isOnline()) {
            snack(getString(R.string.donate_offline));
            return;
        }
        Toast.makeText(this, R.string.donate_opening, Toast.LENGTH_SHORT).show();
        try {
            // ACTION_VIEW abre la app de PayPal si está instalada, o el navegador.
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(payPalUrl())));
            awaitingReturn = true;
        } catch (Exception e) {
            snack(getString(R.string.donate_no_paypal));
        }
    }

    private void share() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, getString(R.string.donate_title) + "\n" + payPalUrl());
        startActivity(Intent.createChooser(send, getString(R.string.donate_share)));
    }

    private void copyLink() {
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cb != null) {
            cb.setPrimaryClip(ClipData.newPlainText("PayPal", payPalUrl()));
            snack(getString(R.string.donate_copied));
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return true; // sin forma de comprobar → no bloquear
        }
        NetworkInfo net = cm.getActiveNetworkInfo();
        return net != null && net.isConnected();
    }

    private void snack(String msg) {
        Snackbar.make(findViewById(R.id.donate_root), msg, Snackbar.LENGTH_SHORT).show();
    }
}
