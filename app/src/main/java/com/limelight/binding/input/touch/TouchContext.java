package com.limelight.binding.input.touch;

public interface TouchContext {
    int getActionIndex();
    void setPointerCount(int pointerCount);
    boolean touchDownEvent(int eventX, int eventY, long eventTime, boolean isNewFinger);
    boolean touchMoveEvent(int eventX, int eventY, long eventTime);
    void touchUpEvent(int eventX, int eventY, long eventTime);
    void cancelTouch();
    boolean isCancelled();
    
    interface OnRightClickListener {
        void onRightClick(float x, float y);
    }
    void setOnRightClickListener(OnRightClickListener listener);

    interface OnLeftClickListener {
        void onLeftClick(float x, float y);
    }
    void setOnLeftClickListener(OnLeftClickListener listener);
}
