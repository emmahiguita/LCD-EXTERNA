package com.limelight.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;

/**
 * PortraitHybridController — SmartDisplay AI Premium · Componente 4
 * ──────────────────────────────────────────────────────────────────
 *
 * Gestiona el Modo Vertical Híbrido de una sola mano.
 * Activo solo cuando la pantalla está en orientación PORTRAIT.
 *
 * ZONAS:
 *  • hybridStreamContainer  (40%) — El stream del PC se inyecta aquí
 *  • hybridSmartBar         (10%) — Atajos Ctrl+C/V, Tab, Esc, Win, etc.
 *  • hybridTouchpadContainer(50%) — Touchpad multitáctil de precisión
 *
 * GESTOS DEL TOUCHPAD:
 *  • 1 dedo arrastre  → movimiento del cursor (modo relativo)
 *  • 2 dedos arrastre → scroll (vertical/horizontal)
 *  • 2 dedos tap      → click derecho
 *  • 3 dedos          → abrir teclado lógico
 *  • 4 dedos          → mostrar/ocultar SmartBar
 *  • Tap simple       → click izquierdo
 *
 * ACELERACIÓN ADAPTATIVA:
 *  • Movimientos lentos  → precisión de pixel
 *  • Movimientos rápidos → aceleración cuadrática
 */
public class PortraitHybridController {

    // ── Constantes de aceleración ─────────────────────────────────────────
    private static final float ACC_THRESHOLD = 8f;    // dp/frame → inicio de aceleración
    private static final float ACC_FACTOR    = 2.2f;  // multiplicador cuadrático

    // ── Constantes de sensibilidad ────────────────────────────────────────
    private static final float SCROLL_SENSITIVITY = 1.5f;
    private static final float CURSOR_SENSITIVITY = 1.8f;

    // ── VK codes para SmartBar ────────────────────────────────────────────
    private static final short VK_TAB   = 0x09;
    private static final short VK_ESC   = 0x1B;
    private static final short VK_CTRL  = 0x11;
    private static final short VK_ALT   = 0x12;
    private static final short VK_WIN   = 0x5B;
    private static final short VK_C     = 0x43;
    private static final short VK_V     = 0x56;
    private static final short VK_Z     = 0x5A;

    // ── Refs ──────────────────────────────────────────────────────────────
    private final View     rootView;
    private final Context  context;
    private NvConnection   conn;
    private LogicalKeyboardOverlay keyboard;
    private AdaptiveCursorView     cursorView;

    // ── Estado de gestos del touchpad ─────────────────────────────────────
    private float  lastX, lastY;
    private int    activePointers = 0;
    private boolean twoFingerTap  = false;
    private long    twoFingerTime = 0;
    private static final long TWO_FINGER_TAP_MS = 180;

    // ── Acumulador de scroll (subpixel) ───────────────────────────────────
    private float scrollAccX = 0f;
    private float scrollAccY = 0f;

    // ── Inercia de Scroll ─────────────────────────────────────────────────
    private android.view.VelocityTracker velocityTracker;
    private float inertiaScrollX = 0f;
    private float inertiaScrollY = 0f;
    private final Runnable inertiaRunnable = new Runnable() {
        @Override
        public void run() {
            if (Math.abs(inertiaScrollX) < 0.5f && Math.abs(inertiaScrollY) < 0.5f) {
                return;
            }
            sendScroll((int) inertiaScrollX, (int) inertiaScrollY);
            inertiaScrollX *= 0.85f;
            inertiaScrollY *= 0.85f;
            handler.postDelayed(this, 16);
        }
    };

    // ── Handler para UI ───────────────────────────────────────────────────
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ── Indicador de gesto ───────────────────────────────────────────────
    private TextView gestureIndicator;

    // ── Badge de conexión (ping/fps) ──────────────────────────────────────
    private TextView connectionInfo;

    public PortraitHybridController(View hybridRootView, Context context) {
        this.rootView = hybridRootView;
        this.context  = context;

        int indicatorId = context.getResources().getIdentifier("hybridGestureIndicator", "id", context.getPackageName());
        gestureIndicator = indicatorId != 0 ? hybridRootView.findViewById(indicatorId) : null;

        connectionInfo = hybridRootView.findViewById(R.id.hybridConnectionInfo);

        setupSmartBar();
        setupTouchpad();
        hideGestureHint();
    }

    /** Actualiza el badge "X ms · Y fps" con métricas reales del stream. */
    public void updateConnectionInfo(int pingMs, int fps) {
        if (connectionInfo == null) return;
        connectionInfo.setText(pingMs + " ms · " + fps + " fps");
    }

    // ── API Pública ───────────────────────────────────────────────────────

    public void setConnection(NvConnection conn) {
        this.conn = conn;
    }

    public void setKeyboard(LogicalKeyboardOverlay kb) {
        this.keyboard = kb;
    }

    public void setCursorView(AdaptiveCursorView cv) {
        this.cursorView = cv;
    }

