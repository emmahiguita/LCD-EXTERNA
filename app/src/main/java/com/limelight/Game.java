package com.limelight;


import com.limelight.binding.PlatformBinding;
import com.limelight.binding.audio.AndroidAudioRenderer;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.capture.InputCaptureManager;
import com.limelight.binding.input.capture.InputCaptureProvider;
import com.limelight.binding.input.touch.AbsoluteTouchContext;
import com.limelight.binding.input.touch.RelativeTouchContext;
import com.limelight.binding.input.driver.UsbDriverService;
import com.limelight.binding.input.evdev.EvdevListener;
import com.limelight.binding.input.touch.TouchContext;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.video.CrashListener;
import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.binding.video.PerfOverlayListener;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.GameGestures;
import com.limelight.ui.AdaptiveCursorView;
import com.limelight.ui.AudioHudController;
import com.limelight.ui.LogicalKeyboardOverlay;
import com.limelight.ui.OverlayFabController;
import com.limelight.ui.WindowControlsController;
import com.limelight.ui.StreamView;
import com.limelight.ui.StreamViewTransformController;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

// ÔöÇÔöÇ SmartDisplay AI ÔÇô Capa 2: Reconexi├│n autom├ítica ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
import com.limelight.smartdisplay.recovery.AutoReconnectManager;
import com.limelight.smartdisplay.recovery.SessionRecoveryManager;
import com.limelight.smartdisplay.recovery.WakeOnLanManager;
import android.widget.ProgressBar;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Rational;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;

import com.limelight.smartdisplay.voice.VoiceCaptureManager;
import com.limelight.smartdisplay.bus.SmartDisplayBus;
import org.json.JSONObject;

