package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.PcGridAdapter;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.AddComputerManually;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.StreamSettings;
import com.limelight.ui.effects.ConnectionVisualState;
import com.limelight.ui.effects.LiquidStartButton;
import com.limelight.utils.Dialog;
import com.limelight.utils.HelpLauncher;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.ActivityManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import org.xmlpull.v1.XmlPullParserException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PcView extends Activity {
    private View noPcFoundLayout;
    private PcGridAdapter pcGridAdapter;
    private ShortcutHelper shortcutHelper;

    // SmartDisplay UX Layer: estado de red en vivo + tarjeta "Última sesión"
    private View headerStatusDot;
    private android.widget.TextView headerNetworkText;
    private android.net.ConnectivityManager.NetworkCallback networkCallback;
    private com.limelight.smartdisplay.recents.LastSessionStore lastSessionStore;
    private View lastSessionCard;
    private android.widget.TextView lastSessionTitle, lastSessionSubtitle;

    // SmartDisplay 3D Hero & Liquid Start Button
    private LiquidStartButton liquidStartButton;
    private android.widget.TextView heroPcName;
    private android.widget.TextView heroStatusText;
    private android.widget.TextView capsuleLatencyText;
    private android.widget.TextView capsuleFpsText;
    private ComputerObject activeComputer;

    // Evita recrear el shortcut de cada PC en cada ciclo de polling (~1.5s).
    private final java.util.Set<String> shortcutCreatedUuids =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private android.content.BroadcastReceiver addPcReceiver;
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    ((ComputerManagerService.ComputerManagerBinder)binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Start updates
                    startComputerUpdates();

                    // Force a keypair to be generated early to avoid discovery delays
                    new AndroidCryptoProvider(PcView.this).getClientCertificate();
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Only reinitialize views if completeOnCreate() was called
        // before this callback. If it was not, completeOnCreate() will
        // handle initializing views with the config change accounted for.
        // This is not prone to races because both callbacks are invoked
        // in the main thread.
        if (completeOnCreateCalled) {
            // Reinitialize views just in case orientation changed
            initializeViews();
        }
    }

    private final static int PAIR_ID = 2;
    private final static int UNPAIR_ID = 3;
    private final static int WOL_ID = 4;
    private final static int DELETE_ID = 5;
    private final static int RESUME_ID = 6;
    private final static int QUIT_ID = 7;
    private final static int VIEW_DETAILS_ID = 8;
    private final static int FULL_APP_LIST_ID = 9;
    private final static int TEST_NETWORK_ID = 10;

    private void initializeViews() {
        setContentView(R.layout.activity_pc_view);

        UiHelper.notifyNewRootView(this);

        // Lluvia animada solo con el tema "Lluvia" (nunca durante el streaming).
        com.limelight.ui.RainView rainView = findViewById(R.id.rain_view);
        if (rainView != null && "rain".equals(com.limelight.utils.ThemeManager.getThemeKey(this))) {
            rainView.setVisibility(View.VISIBLE);
            rainView.start();
        }

        // Permiso de notificaciones (Android 13+) para los avisos de estado.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 9010);
        }

        // Allow floating expanded PiP overlays while browsing PCs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        // Set default preferences if we've never been run
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Setup the list view
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(new androidx.appcompat.widget.Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.action_add_pc) {
                    startActivity(new Intent(PcView.this, AddComputerManually.class));
                    overridePendingTransition(R.anim.activity_slide_in_right, R.anim.activity_fade_exit);
                    return true;
                }
                return false;
            }
        });


        headerStatusDot = findViewById(R.id.header_status_dot);
        headerNetworkText = findViewById(R.id.header_network_text);

        // SmartDisplay UX Layer: Slim Icon Navigation Rail
        final androidx.drawerlayout.widget.DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        View menuButton = findViewById(R.id.menuButton);
        if (drawerLayout != null) {
            if (menuButton != null) {
                menuButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                        } else {
                            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START);
                        }
                    }
                });
            }

            View.OnClickListener railListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final int id = v.getId();
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                    drawerLayout.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                                return;
                            }
                            if (id == R.id.rail_nav_add_pc) {
                                startActivity(new Intent(PcView.this, AddComputerManually.class));
                            } else if (id == R.id.rail_nav_files) {
                                startActivity(new Intent(PcView.this, FileTransferActivity.class));
                            } else if (id == R.id.rail_nav_theme) {
                                showThemeSelectorDialog();
                            } else if (id == R.id.rail_nav_settings) {
                                startActivity(new Intent(PcView.this, StreamSettings.class));
                            } else if (id == R.id.rail_nav_diagnostics) {
                                ServerHelper.doNetworkTest(PcView.this);
                            } else if (id == R.id.rail_nav_logs) {
                                showLogsDialog();
                            } else if (id == R.id.rail_nav_help) {
                                startActivity(new Intent(PcView.this, ManualActivity.class));
                            } else if (id == R.id.rail_nav_donate) {
                                startActivity(new Intent(PcView.this, DonateActivity.class));
                            } else if (id == R.id.rail_nav_about) {
                                showAboutDialog();
                            }
                        }
                    }, 220);
                }
            };

            int[] railButtonIds = new int[]{
                    R.id.rail_nav_add_pc, R.id.rail_nav_files, R.id.rail_nav_theme,
                    R.id.rail_nav_settings, R.id.rail_nav_diagnostics, R.id.rail_nav_logs,
                    R.id.rail_nav_help, R.id.rail_nav_donate, R.id.rail_nav_about
            };
            for (int btnId : railButtonIds) {
                View btn = findViewById(btnId);
                if (btn != null) {
                    btn.setOnClickListener(railListener);
                }
            }

            // Soporte de gesto Atrás para cerrar el Drawer en Android 13+ (OnBackInvokedDispatcher)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                final android.window.OnBackInvokedCallback drawerBackCallback = new android.window.OnBackInvokedCallback() {
                    @Override
                    public void onBackInvoked() {
                        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                        }
                    }
                };
                drawerLayout.addDrawerListener(new androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
                    @Override
                    public void onDrawerOpened(View drawerView) {
                        try {
                            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, drawerBackCallback);
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void onDrawerClosed(View drawerView) {
                        try {
                            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(drawerBackCallback);
                        } catch (Exception ignored) {}
                    }
                });
            }
        }

        // Header vivo: estado de red real + indicador con pulso
        headerStatusDot = findViewById(R.id.header_status_dot);
        headerNetworkText = findViewById(R.id.header_network_text);
        updateNetworkStatus();

        // Botón Agregar PC superior derecho (Acción primaria)
        View btnAddPcTop = findViewById(R.id.btn_add_pc_top);
        if (btnAddPcTop != null) {
            btnAddPcTop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(PcView.this, AddComputerManually.class));
                    overridePendingTransition(R.anim.activity_slide_in_right, R.anim.activity_fade_exit);
                }
            });
        }

        // Botón rápido de agregar PC en encabezado de lista
        View btnAddPcQuick = findViewById(R.id.btnAddPcQuick);
        if (btnAddPcQuick != null) {
            btnAddPcQuick.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(PcView.this, AddComputerManually.class));
                    overridePendingTransition(R.anim.activity_slide_in_right, R.anim.activity_fade_exit);
                }
            });
        }

        // SmartDisplay 3D Hero & Liquid Start Button
        liquidStartButton = findViewById(R.id.liquidStartButton);
        heroPcName = findViewById(R.id.hero_pc_name);
        heroStatusText = findViewById(R.id.hero_status_text);
        capsuleLatencyText = findViewById(R.id.capsuleLatencyText);
        capsuleFpsText = findViewById(R.id.capsuleFpsText);

        if (liquidStartButton != null) {
            liquidStartButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleLiquidStartClick();
                }
            });
        }

        // Tarjeta / Chip "Última sesión"
        lastSessionCard = findViewById(R.id.last_session_card);
        lastSessionTitle = findViewById(R.id.last_session_title);
        lastSessionSubtitle = findViewById(R.id.last_session_subtitle);
        if (lastSessionCard != null) {
            lastSessionCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onLastSessionClicked();
                }
            });
        }
        refreshLastSessionCard();
        refreshHeroCard();



        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.pc_recycler_view);
        
        // Calculate columns based on width
        int columns = 1;
        if (getResources().getConfiguration().smallestScreenWidthDp >= 600) {
            columns = 2;
        }
        if (getResources().getConfiguration().smallestScreenWidthDp >= 840) {
            columns = 3;
        }
        
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, columns));
        recyclerView.setAdapter(pcGridAdapter);
        
        // SmartDisplay FASE 4: Habilitar verdadero Edge-to-Edge (eliminar franja morada)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            
            // 1. Quitar el padding superior que añade UiHelper al contenedor principal
            View contentView = findViewById(android.R.id.content);
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, insets) -> {
                androidx.core.graphics.Insets sysInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(sysInsets.left, 0, sysInsets.right, 0);
                return insets;
            });
            
            // AppBarLayout handles toolbar top padding natively via fitsSystemWindows="true"
            androidx.recyclerview.widget.RecyclerView pcRecycler = findViewById(R.id.pc_recycler_view);
            if (pcRecycler != null) {
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(pcRecycler, (v, insets) -> {
                    androidx.core.graphics.Insets sysInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                    // 16dp base bottom padding + nav bar height
                    int basePadding = Math.round(16 * getResources().getDisplayMetrics().density);
                    v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), basePadding + sysInsets.bottom);
                    return insets;
                });
            }
            
            // NavigationView handles its own insets natively via fitsSystemWindows="true"
            
            contentView.requestApplyInsets();
        }

        pcGridAdapter.setOnItemClickListener(new PcGridAdapter.OnItemClickListener() {
            private long lastClickTime = 0;

            @Override
            public void onItemClick(ComputerObject computer, View view) {
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastClickTime < 600) {
                    return;
                }
                lastClickTime = now;

                activeComputer = computer;
                refreshHeroCard();

                if (view != null && view.getId() == R.id.btn_connect) {
                    // Botón Play directo: conecta al escritorio de inmediato
                    launchDirectDesktop(computer);
                } else {
                    // Tocar la tarjeta: abre la pantalla de detalle del PC
                    Intent i = new Intent(PcView.this, DeviceDetailActivity.class);
                    i.putExtra(AppView.NAME_EXTRA, computer.details.name);
                    i.putExtra(AppView.UUID_EXTRA, computer.details.uuid);
                    startActivity(i);
                    overridePendingTransition(R.anim.activity_slide_in_right, R.anim.activity_fade_exit);
                }
            }
        });
        
        pcGridAdapter.setOnItemOptionsClickListener(new PcGridAdapter.OnItemOptionsClickListener() {
            @Override
            public void onOptionsClick(ComputerObject computer, View view) {
                showOptionsMenu(computer, view);
            }
        });

        noPcFoundLayout = findViewById(R.id.no_pc_found_layout);
        if (pcGridAdapter.getCount() == 0) {
            noPcFoundLayout.setVisibility(View.VISIBLE);
        }
        else {
            noPcFoundLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Cargar el tema seleccionado antes de llamar a super.onCreate
        com.limelight.utils.ThemeManager.apply(this);
        super.onCreate(savedInstanceState);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        // Create a GLSurfaceView to fetch GLRenderer unless we have
        // a cached result already.
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (!glPrefs.savedFingerprint.equals(Build.FINGERPRINT) || glPrefs.glRenderer.isEmpty()) {
            GLSurfaceView surfaceView = new GLSurfaceView(this);
            surfaceView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    // Save the GLRenderer string so we don't need to do this next time
                    glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                    glPrefs.savedFingerprint = Build.FINGERPRINT;
                    glPrefs.writePreferences();

                    LimeLog.info("Fetched GL Renderer: " + glPrefs.glRenderer);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            completeOnCreate();
                        }
                    });
                }

                @Override
                public void onSurfaceChanged(GL10 gl10, int i, int i1) {
                }

                @Override
                public void onDrawFrame(GL10 gl10) {
                }
            });
            setContentView(surfaceView);
        }
        else {
            LimeLog.info("Cached GL Renderer: " + glPrefs.glRenderer);
            completeOnCreate();
        }
    }

    private void completeOnCreate() {
        com.limelight.smartdisplay.AppContainer.init(this);
        completeOnCreateCalled = true;

        shortcutHelper = new ShortcutHelper(this);

        UiHelper.setLocale(this);

        // Inicializar el adaptador antes de construir la UI
        PreferenceConfiguration prefsConfig = PreferenceConfiguration.readPreferences(this);
        pcGridAdapter = new PcGridAdapter(this, prefsConfig);

        // Inicializar el store de última sesión para la tarjeta de reconexión
        lastSessionStore = new com.limelight.smartdisplay.recents.LastSessionStore(this);

        // Construir la interfaz completa (toolbar, drawer, recycler, etc.)
        initializeViews();

        // Bind to the computer manager service
        bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        // Register com.limelight.smartdisplay.ADD_PC receiver
        addPcReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                if ("com.limelight.smartdisplay.ADD_PC".equals(intent.getAction())) {
                    final String ip = intent.getStringExtra("IP");
                    final String tailscaleIp = intent.getStringExtra("TAILSCALE_IP");
                    final String name = intent.getStringExtra("NAME");
                    final String uuid = intent.getStringExtra("UUID");
                    final String mac = intent.getStringExtra("MAC");
                    LimeLog.info("Broadcast ADD_PC recibido. IP: " + ip + ", Tailscale: " + tailscaleIp + ", UUID: " + uuid);
                    if (uuid != null && !uuid.isEmpty()) {
                        final com.limelight.nvstream.http.ComputerDetails details = new com.limelight.nvstream.http.ComputerDetails();
                        details.uuid = uuid;
                        details.name = name != null ? name : "PC Detectado";
                        details.macAddress = mac != null ? mac : "00:00:00:00:00:00";
                        if (ip != null && !ip.isEmpty() && !"null".equals(ip)) {
                            details.localAddress = new com.limelight.nvstream.http.ComputerDetails.AddressTuple(ip, com.limelight.nvstream.http.NvHTTP.DEFAULT_HTTP_PORT);
                        }
                        if (tailscaleIp != null && !tailscaleIp.isEmpty() && !"null".equals(tailscaleIp)) {
                            details.remoteAddress = new com.limelight.nvstream.http.ComputerDetails.AddressTuple(tailscaleIp, com.limelight.nvstream.http.NvHTTP.DEFAULT_HTTP_PORT);
                            details.manualAddress = new com.limelight.nvstream.http.ComputerDetails.AddressTuple(tailscaleIp, com.limelight.nvstream.http.NvHTTP.DEFAULT_HTTP_PORT);
                        }
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (managerBinder != null) {
                                        managerBinder.addComputerBlocking(details);
                                        LimeLog.info("ADD_PC: Computadora agregada/actualizada con éxito.");
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }).start();
                    }
                }
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter("com.limelight.smartdisplay.ADD_PC");
        androidx.core.content.ContextCompat.registerReceiver(this, addPcReceiver, filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        inForeground = true;

        // Reactivar polling si el binder ya está listo
        startComputerUpdates();

        // Monitor de red para el indicador del header
        registerNetworkCallback();
    }

    @Override
    protected void onPause() {
        super.onPause();
        inForeground = false;

        // Pausar polling para no consumir batería en segundo plano
        stopComputerUpdates(false);

        // Dejar de escuchar cambios de red
        unregisterNetworkCallback();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Desregistrar el receiver de ADD_PC
        if (addPcReceiver != null) {
            try {
                unregisterReceiver(addPcReceiver);
            } catch (IllegalArgumentException ignored) {
                // Ya estaba desregistrado
            }
            addPcReceiver = null;
        }

        // Desvincular el servicio de gestión de PCs
        if (managerBinder != null) {
            unbindService(serviceConnection);
            managerBinder = null;
        }

        // Liberar frames y animaciones de LiquidStartButton
        if (liquidStartButton != null) {
            liquidStartButton.release();
            liquidStartButton = null;
        }
    }

    private void startComputerUpdates() {
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details) {
                    if (!freezeUpdates) {
                        final String activeAppName = PcGridAdapter.resolveActiveAppName(details);
                        PcView.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateComputer(details, activeAppName);
                            }
                        });

                        if (details.pairState == PairState.PAIRED && details.uuid != null
                                && shortcutCreatedUuids.add(details.uuid)) {
                            shortcutHelper.createAppViewShortcutForOnlineHost(details);
                        }
                    }
                }
            });
            runningPolling = true;
        }
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) {
                return;
            }

            freezeUpdates = true;

            managerBinder.stopPolling();

            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
        }
    }

    private void showOptionsMenu(final ComputerObject computer, View anchorView) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchorView);
        Menu menu = popup.getMenu();

        if (computer.details.state == ComputerDetails.State.OFFLINE ||
            computer.details.state == ComputerDetails.State.UNKNOWN) {
            menu.add(Menu.NONE, WOL_ID, 1, getResources().getString(R.string.pcview_menu_send_wol));
        }
        else if (computer.details.pairState != PairState.PAIRED) {
            menu.add(Menu.NONE, PAIR_ID, 1, getResources().getString(R.string.pcview_menu_pair_pc));
        }
        else {
            if (computer.details.runningGameId != 0) {
                menu.add(Menu.NONE, RESUME_ID, 1, getResources().getString(R.string.applist_menu_resume));
                menu.add(Menu.NONE, QUIT_ID, 2, getResources().getString(R.string.applist_menu_quit));
            }

            menu.add(Menu.NONE, FULL_APP_LIST_ID, 4, getResources().getString(R.string.pcview_menu_app_list));
        }

        menu.add(Menu.NONE, TEST_NETWORK_ID, 5, getResources().getString(R.string.pcview_menu_test_network));
        menu.add(Menu.NONE, DELETE_ID, 6, getResources().getString(R.string.pcview_menu_delete_pc));
        menu.add(Menu.NONE, VIEW_DETAILS_ID, 7,  getResources().getString(R.string.pcview_menu_details));

        popup.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case PAIR_ID: doPair(computer.details); return true;
                    case UNPAIR_ID: doUnpair(computer.details); return true;
                    case WOL_ID: doWakeOnLan(computer.details); return true;
                    case DELETE_ID: 
                        if (ActivityManager.isUserAMonkey()) return true;
                        UiHelper.displayDeletePcConfirmationDialog(PcView.this, computer.details, new Runnable() {
                            @Override
                            public void run() {
                                if (managerBinder != null) removeComputer(computer.details);
                            }
                        }, null);
                        return true;
                    case FULL_APP_LIST_ID: doAppList(computer.details, false, true); return true;
                    case RESUME_ID: 
                        if (managerBinder != null) ServerHelper.doStart(PcView.this, new NvApp("app", computer.details.runningGameId, false), computer.details, managerBinder);
                        return true;
                    case QUIT_ID:
                        if (managerBinder != null) {
                            UiHelper.displayQuitConfirmationDialog(PcView.this, new Runnable() {
                                @Override
                                public void run() {
                                    ServerHelper.doQuit(PcView.this, computer.details, new NvApp("app", 0, false), managerBinder, null);
                                }
                            }, null);
                        }
                        return true;
                    case VIEW_DETAILS_ID: Dialog.displayDialog(PcView.this, getResources().getString(R.string.title_details), computer.details.toString(), false); return true;
                    case TEST_NETWORK_ID: ServerHelper.doNetworkTest(PcView.this); return true;
                }
                return false;
            }
        });
        
        popup.show();
    }

    private void doPair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.pairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                boolean success = false;
                try {
                    stopComputerUpdates(true);

                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        message = null;
                        success = true;
                    }
                    else {
                        final String pinStr = "9999";

                        Dialog.displayDialog(PcView.this, getResources().getString(R.string.pair_pairing_title),
                                getResources().getString(R.string.pair_pairing_msg)+" "+pinStr+"\n\n"+
                                getResources().getString(R.string.pair_pairing_help), false);

                        com.limelight.smartdisplay.host.SunshinePairHelper.submitPinAsync(
                                PcView.this, computer.activeAddress.address, pinStr, "SmartDisplay");

                        PairingManager pm = httpConn.getPairingManager();

                        PairState pairState = pm.pair(httpConn.getServerInfo(true), pinStr);
                        if (pairState == PairState.PIN_WRONG) {
                            message = getResources().getString(R.string.pair_incorrect_pin);
                        }
                        else if (pairState == PairState.FAILED) {
                            if (computer.runningGameId != 0) {
                                message = getResources().getString(R.string.pair_pc_ingame);
                            }
                            else {
                                message = getResources().getString(R.string.pair_fail);
                            }
                        }
                        else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                            message = getResources().getString(R.string.pair_already_in_progress);
                        }
                        else if (pairState == PairState.PAIRED) {
                            message = null;
                            success = true;

                            managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();

                            managerBinder.invalidateStateForComputer(computer.uuid);
                        }
                        else {
                            message = null;
                        }
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    message = e.getMessage();
                }

                Dialog.closeDialogs();

                final String toastMessage = message;
                final boolean toastSuccess = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (toastMessage != null) {
                            Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                        }

                        if (toastSuccess) {
                            doAppList(computer, true, false);
                        }
                        else {
                            startComputerUpdates();
                        }
                    }
                });
            }
        }).start();
    }

    private void doWakeOnLan(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.ONLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_pc_online), Toast.LENGTH_SHORT).show();
            return;
        }

        if (computer.macAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_no_mac), Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    WakeOnLanSender.sendWolPacket(computer);
                    message = getResources().getString(R.string.wol_waking_msg);
                } catch (IOException e) {
                    message = getResources().getString(R.string.wol_fail);
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doUnpair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.unpairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairingManager.PairState.PAIRED) {
                        httpConn.unpair();
                        if (httpConn.getPairState() == PairingManager.PairState.NOT_PAIRED) {
                            message = getResources().getString(R.string.unpair_success);
                        }
                        else {
                            message = getResources().getString(R.string.unpair_fail);
                        }
                    }
                    else {
                        message = getResources().getString(R.string.unpair_error);
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Intent i = new Intent(this, AppView.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired);
        i.putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames);
        startActivity(i);
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out);
    }

    private void removeComputer(ComputerDetails details) {

        new DiskAssetLoader(this).deleteAssetsForComputer(details.uuid);

        // Delete hidden games preference value
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .remove(details.uuid)
                .apply();

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            if (details.equals(computer.details)) {
                // Disable or delete shortcuts referencing this PC
                shortcutHelper.disableComputerShortcut(details,
                        getResources().getString(R.string.scut_deleted_pc));

                // removeComputer ya emite notifyItemRemoved; un notifyDataSetChanged
                // adicional era redundante y anulaba la animación de salida del item.
                pcGridAdapter.removeComputer(computer);

                if (pcGridAdapter.getCount() == 0) {
                    // Show the "Discovery in progress" view
                    noPcFoundLayout.setVisibility(View.VISIBLE);
                }

                refreshHeroCard();
                break;
            }
        }
    }
    
    private void updateComputer(ComputerDetails details, String activeAppName) {
        ComputerObject existingEntry = null;

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            // Check if this is the same computer
            if (details.uuid.equals(computer.details.uuid)) {
                existingEntry = computer;
                break;
            }
        }

        if (existingEntry != null) {
            ComputerDetails.State oldState = existingEntry.details.state;
            // Replace the information in the existing entry
            existingEntry.details = details;
            existingEntry.activeAppName = activeAppName;
            // Re-bindear SOLO este item, no toda la lista
            pcGridAdapter.notifyItemChangedFor(existingEntry);
            maybeNotifyStateChange(oldState, details);
        }
        else {
            // Add a new entry (addComputer ya hace notifyItemInserted)
            ComputerObject newEntry = new ComputerObject(details);
            newEntry.activeAppName = activeAppName;
            pcGridAdapter.addComputer(newEntry);

            // Remove the "Discovery in progress" view
            noPcFoundLayout.setVisibility(View.INVISIBLE);
            maybeNotifyStateChange(null, details);
        }

        refreshHeroCard();
    }

    /**
     * Notifica al usuario cuando un equipo pasa a estar en línea, y si además
     * requiere emparejamiento. Solo dispara en la transición hacia ONLINE para
     * no repetir avisos en cada sondeo de descubrimiento.
     */
    private void maybeNotifyStateChange(ComputerDetails.State oldState, ComputerDetails details) {
        if (details == null || details.state != ComputerDetails.State.ONLINE) {
            return;
        }
        boolean becameOnline = (oldState != ComputerDetails.State.ONLINE);
        if (!becameOnline) {
            return;
        }
        if (details.pairState == com.limelight.nvstream.http.PairingManager.PairState.NOT_PAIRED) {
            com.limelight.utils.NotificationHelper.notifyPairingRequired(this, details.name);
        } else {
            com.limelight.utils.NotificationHelper.notifyPcOnline(this, details.name);
        }
    }

    // Legacy AbsListView callbacks removed

    // ====================== SmartDisplay UX Layer (FASE 2) ======================

    private void handleNavigationItem(int id) {
        if (id == R.id.nav_add_pc) {
            startActivity(new Intent(PcView.this, AddComputerManually.class));
        } else if (id == R.id.nav_files) {
            startActivity(new Intent(PcView.this, FileTransferActivity.class));
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(PcView.this, StreamSettings.class));
        } else if (id == R.id.nav_theme) {
            showThemeSelectorDialog();
        } else if (id == R.id.nav_diagnostics) {
            ServerHelper.doNetworkTest(PcView.this);
        } else if (id == R.id.nav_logs) {
            showLogsDialog();
        } else if (id == R.id.nav_help) {
            startActivity(new Intent(PcView.this, ManualActivity.class));
        } else if (id == R.id.nav_donate) {
            startActivity(new Intent(PcView.this, DonateActivity.class));
        } else if (id == R.id.nav_about) {
            showAboutDialog();
        }
    }


    private void showThemeSelectorDialog() {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }

        final View dialogView = getLayoutInflater().inflate(R.layout.dialog_theme_selector, null);
        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setNegativeButton("Cerrar", null)
                .create();

        final String currentKey = com.limelight.utils.ThemeManager.getThemeKey(this);
        final boolean currentLight = com.limelight.utils.ThemeManager.isLight(this);

        // Modo Claro / Oscuro
        final com.google.android.material.materialswitch.MaterialSwitch switchMode =
                dialogView.findViewById(R.id.switch_theme_mode);
        final TextView txtModeTitle = dialogView.findViewById(R.id.txt_mode_title);
        final TextView txtModeDesc = dialogView.findViewById(R.id.txt_mode_desc);
        final ImageView iconMode = dialogView.findViewById(R.id.icon_mode);

        if (switchMode != null) {
            switchMode.setChecked(currentLight);
            txtModeTitle.setText(currentLight ? "Modo Claro" : "Modo Oscuro");
            txtModeDesc.setText(currentLight ? "Fondo claro de alta legibilidad diurna" : "Optimizado para paneles OLED y contraste neón");
            iconMode.setImageResource(currentLight ? R.drawable.ic_help : R.drawable.ic_bedtime);

            switchMode.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                    com.limelight.utils.ThemeManager.setMode(PcView.this,
                            isChecked ? com.limelight.utils.ThemeManager.MODE_LIGHT
                                      : com.limelight.utils.ThemeManager.MODE_DARK);
                    dialog.dismiss();
                    recreate();
                }
            });
        }

        // Mapeo de tarjetas de temas
        final String[] keys = {"universe", "pixel", "emerald", "amber", "graphite", "glass", "rain"};
        final int[] cardIds = {
                R.id.theme_card_universe, R.id.theme_card_pixel, R.id.theme_card_emerald,
                R.id.theme_card_amber, R.id.theme_card_graphite, R.id.theme_card_glass,
                R.id.theme_card_rain
        };
        final int[] checkIds = {
                R.id.check_theme_universe, R.id.check_theme_pixel, R.id.check_theme_emerald,
                R.id.check_theme_amber, R.id.check_theme_graphite, R.id.check_theme_glass,
                R.id.check_theme_rain
        };

        for (int i = 0; i < keys.length; i++) {
            final String themeKey = keys[i];
            final com.google.android.material.card.MaterialCardView card = dialogView.findViewById(cardIds[i]);
            final ImageView check = dialogView.findViewById(checkIds[i]);

            if (card != null) {
                boolean isSelected = themeKey.equals(currentKey);
                if (check != null) {
                    check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                }
                if (isSelected) {
                    card.setStrokeWidth((int) (2 * getResources().getDisplayMetrics().density));
                    card.setStrokeColor(com.limelight.utils.ThemeManager.accentColor(this));
                }

                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        com.limelight.utils.ThemeManager.setThemeKey(PcView.this, themeKey);
                        dialog.dismiss();
                        recreate();
                    }
                });
            }
        }

        dialog.show();
    }

    /** Maneja la pulsación del botón central LiquidStartButton. */
    private void handleLiquidStartClick() {
        if (activeComputer == null || activeComputer.details == null) {
            Toast.makeText(this, R.string.searching_pc, Toast.LENGTH_SHORT).show();
            return;
        }

        if (activeComputer.details.state != ComputerDetails.State.ONLINE || activeComputer.details.activeAddress == null) {
            if (liquidStartButton != null) {
                liquidStartButton.setConnectionState(ConnectionVisualState.ERROR);
            }
            doWakeOnLan(activeComputer.details);
            return;
        }

        if (activeComputer.details.pairState != PairState.PAIRED) {
            doPair(activeComputer.details);
            return;
        }

        if (liquidStartButton != null) {
            liquidStartButton.setConnectionState(ConnectionVisualState.CONNECTING);
        }

        launchDirectDesktop(activeComputer);
    }

    /** Actualiza la tarjeta / sección Hero central con el PC seleccionado/activo. */
    private void refreshHeroCard() {
        if (heroPcName == null || heroStatusText == null || liquidStartButton == null) {
            return;
        }

        // Buscar el PC activo:
        // 1. Si hay última sesión registrada y existe en el adapter, usarlo.
        ComputerObject candidate = null;
        if (lastSessionStore != null && lastSessionStore.has()) {
            String lastUuid = lastSessionStore.getPcUuid();
            for (int i = 0; i < pcGridAdapter.getCount(); i++) {
                ComputerObject obj = (ComputerObject) pcGridAdapter.getItem(i);
                if (obj != null && obj.details != null && lastUuid != null && lastUuid.equals(obj.details.uuid)) {
                    candidate = obj;
                    break;
                }
            }
        }

        // 2. Si no o no está online, buscar el primer PC ONLINE en el adapter
        if (candidate == null || candidate.details == null || candidate.details.state != ComputerDetails.State.ONLINE) {
            for (int i = 0; i < pcGridAdapter.getCount(); i++) {
                ComputerObject obj = (ComputerObject) pcGridAdapter.getItem(i);
                if (obj != null && obj.details != null && obj.details.state == ComputerDetails.State.ONLINE) {
                    candidate = obj;
                    break;
                }
            }
        }

        // 3. Si no hay online, usar el primero disponible
        if (candidate == null && pcGridAdapter.getCount() > 0) {
            candidate = (ComputerObject) pcGridAdapter.getItem(0);
        }

        activeComputer = candidate;

        if (activeComputer != null && activeComputer.details != null) {
            heroPcName.setText(activeComputer.details.name);
            liquidStartButton.setTargetPcName(activeComputer.details.name);

            if (activeComputer.details.state == ComputerDetails.State.ONLINE) {
                boolean paired = (activeComputer.details.pairState == PairState.PAIRED);
                heroStatusText.setText(paired ? "Disponible" : "Requiere emparejar");
                heroStatusText.setTextColor(android.graphics.Color.parseColor("#00C853"));
                if (headerStatusDot != null) {
                    headerStatusDot.setBackgroundResource(R.drawable.bg_green_dot);
                }
                liquidStartButton.setConnectionState(ConnectionVisualState.IDLE);
                if (capsuleLatencyText != null) capsuleLatencyText.setText("8 ms");
                if (capsuleFpsText != null) capsuleFpsText.setText("60 FPS");
            } else if (activeComputer.details.state == ComputerDetails.State.UNKNOWN) {
                heroStatusText.setText("Actualizando...");
                heroStatusText.setTextColor(android.graphics.Color.parseColor("#78909C"));
                liquidStartButton.setConnectionState(ConnectionVisualState.IDLE);
                if (capsuleLatencyText != null) capsuleLatencyText.setText("— ms");
                if (capsuleFpsText != null) capsuleFpsText.setText("— FPS");
            } else {
                heroStatusText.setText("Sin conexión");
                heroStatusText.setTextColor(android.graphics.Color.parseColor("#EF5350"));
                if (headerStatusDot != null) {
                    headerStatusDot.setBackgroundResource(R.drawable.online_dot);
                }
                liquidStartButton.setConnectionState(ConnectionVisualState.DISABLED);
                if (capsuleLatencyText != null) capsuleLatencyText.setText("— ms");
                if (capsuleFpsText != null) capsuleFpsText.setText("— FPS");
            }
        } else {
            heroPcName.setText("SmartDisplay");
            heroStatusText.setText("Buscando equipos...");
            heroStatusText.setTextColor(android.graphics.Color.parseColor("#78909C"));
            liquidStartButton.setTargetPcName("PC");
            liquidStartButton.setConnectionState(ConnectionVisualState.DISABLED);
            if (capsuleLatencyText != null) capsuleLatencyText.setText("— ms");
            if (capsuleFpsText != null) capsuleFpsText.setText("— FPS");
        }
    }

    /** Lanza directamente la transmisión del escritorio sin pantallas intermedias. */
    private void launchDirectDesktop(ComputerObject computer) {
        if (computer == null || computer.details == null) return;
        if (computer.details.state != ComputerDetails.State.ONLINE || computer.details.activeAddress == null) {
            Toast.makeText(this, R.string.pair_pc_offline, Toast.LENGTH_SHORT).show();
            if (liquidStartButton != null) {
                liquidStartButton.setConnectionState(ConnectionVisualState.ERROR);
            }
            return;
        }

        if (liquidStartButton != null) {
            liquidStartButton.setConnectionState(ConnectionVisualState.CONNECTING);
        }

        if (managerBinder != null && computer.details.pairState == PairingManager.PairState.PAIRED) {
            Toast.makeText(this, R.string.detail_connecting, Toast.LENGTH_SHORT).show();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        com.limelight.nvstream.http.NvHTTP http = new com.limelight.nvstream.http.NvHTTP(
                                ServerHelper.getCurrentAddressFromComputer(computer.details),
                                computer.details.httpsPort,
                                managerBinder.getUniqueId(),
                                computer.details.serverCert,
                                PlatformBinding.getCryptoProvider(PcView.this));
                        java.util.List<com.limelight.nvstream.http.NvApp> apps = http.getAppList();
                        com.limelight.nvstream.http.NvApp target = null;
                        if (apps != null && !apps.isEmpty()) {
                            for (com.limelight.nvstream.http.NvApp a : apps) {
                                String n = a.getAppName() != null ? a.getAppName().toLowerCase() : "";
                                if (n.contains("desktop") || n.contains("escritorio") || n.contains("mstsc")) {
                                    target = a;
                                    break;
                                }
                            }
                            if (target == null) target = apps.get(0);
                        }
                        final com.limelight.nvstream.http.NvApp finalTarget = target;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (liquidStartButton != null) {
                                    liquidStartButton.setConnectionState(ConnectionVisualState.CONNECTED);
                                }
                                if (finalTarget != null) {
                                    ServerHelper.doStart(PcView.this, finalTarget, computer.details, managerBinder);
                                    overridePendingTransition(R.anim.activity_fade_enter, R.anim.fade_out);
                                } else {
                                    launchViaTrampoline(computer);
                                }
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (liquidStartButton != null) {
                                    liquidStartButton.setConnectionState(ConnectionVisualState.IDLE);
                                }
                                launchViaTrampoline(computer);
                            }
                        });
                    }
                }
            }).start();
        } else {
            launchViaTrampoline(computer);
        }
    }

    private void launchViaTrampoline(ComputerObject computer) {
        Intent i = new Intent(this, ShortcutTrampoline.class);
        i.putExtra(AppView.NAME_EXTRA, computer.details.name);
        i.putExtra(AppView.UUID_EXTRA, computer.details.uuid);
        i.putExtra("AUTO_CONNECT", true);
        i.setAction(Intent.ACTION_DEFAULT);
        startActivity(i);
    }

    @Override
    public void onBackPressed() {
        androidx.drawerlayout.widget.DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        if (drawerLayout != null && drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    /** Lee el tipo de red activo (sin permisos en runtime) y actualiza el header. */
    private void updateNetworkStatus() {
        if (headerNetworkText == null) {
            return;
        }

        String label = getString(R.string.net_offline);
        boolean online = false;
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.net.Network net = cm.getActiveNetwork();
                    android.net.NetworkCapabilities caps =
                            (net != null) ? cm.getNetworkCapabilities(net) : null;
                    if (caps != null
                            && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        online = true;
                        if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                            label = getString(R.string.net_wifi);
                        } else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            label = getString(R.string.net_ethernet);
                        } else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            label = getString(R.string.net_cellular);
                        } else {
                            label = getString(R.string.net_online);
                        }
                    }
                } else {
                    android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                    if (info != null && info.isConnected()) {
                        online = true;
                        if (info.getType() == android.net.ConnectivityManager.TYPE_WIFI) {
                            label = getString(R.string.net_wifi);
                        } else if (info.getType() == android.net.ConnectivityManager.TYPE_ETHERNET) {
                            label = getString(R.string.net_ethernet);
                        } else {
                            label = getString(R.string.net_cellular);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // En caso de cualquier error de sistema, mostramos "Sin conexión"
            label = getString(R.string.net_offline);
        }

        final String finalLabel = label;
        final boolean finalOnline = online;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (headerNetworkText != null) {
                    headerNetworkText.setText(finalLabel);
                }
                if (headerStatusDot != null) {
                    if (finalOnline) {
                        if (headerStatusDot.getAnimation() == null) {
                            headerStatusDot.startAnimation(
                                    android.view.animation.AnimationUtils.loadAnimation(PcView.this, R.anim.pulse));
                        }
                    } else {
                        headerStatusDot.clearAnimation();
                    }
                }
            }
        });
    }

    private void registerNetworkCallback() {
        if (networkCallback != null) {
            return;
        }
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) {
                return;
            }
            networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(android.net.Network network) {
                    updateNetworkStatus();
                }

                @Override
                public void onLost(android.net.Network network) {
                    updateNetworkStatus();
                }

                @Override
                public void onCapabilitiesChanged(android.net.Network network,
                                                  android.net.NetworkCapabilities caps) {
                    updateNetworkStatus();
                }
            };
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            networkCallback = null;
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback == null) {
            return;
        }
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                cm.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception e) {
            // ignorar: el callback ya pudo haberse retirado
        } finally {
            networkCallback = null;
        }
    }

    private void showLogsDialog() {
        StringBuilder sb = new StringBuilder();
        sb.append("Dispositivo: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE)
          .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("ABI: ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?").append('\n');
        try {
            sb.append("Versión app: ")
              .append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName).append('\n');
        } catch (Exception e) {
            // sin versión
        }
        GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (glPrefs.glRenderer != null && !glPrefs.glRenderer.isEmpty()) {
            sb.append("GPU: ").append(glPrefs.glRenderer).append('\n');
        }
        sb.append("PCs detectados: ").append(pcGridAdapter != null ? pcGridAdapter.getCount() : 0);
        Dialog.displayDialog(PcView.this, getString(R.string.logs_title), sb.toString(), false);
    }

    /**
     * FASE 4 (Eliminado): El diálogo emergente bloqueaba la UI en un bucle infinito
     * si la sesión fallaba al iniciar (Game.java guardaba la sesión y regresaba a PcView).
     * Ahora el usuario usa la tarjeta de "Última sesión" en la interfaz.
     * La detección de sesión interrumpida se mantiene vía LastSessionStore + refreshLastSessionCard().
     */

    /** Muestra/oculta la tarjeta "Última sesión" según haya un registro previo. */
    private void refreshLastSessionCard() {
        if (lastSessionCard == null || lastSessionStore == null) {
            return;
        }
        if (!lastSessionStore.has()) {
            lastSessionCard.setVisibility(View.GONE);
            return;
        }

        String pcName  = lastSessionStore.getPcName();
        String appName = lastSessionStore.getAppName();

        if (lastSessionTitle != null) {
            lastSessionTitle.setText(
                    (pcName != null && !pcName.isEmpty()) ? pcName : getString(R.string.app_label));
        }
        if (lastSessionSubtitle != null) {
            if (appName != null && !appName.isEmpty()) {
                lastSessionSubtitle.setText(appName);
                lastSessionSubtitle.setVisibility(View.VISIBLE);
            } else {
                lastSessionSubtitle.setVisibility(View.GONE);
            }
        }
        lastSessionCard.setVisibility(View.VISIBLE);
    }

    /** Relanza la última sesión si su PC está online y emparejado. */
    private void onLastSessionClicked() {
        if (lastSessionStore == null || !lastSessionStore.has() || managerBinder == null) {
            return;
        }
        ComputerDetails target = findComputerByUuid(lastSessionStore.getPcUuid());
        if (target == null
                || target.state != ComputerDetails.State.ONLINE
                || target.pairState != PairState.PAIRED) {
            Toast.makeText(PcView.this,
                    getString(R.string.last_session_unavailable), Toast.LENGTH_SHORT).show();
            return;
        }
        NvApp app = new NvApp(
                lastSessionStore.getAppName(),
                lastSessionStore.getAppId(),
                lastSessionStore.getAppHdr());
        ServerHelper.doStart(PcView.this, app, target, managerBinder);
    }

    private ComputerDetails findComputerByUuid(String uuid) {
        if (uuid == null || uuid.isEmpty() || pcGridAdapter == null) {
            return null;
        }
        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject obj = (ComputerObject) pcGridAdapter.getItem(i);
            if (obj != null && obj.details != null && uuid.equals(obj.details.uuid)) {
                return obj.details;
            }
        }
        return null;
    }

    private void showAboutDialog() {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.app_label)).append('\n');
        sb.append(getString(R.string.nav_app_subtitle)).append("\n\n");
        try {
            sb.append("v")
              .append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName).append("\n\n");
        } catch (Exception e) {
            // sin versión
        }
        sb.append("Basado en Moonlight (GPL v3).");
        Dialog.displayDialog(PcView.this, getString(R.string.about_title), sb.toString(), false);
    }

    public static class ComputerObject {
        public ComputerDetails details;
        /** Nombre de la app activa, resuelto fuera del hilo UI (cache para bind). */
        public String activeAppName;

        public ComputerObject(ComputerDetails details) {
            if (details == null) {
                throw new IllegalArgumentException("details must not be null");
            }
            this.details = details;
        }

        @Override
        public String toString() {
            return details.name;
        }
    }
}
