package com.limelight.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.limelight.R;
import com.limelight.binding.input.InputMode;

public class ControlsMenuOverlay extends LinearLayout {

    private Button btnMenuTouchpad, btnMenuGamepad, btnMenuMultimedia, btnMenuKeyboard, btnCloseMenu;
    private MenuCallback callback;

    public interface MenuCallback {
        void onMenuItemSelected(InputMode mode);
        void onMenuClose();
    }

    public ControlsMenuOverlay(Context context, MenuCallback callback) {
        super(context);
        this.callback = callback;
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.overlay_controls_menu, this, true);

        btnMenuTouchpad = findViewById(R.id.btnMenuTouchpad);
        btnMenuGamepad = findViewById(R.id.btnMenuGamepad);
        btnMenuMultimedia = findViewById(R.id.btnMenuMultimedia);
        btnMenuKeyboard = findViewById(R.id.btnMenuKeyboard);
        btnCloseMenu = findViewById(R.id.btnCloseMenu);

        setupButtonListeners();
    }

    private void setupButtonListeners() {
        btnMenuTouchpad.setOnClickListener(v -> {
            if (callback != null) {
                callback.onMenuItemSelected(InputMode.TOUCHPAD);
            }
        });

        btnMenuGamepad.setOnClickListener(v -> {
            if (callback != null) {
                callback.onMenuItemSelected(InputMode.GAMEPAD);
            }
        });

        btnMenuMultimedia.setOnClickListener(v -> {
            if (callback != null) {
                callback.onMenuItemSelected(InputMode.NAVIGATION); // Using NAVIGATION for multimedia
            }
        });

        btnMenuKeyboard.setOnClickListener(v -> {
            if (callback != null) {
                callback.onMenuItemSelected(InputMode.KEYBOARD);
            }
        });

        btnCloseMenu.setOnClickListener(v -> {
            if (callback != null) {
                callback.onMenuClose();
            }
        });
    }

    public void setOverlayVisibility(boolean visible) {
        animate().cancel();
        if (visible) {
            if (getVisibility() != VISIBLE) {
                setAlpha(0f);
                setTranslationX(-300f);
                setVisibility(VISIBLE);
            }
            animate().alpha(1f).translationX(0f).setDuration(250).withEndAction(null).start();
        } else {
            if (getVisibility() == VISIBLE) {
                animate().alpha(0f).translationX(-300f).setDuration(250).withEndAction(() -> setVisibility(GONE)).start();
            }
        }
    }
}
