package com.limelight.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

/**
 * WindowControlsController — SmartDisplay AI Premium
 * ────────────────────────────────────────────────────
 *
 * Controla la barra de ventanas Windows flotante (cápsula).
 * Soporta arrastre (draggable), snap magnético, persistencia y auto-shrink.
 *
 * Auto-shrink v2: tras inactividad, la cápsula se reduce a una pastilla
 * pequeña (24×24dp) con alpha reducida. Al tocarla, vuelve al tamaño normal.
 * No interfiere con el botón «Volver» (top-start, 24dp de margen).
 */
public class WindowControlsController {

    // ── Teclas Windows virtuales (formato GFE: bit 15 = extended) ────────
    private static final short GFE_EXT = (short) 0x8000;
    private static final short VK_LWIN    = (short) (GFE_EXT | 0x5B);
    private static final short VK_MENU    = (short) (GFE_EXT | 0x12); // Alt
    private static final short VK_CONTROL = (short) (GFE_EXT | 0x11); // Ctrl
    private static final short VK_TAB     = (short) (GFE_EXT | 0x09);
    private static final short VK_F4      = (short) (GFE_EXT | 0x73);
    private static final short VK_UP      = (short) (GFE_EXT | 0x26);
    private static final short VK_DOWN    = (short) (GFE_EXT | 0x28);
    private static final short VK_T       = (short) (GFE_EXT | 0x54);
    private static final short VK_W       = (short) (GFE_EXT | 0x57);
    private static final short VK_D       = (short) (GFE_EXT | 0x44);

    // ── Config Auto-Shrink ────────────────────────────────────────────────
    /** Tiempo de inactividad antes de encoger (ms). */
    private static final long SHRINK_DELAY_MS = 4000;
    /** Escala mínima del estado encogido (pastilla pequeña). */
    private static final float SHRINK_SCALE   = 0.38f;
    /** Alpha del estado encogido. */
    private static final float SHRINK_ALPHA   = 0.55f;
    /** Duración de la animación de encoger. */
    private static final int   SHRINK_DUR_MS  = 350;
    /** Duración de la animación de despertar. */
    private static final int   WAKE_DUR_MS    = 220;

    // ── Estado ────────────────────────────────────────────────────────────
    private boolean isShrunk = false;
    private boolean toolsExpanded = false;
    private boolean vertical = false;

    private final Object macroLock = new Object();

    // ── Refs de vistas ────────────────────────────────────────────────────
    private final View barView;
    private final View capsuleContent;
    private final Context context;
    private final SharedPreferences prefs;
    private NvConnection conn;

    // ── Drag & Snap ───────────────────────────────────────────────────────
    private float dragStartRawX, dragStartRawY;
    private float wrapperStartX, wrapperStartY;
    private boolean dragging = false;
    private final float density;

    private final Handler shrinkHandler = new Handler(Looper.getMainLooper());
    private final Runnable shrinkRunnable = this::shrinkCapsule;