public class Game extends Activity implements SurfaceHolder.Callback,
        OnGenericMotionListener, OnTouchListener, NvConnectionListener, EvdevListener,
        OnSystemUiVisibilityChangeListener, GameGestures, StreamView.InputCallbacks,
        PerfOverlayListener, UsbDriverService.UsbDriverStateListener, View.OnKeyListener {
    private VoiceCaptureManager voiceManager;
    private com.limelight.binding.audio.AndroidAudioRenderer audioRenderer;
    // Audio: el PC SIEMPRE va en silencio durante el streaming (enableLocalAudioPlayback(false));
    // el video del PC se oye SOLO en el m├│vil. La voz m├│vilÔåÆPC es un canal aparte (UDP).
    /** ├Ültimo instante (ms) en que el PC report├│ la posici├│n de su cursor (espejo activo). */
    private long lastCursorPosMs = 0;
    private SmartDisplayBus smartDisplayBus;
    private com.limelight.ui.SmartTaskbarController smartTaskbar;
    private int lastButtonState = 0;

    // Only 2 touches are supported
    private final TouchContext[] touchContextMap = new TouchContext[2];
    private long threeFingerDownTime = 0;

    private static final int REFERENCE_HORIZ_RES = 1280;
    private static final int REFERENCE_VERT_RES = 720;

    private static final int STYLUS_DOWN_DEAD_ZONE_DELAY = 100;
    private static final int STYLUS_DOWN_DEAD_ZONE_RADIUS = 20;

    private static final int STYLUS_UP_DEAD_ZONE_DELAY = 150;
    private static final int STYLUS_UP_DEAD_ZONE_RADIUS = 50;

    private static final int THREE_FINGER_TAP_THRESHOLD = 300;

    private ControllerHandler controllerHandler;
    private KeyboardTranslator keyboardTranslator;
    private VirtualController virtualController;

    private PreferenceConfiguration prefConfig;
    private SharedPreferences tombstonePrefs;

    private StreamConfiguration streamConfig;
    private String connHost;
    private int connPort;
    private int connHttpsPort;
    private String connUniqueId;
    private X509Certificate connServerCert;

    private NvConnection conn;
    private SpinnerDialog spinner;
    private boolean displayedFailureDialog = false;
    private boolean connecting = false;
    private boolean connected = false;
    private boolean autoEnterPip = false;
    private boolean surfaceCreated = false;
    private boolean attemptedConnection = false;
    private int suppressPipRefCount = 0;
    private String pcName;
    private String appName;
    private NvApp app;
    private float desiredRefreshRate;

    private InputCaptureProvider inputCaptureProvider;
    private int modifierFlags = 0;
    private boolean grabbedInput = true;
    private boolean cursorVisible = false;
    private boolean waitingForAllModifiersUp = false;
    private int specialKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private StreamView streamView;
    private long lastAbsTouchUpTime = 0;
    private long lastAbsTouchDownTime = 0;
    private float lastAbsTouchUpX, lastAbsTouchUpY;
    private float lastAbsTouchDownX, lastAbsTouchDownY;

    private boolean isHidingOverlays;
    private TextView notificationOverlayView;
    private int requestedNotificationOverlayVisibility = View.GONE;
    private TextView performanceOverlayView;
    // Throttle de actualizaci├│n del overlay de rendimiento: evita setText en cada frame
    private String lastPerfText = "";
    private long   lastPerfUpdateMs = 0;

    // Badge de conexi├│n (ping/fps) del modo h├¡brido: poll ligero cada 1s.
    private final Handler connStatsHandler = new Handler(android.os.Looper.getMainLooper());
    private int  lastRenderedFrames = 0;
    private long lastConnStatsMs = 0;
    private final Runnable connStatsRunnable = new Runnable() {
        @Override
        public void run() {
            if (connected && portraitHybridCtrl != null && decoderRenderer != null) {
                long now = android.os.SystemClock.elapsedRealtime();
                int frames = decoderRenderer.getTotalFramesRendered();
                long dtMs = now - lastConnStatsMs;
                int fps = (lastConnStatsMs > 0 && dtMs > 0)
                        ? Math.round((frames - lastRenderedFrames) * 1000f / dtMs) : 0;
                lastRenderedFrames = frames;
                lastConnStatsMs = now;
                int pingMs = (int) (MoonBridge.getEstimatedRttInfo() >> 32);
                portraitHybridCtrl.updateConnectionInfo(pingMs, Math.max(fps, 0));
            }
            connStatsHandler.postDelayed(this, 1000);
        }
    };

    // ÔöÇÔöÇ Overlay: FAB + Teclado l├│gico ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
    private OverlayFabController overlayFabController;
    private LogicalKeyboardOverlay logicalKeyboard;

    // ÔöÇÔöÇ Zoom / Pan del stream ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
    private StreamViewTransformController streamTransformCtrl;

    // ÔöÇÔöÇ SmartDisplay AI ÔÇô Premium: Cursor Adaptativo + Control de Ventanas ÔöÇÔöÇÔöÇÔöÇÔöÇ
    private AdaptiveCursorView    adaptiveCursor;
    private com.limelight.ui.SmartCursorEngine smartCursorEngine;
    private AudioHudController    audioHudController;
    private WindowControlsController windowControlsCtrl;
    private com.limelight.ui.MouseModeCircle mouseModeCircle;
    private com.limelight.smartdisplay.files.FileTransferServer fileServer;
    private com.limelight.ui.FileBrowserController fileBrowser;
    private static final int REQ_FAB_FILE_SHARE = 7777;
    private android.content.ClipboardManager clipboardManager;
    private android.content.ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private volatile boolean applyingRemoteClip = false;
    private String lastClipText = null;
    private android.content.BroadcastReceiver companionInfoReceiver;
    private static final String COMPANION_PREFS    = "smartdisplay_companion";
    private static final String KEY_COMPANION_HOST = "companion_host";
    private static final String KEY_COMPANION_TOKEN = "companion_token";
    private static final int REQ_SCREEN_CAPTURE = 7778;
    private View portraitHybridLayout;
    private com.limelight.ui.PortraitHybridController portraitHybridCtrl;
    private com.limelight.smartdisplay.TextFocusWatcher textFocusWatcher;

    // ÔöÇÔöÇ SmartDisplay AI ÔÇô Capa 2: Reconexi├│n autom├ítica ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
    private AutoReconnectManager autoReconnectManager;
    private SessionRecoveryManager sessionRecoveryManager;
    private WakeOnLanManager wakeOnLanManager;
    /** Vista overlay semitransparente que se muestra mientras se reconecta. */
    private android.view.View reconnectOverlayView;
    private android.widget.TextView reconnectStatusText;

    // ÔöÇÔöÇ SmartDisplay AI ÔÇô Calidad adaptativa mid-stream ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
    // Niveles: 0=full, 1=media, 2=baja
    // Cada nivel reduce bitrate + resoluci├│n progresivamente
    private static final int ADAPTIVE_QUALITY_POOR_THRESHOLD = 3;
    private static final int ADAPTIVE_QUALITY_WINDOW_MS = 10_000;
    private static final int[] ADAPTIVE_BITRATE_STEPS = {100, 70, 45};
    // Escala de resoluci├│n por nivel: {escalaX, escalaY} como % del original
    private static final float[] ADAPTIVE_RESOLUTION_SCALE = {1.0f, 0.75f, 0.55f};
    private int adaptiveQualityLevel = 0;        // ├¡ndice (0=full)
    private int adaptiveQualityPoorCount = 0;
    private long adaptiveQualityWindowStart = 0;
    private int adaptiveQualityOriginalBitrate = 0;
    private long adaptiveQualityLastOkayTime = 0;
    private boolean adaptiveQualityEnabled = false;
    private boolean adaptiveReconnecting = false; // true mientras se relanza
    

    private MediaCodecDecoderRenderer decoderRenderer;
    private boolean isTransitioningToPip = false;
    private boolean reportedCrash;

    private WifiManager.WifiLock highPerfWifiLock;
    private WifiManager.WifiLock lowLatencyWifiLock;
    private PowerManager.WakeLock cpuWakeLock;
    /** true cuando la pantalla est├í apagada pero la sesi├│n se mantiene viva. */
    private boolean isScreenOffMode = false;

    private boolean connectedToUsbDriverService = false;
    private ServiceConnection usbDriverServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            UsbDriverService.UsbDriverBinder binder = (UsbDriverService.UsbDriverBinder) iBinder;
            binder.setListener(controllerHandler);
            binder.setStateListener(Game.this);
            binder.start();
            connectedToUsbDriverService = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            connectedToUsbDriverService = false;
        }
    };

    public static final String EXTRA_HOST = "Host";
    public static final String EXTRA_PORT = "Port";
    public static final String EXTRA_HTTPS_PORT = "HttpsPort";
    public static final String EXTRA_APP_NAME = "AppName";
    public static final String EXTRA_APP_ID = "AppId";
    public static final String EXTRA_UNIQUEID = "UniqueId";
    public static final String EXTRA_PC_UUID = "UUID";
    public static final String EXTRA_PC_NAME = "PcName";
    public static final String EXTRA_APP_HDR = "HDR";
    public static final String EXTRA_SERVER_CERT = "ServerCert";
    public static final String EXTRA_MAC = "MacAddress";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SmartDisplay AI: init container (idempotent, safe to call from any activity)
        com.limelight.smartdisplay.AppContainer.init(this);

        UiHelper.setLocale(this);

        // We don't want a title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Full-screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // SmartDisplay: permitir capturas de pantalla con total libertad.
        // Limpiamos FLAG_SECURE expl├¡citamente para contrarrestar cualquier
        // flag heredado del tema, actividad padre o protecci├│n DRM impl├¡cita
        // de la Surface de video de Moonlight.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);

        // If we're going to use immersive mode, we want to have
        // the entire screen
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);

        // Listen for UI visibility events
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(this);

        // Change volume button behavior
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Inflate the content
        setContentView(R.layout.activity_game);

        View btnBack = findViewById(R.id.btnBackToDesktop);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        // Start the spinner
        spinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.conn_establishing_title),
                getResources().getString(R.string.conn_establishing_msg), true);

        // Read the stream preferences
        prefConfig = PreferenceConfiguration.readPreferences(this);
        tombstonePrefs = Game.this.getSharedPreferences("DecoderTombstone", 0);

        // SmartDisplay: calidad adaptativa activa por defecto si no es red medida
        ConnectivityManager initialConnMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        adaptiveQualityEnabled = !initialConnMgr.isActiveNetworkMetered();
        adaptiveQualityOriginalBitrate = prefConfig.bitrate;

        // Enter landscape unless we're on a square screen
        setPreferredOrientationForCurrentDisplay();

        if (prefConfig.stretchVideo || shouldIgnoreInsetsForResolution(prefConfig.width, prefConfig.height)) {
            // Allow the activity to layout under notches if the fill-screen option
            // was turned on by the user or it's a full-screen native resolution
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            }
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getWindow().getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        }

        // Listen for non-touch events on the game surface
        streamView = findViewById(R.id.surfaceView);
        streamView.setOnGenericMotionListener(this);
        streamView.setOnKeyListener(this);
        streamView.setInputCallbacks(this);

        // Listen for touch events on the background touch view to enable trackpad mode
        // to work on areas outside of the StreamView itself. We use a separate View
        // for this rather than just handling it at the Activity level, because that
        // allows proper touch splitting, which the OSC relies upon.
        View backgroundTouchView = findViewById(R.id.backgroundTouchView);
        backgroundTouchView.setOnTouchListener(this);

        // ÔöÇÔöÇ Zoom / Pan del stream (pinch + arrastre 2 dedos + doble-tap reset) ÔöÇ
        android.view.View streamWrapper = findViewById(R.id.streamTransformWrapper);
        if (streamWrapper != null) {
            streamTransformCtrl = new StreamViewTransformController(this, streamWrapper, backgroundTouchView);
        }
        
        // Inicializar Audio HUD
        audioHudController = new AudioHudController(this, (android.view.ViewGroup) streamView.getParent());
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Request unbuffered input event dispatching for all input classes we handle here.
            // Without this, input events are buffered to be delivered in lock-step with VBlank,
            // artificially increasing input latency while streaming.
            streamView.requestUnbufferedDispatch(
                    InputDevice.SOURCE_CLASS_BUTTON | // Keyboards
                    InputDevice.SOURCE_CLASS_JOYSTICK | // Gamepads
                    InputDevice.SOURCE_CLASS_POINTER | // Touchscreens and mice (w/o pointer capture)
                    InputDevice.SOURCE_CLASS_POSITION | // Touchpads
                    InputDevice.SOURCE_CLASS_TRACKBALL // Mice (pointer capture)
            );
            backgroundTouchView.requestUnbufferedDispatch(
                    InputDevice.SOURCE_CLASS_BUTTON | // Keyboards
                    InputDevice.SOURCE_CLASS_JOYSTICK | // Gamepads
                    InputDevice.SOURCE_CLASS_POINTER | // Touchscreens and mice (w/o pointer capture)
                    InputDevice.SOURCE_CLASS_POSITION | // Touchpads
                    InputDevice.SOURCE_CLASS_TRACKBALL // Mice (pointer capture)
            );
        }

        notificationOverlayView = findViewById(R.id.notificationOverlay);

        performanceOverlayView = findViewById(R.id.performanceOverlay);

        // ÔöÇÔöÇ Inicializar overlay FAB + Teclado l├│gico ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        View fabMenuView      = findViewById(R.id.overlayFabMenu);
        View keyboardPanelView = findViewById(R.id.overlayKeyboard);

        if (fabMenuView != null && keyboardPanelView != null) {
            logicalKeyboard      = new LogicalKeyboardOverlay(keyboardPanelView, this);
            overlayFabController = new OverlayFabController(fabMenuView);
            
            logicalKeyboard.setOnVisibilityChangedListener((visible, topY) -> {
                if (overlayFabController != null) {
                    overlayFabController.onKeyboardVisibilityChanged(visible, topY);
                }
                if (mouseModeCircle != null) {
                    mouseModeCircle.adjustForKeyboard(visible ? topY : 0);
                }
            });

            overlayFabController.setOnActionListener(new OverlayFabController.OnActionListener() {
                @Override
                public void onKeyboardToggle() {
                    if (logicalKeyboard.isVisible()) {
                        logicalKeyboard.hide();
                    } else {
                        logicalKeyboard.show();
                    }
                }

                @Override
                public void onMoveToggle() {
                    if (streamTransformCtrl != null) {
                        boolean nowActive = streamTransformCtrl.toggleMoveMode();
                        Toast.makeText(Game.this,
                                nowActive ? R.string.hub_move_mode_on : R.string.hub_move_mode_off,
                                Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onItemAction(int action) {
                    switch (action) {
                        case OverlayFabController.A_SCREEN: // Pantalla: alternar modo portrait/landscape h├¡brido
                            togglePortraitHybridMode();
                            break;
                        case OverlayFabController.A_MOUSE: // Modo mouse = c├¡rculo + trackpad relativo (unificado)
                            toggleMouseModeUnified();
                            break;
                        case OverlayFabController.A_FILES: // Explorador propio: enviar sin salir de la proyecci├│n
                            openFileBrowser();
                            break;
                        case OverlayFabController.A_VOICE: // Voz (micr├│fono)
                            if (voiceManager != null && voiceManager.checkPermissions(Game.this, 100)) {
                                if (voiceManager.isRecording()) {
                                    voiceManager.stop();
                                    // Restaurar el audio del PC en el celular al terminar de hablar.
                                    if (audioRenderer != null) audioRenderer.setMuted(false);
                                    Toast.makeText(Game.this, R.string.voice_deactivated, Toast.LENGTH_SHORT).show();
                                } else {
                                    // Silenciar el audio del PC mientras hablas: evita el bucle de eco
                                    // (altavoz ÔåÆ micr├│fono ÔåÆ PC) y deja la voz limpia.
                                    if (audioRenderer != null) audioRenderer.setMuted(true);
                                    voiceManager.start();
                                    Toast.makeText(Game.this, R.string.voice_activated, Toast.LENGTH_SHORT).show();
                                }
                            } else if (voiceManager == null) {
                                Toast.makeText(Game.this, R.string.voice_manager_not_ready, Toast.LENGTH_SHORT).show();
                            }
                            break;
                        case OverlayFabController.A_PIP: // Picture-in-Picture
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                try {
                                    isTransitioningToPip = true;
                                    enterPictureInPictureMode(getPictureInPictureParams(false));
                                } catch (Exception e) {
                                    isTransitioningToPip = false;
                                }
                            }
                            break;
                        case OverlayFabController.A_EXIT: // Salir de la sesi├│n
                            finish();
                            break;
                    }
                }

                @Override
                public void onMouseModeToggle() {
                    toggleMouseModeUnified();
                }
            });
        }

        // ÔöÇÔöÇ SmartDisplay AI: Modo mouse (trackpad circular) ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        mouseModeCircle = findViewById(R.id.mouseModeCircle);

        // ÔöÇÔöÇ SmartDisplay AI: Smart Taskbar (ventanas reales del PC) ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        // El id del <include> (overlaySmartTaskbar) sustituye al root del layout,
        // as├¡ que se busca por ese id, no por smartTaskbarRoot.
        View taskbarView = findViewById(R.id.overlaySmartTaskbar);
        if (taskbarView != null) {
            smartTaskbar = new com.limelight.ui.SmartTaskbarController(taskbarView,
                    new com.limelight.ui.SmartTaskbarController.Actions() {
                        @Override public void requestWindows() { sendBusCommand("get_windows", null); }
                        @Override public void focus(int hwnd) { sendBusCommand("focus_window", hwnd); }
                        @Override public void closeWindow(int hwnd) { sendBusCommand("close_window", hwnd); }
                        @Override public void minimize(int hwnd) { sendBusCommand("minimize_window", hwnd); }
                    });
        }

        // Edge pull-tab del Taskbar: tab que sobresale del borde izquierdo.
        // Tocar el tab o deslizar hacia la derecha despliega el botón completo.
        // Al pulsar el botón, abre/cierra la taskbar y se vuelve a ocultar.
        int overlayAccent = com.limelight.utils.ThemeManager.accentColor(this);
        View btnTaskbar   = findViewById(R.id.overlayBtnTaskbar);
        View edgeTab      = findViewById(R.id.taskbarEdgeTab);
        if (btnTaskbar != null) {
            tintToolButton(btnTaskbar, overlayAccent);

            // Valores de translación: oculto = -48dp, visible = 0
            float density = getResources().getDisplayMetrics().density;
            final float HIDDEN_TX  = -(54 * density);
            final float VISIBLE_TX = 0f;
            final long  ANIM_DUR   = 220;
            final long  AUTO_HIDE_MS = 2500;

            // Handler para auto-ocultar tras mostrar
            android.os.Handler edgeHideHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            Runnable hideEdgeBtn = () -> btnTaskbar.animate()
                    .translationX(HIDDEN_TX)
                    .setDuration(ANIM_DUR)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .start();

            // Helper para revelar el botón
            Runnable showEdgeBtn = () -> {
                edgeHideHandler.removeCallbacks(hideEdgeBtn);
                btnTaskbar.animate()
                        .translationX(VISIBLE_TX)
                        .setDuration(ANIM_DUR)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
                edgeHideHandler.postDelayed(hideEdgeBtn, AUTO_HIDE_MS);
            };

            // Tocar el tab visual → revelar botón
            if (edgeTab != null) {
                edgeTab.setOnTouchListener((v2, ev) -> {
                    if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
                            || ev.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                        if (Math.abs(btnTaskbar.getTranslationX() - VISIBLE_TX) > 4) {
                            showEdgeBtn.run();
                        }
                    }
                    return false;
                });
                edgeTab.setOnClickListener(v2 -> showEdgeBtn.run());
            }

            // Pulsar el botón → toggle taskbar + auto-ocultar
            btnTaskbar.setOnClickListener(v2 -> {
                if (smartTaskbar != null) smartTaskbar.toggle();
                edgeHideHandler.removeCallbacks(hideEdgeBtn);
                edgeHideHandler.postDelayed(hideEdgeBtn, AUTO_HIDE_MS);
            });
        }

        // ── SmartDisplay AI: Explorador de archivos en overlay ──────────────────────────────────
        View fileBrowserView = findViewById(R.id.fileBrowserOverlay);
        if (fileBrowserView != null) {
            fileBrowser = new com.limelight.ui.FileBrowserController(fileBrowserView, this, files -> {
                sendPickedFiles(files);
                return kotlin.Unit.INSTANCE;
            });
        }


        // ÔöÇÔöÇ SmartDisplay AI Premium: Cursor Adaptativo IA ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        adaptiveCursor = findViewById(R.id.adaptiveCursorView);
        if (adaptiveCursor != null) {
            smartCursorEngine = new com.limelight.ui.SmartCursorEngine(adaptiveCursor);
        }
        // El trackpad circular mueve el cursor REAL del PC (ya renderizado en el
        // stream). No se le enlaza la flecha adaptativa para evitar ver dos cursores.

        // ÔöÇÔöÇ SmartDisplay AI Premium: Barra de Control de Ventanas ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        View windowControlsView = findViewById(R.id.overlayWindowControls);
        if (windowControlsView != null) {
            windowControlsCtrl = new WindowControlsController(windowControlsView, this);
        }

        // ÔöÇÔöÇ SmartDisplay AI Premium: Portrait Hybrid ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        portraitHybridLayout = findViewById(R.id.portraitHybridLayout);
        if (portraitHybridLayout != null) {
            portraitHybridCtrl = new com.limelight.ui.PortraitHybridController(portraitHybridLayout, this);
            portraitHybridCtrl.setKeyboard(logicalKeyboard);
            portraitHybridCtrl.setCursorView(adaptiveCursor);
            
            View streamContainer = portraitHybridLayout.findViewById(R.id.hybridStreamContainer);
            if (streamContainer != null) {
                streamContainer.setOnTouchListener(this);
            }
        }
        inputCaptureProvider = InputCaptureManager.getInputCaptureProvider(this, this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            streamView.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
                @Override
                public boolean onCapturedPointer(View view, MotionEvent motionEvent) {
                    return handleMotionEvent(view, motionEvent);
                }
            });
        }

        // Warn the user if they're on a metered connection
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr.isActiveNetworkMetered()) {
            displayTransientMessage(getResources().getString(R.string.conn_metered));
        }

        // Make sure Wi-Fi is fully powered up
        WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            highPerfWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Moonlight High Perf Lock");
            highPerfWifiLock.setReferenceCounted(false);
            highPerfWifiLock.acquire();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                lowLatencyWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Moonlight Low Latency Lock");
                lowLatencyWifiLock.setReferenceCounted(false);
                lowLatencyWifiLock.acquire();
            }
        } catch (SecurityException e) {
            // Some Samsung Galaxy S10+/S10e devices throw a SecurityException from
            // WifiLock.acquire() even though we have android.permission.WAKE_LOCK in our manifest.
            e.printStackTrace();
        }

        // SmartDisplay AI: CPU WakeLock para mantener la conexi├│n incluso con pantalla suspendida
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartDisplay:CPUKeepAlive");
            cpuWakeLock.setReferenceCounted(false);
            cpuWakeLock.acquire();
            LimeLog.info("SmartDisplay: CPU WakeLock adquirido (pantalla puede suspenderse sin perder conexi├│n)");
        }

        appName = Game.this.getIntent().getStringExtra(EXTRA_APP_NAME);
        pcName = Game.this.getIntent().getStringExtra(EXTRA_PC_NAME);

        // ÔöÇÔöÇ SmartDisplay AI: init Capa 2 ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        sessionRecoveryManager = new SessionRecoveryManager(this);
        autoReconnectManager   = new AutoReconnectManager(this, new AutoReconnectManager.ReconnectCallback() {
            @Override
            public void doReconnect() {
                // Relanza una nueva sesión limpia sobre la Surface existente
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (streamView != null && streamView.getHolder().getSurface().isValid()) {
                            LimeLog.info("AutoReconnect: iniciando nueva sesión limpia de conexión");
                            startNewConnectionSession();
                        }
                    }
                });
            }

            @Override
            public void showReconnectingUI(int attempt, int maxAttempts) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (reconnectOverlayView != null) {
                            reconnectOverlayView.setAlpha(0f);
                            reconnectOverlayView.setVisibility(android.view.View.VISIBLE);
                            reconnectOverlayView.animate().alpha(1f).setDuration(300).start();
                        }
                        if (reconnectStatusText != null) {
                            reconnectStatusText.setText(
                                    getString(R.string.reconnect_status, attempt, maxAttempts));
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            if (streamView != null) {
                                streamView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(15f, 15f, android.graphics.Shader.TileMode.CLAMP));
                            }
                        }
                    }
                });
            }

            @Override
            public void hideReconnectingUI() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (reconnectOverlayView != null) {
                            reconnectOverlayView.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    reconnectOverlayView.setVisibility(android.view.View.GONE);
                                }
                            }).start();
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            if (streamView != null) {
                                streamView.setRenderEffect(null);
                            }
                        }
                        // Reconexi├│n exitosa: mantener pantalla encendida y despertarla si se apag├│
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                    }
                });
            }

            @Override
            public void onReconnectFailed() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (reconnectOverlayView != null) {
                            reconnectOverlayView.setVisibility(android.view.View.GONE);
                        }
                        // M├íximos intentos agotados ÔåÆ flujo normal de Moonlight
                        displayedFailureDialog = false;
                        Dialog.displayDialog(Game.this,
                                getResources().getString(R.string.wol_conn_terminated_title),
                                getString(R.string.reconnect_failed),
                                true);
                    }
                });
            }

            // ÔöÇÔöÇ Wake-on-LAN UI ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
            @Override
            public void showWakeOnLanUI(int elapsedSeconds, int maxSeconds) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (reconnectOverlayView != null) {
                            reconnectOverlayView.setAlpha(0f);
                            reconnectOverlayView.setVisibility(android.view.View.VISIBLE);
                            reconnectOverlayView.animate().alpha(1f).setDuration(300).start();
                        }
                        if (reconnectStatusText != null) {
                            reconnectStatusText.setText(
                                    getString(R.string.wol_waking_status, elapsedSeconds, maxSeconds));
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            if (streamView != null) {
                                streamView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(15f, 15f, android.graphics.Shader.TileMode.CLAMP));
                            }
                        }
                    }
                });
            }

            @Override
            public void hideWakeOnLanUI() {
                // Reutiliza la misma l├│gica de hideReconnectingUI ÔÇö el overlay es el mismo
                hideReconnectingUI();
            }
            // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        });
        autoReconnectManager.start();

        // Buscar views del overlay de reconexi├│n (opcionales en el layout)
        reconnectOverlayView = findViewById(R.id.reconnectOverlay);
        reconnectStatusText  = findViewById(R.id.reconnectStatusText);
        if (reconnectOverlayView != null) {
            reconnectOverlayView.setVisibility(android.view.View.GONE);
        }
        // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

        String host = Game.this.getIntent().getStringExtra(EXTRA_HOST);
        int port = Game.this.getIntent().getIntExtra(EXTRA_PORT, NvHTTP.DEFAULT_HTTP_PORT);
        int httpsPort = Game.this.getIntent().getIntExtra(EXTRA_HTTPS_PORT, 0); // 0 is treated as unknown
        int appId = Game.this.getIntent().getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID);
        String uniqueId = Game.this.getIntent().getStringExtra(EXTRA_UNIQUEID);
        boolean appSupportsHdr = Game.this.getIntent().getBooleanExtra(EXTRA_APP_HDR, false);
        byte[] derCertData = Game.this.getIntent().getByteArrayExtra(EXTRA_SERVER_CERT);

        // ÔöÇÔöÇ Wake-on-LAN Manager ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        String macAddress = Game.this.getIntent().getStringExtra(EXTRA_MAC);
        if (macAddress == null) macAddress = "";
        wakeOnLanManager = new WakeOnLanManager(host, httpsPort, macAddress);
        autoReconnectManager.setWakeOnLan(wakeOnLanManager);
        // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

        // Inicializar el Voice Manager con la IP del host
        if (host != null && !host.isEmpty()) {
            // Aprende autom├íticamente la IP del companion que el PC difunde (prioriza
            // Tailscale ÔåÆ funciona entre redes distintas). Cae al host del stream si no
            // se ha recibido ninguna. As├¡ archivos/voz/portapapeles se conectan sin
            // configuraci├│n manual. Ver registerCompanionInfoReceiver().
            registerCompanionInfoReceiver();
            String companionHost = getSharedPreferences(COMPANION_PREFS, MODE_PRIVATE)
                    .getString(KEY_COMPANION_HOST, null);
            if (companionHost == null || companionHost.isEmpty()) companionHost = host;

            // PIN de emparejamiento con el companion (Ajustes ÔåÆ Companion PIN).
            // Vac├¡o = sin auth (compatibilidad con companion sin config.json).
            final String companionPin = android.preference.PreferenceManager
                    .getDefaultSharedPreferences(this)
                    .getString("companion_pin", "").trim();

            voiceManager = new VoiceCaptureManager(companionHost, companionPin);

            // El explorador ya puede navegar tambi├®n los archivos del PC (modo "PC").
            if (fileBrowser != null) fileBrowser.setCompanionHost(companionHost, companionPin);

            // Inicializar el Bus de Comunicaci├│n de Capa 2 (WebSocket)
            smartDisplayBus = new SmartDisplayBus(companionHost, new SmartDisplayBus.MessageListener() {
                @Override
                public void onMessageReceived(JSONObject json) {
                    String type = json.optString("type", "");
                    if ("clipboard".equals(type)) {
                        final String text = json.optString("text", "");
                        runOnUiThread(() -> applyRemoteClipboard(text));
                    } else if ("foreground_app".equals(type)) {
                        // Notificar al registry: SmartBar, FAB y perfiles por app se
                        // suscriben para adaptar atajos a la aplicaci├│n activa del PC.
                        final String process = json.optString("process", "");
                        final String title   = json.optString("title", "");
                        if (!process.isEmpty()) {
                            com.limelight.smartdisplay.context.ForegroundAppRegistry.INSTANCE
                                    .update(process, title);
                        }
                    } else if ("cursor_profile".equals(type)) {
                        // Perfil de cursor por app: sensibilidad, precision mode,
                        // magnetismo del Smart Snap. Documentado en COMPANION_PROTOCOL.md (B.1).
                        final String app   = json.optString("app", "");
                        final double sens  = json.optDouble("sensitivity", 1.0);
                        final boolean prec = json.optBoolean("precision",   false);
                        final double mag   = json.optDouble("magnetism",    0.15);
                        com.limelight.smartdisplay.context.CursorProfileRegistry.INSTANCE
                                .update(app, (float) sens, prec, (float) mag);
                    } else if ("windows".equals(type)) {
                        // Smart Taskbar: lista REAL de ventanas del PC.
                        final org.json.JSONArray wins = json.optJSONArray("windows");
                        runOnUiThread(() -> {
                            if (smartTaskbar != null) smartTaskbar.render(wins);
                        });
                    } else if ("dev_result".equals(type)) {
                        // Panel DEV: resultado del gradlew ejecutado en el PC.
                        // Resultado dev_gradle ignorado (componente DEV eliminado)
                    }
                }

                @Override
                public void onConnected() {
                    LimeLog.info("SD_Bus: Bus de comunicaci├│n conectado");
                }

                @Override
                public void onDisconnected() {
                    LimeLog.info("SD_Bus: Bus de comunicaci├│n desconectado");
                }
            }, companionPin);
            smartDisplayBus.start();
            setupClipboardSync();

            // ÔöÇÔöÇ SmartDisplay AI Premium: TextFocusWatcher ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
            textFocusWatcher = new com.limelight.smartdisplay.TextFocusWatcher("ws://" + companionHost + ":8765", new com.limelight.smartdisplay.TextFocusWatcher.Callback() {
                @Override
                public void onTextFieldFocused(float x, float y, float w, float h, String app) {
                    LimeLog.info("TextFocusWatcher: Foco en campo de texto. App: " + app);
                    
                    if (logicalKeyboard != null && !logicalKeyboard.isVisible()) {
                        logicalKeyboard.show();
                    }
                    
                    if (portraitHybridCtrl != null) {
                        portraitHybridCtrl.updateSmartBarForApp(app);
                    }

                    if (adaptiveCursor != null && streamView != null && prefConfig != null && prefConfig.width > 0 && prefConfig.height > 0) {
                        float scaleX = (float) streamView.getWidth() / prefConfig.width;
                        float scaleY = (float) streamView.getHeight() / prefConfig.height;
                        
                        float localX = streamView.getX() + (x * scaleX);
                        float localY = streamView.getY() + (y * scaleY);
                        float localW = w * scaleX;
                        float localH = h * scaleY;
                        
                        adaptiveCursor.setFocusHighlight(localX, localY, localW, localH);

                        // SmartCursorEngine: el cursor adopta el estado TEXT (I-beam + halo).
                        if (smartCursorEngine != null) {
                            smartCursorEngine.onHoverElementChanged(
                                    com.limelight.ui.SmartCursorEngine.ElementType.TEXT_INPUT);
                        }

                    }
                }

                @Override
                public void onCursorPos(float x, float y) {
                    // MODELO DE AUTORIDAD: el PC es el maestro; el cursor gigante es su espejo.
                    if (adaptiveCursor == null || streamView == null || prefConfig == null
                            || prefConfig.width <= 0 || prefConfig.height <= 0) return;
                    // 1) Windows ÔåÆ coords locales del stream (sin transformar).
                    float scaleX = (float) streamView.getWidth() / prefConfig.width;
                    float scaleY = (float) streamView.getHeight() / prefConfig.height;
                    float localX = streamView.getX() + (x * scaleX);
                    float localY = streamView.getY() + (y * scaleY);
                    // 2) Aplicar la transformaci├│n de zoom/paneo del wrapper para que el
                    //    espejo siga siendo exacto cuando hay zoom: screen = pivot + (p - pivot)*scale + translation
                    android.view.View wrapper = findViewById(R.id.streamTransformWrapper);
                    if (wrapper != null && wrapper.getWidth() > 0) {
                        float pivotX = wrapper.getWidth() / 2f;
                        float pivotY = wrapper.getHeight() / 2f;
                        localX = pivotX + (localX - pivotX) * wrapper.getScaleX() + wrapper.getTranslationX();
                        localY = pivotY + (localY - pivotY) * wrapper.getScaleY() + wrapper.getTranslationY();
                    }
                    lastCursorPosMs = android.os.SystemClock.elapsedRealtime();
                    adaptiveCursor.moveTo(localX, localY);
                }

                @Override
                public void onTextFieldCleared() {
                    LimeLog.info("TextFocusWatcher: Foco fuera de campo de texto");
                    if (adaptiveCursor != null) {
                        adaptiveCursor.setFocusHighlight(0, 0, 0, 0);
                    }
                    if (smartCursorEngine != null) {
                        smartCursorEngine.onHoverElementChanged(
                                com.limelight.ui.SmartCursorEngine.ElementType.NONE);
                    }
                }

                @Override
                public void onHoverElement(String element, float x, float y, float w, float h,
                                           float snapX, float snapY, boolean hasSnap, String app) {
                    if (smartCursorEngine == null) return;
                    com.limelight.ui.SmartCursorEngine.ElementType type;
                    switch (element) {
                        case "text_input":   type = com.limelight.ui.SmartCursorEngine.ElementType.TEXT_INPUT; break;
                        case "button":       type = com.limelight.ui.SmartCursorEngine.ElementType.BUTTON; break;
                        case "link":         type = com.limelight.ui.SmartCursorEngine.ElementType.LINK; break;
                        case "ide_workspace":type = com.limelight.ui.SmartCursorEngine.ElementType.IDE_WORKSPACE; break;
                        default:             type = com.limelight.ui.SmartCursorEngine.ElementType.NONE; break;
                    }
                    smartCursorEngine.onHoverElementChanged(type);

                    // Smart Snap: convertir coordenadas remotas ÔåÆ locales y evaluar.
                    if (hasSnap && adaptiveCursor != null && streamView != null
                            && prefConfig != null && prefConfig.width > 0 && prefConfig.height > 0) {
                        float scaleX = (float) streamView.getWidth() / prefConfig.width;
                        float scaleY = (float) streamView.getHeight() / prefConfig.height;
                        float localSnapX = streamView.getX() + (snapX * scaleX);
                        float localSnapY = streamView.getY() + (snapY * scaleY);
                        smartCursorEngine.evaluateSmartSnap(
                                adaptiveCursor.getCursorX(), adaptiveCursor.getCursorY(),
                                localSnapX, localSnapY);
                    }
                }

                @Override
                public void onConnectionStatus(boolean connected) {
                    LimeLog.info("TextFocusWatcher: Conexi├│n " + (connected ? "establecida" : "perdida"));
                }
            }, companionPin);
            textFocusWatcher.connect();

            // Configurar la vista inicial seg├║n la orientaci├│n del dispositivo
            updateLayoutForOrientation(getResources().getConfiguration().orientation);
        }

        app = new NvApp(appName != null ? appName : "app", appId, appSupportsHdr);

        X509Certificate serverCert = null;
        try {
            if (derCertData != null) {
                serverCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(derCertData));
            }
        } catch (CertificateException e) {
            e.printStackTrace();
        }

        if (appId == StreamConfiguration.INVALID_APP_ID) {
            finish();
            return;
        }

        // Initialize the MediaCodec helper before creating the decoder
        GlPreferences glPrefs = GlPreferences.readPreferences(this);
        MediaCodecHelper.initialize(this, glPrefs.glRenderer);

        // Check if the user has enabled HDR
        boolean willStreamHdr = false;
        if (prefConfig.enableHdr) {
            // Start our HDR checklist
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Display display = getWindowManager().getDefaultDisplay();
                Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();

                // We must now ensure our display is compatible with HDR10
                if (hdrCaps != null) {
                    // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                    for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                        if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                            willStreamHdr = true;
                            break;
                        }
                    }
                }

                if (!willStreamHdr) {
                    // Nope, no HDR for us :(
                    Toast.makeText(this, "La pantalla no soporta HDR10", Toast.LENGTH_LONG).show();
                }
            }
            else {
                Toast.makeText(this, "HDR requiere Android 7.0 o posterior", Toast.LENGTH_LONG).show();
            }
        }

        // Check if the user has enabled performance stats overlay
        if (prefConfig.enablePerfOverlay) {
            performanceOverlayView.setVisibility(View.VISIBLE);
        }

        decoderRenderer = new MediaCodecDecoderRenderer(
                this,
                prefConfig,
                new CrashListener() {
                    @Override
                    public void notifyCrash(Exception e) {
                        // The MediaCodec instance is going down due to a crash
                        // let's tell the user something when they open the app again

                        // We must use commit because the app will crash when we return from this function
                        tombstonePrefs.edit().putInt("CrashCount", tombstonePrefs.getInt("CrashCount", 0) + 1).commit();
                        reportedCrash = true;
                    }
                },
                tombstonePrefs.getInt("CrashCount", 0),
                connMgr.isActiveNetworkMetered(),
                willStreamHdr,
                glPrefs.glRenderer,
                this);

        // Don't stream HDR if the decoder can't support it
        if (willStreamHdr && !decoderRenderer.isHevcMain10Hdr10Supported() && !decoderRenderer.isAv1Main10Supported()) {
            willStreamHdr = false;
            Toast.makeText(this, "El decodificador no soporta el perfil HDR10", Toast.LENGTH_LONG).show();
        }

        // Display a message to the user if HEVC was forced on but we still didn't find a decoder
        if (prefConfig.videoFormat == PreferenceConfiguration.FormatOption.FORCE_HEVC && !decoderRenderer.isHevcSupported()) {
            Toast.makeText(this, "No se encontr├│ decodificador HEVC", Toast.LENGTH_LONG).show();
        }

        // Display a message to the user if AV1 was forced on but we still didn't find a decoder
        if (prefConfig.videoFormat == PreferenceConfiguration.FormatOption.FORCE_AV1 && !decoderRenderer.isAv1Supported()) {
            Toast.makeText(this, "No se encontr├│ decodificador AV1", Toast.LENGTH_LONG).show();
        }

        // H.264 is always supported
        int supportedVideoFormats = MoonBridge.VIDEO_FORMAT_H264;
        if (decoderRenderer.isHevcSupported()) {
            supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_H265;
            if (willStreamHdr && decoderRenderer.isHevcMain10Hdr10Supported()) {
                supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_H265_MAIN10;
            }
        }
        if (decoderRenderer.isAv1Supported()) {
            supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_AV1_MAIN8;
            if (willStreamHdr && decoderRenderer.isAv1Main10Supported()) {
                supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_AV1_MAIN10;
            }
        }

        int gamepadMask = ControllerHandler.getAttachedControllerMask(this);
        if (!prefConfig.multiController) {
            // Always set gamepad 1 present for when multi-controller is
            // disabled for games that don't properly support detection
            // of gamepads removed and replugged at runtime.
            gamepadMask = 1;
        }
        if (prefConfig.onscreenController) {
            // If we're using OSC, always set at least gamepad 1.
            gamepadMask |= 1;
        }

        // Set to the optimal mode for streaming
        float displayRefreshRate = prepareDisplayForRendering();
        LimeLog.info("Display refresh rate: "+displayRefreshRate);

        // If the user requested frame pacing using a capped FPS, we will need to change our
        // desired FPS setting here in accordance with the active display refresh rate.
        int roundedRefreshRate = Math.round(displayRefreshRate);
        int chosenFrameRate = prefConfig.fps;
        if (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS) {
            if (prefConfig.fps >= roundedRefreshRate) {
                if (prefConfig.fps > roundedRefreshRate + 3) {
                    // Use frame drops when rendering above the screen frame rate
                    prefConfig.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED;
                    LimeLog.info("Using drop mode for FPS > Hz");
                } else if (roundedRefreshRate <= 49) {
                    // Let's avoid clearly bogus refresh rates and fall back to legacy rendering
                    prefConfig.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED;
                    LimeLog.info("Bogus refresh rate: " + roundedRefreshRate);
                }
                else {
                    chosenFrameRate = roundedRefreshRate - 1;
                    LimeLog.info("Adjusting FPS target for screen to " + chosenFrameRate);
                }
            }
        }

        // SmartDisplay: calidad adaptativa (bitrate + resoluci├│n)
        int effectiveBitrate = prefConfig.bitrate;
        int effectiveWidth = prefConfig.width;
        int effectiveHeight = prefConfig.height;
        if (adaptiveQualityEnabled && adaptiveQualityLevel > 0) {
            effectiveBitrate = getAdaptiveBitrate();
            int[] res = getAdaptiveResolution(prefConfig.width, prefConfig.height);
            effectiveWidth = res[0];
            effectiveHeight = res[1];
            LimeLog.info("AdaptiveQuality: nivel " + adaptiveQualityLevel + " ÔåÆ "
                    + effectiveWidth + "x" + effectiveHeight + " @ " + effectiveBitrate + " kbps");
        }

        StreamConfiguration config = new StreamConfiguration.Builder()
                .setResolution(effectiveWidth, effectiveHeight)
                .setLaunchRefreshRate(prefConfig.fps)
                .setRefreshRate(chosenFrameRate)
                .setApp(app)
                .setBitrate(effectiveBitrate)
                .setEnableSops(prefConfig.enableSops)
                // PC SIEMPRE en silencio durante el streaming: el audio del PC (videos)
                // se oye solo en el m├│vil. La voz m├│vilÔåÆPC es un canal UDP aparte.
                .enableLocalAudioPlayback(prefConfig.playHostAudio)
                .setMaxPacketSize(1392)
                .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO) // NvConnection will perform LAN and VPN detection
                .setSupportedVideoFormats(supportedVideoFormats)
                .setAttachedGamepadMask(gamepadMask)
                .setClientRefreshRateX100((int)(displayRefreshRate * 100))
                .setAudioConfiguration(prefConfig.audioConfiguration)
                .setColorSpace(decoderRenderer.getPreferredColorSpace())
                .setColorRange(decoderRenderer.getPreferredColorRange())
                .setPersistGamepadsAfterDisconnect(!prefConfig.multiController)
                .build();

        // Guardar parámetros de conexión para reconexión determinista
        this.connHost = host;
        this.connPort = port;
        this.connHttpsPort = httpsPort;
        this.connUniqueId = uniqueId;
        this.connServerCert = serverCert;
        this.streamConfig = config;

        // Initialize the connection
        conn = new NvConnection(getApplicationContext(),
                new ComputerDetails.AddressTuple(host, port),
                httpsPort, uniqueId, config,
                PlatformBinding.getCryptoProvider(this), serverCert);
        controllerHandler = new ControllerHandler(this, conn, this, prefConfig);
        keyboardTranslator = new KeyboardTranslator();

        // Proveer la conexi├│n al teclado l├│gico para poder enviar teclas al PC
        if (logicalKeyboard != null) {
            logicalKeyboard.setConnection(conn);
        }
        if (windowControlsCtrl != null) {
            windowControlsCtrl.setConnection(conn);
        }
        if (mouseModeCircle != null) {
            mouseModeCircle.setConnection(conn);
        }
        if (portraitHybridCtrl != null) {
            portraitHybridCtrl.setConnection(conn);
        }

        InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        inputManager.registerInputDeviceListener(keyboardTranslator, null);

        // SmartDisplay Touch Engine 2.0 (FASE 3): aplicar el perfil de interacci├│n
        // elegido por el usuario (Quick Actions) antes de procesar toques.
        new com.limelight.smartdisplay.profile.ProfileManager(this).applyToActive();

        // Initialize touch contexts
        for (int i = 0; i < touchContextMap.length; i++) {
            if (!prefConfig.touchscreenTrackpad) {
                touchContextMap[i] = new AbsoluteTouchContext(conn, i, streamView);
            }
            else {
                touchContextMap[i] = new RelativeTouchContext(conn, i,
                        REFERENCE_HORIZ_RES, REFERENCE_VERT_RES,
                        streamView, prefConfig);
            }
            touchContextMap[i].setOnRightClickListener((x, y) -> {
                if (streamTransformCtrl != null) {
                    streamTransformCtrl.zoomToRightClick(x, y);
                }
                if (adaptiveCursor != null) {
                    adaptiveCursor.fireRightClick(x, y);
                }
            });
            touchContextMap[i].setOnLeftClickListener((x, y) -> {
                if (adaptiveCursor != null) {
                    adaptiveCursor.fireLeftClick(x, y);
                }
            });
        }

        if (prefConfig.onscreenController) {
            // create virtual onscreen controller
            virtualController = new VirtualController(controllerHandler,
                    (FrameLayout)streamView.getParent(),
                    this);
            virtualController.refreshLayout();
            virtualController.show();
        }

        // Si iniciamos en modo trackpad, ocultar Cursor IA para evitar cursores duplicados
        if (prefConfig.touchscreenTrackpad && adaptiveCursor != null) {
            adaptiveCursor.setVisibility(View.GONE);
        }

        if (prefConfig.usbDriver) {
            // Start the USB driver
            bindService(new Intent(this, UsbDriverService.class),
                    usbDriverServiceConnection, Service.BIND_AUTO_CREATE);
        }

        if (!decoderRenderer.isAvcSupported()) {
            if (spinner != null) {
                spinner.dismiss();
                spinner = null;
            }

            // If we can't find an AVC decoder, we can't proceed
            Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title),
                    "This device or ROM doesn't support hardware accelerated H.264 playback.", true);
            return;
        }

        // The connection will be started when the surface gets created
        streamView.getHolder().addCallback(this);
    }

    private void setPreferredOrientationForCurrentDisplay() {
        Display display = getWindowManager().getDefaultDisplay();

        // For semi-square displays, we use more complex logic to determine which orientation to use (if any)
        if (PreferenceConfiguration.isSquarishScreen(display)) {
            int desiredOrientation = Configuration.ORIENTATION_UNDEFINED;

            // OSC doesn't properly support portrait displays, so don't use it in portrait mode by default
            if (prefConfig.onscreenController) {
                desiredOrientation = Configuration.ORIENTATION_LANDSCAPE;
            }

            // For native resolution, we will lock the orientation to the one that matches the specified resolution
            if (PreferenceConfiguration.isNativeResolution(prefConfig.width, prefConfig.height)) {
                if (prefConfig.width > prefConfig.height) {
                    desiredOrientation = Configuration.ORIENTATION_LANDSCAPE;
                }
                else {
                    desiredOrientation = Configuration.ORIENTATION_PORTRAIT;
                }
            }

            if (desiredOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
            }
            else if (desiredOrientation == Configuration.ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            }
            else {
                // If we don't have a reason to lock to portrait or landscape, allow any orientation
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
            }
        }
        else {
            // [CAMBIO] Permitir rotaci├│n libre ÔÇö el usuario puede girar el
            // dispositivo entre landscape y portrait libremente.
            // SCREEN_ORIENTATION_FULL_USER respeta el bloqueo de rotaci├│n
            // del sistema del usuario (si lo tiene activado, no rota).
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateLayoutForOrientation(newConfig.orientation);

        // Reajustar el teclado flotante para que no quede fuera de pantalla al rotar.
        if (logicalKeyboard != null) {
            logicalKeyboard.onOrientationChanged();
        }

        // Reajustar la c├ípsula de ventanas: su X guardada en horizontal puede quedar
        // fuera de la pantalla vertical (m├ís estrecha) ÔåÆ no se ve├¡a en vertical.
        if (windowControlsCtrl != null) {
            windowControlsCtrl.onOrientationChanged();
        }

        // Set requested orientation for possible new screen size
        setPreferredOrientationForCurrentDisplay();

        if (virtualController != null) {
            // Refresh layout of OSC for possible new screen size
            virtualController.refreshLayout();
        }

        // Hide on-screen overlays in PiP mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isInPictureInPictureMode()) {
                isHidingOverlays = true;

                if (virtualController != null) {
                    virtualController.hide();
                }

                performanceOverlayView.setVisibility(View.GONE);
                notificationOverlayView.setVisibility(View.GONE);

                // Disable sensors while in PiP mode
                controllerHandler.disableSensors();

                // Update GameManager state to indicate we're in PiP (still gaming, but interruptible)
                UiHelper.notifyStreamEnteringPiP(this);
            }
            else {
                isHidingOverlays = false;

                // Restore overlays to previous state when leaving PiP

                if (virtualController != null) {
                    virtualController.show();
                }

                if (prefConfig.enablePerfOverlay) {
                    performanceOverlayView.setVisibility(View.VISIBLE);
                }

                notificationOverlayView.setVisibility(requestedNotificationOverlayVisibility);

                // Enable sensors again after exiting PiP
                controllerHandler.enableSensors();

                // Update GameManager state to indicate we're out of PiP (gaming, non-interruptible)
                UiHelper.notifyStreamExitingPiP(this);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private PictureInPictureParams getPictureInPictureParams(boolean autoEnter) {
        PictureInPictureParams.Builder builder =
                new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(prefConfig.width, prefConfig.height))
                        .setSourceRectHint(new Rect(
                                streamView.getLeft(), streamView.getTop(),
                                streamView.getRight(), streamView.getBottom()));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter);
            builder.setSeamlessResizeEnabled(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (appName != null) {
                builder.setTitle(appName);
                if (pcName != null) {
                    builder.setSubtitle(pcName);
                }
            }
            else if (pcName != null) {
                builder.setTitle(pcName);
            }
        }

        return builder.build();
    }

    private void updatePipAutoEnter() {
        if (!prefConfig.enablePip) {
            return;
        }

        boolean autoEnter = connected && suppressPipRefCount == 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(getPictureInPictureParams(autoEnter));
        }
        else {
            autoEnterPip = autoEnter;
        }
    }

    public void setMetaKeyCaptureState(boolean enabled) {
        // This uses custom APIs present on some Samsung devices to allow capture of
        // meta key events while streaming.
        try {
            Class<?> semWindowManager = Class.forName("com.samsung.android.view.SemWindowManager");
            Method getInstanceMethod = semWindowManager.getMethod("getInstance");
            Object manager = getInstanceMethod.invoke(null);

            if (manager != null) {
                Class<?>[] parameterTypes = new Class<?>[2];
                parameterTypes[0] = ComponentName.class;
                parameterTypes[1] = boolean.class;
                Method requestMetaKeyEventMethod = semWindowManager.getDeclaredMethod("requestMetaKeyEvent", parameterTypes);
                requestMetaKeyEventMethod.invoke(manager, this.getComponentName(), enabled);
            }
            else {
                LimeLog.warning("SemWindowManager.getInstance() returned null");
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();

        // PiP is only supported on Oreo and later, and we don't need to manually enter PiP on
        // Android S and later. On Android R, we will use onPictureInPictureRequested() instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (autoEnterPip) {
                try {
                    // This has thrown all sorts of weird exceptions on Samsung devices
                    // running Oreo. Just eat them and close gracefully on leave, rather
                    // than crashing.
                    isTransitioningToPip = true;
                    enterPictureInPictureMode(getPictureInPictureParams(false));
                } catch (Exception e) {
                    isTransitioningToPip = false;
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        isTransitioningToPip = false;
        View fabContainer = findViewById(R.id.overlayFabContainer);
        if (fabContainer != null) {
            fabContainer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.R)
    public boolean onPictureInPictureRequested() {
        // Enter PiP when requested unless we're on Android 12 which supports auto-enter.
        if (autoEnterPip && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPictureInPictureMode(getPictureInPictureParams(false));
        }
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // We can't guarantee the state of modifiers keys which may have
        // lifted while focus was not on us. Clear the modifier state.
        this.modifierFlags = 0;

        // With Android native pointer capture, capture is lost when focus is lost,
        // so it must be requested again when focus is regained.
        inputCaptureProvider.onWindowFocusChanged(hasFocus);
    }

    private boolean isRefreshRateEqualMatch(float refreshRate) {
        return refreshRate >= prefConfig.fps &&
                refreshRate <= prefConfig.fps + 3;
    }

    private boolean isRefreshRateGoodMatch(float refreshRate) {
        return refreshRate >= prefConfig.fps &&
                Math.round(refreshRate) % prefConfig.fps <= 3;
    }

    private boolean shouldIgnoreInsetsForResolution(int width, int height) {
        // Never ignore insets for non-native resolutions
        if (!PreferenceConfiguration.isNativeResolution(width, height)) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display display = getWindowManager().getDefaultDisplay();
            for (Display.Mode candidate : display.getSupportedModes()) {
                // Ignore insets if this is an exact match for the display resolution
                if ((width == candidate.getPhysicalWidth() && height == candidate.getPhysicalHeight()) ||
                        (height == candidate.getPhysicalWidth() && width == candidate.getPhysicalHeight())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean mayReduceRefreshRate() {
        return prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS ||
                prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS ||
                (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_BALANCED && prefConfig.reduceRefreshRate);
    }

    private float prepareDisplayForRendering() {
        Display display = getWindowManager().getDefaultDisplay();
        WindowManager.LayoutParams windowLayoutParams = getWindow().getAttributes();
        float displayRefreshRate;

        // On M, we can explicitly set the optimal display mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode bestMode = display.getMode();
            boolean isNativeResolutionStream = PreferenceConfiguration.isNativeResolution(prefConfig.width, prefConfig.height);
            boolean refreshRateIsGood = isRefreshRateGoodMatch(bestMode.getRefreshRate());
            boolean refreshRateIsEqual = isRefreshRateEqualMatch(bestMode.getRefreshRate());

            LimeLog.info("Current display mode: "+bestMode.getPhysicalWidth()+"x"+
                    bestMode.getPhysicalHeight()+"x"+bestMode.getRefreshRate());

            for (Display.Mode candidate : display.getSupportedModes()) {
                boolean refreshRateReduced = candidate.getRefreshRate() < bestMode.getRefreshRate();
                boolean resolutionReduced = candidate.getPhysicalWidth() < bestMode.getPhysicalWidth() ||
                        candidate.getPhysicalHeight() < bestMode.getPhysicalHeight();
                boolean resolutionFitsStream = candidate.getPhysicalWidth() >= prefConfig.width &&
                        candidate.getPhysicalHeight() >= prefConfig.height;

                LimeLog.info("Examining display mode: "+candidate.getPhysicalWidth()+"x"+
                        candidate.getPhysicalHeight()+"x"+candidate.getRefreshRate());

                if (candidate.getPhysicalWidth() > 4096 && prefConfig.width <= 4096) {
                    // Avoid resolutions options above 4K to be safe
                    continue;
                }

                // On non-4K streams, we force the resolution to never change unless it's above
                // 60 FPS, which may require a resolution reduction due to HDMI bandwidth limitations,
                // or it's a native resolution stream.
                if (prefConfig.width < 3840 && prefConfig.fps <= 60 && !isNativeResolutionStream) {
                    if (display.getMode().getPhysicalWidth() != candidate.getPhysicalWidth() ||
                            display.getMode().getPhysicalHeight() != candidate.getPhysicalHeight()) {
                        continue;
                    }
                }

                // Make sure the resolution doesn't regress unless if it's over 60 FPS
                // where we may need to reduce resolution to achieve the desired refresh rate.
                if (resolutionReduced && !(prefConfig.fps > 60 && resolutionFitsStream)) {
                    continue;
                }

                if (mayReduceRefreshRate() && refreshRateIsEqual && !isRefreshRateEqualMatch(candidate.getRefreshRate())) {
                    // If we had an equal refresh rate and this one is not, skip it. In min latency
                    // mode, we want to always prefer the highest frame rate even though it may cause
                    // microstuttering.
                    continue;
                }
                else if (refreshRateIsGood) {
                    // We've already got a good match, so if this one isn't also good, it's not
                    // worth considering at all.
                    if (!isRefreshRateGoodMatch(candidate.getRefreshRate())) {
                        continue;
                    }

                    if (mayReduceRefreshRate()) {
                        // User asked for the lowest possible refresh rate, so don't raise it if we
                        // have a good match already
                        if (candidate.getRefreshRate() > bestMode.getRefreshRate()) {
                            continue;
                        }
                    }
                    else {
                        // User asked for the highest possible refresh rate, so don't reduce it if we
                        // have a good match already
                        if (refreshRateReduced) {
                            continue;
                        }
                    }
                }
                else if (!isRefreshRateGoodMatch(candidate.getRefreshRate())) {
                    // We didn't have a good match and this match isn't good either, so just don't
                    // reduce the refresh rate.
                    if (refreshRateReduced) {
                        continue;
                    }
                } else {
                    // We didn't have a good match and this match is good. Prefer this refresh rate
                    // even if it reduces the refresh rate. Lowering the refresh rate can be beneficial
                    // when streaming a 60 FPS stream on a 90 Hz device. We want to select 60 Hz to
                    // match the frame rate even if the active display mode is 90 Hz.
                }

                bestMode = candidate;
                refreshRateIsGood = isRefreshRateGoodMatch(candidate.getRefreshRate());
                refreshRateIsEqual = isRefreshRateEqualMatch(candidate.getRefreshRate());
            }

            LimeLog.info("Best display mode: "+bestMode.getPhysicalWidth()+"x"+
                    bestMode.getPhysicalHeight()+"x"+bestMode.getRefreshRate());

            // Only apply new window layout parameters if we've actually changed the display mode
            if (display.getMode().getModeId() != bestMode.getModeId()) {
                // If we only changed refresh rate and we're on an OS that supports Surface.setFrameRate()
                // use that instead of using preferredDisplayModeId to avoid the possibility of triggering
                // bugs that can cause the system to switch from 4K60 to 4K24 on Chromecast 4K.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        display.getMode().getPhysicalWidth() != bestMode.getPhysicalWidth() ||
                        display.getMode().getPhysicalHeight() != bestMode.getPhysicalHeight()) {
                    // Apply the display mode change
                    windowLayoutParams.preferredDisplayModeId = bestMode.getModeId();
                    getWindow().setAttributes(windowLayoutParams);
                }
                else {
                    LimeLog.info("Using setFrameRate() instead of preferredDisplayModeId due to matching resolution");
                }
            }
            else {
                LimeLog.info("Current display mode is already the best display mode");
            }

            displayRefreshRate = bestMode.getRefreshRate();
        }
        // On L, we can at least tell the OS that we want a refresh rate
        else {
            float bestRefreshRate = display.getRefreshRate();
            for (float candidate : display.getSupportedRefreshRates()) {
                LimeLog.info("Examining refresh rate: "+candidate);

                if (candidate > bestRefreshRate) {
                    // Ensure the frame rate stays around 60 Hz for <= 60 FPS streams
                    if (prefConfig.fps <= 60) {
                        if (candidate >= 63) {
                            continue;
                        }
                    }

                    bestRefreshRate = candidate;
                }
            }

            LimeLog.info("Selected refresh rate: "+bestRefreshRate);
            windowLayoutParams.preferredRefreshRate = bestRefreshRate;
            displayRefreshRate = bestRefreshRate;

            // Apply the refresh rate change
            getWindow().setAttributes(windowLayoutParams);
        }

        // Until Marshmallow, we can't ask for a 4K display mode, so we'll
        // need to hint the OS to provide one.
        boolean aspectRatioMatch = false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // We'll calculate whether we need to scale by aspect ratio. If not, we'll use
            // setFixedSize so we can handle 4K properly. The only known devices that have
            // >= 4K screens have exactly 4K screens, so we'll be able to hit this good path
            // on these devices. On Marshmallow, we can start changing to 4K manually but no
            // 4K devices run 6.0 at the moment.
            Point screenSize = new Point(0, 0);
            display.getSize(screenSize);

            double screenAspectRatio = ((double)screenSize.y) / screenSize.x;
            double streamAspectRatio = ((double)prefConfig.height) / prefConfig.width;
            if (Math.abs(screenAspectRatio - streamAspectRatio) < 0.001) {
                LimeLog.info("Stream has compatible aspect ratio with output display");
                aspectRatioMatch = true;
            }
        }

        if (prefConfig.stretchVideo || aspectRatioMatch) {
            // Set the surface to the size of the video
            streamView.getHolder().setFixedSize(prefConfig.width, prefConfig.height);
        }
        else {
            // Set the surface to scale based on the aspect ratio of the stream
            streamView.setDesiredAspectRatio((double)prefConfig.width / (double)prefConfig.height);
        }

        // Set the desired refresh rate that will get passed into setFrameRate() later
        desiredRefreshRate = displayRefreshRate;

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // TVs may take a few moments to switch refresh rates, and we can probably assume
            // it will be eventually activated.
            // TODO: Improve this
            return displayRefreshRate;
        }
        else {
            // Use the lower of the current refresh rate and the selected refresh rate.
            // The preferred refresh rate may not actually be applied (ex: Battery Saver mode).
            return Math.min(getWindowManager().getDefaultDisplay().getRefreshRate(), displayRefreshRate);
        }
    }

    @SuppressLint("InlinedApi")
    private final Runnable hideSystemUi = new Runnable() {
            @Override
            public void run() {
                // TODO: Do we want to use WindowInsetsController here on R+ instead of
                // SYSTEM_UI_FLAG_IMMERSIVE_STICKY? They seem to do the same thing as of S...

                // In multi-window mode on N+, we need to drop our layout flags or we'll
                // be drawing underneath the system UI.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode()) {
                    Game.this.getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                }
                else {
                    // Use immersive mode
                    Game.this.getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                }
            }
    };

    private void hideSystemUi(int delay) {
        Handler h = getWindow().getDecorView().getHandler();
        if (h != null) {
            h.removeCallbacks(hideSystemUi);
            h.postDelayed(hideSystemUi, delay);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.N)
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);

        // In multi-window, we don't want to use the full-screen layout
        // flag. It will cause us to collide with the system UI.
        // This function will also be called for PiP so we can cover
        // that case here too.
        if (isInMultiWindowMode) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decoderRenderer.notifyVideoBackground();
        }
        else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decoderRenderer.notifyVideoForeground();
        }

        // Correct the system UI visibility flags
        hideSystemUi(50);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LimeLog.info("Game Activity destroyed");
        
        if (voiceManager != null) {
            voiceManager.stop();
        }

        if (smartDisplayBus != null) {
            // Restaurar audio del PC antes de desconectar el bus (el companion
            // server forzar├í a Windows a re-enumerar el dispositivo de audio).
            try {
                org.json.JSONObject restoreAudioMsg = new org.json.JSONObject();
                restoreAudioMsg.put("type", "restore_audio");
                smartDisplayBus.sendMessage(restoreAudioMsg);
            } catch (Exception ignored) {}
            smartDisplayBus.stop();
        }

        // Detener el poll del badge de conexi├│n.
        connStatsHandler.removeCallbacks(connStatsRunnable);

        // Limpiar los registries globales para que ning├║n listener de la sesi├│n
        // anterior siga vivo al iniciar una nueva.
        com.limelight.smartdisplay.context.ForegroundAppRegistry.INSTANCE.reset();
        com.limelight.smartdisplay.context.CursorProfileRegistry.INSTANCE.reset();

        if (fileServer != null) {
            fileServer.stop();
            fileServer = null;
        }

        if (clipboardManager != null && clipListener != null) {
            try { clipboardManager.removePrimaryClipChangedListener(clipListener); } catch (Exception ignored) {}
            clipListener = null;
        }

        if (companionInfoReceiver != null) {
            try { unregisterReceiver(companionInfoReceiver); } catch (Exception ignored) {}
            companionInfoReceiver = null;
        }

        if (textFocusWatcher != null) {
            textFocusWatcher.disconnect();
        }

        // Detener captura de pantalla si est├í activa (el servicio vive en
        // proceso separado, pero la activity ya no escuchar├í broadcasts)
        stopScreenCaptureService();

        // ÔöÇÔöÇ SmartDisplay AI: liberar Capa 2 ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        if (autoReconnectManager != null) {
            autoReconnectManager.stop();
        }
        // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

        // SmartDisplay: detener bucles de animaci├│n de overlays (halo FAB / RGB teclado)
        if (overlayFabController != null) {
            overlayFabController.release();
        }
        if (logicalKeyboard != null) {
            logicalKeyboard.release();
        }
        if (adaptiveCursor != null) {
            adaptiveCursor.release();
        }
        if (windowControlsCtrl != null) {
            windowControlsCtrl.release();
        }

        if (controllerHandler != null) {
            controllerHandler.destroy();
        }
        if (keyboardTranslator != null) {
            InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
            inputManager.unregisterInputDeviceListener(keyboardTranslator);
        }

        if (cpuWakeLock != null) {
            cpuWakeLock.release();
            LimeLog.info("SmartDisplay: CPU WakeLock liberado");
        }
        if (lowLatencyWifiLock != null) {
            lowLatencyWifiLock.release();
        }
        if (highPerfWifiLock != null) {
            highPerfWifiLock.release();
        }

        if (connectedToUsbDriverService) {
            // Unbind from the discovery service
            unbindService(usbDriverServiceConnection);
        }

        // Destroy the capture provider
        inputCaptureProvider.destroy();
    }

    /**
     * Handles the result of the RECORD_AUDIO permission request (code 100).
     * If granted, starts voice capture immediately so the user does not have
     * to press the Voice button a second time.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission just granted: start voice automatically
                if (voiceManager != null && !voiceManager.isRecording()) {
                    // Silenciar el audio del PC mientras hablas (evita el bucle de eco).
                    if (audioRenderer != null) audioRenderer.setMuted(true);
                    voiceManager.start();
                    Toast.makeText(this, R.string.voice_activated, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, R.string.voice_permission_needed, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
            // Stop any further input device notifications before we lose focus (and pointer capture)
            if (controllerHandler != null) {
                controllerHandler.stop();
            }

            // Ungrab input to prevent further input device notifications
            setInputGrabState(false);
        }

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ÔöÇÔöÇ SmartDisplay AI: Volviendo de pantalla suspendida ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        // Cuando la pantalla se enciende tras haber estado apagada durante el
        // streaming, onResume() se llama ANTES de surfaceCreated(). Restauramos
        // FLAG_KEEP_SCREEN_ON para evitar que se vuelva a apagar inmediatamente,
        // y dejamos que surfaceCreated() reinicie el decoder cuando la Surface
        // est├® lista. Si la conexi├│n se perdi├│ mientras tanto, el AutoReconnectManager
        // se encargar├í de reconectarla.
        if (isScreenOffMode) {
            LimeLog.info("onResume: volviendo de pantalla suspendida ÔÇö restaurando flags");
            isScreenOffMode = false;
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // La Surface se recrear├í ÔåÆ surfaceCreated() ÔåÆ el decoder debe reinicializarse.
            // Si la conexi├│n ya no est├í viva, connectionTerminated() activar├í la reconexi├│n.
            // SmartDisplay AI: reactivar el FAB una vez que la pantalla est├í visible.
            if (overlayFabController != null) {
                overlayFabController.exitPerformanceMode(); // reactiva halo pulse
            }
        } else if (connected) {
            // SmartDisplay AI: si la pantalla se apag├│ por timeout y el usuario
            // la enciende, re-a├▒adir FLAG_KEEP_SCREEN_ON para que no se vuelva
            // a apagar inmediatamente. Tambi├®n forzar el brillo de la pantalla
            // con FLAG_TURN_SCREEN_ON si estaba apagada.
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            LimeLog.info("onResume: reforzando FLAG_KEEP_SCREEN_ON (conexi├│n activa)");
        }
        // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
    }

    @Override
    protected void onStop() {
        super.onStop();

        boolean inPip = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            inPip = isInPictureInPictureMode();
        }

        // SmartDisplay AI: Si el OS llama a onStop() pero estamos en PiP o transicionando a PiP,
        // no debemos cancelar la conexi├│n ni la reconexi├│n.
        if (inPip || isTransitioningToPip) {
            LimeLog.info("onStop ignorado por estar en PiP mode o transicionando.");
            return;
        }

        // ÔöÇÔöÇ SmartDisplay AI: Pantalla suspendida ÔåÆ mantener conexi├│n viva ÔöÇÔöÇÔöÇÔöÇÔöÇ
        // Si la actividad NO est├í finalizando (el usuario no puls├│ Back/Home) y la
        // pantalla est├í apagada, asumimos que es un bloqueo/suspensi├│n del dispositivo.
        // En lugar de matar la sesi├│n, ocultamos los overlays y dejamos que
        // WiFiLock + CPU WakeLock mantengan la red y el procesamiento activos.
        // Cuando la pantalla se encienda, onResume() reanudar├í normalmente.
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean screenOff = pm != null && !pm.isInteractive();
        if (!isFinishing() && screenOff && connected) {
            LimeLog.info("onStop: pantalla suspendida ÔÇö manteniendo conexi├│n y WakeLocks activos");
            isScreenOffMode = true;
            // Ocultar overlays para no consumir recursos
            if (virtualController != null) {
                virtualController.hide();
            }
            // SmartDisplay AI: colapsar el FAB silenciosamente para que no
            // intente animar sobre una Surface que est├í a punto de destruirse.
            if (overlayFabController != null) {
                overlayFabController.collapse();
                overlayFabController.enterPerformanceMode(); // detiene halo pulse y animaciones
            }
            // La conexi├│n, los WiFiLocks y el CPU WakeLock se mantienen vivos.
            // El decoder se pausar├í en surfaceDestroyed() sin matar la conexi├│n.
            return;
        }
        // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        if (virtualController != null) {
            virtualController.hide();
        }

        // ÔöÇÔöÇ SmartDisplay AI: cancelar reconexi├│n si el usuario sale ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        // IMPORTANTE: Si hay una reconexi├│n activa en curso (e.g. el PC se suspendi├│
        // y estamos esperando a que despierte), NO cancelamos: permitimos que el
        // AutoReconnectManager siga vivo para retomar la sesi├│n cuando el PC
        // vuelva. Solo cancelamos si el usuario sali├│ expl├¡citamente (isFinishing).
        if (autoReconnectManager != null) {
            if (isFinishing()) {
                // El usuario sali├│ voluntariamente: cancelar todo.
                autoReconnectManager.cancelReconnect();
                if (sessionRecoveryManager != null) {
                    sessionRecoveryManager.clearSession();
                }
            } else if (!autoReconnectManager.isReconnecting()) {
                // No hay reconexi├│n activa: limpiar sesi├│n normalmente.
                if (sessionRecoveryManager != null) {
                    sessionRecoveryManager.clearSession();
                }
            }
            // Si isReconnecting() == true y !isFinishing(), NO cancelamos:
            // el PC puede estar suspendido y reconectar├í cuando despierte.
        }
        // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

        if (conn != null) {
            int videoFormat = decoderRenderer.getActiveVideoFormat();

            displayedFailureDialog = true;
            stopConnection();

            if (prefConfig.enableLatencyToast) {
                int averageEndToEndLat = decoderRenderer.getAverageEndToEndLatency();
                int averageDecoderLat = decoderRenderer.getAverageDecoderLatency();
                String message = null;
                if (averageEndToEndLat > 0) {
                    message = getResources().getString(R.string.conn_client_latency)+" "+averageEndToEndLat+" ms";
                    if (averageDecoderLat > 0) {
                        message += " ("+getResources().getString(R.string.conn_client_latency_hw)+" "+averageDecoderLat+" ms)";
                    }
                }
                else if (averageDecoderLat > 0) {
                    message = getResources().getString(R.string.conn_hardware_latency)+" "+averageDecoderLat+" ms";
                }

                // Add the video codec to the post-stream toast
                if (message != null) {
                    message += " [";

                    if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_H264) != 0) {
                        message += "H.264";
                    }
                    else if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_H265) != 0) {
                        message += "HEVC";
                    }
                    else if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_AV1) != 0) {
                        message += "AV1";
                    }
                    else {
                        message += "UNKNOWN";
                    }

                    if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0) {
                        message += " HDR";
                    }

                    message += "]";
                }

                if (message != null) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            }

            // Clear the tombstone count if we terminated normally
            if (!reportedCrash && tombstonePrefs.getInt("CrashCount", 0) != 0) {
                tombstonePrefs.edit()
                        .putInt("CrashCount", 0)
                        .putInt("LastNotifiedCrashCount", 0)
                        .apply();
            }
        }

        finish();
    }

    /**
     * Modo mouse unificado: el c├¡rculo flotante y el modo trackpad relativo van
     * juntos. Mostrar el c├¡rculo activa el trackpad; ocultarlo vuelve a t├íctil
     * absoluto. (Antes eran dos tent├ículos: "Modo Mouse" y "Cursor IA".)
     */
    /** Env├¡a un comando simple al PC por el bus (Smart Taskbar: ventanas). */
    /** Ti├▒e un bot├│n flotante de herramientas (icono + borde) con el acento del tema. */
    private void tintToolButton(View btn, int accent) {
        if (btn instanceof android.widget.ImageView) {
            ((android.widget.ImageView) btn).setColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        android.graphics.drawable.Drawable bg = btn.getBackground();
        if (bg instanceof android.graphics.drawable.GradientDrawable) {
            int soft = (accent & 0x00FFFFFF) | 0x99000000;
            ((android.graphics.drawable.GradientDrawable) bg.mutate())
                    .setStroke(Math.round(btn.getResources().getDisplayMetrics().density), soft);
        }
    }

    private void sendBusCommand(String type, Integer hwnd) {
        if (smartDisplayBus == null) return;
        try {
            JSONObject j = new JSONObject();
            j.put("type", type);
            if (hwnd != null) j.put("hwnd", (int) hwnd);
            smartDisplayBus.sendMessage(j);
        } catch (Exception ignored) {
        }
    }

    private void togglePortraitHybridMode() {
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
        } else {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
        }
    }

    private void toggleMouseModeUnified() {
        boolean shown = (mouseModeCircle != null) && mouseModeCircle.toggle();
        if (prefConfig != null && prefConfig.touchscreenTrackpad != shown) {
            toggleTouchMode();   // sincroniza el modo de entrada con el c├¡rculo
        }
        // Con el c├¡rculo activo el ├║nico cursor visible es el REAL del PC:
        // ocultamos la flecha adaptativa para no mostrar dos cursores.
        if (adaptiveCursor != null && shown) {
            adaptiveCursor.setVisibility(View.GONE);
        }
        Toast.makeText(this,
                shown ? "Modo mouse: activado (trackpad + c├¡rculo)"
                      : "Modo mouse: desactivado",
                Toast.LENGTH_SHORT).show();
    }

    public void toggleTouchMode() {
        prefConfig.touchscreenTrackpad = !prefConfig.touchscreenTrackpad;
        
        for (int i = 0; i < touchContextMap.length; i++) {
            if (!prefConfig.touchscreenTrackpad) {
                touchContextMap[i] = new AbsoluteTouchContext(conn, i, streamView);
            }
            else {
                touchContextMap[i] = new RelativeTouchContext(conn, i,
                        REFERENCE_HORIZ_RES, REFERENCE_VERT_RES,
                        streamView, prefConfig);
            }
            touchContextMap[i].setOnRightClickListener((x, y) -> {
                if (streamTransformCtrl != null) {
                    streamTransformCtrl.zoomToRightClick(x, y);
                }
                if (adaptiveCursor != null) {
                    adaptiveCursor.fireRightClick(x, y);
                }
            });
            touchContextMap[i].setOnLeftClickListener((x, y) -> {
                if (adaptiveCursor != null) {
                    adaptiveCursor.fireLeftClick(x, y);
                }
            });
        }
        
        if (prefConfig.touchscreenTrackpad) {
            Toast.makeText(this, "Modo Trackpad Activado (Movimiento libre y Clicks)", Toast.LENGTH_SHORT).show();
            if (adaptiveCursor != null) {
                adaptiveCursor.setVisibility(View.GONE);
            }
        } else {
            Toast.makeText(this, "Modo T├íctil Activado (Cursor IA)", Toast.LENGTH_SHORT).show();
            if (adaptiveCursor != null) {
                adaptiveCursor.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setInputGrabState(boolean grab) {
        // Grab/ungrab the mouse cursor
        if (grab) {
            inputCaptureProvider.enableCapture();

            // Enabling capture may hide the cursor again, so
            // we will need to show it again.
            if (cursorVisible) {
                inputCaptureProvider.showCursor();
            }
        }
        else {
            inputCaptureProvider.disableCapture();
        }

        // Grab/ungrab system keyboard shortcuts
        setMetaKeyCaptureState(grab);

        grabbedInput = grab;
    }

    private final Runnable toggleGrab = new Runnable() {
        @Override
        public void run() {
            setInputGrabState(!grabbedInput);
        }
    };

    // Returns true if the key stroke was consumed
    private boolean handleSpecialKeys(int androidKeyCode, boolean down) {
        int modifierMask = 0;
        int nonModifierKeyCode = KeyEvent.KEYCODE_UNKNOWN;

        if (androidKeyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
            androidKeyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            modifierMask = KeyboardPacket.MODIFIER_CTRL;
        }
        else if (androidKeyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                 androidKeyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            modifierMask = KeyboardPacket.MODIFIER_SHIFT;
        }
        else if (androidKeyCode == KeyEvent.KEYCODE_ALT_LEFT ||
                 androidKeyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            modifierMask = KeyboardPacket.MODIFIER_ALT;
        }
        else if (androidKeyCode == KeyEvent.KEYCODE_META_LEFT ||
                androidKeyCode == KeyEvent.KEYCODE_META_RIGHT) {
            modifierMask = KeyboardPacket.MODIFIER_META;
        }
        else {
            nonModifierKeyCode = androidKeyCode;
        }

        if (down) {
            this.modifierFlags |= modifierMask;
        }
        else {
            this.modifierFlags &= ~modifierMask;
        }

        // Handle the special combos on the key up
        if (waitingForAllModifiersUp || specialKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
            if (specialKeyCode == androidKeyCode) {
                // If this is a key up for the special key itself, eat that because the host never saw the original key down
                return true;
            }
            else if (modifierFlags != 0) {
                // While we're waiting for modifiers to come up, eat all key downs and allow all key ups to pass
                return down;
            }
            else {
                // When all modifiers are up, perform the special action
                switch (specialKeyCode) {
                    // Toggle input grab
                    case KeyEvent.KEYCODE_Z:
                        Handler h = getWindow().getDecorView().getHandler();
                        if (h != null) {
                            h.postDelayed(toggleGrab, 250);
                        }
                        break;

                    // Quit
                    case KeyEvent.KEYCODE_Q:
                        finish();
                        break;

                    // Toggle cursor visibility
                    case KeyEvent.KEYCODE_C:
                        if (!grabbedInput) {
                            inputCaptureProvider.enableCapture();
                            grabbedInput = true;
                        }
                        cursorVisible = !cursorVisible;
                        if (cursorVisible) {
                            inputCaptureProvider.showCursor();
                        } else {
                            inputCaptureProvider.hideCursor();
                        }
                        break;

                    default:
                        break;
                }

                // Reset special key state
                specialKeyCode = KeyEvent.KEYCODE_UNKNOWN;
                waitingForAllModifiersUp = false;
            }
        }
        // Check if Ctrl+Alt+Shift is down when a non-modifier key is pressed
        else if ((modifierFlags & (KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_ALT | KeyboardPacket.MODIFIER_SHIFT)) ==
                (KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_ALT | KeyboardPacket.MODIFIER_SHIFT) &&
                (down && nonModifierKeyCode != KeyEvent.KEYCODE_UNKNOWN)) {
            switch (androidKeyCode) {
                case KeyEvent.KEYCODE_Z:
                case KeyEvent.KEYCODE_Q:
                case KeyEvent.KEYCODE_C:
                    // Remember that a special key combo was activated, so we can consume all key
                    // events until the modifiers come up
                    specialKeyCode = androidKeyCode;
                    waitingForAllModifiersUp = true;
                    return true;

                default:
                    // This isn't a special combo that we consume on the client side
                    return false;
            }
        }

        // Not a special combo
        return false;
    }

    // We cannot simply use modifierFlags for all key event processing, because
    // some IMEs will not generate real key events for pressing Shift. Instead
    // they will simply send key events with isShiftPressed() returning true,
    // and we will need to send the modifier flag ourselves.
    private byte getModifierState(KeyEvent event) {
        // Start with the global modifier state to ensure we cover the case
        // detailed in https://github.com/moonlight-stream/moonlight-android/issues/840
        byte modifier = getModifierState();
        if (event.isShiftPressed()) {
            modifier |= KeyboardPacket.MODIFIER_SHIFT;
        }
        if (event.isCtrlPressed()) {
            modifier |= KeyboardPacket.MODIFIER_CTRL;
        }
        if (event.isAltPressed()) {
            modifier |= KeyboardPacket.MODIFIER_ALT;
        }
        if (event.isMetaPressed()) {
            modifier |= KeyboardPacket.MODIFIER_META;
        }
        return modifier;
    }

    private byte getModifierState() {
        return (byte) modifierFlags;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return handleKeyDown(event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean handleKeyDown(KeyEvent event) {
        // Pass-through virtual navigation keys
        if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
            return false;
        }

        // Handle a synthetic back button event that some Android OS versions
        // create as a result of a right-click. This event WILL repeat if
        // the right mouse button is held down, so we ignore those.
        int eventSource = event.getSource();
        if ((eventSource == InputDevice.SOURCE_MOUSE ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE) &&
                event.getKeyCode() == KeyEvent.KEYCODE_BACK) {

            // Send the right mouse button event if mouse back and forward
            // are disabled. If they are enabled, handleMotionEvent() will take
            // care of this.
            if (!prefConfig.mouseNavButtons) {
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
            }

            // Always return true, otherwise the back press will be propagated
            // up to the parent and finish the activity.
            return true;
        }

        boolean handled = false;

        if (ControllerHandler.isGameControllerDevice(event.getDevice())) {
            // Always try the controller handler first, unless it's an alphanumeric keyboard device.
            // Otherwise, controller handler will eat keyboard d-pad events.
            handled = controllerHandler.handleButtonDown(event);
        }

        // Try the keyboard handler if it wasn't handled as a game controller
        if (!handled) {
            // Let this method take duplicate key down events
            if (handleSpecialKeys(event.getKeyCode(), true)) {
                return true;
            }

            // Pass through keyboard input if we're not grabbing
            if (!grabbedInput) {
                return false;
            }

            // We'll send it as a raw key event if we have a key mapping, otherwise we'll send it
            // as UTF-8 text (if it's a printable character).
            short translated = keyboardTranslator.translate(event.getKeyCode(), event.getDeviceId());
            if (translated == 0) {
                // Make sure it has a valid Unicode representation and it's not a dead character
                // (which we don't support). If those are true, we can send it as UTF-8 text.
                //
                // NB: We need to be sure this happens before the getRepeatCount() check because
                // UTF-8 events don't auto-repeat on the host side.
                int unicodeChar = event.getUnicodeChar();
                if ((unicodeChar & KeyCharacterMap.COMBINING_ACCENT) == 0 && (unicodeChar & KeyCharacterMap.COMBINING_ACCENT_MASK) != 0) {
                    conn.sendUtf8Text(""+(char)unicodeChar);
                    return true;
                }

                return false;
            }

            // Eat repeat down events
            if (event.getRepeatCount() > 0) {
                return true;
            }

            conn.sendKeyboardInput(translated, KeyboardPacket.KEY_DOWN, getModifierState(event),
                    keyboardTranslator.hasNormalizedMapping(event.getKeyCode(), event.getDeviceId()) ? 0 : MoonBridge.SS_KBE_FLAG_NON_NORMALIZED);
        }

        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return handleKeyUp(event) || super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean handleKeyUp(KeyEvent event) {
        // Pass-through virtual navigation keys
        if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
            return false;
        }

        // Handle a synthetic back button event that some Android OS versions
        // create as a result of a right-click.
        int eventSource = event.getSource();
        if ((eventSource == InputDevice.SOURCE_MOUSE ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE) &&
                event.getKeyCode() == KeyEvent.KEYCODE_BACK) {

            // Send the right mouse button event if mouse back and forward
            // are disabled. If they are enabled, handleMotionEvent() will take
            // care of this.
            if (!prefConfig.mouseNavButtons) {
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
            }

            // Always return true, otherwise the back press will be propagated
            // up to the parent and finish the activity.
            return true;
        }

        boolean handled = false;
        if (ControllerHandler.isGameControllerDevice(event.getDevice())) {
            // Always try the controller handler first, unless it's an alphanumeric keyboard device.
            // Otherwise, controller handler will eat keyboard d-pad events.
            handled = controllerHandler.handleButtonUp(event);
        }

        // Try the keyboard handler if it wasn't handled as a game controller
        if (!handled) {
            if (handleSpecialKeys(event.getKeyCode(), false)) {
                return true;
            }

            // Pass through keyboard input if we're not grabbing
            if (!grabbedInput) {
                return false;
            }

            short translated = keyboardTranslator.translate(event.getKeyCode(), event.getDeviceId());
            if (translated == 0) {
                // If we sent this event as UTF-8 on key down, also report that it was handled
                // when we get the key up event for it.
                int unicodeChar = event.getUnicodeChar();
                return (unicodeChar & KeyCharacterMap.COMBINING_ACCENT) == 0 && (unicodeChar & KeyCharacterMap.COMBINING_ACCENT_MASK) != 0;
            }

            conn.sendKeyboardInput(translated, KeyboardPacket.KEY_UP, getModifierState(event),
                    keyboardTranslator.hasNormalizedMapping(event.getKeyCode(), event.getDeviceId()) ? 0 : MoonBridge.SS_KBE_FLAG_NON_NORMALIZED);
        }

        return true;
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        return handleKeyMultiple(event) || super.onKeyMultiple(keyCode, repeatCount, event);
    }

    private boolean handleKeyMultiple(KeyEvent event) {
        // We can receive keys from a software keyboard that don't correspond to any existing
        // KEYCODE value. Android will give those to us as an ACTION_MULTIPLE KeyEvent.
        //
        // Despite the fact that the Android docs say this is unused since API level 29, these
        // events are still sent as of Android 13 for the above case.
        //
        // For other cases of ACTION_MULTIPLE, we will not report those as handled so hopefully
        // they will be passed to us again as regular singular key events.
        if (event.getKeyCode() != KeyEvent.KEYCODE_UNKNOWN || event.getCharacters() == null) {
            return false;
        }

        conn.sendUtf8Text(event.getCharacters());
        return true;
    }

    private TouchContext getTouchContext(int actionIndex)
    {
        if (actionIndex < touchContextMap.length) {
            return touchContextMap[actionIndex];
        }
        else {
            return null;
        }
    }

    @Override
    public void toggleKeyboard() {
        LimeLog.info("Toggling keyboard overlay");
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.toggleSoftInput(0, 0);
    }

    private byte getLiTouchTypeFromEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                return MoonBridge.LI_TOUCH_EVENT_DOWN;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if ((event.getFlags() & MotionEvent.FLAG_CANCELED) != 0) {
                    return MoonBridge.LI_TOUCH_EVENT_CANCEL;
                }
                else {
                    return MoonBridge.LI_TOUCH_EVENT_UP;
                }

            case MotionEvent.ACTION_MOVE:
                return MoonBridge.LI_TOUCH_EVENT_MOVE;

            case MotionEvent.ACTION_CANCEL:
                // ACTION_CANCEL applies to *all* pointers in the gesture, so it maps to CANCEL_ALL
                // rather than CANCEL. For a single pointer cancellation, that's indicated via
                // FLAG_CANCELED on a ACTION_POINTER_UP.
                // https://developer.android.com/develop/ui/views/touch-and-input/gestures/multi
                return MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL;

            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                return MoonBridge.LI_TOUCH_EVENT_HOVER;

            case MotionEvent.ACTION_HOVER_EXIT:
                return MoonBridge.LI_TOUCH_EVENT_HOVER_LEAVE;

            case MotionEvent.ACTION_BUTTON_PRESS:
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return MoonBridge.LI_TOUCH_EVENT_BUTTON_ONLY;

            default:
               return -1;
        }
    }

    private float[] getStreamViewRelativeNormalizedXY(View view, MotionEvent event, int pointerIndex) {
        float normalizedX = event.getX(pointerIndex);
        float normalizedY = event.getY(pointerIndex);

        // For the containing background view, we must subtract the origin
        // of the StreamView to get video-relative coordinates.
        if (view != streamView) {
            normalizedX -= streamView.getX();
            normalizedY -= streamView.getY();
        }

        normalizedX = Math.max(normalizedX, 0.0f);
        normalizedY = Math.max(normalizedY, 0.0f);

        normalizedX = Math.min(normalizedX, streamView.getWidth());
        normalizedY = Math.min(normalizedY, streamView.getHeight());

        normalizedX /= streamView.getWidth();
        normalizedY /= streamView.getHeight();

        return new float[] { normalizedX, normalizedY };
    }

    private static float normalizeValueInRange(float value, InputDevice.MotionRange range) {
        return (value - range.getMin()) / range.getRange();
    }

    private static float getPressureOrDistance(MotionEvent event, int pointerIndex) {
        InputDevice dev = event.getDevice();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_HOVER_EXIT:
                // Hover events report distance
                if (dev != null) {
                    InputDevice.MotionRange distanceRange = dev.getMotionRange(MotionEvent.AXIS_DISTANCE, event.getSource());
                    if (distanceRange != null) {
                        return normalizeValueInRange(event.getAxisValue(MotionEvent.AXIS_DISTANCE, pointerIndex), distanceRange);
                    }
                }
                return 0.0f;

            default:
                // Other events report pressure
                return event.getPressure(pointerIndex);
        }
    }

    private static short getRotationDegrees(MotionEvent event, int pointerIndex) {
        InputDevice dev = event.getDevice();
        if (dev != null) {
            if (dev.getMotionRange(MotionEvent.AXIS_ORIENTATION, event.getSource()) != null) {
                short rotationDegrees = (short) Math.toDegrees(event.getOrientation(pointerIndex));
                if (rotationDegrees < 0) {
                    rotationDegrees += 360;
                }
                return rotationDegrees;
            }
        }
        return MoonBridge.LI_ROT_UNKNOWN;
    }

    private static float[] polarToCartesian(float r, float theta) {
        return new float[] { (float)(r * Math.cos(theta)), (float)(r * Math.sin(theta)) };
    }

    private static float cartesianToR(float[] point) {
        return (float)Math.sqrt(Math.pow(point[0], 2) + Math.pow(point[1], 2));
    }

    private float[] getStreamViewNormalizedContactArea(MotionEvent event, int pointerIndex) {
        float orientation;

        // If the orientation is unknown, we'll just assume it's at a 45 degree angle and scale it by
        // X and Y scaling factors evenly.
        if (event.getDevice() == null || event.getDevice().getMotionRange(MotionEvent.AXIS_ORIENTATION, event.getSource()) == null) {
            orientation = (float)(Math.PI / 4);
        }
        else {
            orientation = event.getOrientation(pointerIndex);
        }

        float contactAreaMajor, contactAreaMinor;
        switch (event.getActionMasked()) {
            // Hover events report the tool size
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_HOVER_EXIT:
                contactAreaMajor = event.getToolMajor(pointerIndex);
                contactAreaMinor = event.getToolMinor(pointerIndex);
                break;

            // Other events report contact area
            default:
                contactAreaMajor = event.getTouchMajor(pointerIndex);
                contactAreaMinor = event.getTouchMinor(pointerIndex);
                break;
        }

        // The contact area major axis is parallel to the orientation, so we simply convert
        // polar to cartesian coordinates using the orientation as theta.
        float[] contactAreaMajorCartesian = polarToCartesian(contactAreaMajor, orientation);

        // The contact area minor axis is perpendicular to the contact area major axis (and thus
        // the orientation), so rotate the orientation angle by 90 degrees.
        float[] contactAreaMinorCartesian = polarToCartesian(contactAreaMinor, (float)(orientation + (Math.PI / 2)));

        // Normalize the contact area to the stream view size
        contactAreaMajorCartesian[0] = Math.min(Math.abs(contactAreaMajorCartesian[0]), streamView.getWidth()) / streamView.getWidth();
        contactAreaMinorCartesian[0] = Math.min(Math.abs(contactAreaMinorCartesian[0]), streamView.getWidth()) / streamView.getWidth();
        contactAreaMajorCartesian[1] = Math.min(Math.abs(contactAreaMajorCartesian[1]), streamView.getHeight()) / streamView.getHeight();
        contactAreaMinorCartesian[1] = Math.min(Math.abs(contactAreaMinorCartesian[1]), streamView.getHeight()) / streamView.getHeight();

        // Convert the normalized values back into polar coordinates
        return new float[] { cartesianToR(contactAreaMajorCartesian), cartesianToR(contactAreaMinorCartesian) };
    }

    private boolean sendPenEventForPointer(View view, MotionEvent event, byte eventType, byte toolType, int pointerIndex) {
        byte penButtons = 0;
        if ((event.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0) {
            penButtons |= MoonBridge.LI_PEN_BUTTON_PRIMARY;
        }
        if ((event.getButtonState() & MotionEvent.BUTTON_STYLUS_SECONDARY) != 0) {
            penButtons |= MoonBridge.LI_PEN_BUTTON_SECONDARY;
        }

        byte tiltDegrees = MoonBridge.LI_TILT_UNKNOWN;
        InputDevice dev = event.getDevice();
        if (dev != null) {
            if (dev.getMotionRange(MotionEvent.AXIS_TILT, event.getSource()) != null) {
                tiltDegrees = (byte)Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex));
            }
        }

        float[] normalizedCoords = getStreamViewRelativeNormalizedXY(view, event, pointerIndex);
        float[] normalizedContactArea = getStreamViewNormalizedContactArea(event, pointerIndex);
        return conn.sendPenEvent(eventType, toolType, penButtons,
                normalizedCoords[0], normalizedCoords[1],
                getPressureOrDistance(event, pointerIndex),
                normalizedContactArea[0], normalizedContactArea[1],
                getRotationDegrees(event, pointerIndex), tiltDegrees) != MoonBridge.LI_ERR_UNSUPPORTED;
    }

    private static byte convertToolTypeToStylusToolType(MotionEvent event, int pointerIndex) {
        switch (event.getToolType(pointerIndex)) {
            case MotionEvent.TOOL_TYPE_ERASER:
                return MoonBridge.LI_TOOL_TYPE_ERASER;
            case MotionEvent.TOOL_TYPE_STYLUS:
                return MoonBridge.LI_TOOL_TYPE_PEN;
            default:
                return MoonBridge.LI_TOOL_TYPE_UNKNOWN;
        }
    }

    private boolean trySendPenEvent(View view, MotionEvent event) {
        byte eventType = getLiTouchTypeFromEvent(event);
        if (eventType < 0) {
            return false;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            // Move events may impact all active pointers
            boolean handledStylusEvent = false;
            for (int i = 0; i < event.getPointerCount(); i++) {
                byte toolType = convertToolTypeToStylusToolType(event, i);
                if (toolType == MoonBridge.LI_TOOL_TYPE_UNKNOWN) {
                    // Not a stylus pointer, so skip it
                    continue;
                }
                else {
                    // This pointer is a stylus, so we'll report that we handled this event
                    handledStylusEvent = true;
                }

                if (!sendPenEventForPointer(view, event, eventType, toolType, i)) {
                    // Pen events aren't supported by the host
                    return false;
                }
            }
            return handledStylusEvent;
        }
        else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            // Cancel impacts all active pointers
            return conn.sendPenEvent(MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL, MoonBridge.LI_TOOL_TYPE_UNKNOWN, (byte)0,
                    0, 0, 0, 0, 0,
                    MoonBridge.LI_ROT_UNKNOWN, MoonBridge.LI_TILT_UNKNOWN) != MoonBridge.LI_ERR_UNSUPPORTED;
        }
        else {
            // Up, Down, and Hover events are specific to the action index
            byte toolType = convertToolTypeToStylusToolType(event, event.getActionIndex());
            if (toolType == MoonBridge.LI_TOOL_TYPE_UNKNOWN) {
                // Not a stylus event
                return false;
            }
            return sendPenEventForPointer(view, event, eventType, toolType, event.getActionIndex());
        }
    }

    private boolean sendTouchEventForPointer(View view, MotionEvent event, byte eventType, int pointerIndex) {
        float[] normalizedCoords = getStreamViewRelativeNormalizedXY(view, event, pointerIndex);
        float[] normalizedContactArea = getStreamViewNormalizedContactArea(event, pointerIndex);
        return conn.sendTouchEvent(eventType, event.getPointerId(pointerIndex),
                normalizedCoords[0], normalizedCoords[1],
                getPressureOrDistance(event, pointerIndex),
                normalizedContactArea[0], normalizedContactArea[1],
                getRotationDegrees(event, pointerIndex)) != MoonBridge.LI_ERR_UNSUPPORTED;
    }

    private boolean trySendTouchEvent(View view, MotionEvent event) {
        byte eventType = getLiTouchTypeFromEvent(event);
        if (eventType < 0) {
            return false;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            // Move events may impact all active pointers
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (!sendTouchEventForPointer(view, event, eventType, i)) {
                    return false;
                }
            }
            return true;
        }
        else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            // Cancel impacts all active pointers
            return conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL, 0,
                    0, 0, 0, 0, 0,
                    MoonBridge.LI_ROT_UNKNOWN) != MoonBridge.LI_ERR_UNSUPPORTED;
        }
        else {
            // Up, Down, and Hover events are specific to the action index
            return sendTouchEventForPointer(view, event, eventType, event.getActionIndex());
        }
    }

    // -- Adaptive Touch Mode --
    private int getAdaptiveX(View view, MotionEvent event, int actionIndex) {
        return getAdaptiveMappedValue(view, event.getX(actionIndex), true);
    }
    
    private int getAdaptiveY(View view, MotionEvent event, int actionIndex) {
        return getAdaptiveMappedValue(view, event.getY(actionIndex), false);
    }
    
    private int getAdaptiveHistoricalX(View view, MotionEvent event, int actionIndex, int pos) {
        return getAdaptiveMappedValue(view, event.getHistoricalX(actionIndex, pos), true);
    }
    
    private int getAdaptiveHistoricalY(View view, MotionEvent event, int actionIndex, int pos) {
        return getAdaptiveMappedValue(view, event.getHistoricalY(actionIndex, pos), false);
    }
    
    private int getAdaptiveMappedValue(View view, float rawVal, boolean isX) {
        if (view == streamView) {
            return (int) rawVal;
        }
        float scale = 1.0f;
        float translate = 0f;
        float pivot = 0f;
        if (streamTransformCtrl != null) {
            scale = streamTransformCtrl.getScale();
            translate = isX ? streamTransformCtrl.getTranslateX() : streamTransformCtrl.getTranslateY();
            View streamWrapper = findViewById(R.id.streamTransformWrapper);
            if (streamWrapper != null) {
                pivot = isX ? (streamWrapper.getWidth() / 2f) : (streamWrapper.getHeight() / 2f);
            }
        }
        float unscaledVal = pivot + (rawVal - translate - pivot) / scale;
        if (streamView != null && !prefConfig.touchscreenTrackpad) {
            unscaledVal -= isX ? streamView.getX() : streamView.getY();
        }
        return (int) unscaledVal;
    }

    // Returns true if the event was consumed
    // NB: View is only present if called from a view callback
    private boolean handleMotionEvent(View view, MotionEvent event) {
        // Pass through mouse/touch/joystick input if we're not grabbing
        if (!grabbedInput) {
            return false;
        }

        int eventSource = event.getSource();
        int deviceSources = event.getDevice() != null ? event.getDevice().getSources() : 0;
        if ((eventSource & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
            if (controllerHandler.handleMotionEvent(event)) {
                return true;
            }
        }
        else if ((deviceSources & InputDevice.SOURCE_CLASS_JOYSTICK) != 0 && controllerHandler.tryHandleTouchpadEvent(event)) {
            return true;
        }
        else if ((eventSource & InputDevice.SOURCE_CLASS_POINTER) != 0 ||
                 (eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0 ||
                 eventSource == InputDevice.SOURCE_MOUSE_RELATIVE)
        {
            // This case is for mice and non-finger touch devices
            if (eventSource == InputDevice.SOURCE_MOUSE ||
                    (eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0 || // SOURCE_TOUCHPAD
                    eventSource == InputDevice.SOURCE_MOUSE_RELATIVE ||
                    (event.getPointerCount() >= 1 &&
                            (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE ||
                                    event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                                    event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER)) ||
                    eventSource == 12290) // 12290 = Samsung DeX mode desktop mouse
            {
                int buttonState = event.getButtonState();
                int changedButtons = buttonState ^ lastButtonState;

                // The DeX touchpad on the Fold 4 sends proper right click events using BUTTON_SECONDARY,
                // but doesn't send BUTTON_PRIMARY for a regular click. Instead it sends ACTION_DOWN/UP,
                // so we need to fix that up to look like a sane input event to process it correctly.
                if (eventSource == 12290) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        buttonState |= MotionEvent.BUTTON_PRIMARY;
                    }
                    else if (event.getAction() == MotionEvent.ACTION_UP) {
                        buttonState &= ~MotionEvent.BUTTON_PRIMARY;
                    }
                    else {
                        // We may be faking the primary button down from a previous event,
                        // so be sure to add that bit back into the button state.
                        buttonState |= (lastButtonState & MotionEvent.BUTTON_PRIMARY);
                    }

                    changedButtons = buttonState ^ lastButtonState;
                }

                // Ignore mouse input if we're not capturing from our input source
                if (!inputCaptureProvider.isCapturingActive()) {
                    // We return true here because otherwise the events may end up causing
                    // Android to synthesize d-pad events.
                    return true;
                }

                // Always update the position before sending any button events. If we're
                // dealing with a stylus without hover support, our position might be
                // significantly different than before.
                if (inputCaptureProvider.eventHasRelativeMouseAxes(event)) {
                    // Send the deltas straight from the motion event
                    short deltaX = (short)inputCaptureProvider.getRelativeAxisX(event);
                    short deltaY = (short)inputCaptureProvider.getRelativeAxisY(event);

                    if (deltaX != 0 || deltaY != 0) {
                        if (prefConfig.absoluteMouseMode) {
                            // NB: view may be null, but we can unconditionally use streamView because we don't need to adjust
                            // relative axis deltas for the position of the streamView within the parent's coordinate system.
                            conn.sendMouseMoveAsMousePosition(deltaX, deltaY, (short)streamView.getWidth(), (short)streamView.getHeight());
                        }
                        else {
                            conn.sendMouseMove(deltaX, deltaY);
                        }
                    }
                }
                else if ((eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0) {
                    // If this input device is not associated with the view itself (like a trackpad),
                    // we'll convert the device-specific coordinates to use to send the cursor position.
                    // This really isn't ideal but it's probably better than nothing.
                    //
                    // Trackpad on newer versions of Android (Oreo and later) should be caught by the
                    // relative axes case above. If we get here, we're on an older version that doesn't
                    // support pointer capture.
                    InputDevice device = event.getDevice();
                    if (device != null) {
                        InputDevice.MotionRange xRange = device.getMotionRange(MotionEvent.AXIS_X, eventSource);
                        InputDevice.MotionRange yRange = device.getMotionRange(MotionEvent.AXIS_Y, eventSource);

                        // All touchpads coordinate planes should start at (0, 0)
                        if (xRange != null && yRange != null && xRange.getMin() == 0 && yRange.getMin() == 0) {
                            int xMax = (int)xRange.getMax();
                            int yMax = (int)yRange.getMax();

                            // Touchpads must be smaller than (65535, 65535)
                            if (xMax <= Short.MAX_VALUE && yMax <= Short.MAX_VALUE) {
                                conn.sendMousePosition((short)event.getX(), (short)event.getY(),
                                                       (short)xMax, (short)yMax);
                            }
                        }
                    }
                }
                else if (view != null && trySendPenEvent(view, event)) {
                    // If our host supports pen events, send it directly
                    return true;
                }
                else if (view != null) {
                    // Otherwise send absolute position based on the view for SOURCE_CLASS_POINTER
                    updateMousePosition(view, event);
                }

                if (event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
                    // Send the vertical scroll packet
                    conn.sendMouseHighResScroll((short)(event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 120));
                    conn.sendMouseHighResHScroll((short)(event.getAxisValue(MotionEvent.AXIS_HSCROLL) * 120));
                }

                if ((changedButtons & MotionEvent.BUTTON_PRIMARY) != 0) {
                    if ((buttonState & MotionEvent.BUTTON_PRIMARY) != 0) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                    }
                    else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                    }
                }

                // Mouse secondary or stylus primary is right click (stylus down is left click)
                if ((changedButtons & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_STYLUS_PRIMARY)) != 0) {
                    if ((buttonState & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_STYLUS_PRIMARY)) != 0) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                    }
                    else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                    }
                }

                // Mouse tertiary or stylus secondary is middle click
                if ((changedButtons & (MotionEvent.BUTTON_TERTIARY | MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0) {
                    if ((buttonState & (MotionEvent.BUTTON_TERTIARY | MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE);
                    }
                    else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE);
                    }
                }

                if (prefConfig.mouseNavButtons) {
                    if ((changedButtons & MotionEvent.BUTTON_BACK) != 0) {
                        if ((buttonState & MotionEvent.BUTTON_BACK) != 0) {
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X1);
                        }
                        else {
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1);
                        }
                    }

                    if ((changedButtons & MotionEvent.BUTTON_FORWARD) != 0) {
                        if ((buttonState & MotionEvent.BUTTON_FORWARD) != 0) {
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X2);
                        }
                        else {
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2);
                        }
                    }
                }

                // Handle stylus presses
                if (event.getPointerCount() == 1 && event.getActionIndex() == 0) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                            lastAbsTouchDownTime = event.getEventTime();
                            lastAbsTouchDownX = event.getX(0);
                            lastAbsTouchDownY = event.getY(0);

                            // Stylus is left click
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                        } else if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) {
                            lastAbsTouchDownTime = event.getEventTime();
                            lastAbsTouchDownX = event.getX(0);
                            lastAbsTouchDownY = event.getY(0);

                            // Eraser is right click
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                        }
                    }
                    else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                            lastAbsTouchUpTime = event.getEventTime();
                            lastAbsTouchUpX = event.getX(0);
                            lastAbsTouchUpY = event.getY(0);

                            // Stylus is left click
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        } else if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) {
                            lastAbsTouchUpTime = event.getEventTime();
                            lastAbsTouchUpX = event.getX(0);
                            lastAbsTouchUpY = event.getY(0);

                            // Eraser is right click
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                        }
                    }
                }

                lastButtonState = buttonState;
            }
            // This case is for fingers
            else
            {
                if (virtualController != null &&
                        (virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons ||
                         virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons)) {
                    // Ignore presses when the virtual controller is being configured
                    return true;
                }

                int actionIndex = event.getActionIndex();

                int eventX = getAdaptiveX(view, event, actionIndex);
                int eventY = getAdaptiveY(view, event, actionIndex);

                // Special handling for 3 finger gesture
                if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN &&
                        event.getPointerCount() == 3) {
                    // Three fingers down
                    threeFingerDownTime = event.getEventTime();

                    // Cancel the first and second touches to avoid
                    // erroneous events
                    for (TouchContext aTouchContext : touchContextMap) {
                        aTouchContext.cancelTouch();
                    }

                    return true;
                }

                // TODO: Re-enable native touch when have a better solution for handling
                // cancelled touches from Android gestures and 3 finger taps to activate
                // the software keyboard.
                /*if (!prefConfig.touchscreenTrackpad && trySendTouchEvent(view, event)) {
                    // If this host supports touch events and absolute touch is enabled,
                    // send it directly as a touch event.
                    return true;
                }*/

                TouchContext context = getTouchContext(actionIndex);
                if (context == null) {
                    return false;
                }

                switch (event.getActionMasked())
                {
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_DOWN:
                    for (TouchContext touchContext : touchContextMap) {
                        touchContext.setPointerCount(event.getPointerCount());
                    }
                    context.touchDownEvent(eventX, eventY, event.getEventTime(), true);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                    if (event.getPointerCount() == 1 &&
                            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || (event.getFlags() & MotionEvent.FLAG_CANCELED) == 0)) {
                        // All fingers up
                        if (event.getEventTime() - threeFingerDownTime < THREE_FINGER_TAP_THRESHOLD) {
                            // This is a 3 finger tap to bring up the keyboard
                            toggleKeyboard();
                            return true;
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (event.getFlags() & MotionEvent.FLAG_CANCELED) != 0) {
                        context.cancelTouch();
                    }
                    else {
                        context.touchUpEvent(eventX, eventY, event.getEventTime());
                    }

                    for (TouchContext touchContext : touchContextMap) {
                        touchContext.setPointerCount(event.getPointerCount() - 1);
                    }
                    if (actionIndex == 0 && event.getPointerCount() > 1 && !context.isCancelled()) {
                        // The original secondary touch now becomes primary
                        context.touchDownEvent(
                                getAdaptiveX(view, event, 1),
                                getAdaptiveY(view, event, 1),
                                event.getEventTime(), false);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    // ACTION_MOVE is special because it always has actionIndex == 0
                    // We'll call the move handlers for all indexes manually

                    // First process the historical events
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        for (TouchContext aTouchContextMap : touchContextMap) {
                            if (aTouchContextMap.getActionIndex() < event.getPointerCount())
                            {
                                aTouchContextMap.touchMoveEvent(
                                        getAdaptiveHistoricalX(view, event, aTouchContextMap.getActionIndex(), i),
                                        getAdaptiveHistoricalY(view, event, aTouchContextMap.getActionIndex(), i),
                                        event.getHistoricalEventTime(i));
                            }
                        }
                    }

                    // Now process the current values
                    for (TouchContext aTouchContextMap : touchContextMap) {
                        if (aTouchContextMap.getActionIndex() < event.getPointerCount())
                        {
                            aTouchContextMap.touchMoveEvent(
                                    getAdaptiveX(view, event, aTouchContextMap.getActionIndex()),
                                    getAdaptiveY(view, event, aTouchContextMap.getActionIndex()),
                                    event.getEventTime());
                        }
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    for (TouchContext aTouchContext : touchContextMap) {
                        aTouchContext.cancelTouch();
                        aTouchContext.setPointerCount(0);
                    }
                    break;
                default:
                    return false;
                }
            }

            // Handled a known source
            return true;
        }

        // Unknown class
        return false;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return handleMotionEvent(null, event) || super.onGenericMotionEvent(event);

    }

    private void updateMousePosition(View touchedView, MotionEvent event) {
        // X and Y are already relative to the provided view object
        float eventX, eventY;

        // For our StreamView itself, we can use the coordinates unmodified.
        if (touchedView == streamView) {
            eventX = event.getX(0);
            eventY = event.getY(0);
        }
        else {
            // For the containing background view, we must subtract the origin
            // of the StreamView to get video-relative coordinates.
            eventX = event.getX(0) - streamView.getX();
            eventY = event.getY(0) - streamView.getY();
        }

        if (event.getPointerCount() == 1 && event.getActionIndex() == 0 &&
                (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER ||
                event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS))
        {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_EXIT:
                case MotionEvent.ACTION_HOVER_MOVE:
                    if (event.getEventTime() - lastAbsTouchUpTime <= STYLUS_UP_DEAD_ZONE_DELAY &&
                            Math.sqrt(Math.pow(eventX - lastAbsTouchUpX, 2) + Math.pow(eventY - lastAbsTouchUpY, 2)) <= STYLUS_UP_DEAD_ZONE_RADIUS) {
                        // Enforce a small deadzone between touch up and hover or touch down to allow more precise double-clicking
                        return;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    if (event.getEventTime() - lastAbsTouchDownTime <= STYLUS_DOWN_DEAD_ZONE_DELAY &&
                            Math.sqrt(Math.pow(eventX - lastAbsTouchDownX, 2) + Math.pow(eventY - lastAbsTouchDownY, 2)) <= STYLUS_DOWN_DEAD_ZONE_RADIUS) {
                        // Enforce a small deadzone between touch down and move or touch up to allow more precise double-clicking
                        return;
                    }
                    break;
            }
        }

        // We may get values slightly outside our view region on ACTION_HOVER_ENTER and ACTION_HOVER_EXIT.
        // Normalize these to the view size. We can't just drop them because we won't always get an event
        // right at the boundary of the view, so dropping them would result in our cursor never really
        // reaching the sides of the screen.
        eventX = Math.min(Math.max(eventX, 0), streamView.getWidth());
        eventY = Math.min(Math.max(eventY, 0), streamView.getHeight());

        conn.sendMousePosition((short)eventX, (short)eventY, (short)streamView.getWidth(), (short)streamView.getHeight());
    }

    public void sendHardwareKey(short keyMap, byte keyDirection) {
        if (conn != null) {
            conn.sendKeyboardInput(keyMap, keyDirection, (byte) 0, (byte) 0);
        }
    }

    @Override
    public boolean onGenericMotion(View view, MotionEvent event) {
        return handleMotionEvent(view, event);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // Tell the OS not to buffer input events for us
            //
            // NB: This is still needed even when we call the newer requestUnbufferedDispatch()!
            view.requestUnbufferedDispatch(event);
        }

        // -- SmartDisplay AI Premium: Cursor Adaptativo + Control de Ventanas --
        // Si el PC est├í reportando la posici├│n real de su cursor (espejo activo),
        // NO movemos el cursor con el dedo: lo gobierna el PC (modelo de autoridad).
        // Solo usamos el dedo como respaldo si el companion no env├¡a cursor_pos.
        if (adaptiveCursor != null) {
            boolean mirrorActive = (android.os.SystemClock.elapsedRealtime() - lastCursorPosMs) < 1500;
            if (!mirrorActive) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
                        || action == MotionEvent.ACTION_POINTER_DOWN) {
                    adaptiveCursor.moveTo(event.getX(0), event.getY(0));
                }
            }
        }
        if (windowControlsCtrl != null && windowControlsCtrl.onTouchEvent(event)) {
            return true;
        }

        // ÔöÇÔöÇ Zoom / Pan del stream ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        // Si el StreamViewTransformController consume el evento (pinch, paneo,
        // doble-tap), NO lo pasamos al juego. Solo en escala 1├ù y sin
        // transformaci├│n el evento llega a handleMotionEvent() normalmente.
        if (streamTransformCtrl != null && streamTransformCtrl.onTouchEvent(event)) {
            for (TouchContext aTouchContext : touchContextMap) {
                if (aTouchContext != null) {
                    aTouchContext.cancelTouch();
                }
            }
            return true;
        }

        return handleMotionEvent(view, event);
    }

    @Override
    public void stageStarting(final String stage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (spinner != null) {
                    spinner.setMessage(getResources().getString(R.string.conn_starting) + " " + stage);
                }
            }
        });
    }

    @Override
    public void stageComplete(String stage) {
    }

    private void stopConnection() {
        if (connecting || connected) {
            connecting = connected = false;
            updatePipAutoEnter();

            controllerHandler.stop();

            // Update GameManager state to indicate we're no longer in game
            UiHelper.notifyStreamEnded(this);

            // Stop may take a few hundred ms to do some network I/O to tell
            // the server we're going away and clean up. Let it run in a separate
            // thread to keep things smooth for the UI. Inside moonlight-common,
            // we prevent another thread from starting a connection before and
            // during the process of stopping this one.
            new Thread() {
                public void run() {
                    try {
                        conn.stop();
                    } catch (Exception ignored) {}
                }
            }.start();
        }
    }

    public synchronized void startNewConnectionSession() {
        if (conn != null) {
            final NvConnection oldConn = conn;
            new Thread(() -> {
                try {
                    oldConn.stop();
                } catch (Exception ignored) {}
            }).start();
        }

        if (streamConfig == null || connHost == null) {
            LimeLog.severe("Cannot start connection session: streamConfig or connHost is null");
            return;
        }

        conn = new NvConnection(getApplicationContext(),
                new ComputerDetails.AddressTuple(connHost, connPort),
                connHttpsPort, connUniqueId, streamConfig,
                PlatformBinding.getCryptoProvider(this), connServerCert);

        if (controllerHandler != null) {
            controllerHandler.destroy();
        }
        controllerHandler = new ControllerHandler(this, conn, this, prefConfig);

        if (logicalKeyboard != null) logicalKeyboard.setConnection(conn);
        if (windowControlsCtrl != null) windowControlsCtrl.setConnection(conn);
        if (mouseModeCircle != null) mouseModeCircle.setConnection(conn);
        if (portraitHybridCtrl != null) portraitHybridCtrl.setConnection(conn);

        for (int i = 0; i < touchContextMap.length; i++) {
            if (!prefConfig.touchscreenTrackpad) {
                touchContextMap[i] = new AbsoluteTouchContext(conn, i, streamView);
            } else {
                touchContextMap[i] = new RelativeTouchContext(conn, i,
                        REFERENCE_HORIZ_RES, REFERENCE_VERT_RES,
                        streamView, prefConfig);
            }
            touchContextMap[i].setOnRightClickListener((x, y) -> {
                if (streamTransformCtrl != null) {
                    streamTransformCtrl.zoomToRightClick(x, y);
                }
                if (adaptiveCursor != null) {
                    adaptiveCursor.fireRightClick(x, y);
                }
            });
            touchContextMap[i].setOnLeftClickListener((x, y) -> {
                if (adaptiveCursor != null) {
                    adaptiveCursor.fireLeftClick(x, y);
                }
            });
        }

        connecting = true;
        connected = false;
        displayedFailureDialog = false;
        UiHelper.notifyStreamConnecting(this);

        if (streamView != null && streamView.getHolder().getSurface().isValid()) {
            decoderRenderer.setRenderTarget(streamView.getHolder());
        }
        audioRenderer = new AndroidAudioRenderer(this, prefConfig.enableAudioFx);
        if (voiceManager != null && voiceManager.isRecording()) {
            audioRenderer.setMuted(true);
        }

        conn.start(audioRenderer, decoderRenderer, this);
    }

    @Override
    public void stageFailed(final String stage, final int portFlags, final int errorCode) {
        // ÔöÇÔöÇ SmartDisplay AI: si estamos reconectando, NO mostramos el di├ílogo de
        // error de Moonlight ni hacemos el test de puertos (costoso). Lo tratamos
        // como un intento de reconexi├│n fallido y dejamos que el manager decida.
        if (autoReconnectManager != null && autoReconnectManager.isReconnecting()) {
            LimeLog.warning("AutoReconnect: stageFailed durante reconexi├│n (" + stage + ", code=" + errorCode + ")");
            if (conn != null) {
                new Thread() {
                    public void run() {
                        conn.stop();
                    }
                }.start();
            }
            autoReconnectManager.onReconnectAttemptFailed(errorCode);
            return;
        }
        // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

        // Perform a connection test if the failure could be due to a blocked port
        // This does network I/O, so don't do it on the main thread.
        // Perform a connection test if the failure could be due to a blocked port.
        // Network I/O ÔÇö se ejecuta en el hilo de callback de Moonlight (background), NO en UI thread.
        final int portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443, portFlags);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (spinner != null) {
                    spinner.dismiss();
                    spinner = null;
                }

                if (!displayedFailureDialog) {
                    displayedFailureDialog = true;
                    LimeLog.severe(stage + " failed: " + errorCode);

                    // If video initialization failed and the surface is still valid, display extra information for the user
                    if (stage.contains("video") && streamView.getHolder().getSurface().isValid()) {
                        Toast.makeText(Game.this, getResources().getText(R.string.video_decoder_init_failed), Toast.LENGTH_LONG).show();
                    }

                    String dialogText = getResources().getString(R.string.conn_error_msg) + " " + stage +" (error "+errorCode+")";

                    if (portFlags != 0) {
                        dialogText += "\n\n" + getResources().getString(R.string.check_ports_msg) + "\n" +
                                MoonBridge.stringifyPortFlags(portFlags, "\n");
                    }

                    if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0)  {
                        dialogText += "\n\n" + getResources().getString(R.string.nettest_text_blocked);
                    }

                    Dialog.displayDialog(Game.this, getResources().getString(R.string.conn_error_title), dialogText, true);
                }
            }
        });
    }

    @Override
    public void connectionTerminated(final int errorCode) {
        // ÔöÇÔöÇ SmartDisplay AI: si estamos relanzando por bitrate adaptativo, ÔöÇÔöÇ
        // la terminaci├│n de la conexi├│n vieja es esperada ÔÇö ignorarla.
        if (adaptiveReconnecting) {
            LimeLog.info("AdaptiveBitrate: ignorando connectionTerminated (relanzamiento en curso)");
            return;
        }
        // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

        // ÔöÇÔöÇ SmartDisplay AI: intentar reconexi├│n autom├ítica ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        // Si el manager toma el control, suprimimos el di├ílogo de error de
        // Moonlight para que el usuario no lo vea mientras se reconecta.
        if (autoReconnectManager != null && !displayedFailureDialog) {
            // Paramos el input/controlador pero NO cerramos la actividad
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    controllerHandler.stop();
                    setInputGrabState(false);
                }
            });

            // Resetear estado de conexi├│n interno para permitir reintento
            connecting = false;
            connected  = false;

            // Si YA est├íbamos reconectando y la conexi├│n volvi├│ a caer, esto es
            // un intento de reconexi├│n fallido: contamos el intento (backoff +
            // l├¡mite m├íximo) en lugar de reiniciar el ciclo. Sin di├ílogo de error.
            if (autoReconnectManager.isReconnecting()) {
                if (conn != null) {
                    new Thread() {
                        public void run() {
                            conn.stop();
                        }
                    }.start();
                }
                autoReconnectManager.onReconnectAttemptFailed(errorCode);
                return;
            }

            if (autoReconnectManager.onConnectionTerminated(errorCode)) {
                // SmartDisplay AI FIX: Debemos liberar la conexi├│n de C (conn.stop())
                // para que la reconexi├│n no se bloquee intentando adquirir el sem├íforo.
                if (conn != null) {
                    new Thread() {
                        public void run() {
                            conn.stop();
                        }
                    }.start();
                }
                // El manager gestionar├í la reconexi├│n. No seguimos con el
                // flujo est├índar de Moonlight (di├ílogo de error / finish).
                return;
            }
        }
        // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

        // Perform a connection test if the failure could be due to a blocked port.
        // Network I/O ÔÇö se ejecuta en el hilo de callback de Moonlight (background), NO en UI thread.
        final int portFlags = MoonBridge.getPortFlagsFromTerminationErrorCode(errorCode);
        final int portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER,443, portFlags);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Let the display go to sleep now
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                // Stop processing controller input
                controllerHandler.stop();

                // Ungrab input
                setInputGrabState(false);

                // Salir de modo rendimiento al terminar la sesi├│n
                if (overlayFabController != null) {
                    overlayFabController.exitPerformanceMode();
                }
                if (logicalKeyboard != null) {
                    logicalKeyboard.resumeAnimations();
                }

                if (!displayedFailureDialog) {
                    displayedFailureDialog = true;
                    LimeLog.severe("Connection terminated: " + errorCode);
                    stopConnection();

                    // Display the error dialog if it was an unexpected termination.
                    // Otherwise, just finish the activity immediately.
                    if (errorCode != MoonBridge.ML_ERROR_GRACEFUL_TERMINATION) {
                        String message;

                        if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                            // If we got a blocked result, that supersedes any other error message
                            message = getResources().getString(R.string.nettest_text_blocked);
                        }
                        else {
                            switch (errorCode) {
                                case MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC:
                                    message = getResources().getString(R.string.no_video_received_error);
                                    break;

                                case MoonBridge.ML_ERROR_NO_VIDEO_FRAME:
                                    message = getResources().getString(R.string.no_frame_received_error);
                                    break;

                                case MoonBridge.ML_ERROR_UNEXPECTED_EARLY_TERMINATION:
                                case MoonBridge.ML_ERROR_PROTECTED_CONTENT:
                                    message = getResources().getString(R.string.early_termination_error);
                                    break;

                                case MoonBridge.ML_ERROR_FRAME_CONVERSION:
                                    message = getResources().getString(R.string.frame_conversion_error);
                                    break;

                                default:
                                    String errorCodeString;
                                    // We'll assume large errors are hex values
                                    if (Math.abs(errorCode) > 1000) {
                                        errorCodeString = Integer.toHexString(errorCode);
                                    }
                                    else {
                                        errorCodeString = Integer.toString(errorCode);
                                    }
                                    message = getResources().getString(R.string.conn_terminated_msg) + "\n\n" +
                                            getResources().getString(R.string.error_code_prefix) + " " + errorCodeString;
                                    break;
                            }
                        }

                        if (portFlags != 0) {
                            message += "\n\n" + getResources().getString(R.string.check_ports_msg) + "\n" +
                                    MoonBridge.stringifyPortFlags(portFlags, "\n");
                        }

                        Dialog.displayDialog(Game.this, getResources().getString(R.string.conn_terminated_title),
                                message, true);
                    }
                    else {
                        finish();
                    }
                }
            }
        });
    }

    @Override
    public void connectionStatusUpdate(final int connectionStatus) {
        // ÔöÇÔöÇ SmartDisplay AI: calidad adaptativa mid-stream ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        if (adaptiveQualityEnabled && connected && !autoReconnectManager.isReconnecting()) {
            long now = System.currentTimeMillis();
            if (connectionStatus == MoonBridge.CONN_STATUS_POOR) {
                if (now - adaptiveQualityWindowStart > ADAPTIVE_QUALITY_WINDOW_MS) {
                    adaptiveQualityPoorCount = 0;
                    adaptiveQualityWindowStart = now;
                }
                adaptiveQualityPoorCount++;
                adaptiveQualityLastOkayTime = 0;

                if (adaptiveQualityPoorCount >= ADAPTIVE_QUALITY_POOR_THRESHOLD &&
                        adaptiveQualityLevel < ADAPTIVE_RESOLUTION_SCALE.length - 1) {
                    adaptiveQualityLevel++;
                    adaptiveQualityPoorCount = 0;
                    adaptiveQualityWindowStart = now;
                    int[] res = getAdaptiveResolution(prefConfig.width, prefConfig.height);
                    LimeLog.info("AdaptiveQuality: degradando nivel " + adaptiveQualityLevel
                            + " ÔåÆ " + res[0] + "x" + res[1] + " @ " + getAdaptiveBitrate() + " kbps");
                    triggerAdaptiveReconnect();
                }
            } else if (connectionStatus == MoonBridge.CONN_STATUS_OKAY) {
                adaptiveQualityPoorCount = 0;
                adaptiveQualityWindowStart = now;

                if (adaptiveQualityLevel > 0) {
                    if (adaptiveQualityLastOkayTime == 0) {
                        adaptiveQualityLastOkayTime = now;
                    } else if (now - adaptiveQualityLastOkayTime >= 15_000) {
                        adaptiveQualityLevel--;
                        adaptiveQualityLastOkayTime = 0;
                        int[] res = getAdaptiveResolution(prefConfig.width, prefConfig.height);
                        LimeLog.info("AdaptiveQuality: mejorando nivel " + adaptiveQualityLevel
                                + " ÔåÆ " + res[0] + "x" + res[1] + " @ " + getAdaptiveBitrate() + " kbps");
                        triggerAdaptiveReconnect();
                    }
                }
            }
        }
        // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (prefConfig.disableWarnings) {
                    return;
                }

                if (connectionStatus == MoonBridge.CONN_STATUS_POOR) {
                    // Ignorar silenciosamente las alertas de conexi├│n lenta para no
                    // interrumpir la inmersi├│n del usuario, dejando que el protocolo (FEC)
                    // gestione la estabilidad autom├íticamente sin molestos overlays.
                    requestedNotificationOverlayVisibility = View.GONE;
                }
                else if (connectionStatus == MoonBridge.CONN_STATUS_OKAY) {
                    requestedNotificationOverlayVisibility = View.GONE;
                }

                if (!isHidingOverlays) {
                    notificationOverlayView.setVisibility(requestedNotificationOverlayVisibility);
                }
            }
        });
    }

    /** Retorna resoluci├│n adaptativa para el nivel actual, manteniendo aspect ratio. */
    private int[] getAdaptiveResolution(int originalW, int originalH) {
        int idx = Math.min(adaptiveQualityLevel, ADAPTIVE_RESOLUTION_SCALE.length - 1);
        float scale = ADAPTIVE_RESOLUTION_SCALE[idx];
        int w = Math.max((int)(originalW * scale) & ~1, 640);  // alinear a par, min 640
        int h = Math.max((int)(originalH * scale) & ~1, 360);  // alinear a par, min 360
        // Mantener aspect ratio exacto si el escalado redonde├│ mal
        float targetRatio = (float)originalW / originalH;
        float actualRatio = (float)w / h;
        if (Math.abs(actualRatio - targetRatio) > 0.01f) {
            w = (int)(h * targetRatio) & ~1;
        }
        return new int[]{w, h};
    }

    /** Retorna bitrate adaptativo para el nivel actual. */
    private int getAdaptiveBitrate() {
        int idx = Math.min(adaptiveQualityLevel, ADAPTIVE_BITRATE_STEPS.length - 1);
        return Math.max(
                adaptiveQualityOriginalBitrate * ADAPTIVE_BITRATE_STEPS[idx] / 100,
                5000); // nunca debajo de 5 Mbps
    }

    /**
     * SmartDisplay AI: reinicia la sesi├│n con nueva calidad (bitrate + resoluci├│n).
     * Crea un nuevo NvConnection porque bitrate/resoluci├│n son inmutables.
     */
    private void triggerAdaptiveReconnect() {
        // Capturar par├ímetros de conexi├│n originales (necesitamos crear un nuevo NvConnection)
        final String host = Game.this.getIntent().getStringExtra(EXTRA_HOST);
        final int port = Game.this.getIntent().getIntExtra(EXTRA_PORT, NvHTTP.DEFAULT_HTTP_PORT);
        final int httpsPort = Game.this.getIntent().getIntExtra(EXTRA_HTTPS_PORT, 0);
        final String uniqueId = Game.this.getIntent().getStringExtra(EXTRA_UNIQUEID);
        byte[] derCertData = Game.this.getIntent().getByteArrayExtra(EXTRA_SERVER_CERT);
        X509Certificate serverCert = null;
        try {
            if (derCertData != null) {
                serverCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(derCertData));
            }
        } catch (Exception e) { e.printStackTrace(); }

        final X509Certificate finalServerCert = serverCert;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!connected || conn == null || autoReconnectManager.isReconnecting()) {
                    adaptiveReconnecting = false;
                    return;
                }
                int[] res = getAdaptiveResolution(prefConfig.width, prefConfig.height);
                int bitrate = getAdaptiveBitrate();
                LimeLog.info("AdaptiveQuality: relanzando conexi├│n nivel " + adaptiveQualityLevel
                        + " ÔåÆ " + res[0] + "x" + res[1] + " @ " + bitrate + " kbps");
                // UI overlay
                if (reconnectOverlayView != null) {
                    reconnectOverlayView.setAlpha(0f);
                    reconnectOverlayView.setVisibility(View.VISIBLE);
                    reconnectOverlayView.animate().alpha(0.6f).setDuration(200).start();
                }
                if (reconnectStatusText != null) {
                    String[] labels = {"Calidad m├íxima", "Calidad media", "Calidad baja"};
                    int idx = Math.min(adaptiveQualityLevel, labels.length - 1);
                    reconnectStatusText.setText("Ajustando: " + labels[idx] + " (" + res[0] + "x" + res[1] + ")");
                }
                // Detener input
                controllerHandler.stop();
                setInputGrabState(false);
                connecting = false;
                connected = false;
                // Detener conexi├│n actual y crear nueva con bitrate modificado
                adaptiveReconnecting = true;
                final NvConnection oldConn = conn;
                new Thread() {
                    public void run() {
                        oldConn.stop();
                        // Reconstruir StreamConfiguration con calidad adaptativa (bitrate + resoluci├│n)
                        int effectiveBitrate = getAdaptiveBitrate();
                        int[] adaptiveRes = getAdaptiveResolution(prefConfig.width, prefConfig.height);
                        StreamConfiguration newConfig = new StreamConfiguration.Builder()
                                .setResolution(adaptiveRes[0], adaptiveRes[1])
                                .setLaunchRefreshRate(prefConfig.fps)
                                .setRefreshRate(prefConfig.fps) // mismo fps
                                .setApp(app)
                                .setBitrate(effectiveBitrate)
                                .setEnableSops(prefConfig.enableSops)
                                .enableLocalAudioPlayback(prefConfig.playHostAudio)
                                .setMaxPacketSize(1392)
                                .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
                                .setSupportedVideoFormats(decoderRenderer.getActiveVideoFormat() != 0 ?
                                        decoderRenderer.getActiveVideoFormat() : MoonBridge.VIDEO_FORMAT_H264)
                                .setAttachedGamepadMask(controllerHandler != null ?
                                        ControllerHandler.getAttachedControllerMask(Game.this) : 1)
                                .setClientRefreshRateX100((int)(desiredRefreshRate * 100))
                                .setAudioConfiguration(prefConfig.audioConfiguration)
                                .setColorSpace(decoderRenderer.getPreferredColorSpace())
                                .setColorRange(decoderRenderer.getPreferredColorRange())
                                .setPersistGamepadsAfterDisconnect(!prefConfig.multiController)
                                .build();

                        final NvConnection newConn = new NvConnection(getApplicationContext(),
                                new ComputerDetails.AddressTuple(host, port),
                                httpsPort, uniqueId, newConfig,
                                PlatformBinding.getCryptoProvider(Game.this), finalServerCert);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                adaptiveReconnecting = false;
                                if (streamView != null && streamView.getHolder().getSurface().isValid()) {
                                    conn = newConn;
                                    connecting = true;
                                    decoderRenderer.setRenderTarget(streamView.getHolder());
                                    audioRenderer = new AndroidAudioRenderer(Game.this, prefConfig.enableAudioFx);
                                    if (voiceManager != null && voiceManager.isRecording()) {
                                        audioRenderer.setMuted(true);
                                    }
                                    // Re-enlazar componentes que usan conn
                                    if (logicalKeyboard != null) logicalKeyboard.setConnection(newConn);
                                    if (windowControlsCtrl != null) windowControlsCtrl.setConnection(newConn);
                                    if (mouseModeCircle != null) mouseModeCircle.setConnection(newConn);
                                    if (portraitHybridCtrl != null) portraitHybridCtrl.setConnection(newConn);
                                    // Relanzar controller handler con nueva conexi├│n
                                    controllerHandler = new ControllerHandler(Game.this, newConn, Game.this, prefConfig);
                                    newConn.start(audioRenderer, decoderRenderer, Game.this);
                                    // Toast informativo
                                    String[] labels = {"Calidad m├íxima", "Calidad media", "Calidad baja"};
                                    int idx = Math.min(adaptiveQualityLevel, labels.length - 1);
                                    int[] tres = getAdaptiveResolution(prefConfig.width, prefConfig.height);
                                    Toast.makeText(Game.this,
                                            labels[idx] + ": " + tres[0] + "x" + tres[1] + " @ " + effectiveBitrate + " kbps",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    // Surface inv├ílida ÔÇö no podemos reconectar ahora
                                    adaptiveReconnecting = false;
                                    if (reconnectOverlayView != null) {
                                        reconnectOverlayView.setVisibility(View.GONE);
                                    }
                                }
                            }
                        });
                    }
                }.start();
            }
        });
    }

    @Override
    public void connectionStarted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (spinner != null) {
                    spinner.dismiss();
                    spinner = null;
                }

                connected = true;
                connecting = false;
                updatePipAutoEnter();

                // ÔöÇÔöÇ SmartDisplay AI: conexi├│n exitosa ÔåÆ notificar manager ÔöÇÔöÇÔöÇÔöÇ
                if (autoReconnectManager != null) {
                    autoReconnectManager.onConnectionEstablished();
                }
                // Arrancar el poll del badge de conexi├│n (ping/fps) del modo h├¡brido.
                lastRenderedFrames = 0;
                lastConnStatsMs = 0;
                connStatsHandler.removeCallbacks(connStatsRunnable);
                connStatsHandler.post(connStatsRunnable);
                // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

                // Hide the mouse cursor now after a short delay.
                // Doing it before dismissing the spinner seems to be undone
                // when the spinner gets displayed. On Android Q, even now
                // is too early to capture. We will delay a second to allow
                // the spinner to dismiss before capturing.
                Handler h = new Handler();
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setInputGrabState(true);
                    }
                }, 500);

                // Keep the display on and wake it if asleep (reconnection completed)
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

                // Update GameManager state to indicate we're in game
                UiHelper.notifyStreamConnected(Game.this);

                // ÔöÇÔöÇ Modo rendimiento: detener halo FAB y RGB teclado durante streaming ÔöÇÔöÇ
                if (overlayFabController != null) {
                    overlayFabController.enterPerformanceMode();
                }
                if (logicalKeyboard != null) {
                    logicalKeyboard.pauseAnimations();
                }
                // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

                hideSystemUi(1000);
            }
        });

        // ÔöÇÔöÇ SmartDisplay AI: guardar sesi├│n para recuperaci├│n ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        if (sessionRecoveryManager != null) {
            String host       = Game.this.getIntent().getStringExtra(EXTRA_HOST);
            int    port       = Game.this.getIntent().getIntExtra(EXTRA_PORT, 47989);
            int    httpsPort  = Game.this.getIntent().getIntExtra(EXTRA_HTTPS_PORT, 0);
            int    appId      = Game.this.getIntent().getIntExtra(EXTRA_APP_ID, -1);
            String uniqueId   = Game.this.getIntent().getStringExtra(EXTRA_UNIQUEID);
            String pcUuid     = Game.this.getIntent().getStringExtra(EXTRA_PC_UUID);
            boolean appHdr    = Game.this.getIntent().getBooleanExtra(EXTRA_APP_HDR, false);
            String  macAddr    = Game.this.getIntent().getStringExtra(EXTRA_MAC);
            if (macAddr == null) macAddr = "";
            sessionRecoveryManager.saveSession(
                    host, port, httpsPort, appName, appId,
                    uniqueId, pcUuid, pcName, appHdr, macAddr);
        }
        // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

        // Report this shortcut being used (off the main thread to prevent ANRs)
        ComputerDetails computer = new ComputerDetails();
        computer.name = pcName;
        computer.uuid = Game.this.getIntent().getStringExtra(EXTRA_PC_UUID);
        ShortcutHelper shortcutHelper = new ShortcutHelper(this);
        shortcutHelper.reportComputerShortcutUsed(computer);
        if (appName != null) {
            // This may be null if launched from the "Resume Session" PC context menu item
            shortcutHelper.reportGameLaunched(computer, app);
        }
    }

    @Override
    public void displayMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(Game.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void displayTransientMessage(final String message) {
        if (!prefConfig.disableWarnings) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Game.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) {
        LimeLog.info(String.format((Locale)null, "Rumble on gamepad %d: %04x %04x", controllerNumber, lowFreqMotor, highFreqMotor));

        controllerHandler.handleRumble(controllerNumber, lowFreqMotor, highFreqMotor);
    }

    @Override
    public void rumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger) {
        LimeLog.info(String.format((Locale)null, "Rumble on gamepad triggers %d: %04x %04x", controllerNumber, leftTrigger, rightTrigger));

        controllerHandler.handleRumbleTriggers(controllerNumber, leftTrigger, rightTrigger);
    }

    @Override
    public void setHdrMode(boolean enabled, byte[] hdrMetadata) {
        LimeLog.info("Display HDR mode: " + (enabled ? "enabled" : "disabled"));
        decoderRenderer.setHdrMode(enabled, hdrMetadata);
    }

    @Override
    public void setMotionEventState(short controllerNumber, byte motionType, short reportRateHz) {
        controllerHandler.handleSetMotionEventState(controllerNumber, motionType, reportRateHz);
    }

    @Override
    public void setControllerLED(short controllerNumber, byte r, byte g, byte b) {
        controllerHandler.handleSetControllerLED(controllerNumber, r, g, b);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (!surfaceCreated) {
            throw new IllegalStateException("Surface changed before creation!");
        }

        if (!attemptedConnection) {
            attemptedConnection = true;

            // Update GameManager state to indicate we're "loading" while connecting
            UiHelper.notifyStreamConnecting(Game.this);

            decoderRenderer.setRenderTarget(holder);
            audioRenderer = new AndroidAudioRenderer(Game.this, prefConfig.enableAudioFx);
            conn.start(audioRenderer, decoderRenderer, Game.this);
        } else {
            // ÔöÇÔöÇ SmartDisplay AI: Recuperaci├│n de pantalla suspendida ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
            // Si la Surface fue recreada tras una suspensi├│n de pantalla, el
            // decoder anterior ya fue pausado (prepareForStop en surfaceDestroyed)
            // y no puede reusarse. Detenemos la conexi├│n anterior y lanzamos una
            // nueva sesi├│n sobre la Surface reci├®n creada.
            if (isScreenOffMode && connected) {
                LimeLog.info("surfaceChanged: recuperando de pantalla suspendida — reiniciando pipeline de video limpiamente");
                isScreenOffMode = false;
                startNewConnectionSession();
                LimeLog.info("surfaceChanged: pipeline de video reiniciado tras pantalla suspendida");
            } else {
                // SmartDisplay: Si la superficie fue recreada (ej. por PiP o multiventana),
                // necesitamos actualizar el render target del decodificador.
                decoderRenderer.setRenderTarget(holder);
            }
            // ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        float desiredFrameRate;

        surfaceCreated = true;

        // Android will pick the lowest matching refresh rate for a given frame rate value, so we want
        // to report the true FPS value if refresh rate reduction is enabled. We also report the true
        // FPS value if there's no suitable matching refresh rate. In that case, Android could try to
        // select a lower refresh rate that avoids uneven pull-down (ex: 30 Hz for a 60 FPS stream on
        // a display that maxes out at 50 Hz).
        if (mayReduceRefreshRate() || desiredRefreshRate < prefConfig.fps) {
            desiredFrameRate = prefConfig.fps;
        }
        else {
            // Otherwise, we will pretend that our frame rate matches the refresh rate we picked in
            // prepareDisplayForRendering(). This will usually be the highest refresh rate that our
            // frame rate evenly divides into, which ensures the lowest possible display latency.
            desiredFrameRate = desiredRefreshRate;
        }

        // Tell the OS about our frame rate to allow it to adapt the display refresh rate appropriately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // We want to change frame rate even if it's not seamless, since prepareDisplayForRendering()
            // will not set the display mode on S+ if it only differs by the refresh rate. It depends
            // on us to trigger the frame rate switch here.
            holder.getSurface().setFrameRate(desiredFrameRate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    Surface.CHANGE_FRAME_RATE_ALWAYS);
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            holder.getSurface().setFrameRate(desiredFrameRate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (!surfaceCreated) {
            throw new IllegalStateException("Surface destroyed before creation!");
        }

        if (attemptedConnection) {
            if (connected) {
                boolean inPip = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    inPip = isInPictureInPictureMode();
                }

                // SmartDisplay AI: Si estamos en PiP, NO paramos el decodificador ni la conexi├│n.
                if (!inPip && !isTransitioningToPip) {
                    // SmartDisplay AI: Si la pantalla est├í suspendida, solo pausamos el
                    // decoder (no hay superficie donde renderizar) pero NO matamos la
                    // conexi├│n. Cuando la pantalla se encienda, surfaceCreated() +
                    // onResume() reanudar├ín el pipeline de video normalmente.
                    if (isScreenOffMode) {
                        LimeLog.info("surfaceDestroyed: pantalla suspendida ÔÇö pausando decoder sin matar conexi├│n");
                        decoderRenderer.prepareForStop();
                    } else {
                        decoderRenderer.prepareForStop();
                        stopConnection();
                    }
                } else {
                    LimeLog.info("surfaceDestroyed ignorado por estar en PiP mode o transicionando.");
                }
            } else {
                decoderRenderer.prepareForStop();
            }
        }
    }

    @Override
    public void mouseMove(int deltaX, int deltaY) {
        if (conn != null) {
            conn.sendMouseMove((short) deltaX, (short) deltaY);
        }
    }

    @Override
    public void mouseButtonEvent(int buttonId, boolean down) {
        if (conn == null) {
            return;
        }

        byte buttonIndex;

        switch (buttonId)
        {
        case EvdevListener.BUTTON_LEFT:
            buttonIndex = MouseButtonPacket.BUTTON_LEFT;
            break;
        case EvdevListener.BUTTON_MIDDLE:
            buttonIndex = MouseButtonPacket.BUTTON_MIDDLE;
            break;
        case EvdevListener.BUTTON_RIGHT:
            buttonIndex = MouseButtonPacket.BUTTON_RIGHT;
            break;
        case EvdevListener.BUTTON_X1:
            buttonIndex = MouseButtonPacket.BUTTON_X1;
            break;
        case EvdevListener.BUTTON_X2:
            buttonIndex = MouseButtonPacket.BUTTON_X2;
            break;
        default:
            LimeLog.warning("Unhandled button: "+buttonId);
            return;
        }

        if (down) {
            conn.sendMouseButtonDown(buttonIndex);
        }
        else {
            conn.sendMouseButtonUp(buttonIndex);
        }
    }

    @Override
    public void mouseVScroll(byte amount) {
        if (conn != null) {
            conn.sendMouseScroll(amount);
        }
    }

    @Override
    public void mouseHScroll(byte amount) {
        if (conn != null) {
            conn.sendMouseHScroll(amount);
        }
    }

    @Override
    public void keyboardEvent(boolean buttonDown, short keyCode) {
        if (conn == null) {
            return;
        }

        short keyMap = keyboardTranslator.translate(keyCode, -1);
        if (keyMap != 0) {
            // handleSpecialKeys() takes the Android keycode
            if (handleSpecialKeys(keyCode, buttonDown)) {
                return;
            }

            if (buttonDown) {
                conn.sendKeyboardInput(keyMap, KeyboardPacket.KEY_DOWN, getModifierState(), (byte)0);
            }
            else {
                conn.sendKeyboardInput(keyMap, KeyboardPacket.KEY_UP, getModifierState(), (byte)0);
            }
        }
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        // Don't do anything if we're not connected
        if (!connected) {
            return;
        }

        // This flag is set for all devices
        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            hideSystemUi(2000);
        }
        else if ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
            hideSystemUi(2000);
        }
    }

    @Override
    public void onPerfUpdate(final String text) {
        // Throttle: ignorar actualizaciones si el texto es el mismo o han pasado menos de 1 segundo.
        // Evita setText() en cada frame cuando el overlay de rendimiento est├í visible.
        long now = android.os.SystemClock.elapsedRealtime();
        if (text.equals(lastPerfText) || now - lastPerfUpdateMs < 1000) {
            return;
        }
        lastPerfText   = text;
        lastPerfUpdateMs = now;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                performanceOverlayView.setText(text);
            }
        });
    }

    @Override
    public void onUsbPermissionPromptStarting() {
        // Disable PiP auto-enter while the USB permission prompt is on-screen. This prevents
        // us from entering PiP while the user is interacting with the OS permission dialog.
        suppressPipRefCount++;
        updatePipAutoEnter();
    }

    @Override
    public void onUsbPermissionPromptCompleted() {
        suppressPipRefCount--;
        updatePipAutoEnter();
    }

    // ÔöÇÔöÇ Auto-descubrimiento del host del companion (IP Tailscale del PC) ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

    /**
     * El PC (versi├│n Escritorio) difunde su IP por ADB con la acci├│n
     * START_SCREEN_CAPTURE (extras TAILSCALE_IP/PC_IP), priorizando Tailscale para
     * que funcione entre redes distintas. Persistimos esa IP y los canales del
     * companion la usan autom├íticamente ÔÇö sin configuraci├│n manual.
     */
    private void registerCompanionInfoReceiver() {
        if (companionInfoReceiver != null) return;
        companionInfoReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context ctx, Intent intent) {
                String action = intent.getAction();
                if ("com.limelight.smartdisplay.START_SCREEN_CAPTURE".equals(action)) {
                    String ts = intent.getStringExtra("TAILSCALE_IP");
                    String pc = intent.getStringExtra("PC_IP");
                    String lan = intent.getStringExtra("LAN_IP");
                    String token = intent.getStringExtra("TOKEN");
                    String chosen = isUsableIp(ts) ? ts : (isUsableIp(pc) ? pc : (isUsableIp(lan) ? lan : null));
                    if (chosen != null || token != null) {
                        SharedPreferences.Editor ed = getSharedPreferences(COMPANION_PREFS, MODE_PRIVATE).edit();
                        if (chosen != null) {
                            ed.putString(KEY_COMPANION_HOST, chosen);
                            LimeLog.info("Companion host aprendido del PC: " + chosen);
                        }
                        if (token != null && !token.isEmpty() && !"null".equals(token)) {
                            ed.putString(KEY_COMPANION_TOKEN, token);
                            LimeLog.info("Token de sesi├│n guardado");
                        }
                        ed.apply();
                    }
                    // NOTA: NO lanzar MediaProjection aqu├¡. Hacerlo abr├¡a el di├ílogo de
                    // consentimiento (startActivityForResult) y mandaba el Game a onPause
                    // DURANTE el handshake RTSP de Moonlight ÔåÆ "Fallo RTSP (error 104)".
                    // El broadcast solo aprende IP/token; la captura m├│vilÔåÆPC debe
                    // iniciarla el usuario expl├¡citamente (no de forma autom├ítica).
                } else if ("com.limelight.smartdisplay.STOP_SCREEN_CAPTURE".equals(action)) {
                    LimeLog.info("Deteniendo captura de pantalla por broadcast del PC");
                    stopScreenCaptureService();
                }
            }
        };
        android.content.IntentFilter filter =
                new android.content.IntentFilter("com.limelight.smartdisplay.START_SCREEN_CAPTURE");
        filter.addAction("com.limelight.smartdisplay.STOP_SCREEN_CAPTURE");
        androidx.core.content.ContextCompat.registerReceiver(this, companionInfoReceiver, filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
    }

    /**
     * Solicita al usuario el permiso de captura de pantalla (MediaProjection).
     * Llamado cuando el PC difunde START_SCREEN_CAPTURE.
     */
    @TargetApi(21)
    private void requestScreenCaptureForDesktop() {
        MediaProjectionManager mpMgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mpMgr == null) return;
        try {
            Intent captureIntent = mpMgr.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQ_SCREEN_CAPTURE);
        } catch (Exception e) {
            LimeLog.warning("Error lanzando consentimiento MediaProjection: " + e.getMessage());
        }
    }

    /**
     * Arranca el ScreenCaptureService en primer plano con el resultado del
     * consentimiento MediaProjection y la informaci├│n de conexi├│n al PC.
     */
    private void startScreenCaptureService(int resultCode, Intent data) {
        // Detener servicio previo si existe ÔÇö evita m├║ltiples capturas simult├íneas
        stopScreenCaptureService();

        SharedPreferences prefs = getSharedPreferences(COMPANION_PREFS, MODE_PRIVATE);
        String host = prefs.getString(KEY_COMPANION_HOST, null);
        String token = prefs.getString(KEY_COMPANION_TOKEN, null);
        if (host == null) {
            LimeLog.warning("No se puede iniciar captura: sin IP del PC");
            Toast.makeText(this, "Sin IP del PC ÔÇö recon├®ctate al escritorio", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent svc = new Intent(this, com.limelight.smartdisplay.capture.ScreenCaptureService.class);
            svc.putExtra("code", resultCode);
            svc.putExtra("data", data);
            svc.putExtra("ws_host", host);
            svc.putExtra("token", token);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
            LimeLog.info("ScreenCaptureService iniciado ÔåÆ " + host);
        } catch (Exception e) {
            LimeLog.warning("Error iniciando ScreenCaptureService: " + e.getMessage());
        }
    }

    /** Detiene el ScreenCaptureService si est├í en ejecuci├│n. */
    private void stopScreenCaptureService() {
        try {
            stopService(new Intent(this, com.limelight.smartdisplay.capture.ScreenCaptureService.class));
        } catch (Exception e) {
            LimeLog.warning("Error deteniendo ScreenCaptureService: " + e.getMessage());
        }
    }

    private static boolean isUsableIp(String s) {
        return s != null && !s.isEmpty() && !"null".equals(s);
    }

    // ÔöÇÔöÇ Sincronizaci├│n de portapapeles (M├│vil Ôåö PC) por el bus ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

    private void setupClipboardSync() {
        clipboardManager = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboardManager == null) return;
        clipListener = () -> {
            if (applyingRemoteClip) return;   // evita rebote de lo que aplic├│ el PC
            android.content.ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence cs = clip.getItemAt(0).coerceToText(this);
            if (cs == null) return;
            String text = cs.toString();
            if (text.isEmpty() || text.equals(lastClipText)) return;
            lastClipText = text;
            if (smartDisplayBus != null) {
                try {
                    org.json.JSONObject m = new org.json.JSONObject();
                    m.put("type", "clipboard");
                    m.put("text", text);
                    smartDisplayBus.sendMessage(m);
                } catch (Exception ignored) {}
            }
        };
        clipboardManager.addPrimaryClipChangedListener(clipListener);
    }

    /** Aplica en el portapapeles del m├│vil lo que copi├│ el PC (sin reenviarlo). */
    private void applyRemoteClipboard(String text) {
        if (text == null || text.equals(lastClipText) || clipboardManager == null) return;
        applyingRemoteClip = true;
        try {
            clipboardManager.setPrimaryClip(
                    android.content.ClipData.newPlainText("SmartDisplay", text));
            lastClipText = text;
        } finally {
            applyingRemoteClip = false;
        }
    }

    // ÔöÇÔöÇ Explorador de archivos en overlay (no sale de la proyecci├│n) ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

    /** Abre el explorador propio; pide "Acceso a todos los archivos" una sola vez. */
    private void openFileBrowser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !android.os.Environment.isExternalStorageManager()) {
            Toast.makeText(this,
                    "Concede una vez 'Acceso a todos los archivos' para enviar sin salir de la proyecci├│n",
                    Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(
                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Exception ignored) {}
            }
            return;
        }
        if (fileServer == null) {
            fileServer = new com.limelight.smartdisplay.files.FileTransferServer(
                    this, com.limelight.smartdisplay.files.FileTransferServer.DEFAULT_PORT);
        }
        if (!fileServer.isRunning()) fileServer.start();
        if (fileBrowser != null) fileBrowser.show();
    }

    /** Comparte los archivos elegidos en el explorador y notifica al PC. */
    private void sendPickedFiles(java.util.List<? extends java.io.File> files) {
        if (files == null || files.isEmpty()) return;
        if (fileServer == null) {
            fileServer = new com.limelight.smartdisplay.files.FileTransferServer(
                    this, com.limelight.smartdisplay.files.FileTransferServer.DEFAULT_PORT);
        }
        if (!fileServer.isRunning()) fileServer.start();
        int added = 0;
        for (java.io.File f : files) {
            if (f != null && f.isFile()) {
                fileServer.addShared(new com.limelight.smartdisplay.files.FileTransferServer.SharedItem(
                        android.net.Uri.fromFile(f), f.getName(), f.length()));
                added++;
            }
        }
        notifyPcOfSharedFiles();
        Toast.makeText(this, added + " archivo(s) enviados al PC", Toast.LENGTH_LONG).show();
    }

    // ÔöÇÔöÇ Env├¡o de archivos al PC desde el FAB (sin navegador) ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ

    /** Arranca el servidor (si hace falta) y abre el selector para elegir archivos. */
    private void shareFilesViaFab() {
        try {
            if (fileServer == null) {
                fileServer = new com.limelight.smartdisplay.files.FileTransferServer(
                        this, com.limelight.smartdisplay.files.FileTransferServer.DEFAULT_PORT);
            }
            if (!fileServer.isRunning()) fileServer.start();

            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, REQ_FAB_FILE_SHARE);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir el selector de archivos", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // ÔöÇÔöÇ Screen capture consent (MediaProjection) ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        if (requestCode == REQ_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                LimeLog.info("Permiso MediaProjection concedido ÔÇö iniciando captura");
                startScreenCaptureService(resultCode, data);
            } else {
                LimeLog.warning("Permiso MediaProjection denegado por el usuario");
                Toast.makeText(this, "Captura de pantalla denegada", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // ÔöÇÔöÇ File sharing (existing) ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        if (requestCode != REQ_FAB_FILE_SHARE || resultCode != RESULT_OK || data == null || fileServer == null) {
            return;
        }
        int added = 0;
        if (data.getClipData() != null) {
            int n = data.getClipData().getItemCount();
            for (int k = 0; k < n; k++) {
                if (addSharedUriToFab(data.getClipData().getItemAt(k).getUri())) added++;
            }
        } else if (data.getData() != null) {
            if (addSharedUriToFab(data.getData())) added++;
        }
        notifyPcOfSharedFiles();
        String ip = getWifiIp();
        Toast.makeText(this,
                added > 0
                    ? added + " archivo(s) listos. El PC los recibir├í autom├íticamente"
                        + (ip != null ? " (o http://" + ip + ":" + fileServer.getPort() + ")" : "")
                    : "No se a├▒adi├│ ning├║n archivo",
                Toast.LENGTH_LONG).show();
    }

    private boolean addSharedUriToFab(android.net.Uri uri) {
        if (uri == null) return false;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        String name = "archivo";
        long size = 0;
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                int si = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (ni >= 0) name = c.getString(ni);
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
            }
        } catch (Exception ignored) {}
        fileServer.addShared(new com.limelight.smartdisplay.files.FileTransferServer.SharedItem(uri, name, size));
        return true;
    }

    /** Notifica al PC (v├¡a SmartDisplayBus) las URLs directas para que las descargue solo. */
    private void notifyPcOfSharedFiles() {
        if (smartDisplayBus == null || fileServer == null) return;
        String ip = getWifiIp();
        if (ip == null) return;
        try {
            String base = "http://" + ip + ":" + fileServer.getPort();
            org.json.JSONObject msg = new org.json.JSONObject();
            msg.put("type", "files_offer");
            msg.put("url", base);
            org.json.JSONArray arr = new org.json.JSONArray();
            java.util.List<com.limelight.smartdisplay.files.FileTransferServer.SharedItem> shared = fileServer.getShared();
            for (int idx = 0; idx < shared.size(); idx++) {
                com.limelight.smartdisplay.files.FileTransferServer.SharedItem it = shared.get(idx);
                org.json.JSONObject f = new org.json.JSONObject();
                f.put("name", it.name);
                f.put("size", it.size);
                f.put("url", base + "/dl?i=" + idx);
                arr.put(f);
            }
            msg.put("files", arr);
            smartDisplayBus.sendMessage(msg);
        } catch (Exception ignored) {}
    }

    private String getWifiIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                java.net.NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // Alterna el HUD de rendimiento. Comparte prefConfig con el decoder, as├¡ que
    // activar enablePerfOverlay reanuda el flujo de m├®tricas (onPerfUpdate) sin
    // tocar el core de streaming.
    private void togglePerformanceHud() {
        if (performanceOverlayView == null || prefConfig == null) return;
        boolean show = performanceOverlayView.getVisibility() != View.VISIBLE;
        prefConfig.enablePerfOverlay = show;
        float dy = 16f * getResources().getDisplayMetrics().density;
        if (show) {
            performanceOverlayView.setText("Midiendo rendimientoÔÇª");
            performanceOverlayView.setAlpha(0f);
            performanceOverlayView.setTranslationY(-dy);
            performanceOverlayView.setVisibility(View.VISIBLE);
            performanceOverlayView.animate().alpha(1f).translationY(0f).setDuration(260)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f))
                    .start();
        } else {
            performanceOverlayView.animate().alpha(0f).translationY(-dy).setDuration(180)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.3f, 0f, 0.8f, 0.15f))
                    .withEndAction(() -> performanceOverlayView.setVisibility(View.GONE))
                    .start();
        }
        Toast.makeText(this,
                show ? "HUD de rendimiento: activado" : "HUD de rendimiento: desactivado",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
        switch (keyEvent.getAction()) {
            case KeyEvent.ACTION_DOWN:
                return handleKeyDown(keyEvent);
            case KeyEvent.ACTION_UP:
                return handleKeyUp(keyEvent);
            case KeyEvent.ACTION_MULTIPLE:
                return handleKeyMultiple(keyEvent);
            default:
                return false;
        }
    }

    public StreamViewTransformController getStreamTransformCtrl() {
        return streamTransformCtrl;
    }

    private void updateLayoutForOrientation(int orientation) {
        // Desactivado el reparentado din├ímico de streamWrapper para evitar la destrucci├│n 
        // de la Surface de OpenGL de video. Esto elimina cuellos de botella de renderizado 
        // y previene de ra├¡z la p├®rdida de imagen (pantalla negra) tras rotaciones.
        
        if (portraitHybridLayout != null) {
            portraitHybridLayout.setVisibility(View.GONE); // No usar el layout h├¡brido ya que fue simplificado
        }
        
        android.view.View overlayContainer = findViewById(R.id.overlayContainer);
        android.view.View windowControls = findViewById(R.id.overlayWindowControls);

        if (overlayContainer != null) {
            overlayContainer.setVisibility(View.VISIBLE);
        }

        // Paridad V/H: los controles de ventana (Min/Max/Cerrar) est├ín disponibles
        // en ambas orientaciones; la c├ípsula es flotante, arrastrable y con auto-hide,
        // as├¡ que no estorba en vertical.
        if (windowControls != null && !isHidingOverlays) windowControls.setVisibility(View.VISIBLE);

        // ÔöÇÔöÇ FAB: reclampar posici├│n tras rotaci├│n ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
        // Al rotar de portrait (alto ~2400px) a landscape (alto ~1080px),
        // la Y guardada del FAB puede quedar fuera de pantalla y desaparecer.
        // onOrientationChanged() reclampa la posici├│n, guarda la corregida
        // e invalida la cach├® de geometr├¡a radial.
        if (overlayFabController != null) {
            overlayFabController.onOrientationChanged();
        }

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (virtualController != null) virtualController.hide();
        } else {
            if (virtualController != null && !isHidingOverlays) virtualController.show();
        }
    }
}
