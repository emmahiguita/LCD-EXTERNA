package com.limelight.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

/**
 * SmartDisplayOverlay
 * ─────────────────────────────────────────────────────────────────────────────
 * Single-responsibility class that manages all floating UI elements on top of
 * the stream SurfaceView.  It does NOT touch the video path at all.
 *
 * Architecture:
 *   • Inflates overlay_launcher.xml into the root FrameLayout.
 *   • Inflates overlay_keyboard.xml into the same root FrameLayout.
 *   • The launcher FAB is draggable — position persisted in SharedPreferences.
 *   • Keyboard tabs (NORMAL / DEV) switch visible sub-layouts.
 *   • Hand mode: diverts touch events to Pan mode (Game.java handles the delta).
 *   • Compact mode: collapses keyboard to a single bar.
 *   • Rotation: restores FAB position from prefs on every onConfigurationChanged.
 *
 * Usage from Game.java:
 *   overlay = new SmartDisplayOverlay(this, rootFrameLayout, conn);
 *   // In onConfigurationChanged → overlay.onConfigurationChanged(newConfig);
 *   // In onDestroy → overlay.destroy();
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class SmartDisplayOverlay {

    // ── GFE Keycode builder (matches KeyboardTranslator format) ────────────
    private static short gfk(int vk) { return (short) ((0x80 << 8) | vk); }

    // Windows VK codes (subset needed for the overlay)
    private static final short VK_BACK     = gfk(0x08);
    private static final short VK_TAB      = gfk(0x09);
    private static final short VK_RETURN   = gfk(0x0D);
    private static final short VK_ESCAPE   = gfk(0x1B);
    private static final short VK_SPACE    = gfk(0x20);
    private static final short VK_HOME     = gfk(0x24);
    private static final short VK_END      = gfk(0x23);
    private static final short VK_DELETE   = gfk(0x2E);
    private static final short VK_LWIN     = gfk(0x5B);
    // Letters A-Z: VK = ASCII code (uppercase)
    private static final short VK_C        = gfk(0x43);
    private static final short VK_F_KEY    = gfk(0x46);
    private static final short VK_H        = gfk(0x48);
    private static final short VK_K        = gfk(0x4B);
    private static final short VK_P        = gfk(0x50);
    private static final short VK_S        = gfk(0x53);
    private static final short VK_V        = gfk(0x56);
    private static final short VK_X        = gfk(0x58);
    private static final short VK_Y        = gfk(0x59);
    private static final short VK_Z        = gfk(0x5A);
    private static final short VK_BACK_QUOTE = gfk(0xC0);
    // Function keys
    private static final short VK_F1       = gfk(0x70);
    private static final short VK_F2       = gfk(0x71);
    private static final short VK_F3       = gfk(0x72);
    private static final short VK_F4       = gfk(0x73);
    private static final short VK_F5       = gfk(0x74);
    private static final short VK_F6       = gfk(0x75);
    private static final short VK_F7       = gfk(0x76);
    private static final short VK_F8       = gfk(0x77);
    private static final short VK_F9       = gfk(0x78);
    private static final short VK_F10      = gfk(0x79);
    private static final short VK_F11      = gfk(0x7A);
    private static final short VK_F12      = gfk(0x7B);

    // ── Prefs keys ──────────────────────────────────────────────────────────
    private static final String PREFS_NAME   = "SmartDisplayOverlayPrefs";
    private static final String PREF_FAB_X   = "fab_x_ratio";
    private static final String PREF_FAB_Y   = "fab_y_ratio";
    private static final String PREF_KB_TAB  = "kb_tab";
    private static final String PREF_COMPACT = "kb_compact";
    private static final String PREF_KB_SCALE = "kb_scale";
    private static final String PREF_KB_ONE_HANDED = "kb_one_handed";

    // ── State ───────────────────────────────────────────────────────────────
    private final Context          context;
    private final FrameLayout      rootLayout;
    private final NvConnection     conn;
    private final SharedPreferences prefs;

    // Overlay views (launcher)
    private View        overlayLauncherRoot;
    private FrameLayout launcherAnchor;
    private ViewGroup   radialMenu;
    private LinearLayout monitorPanel;
    private LinearLayout monitorChipsRow;
    private TextView    menuHandBadge;
    private TextView    menuZoomBadge;
    private TextView    menuOrientBadge;

    // Overlay views (keyboard)
    private View        overlayKeyboardRoot;
    private LinearLayout normalKeyboard;
    private LinearLayout devKeyboard;
    private LinearLayout compactBar;
    private TextView    tabNormal;
    private TextView    tabDev;

    // Modifier toggle state (sticky keys)
    private boolean ctrlDown  = false;
    private boolean altDown   = false;
    private boolean shiftDown = false;

    // Mode flags
    private boolean handModeEnabled = false;
    private boolean radialMenuVisible = false;
    private boolean keyboardVisible   = false;
    private boolean compactMode;
    private int     activeTab;       // 0 = NORMAL, 1 = DEV
    private int     orientationMode; // 0 = AUTO, 1 = VERTICAL, 2 = HORIZONTAL
    private int     selectedMonitor; // index of the highlighted monitor chip
    private static final int MONITOR_COUNT = 3;

    // FAB drag
    private float fabTouchOffsetX, fabTouchOffsetY;
    private boolean fabDragging = false;
    private long    fabDownTime = 0;
    private static final long TAP_THRESHOLD_MS = 200;
    private long lastFabTapTime = 0;
    private boolean isFabHiddenTemporarily = false;
    private boolean fabLongPressed = false;
    private float fabDownRawX = 0f;
    private float fabDownRawY = 0f;
    private final Runnable fabLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            fabLongPressed = true;
            if (launcherAnchor != null) {
                launcherAnchor.animate().scaleX(1.15f).scaleY(1.15f).alpha(1.0f).setDuration(150).start();
            }
        }
    };


    // Zoom state (reported back from Game.java ScaleGestureDetector)
    private float currentZoom = 1.0f;

    private float keyboardScale = 1.0f;
    private int oneHandedState = 0; // 0 = Center, 1 = Left-hand, 2 = Right-hand

    // Keyboard drag state
    private float kbTouchOffsetX, kbTouchOffsetY;
    private boolean kbDragging = false;
    private ScaleGestureDetector scaleGestureDetector;

    // Callbacks into Game.java for actions the overlay cannot perform by itself.
    public interface Listener {
        void onPanModeChanged(boolean enabled);  // MOVER (free viewport) on/off
        void onZoomReset();                       // ZOOM → 1:1
        void onOrientationModeChanged(int mode);  // 0 = AUTO, 1 = VERTICAL, 2 = HORIZONTAL
        void onMonitorSelected(int index);        // a monitor chip was tapped
    }
    private Listener listener;

    // ── Constructor ─────────────────────────────────────────────────────────
    public SmartDisplayOverlay(Context context, FrameLayout rootLayout, NvConnection conn) {
        this.context    = context;
        this.rootLayout = rootLayout;
        this.conn       = conn;
        this.prefs      = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        this.compactMode     = prefs.getBoolean(PREF_COMPACT, false);
        this.activeTab       = prefs.getInt(PREF_KB_TAB, 0);
        this.orientationMode = prefs.getInt(PREF_ORIENT, 0);
        this.selectedMonitor = 0;
        this.keyboardScale   = prefs.getFloat(PREF_KB_SCALE, 1.0f);
        this.oneHandedState  = prefs.getInt(PREF_KB_ONE_HANDED, 0);

        inflateLauncher();
        inflateKeyboard();
    }

    public void setListener(Listener l) { this.listener = l; }

    // ══════════════════════════════════════════════════════════════════════════
    // INFLATE & WIRE LAUNCHER
    // ══════════════════════════════════════════════════════════════════════════
    private void inflateLauncher() {
        overlayLauncherRoot = LayoutInflater.from(context)
                .inflate(R.layout.overlay_launcher, rootLayout, false);
        rootLayout.addView(overlayLauncherRoot);

        launcherAnchor  = overlayLauncherRoot.findViewById(R.id.launcherAnchor);
        radialMenu      = overlayLauncherRoot.findViewById(R.id.radialMenu);
        monitorPanel    = overlayLauncherRoot.findViewById(R.id.monitorPanel);
        monitorChipsRow = overlayLauncherRoot.findViewById(R.id.monitorChipsRow);
        menuHandBadge   = overlayLauncherRoot.findViewById(R.id.menuHandBadge);
        menuZoomBadge   = overlayLauncherRoot.findViewById(R.id.menuZoomBadge);
        menuOrientBadge = overlayLauncherRoot.findViewById(R.id.menuOrientBadge);

        // Set initial reposo opacity to 35%
        if (launcherAnchor != null) {
            launcherAnchor.setAlpha(0.35f);
        }

        // FAB drag + tap
        launcherAnchor.setOnTouchListener(this::onFabTouch);

        // Radial menu items
        overlayLauncherRoot.findViewById(R.id.menuItemHand)
                .setOnClickListener(v -> toggleHandMode());
        overlayLauncherRoot.findViewById(R.id.menuItemKeyboard)
                .setOnClickListener(v -> { hideRadialMenu(); toggleKeyboard(); });
        overlayLauncherRoot.findViewById(R.id.menuItemMonitors)
                .setOnClickListener(v -> { hideRadialMenu(); toggleMonitorPanel(); });
        overlayLauncherRoot.findViewById(R.id.menuItemConfig)
                .setOnClickListener(v -> hideRadialMenu()); // sin pantalla de ajustes aún
        overlayLauncherRoot.findViewById(R.id.menuItemClose)
                .setOnClickListener(v -> hideRadialMenu());

        // Monitor panel close
        overlayLauncherRoot.findViewById(R.id.btnCloseMonitors)
                .setOnClickListener(v -> monitorPanel.setVisibility(View.GONE));

        // Build the monitor chips
        populateMonitors();

        // Restore FAB position after layout
        overlayLauncherRoot.post(this::restoreFabPosition);
    }


    // ══════════════════════════════════════════════════════════════════════════
    // INFLATE & WIRE KEYBOARD
    // ══════════════════════════════════════════════════════════════════════════
    private void inflateKeyboard() {
        overlayKeyboardRoot = LayoutInflater.from(context)
                .inflate(R.layout.overlay_keyboard, rootLayout, false);
        rootLayout.addView(overlayKeyboardRoot);

        // Update pivots on layout change to ensure correct bottom-center scaling
        overlayKeyboardRoot.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            v.setPivotX((right - left) / 2.0f);
            v.setPivotY(bottom - top);
        });

        normalKeyboard = overlayKeyboardRoot.findViewById(R.id.normalKeyboard);
        devKeyboard    = overlayKeyboardRoot.findViewById(R.id.devKeyboard);
        compactBar     = overlayKeyboardRoot.findViewById(R.id.compactBar);
        tabNormal      = overlayKeyboardRoot.findViewById(R.id.tabNormal);
        tabDev         = overlayKeyboardRoot.findViewById(R.id.tabDev);

        // Start hidden
        overlayKeyboardRoot.setVisibility(View.GONE);

        // Tab switching
        tabNormal.setOnClickListener(v -> switchTab(0));
        tabDev.setOnClickListener(v -> switchTab(1));

        // Compact toggle
        overlayKeyboardRoot.findViewById(R.id.btnKbCompact)
                .setOnClickListener(v -> toggleCompact());

        // Close
        overlayKeyboardRoot.findViewById(R.id.btnKbClose)
                .setOnClickListener(v -> hideKeyboard());

        // Apply initial state
        switchTab(activeTab);
        if (compactMode) applyCompactMode(true);

        // Wire all keys (NORMAL tab)
        wireNormalKeys();

        // Wire all keys (DEV tab)
        wireDevKeys();

        // Wire compact bar
        wireCompactBar();

        // Keyboard scale adjusters
        View btnScaleDown = overlayKeyboardRoot.findViewById(R.id.btnKbScaleDown);
        if (btnScaleDown != null) {
            btnScaleDown.setOnClickListener(v -> {
                oneHandedState = 0; // Reset one-handed mode on manual scaling
                keyboardScale = Math.max(0.4f, keyboardScale - 0.1f);
                applyOneHandedMode();
            });
        }
        View btnScaleUp = overlayKeyboardRoot.findViewById(R.id.btnKbScaleUp);
        if (btnScaleUp != null) {
            btnScaleUp.setOnClickListener(v -> {
                oneHandedState = 0; // Reset one-handed mode on manual scaling
                keyboardScale = Math.min(1.2f, keyboardScale + 0.1f);
                applyOneHandedMode();
            });
        }

        // One-handed mode toggle
        View btnOneHanded = overlayKeyboardRoot.findViewById(R.id.btnKbOneHanded);
        if (btnOneHanded != null) {
            btnOneHanded.setOnClickListener(v -> toggleOneHandedMode());
        }

        // Keyboard top bar drag touch listener
        View topBar = overlayKeyboardRoot.findViewById(R.id.keyboardTopBar);
        if (topBar != null) {
            topBar.setOnTouchListener(this::onKeyboardTouch);
        }

        // Initialize two-finger pinch-to-zoom detector for keyboard scaling
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                oneHandedState = 0; // Reset one-handed mode on manual scaling
                keyboardScale *= detector.getScaleFactor();
                keyboardScale = Math.max(0.4f, Math.min(1.2f, keyboardScale));
                applyOneHandedMode();
                return true;
            }
        });

        // Apply scale and position at startup
        applyOneHandedMode();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FAB DRAG LOGIC
    // ══════════════════════════════════════════════════════════════════════════
    private boolean onFabTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fabTouchOffsetX = e.getRawX() - launcherAnchor.getX();
                fabTouchOffsetY = e.getRawY() - launcherAnchor.getY();
                fabDownTime = System.currentTimeMillis();
                fabDragging = false;
                fabLongPressed = false;
                fabDownRawX = e.getRawX();
                fabDownRawY = e.getRawY();
                launcherAnchor.postDelayed(fabLongPressRunnable, 500);
                launcherAnchor.setAlpha(1.0f); // Restores 100% opacity on interaction
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!fabLongPressed) {
                    float dist = (float) Math.hypot(e.getRawX() - fabDownRawX, e.getRawY() - fabDownRawY);
                    if (dist > 15) {
                        launcherAnchor.removeCallbacks(fabLongPressRunnable);
                    }
                }
                if (fabLongPressed) {
                    fabDragging = true;
                    hideRadialMenu();
                    float newX = e.getRawX() - fabTouchOffsetX;
                    float newY = e.getRawY() - fabTouchOffsetY;
                    // Clamp within root bounds
                    newX = Math.max(0, Math.min(newX, rootLayout.getWidth()  - launcherAnchor.getWidth()));
                    newY = Math.max(0, Math.min(newY, rootLayout.getHeight() - launcherAnchor.getHeight()));
                    launcherAnchor.setX(newX);
                    launcherAnchor.setY(newY);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                launcherAnchor.removeCallbacks(fabLongPressRunnable);
                launcherAnchor.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                if (fabDragging) {
                    float currentX = launcherAnchor.getX();
                    float screenWidth = rootLayout.getWidth();
                    float fabWidth = launcherAnchor.getWidth();
                    float leftDist = currentX;
                    float rightDist = screenWidth - (currentX + fabWidth);
                    float targetX = (leftDist < rightDist) ? 0 : (screenWidth - fabWidth);
                    launcherAnchor.animate()
                            .x(targetX)
                            .alpha(0.35f)
                            .setDuration(250)
                            .withEndAction(this::saveFabPosition)
                            .start();
                } else if ((System.currentTimeMillis() - fabDownTime) < TAP_THRESHOLD_MS) {
                    long now = System.currentTimeMillis();
                    if (now - lastFabTapTime < 300) { // Double tap
                        isFabHiddenTemporarily = true;
                        launcherAnchor.animate().alpha(0.02f).setDuration(200).start();
                    } else { // Single tap
                        if (isFabHiddenTemporarily) {
                            isFabHiddenTemporarily = false;
                            launcherAnchor.animate().alpha(0.35f).setDuration(200).start();
                        } else {
                            toggleRadialMenu();
                        }
                    }
                    lastFabTapTime = now;
                } else {
                    if (!radialMenuVisible && !isFabHiddenTemporarily) {
                        launcherAnchor.animate().alpha(0.35f).setDuration(200).start(); // Fade back to 35%
                    }
                }
                fabDragging = false;
                fabLongPressed = false;
                return true;
        }
        return false;
    }


    private void saveFabPosition() {
        float xRatio = launcherAnchor.getX() / Math.max(1, rootLayout.getWidth()  - launcherAnchor.getWidth());
        float yRatio = launcherAnchor.getY() / Math.max(1, rootLayout.getHeight() - launcherAnchor.getHeight());
        prefs.edit().putFloat(PREF_FAB_X, xRatio).putFloat(PREF_FAB_Y, yRatio).apply();
    }

    private void restoreFabPosition() {
        float xRatio = prefs.getFloat(PREF_FAB_X, 0.85f);
        float yRatio = prefs.getFloat(PREF_FAB_Y, 0.5f);
        float maxX = rootLayout.getWidth()  - launcherAnchor.getWidth();
        float maxY = rootLayout.getHeight() - launcherAnchor.getHeight();
        launcherAnchor.setX(Math.max(0, Math.min(xRatio * maxX, maxX)));
        launcherAnchor.setY(Math.max(0, Math.min(yRatio * maxY, maxY)));
        positionRadialMenu();
    }

    private void positionRadialMenu() {
        overlayLauncherRoot.post(() -> {
            float fabX = launcherAnchor.getX();
            float fabY = launcherAnchor.getY();
            float fabW = launcherAnchor.getWidth();
            float fabH = launcherAnchor.getHeight();
            float menuW = radialMenu.getWidth() > 0 ? radialMenu.getWidth() : 240 * context.getResources().getDisplayMetrics().density;
            float menuH = radialMenu.getHeight() > 0 ? radialMenu.getHeight() : 240 * context.getResources().getDisplayMetrics().density;

            // Center the radial menu on the FAB center
            float x = fabX + (fabW / 2.0f) - (menuW / 2.0f);
            float y = fabY + (fabH / 2.0f) - (menuH / 2.0f);

            // Clamp within root bounds
            x = Math.max(0, Math.min(x, rootLayout.getWidth() - menuW));
            y = Math.max(0, Math.min(y, rootLayout.getHeight() - menuH));

            radialMenu.setX(x);
            radialMenu.setY(y);
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RADIAL MENU
    // ══════════════════════════════════════════════════════════════════════════
    private void toggleRadialMenu() {
        if (radialMenuVisible) {
            hideRadialMenu();
        } else {
            positionRadialMenu();
            
            // Fade out launcher anchor and set GONE to avoid blocking touches
            launcherAnchor.animate().alpha(0f).setDuration(120).withEndAction(() -> launcherAnchor.setVisibility(View.GONE)).start();

            radialMenu.setVisibility(View.VISIBLE);
            radialMenu.setAlpha(0f);
            radialMenu.setScaleX(0.85f);
            radialMenu.setScaleY(0.85f);
            radialMenu.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
            radialMenuVisible = true;
        }
    }

    private void hideRadialMenu() {
        if (!radialMenuVisible) return;
        
        radialMenu.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(120)
                .withEndAction(() -> radialMenu.setVisibility(View.GONE))
                .start();

        // Restore launcher anchor to reposo (35% opacity)
        launcherAnchor.setVisibility(View.VISIBLE);
        launcherAnchor.setAlpha(0f);
        launcherAnchor.animate().alpha(0.35f).setDuration(180).start();
        
        radialMenuVisible = false;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HAND MODE
    // ══════════════════════════════════════════════════════════════════════════
    private void toggleHandMode() {
        handModeEnabled = !handModeEnabled;
        if (handModeEnabled) {
            hideKeyboard();
            if (monitorPanel != null) {
                monitorPanel.setVisibility(View.GONE);
            }
        }
        
        // Dynamic background tint highlight for active state
        View handButton = overlayLauncherRoot.findViewById(R.id.menuItemHand);
        if (handButton != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                handButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        handModeEnabled ? 0x80BB86FC : 0x20FFFFFF));
            } else {
                handButton.setBackgroundColor(handModeEnabled ? 0x80BB86FC : 0x20FFFFFF);
            }
        }

        if (menuHandBadge != null) {
            menuHandBadge.setText(handModeEnabled ? "ON" : "OFF");
            menuHandBadge.setTextColor(handModeEnabled ? 0xFF03DAC6 : 0xFF777777);
        }
        if (listener != null) listener.onPanModeChanged(handModeEnabled);
        hideRadialMenu();
    }


    public boolean isHandModeEnabled() { return handModeEnabled; }

    // ══════════════════════════════════════════════════════════════════════════
    // KEYBOARD VISIBILITY
    // ══════════════════════════════════════════════════════════════════════════
    private void toggleKeyboard() {
        if (keyboardVisible) hideKeyboard(); else showKeyboard();
    }

    private void showKeyboard() {
        if (handModeEnabled) {
            android.widget.Toast.makeText(context, "Desactiva el modo movimiento para abrir el teclado", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        overlayKeyboardRoot.setVisibility(View.VISIBLE);
        overlayKeyboardRoot.setAlpha(0f);
        overlayKeyboardRoot.animate().alpha(1f).setDuration(160).start();
        keyboardVisible = true;
    }

    private void hideKeyboard() {
        overlayKeyboardRoot.animate().alpha(0f).setDuration(120)
                .withEndAction(() -> overlayKeyboardRoot.setVisibility(View.GONE))
                .start();
        keyboardVisible = false;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TAB SWITCHING
    // ══════════════════════════════════════════════════════════════════════════
    private void switchTab(int tab) {
        activeTab = tab;
        prefs.edit().putInt(PREF_KB_TAB, tab).apply();

        if (tab == 0) {
            normalKeyboard.setVisibility(compactMode ? View.GONE : View.VISIBLE);
            devKeyboard.setVisibility(View.GONE);
            tabNormal.setTextColor(0xFFBB86FC);
            tabNormal.setBackgroundResource(R.drawable.bg_tab_active);
            tabDev.setTextColor(0xFF888888);
            tabDev.setBackgroundResource(R.drawable.bg_tab_inactive);
        } else {
            normalKeyboard.setVisibility(View.GONE);
            devKeyboard.setVisibility(compactMode ? View.GONE : View.VISIBLE);
            tabDev.setTextColor(0xFFBB86FC);
            tabDev.setBackgroundResource(R.drawable.bg_tab_active);
            tabNormal.setTextColor(0xFF888888);
            tabNormal.setBackgroundResource(R.drawable.bg_tab_inactive);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMPACT MODE
    // ══════════════════════════════════════════════════════════════════════════
    private void toggleCompact() {
        compactMode = !compactMode;
        prefs.edit().putBoolean(PREF_COMPACT, compactMode).apply();
        applyCompactMode(compactMode);
    }

    private void applyCompactMode(boolean compact) {
        compactBar.setVisibility(compact ? View.VISIBLE : View.GONE);
        if (compact) {
            normalKeyboard.setVisibility(View.GONE);
            devKeyboard.setVisibility(View.GONE);
        } else {
            switchTab(activeTab);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MONITOR PANEL
    // ══════════════════════════════════════════════════════════════════════════
    private void toggleMonitorPanel() {
        if (handModeEnabled) {
            android.widget.Toast.makeText(context, "Desactiva el modo movimiento para abrir el panel de monitores", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (monitorPanel.getVisibility() == View.VISIBLE) {
            monitorPanel.setVisibility(View.GONE);
        } else {
            monitorPanel.setVisibility(View.VISIBLE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ZOOM
    // ══════════════════════════════════════════════════════════════════════════
    public void updateZoom(float zoom) {
        currentZoom = zoom;
        menuZoomBadge.setText(Math.round(zoom * 100) + "%");
    }

    private void resetZoom() {
        updateZoom(1.0f);
        if (listener != null) listener.onZoomReset();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ORIENTATION  (AUTO → VERTICAL → HORIZONTAL → AUTO)
    // ══════════════════════════════════════════════════════════════════════════
    private static final String PREF_ORIENT = "orientation_mode";

    private void cycleOrientation() {
        orientationMode = (orientationMode + 1) % 3;
        prefs.edit().putInt(PREF_ORIENT, orientationMode).apply();
        updateOrientationBadge();
        if (listener != null) listener.onOrientationModeChanged(orientationMode);
    }

    private void updateOrientationBadge() {
        if (menuOrientBadge == null) return;
        switch (orientationMode) {
            case 1:  menuOrientBadge.setText("VERT"); break;
            case 2:  menuOrientBadge.setText("HORIZ"); break;
            default: menuOrientBadge.setText("AUTO"); break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MONITOR CHIPS
    // ══════════════════════════════════════════════════════════════════════════
    private void populateMonitors() {
        if (monitorChipsRow == null) return;
        monitorChipsRow.removeAllViews();
        for (int i = 0; i < MONITOR_COUNT; i++) {
            final int index = i;
            TextView chip = new TextView(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(6, 0, 6, 0);
            chip.setLayoutParams(lp);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setText("🖥 " + (i + 1));
            chip.setTextSize(13f);
            chip.setOnClickListener(v -> selectMonitor(index));
            monitorChipsRow.addView(chip);
        }
        selectMonitor(selectedMonitor, false);
    }

    private void selectMonitor(int index) {
        selectMonitor(index, true);
    }

    private void selectMonitor(int index, boolean notify) {
        if (monitorChipsRow == null || index < 0 || index >= monitorChipsRow.getChildCount()) return;
        selectedMonitor = index;
        for (int i = 0; i < monitorChipsRow.getChildCount(); i++) {
            TextView chip = (TextView) monitorChipsRow.getChildAt(i);
            boolean sel = (i == index);
            chip.setBackgroundColor(sel ? 0xFF3A2F5A : 0x22FFFFFF);
            chip.setTextColor(sel ? 0xFFBB86FC : 0xFFCCCCCC);
        }
        if (notify && listener != null) listener.onMonitorSelected(index);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // KEY INJECTION HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    private void sendKey(short keyCode) {
        if (conn == null) return;
        conn.sendKeyboardInput(keyCode, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
        conn.sendKeyboardInput(keyCode, KeyboardPacket.KEY_UP,   (byte) 0, (byte) 0);
    }

    private void sendKeyWithMods(short keyCode, byte modifiers) {
        if (conn == null) return;
        conn.sendKeyboardInput(keyCode, KeyboardPacket.KEY_DOWN, modifiers, (byte) 0);
        conn.sendKeyboardInput(keyCode, KeyboardPacket.KEY_UP,   modifiers, (byte) 0);
    }

    /** Builds active modifier byte from toggle state. */
    private byte activeMods() {
        byte mods = 0;
        if (ctrlDown)  mods |= KeyboardPacket.MODIFIER_CTRL;
        if (altDown)   mods |= KeyboardPacket.MODIFIER_ALT;
        if (shiftDown) mods |= KeyboardPacket.MODIFIER_SHIFT;
        return mods;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WIRE NORMAL KEYBOARD KEYS
    // ══════════════════════════════════════════════════════════════════════════
    private void wireNormalKeys() {
        // Letters A-Z: GFE VK = 0x41 + offset (uppercase ASCII)
        // Digits 0-9: GFE VK = 0x30 + offset
        int[][] normalMap = {
            {R.id.kb_1, 0x31}, {R.id.kb_2, 0x32}, {R.id.kb_3, 0x33}, {R.id.kb_4, 0x34},
            {R.id.kb_5, 0x35}, {R.id.kb_6, 0x36}, {R.id.kb_7, 0x37}, {R.id.kb_8, 0x38},
            {R.id.kb_9, 0x39}, {R.id.kb_0, 0x30},
            {R.id.kb_q, 0x51}, {R.id.kb_w, 0x57}, {R.id.kb_e, 0x45}, {R.id.kb_r, 0x52},
            {R.id.kb_t, 0x54}, {R.id.kb_y, 0x59}, {R.id.kb_u, 0x55}, {R.id.kb_i, 0x49},
            {R.id.kb_o, 0x4F}, {R.id.kb_p, 0x50},
            {R.id.kb_a, 0x41}, {R.id.kb_s, 0x53}, {R.id.kb_d, 0x44}, {R.id.kb_f, 0x46},
            {R.id.kb_g, 0x47}, {R.id.kb_h, 0x48}, {R.id.kb_j, 0x4A}, {R.id.kb_k, 0x4B},
            {R.id.kb_l, 0x4C},
            {R.id.kb_z, 0x5A}, {R.id.kb_x, 0x58}, {R.id.kb_c, 0x43}, {R.id.kb_v, 0x56},
            {R.id.kb_b, 0x42}, {R.id.kb_n, 0x4E}, {R.id.kb_m, 0x4D},
        };
        for (int[] pair : normalMap) {
            final short vk = gfk(pair[1]);
            View v = overlayKeyboardRoot.findViewById(pair[0]);
            if (v != null) v.setOnClickListener(x -> sendKey(vk));
        }

        // Special keys
        setKeyClick(R.id.kb_bspc,  VK_BACK);
        setKeyClick(R.id.kb_del,   VK_DELETE);
        setKeyClick(R.id.kb_space, VK_SPACE);
        setKeyClick(R.id.kb_enter, VK_RETURN);
        setKeyClick(R.id.kb_tab,   VK_TAB);
        setKeyClick(R.id.kb_esc,   VK_ESCAPE);
        // HOME and END keys are not present in layout
        // setKeyClick(R.id.kb_home,  VK_HOME);
        // setKeyClick(R.id.kb_end,   VK_END);

        // Modifier toggles (sticky)
        setStickyKey(R.id.kb_ctrl,  () -> toggleModifier(1));
        setStickyKey(R.id.kb_alt,   () -> toggleModifier(2));
        setStickyKey(R.id.kb_shift, () -> toggleModifier(3));
        setStickyKey(R.id.kb_win,   () -> sendKey(VK_LWIN));
    }

    private void wireDevKeys() {
        // Section 1: Modifiers
        setStickyKey(R.id.dev_ctrl,  () -> toggleModifier(1));
        setStickyKey(R.id.dev_alt,   () -> toggleModifier(2));
        setStickyKey(R.id.dev_shift, () -> toggleModifier(3));
        setStickyKey(R.id.dev_win,   () -> sendKey(VK_LWIN));
        setStickyKey(R.id.dev_fn,    () -> { /* Fn modifier logic if needed, or no-op */ });
        setKeyClick(R.id.dev_tab,    VK_TAB);
        setKeyClick(R.id.dev_esc,    VK_ESCAPE);
        setKeyClick(R.id.dev_del,    VK_DELETE);

        setSpecialKeyTooltip(R.id.dev_ctrl,  "CTRL: Tecla modificadora. Combina con otras teclas.");
        setSpecialKeyTooltip(R.id.dev_alt,   "ALT: Tecla modificadora Alt/Option.");
        setSpecialKeyTooltip(R.id.dev_shift, "SHIFT: Tecla modificadora Mayús.");
        setSpecialKeyTooltip(R.id.dev_win,   "WIN: Abre el menú de inicio de Windows.");
        setSpecialKeyTooltip(R.id.dev_fn,    "FN: Tecla de función.");
        setSpecialKeyTooltip(R.id.dev_tab,    "TAB: Inserta una tabulación.");
        setSpecialKeyTooltip(R.id.dev_esc,    "ESC: Tecla de escape.");
        setSpecialKeyTooltip(R.id.dev_del,    "DEL: Suprime el carácter delante del cursor.");

        // Section 2: Navigation
        setKeyClick(R.id.dev_home,  VK_HOME);
        setKeyClick(R.id.dev_end,   VK_END);
        setKeyClick(R.id.dev_pgup,  gfk(0x21)); // VK_PRIOR (Page Up)
        setKeyClick(R.id.dev_pgdn,  gfk(0x22)); // VK_NEXT (Page Down)
        setKeyClick(R.id.dev_ins,   gfk(0x2D)); // VK_INSERT
        setKeyClick(R.id.dev_bspc,  VK_BACK);
        setKeyClick(R.id.dev_enter, VK_RETURN);

        setSpecialKeyTooltip(R.id.dev_home,  "HOME: Mueve el cursor al inicio de la línea.");
        setSpecialKeyTooltip(R.id.dev_end,   "END: Mueve el cursor al final de la línea.");
        setSpecialKeyTooltip(R.id.dev_pgup,  "PAGE UP: Desplaza la página hacia arriba.");
        setSpecialKeyTooltip(R.id.dev_pgdn,  "PAGE DOWN: Desplaza la página hacia abajo.");
        setSpecialKeyTooltip(R.id.dev_ins,   "INSERT: Alterna entre el modo insertar y sobreescribir.");

        // Section 3: Functions (F1-F12)
        short[] fKeys = {
            VK_F1,  VK_F2,  VK_F3,  VK_F4,  VK_F5,  VK_F6,
            VK_F7,  VK_F8,  VK_F9,  VK_F10, VK_F11, VK_F12
        };
        int[] fIds = {
            R.id.dev_f1, R.id.dev_f2, R.id.dev_f3, R.id.dev_f4, R.id.dev_f5, R.id.dev_f6,
            R.id.dev_f7, R.id.dev_f8, R.id.dev_f9, R.id.dev_f10, R.id.dev_f11, R.id.dev_f12
        };
        for (int i = 0; i < fIds.length; i++) {
            final short vk = fKeys[i];
            View v = overlayKeyboardRoot.findViewById(fIds[i]);
            if (v != null) v.setOnClickListener(x -> sendKey(vk));
        }

        // Section 4: Symbols (Brackets, punctuation, etc.)
        setKeyClick(R.id.dev_brack_open,  gfk(0xDB)); // [
        setKeyClick(R.id.dev_brack_close, gfk(0xDD)); // ]
        setKeyClick(R.id.dev_dot,         gfk(0xBE)); // .
        setKeyClick(R.id.dev_semi,        gfk(0xBA)); // ;
        setKeyClick(R.id.dev_colon,       gfk(0xBA)); // Shift+; = : (represented as shifted in setShiftedKey below)
        
        // { } ( ) < > require Shift
        setShiftedKey(R.id.dev_brace_open,  gfk(0xDB)); // {
        setShiftedKey(R.id.dev_brace_close, gfk(0xDD)); // }
        setShiftedKey(R.id.dev_paren_open,  gfk(0x39)); // (
        setShiftedKey(R.id.dev_paren_close, gfk(0x30)); // )
        setShiftedKey(R.id.dev_angle_open,  gfk(0xBC)); // <
        setShiftedKey(R.id.dev_angle_close, gfk(0xBE)); // >
        setShiftedKey(R.id.dev_colon,       gfk(0xBA)); // :

        // New Symbols (Backslash, Pipe, Amp, Star, Percent, Dollar, Hash, At, Tilde, Backtick)
        setKeyClick(R.id.dev_backslash,   gfk(0xDC)); // \
        setShiftedKey(R.id.dev_pipe,      gfk(0xDC)); // |
        setShiftedKey(R.id.dev_amp,       gfk(0x37)); // &
        setShiftedKey(R.id.dev_star,      gfk(0x38)); // *
        setShiftedKey(R.id.dev_percent,   gfk(0x35)); // %
        setShiftedKey(R.id.dev_dollar,    gfk(0x34)); // $
        setShiftedKey(R.id.dev_hash,      gfk(0x33)); // #
        setShiftedKey(R.id.dev_at,        gfk(0x32)); // @
        setShiftedKey(R.id.dev_tilde,     gfk(0xC0)); // ~
        setKeyClick(R.id.dev_backtick,    gfk(0xC0)); // `

        // Section 5: Operators
        // ==, !=, &&, ||, =>, <=, >=, ++, --, +=, -=, *=, /=
        View eqeq = overlayKeyboardRoot.findViewById(R.id.dev_eq_eq);
        if (eqeq != null) eqeq.setOnClickListener(x -> { sendKey(gfk(0xBB)); sendKey(gfk(0xBB)); });
        View noteq = overlayKeyboardRoot.findViewById(R.id.dev_not_eq);
        if (noteq != null) noteq.setOnClickListener(x -> { sendKeyWithMods(gfk(0x31), KeyboardPacket.MODIFIER_SHIFT); sendKey(gfk(0xBB)); });
        View andop = overlayKeyboardRoot.findViewById(R.id.dev_and);
        if (andop != null) andop.setOnClickListener(x -> { sendKeyWithMods(gfk(0x37), KeyboardPacket.MODIFIER_SHIFT); sendKeyWithMods(gfk(0x37), KeyboardPacket.MODIFIER_SHIFT); });
        View orop = overlayKeyboardRoot.findViewById(R.id.dev_or);
        if (orop != null) orop.setOnClickListener(x -> { sendKeyWithMods(gfk(0xDC), KeyboardPacket.MODIFIER_SHIFT); sendKeyWithMods(gfk(0xDC), KeyboardPacket.MODIFIER_SHIFT); });
        View arrow = overlayKeyboardRoot.findViewById(R.id.dev_arrow);
        if (arrow != null) arrow.setOnClickListener(x -> { sendKey(gfk(0xBB)); sendKeyWithMods(gfk(0xBE), KeyboardPacket.MODIFIER_SHIFT); });
        View lteq = overlayKeyboardRoot.findViewById(R.id.dev_lt_eq);
        if (lteq != null) lteq.setOnClickListener(x -> { sendKeyWithMods(gfk(0xBC), KeyboardPacket.MODIFIER_SHIFT); sendKey(gfk(0xBB)); });
        View gteq = overlayKeyboardRoot.findViewById(R.id.dev_gt_eq);
        if (gteq != null) gteq.setOnClickListener(x -> { sendKeyWithMods(gfk(0xBE), KeyboardPacket.MODIFIER_SHIFT); sendKey(gfk(0xBB)); });
        View plpl = overlayKeyboardRoot.findViewById(R.id.dev_plus_plus);
        if (plpl != null) plpl.setOnClickListener(x -> { sendKeyWithMods(gfk(0xBB), KeyboardPacket.MODIFIER_SHIFT); sendKeyWithMods(gfk(0xBB), KeyboardPacket.MODIFIER_SHIFT); });
        View mnmr = overlayKeyboardRoot.findViewById(R.id.dev_minus_minus);
        if (mnmr != null) mnmr.setOnClickListener(x -> { sendKey(gfk(0xBD)); sendKey(gfk(0xBD)); });
        
        View pleq = overlayKeyboardRoot.findViewById(R.id.dev_plus_eq);
        if (pleq != null) pleq.setOnClickListener(x -> { sendKeyWithMods(gfk(0xBB), KeyboardPacket.MODIFIER_SHIFT); sendKey(gfk(0xBB)); });
        View mneq = overlayKeyboardRoot.findViewById(R.id.dev_minus_eq);
        if (mneq != null) mneq.setOnClickListener(x -> { sendKey(gfk(0xBD)); sendKey(gfk(0xBB)); });
        View mteq = overlayKeyboardRoot.findViewById(R.id.dev_star_eq);
        if (mteq != null) mteq.setOnClickListener(x -> { sendKeyWithMods(gfk(0x38), KeyboardPacket.MODIFIER_SHIFT); sendKey(gfk(0xBB)); });
        View dveq = overlayKeyboardRoot.findViewById(R.id.dev_slash_eq);
        if (dveq != null) dveq.setOnClickListener(x -> { sendKey(gfk(0xBF)); sendKey(gfk(0xBB)); });

        // Section 6: Shortcut chips (Horizontal scroll)
        // VS CODE
        setChipShortcut(R.id.chip_vs_palette, VK_P, (byte)(KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT), "Ctrl+Shift+P: Abre la paleta de comandos de VS Code");
        setChipShortcut(R.id.chip_vs_files,   VK_P, KeyboardPacket.MODIFIER_CTRL, "Ctrl+P: Buscar archivos en VS Code");
        setChipShortcut(R.id.chip_vs_sidebar, gfk(0x42), KeyboardPacket.MODIFIER_CTRL, "Ctrl+B: Mostrar/ocultar barra lateral de VS Code");
        setChipShortcut(R.id.chip_vs_term,    VK_BACK_QUOTE, KeyboardPacket.MODIFIER_CTRL, "Ctrl+`: Abrir/cerrar terminal integrada en VS Code");
        setChipShortcut(R.id.chip_vs_format,  gfk(0x46), (byte)(KeyboardPacket.MODIFIER_ALT | KeyboardPacket.MODIFIER_SHIFT), "Alt+Shift+F: Formatear documento en VS Code");
        setChipShortcut(R.id.chip_vs_comment, gfk(0xBF), KeyboardPacket.MODIFIER_CTRL, "Ctrl+/: Comentar/descomentar línea en VS Code");
        setChipShortcut(R.id.chip_vs_def,     VK_F12, (byte)0, "F12: Ir a la definición de un símbolo en VS Code");

        // ANDROID STUDIO
        View doubleShift = overlayKeyboardRoot.findViewById(R.id.chip_as_everywhere);
        if (doubleShift != null) {
            doubleShift.setOnClickListener(x -> {
                sendKey(gfk(0x10)); // Shift press 1
                try { Thread.sleep(80); } catch (InterruptedException e) {}
                sendKey(gfk(0x10)); // Shift press 2
            });
            setSpecialKeyTooltip(R.id.chip_as_everywhere, "Double-Shift: Buscar en todas partes (Android Studio)");
        }
        setChipShortcut(R.id.chip_as_class,   gfk(0x4E), KeyboardPacket.MODIFIER_CTRL, "Ctrl+N: Buscar clase en Android Studio");
        setChipShortcut(R.id.chip_as_recent,  gfk(0x45), KeyboardPacket.MODIFIER_CTRL, "Ctrl+E: Mostrar archivos recientes en Android Studio");
        setChipShortcut(R.id.chip_as_format,  gfk(0x4C), (byte)(KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_ALT), "Ctrl+Alt+L: Formatear código en Android Studio");

        // CURSOR AI
        setChipShortcut(R.id.chip_cur_edit,     VK_K, KeyboardPacket.MODIFIER_CTRL, "Ctrl+K: Abrir edición/chat en línea con IA (Cursor)");
        setChipShortcut(R.id.chip_cur_chat,     gfk(0x4C), KeyboardPacket.MODIFIER_CTRL, "Ctrl+L: Abrir panel de chat con IA (Cursor)");
        setChipShortcut(R.id.chip_cur_composer, gfk(0x49), KeyboardPacket.MODIFIER_CTRL, "Ctrl+I: Abrir IA Composer (Cursor)");

        // TERMINAL
        setChipShortcut(R.id.chip_term_interrupt, VK_C, KeyboardPacket.MODIFIER_CTRL, "Ctrl+C: Interrumpir proceso en terminal / Copiar");
        setChipShortcut(R.id.chip_term_paste,     VK_V, KeyboardPacket.MODIFIER_CTRL, "Ctrl+V: Pegar en la terminal / Pegar texto");
        setChipShortcut(R.id.chip_term_cut,       VK_X, KeyboardPacket.MODIFIER_CTRL, "Ctrl+X: Cortar texto");
        setChipShortcut(R.id.chip_term_undo,      VK_Z, KeyboardPacket.MODIFIER_CTRL, "Ctrl+Z: Deshacer acción");
        setChipShortcut(R.id.chip_term_history,   gfk(0x52), KeyboardPacket.MODIFIER_CTRL, "Ctrl+R: Buscar comando en historial de la terminal");
        setChipShortcut(R.id.chip_term_save,      VK_S, KeyboardPacket.MODIFIER_CTRL, "Ctrl+S: Guardar archivo actual");

        // SYSTEM
        setChipShortcut(R.id.chip_sys_alt_tab,   VK_TAB, KeyboardPacket.MODIFIER_ALT, "Alt+Tab: Cambiar entre ventanas activas");
        setChipShortcut(R.id.chip_sys_alt_f4,    VK_F4, KeyboardPacket.MODIFIER_ALT, "Alt+F4: Cerrar la ventana actual");
        setChipShortcut(R.id.chip_sys_tab,       VK_TAB, KeyboardPacket.MODIFIER_CTRL, "Ctrl+Tab: Ir a la siguiente pestaña");
        setChipShortcut(R.id.chip_sys_tab_prev,  VK_TAB, (byte)(KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT), "Ctrl+Shift+Tab: Ir a la pestaña anterior");
    }

    private void wireCompactBar() {
        setStickyKey(R.id.compCtrl,  () -> toggleModifier(1));
        setStickyKey(R.id.compAlt,   () -> toggleModifier(2));
        setStickyKey(R.id.compShift, () -> toggleModifier(3));
        setKeyClick(R.id.compTab,    VK_TAB);
        setKeyClick(R.id.compEsc,    VK_ESCAPE);
        setKeyClick(R.id.compBsp,    VK_BACK);
        setKeyClick(R.id.compEnter,  VK_RETURN);
        
        // Expand/Compact toggle
        View btnExpand = overlayKeyboardRoot.findViewById(R.id.btnKbExpand);
        if (btnExpand != null) {
            btnExpand.setOnClickListener(v -> toggleCompact());
        }

        setSpecialKeyTooltip(R.id.compCtrl,  "CTRL: Tecla modificadora Control.");
        setSpecialKeyTooltip(R.id.compAlt,   "ALT: Tecla modificadora Alt.");
        setSpecialKeyTooltip(R.id.compShift, "SHIFT: Tecla modificadora Shift.");
        setSpecialKeyTooltip(R.id.compTab,   "TAB: Tecla Tabulación.");
        setSpecialKeyTooltip(R.id.compEsc,   "ESC: Tecla Escape.");
    }


    // ══════════════════════════════════════════════════════════════════════════
    // UTILITY WIRING METHODS
    // ══════════════════════════════════════════════════════════════════════════
    private void setKeyClick(int viewId, short vkCode) {
        View v = overlayKeyboardRoot.findViewById(viewId);
        if (v != null) v.setOnClickListener(x -> sendKeyWithMods(vkCode, activeMods()));
    }

    private void setStickyKey(int viewId, Runnable toggle) {
        View v = overlayKeyboardRoot.findViewById(viewId);
        if (v != null) v.setOnClickListener(x -> toggle.run());
    }

    private void toggleModifier(int mod) {
        if (mod == 1) ctrlDown = !ctrlDown;
        if (mod == 2) altDown = !altDown;
        if (mod == 3) shiftDown = !shiftDown;
        updateModifierVisuals();
    }

    private void updateModifierVisuals() {
        setModifierVisuals(R.id.kb_ctrl,   R.id.dev_ctrl,   R.id.compCtrl,  ctrlDown);
        setModifierVisuals(R.id.kb_alt,    R.id.dev_alt,    R.id.compAlt,   altDown);
        setModifierVisuals(R.id.kb_shift,  R.id.dev_shift,  R.id.compShift, shiftDown);
    }

    private void setModifierVisuals(int kbId, int devId, int compId, boolean active) {
        int color = active ? 0xFFBB86FC : 0xFFE0E0E0; // Purple when active, light gray otherwise
        
        TextView vKb = overlayKeyboardRoot.findViewById(kbId);
        if (vKb != null) vKb.setTextColor(color);
        
        TextView vDev = overlayKeyboardRoot.findViewById(devId);
        if (vDev != null) vDev.setTextColor(color);
        
        TextView vComp = overlayKeyboardRoot.findViewById(compId);
        if (vComp != null) vComp.setTextColor(color);
    }

    private void showPremiumTooltip(View anchorView, String text) {
        TextView tooltipView = new TextView(context);
        tooltipView.setText(text);
        tooltipView.setTextSize(12f);
        tooltipView.setPadding(24, 12, 24, 12);
        
        int bgColor;
        int borderColor;
        int textColor;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            bgColor = context.getResources().getColor(R.color.tooltip_bg, context.getTheme());
            borderColor = context.getResources().getColor(R.color.tooltip_border, context.getTheme());
            textColor = context.getResources().getColor(R.color.overlay_text_primary, context.getTheme());
        } else {
            bgColor = context.getResources().getColor(R.color.tooltip_bg);
            borderColor = context.getResources().getColor(R.color.tooltip_border);
            textColor = context.getResources().getColor(R.color.overlay_text_primary);
        }
        
        tooltipView.setTextColor(textColor);
        
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(16f);
        gd.setStroke(2, borderColor);
        tooltipView.setBackground(gd);
        
        final android.widget.PopupWindow popup = new android.widget.PopupWindow(
                tooltipView,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popup.setOutsideTouchable(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            popup.setElevation(16f);
        }
        
        tooltipView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int tooltipHeight = tooltipView.getMeasuredHeight();
        int tooltipWidth = tooltipView.getMeasuredWidth();
        
        int xOffset = (anchorView.getWidth() - tooltipWidth) / 2;
        int yOffset = -tooltipHeight - anchorView.getHeight() - 12; // Posicionarlo por encima
        
        try {
            popup.showAsDropDown(anchorView, xOffset, yOffset);
        } catch (Exception e) {
            // Fallback in case window is not attached yet
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show();
        }
        
        anchorView.postDelayed(popup::dismiss, 2500);
    }

    private void setChipShortcut(int viewId, short vkCode, byte extraMods, String description) {
        View v = overlayKeyboardRoot.findViewById(viewId);
        if (v != null) {
            v.setOnClickListener(x -> sendKeyWithMods(vkCode, (byte)(activeMods() | extraMods)));
            v.setOnLongClickListener(x -> {
                showPremiumTooltip(v, description);
                return true;
            });
        }
    }

    private void setSpecialKeyTooltip(int viewId, String description) {
        View v = overlayKeyboardRoot.findViewById(viewId);
        if (v != null) {
            v.setOnLongClickListener(x -> {
                showPremiumTooltip(v, description);
                return true;
            });
        }
    }


    /** Sends a key with SHIFT modifier added. */
    private void setShiftedKey(int viewId, short vkCode) {
        View v = overlayKeyboardRoot.findViewById(viewId);
        if (v != null) v.setOnClickListener(x -> sendKeyWithMods(vkCode, (byte)(activeMods() | KeyboardPacket.MODIFIER_SHIFT)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CLEANUP & LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════
    public void onConfigurationChanged(Configuration newConfig) {
        // Called when the device rotates (portrait <-> landscape). A plain post()
        // can run BEFORE the new layout pass, so it would read the OLD width/height
        // and leave the FAB off-screen ("disappeared"). Instead, reposition on the
        // first global layout AFTER the rotation, when the new dimensions are real.
        if (overlayLauncherRoot == null) return;

        overlayLauncherRoot.setVisibility(View.VISIBLE);
        overlayLauncherRoot.bringToFront();

        final android.view.ViewTreeObserver vto = overlayLauncherRoot.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (overlayLauncherRoot.getViewTreeObserver().isAlive()) {
                    overlayLauncherRoot.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                restoreFabPosition();
            }
        });
        overlayLauncherRoot.requestLayout();
    }

    private void toggleOneHandedMode() {
        oneHandedState = (oneHandedState + 1) % 3;
        prefs.edit().putInt(PREF_KB_ONE_HANDED, oneHandedState).apply();
        applyOneHandedMode();
    }

    private void applyOneHandedMode() {
        TextView btnOneHanded = overlayKeyboardRoot.findViewById(R.id.btnKbOneHanded);
        if (btnOneHanded != null) {
            switch (oneHandedState) {
                case 1:
                    btnOneHanded.setText("1H-L");
                    btnOneHanded.setTextColor(0xFFBB86FC); // active purple
                    break;
                case 2:
                    btnOneHanded.setText("1H-R");
                    btnOneHanded.setTextColor(0xFFBB86FC); // active purple
                    break;
                default:
                    btnOneHanded.setText("1H");
                    btnOneHanded.setTextColor(0xFFAAAAAA); // inactive gray
                    break;
            }
        }
        applyKeyboardScaleAndTranslation();
    }

    private void applyKeyboardScaleAndTranslation() {
        if (overlayKeyboardRoot == null) return;

        float targetScale = keyboardScale;
        float targetTransX = overlayKeyboardRoot.getTranslationX();
        float targetTransY = overlayKeyboardRoot.getTranslationY();

        int width = overlayKeyboardRoot.getWidth();
        if (width == 0) {
            overlayKeyboardRoot.post(this::applyKeyboardScaleAndTranslation);
            return;
        }

        if (oneHandedState == 1) { // Left-aligned
            targetScale = 0.7f;
            targetTransX = -(width * (1.0f - targetScale) / 2.0f);
            targetTransY = 0f; // reset Y to bottom when toggling mode
        } else if (oneHandedState == 2) { // Right-aligned
            targetScale = 0.7f;
            targetTransX = (width * (1.0f - targetScale) / 2.0f);
            targetTransY = 0f; // reset Y to bottom when toggling mode
        } else {
            // Center/Standard mode: reset X translation to 0
            targetTransX = 0f;
        }

        overlayKeyboardRoot.setScaleX(targetScale);
        overlayKeyboardRoot.setScaleY(targetScale);
        overlayKeyboardRoot.setTranslationX(targetTransX);
        overlayKeyboardRoot.setTranslationY(targetTransY);
        prefs.edit().putFloat(PREF_KB_SCALE, keyboardScale).apply();
    }

    private boolean onKeyboardTouch(View v, MotionEvent e) {
        if (scaleGestureDetector != null) {
            scaleGestureDetector.onTouchEvent(e);
            if (scaleGestureDetector.isInProgress()) {
                kbDragging = false;
                return true;
            }
        }

        // If touch is on a clickable child of the top bar, let the child handle it
        View topBar = overlayKeyboardRoot.findViewById(R.id.keyboardTopBar);
        if (v == topBar && topBar instanceof ViewGroup) {
            if (isTouchOnClickableChild((ViewGroup) topBar, e)) {
                return false;
            }
        }

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                kbTouchOffsetX = e.getRawX() - overlayKeyboardRoot.getTranslationX();
                kbTouchOffsetY = e.getRawY() - overlayKeyboardRoot.getTranslationY();
                kbDragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(e.getRawX() - (overlayKeyboardRoot.getTranslationX() + kbTouchOffsetX));
                float dy = Math.abs(e.getRawY() - (overlayKeyboardRoot.getTranslationY() + kbTouchOffsetY));
                if (dx > 8 || dy > 8) {
                    kbDragging = true;
                }
                if (kbDragging) {
                    float currentScale = overlayKeyboardRoot.getScaleX();
                    float newTranslationX = e.getRawX() - kbTouchOffsetX;
                    float newTranslationY = e.getRawY() - kbTouchOffsetY;

                    int rootWidth = rootLayout.getWidth();
                    int rootHeight = rootLayout.getHeight();
                    int kbWidth = overlayKeyboardRoot.getWidth();
                    int kbHeight = overlayKeyboardRoot.getHeight();

                    float scaledWidth = currentScale * kbWidth;
                    float scaledHeight = currentScale * kbHeight;

                    // Relaxed clamping: allow dragging horizontally and vertically,
                    // keeping at least 100px visible on screen.
                    float halfRemainingWidth = (kbWidth - scaledWidth) / 2.0f;
                    float maxTranslationX = rootWidth - 100f - halfRemainingWidth;
                    float minTranslationX = 100f - rootWidth + halfRemainingWidth;
                    newTranslationX = Math.max(minTranslationX, Math.min(newTranslationX, maxTranslationX));

                    float maxTranslationY = scaledHeight * 0.8f;
                    float minTranslationY = -(rootHeight - scaledHeight);
                    if (minTranslationY > 0) {
                        minTranslationY = 0;
                    }
                    newTranslationY = Math.max(minTranslationY, Math.min(newTranslationY, maxTranslationY));

                    overlayKeyboardRoot.setTranslationX(newTranslationX);
                    overlayKeyboardRoot.setTranslationY(newTranslationY);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                kbDragging = false;
                return true;
        }
        return false;
    }

    private boolean isTouchOnClickableChild(ViewGroup parent, MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                if (x >= child.getLeft() && x <= child.getRight() &&
                    y >= child.getTop() && y <= child.getBottom()) {
                    if (child.isClickable()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void destroy() {
        if (overlayLauncherRoot != null) rootLayout.removeView(overlayLauncherRoot);
        if (overlayKeyboardRoot != null) rootLayout.removeView(overlayKeyboardRoot);
    }
}
