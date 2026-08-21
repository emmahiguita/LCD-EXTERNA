package com.limelight.grid;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PcGridAdapter extends RecyclerView.Adapter<PcGridAdapter.PcViewHolder> {

    private final Context context;
    private final List<PcView.ComputerObject> itemList = new ArrayList<>();
    private OnItemClickListener listener;
    private OnItemOptionsClickListener optionsListener;

    public interface OnItemClickListener {
        void onItemClick(PcView.ComputerObject computer, View view);
    }

    public interface OnItemOptionsClickListener {
        void onOptionsClick(PcView.ComputerObject computer, View view);
    }

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        this.context = context;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
    
    public void setOnItemOptionsClickListener(OnItemOptionsClickListener listener) {
        this.optionsListener = listener;
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
        sortList();
        // Notify with the exact position after sorting to enable item animation
        int insertedAt = itemList.indexOf(computer);
        notifyItemInserted(insertedAt);
    }

    private void sortList() {
        Collections.sort(itemList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                return lhs.details.name.toLowerCase().compareTo(rhs.details.name.toLowerCase());
            }
        });
    }

    /**
     * Notifica el cambio de UN item concreto (en vez de notifyDataSetChanged).
     * Evita re-bindear toda la lista en cada actualización de polling.
     */
    public void notifyItemChangedFor(PcView.ComputerObject computer) {
        int idx = itemList.indexOf(computer);
        if (idx >= 0) {
            notifyItemChanged(idx);
        }
    }

    /**
     * Resuelve el nombre de la app activa parseando rawAppList.
     * DEBE llamarse FUERA del hilo UI (el parseo XML es costoso); el resultado
     * se cachea en ComputerObject.activeAppName para que bind() solo lea un String.
     * Devuelve null si no hay app activa o no se puede resolver.
     */
    public static String resolveActiveAppName(ComputerDetails details) {
        if (details == null || details.runningGameId == 0
                || details.state != ComputerDetails.State.ONLINE
                || details.rawAppList == null) {
            return null;
        }
        try {
            java.util.LinkedList<com.limelight.nvstream.http.NvApp> apps =
                    com.limelight.nvstream.http.NvHTTP.getAppListByReader(
                            new java.io.StringReader(details.rawAppList));
            for (com.limelight.nvstream.http.NvApp app : apps) {
                if (app.getAppId() == details.runningGameId) {
                    return app.getAppName();
                }
            }
        } catch (Exception e) {
            // Parseo fallido: bind() mostrará "En sesión"
        }
        return null;
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        int removedAt = itemList.indexOf(computer);
        boolean removed = itemList.remove(computer);
        if (removed) {
            notifyItemRemoved(removedAt);
        }
        return removed;
    }

    public int getCount() {
        return itemList.size();
    }

    public PcView.ComputerObject getItem(int position) {
        return itemList.get(position);
    }

    @NonNull
    @Override
    public PcViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.pc_grid_item, parent, false);
        return new PcViewHolder(view);
    }

    private int lastAnimatedPosition = -1;

    @Override
    public void onBindViewHolder(@NonNull PcViewHolder holder, int position) {
        PcView.ComputerObject obj = itemList.get(position);
        holder.bind(obj, listener, optionsListener);

        if (position > lastAnimatedPosition) {
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(48f);
            holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(360)
                    .setStartDelay(Math.min(position * 50L, 250L))
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .start();
            lastAnimatedPosition = position;
        }
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    @Override
    public void onViewRecycled(@NonNull PcViewHolder holder) {
        super.onViewRecycled(holder);
        // Evita animadores de pulso infinitos huérfanos acumulándose al reciclar.
        if (holder.pulseAnimator != null) {
            holder.pulseAnimator.cancel();
        }
    }

    public static class PcViewHolder extends RecyclerView.ViewHolder {
        ImageView statusBadge;
        View onlineIndicator;
        TextView gridText;
        TextView txtActiveApp;
        TextView gridIpText;
        TextView gridStatusText;
        ProgressBar gridSpinner;
        MaterialButton btnConnect;
        MaterialButton btnOptions;
        android.animation.Animator pulseAnimator;

        public PcViewHolder(@NonNull View itemView) {
            super(itemView);
            statusBadge = itemView.findViewById(R.id.status_badge);
            onlineIndicator = itemView.findViewById(R.id.online_indicator);
            gridText = itemView.findViewById(R.id.grid_text);
            txtActiveApp = itemView.findViewById(R.id.txt_active_app);
            gridIpText = itemView.findViewById(R.id.grid_ip_text);
            gridStatusText = itemView.findViewById(R.id.grid_status_text);
            gridSpinner = itemView.findViewById(R.id.grid_spinner);
            btnConnect = itemView.findViewById(R.id.btn_connect);
            btnOptions = itemView.findViewById(R.id.btn_options);
            
            if (onlineIndicator != null) {
                pulseAnimator = android.animation.AnimatorInflater.loadAnimator(itemView.getContext(), R.animator.pulse_dot_anim);
                pulseAnimator.setTarget(onlineIndicator);
            }
        }

        public void bind(final PcView.ComputerObject obj, final OnItemClickListener listener, final OnItemOptionsClickListener optionsListener) {
            gridText.setText(obj.details.name);
            gridIpText.setText(obj.details.activeAddress != null ? obj.details.activeAddress.address : "IP desconocida");

            if (txtActiveApp != null) {
                if (obj.details.runningGameId != 0 && obj.details.state == ComputerDetails.State.ONLINE) {
                    // Lee el nombre ya resuelto fuera del hilo UI (sin parsear XML aquí).
                    if (obj.activeAppName != null && !obj.activeAppName.isEmpty()) {
                        txtActiveApp.setText(itemView.getContext().getString(R.string.pcgrid_app_active, obj.activeAppName));
                    } else {
                        txtActiveApp.setText(R.string.pcgrid_session_active);
                    }
                    txtActiveApp.setVisibility(View.VISIBLE);
                } else {
                    txtActiveApp.setVisibility(View.GONE);
                }
            }

            // Handle State
            if (obj.details.state == ComputerDetails.State.ONLINE) {
                if (onlineIndicator != null) {
                    onlineIndicator.setVisibility(View.VISIBLE);
                    onlineIndicator.setBackgroundResource(R.drawable.online_dot);
                    if (pulseAnimator != null && !pulseAnimator.isRunning()) {
                        pulseAnimator.start();
                    }
                }
                statusBadge.setColorFilter(Color.parseColor("#00C853")); // Online
                if (obj.details.pairState == PairingManager.PairState.PAIRED) {
                    gridStatusText.setText("Listo para conectar");
                } else {
                    gridStatusText.setText("Requiere emparejamiento");
                    statusBadge.setColorFilter(Color.parseColor("#FFB300")); // Pairing
                }
                gridText.setAlpha(1.0f);
            } else if (obj.details.state == ComputerDetails.State.UNKNOWN) {
                if (onlineIndicator != null) {
                    if (pulseAnimator != null) pulseAnimator.cancel();
                    onlineIndicator.setVisibility(View.INVISIBLE);
                }
                statusBadge.setColorFilter(Color.parseColor("#78909C"));
                gridStatusText.setText("Actualizando...");
                gridText.setAlpha(0.6f);
            } else {
                if (onlineIndicator != null) {
                    if (pulseAnimator != null) pulseAnimator.cancel();
                    onlineIndicator.setVisibility(View.INVISIBLE);
                }
                statusBadge.setColorFilter(Color.parseColor("#EF5350")); // Offline
                gridStatusText.setText("Sin conexión");
                gridText.setAlpha(0.6f);
            }

            if (obj.details.state == ComputerDetails.State.UNKNOWN) {
                gridSpinner.setVisibility(View.VISIBLE);
            } else {
                gridSpinner.setVisibility(View.INVISIBLE);
            }

            btnConnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onItemClick(obj, v);
                }
            });

            btnOptions.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (optionsListener != null) optionsListener.onOptionsClick(obj, v);
                }
            });
            
            // Allow whole card click as well
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onItemClick(obj, v);
                }
            });
            
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (optionsListener != null) optionsListener.onOptionsClick(obj, v);
                    return true;
                }
            });
        }
    }
}
