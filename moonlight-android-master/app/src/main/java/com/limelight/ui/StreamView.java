package com.limelight.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceView;
import android.view.View;

public class StreamView extends SurfaceView {
    private double desiredAspectRatio;
    private InputCallbacks inputCallbacks;

    // ── Pan / Zoom (free-viewport "MOVER" mode) ──────────────────────────────
    private ScaleGestureDetector scaleDetector;
    private float curScale  = 1f;
    private float curTransX = 0f;
    private float curTransY = 0f;
    private float lastFocusX, lastFocusY;
    private PanZoomListener panZoomListener;

    // The view the pan/zoom transform is applied to. MUST be the parent wrapper,
    // NOT the SurfaceView itself (transforming a SurfaceView tears down its
    // surface → flicker + dropped stream). Falls back to `this` if never set.
    private View transformTarget;

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 4.0f;

    /** Reports the live zoom factor back to the overlay badge. */
    public interface PanZoomListener {
        void onZoomChanged(float scale);
    }

    public void setPanZoomListener(PanZoomListener l) {
        this.panZoomListener = l;
    }

    /** Set the view to transform for pan/zoom (the FrameLayout wrapping this SurfaceView). */
    public void setTransformTarget(View target) {
        this.transformTarget = target;
    }

    private View getTransformTarget() {
        return transformTarget != null ? transformTarget : this;
    }

    public void setDesiredAspectRatio(double aspectRatio) {
        this.desiredAspectRatio = aspectRatio;
    }

    public void setInputCallbacks(InputCallbacks callbacks) {
        this.inputCallbacks = callbacks;
    }

    public StreamView(Context context) {
        super(context);
    }

    public StreamView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StreamView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StreamView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    // ── Pan / Zoom implementation ────────────────────────────────────────────

    private void ensureScaleDetector() {
        if (scaleDetector != null) return;
        scaleDetector = new ScaleGestureDetector(getContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        curScale *= detector.getScaleFactor();
                        curScale = Math.max(MIN_SCALE, Math.min(curScale, MAX_SCALE));
                        clampTranslation();
                        applyTransform();
                        if (panZoomListener != null) {
                            panZoomListener.onZoomChanged(curScale);
                        }
                        return true;
                    }
                });
    }

    private final android.os.Handler tapHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int tapCount = 0;
    private long lastTapTime = 0;
    private float lastTapX = 0f;
    private float lastTapY = 0f;
    private final Runnable tapRunnable = new Runnable() {
        @Override
        public void run() {
            if (tapCount == 2) {
                resetPanZoom();
            }
            tapCount = 0;
        }
    };

    private void performTripleTapZoom(float touchX, float touchY) {
        curScale = 1.0f;
        curTransX = (getWidth() / 2.0f) - touchX;
        curTransY = (getHeight() / 2.0f) - touchY;
        clampTranslation();
        applyTransform();
        if (panZoomListener != null) {
            panZoomListener.onZoomChanged(curScale);
        }
    }

    /**
     * Handle a touch event as pan + pinch-zoom on the video surface.
     * Called by Game.java ONLY while "MOVER" (hand) mode is enabled, so it never
     * interferes with normal touch-to-mouse input. Always consumes the event.
     */
    public boolean handlePanZoomTouch(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            long now = System.currentTimeMillis();
            if (now - lastTapTime < 300) {
                tapCount++;
            } else {
                tapCount = 1;
            }
            lastTapTime = now;
            lastTapX = event.getX();
            lastTapY = event.getY();

            tapHandler.removeCallbacks(tapRunnable);

            if (tapCount == 3) {
                performTripleTapZoom(lastTapX, lastTapY);
                tapCount = 0;
                return true;
            } else {
                tapHandler.postDelayed(tapRunnable, 250);
            }
        }

        ensureScaleDetector();
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP: {
                float[] focus = computeFocus(event,
                        event.getActionMasked() == MotionEvent.ACTION_POINTER_UP
                                ? event.getActionIndex() : -1);
                lastFocusX = focus[0];
                lastFocusY = focus[1];
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                float[] focus = computeFocus(event, -1);
                // Pan by how far the gesture centroid moved (works for 1 or 2 fingers).
                curTransX += focus[0] - lastFocusX;
                curTransY += focus[1] - lastFocusY;
                lastFocusX = focus[0];
                lastFocusY = focus[1];
                clampTranslation();
                applyTransform();
                break;
            }
            default:
                break;
        }
        return true;
    }


    /** Average position of active pointers, optionally skipping one (a finger lifting). */
    private float[] computeFocus(MotionEvent event, int skipIndex) {
        float sumX = 0f, sumY = 0f;
        int count = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == skipIndex) continue;
            sumX += event.getX(i);
            sumY += event.getY(i);
            count++;
        }
        if (count == 0) return new float[] { lastFocusX, lastFocusY };
        return new float[] { sumX / count, sumY / count };
    }

    /** Keep translation within bounds but allow the video to be shifted almost completely off-screen for maximum mobility. */
    private void clampTranslation() {
        float maxX = curScale * getWidth() * 0.8f;
        float maxY = curScale * getHeight() * 0.8f;
        curTransX = Math.max(-maxX, Math.min(curTransX, maxX));
        curTransY = Math.max(-maxY, Math.min(curTransY, maxY));
    }

    private void applyTransform() {
        // Apply to the wrapper FrameLayout (NOT the SurfaceView), with the centre
        // as pivot so clampTranslation()'s symmetric bounds are correct.
        View t = getTransformTarget();
        t.setPivotX(t.getWidth()  / 2f);
        t.setPivotY(t.getHeight() / 2f);
        t.setScaleX(curScale);
        t.setScaleY(curScale);
        t.setTranslationX(curTransX);
        t.setTranslationY(curTransY);
    }

    /** Snap back to a 1:1 un-panned view. Called when leaving MOVER mode or on ZOOM reset. */
    public void resetPanZoom() {
        curScale  = 1f;
        curTransX = 0f;
        curTransY = 0f;
        applyTransform();
        if (panZoomListener != null) {
            panZoomListener.onZoomChanged(curScale);
        }
    }

    public float getCurrentScale() {
        return curScale;
    }

    public float getCurrentTranslationX() {
        return curTransX;
    }

    public float getCurrentTranslationY() {
        return curTransY;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clampTranslation();
        applyTransform();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // If no fixed aspect ratio has been provided, simply use the default onMeasure() behavior
        if (desiredAspectRatio == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Based on code from: https://www.buzzingandroid.com/2012/11/easy-measuring-of-custom-views-with-specific-aspect-ratio/
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int measuredHeight, measuredWidth;
        if (widthSize > heightSize * desiredAspectRatio) {
            measuredHeight = heightSize;
            measuredWidth = (int)(measuredHeight * desiredAspectRatio);
        } else {
            measuredWidth = widthSize;
            measuredHeight = (int)(measuredWidth / desiredAspectRatio);
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        // This callbacks allows us to override dumb IME behavior like when
        // Samsung's default keyboard consumes Shift+Space.
        if (inputCallbacks != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (inputCallbacks.handleKeyDown(event)) {
                    return true;
                }
            }
            else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (inputCallbacks.handleKeyUp(event)) {
                    return true;
                }
            }
        }

        return super.onKeyPreIme(keyCode, event);
    }

    public interface InputCallbacks {
        boolean handleKeyUp(KeyEvent event);
        boolean handleKeyDown(KeyEvent event);
    }
}
