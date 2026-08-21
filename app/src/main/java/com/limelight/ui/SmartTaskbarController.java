package com.limelight.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.limelight.R;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Smart Taskbar — réplica VIVA de la barra de tareas del PC.
 *
 * No inventa iconos: renderiza las ventanas REALES que envía el companion
 * (get_windows → [{hwnd,pid,title,minimized}]). Cada chip es una ventana:
 *   • Tap: traer la ventana al frente (focus).
 *   • Mantener pulsado: menú Minimizar / Cerrar.
 * Sondea mientras está visible y se detiene al ocultarse (ahorra recursos).
 */
public class SmartTaskbarController {

    /** Puente hacia el PC (implementado por Game vía SmartDisplayBus). */
    public interface Actions {
        void requestWindows();
        void focus(int hwnd);
        void closeWindow(int hwnd);
        void minimize(int hwnd);
    }

    private static final long POLL_MS = 2500L;

    private final View root;
    private final LinearLayout chips;
    private final View emptyLabel;
    private final View body;
    private final ImageView minimizeBtn;
    private final Actions actions;
    private final float density;
    private final int accent;   // color de acento del tema elegido
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean visible = false;
    private boolean minimized = false;

    // Firma del último set de ventanas renderizado. El sondeo cada POLL_MS suele
    // devolver la misma lista; si no cambió, se evita destruir/recrear todos los
    // chips (churn de Views + GC innecesario mientras la barra está visible).
    private String lastSignature = null;

