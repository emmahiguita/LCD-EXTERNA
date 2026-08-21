package com.limelight;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.smartdisplay.bus.SmartDisplayBus;
import com.limelight.utils.ServerHelper;

import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * Pantalla de detalle por PC (diseño "Emma PC"): encabezado con estado real,
 * tarjetas de acción (Escritorio · Control remoto y Steam), Control de energía
 * (Encender WOL + Suspender/Reiniciar/Apagar reales vía bus) y Estado rápido con
 * Ping, CPU y RAM reales. Navegación inferior propia (Inicio / Ajustes).
 */
public class DeviceDetailActivity extends Activity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String uuidString;
    private String pcName;
    private ComputerDetails computer;
    private boolean statsRequested = false;
    private boolean polling = false;

    private TextView statusText;
    private View statusDot;
    private TextView statPing;
    private TextView statCpu;
    private TextView statRam;

    private boolean inForeground = true;

    private final ComputerManagerListener computerListener = new ComputerManagerListener() {
        @Override
        public void notifyComputerUpdated(final ComputerDetails details) {
            if (details == null || uuidString == null
                    || !uuidString.equalsIgnoreCase(details.uuid)) {
                return;
            }
            computer = details;
            mainHandler.post(() -> {
                refreshStatus();
                if (details.state == ComputerDetails.State.ONLINE && !statsRequested) {
                    statsRequested = true;
                    measurePingAsync();
                    fetchStatsAsync();
                }
            });
        }
    };

    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    (ComputerManagerService.ComputerManagerBinder) binder;
            new Thread(() -> {
                localBinder.waitForReady();
                managerBinder = localBinder;
                computer = localBinder.getComputer(uuidString);
                // Sondeo en vivo: refresca estado/acciones cuando el PC cambia
                // (p. ej. UNKNOWN -> ONLINE). Se detiene en segundo plano para no
                // interferir con el emparejamiento de Sunshine (HTTP concurrente).
                mainHandler.post(() -> {
                    refreshStatus();
                    startComputerUpdates();
                });
            }).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            managerBinder = null;
        }
    };

    private void startComputerUpdates() {
        if (managerBinder == null || polling || !inForeground) {
            return;
        }
        managerBinder.startPolling(computerListener);
        polling = true;
    }

    private void stopComputerUpdates() {
        if (managerBinder != null && polling) {
            try {
                managerBinder.stopPolling();
            } catch (Exception ignored) {
            }
        }
        polling = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.limelight.utils.ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_detail);

        uuidString = getIntent().getStringExtra(AppView.UUID_EXTRA);
        pcName = getIntent().getStringExtra(AppView.NAME_EXTRA);

        findViewById(R.id.detail_back).setOnClickListener(v -> onBackPressed());

        View moreBtn = findViewById(R.id.detail_more);
        if (moreBtn != null) {
            moreBtn.setOnClickListener(this::showMoreMenu);
        }

        ((TextView) findViewById(R.id.detail_title)).setText(pcName != null ? pcName : "SmartDisplay");
        statusText = findViewById(R.id.detail_status_text);
        statusDot = findViewById(R.id.detail_status_dot);
        statPing = findViewById(R.id.stat_ping);
        statCpu = findViewById(R.id.stat_cpu);
        statRam = findViewById(R.id.stat_ram);

        String dash = getString(R.string.detail_stat_empty);
        statPing.setText(dash);
        statCpu.setText(dash);
        statRam.setText(dash);

        findViewById(R.id.card_desktop).setOnClickListener(v -> launchDesktop());
        findViewById(R.id.card_steam).setOnClickListener(v -> launchSteam());

        findViewById(R.id.power_on).setOnClickListener(v -> doWol());
        findViewById(R.id.power_suspend).setOnClickListener(v -> sendPowerCommand("suspend"));
        findViewById(R.id.power_restart).setOnClickListener(v -> sendPowerCommand("restart"));
        findViewById(R.id.power_off).setOnClickListener(v -> sendPowerCommand("shutdown"));

        bindService(new Intent(this, ComputerManagerService.class),
                serviceConnection, Context.BIND_AUTO_CREATE);

        runEntranceAnimation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        inForeground = true;
        com.limelight.ui.glass.LiquidGlassCircleLayout hero = findViewById(R.id.deviceHeroOrb);
        if (hero != null) {
            hero.startBreathing(3.5f, 3000L);
        }
        // Al volver (p. ej. tras emparejar/streaming) re-evalúa el estado.
        statsRequested = false;
        refreshStatus();
        startComputerUpdates();
    }

    @Override
    protected void onPause() {
        inForeground = false;
        com.limelight.ui.glass.LiquidGlassCircleLayout hero = findViewById(R.id.deviceHeroOrb);
        if (hero != null) {
            hero.stopBreathing();
        }
        stopComputerUpdates();
        super.onPause();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.activity_fade_enter, R.anim.activity_slide_out_right);
    }

    @Override
    protected void onDestroy() {
        stopComputerUpdates();
        com.limelight.ui.glass.LiquidGlassCircleLayout hero = findViewById(R.id.deviceHeroOrb);
        if (hero != null) {
            hero.stopBreathing();
        }
        try {
            unbindService(serviceConnection);
        } catch (Exception ignored) {
        }
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void refreshStatus() {
        boolean online = computer != null && computer.state == ComputerDetails.State.ONLINE;
        boolean paired = online && computer.pairState == PairingManager.PairState.PAIRED;

        int colorRes;
        if (!online) {
            statusText.setText(R.string.detail_status_offline);
            colorRes = R.color.status_offline;
        } else if (paired) {
            statusText.setText(R.string.detail_status_connected);
            colorRes = R.color.status_online;
        } else {
            statusText.setText(R.string.detail_status_pairing);
            colorRes = R.color.status_pairing;
        }
        if (statusDot != null) {
            statusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, colorRes)));
        }

        // Las acciones de conexión y de energía remota requieren PC en línea.
        // Encender (WOL) permanece siempre disponible (sirve cuando está apagado).
        setActionEnabled(R.id.card_desktop, online);
        setActionEnabled(R.id.card_steam, online);
        setActionEnabled(R.id.power_suspend, online);
        setActionEnabled(R.id.power_restart, online);
        setActionEnabled(R.id.power_off, online);
    }

    private void setActionEnabled(int viewId, boolean enabled) {
        View v = findViewById(viewId);
        if (v == null) {
            return;
        }
        v.setEnabled(enabled);
        v.setClickable(enabled);
        v.setAlpha(enabled ? 1f : 0.4f);
    }

    /** Mide el ping real al PC (conexión TCP a su dirección activa). */
    private void measurePingAsync() {
        if (computer == null || computer.activeAddress == null) {
            return;
        }
        final String host = computer.activeAddress.address;
        final int port = computer.activeAddress.port;
        new Thread(() -> {
            long ms = -1;
            try (Socket socket = new Socket()) {
                long start = System.currentTimeMillis();
                socket.connect(new InetSocketAddress(host, port), 1500);
                ms = System.currentTimeMillis() - start;
            } catch (Exception ignored) {
            }
            final long result = ms;
            mainHandler.post(() -> {
                if (result >= 0) {
                    statPing.setText(result + " ms");
                }
            });
        }).start();
    }

    /** Pide CPU/RAM reales al companion por el bus (respuesta {"type":"stats",...}). */
    private void fetchStatsAsync() {
        if (computer == null || computer.activeAddress == null
                || computer.state != ComputerDetails.State.ONLINE) {
            return;
        }
        final String host = computer.activeAddress.address;
        final String pin = android.preference.PreferenceManager
                .getDefaultSharedPreferences(this).getString("companion_pin", "");

        final SmartDisplayBus[] ref = new SmartDisplayBus[1];
        SmartDisplayBus bus = new SmartDisplayBus(host, new SmartDisplayBus.MessageListener() {
            @Override
            public void onConnected() {
                try {
                    JSONObject req = new JSONObject();
                    req.put("type", "get_stats");
                    ref[0].sendMessage(req);
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onMessageReceived(JSONObject json) {
                if (!"stats".equals(json.optString("type"))) {
                    return;
                }
                final int cpu = json.optInt("cpu", -1);
                final int ram = json.optInt("ram", -1);
                mainHandler.post(() -> {
                    if (cpu >= 0) statCpu.setText(cpu + "%");
                    if (ram >= 0) statRam.setText(ram + "%");
                });
                ref[0].stop();
            }

            @Override
            public void onDisconnected() {
            }
        }, pin);
        ref[0] = bus;
        bus.start();
        mainHandler.postDelayed(bus::stop, 5000);

        // Si seguimos en primer plano, programa la siguiente medición en 3s
        if (inForeground && computer.state == ComputerDetails.State.ONLINE) {
            mainHandler.postDelayed(() -> {
                if (inForeground && computer != null && computer.state == ComputerDetails.State.ONLINE) {
                    measurePingAsync();
                    fetchStatsAsync();
                }
            }, 3000);
        }
    }

    // ── Acciones ────────────────────────────────────────────────────────────

    private void launchDesktop() {
        launchApp(null); // null = escritorio remoto
    }

    private void launchSteam() {
        launchApp("steam");
    }

    private long lastLaunchTime = 0;

    /**
     * Lanza una app por nombre. Si el PC ya está emparejado, usa el camino probado
     * (ServerHelper.doStart con el binder propio de esta pantalla, que es válido).
     * Si falta emparejar, delega en ShortcutTrampoline (que empareja y conecta).
     */
    private void launchApp(final String targetName) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastLaunchTime < 1000) {
            return;
        }
        lastLaunchTime = now;

        final ComputerManagerService.ComputerManagerBinder binder = managerBinder;
        final ComputerDetails c = computer;
        if (c == null || c.state != ComputerDetails.State.ONLINE || c.activeAddress == null) {
            Toast.makeText(this, R.string.detail_power_offline, Toast.LENGTH_SHORT).show();
            return;
        }
        // Si aún no está emparejado, ShortcutTrampoline maneja el emparejamiento + conexión.
        if (binder == null || c.pairState != PairingManager.PairState.PAIRED) {
            launchViaTrampoline(targetName);
            return;
        }
        Toast.makeText(this, R.string.detail_connecting, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                NvHTTP http = new NvHTTP(
                        ServerHelper.getCurrentAddressFromComputer(c),
                        c.httpsPort,
                        binder.getUniqueId(),
                        c.serverCert,
                        PlatformBinding.getCryptoProvider(DeviceDetailActivity.this));
                List<NvApp> apps = http.getAppList();
                final NvApp target = findApp(apps, targetName);
                mainHandler.post(() -> {
                    if (target == null) {
                        // Si no se halló la app pedida (p. ej. Steam), avisar y abrir la lista.
                        if (targetName != null && !targetName.isEmpty()) {
                            Toast.makeText(DeviceDetailActivity.this,
                                    getString(R.string.detail_app_not_found, targetName),
                                    Toast.LENGTH_SHORT).show();
                        }
                        openAppList();
                    } else {
                        ServerHelper.doStart(DeviceDetailActivity.this, target, c, binder);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> launchViaTrampoline(targetName));
            }
        }).start();
    }

    /** Busca la app objetivo por nombre; si es null, busca el escritorio remoto. */
    private NvApp findApp(List<NvApp> apps, String targetName) {
        if (apps == null || apps.isEmpty()) {
            return null;
        }
        for (NvApp a : apps) {
            String n = a.getAppName() != null ? a.getAppName().toLowerCase() : "";
            if (targetName != null && !targetName.isEmpty()) {
                if (n.contains(targetName.toLowerCase())) {
                    return a;
                }
            } else if (n.contains("desktop") || n.contains("escritorio") || n.contains("mstsc")) {
                return a;
            }
        }
        // Escritorio: si no hay una app con ese nombre, usar la primera disponible.
        return (targetName == null || targetName.isEmpty()) ? apps.get(0) : null;
    }

    private void launchViaTrampoline(String targetName) {
        Intent i = new Intent(this, ShortcutTrampoline.class);
        i.putExtra(AppView.NAME_EXTRA, pcName);
        i.putExtra(AppView.UUID_EXTRA, uuidString);
        i.putExtra("AUTO_CONNECT", true);
        if (targetName != null && !targetName.isEmpty()) {
            i.putExtra("TARGET_APP_NAME", targetName);
        }
        i.setAction(Intent.ACTION_DEFAULT);
        startActivity(i);
    }

    private void openAppList() {
        Intent i = new Intent(this, AppView.class);
        i.putExtra(AppView.NAME_EXTRA, pcName);
        i.putExtra(AppView.UUID_EXTRA, uuidString);
        startActivity(i);
    }

    // ── Control de energía ──────────────────────────────────────────────────

    private void doWol() {
        if (computer == null) {
            return;
        }
        final ComputerDetails target = computer;
        Toast.makeText(this, R.string.detail_wol_sent, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                WakeOnLanSender.sendWolPacket(target);
            } catch (Exception ignored) {
            }
        }).start();
    }

    /** Punto de entrada: valida, y si es destructiva (reiniciar/apagar) pide confirmación. */
    private void sendPowerCommand(final String action) {
        if (computer == null || computer.activeAddress == null
                || computer.state != ComputerDetails.State.ONLINE) {
            Toast.makeText(this, R.string.detail_power_offline, Toast.LENGTH_SHORT).show();
            return;
        }
        String name = pcName != null ? pcName : "PC";
        if ("shutdown".equals(action) || "restart".equals(action)) {
            int msg = "shutdown".equals(action)
                    ? R.string.detail_power_confirm_shutdown
                    : R.string.detail_power_confirm_restart;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setMessage(getString(msg, name))
                    .setPositiveButton(R.string.detail_power_confirm_ok, (d, w) -> doPowerCommand(action))
                    .setNegativeButton(R.string.detail_power_cancel, null)
                    .show();
        } else {
            doPowerCommand(action);
        }
    }

    /** Envía el comando de energía real por el bus (el companion lo ejecuta en el PC). */
    private void doPowerCommand(final String action) {
        final String host = computer.activeAddress.address;
        final String pin = android.preference.PreferenceManager
                .getDefaultSharedPreferences(this).getString("companion_pin", "");

        final SmartDisplayBus[] ref = new SmartDisplayBus[1];
        SmartDisplayBus bus = new SmartDisplayBus(host, new SmartDisplayBus.MessageListener() {
            @Override
            public void onConnected() {
                try {
                    JSONObject j = new JSONObject();
                    j.put("type", "power");
                    j.put("action", action);
                    ref[0].sendMessage(j);
                } catch (Exception ignored) {
                }
                ref[0].stop();
            }

            @Override
            public void onMessageReceived(JSONObject json) {
            }

            @Override
            public void onDisconnected() {
            }
        }, pin);
        ref[0] = bus;
        bus.start();
        mainHandler.postDelayed(bus::stop, 4000);

        int msg;
        switch (action) {
            case "suspend": msg = R.string.detail_power_suspending; break;
            case "restart": msg = R.string.detail_power_restarting; break;
            default:        msg = R.string.detail_power_shutting; break;
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void showMoreMenu(View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.applist_refresh_title);
        popup.getMenu().add(0, 2, 1, R.string.settings);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openAppList();
                return true;
            } else if (item.getItemId() == 2) {
                startActivity(new Intent(this, com.limelight.preferences.StreamSettings.class));
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void runEntranceAnimation() {
        final View header = findViewById(R.id.header);
        final com.limelight.ui.glass.LiquidGlassCircleLayout heroOrb = findViewById(R.id.deviceHeroOrb);
        final View systemMetrics = findViewById(R.id.systemMetrics);
        final View desktopAction = findViewById(R.id.card_desktop);
        final View steamAction = findViewById(R.id.card_steam);
        final View powerOn = findViewById(R.id.power_on);
        final View powerSuspend = findViewById(R.id.power_suspend);
        final View powerRestart = findViewById(R.id.power_restart);
        final View powerOff = findViewById(R.id.power_off);

        android.view.animation.Interpolator decel = new android.view.animation.DecelerateInterpolator(1.8f);
        android.view.animation.Interpolator overshoot = new android.view.animation.OvershootInterpolator(1.25f);

        // 1. Header (Slide Down + Fade In)
        if (header != null) {
            header.setAlpha(0f);
            header.setTranslationY(-dpToPx(35));
            header.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(320L)
                    .setInterpolator(decel)
                    .start();
        }

        // 2. Hero Orb (Pop Up + Scale + Overshoot + Idle Floating)
        if (heroOrb != null) {
            heroOrb.setAlpha(0f);
            heroOrb.setScaleX(0.78f);
            heroOrb.setScaleY(0.78f);
            heroOrb.setTranslationY(dpToPx(40));
            heroOrb.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(420L)
                    .setInterpolator(overshoot)
                    .withEndAction(() -> {
                        if (inForeground) {
                            heroOrb.startBreathing(3.5f, 3000L);
                        }
                    })
                    .start();
        }

        // 3. System Metrics (Slide Up + Fade In)
        if (systemMetrics != null) {
            systemMetrics.setAlpha(0f);
            systemMetrics.setTranslationY(dpToPx(30));
            systemMetrics.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(80L)
                    .setDuration(340L)
                    .setInterpolator(decel)
                    .start();
        }

        // 4. Desktop & Steam Actions (Staggered Pop In)
        if (desktopAction != null) {
            desktopAction.setAlpha(0f);
            desktopAction.setScaleX(0.80f);
            desktopAction.setScaleY(0.80f);
            desktopAction.setTranslationY(dpToPx(25));
            desktopAction.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setStartDelay(140L)
                    .setDuration(360L)
                    .setInterpolator(overshoot)
                    .start();
        }

        if (steamAction != null) {
            steamAction.setAlpha(0f);
            steamAction.setScaleX(0.80f);
            steamAction.setScaleY(0.80f);
            steamAction.setTranslationY(dpToPx(25));
            steamAction.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setStartDelay(190L)
                    .setDuration(360L)
                    .setInterpolator(overshoot)
                    .start();
        }

        // 5. Power Row (Cascade Pop In)
        View[] powerButtons = new View[]{powerOn, powerSuspend, powerRestart, powerOff};
        long powerDelay = 240L;
        for (View btn : powerButtons) {
            if (btn != null) {
                btn.setAlpha(0f);
                btn.setScaleX(0.65f);
                btn.setScaleY(0.65f);
                btn.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setStartDelay(powerDelay)
                        .setDuration(300L)
                        .setInterpolator(overshoot)
                        .start();
                powerDelay += 45L;
            }
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
