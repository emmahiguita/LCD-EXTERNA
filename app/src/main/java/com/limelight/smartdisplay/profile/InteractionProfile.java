package com.limelight.smartdisplay.profile;

/**
 * SmartDisplay AI – Touch Engine 2.0 (FASE 3)
 *
 * Perfiles de interacción seleccionables desde los "Quick Actions" de la
 * pantalla principal. Cada perfil ajusta parámetros de la capa de interpretación
 * táctil (por encima del Input Base Engine / protocolo, que NO se tocan).
 *
 * Los valores por defecto del perfil {@link #AUTO} reproducen exactamente el
 * comportamiento actual (sensibilidad 1.0), de modo que activar el sistema no
 * cambia nada hasta que el usuario elige un perfil concreto.
 */
public enum InteractionProfile {

    //          id            sensibilidad  scroll   naturalScroll
    AUTO        ("auto",        1.00f,       1.0f,    false),
    GAMING      ("gaming",      1.35f,       1.0f,    false),
    PRODUCTIVITY("productivity",1.00f,       1.2f,    false),
    MEDIA       ("media",       0.85f,       1.0f,    true),
    OFFICE      ("office",      1.10f,       1.3f,    false);

    private final String id;
    private final float pointerSensitivity;
    private final float scrollSensitivity;
    private final boolean naturalScroll;

    InteractionProfile(String id, float pointerSensitivity,
                       float scrollSensitivity, boolean naturalScroll) {
        this.id = id;
        this.pointerSensitivity = pointerSensitivity;
        this.scrollSensitivity = scrollSensitivity;
        this.naturalScroll = naturalScroll;
    }

    public String getId()                { return id; }
    public float  getPointerSensitivity(){ return pointerSensitivity; }
    public float  getScrollSensitivity() { return scrollSensitivity; }
    public boolean isNaturalScroll()     { return naturalScroll; }

    /** Resuelve un perfil por su id persistido; devuelve {@link #AUTO} si no coincide. */
    public static InteractionProfile fromId(String id) {
        if (id != null) {
            for (InteractionProfile p : values()) {
                if (p.id.equals(id)) {
                    return p;
                }
            }
        }
        return AUTO;
    }
}
