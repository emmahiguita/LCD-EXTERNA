package com.limelight.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import android.widget.RelativeLayout;

import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;

public class TouchpadOverlay extends LinearLayout {

    private NvConnection conn;
    private View touchpadArea;
    private Button btnLeftClick, btnRightClick, btnMiddleClick, btnScrollUp, btnScrollDown;
    private View mouseButtonsContainer;
    private Button btnToggleMouseButtons;

    private float lastX, lastY;
    private float downX, downY;
    private boolean isDragging = false;

    public TouchpadOverlay(Context context, NvConnection conn) {
        super(context);
        this.conn = conn;
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.overlay_touchpad, this, true);

        touchpadArea = findViewById(R.id.touchArea);
        btnLeftClick = findViewById(R.id.btnLmb);
        btnRightClick = findViewById(R.id.btnRmb);
        btnMiddleClick = findViewById(R.id.btnMmb);
        mouseButtonsContainer = findViewById(R.id.mouseButtonsContainer);
        btnToggleMouseButtons = findViewById(R.id.btnToggleMouseButtons);

        if (btnToggleMouseButtons != null && mouseButtonsContainer != null) {
            // By default, make it gone to save space
            mouseButtonsContainer.setVisibility(View.GONE);
            btnToggleMouseButtons.setOnClickListener(v -> {
                boolean isVisible = mouseButtonsContainer.getVisibility() == View.VISIBLE;
                if (isVisible) {
                    mouseButtonsContainer.setVisibility(View.GONE);
                    btnToggleMouseButtons.setAlpha(0.5f);
                } else {
                    mouseButtonsContainer.setVisibility(View.VISIBLE);
                    btnToggleMouseButtons.setAlpha(1.0f);
                }
            });
        }

        setupTouchpad();
        setupButtonListeners();
    }

    private void setupTouchpad() {
        touchpadArea.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDragging = true;
                    lastX = event.getX();
                    lastY = event.getY();
                    downX = event.getX();
                    downY = event.getY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isDragging && conn != null) {
                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;

                        // Send mouse movement
                        conn.sendMouseMove((short) dx, (short) dy);

                        lastX = event.getX();
                        lastY = event.getY();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    isDragging = false;
                    // Only send left click if it was a tap (minimal movement)
                    float travelX = event.getX() - downX;
                    float travelY = event.getY() - downY;
                    float distance = (float) Math.sqrt(travelX * travelX + travelY * travelY);
                    if (distance < 15 && conn != null) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                    }
                    return true;
            }
            return false;
        });
    }

    private void setupButtonListeners() {
        btnLeftClick.setOnClickListener(v -> {
            if (conn != null) {
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
            }
        });

        btnRightClick.setOnClickListener(v -> {
            if (conn != null) {
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
            }
        });

        btnMiddleClick.setOnClickListener(v -> {
            if (conn != null) {
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE);
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE);
            }
        });

        // Scroll functionality removed from UI
    }

    public void setOverlayVisibility(boolean visible) {
        animate().cancel();
        if (visible) {
            if (getVisibility() != VISIBLE) {
                setAlpha(0f);
                setVisibility(VISIBLE);
            }
            animate().alpha(1f).setDuration(200).withEndAction(null).start();
        } else {
            if (getVisibility() == VISIBLE) {
                animate().alpha(0f).setDuration(200).withEndAction(() -> setVisibility(GONE)).start();
            }
        }
    }
}
