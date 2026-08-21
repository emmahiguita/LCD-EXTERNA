package com.limelight.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.limelight.R;
import com.limelight.Game;
import com.limelight.nvstream.input.KeyboardPacket;

public class AudioHudController {

    private final Game gameContext;
    private final ViewGroup parentView;
    private View root;

    private float dX, dY;
    private boolean isDragging;

    public AudioHudController(Game gameContext, ViewGroup parentView) {
        this.gameContext = gameContext;
        this.parentView = parentView;
        initView();
    }

    private void initView() {
        LayoutInflater inflater = LayoutInflater.from(gameContext);
        root = inflater.inflate(R.layout.audio_hud_widget, parentView, false);

        // Positioning at the right edge by default
        root.setX(parentView.getWidth() - 200);
        root.setY(200);

        View handle = root.findViewById(R.id.audioHudHandle);
        handle.setOnTouchListener(this::handleTouchDrag);

        ImageButton btnMic = root.findViewById(R.id.btnHudMic);
        ImageButton btnVolUp = root.findViewById(R.id.btnHudVolUp);
        ImageButton btnVolDown = root.findViewById(R.id.btnHudVolDown);
        ImageButton btnMute = root.findViewById(R.id.btnHudMute);
        ImageButton btnCall = root.findViewById(R.id.btnHudCall);

        btnMic.setOnClickListener(v -> sendMacroMicMute());
        btnVolUp.setOnClickListener(v -> sendMacroVolUp());
        btnVolDown.setOnClickListener(v -> sendMacroVolDown());
        btnMute.setOnClickListener(v -> sendMacroMute());
        btnCall.setOnClickListener(v -> sendMacroAcceptCall());

        parentView.addView(root);
        hide(); // Oculto por defecto
    }

    public void show() {
        if (root != null) {
            root.setVisibility(View.VISIBLE);
            root.animate().alpha(1f).setDuration(200).start();
        }
    }

    public void hide() {
        if (root != null) {
            root.animate().alpha(0f).setDuration(200).withEndAction(() -> root.setVisibility(View.GONE)).start();
        }
    }

    public void toggle() {
        if (root != null && root.getVisibility() == View.VISIBLE) {
            hide();
        } else {
            show();
        }
    }

    private boolean handleTouchDrag(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dX = root.getX() - event.getRawX();
                dY = root.getY() - event.getRawY();
                isDragging = true;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    root.animate().x(event.getRawX() + dX).y(event.getRawY() + dY).setDuration(0).start();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                return true;
        }
        return false;
    }

    // --- MACROS PARA PC ---
    
    // Ctrl + Shift + M (Teams / Discord / Zoom Universal Mic Mute)
    private void sendMacroMicMute() {
        short ctrl = (short) ((0x80 << 8) | 0xA2); // L-CTRL
        short shift = (short) ((0x80 << 8) | 0xA0); // L-SHIFT
        short m = (short) ((0x80 << 8) | 0x4D); // M

        gameContext.sendHardwareKey(ctrl, KeyboardPacket.KEY_DOWN);
        gameContext.sendHardwareKey(shift, KeyboardPacket.KEY_DOWN);
        gameContext.sendHardwareKey(m, KeyboardPacket.KEY_DOWN);
        
        gameContext.sendHardwareKey(m, KeyboardPacket.KEY_UP);
        gameContext.sendHardwareKey(shift, KeyboardPacket.KEY_UP);
        gameContext.sendHardwareKey(ctrl, KeyboardPacket.KEY_UP);
    }

    // VK_VOLUME_UP (0xAF)
    private void sendMacroVolUp() {
        short volUp = (short) ((0x80 << 8) | 0xAF);
        gameContext.sendHardwareKey(volUp, KeyboardPacket.KEY_DOWN);
        gameContext.sendHardwareKey(volUp, KeyboardPacket.KEY_UP);
    }

    // VK_VOLUME_DOWN (0xAE)
    private void sendMacroVolDown() {
        short volDown = (short) ((0x80 << 8) | 0xAE);
        gameContext.sendHardwareKey(volDown, KeyboardPacket.KEY_DOWN);
        gameContext.sendHardwareKey(volDown, KeyboardPacket.KEY_UP);
    }

    // VK_VOLUME_MUTE (0xAD)
    private void sendMacroMute() {
        short mute = (short) ((0x80 << 8) | 0xAD);
        gameContext.sendHardwareKey(mute, KeyboardPacket.KEY_DOWN);
        gameContext.sendHardwareKey(mute, KeyboardPacket.KEY_UP);
    }

    // VK_MEDIA_PLAY_PAUSE (0xB3)
    private void sendMacroAcceptCall() {
        short playPause = (short) ((0x80 << 8) | 0xB3);
        gameContext.sendHardwareKey(playPause, KeyboardPacket.KEY_DOWN);
        gameContext.sendHardwareKey(playPause, KeyboardPacket.KEY_UP);
    }
}