    // ── Arrastre (movilidad total por la pantalla) ──────────────────────────
    private final SharedPreferences prefs;
    private float dragStartRawX, dragStartRawY, viewStartX, viewStartY;
    private boolean dragging = false;
    private boolean positionRestored = false;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!visible || minimized) return;
            actions.requestWindows();
            handler.postDelayed(this, POLL_MS);
        }
    };

    public SmartTaskbarController(View barRoot, Actions actions) {
        this.root = barRoot;
        this.actions = actions;
        this.density = barRoot.getResources().getDisplayMetrics().density;
        this.accent = com.limelight.utils.ThemeManager.accentColor(barRoot.getContext());
        this.chips = barRoot.findViewById(R.id.taskbarChips);
        this.emptyLabel = barRoot.findViewById(R.id.taskbarEmpty);
        this.body = barRoot.findViewById(R.id.taskbarBody);
        this.minimizeBtn = barRoot.findViewById(R.id.taskbarMinimize);
        this.prefs = barRoot.getContext().getSharedPreferences("smartdisplay_taskbar", Context.MODE_PRIVATE);
        View close = barRoot.findViewById(R.id.taskbarClose);
        if (close != null) close.setOnClickListener(v -> hide());
        if (minimizeBtn != null) minimizeBtn.setOnClickListener(v -> toggleMinimize());
        applyAccentToPanel();
        setupDrag();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDrag() {
        View grip = root.findViewById(R.id.taskbarGrip);
        if (grip == null) return;
        grip.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartRawX = event.getRawX();
                    dragStartRawY = event.getRawY();
                    viewStartX = root.getX();
                    viewStartY = root.getY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - dragStartRawX;
                    float dy = event.getRawY() - dragStartRawY;
                    if (!dragging && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) dragging = true;
                    if (dragging) {
                        root.setX(clamp(viewStartX + dx, true));
                        root.setY(clamp(viewStartY + dy, false));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) savePosition();
                    return true;
            }
            return false;
        });
    }

    private float clamp(float value, boolean horizontal) {
        View parent = (View) root.getParent();
        if (parent == null) return value;
        float max = horizontal
                ? Math.max(0, parent.getWidth() - root.getWidth())
                : Math.max(0, parent.getHeight() - root.getHeight());
        return Math.max(0, Math.min(value, max));
    }

    private void savePosition() {
        prefs.edit().putFloat("tb_x", root.getX()).putFloat("tb_y", root.getY()).apply();
    }

    private void restorePosition() {
        float x = prefs.getFloat("tb_x", -1f);
        float y = prefs.getFloat("tb_y", -1f);
        if (x >= 0 && y >= 0) {
            root.post(() -> {
                root.setX(clamp(x, true));
                root.setY(clamp(y, false));
            });
        }
    }

    /** Tiñe el borde del panel glass con el acento del tema (translúcido). */
    private void applyAccentToPanel() {
        android.graphics.drawable.Drawable bg = root.getBackground();
        if (bg instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.Drawable base =
                    ((android.graphics.drawable.LayerDrawable) bg).getDrawable(0);
            if (base instanceof android.graphics.drawable.GradientDrawable) {
                int soft = (accent & 0x00FFFFFF) | 0x66000000; // ~40% alpha
                ((android.graphics.drawable.GradientDrawable) base.mutate())
                        .setStroke(Math.round(1f * density), soft);
            }
        }
    }

    public boolean isVisible() { return visible; }

    public void toggle() { if (visible) hide(); else show(); }

    /** Colapsa/expande el cuerpo (chips) dejando solo la cabecera como asa flotante. */
    private void toggleMinimize() {
        if (body == null) return;
        minimized = !minimized;
        if (minimizeBtn != null) {
            minimizeBtn.setImageResource(minimized ? R.drawable.ic_expand_panel : R.drawable.ic_minimize);
        }
        if (minimized) {
            handler.removeCallbacks(poll);
            body.setPivotY(0f);
            body.animate().alpha(0f).scaleY(0.85f).setDuration(150)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> body.setVisibility(View.GONE))
                    .start();
        } else {
            body.setVisibility(View.VISIBLE);
            body.setPivotY(0f);
            body.setAlpha(0f);
            body.setScaleY(0.85f);
            body.animate().alpha(1f).scaleY(1f).setDuration(190)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            actions.requestWindows();
            handler.removeCallbacks(poll);
            handler.postDelayed(poll, POLL_MS);
        }
    }

    public void show() {
        visible = true;
        if (minimized) {   // al reabrir siempre expandida
            minimized = false;
            if (body != null) { body.setVisibility(View.VISIBLE); body.setAlpha(1f); body.setScaleY(1f); }
            if (minimizeBtn != null) minimizeBtn.setImageResource(R.drawable.ic_minimize);
        }
        root.setVisibility(View.VISIBLE);
        // Entrada Material 3: fade + scale.
        root.setAlpha(0f);
        root.setScaleX(0.92f);
        root.setScaleY(0.92f);
        root.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        if (!positionRestored) { positionRestored = true; restorePosition(); }
        actions.requestWindows();
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, POLL_MS);
    }

    public void hide() {
        visible = false;
        handler.removeCallbacks(poll);
        root.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f)
                .setDuration(160)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    root.setVisibility(View.GONE);
                    root.setAlpha(1f);
                    root.setScaleX(1f);
                    root.setScaleY(1f);
                })
                .start();
    }

    public void release() {
        handler.removeCallbacks(poll);
    }

    /** Renderiza las ventanas reales recibidas del PC. Llamar en el hilo UI. */
    public void render(JSONArray windows) {
        if (!visible || minimized) return;
        int count = windows != null ? windows.length() : 0;

        // Si el set de ventanas es idéntico al último render, no se toca la vista.
        String signature = buildSignature(windows, count);
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;

        chips.removeAllViews();
        emptyLabel.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < count; i++) {
            JSONObject w = windows.optJSONObject(i);
            if (w == null) continue;
            final int hwnd = w.optInt("hwnd", 0);
            String title = w.optString("title", "");
            boolean minimized = w.optBoolean("minimized", false);
            if (hwnd == 0 || title.isEmpty()) continue;
            chips.addView(buildChip(hwnd, title, minimized));
        }
    }

    private String buildSignature(JSONArray windows, int count) {
        if (count == 0) return "";
        StringBuilder sb = new StringBuilder(count * 24);
        for (int i = 0; i < count; i++) {
            JSONObject w = windows.optJSONObject(i);
            if (w == null) continue;
            sb.append(w.optInt("hwnd", 0)).append(':')
              .append(w.optBoolean("minimized", false) ? '1' : '0').append(':')
              .append(w.optString("title", "")).append('|');
        }
        return sb.toString();
    }

    private TextView buildChip(final int hwnd, String title, boolean minimized) {
        TextView chip = new TextView(root.getContext());
        // Título recortado para que el chip no se alargue demasiado.
        chip.setText(title.length() > 22 ? title.substring(0, 21) + "…" : title);
        chip.setSingleLine(true);
        chip.setTextColor(0xFFFFFFFF);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.taskbar_chip_bg);
        // Borde del chip con el acento del tema elegido.
        android.graphics.drawable.Drawable cbg = chip.getBackground();
        if (cbg instanceof android.graphics.drawable.GradientDrawable) {
            ((android.graphics.drawable.GradientDrawable) cbg.mutate())
                    .setStroke(Math.round(1.5f * density), accent);
        }
        int ph = dp(12), pv = dp(8);
        chip.setPadding(ph, pv, ph, pv);
        chip.setAlpha(minimized ? 0.5f : 1f); // ventana minimizada = atenuada

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);

        chip.setOnClickListener(v -> actions.focus(hwnd));
        chip.setOnLongClickListener(v -> {
            PopupMenu menu = new PopupMenu(root.getContext(), v);
            menu.getMenu().add(0, 1, 0, R.string.taskbar_minimize);
            menu.getMenu().add(0, 2, 1, R.string.taskbar_close_win);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) actions.minimize(hwnd);
                else if (item.getItemId() == 2) actions.closeWindow(hwnd);
                return true;
            });
            menu.show();
            return true;
        });
        return chip;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