    public WindowControlsController(View barRootView, Context context) {
        this.barView = barRootView;
        this.context = context;
        this.prefs   = context.getSharedPreferences("smartdisplay_wc", Context.MODE_PRIVATE);
        this.density = context.getResources().getDisplayMetrics().density;
        this.capsuleContent = barRootView.findViewById(R.id.windowControlsContent);

        // Restaurar orientación persistida.
        vertical = prefs.getBoolean("wc_vertical", false);

        barView.post(() -> {
            if (vertical) applyOrientation(true, false);
            adjustCarouselWidth();
            restorePosition();
            resetShrinkTimer();
        });

        // Reencaja en pantalla cuando cambia de tamaño.
        barView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if ((r - l) != (or - ol) || (b - t) != (ob - ot)) {
                clampIntoParent(false);
            }
        });

        bindButtons();
        setupDrag();
    }

    public void setConnection(NvConnection conn) {
        this.conn = conn;
    }

    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    public boolean isVisible() { return barView.getVisibility() == View.VISIBLE; }

    public void release() {
        shrinkHandler.removeCallbacksAndMessages(null);
    }

    public void onOrientationChanged() {
        barView.post(() -> {
            adjustCarouselWidth();
            clampIntoParent(true);
            if (isShrunk) wakeCapsule();
            resetShrinkTimer();
        });
    }

    // ── Orientación ───────────────────────────────────────────────────────

    private void toggleOrientation() {
        applyOrientation(!vertical, true);
        wakeCapsule();
        resetShrinkTimer();
    }

    private void applyOrientation(boolean v, boolean save) {
        vertical = v;
        int o = v ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;

        if (capsuleContent instanceof LinearLayout) {
            LinearLayout content = (LinearLayout) capsuleContent;
            content.setOrientation(o);
            ViewGroup.LayoutParams clp = content.getLayoutParams();
            clp.width  = ViewGroup.LayoutParams.WRAP_CONTENT;
            clp.height = v ? ViewGroup.LayoutParams.WRAP_CONTENT : (int) (48 * density);
            content.setLayoutParams(clp);
        }
        View toolsV = barView.findViewById(R.id.windowControlsTools);
        if (toolsV instanceof LinearLayout) ((LinearLayout) toolsV).setOrientation(o);

        View scroll = barView.findViewById(R.id.windowControlsScroll);
        if (scroll != null) {
            ViewGroup.LayoutParams slp = scroll.getLayoutParams();
            slp.width  = ViewGroup.LayoutParams.WRAP_CONTENT;
            slp.height = v ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
            scroll.setLayoutParams(slp);
        }

        int side = (int) (48 * density);
        int[] ids = {
                R.id.windowControlsGrip, R.id.btnWinMinimize, R.id.btnWinMaximize,
                R.id.btnWinClose, R.id.btnWinAltTab, R.id.btnWinCtrlTab,
                R.id.btnWinNewTab, R.id.btnWinCloseTab, R.id.btnWinDesktop,
                R.id.btnWinTaskView, R.id.btnWinAddTools
        };
        for (int id : ids) {
            View b = barView.findViewById(id);
            if (b == null) continue;
            ViewGroup.LayoutParams blp = b.getLayoutParams();
            blp.width  = side;
            blp.height = v ? side : ViewGroup.LayoutParams.MATCH_PARENT;
            b.setLayoutParams(blp);
        }

        if (save) prefs.edit().putBoolean("wc_vertical", v).apply();
        barView.post(() -> {
            if (!v) adjustCarouselWidth();
            clampIntoParent(true);
        });
    }

    // ── Clamping ──────────────────────────────────────────────────────────

    /**
     * Mantiene la cápsula dentro de los límites del contenedor, además respeta
     * la zona del botón «Volver» (top-start, ~96×96dp) para no solaparse.
     */
    private void clampIntoParent(boolean save) {
        View parent = (View) barView.getParent();
        if (parent == null || parent.getWidth() == 0) return;

        float maxX = Math.max(0, parent.getWidth()  - barView.getWidth());
        float maxY = Math.max(0, parent.getHeight() - barView.getHeight());

        float nx = Math.max(0, Math.min(barView.getX(), maxX));
        float ny = Math.max(0, Math.min(barView.getY(), maxY));

        // Zona de no-solape del botón Atrás (top-start, 24+48=72dp cada lado)
        float avoidW = 80 * density;
        float avoidH = 80 * density;
        // Si la barra está en la esquina top-start, empujarla fuera
        if (nx < avoidW && ny < avoidH) {
            // Moverla al lado derecho si hay espacio, o debajo de la zona
            if (maxX - avoidW > avoidW) {
                nx = avoidW; // empujar hacia la derecha de la zona prohibida
            } else {
                ny = avoidH; // bajar
            }
        }

        if (nx != barView.getX() || ny != barView.getY()) {
            barView.setX(nx);
            barView.setY(ny);
            if (save) savePosition();
        }
    }

    // ── Drag & Snap ───────────────────────────────────────────────────────

    private void setupDrag() {
        View dragHandle = barView.findViewById(R.id.windowControlsGrip);
        if (dragHandle == null) dragHandle = capsuleContent;
        if (dragHandle == null) return;

        // Long-press en el grip = cambiar orientación.
        dragHandle.setOnLongClickListener(v -> {
            toggleOrientation();
            return true;
        });

        dragHandle.setOnTouchListener((v, event) -> {
            // Cualquier toque reactiva si estaba encogida.
            if (isShrunk) {
                wakeCapsule();
                // no consume el evento para permitir también el drag
            }
            resetShrinkTimer();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartRawX = event.getRawX();
                    dragStartRawY = event.getRawY();
                    wrapperStartX = barView.getX();
                    wrapperStartY = barView.getY();
                    dragging = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - dragStartRawX;
                    float dy = event.getRawY() - dragStartRawY;
                    if (!dragging && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
                        dragging = true;
                    }
                    if (dragging) {
                        barView.setX(wrapperStartX + dx);
                        barView.setY(wrapperStartY + dy);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        snapToEdges();
                    }
                    break;
            }
            return false; // dejar pasar los clicks
        });
    }

    private void adjustCarouselWidth() {
        View scroll = barView.findViewById(R.id.windowControlsScroll);
        View tools  = barView.findViewById(R.id.windowControlsTools);
        View parent = (View) barView.getParent();
        if (scroll == null || tools == null || parent == null || parent.getWidth() == 0) return;

        int reserve = (int) (96 * density);
        int maxW = Math.max((int) (120 * density), parent.getWidth() - reserve);
        int contentW = tools.getWidth();
        ViewGroup.LayoutParams lp = scroll.getLayoutParams();
        int desired = (contentW > maxW) ? maxW : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (lp.width != desired) {
            lp.width = desired;
            scroll.setLayoutParams(lp);
        }
    }

    private void snapToEdges() {
        View parent = (View) barView.getParent();
        if (parent == null) return;

        float snapDistance = 32 * density;
        float x = barView.getX();
        float y = barView.getY();
        float targetX = x;
        float targetY = y;

        float parentW = parent.getWidth();
        float parentH = parent.getHeight();
        float w = barView.getWidth();
        float h = barView.getHeight();

        // Snap X
        if (x < snapDistance) targetX = 16 * density;
        else if (x + w > parentW - snapDistance) targetX = parentW - w - (16 * density);
        else if (Math.abs(x + w / 2 - parentW / 2) < snapDistance * 2) targetX = parentW / 2 - w / 2;

        // Snap Y
        if (y < snapDistance) targetY = 16 * density;
        else if (y + h > parentH - snapDistance) targetY = parentH - h - (16 * density);

        // Clamp (respetando zona del botón Atrás en top-start)
        float avoidW = 80 * density;
        float avoidH = 80 * density;
        if (targetX < avoidW && targetY < avoidH) {
            targetX = avoidW;
        }

        targetX = Math.max(0, Math.min(targetX, parentW - w));
        targetY = Math.max(0, Math.min(targetY, parentH - h));

        barView.animate()
                .x(targetX)
                .y(targetY)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(this::savePosition)
                .start();
    }

    private void savePosition() {
        prefs.edit()
             .putFloat("wc_x", barView.getX())
             .putFloat("wc_y", barView.getY())
             .apply();
    }

    private void restorePosition() {
        float savedX = prefs.getFloat("wc_x", -1f);
        float savedY = prefs.getFloat("wc_y", -1f);
        if (savedX >= 0 && savedY >= 0) {
            barView.setX(savedX);
            barView.setY(savedY);
            clampIntoParent(true);
        } else {
            // Posición por defecto: esquina superior-derecha (lejos del botón Atrás)
            barView.post(() -> {
                View parent = (View) barView.getParent();
                if (parent != null && parent.getWidth() > 0) {
                    float defaultX = parent.getWidth() - barView.getWidth() - (16 * density);
                    float defaultY = 24 * density;
                    barView.setX(Math.max(0, defaultX));
                    barView.setY(Math.max(0, defaultY));
                    savePosition();
                }
            });
        }
    }

    // ── Auto-Shrink ───────────────────────────────────────────────────────

    private void resetShrinkTimer() {
        shrinkHandler.removeCallbacks(shrinkRunnable);
        shrinkHandler.postDelayed(shrinkRunnable, SHRINK_DELAY_MS);
    }

    /**
     * Encoge la cápsula a una pastilla pequeña para no estorbar.
     * Mantiene el pivot en el centro de la cápsula para un encogimiento simétrico.
     */
    private void shrinkCapsule() {
        if (isShrunk) return;
        isShrunk = true;
        barView.setPivotX(barView.getWidth() / 2f);
        barView.setPivotY(barView.getHeight() / 2f);
        barView.animate()
               .scaleX(SHRINK_SCALE)
               .scaleY(SHRINK_SCALE)
               .alpha(SHRINK_ALPHA)
               .setDuration(SHRINK_DUR_MS)
               .setInterpolator(new DecelerateInterpolator())
               .start();
    }

    /**
     * Despierta la cápsula a tamaño completo con una animación de rebote suave.
     */
    private void wakeCapsule() {
        if (!isShrunk) return;
        isShrunk = false;
        barView.animate()
               .scaleX(1f)
               .scaleY(1f)
               .alpha(1f)
               .setDuration(WAKE_DUR_MS)
               .setInterpolator(new OvershootInterpolator(1.5f))
               .start();
    }

    // ── Botones ───────────────────────────────────────────────────────────

    private void bindButtons() {
        View btnMin    = barView.findViewById(R.id.btnWinMinimize);
        View btnMax    = barView.findViewById(R.id.btnWinMaximize);
        View btnClose  = barView.findViewById(R.id.btnWinClose);
        View btnAltTab = barView.findViewById(R.id.btnWinAltTab);
        View btnCtrlTab = barView.findViewById(R.id.btnWinCtrlTab);
        final View btnAddTools = barView.findViewById(R.id.btnWinAddTools);
        final View btnNewTab   = barView.findViewById(R.id.btnWinNewTab);
        final View btnCloseTab = barView.findViewById(R.id.btnWinCloseTab);
        final View btnDesktop  = barView.findViewById(R.id.btnWinDesktop);
        final View btnTaskView = barView.findViewById(R.id.btnWinTaskView);

        View.OnClickListener listener = v -> {
            // Si está encogida, el primer toque la despierta
            if (isShrunk) {
                wakeCapsule();
                resetShrinkTimer();
                return;
            }
            if (dragging) return;
            animateButton(v);
            resetShrinkTimer();

            int id = v.getId();
            if (id == R.id.btnWinMinimize) {
                sendMacro(VK_LWIN, VK_DOWN);
            } else if (id == R.id.btnWinMaximize) {
                sendMacro(VK_LWIN, VK_UP);
            } else if (id == R.id.btnWinClose) {
                sendMacro(VK_MENU, VK_F4);
            } else if (id == R.id.btnWinAltTab) {
                sendMacro(VK_MENU, VK_TAB);
            } else if (id == R.id.btnWinCtrlTab) {
                sendMacro(VK_CONTROL, VK_TAB);
            } else if (id == R.id.btnWinNewTab) {
                sendMacro(VK_CONTROL, VK_T);
            } else if (id == R.id.btnWinCloseTab) {
                sendMacro(VK_CONTROL, VK_W);
            } else if (id == R.id.btnWinDesktop) {
                sendMacro(VK_LWIN, VK_D);
            } else if (id == R.id.btnWinTaskView) {
                sendMacro(VK_LWIN, VK_TAB);
            }
        };

        if (btnMin != null) btnMin.setOnClickListener(listener);
        if (btnMax != null) btnMax.setOnClickListener(listener);
        if (btnClose != null) btnClose.setOnClickListener(listener);
        if (btnAltTab != null) btnAltTab.setOnClickListener(listener);
        if (btnCtrlTab != null) btnCtrlTab.setOnClickListener(listener);
        if (btnNewTab != null) btnNewTab.setOnClickListener(listener);
        if (btnCloseTab != null) btnCloseTab.setOnClickListener(listener);
        if (btnDesktop != null) btnDesktop.setOnClickListener(listener);
        if (btnTaskView != null) btnTaskView.setOnClickListener(listener);

        if (btnAddTools != null) {
            btnAddTools.setOnClickListener(v -> {
                if (isShrunk) { wakeCapsule(); resetShrinkTimer(); return; }
                if (dragging) return;
                animateButton(v);
                resetShrinkTimer();
                toolsExpanded = !toolsExpanded;
                int vis = toolsExpanded ? View.VISIBLE : View.GONE;
                if (btnNewTab != null)   btnNewTab.setVisibility(vis);
                if (btnCloseTab != null) btnCloseTab.setVisibility(vis);
                if (btnDesktop != null)  btnDesktop.setVisibility(vis);
                if (btnTaskView != null) btnTaskView.setVisibility(vis);
                barView.post(() -> {
                    adjustCarouselWidth();
                    barView.post(this::snapToEdges);
                });
            });
        }
    }

    // ── Macros de teclado ─────────────────────────────────────────────────

    private void sendMacro(short modKey, short mainKey) {
        if (conn == null) return;
        synchronized (macroLock) {
            try {
                conn.sendKeyboardInput(modKey,  KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
                conn.sendKeyboardInput(mainKey, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
                conn.sendKeyboardInput(mainKey, KeyboardPacket.KEY_UP,   (byte) 0, (byte) 0);
                conn.sendKeyboardInput(modKey,  KeyboardPacket.KEY_UP,   (byte) 0, (byte) 0);
            } catch (Exception ignored) {}
        }
    }

    private void animateButton(View v) {
        v.animate()
         .scaleX(0.85f).scaleY(0.85f)
         .setDuration(80)
         .withEndAction(() -> v.animate()
                 .scaleX(1f).scaleY(1f)
                 .setDuration(120)
                 .setInterpolator(new OvershootInterpolator(2f))
                 .start())
         .start();
    }
}
