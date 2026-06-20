package com.limelight.binding.input.gesture;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

public class NavigationGestureDetector {

    private static final float SWIPE_THRESHOLD = 100f;
    private static final float SWIPE_VELOCITY_THRESHOLD = 500f;

    private NvConnection conn;
    private GestureDetector gestureDetector;
    private Handler handler;

    public NavigationGestureDetector(NvConnection conn) {
        this.conn = conn;
        this.handler = new Handler(Looper.getMainLooper());
        this.gestureDetector = new GestureDetector(new NavigationGestureListener());
    }

    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    private class NavigationGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();

            if (Math.abs(diffX) > Math.abs(diffY)) {
                // Horizontal swipe
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // Swipe right - forward
                        sendNavigationKey((short) 0x004E); // Right Arrow (forward)
                    } else {
                        // Swipe left - back
                        sendNavigationKey((short) 0x000E); // Backspace (back)
                    }
                    return true;
                }
            } else {
                // Vertical swipe
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        // Swipe down - home
                        sendNavigationKey((short) 0x0032); // Home key
                    } else {
                        // Swipe up - multitask
                        sendNavigationKey((short) 0x003B); // F11 (often used for fullscreen/multitask)
                    }
                    return true;
                }
            }
            return false;
        }
    }

    private void sendNavigationKey(short keyCode) {
        if (conn != null) {
            // Send key down
            conn.sendKeyboardInput(keyCode, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
            // Send key up after a short delay using Handler to avoid blocking UI thread
            handler.postDelayed(() -> {
                if (conn != null) {
                    conn.sendKeyboardInput(keyCode, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
                }
            }, 50);
        }
    }
}
