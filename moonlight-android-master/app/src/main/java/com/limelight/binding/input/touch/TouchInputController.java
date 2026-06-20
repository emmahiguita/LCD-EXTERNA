package com.limelight.binding.input.touch;

import android.view.MotionEvent;

public class TouchInputController {

    private boolean isDragging = false;
    private float lastX, lastY;

    public interface Callback {
        void onMouseMove(int x, int y);
        void onClick();
        void onRightClick();
        void onScroll(int delta);
    }

    private Callback callback;

    public TouchInputController(Callback callback) {
        this.callback = callback;
    }

    public boolean handleTouch(float x, float y, int action) {
        if (callback == null) {
            return false;
        }

        switch (action) {

            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                lastX = x;
                lastY = y;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    int dx = (int)(x - lastX);
                    int dy = (int)(y - lastY);

                    callback.onMouseMove(dx, dy);

                    lastX = x;
                    lastY = y;
                }
                return true;

            case MotionEvent.ACTION_UP:
                isDragging = false;
                callback.onClick();
                return true;
        }

        return false;
    }
}