    public boolean isPortraitMode() {
        int orientation = context.getResources().getConfiguration().orientation;
        return orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    // ── SmartBar — Atajos de teclado Contextuales ──────────────────────────

    private void setupSmartBar() {
        // Limpiado: El menú inferior es redundante ya que se dispone de un teclado completo
    }

    private void bindKey(int id, Runnable action) {
    }

    public void updateSmartBarForApp(String app) {
    }

    private void rebuildSmartBar(String app) {
    }

    private void setKeyVisibility(int id, boolean visible) {
    }

    // ── Touchpad de Precisión IA ──────────────────────────────────────────

    private void setupTouchpad() {
        int touchpadId = context.getResources().getIdentifier("hybridTouchpad", "id", context.getPackageName());
        View touchpad = touchpadId != 0 ? rootView.findViewById(touchpadId) : null;
        
        int leftClickId = context.getResources().getIdentifier("hybridLeftClick", "id", context.getPackageName());
        View leftClick = leftClickId != 0 ? rootView.findViewById(leftClickId) : null;
        
        int rightClickId = context.getResources().getIdentifier("hybridRightClick", "id", context.getPackageName());
        View rightClick = rightClickId != 0 ? rootView.findViewById(rightClickId) : null;

        if (touchpad != null) {
            touchpad.setOnTouchListener(this::handleTouchpadEvent);
        }
        if (leftClick != null) {
            leftClick.setOnClickListener(v -> sendMouseClick(MouseButtonPacket.BUTTON_LEFT));
        }
        if (rightClick != null) {
            rightClick.setOnClickListener(v -> sendMouseClick(MouseButtonPacket.BUTTON_RIGHT));
        }
    }

    private boolean handleTouchpadEvent(View v, MotionEvent event) {
        int pointerCount = event.getPointerCount();
        int action = event.getActionMasked();

        if (velocityTracker == null) {
            velocityTracker = android.view.VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                activePointers = pointerCount;
                lastX = event.getX(0);
                lastY = event.getY(0);
                if (pointerCount == 2) {
                    twoFingerTap  = true;
                    twoFingerTime = System.currentTimeMillis();
                }
                // Detener inercia actual si tocan de nuevo
                handler.removeCallbacks(inertiaRunnable);
                showGestureHint(pointerCount);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = (event.getX(0) - lastX) * CURSOR_SENSITIVITY;
                float dy = (event.getY(0) - lastY) * CURSOR_SENSITIVITY;
                lastX = event.getX(0);
                lastY = event.getY(0);

                if (pointerCount == 1) {
                    dx = applyAcceleration(dx);
                    dy = applyAcceleration(dy);
                    sendMouseMove((int) dx, (int) dy);

                } else if (pointerCount == 2) {
                    twoFingerTap = false;
                    scrollAccX += dx * SCROLL_SENSITIVITY;
                    scrollAccY += dy * SCROLL_SENSITIVITY;
                    int scrollX = (int) scrollAccX;
                    int scrollY = (int) scrollAccY;
                    if (scrollX != 0 || scrollY != 0) {
                        sendScroll(scrollX, scrollY);
                        scrollAccX -= scrollX;
                        scrollAccY -= scrollY;
                    }

                }
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                activePointers = pointerCount - 1;
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                int finalCount = activePointers;
                activePointers = 0;
                scrollAccX = 0;
                scrollAccY = 0;
                hideGestureIndicator();

                // Calcular velocidad para inercia si soltamos el gesto de 2 dedos (scroll)
                if (finalCount == 2 && velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000); // px/s
                    float vx = velocityTracker.getXVelocity();
                    float vy = velocityTracker.getYVelocity();
                    inertiaScrollX = (vx / 1000f) * SCROLL_SENSITIVITY * 2.5f;
                    inertiaScrollY = (vy / 1000f) * SCROLL_SENSITIVITY * 2.5f;
                    if (Math.abs(inertiaScrollX) > 1f || Math.abs(inertiaScrollY) > 1f) {
                        handler.post(inertiaRunnable);
                    }
                }

                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }

                long elapsed = System.currentTimeMillis() - twoFingerTime;
                if (finalCount == 2 && twoFingerTap && elapsed < TWO_FINGER_TAP_MS) {
                    sendMouseClick(MouseButtonPacket.BUTTON_RIGHT);
                    if (cursorView != null) cursorView.fireRightClick(lastX, lastY);
                } else if (finalCount == 1) {
                    sendMouseClick(MouseButtonPacket.BUTTON_LEFT);
                    if (cursorView != null) cursorView.fireLeftClick(lastX, lastY);
                } else if (finalCount == 3) {
                    handler.post(() -> {
                        if (keyboard != null) {
                            if (keyboard.isVisible()) keyboard.hide();
                            else keyboard.show();
                        }
                    });
                } else if (finalCount == 4) {
                    handler.post(() -> {
                        int barId = context.getResources().getIdentifier("hybridSmartBar", "id", context.getPackageName());
                        View smartBar = barId != 0 ? rootView.findViewById(barId) : null;
                        if (smartBar != null) {
                            if (smartBar.getVisibility() == View.VISIBLE) {
                                smartBar.animate().translationY(-smartBar.getHeight()).alpha(0f).setDuration(220)
                                        .withEndAction(() -> smartBar.setVisibility(View.GONE)).start();
                            } else {
                                smartBar.setVisibility(View.VISIBLE);
                                smartBar.setTranslationY(-smartBar.getHeight());
                                smartBar.setAlpha(0f);
                                smartBar.animate().translationY(0f).alpha(1f).setDuration(220).start();
                            }
                        }
                    });
                }
                twoFingerTap = false;
                return true;
        }
        return false;
    }

    // ── Aceleración adaptativa del cursor ─────────────────────────────────

    private float applyAcceleration(float delta) {
        float abs = Math.abs(delta);
        if (abs < ACC_THRESHOLD) return delta; // zona de precisión, sin aceleración
        // Aceleración cuadrática suave para movimientos rápidos
        float sign  = delta < 0 ? -1 : 1;
        float extra = (abs - ACC_THRESHOLD) / ACC_THRESHOLD;
        return sign * (ACC_THRESHOLD + extra * extra * ACC_FACTOR);
    }

    // ── Envío de input al PC ──────────────────────────────────────────────

    private void sendMouseMove(int dx, int dy) {
        if (conn == null || (dx == 0 && dy == 0)) return;
        try { conn.sendMouseMove((short) dx, (short) dy); } catch (Exception ignored) {}
    }

    private void sendScroll(int dx, int dy) {
        if (conn == null) return;
        try {
            if (dy != 0) conn.sendMouseHighResScroll((short) dy);
            if (dx != 0) conn.sendMouseHighResHScroll((short) dx);
        } catch (Exception ignored) {}
    }

    private void sendMouseClick(byte button) {
        if (conn == null) return;
        try {
            conn.sendMouseButtonDown(button);
            handler.postDelayed(() -> {
                try { conn.sendMouseButtonUp(button); } catch (Exception ignored) {}
            }, 40);
        } catch (Exception ignored) {}
    }

    private void sendKey(short vk) {
        if (conn == null) return;
        try {
            conn.sendKeyboardInput(vk, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
            handler.postDelayed(() -> {
                try { conn.sendKeyboardInput(vk, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0); } catch (Exception ignored) {}
            }, 40);
        } catch (Exception ignored) {}
    }

    private void sendCombo(short mod, short key) {
        if (conn == null) return;
        try {
            conn.sendKeyboardInput(mod, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
            handler.postDelayed(() -> {
                try {
                    conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
                    conn.sendKeyboardInput(mod, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
                } catch (Exception ignored) {}
            }, 60);
        } catch (Exception ignored) {}
    }

    private boolean ctrlActive = false;
    private boolean altActive  = false;

    private void toggleModifier(short vk) {
        // Los modificadores tipo toggle (sticky keys) se implementan
        // manteniendo la tecla presionada hasta que se pulse de nuevo
        boolean active = (vk == VK_CTRL) ? ctrlActive : altActive;
        if (!active) {
            try { if (conn != null) conn.sendKeyboardInput(vk, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0); } catch (Exception ignored) {}
            if (vk == VK_CTRL) ctrlActive = true; else altActive = true;
        } else {
            try { if (conn != null) conn.sendKeyboardInput(vk, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0); } catch (Exception ignored) {}
            if (vk == VK_CTRL) ctrlActive = false; else altActive = false;
        }
        // Actualizar color visual del botón
        int btnId = context.getResources().getIdentifier(vk == VK_CTRL ? "sbCtrl" : "sbAlt", "id", context.getPackageName());
        View btn = btnId != 0 ? rootView.findViewById(btnId) : null;
        if (btn instanceof TextView) {
            boolean nowActive = (vk == VK_CTRL) ? ctrlActive : altActive;
            ((TextView) btn).setTextColor(context.getColor(
                    nowActive ? android.R.color.holo_blue_light : android.R.color.white));
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────

    private void showGestureHint(int fingers) {
        if (gestureIndicator == null) return;
        String[] icons = {"", "☝", "✌", "🤟", "✋"};
        String icon = fingers > 0 && fingers < icons.length ? icons[fingers] : "";
        gestureIndicator.setText(icon);
        gestureIndicator.setVisibility(View.VISIBLE);
        gestureIndicator.animate().alpha(0.7f).setDuration(100).start();
    }

    private void hideGestureIndicator() {
        if (gestureIndicator == null) return;
        gestureIndicator.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> gestureIndicator.setVisibility(View.INVISIBLE))
                .start();
    }

    private void hideGestureHint() {
        int hintId = context.getResources().getIdentifier("hybridGestureHint", "id", context.getPackageName());
        View hint = hintId != 0 ? rootView.findViewById(hintId) : null;
        if (hint == null) return;
        handler.postDelayed(() -> hint.animate().alpha(0f).setDuration(600)
                .withEndAction(() -> hint.setVisibility(View.GONE)).start(), 4000);
    }

    private void animateKey(View v) {
        v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(70)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                .start();
    }
}
