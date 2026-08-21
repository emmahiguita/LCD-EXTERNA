package com.limelight.smartdisplay.profile;

/**
 * SmartDisplay AI – Touch Engine 2.0 (FASE 3)
 *
 * Estado activo del perfil de interacción, accesible de forma ligera desde la
 * capa de interpretación táctil (TouchContext) sin acoplarla a SharedPreferences
 * ni cambiar sus constructores.
 *
 * Los campos son volátiles porque se escriben desde el hilo UI (al seleccionar
 * perfil o al iniciar la sesión) y se leen desde el hilo de toques.
 *
 * Valor por defecto neutro (1.0): comportamiento idéntico al original mientras
 * no se aplique un perfil.
 */
public final class ActiveProfile {

    private static volatile InteractionProfile current = InteractionProfile.AUTO;
    private static volatile float pointerSensitivity = 1.0f;
    private static volatile float scrollSensitivity = 1.0f;
    private static volatile boolean naturalScroll = false;

    private ActiveProfile() {}

    public static void set(InteractionProfile profile) {
        if (profile == null) {
            profile = InteractionProfile.AUTO;
        }
        current = profile;
        pointerSensitivity = profile.getPointerSensitivity();
        scrollSensitivity = profile.getScrollSensitivity();
        naturalScroll = profile.isNaturalScroll();
    }

    public static InteractionProfile getCurrent()    { return current; }
    public static float   getPointerSensitivity()    { return pointerSensitivity; }
    public static float   getScrollSensitivity()     { return scrollSensitivity; }
    public static boolean isNaturalScroll()          { return naturalScroll; }
}
