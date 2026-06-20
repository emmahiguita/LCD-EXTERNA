package com.limelight.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.ControllerPacket;

public class GamepadOverlay extends RelativeLayout {

    private NvConnection conn;
    private Button btnDpadUp, btnDpadDown, btnDpadLeft, btnDpadRight;
    private Button btnA, btnB, btnX, btnY;
    private Button btnLB, btnLT, btnRB, btnRT;
    private Button btnStart, btnSelect;
    private View leftStick, rightStick;

    private short controllerNumber = 0;
    private int buttonFlags = 0;
    private byte leftTriggerValue = 0;
    private byte rightTriggerValue = 0;

    public GamepadOverlay(Context context, NvConnection conn) {
        super(context);
        this.conn = conn;
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.overlay_gamepad, this, true);

        // D-Pad
        btnDpadUp = findViewById(R.id.btnDpadUp);
        btnDpadDown = findViewById(R.id.btnDpadDown);
        btnDpadLeft = findViewById(R.id.btnDpadLeft);
        btnDpadRight = findViewById(R.id.btnDpadRight);

        // Action buttons
        btnA = findViewById(R.id.btnA);
        btnB = findViewById(R.id.btnB);
        btnX = findViewById(R.id.btnX);
        btnY = findViewById(R.id.btnY);

        // Shoulder buttons
        btnLB = findViewById(R.id.btnLB);
        btnLT = findViewById(R.id.btnLT);
        btnRB = findViewById(R.id.btnRB);
        btnRT = findViewById(R.id.btnRT);

        // Start/Select
        btnStart = findViewById(R.id.btnStart);
        btnSelect = findViewById(R.id.btnSelect);

        // Sticks removed from UI

        setupButtonListeners();
    }

    private void setupButtonListeners() {
        // D-Pad
        btnDpadUp.setOnTouchListener(new GamepadTouchListener(ControllerPacket.UP_FLAG));
        btnDpadDown.setOnTouchListener(new GamepadTouchListener(ControllerPacket.DOWN_FLAG));
        btnDpadLeft.setOnTouchListener(new GamepadTouchListener(ControllerPacket.LEFT_FLAG));
        btnDpadRight.setOnTouchListener(new GamepadTouchListener(ControllerPacket.RIGHT_FLAG));

        // Action buttons
        btnA.setOnTouchListener(new GamepadTouchListener(ControllerPacket.A_FLAG));
        btnB.setOnTouchListener(new GamepadTouchListener(ControllerPacket.B_FLAG));
        btnX.setOnTouchListener(new GamepadTouchListener(ControllerPacket.X_FLAG));
        btnY.setOnTouchListener(new GamepadTouchListener(ControllerPacket.Y_FLAG));

        // Shoulder buttons
        btnLB.setOnTouchListener(new GamepadTouchListener(ControllerPacket.LB_FLAG));
        btnLT.setOnTouchListener((v, event) -> {
            if (conn == null) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    leftTriggerValue = (byte) 0xFF;
                    sendControllerInput();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    leftTriggerValue = 0;
                    sendControllerInput();
                    return true;
            }
            return false;
        });
        btnRB.setOnTouchListener(new GamepadTouchListener(ControllerPacket.RB_FLAG));
        btnRT.setOnTouchListener((v, event) -> {
            if (conn == null) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    rightTriggerValue = (byte) 0xFF;
                    sendControllerInput();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    rightTriggerValue = 0;
                    sendControllerInput();
                    return true;
            }
            return false;
        });

        // Start/Select
        btnStart.setOnTouchListener(new GamepadTouchListener(ControllerPacket.PLAY_FLAG));
        btnSelect.setOnTouchListener(new GamepadTouchListener(ControllerPacket.BACK_FLAG));
    }

    private class GamepadTouchListener implements OnTouchListener {
        private int flag;

        public GamepadTouchListener(int flag) {
            this.flag = flag;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (conn == null) {
                return false;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    buttonFlags |= flag;
                    sendControllerInput();
                    return true;

                case MotionEvent.ACTION_UP:
                    buttonFlags &= ~flag;
                    sendControllerInput();
                    return true;
            }
            return false;
        }
    }

    private void sendControllerInput() {
        if (conn != null) {
            conn.sendControllerInput(
                controllerNumber,
                (short) 1, // activeGamepadMask
                buttonFlags, 
                leftTriggerValue, rightTriggerValue, // Triggers
                (short) 0, (short) 0, // Left stick
                (short) 0, (short) 0  // Right stick
            );
        }
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
