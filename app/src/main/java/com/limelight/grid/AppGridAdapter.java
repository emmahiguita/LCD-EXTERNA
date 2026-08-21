package com.limelight.grid;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.AppView;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.grid.assets.MemoryAssetLoader;
import com.limelight.grid.assets.NetworkAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppGridAdapter extends RecyclerView.Adapter<AppGridAdapter.AppViewHolder> {

    private static final int ART_WIDTH_PX  = 300;
    private static final int SMALL_WIDTH_DP = 100;
    private static final int LARGE_WIDTH_DP = 150;

    // ── Datos ─────────────────────────────────────────────────────────────────
    /** Lista visible (filtrada por isHidden) */
    final ArrayList<AppView.AppObject> itemList = new ArrayList<>();
    /** Lista completa incluyendo ocultas (fuente de verdad) */
    private final ArrayList<AppView.AppObject> allApps = new ArrayList<>();

    // ── Contexto y config ─────────────────────────────────────────────────────
    private final Context context;
    private final ComputerDetails computer;
    private final String uniqueId;
    private final boolean showHiddenApps;
    private Set<Integer> hiddenAppIds = new HashSet<>();

    // ── Cargador de carátulas ─────────────────────────────────────────────────
    private CachedAppAssetLoader loader;

    // ── Layout ID activo ──────────────────────────────────────────────────────
    private int layoutId;

    // ── Listener de clicks ────────────────────────────────────────────────────
    private OnAppClickListener clickListener;
    private OnAppLongClickListener longClickListener;

    public interface OnAppClickListener {
        void onAppClick(AppView.AppObject app, View itemView);
    }

    public interface OnAppLongClickListener {
        boolean onAppLongClick(AppView.AppObject app, View itemView);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CONSTRUCTOR
    // ─────────────────────────────────────────────────────────────────────────
    public AppGridAdapter(Context context, PreferenceConfiguration prefs,
                          ComputerDetails computer, String uniqueId, boolean showHiddenApps) {
        this.context       = context;
        this.computer      = computer;
        this.uniqueId      = uniqueId;
        this.showHiddenApps = showHiddenApps;
        this.layoutId      = getLayoutIdForPreferences(prefs);
        setHasStableIds(true);
        updateLayoutWithPreferences(context, prefs);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CALLBACKS PÚBLICOS
    // ─────────────────────────────────────────────────────────────────────────
    public void setOnAppClickListener(OnAppClickListener l)         { this.clickListener     = l; }
    public void setOnAppLongClickListener(OnAppLongClickListener l) { this.longClickListener = l; }

    // ─────────────────────────────────────────────────────────────────────────
    //  LAYOUT / PREFERENCIAS
    // ─────────────────────────────────────────────────────────────────────────
    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        return prefs.smallIconMode ? R.layout.app_grid_item_small : R.layout.app_grid_item;
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        int dpi = context.getResources().getDisplayMetrics().densityDpi;
        int dp  = prefs.smallIconMode ? SMALL_WIDTH_DP : LARGE_WIDTH_DP;

        double scalingDivisor = ART_WIDTH_PX / (dp * (dpi / 160.0));
        if (scalingDivisor < 1.0) scalingDivisor = 1.0;
        LimeLog.info("Art scaling divisor: " + scalingDivisor);

        if (loader != null) {
            cancelQueuedOperations();
        }

        this.loader = new CachedAppAssetLoader(computer, scalingDivisor,
                new NetworkAssetLoader(context, uniqueId),
                new MemoryAssetLoader(),
                new DiskAssetLoader(context),
                BitmapFactory.decodeResource(context.getResources(), R.drawable.no_app_image));

        int newLayoutId = getLayoutIdForPreferences(prefs);
        if (newLayoutId != this.layoutId) {
            this.layoutId = newLayoutId;
            notifyDataSetChanged(); // layout type changed: full rebind needed
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GESTIÓN DE DATOS
    // ─────────────────────────────────────────────────────────────────────────
    public void updateHiddenApps(Set<Integer> newHiddenAppIds, boolean hideImmediately) {
        this.hiddenAppIds.clear();
        this.hiddenAppIds.addAll(newHiddenAppIds);

        // Snapshot isHidden into each object BEFORE building the new list,
        // so that DiffUtil compares stable (old vs new) values.
        for (AppView.AppObject app : allApps) {
            app.isHidden = hiddenAppIds.contains(app.app.getAppId());
        }

        // Always compute via diff to handle both hide-immediately and lazy paths.
        // This avoids the mutable-object bug where the "old" list already
        // reflected new values before dispatchDiff ran.
        List<AppView.AppObject> newVisible = new ArrayList<>();
        for (AppView.AppObject app : allApps) {
            if (!app.isHidden || showHiddenApps) {
                newVisible.add(app);
            }
        }
        if (hideImmediately) {
            dispatchDiff(newVisible);
        } else {
            // Lazy path: still update the visible list properly via diff
            // so no items are left dangling after hide toggling.
            dispatchDiff(newVisible);
        }
    }

    public void addApp(AppView.AppObject app) {
        app.isHidden = hiddenAppIds.contains(app.app.getAppId());
        allApps.add(app);
        sortList(allApps);

        if (showHiddenApps || !app.isHidden) {
            loader.queueCacheLoad(app.app);
            // Build the new sorted visible list and dispatch a full diff.
            // Using notifyItemInserted(pos) after sortList() would leave all
            // other items that shifted position unnotified, causing visual
            // glitches in RecyclerView. dispatchDiff handles moves correctly.
            List<AppView.AppObject> newVisible = new ArrayList<>();
            for (AppView.AppObject o : allApps) {
                if (!o.isHidden || showHiddenApps) {
                    newVisible.add(o);
                }
            }
            dispatchDiff(newVisible);
        }
    }

    public void removeApp(AppView.AppObject app) {
        int visPos = itemList.indexOf(app);
        if (visPos >= 0) {
            itemList.remove(visPos);
            notifyItemRemoved(visPos);
        }
        allApps.remove(app);
    }

    public void clear() {
        int oldSize = itemList.size();
        itemList.clear();
        allApps.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
    }

    // ── Compatibilidad con AppView que llama getItem(pos) y getCount() ────────
    public int getCount() {
        return itemList.size();
    }

    public AppView.AppObject getItem(int position) {
        return itemList.get(position);
    }

    // ── DiffUtil helper ───────────────────────────────────────────────────────
    private void dispatchDiff(final List<AppView.AppObject> newList) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return itemList.size(); }
            @Override public int getNewListSize() { return newList.size();  }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return itemList.get(oldPos).app.getAppId() == newList.get(newPos).app.getAppId();
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                AppView.AppObject o = itemList.get(oldPos);
                AppView.AppObject n = newList.get(newPos);
                return o.isRunning == n.isRunning
                        && o.isHidden == n.isHidden
                        && o.app.getAppName().equals(n.app.getAppName());
            }
        });
        itemList.clear();
        itemList.addAll(newList);
        result.dispatchUpdatesTo(this);
    }

    private static void sortList(List<AppView.AppObject> list) {
        Collections.sort(list, new Comparator<AppView.AppObject>() {
            @Override
            public int compare(AppView.AppObject lhs, AppView.AppObject rhs) {
                return lhs.app.getAppName().toLowerCase()
                        .compareTo(rhs.app.getAppName().toLowerCase());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LOADER
    // ─────────────────────────────────────────────────────────────────────────
    public void cancelQueuedOperations() {
        loader.cancelForegroundLoads();
        loader.cancelBackgroundLoads();
        loader.freeCacheMemory();
        // Terminate the executor pools so their threads don't leak
        // when this loader instance is replaced (e.g. after a preference change).
        loader.shutdown();
    }

    /**
     * Re-dispatches a DiffUtil update from the current allApps list.
     * Called by AppView after individual removeApp() calls so that
     * RecyclerView gets precise change events instead of a full rebind.
     */
    public void refreshVisibleList() {
        List<AppView.AppObject> newVisible = new ArrayList<>();
        for (AppView.AppObject app : allApps) {
            if (!app.isHidden || showHiddenApps) {
                newVisible.add(app);
            }
        }
        dispatchDiff(newVisible);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RecyclerView.Adapter overrides
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public long getItemId(int position) {
        return itemList.get(position).app.getAppId();
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new AppViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppView.AppObject obj = itemList.get(position);

        // Load box art
        loader.populateImageView(obj.app, holder.imgView, holder.txtView);

        // Running overlay
        if (obj.isRunning) {
            holder.overlayView.setImageResource(R.drawable.ic_play);
            holder.overlayView.setVisibility(View.VISIBLE);
        } else {
            holder.overlayView.setVisibility(View.GONE);
        }

        // Hidden state: dim the card
        holder.itemView.setAlpha(obj.isHidden ? 0.40f : 1.0f);

        // Clicks
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onAppClick(obj, v);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) return longClickListener.onAppLongClick(obj, v);
            return false;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VIEW HOLDER
    // ─────────────────────────────────────────────────────────────────────────
    public static class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView  imgView;
        final ImageView  overlayView;
        final TextView   txtView;
        final ProgressBar prgView;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            imgView     = itemView.findViewById(R.id.grid_image);
            overlayView = itemView.findViewById(R.id.grid_overlay);
            txtView     = itemView.findViewById(R.id.grid_text);
            prgView     = itemView.findViewById(R.id.grid_spinner);
        }
    }
}
