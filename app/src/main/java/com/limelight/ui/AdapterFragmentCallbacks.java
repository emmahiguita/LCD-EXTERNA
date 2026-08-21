package com.limelight.ui;

import android.widget.AbsListView;
import androidx.recyclerview.widget.RecyclerView;

public interface AdapterFragmentCallbacks {
    int getAdapterFragmentLayoutId();

    /**
     * Legacy callback para pantallas que siguen usando AbsListView (GridView).
     * Implementación por defecto vacía para no forzar a los implementadores
     * que ya migraron a RecyclerView.
     */
    default void receiveAbsListView(AbsListView gridView) {}

    /**
     * Nuevo callback para pantallas que usan RecyclerView.
     * Implementación por defecto vacía para compatibilidad con pantallas legacy.
     */
    default void receiveRecyclerView(RecyclerView recyclerView) {}
}
