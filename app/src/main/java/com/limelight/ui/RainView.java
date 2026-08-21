package com.limelight.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Animación de lluvia ligera dibujada sobre el fondo. Pensada solo para la
 * pantalla principal cuando el tema "Lluvia" está activo: NO se usa durante el
 * streaming (la PC es la protagonista). Se detiene sola al desprenderse de la
 * ventana para no consumir batería.
 */
public class RainView extends View {

    private static final int DROP_COUNT = 90;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    private float[] x;
    private float[] y;
    private float[] speed;
    private float[] length;
    private boolean initialized;
    private boolean running;

    public RainView(Context context) {
        super(context);
        init();
    }

    public RainView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RainView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        // Azul claro translúcido acorde al tema Lluvia.
        paint.setColor(Color.argb(120, 125, 211, 252));
        paint.setStrokeWidth(dp(1.4f));
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private void seed(int w, int h) {
        x = new float[DROP_COUNT];
        y = new float[DROP_COUNT];
        speed = new float[DROP_COUNT];
        length = new float[DROP_COUNT];
        for (int i = 0; i < DROP_COUNT; i++) {
            respawn(i, w, h, true);
        }
        initialized = true;
    }

    private void respawn(int i, int w, int h, boolean anywhere) {
        x[i] = random.nextFloat() * w;
        y[i] = anywhere ? random.nextFloat() * h : -random.nextFloat() * dp(60);
        speed[i] = dp(9) + random.nextFloat() * dp(16);
        length[i] = dp(10) + random.nextFloat() * dp(22);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            seed(w, h);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!initialized || !running) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        for (int i = 0; i < DROP_COUNT; i++) {
            canvas.drawLine(x[i], y[i], x[i], y[i] + length[i], paint);
            y[i] += speed[i];
            if (y[i] > h) {
                respawn(i, w, h, false);
            }
        }
        postInvalidateOnAnimation();
    }

    /** Inicia la animación (idempotente). */
    public void start() {
        if (!running) {
            running = true;
            postInvalidateOnAnimation();
        }
    }

    /** Detiene la animación. */
    public void stop() {
        running = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
