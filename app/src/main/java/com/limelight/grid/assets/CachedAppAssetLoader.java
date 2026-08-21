package com.limelight.grid.assets;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CachedAppAssetLoader {
    private static final int MAX_CONCURRENT_DISK_LOADS = 3;
    private static final int MAX_CONCURRENT_NETWORK_LOADS = 3;
    private static final int MAX_CONCURRENT_CACHE_LOADS = 1;

    private static final int MAX_PENDING_CACHE_LOADS = 100;
    private static final int MAX_PENDING_NETWORK_LOADS = 40;
    private static final int MAX_PENDING_DISK_LOADS = 40;

    // Ejecutor compartido para posts al hilo principal
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final ThreadPoolExecutor cacheExecutor = new ThreadPoolExecutor(
            MAX_CONCURRENT_CACHE_LOADS, MAX_CONCURRENT_CACHE_LOADS,
            Long.MAX_VALUE, TimeUnit.DAYS,
            new LinkedBlockingQueue<Runnable>(MAX_PENDING_CACHE_LOADS),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private final ThreadPoolExecutor foregroundExecutor = new ThreadPoolExecutor(
            MAX_CONCURRENT_DISK_LOADS, MAX_CONCURRENT_DISK_LOADS,
            Long.MAX_VALUE, TimeUnit.DAYS,
            new LinkedBlockingQueue<Runnable>(MAX_PENDING_DISK_LOADS),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private final ThreadPoolExecutor networkExecutor = new ThreadPoolExecutor(
            MAX_CONCURRENT_NETWORK_LOADS, MAX_CONCURRENT_NETWORK_LOADS,
            Long.MAX_VALUE, TimeUnit.DAYS,
            new LinkedBlockingQueue<Runnable>(MAX_PENDING_NETWORK_LOADS),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private final ComputerDetails computer;
    private final double scalingDivider;
    private final NetworkAssetLoader networkLoader;
    private final MemoryAssetLoader memoryLoader;
    private final DiskAssetLoader diskLoader;
    private final Bitmap placeholderBitmap;
    private final Bitmap noAppImageBitmap;

    public CachedAppAssetLoader(ComputerDetails computer, double scalingDivider,
                                NetworkAssetLoader networkLoader, MemoryAssetLoader memoryLoader,
                                DiskAssetLoader diskLoader, Bitmap noAppImageBitmap) {
        this.computer = computer;
        this.scalingDivider = scalingDivider;
        this.networkLoader = networkLoader;
        this.memoryLoader = memoryLoader;
        this.diskLoader = diskLoader;
        this.noAppImageBitmap = noAppImageBitmap;
        this.placeholderBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    }

    public void cancelBackgroundLoads() {
        Runnable r;
        while ((r = cacheExecutor.getQueue().poll()) != null) {
            cacheExecutor.remove(r);
        }
    }

    public void cancelForegroundLoads() {
        Runnable r;

        while ((r = foregroundExecutor.getQueue().poll()) != null) {
            foregroundExecutor.remove(r);
        }

        while ((r = networkExecutor.getQueue().poll()) != null) {
            networkExecutor.remove(r);
        }
    }

    /**
     * Terminates all executor pools immediately.
     * Must be called when this loader instance is being discarded to prevent
     * thread pool leaks when the adapter creates a new CachedAppAssetLoader.
     */
    public void shutdown() {
        cacheExecutor.shutdownNow();
        foregroundExecutor.shutdownNow();
        networkExecutor.shutdownNow();
    }

    public void freeCacheMemory() {
        memoryLoader.clearCache();
    }

    private ScaledBitmap doNetworkAssetLoad(LoaderTuple tuple, AtomicBoolean cancelled) {
        // Try 3 times
        for (int i = 0; i < 3; i++) {
            // Check whether we've been cancelled
            if (cancelled != null && cancelled.get()) {
                return null;
            }

            InputStream in = networkLoader.getBitmapStream(tuple);
            if (in != null) {
                // Write the stream straight to disk
                diskLoader.populateCacheWithStream(tuple, in);

                // Close the network input stream
                try {
                    in.close();
                } catch (IOException ignored) {}

                // If there's a task associated with this load, we should return the bitmap
                if (cancelled != null) {
                    // If the cached bitmap is valid, return it. Otherwise, we'll try the load again
                    ScaledBitmap bmp = diskLoader.loadBitmapFromCache(tuple, (int) scalingDivider);
                    if (bmp != null) {
                        return bmp;
                    }
                } else {
                    // Otherwise it's a background load and we return nothing
                    return null;
                }
            }

            // Wait 1 second with a bit of fuzz
            try {
                Thread.sleep((int) (1000 + (Math.random() * 500)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        return null;
    }

    /**
     * Sustituto de AsyncTask<LoaderTuple, Void, ScaledBitmap>.
     *
     * Toda la lógica de carga corre en el ThreadPoolExecutor apropiado.
     * Los callbacks de UI se despachan vía MAIN_HANDLER.post() en lugar de
     * onProgressUpdate/onPostExecute, eliminando la dependencia de AsyncTask.
     */
    private class LoaderTask implements Runnable {
        final WeakReference<ImageView> imageViewRef;
        final WeakReference<TextView>  textViewRef;
        final boolean diskOnly;
        final LoaderTuple tuple;
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        LoaderTask(ImageView imageView, TextView textView, boolean diskOnly, LoaderTuple tuple) {
            this.imageViewRef = new WeakReference<>(imageView);
            this.textViewRef  = new WeakReference<>(textView);
            this.diskOnly     = diskOnly;
            this.tuple        = tuple;
        }

        void cancel() {
            cancelled.set(true);
        }

        boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public void run() {
            if (isCancelled() || imageViewRef.get() == null || textViewRef.get() == null) {
                return;
            }

            ScaledBitmap bmp = diskLoader.loadBitmapFromCache(tuple, (int) scalingDivider);
            if (bmp == null) {
                if (!diskOnly) {
                    bmp = doNetworkAssetLoad(tuple, cancelled);
                } else {
                    // Bitmap not on disk yet — dispatch a network load task and show placeholder
                    dispatchNetworkFallback();
                    return;
                }
            }

            // Cache the bitmap in memory
            if (bmp != null) {
                memoryLoader.populateCache(tuple, bmp);
            }

            // Dispatch UI update to main thread
            final ScaledBitmap finalBmp = bmp;
            MAIN_HANDLER.post(() -> onLoadComplete(finalBmp));
        }

        /** Called when disk cache misses: shows placeholder and spawns a network task. */
        private void dispatchNetworkFallback() {
            MAIN_HANDLER.post(() -> {
                if (isCancelled()) return;
                final ImageView imageView = imageViewRef.get();
                final TextView  textView  = textViewRef.get();
                if (getLoaderTask(imageView) != this) return;

                // Create and attach the network-capable task
                LoaderTask networkTask = new LoaderTask(imageView, textView, false, tuple);
                AsyncDrawable asyncDrawable = new AsyncDrawable(
                        imageView.getResources(), noAppImageBitmap, networkTask);
                imageView.setImageDrawable(asyncDrawable);
                imageView.startAnimation(AnimationUtils.loadAnimation(
                        imageView.getContext(), R.anim.boxart_fadein));
                imageView.setVisibility(View.VISIBLE);
                textView.setVisibility(View.VISIBLE);
                networkExecutor.execute(networkTask);
            });
        }

        /** UI callback equivalent to onPostExecute. Must be called on the main thread. */
        private void onLoadComplete(final ScaledBitmap bitmap) {
            if (isCancelled()) return;

            final ImageView imageView = imageViewRef.get();
            final TextView  textView  = textViewRef.get();
            if (getLoaderTask(imageView) != this) return;

            if (bitmap != null) {
                textView.setVisibility(isBitmapPlaceholder(bitmap) ? View.VISIBLE : View.GONE);

                if (imageView.getVisibility() == View.VISIBLE) {
                    // Fade out the old image, then fade in the new one
                    Animation fadeOut = AnimationUtils.loadAnimation(
                            imageView.getContext(), R.anim.boxart_fadeout);
                    fadeOut.setAnimationListener(new Animation.AnimationListener() {
                        @Override public void onAnimationStart(Animation a) {}
                        @Override public void onAnimationRepeat(Animation a) {}
                        @Override
                        public void onAnimationEnd(Animation a) {
                            imageView.setImageBitmap(bitmap.bitmap);
                            imageView.startAnimation(AnimationUtils.loadAnimation(
                                    imageView.getContext(), R.anim.boxart_fadein));
                        }
                    });
                    imageView.startAnimation(fadeOut);
                } else {
                    imageView.setImageBitmap(bitmap.bitmap);
                    imageView.startAnimation(AnimationUtils.loadAnimation(
                            imageView.getContext(), R.anim.boxart_fadein));
                    imageView.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    // ── AsyncDrawable: guarda referencia débil al LoaderTask activo ───────────
    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<LoaderTask> loaderTaskReference;

        public AsyncDrawable(android.content.res.Resources res, Bitmap bitmap,
                             LoaderTask loaderTask) {
            super(res, bitmap);
            loaderTaskReference = new WeakReference<>(loaderTask);
        }

        public LoaderTask getLoaderTask() {
            return loaderTaskReference.get();
        }
    }

    private static LoaderTask getLoaderTask(ImageView imageView) {
        if (imageView == null) return null;
        final Drawable drawable = imageView.getDrawable();
        if (drawable instanceof AsyncDrawable) {
            return ((AsyncDrawable) drawable).getLoaderTask();
        }
        return null;
    }

    private static boolean cancelPendingLoad(LoaderTuple tuple, ImageView imageView) {
        final LoaderTask loaderTask = getLoaderTask(imageView);
        if (loaderTask != null && !loaderTask.isCancelled()) {
            final LoaderTuple taskTuple = loaderTask.tuple;
            if (taskTuple == null || !taskTuple.equals(tuple)) {
                loaderTask.cancel();
            } else {
                // Already loading what we want
                return false;
            }
        }
        return true;
    }

    public void queueCacheLoad(NvApp app) {
        final LoaderTuple tuple = new LoaderTuple(computer, app);

        if (memoryLoader.loadBitmapFromCache(tuple) != null) {
            // It's in memory which means it must also be on disk
            return;
        }

        // Queue a fetch in the cache executor
        cacheExecutor.execute(() -> {
            if (diskLoader.checkCacheExists(tuple)) {
                return;
            }
            // Try to load the asset from the network and cache result on disk
            doNetworkAssetLoad(tuple, null);
        });
    }

    private boolean isBitmapPlaceholder(ScaledBitmap bitmap) {
        return (bitmap == null) ||
                (bitmap.originalWidth == 130 && bitmap.originalHeight == 180) || // GFE 2.0
                (bitmap.originalWidth == 628 && bitmap.originalHeight == 888);    // GFE 3.0
    }

    public boolean populateImageView(NvApp app, ImageView imgView, TextView textView) {
        LoaderTuple tuple = new LoaderTuple(computer, app);

        // Cancel any pending task for this view (unless it's already loading the same image)
        if (!cancelPendingLoad(tuple, imgView)) {
            return true;
        }

        // Always set the app name text so it's available if needed later
        textView.setText(app.getAppName());

        // Fast path: hit the memory cache
        ScaledBitmap bmp = memoryLoader.loadBitmapFromCache(tuple);
        if (bmp != null) {
            imgView.setVisibility(View.VISIBLE);
            imgView.setImageBitmap(bmp.bitmap);
            textView.setVisibility(isBitmapPlaceholder(bmp) ? View.VISIBLE : View.GONE);
            return true;
        }

        // Slow path: submit a disk-first task, attaching it via AsyncDrawable
        final LoaderTask task = new LoaderTask(imgView, textView, true, tuple);
        final AsyncDrawable asyncDrawable = new AsyncDrawable(
                imgView.getResources(), placeholderBitmap, task);
        textView.setVisibility(View.INVISIBLE);
        imgView.setVisibility(View.INVISIBLE);
        imgView.setImageDrawable(asyncDrawable);

        foregroundExecutor.execute(task);
        return false;
    }

    // ── LoaderTuple ──────────────────────────────────────────────────────────
    public static class LoaderTuple {
        public final ComputerDetails computer;
        public final NvApp app;

        public LoaderTuple(ComputerDetails computer, NvApp app) {
            this.computer = computer;
            this.app = app;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LoaderTuple)) return false;
            LoaderTuple other = (LoaderTuple) o;
            return computer.uuid.equals(other.computer.uuid)
                    && app.getAppId() == other.app.getAppId();
        }

        @Override
        public String toString() {
            return "(" + computer.uuid + ", " + app.getAppId() + ")";
        }
    }
}
