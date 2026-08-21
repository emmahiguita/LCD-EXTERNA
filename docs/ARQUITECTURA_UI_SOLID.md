# ARQUITECTURA UI — COMPONENTES, RESPONSABILIDADES Y SOLID

> **Objetivo:** Mapear cada archivo `ui/` a su responsabilidad única, detectar violaciones SRP, y proponer una estructura limpia sin cambiar comportamientos.

---

## MAPA ACTUAL (24 archivos en `ui/`)

### Capa 0 — Vistas personalizadas (Custom Views)
| Archivo | SRP | Responsabilidad real |
|:--------|:---:|:---------------------|
| `StreamView.java` | ✅ | Canvas del stream, SurfaceHolder callbacks |
| `RadialRingView.java` | ✅ | Dibuja anillos orbitales con Canvas |
| `RainView.java` | ✅ | Efecto visual decorativo de lluvia |

### Capa 1 — FAB Radial (7 archivos para 1 responsabilidad)
| Archivo | SRP | Responsabilidad real |
|:--------|:---:|:---------------------|
| `OverlayFabController.java` | ✅ | Fachada SOLID — coordina los 6 sub-controladores |
| `FABController.java` | ✅ | Estado visual y posición del FAB principal |
| `RadialMenuController.java` | ✅ | Pool de ítems, caché de geometría, ítems dinámicos |
| `OverlayAnimationController.java` | ✅ | Animaciones de expandir/colapsar |
| `OverlayGestureController.java` | ✅ | Drag, snap, gestos del FAB |
| `OverlayKeyboardController.java` | ✅ | Reposiciona FAB cuando el teclado se abre |
| `TooltipController.java` | ✅ | Tooltips al hover sobre botones radiales |

**Diagnóstico FAB:** Ok. La refactorización a v6.0 SOLID fue correcta. Cada sub-controlador tiene una responsabilidad única.

### Capa 2 — Productividad (Overlays funcionales)
| Archivo | SRP | Responsabilidad real |
|:--------|:---:|:---------------------|
| `SmartTaskbarController.java` | ✅ | Chips de ventanas del PC + polling 2.5s |
| `WindowControlsController.java` | ✅ | Barra Minimizar/Maximizar/Cerrar + macros de teclado |
| `DevPanelController.java` | ✅ | Botones Limpiar/Compilar/Ejecutar + resultados Gradle |
| `FileBrowserController.kt` | ✅ | Explorador de archivos del PC overlay |
| `AudioHudController.java` | ✅ | HUD de audio |
| `PortraitHybridController.java` | ✅ | Modo híbrido vertical |

### Capa 3 — Interacción (Mouse, Cursor, Gestos)
| Archivo | SRP | Responsabilidad real |
|:--------|:---:|:---------------------|
| `AdaptiveCursorView.kt` | ✅ | Cursor contextual que cambia de forma |
| `SmartCursorEngine.kt` | ✅ | Motor de cursor inteligente (snap, magnetismo) |
| `MouseModeCircle.kt` | ✅ | Trackpad circular neón |
| `GameGestures.java` | ✅ | Gestos del stream |

### Capa 4 — Extras
| Archivo | SRP | Responsabilidad real |
|:--------|:---:|:---------------------|
| `MascotEngine.kt` | ✅ | Pulpo mascota animado |
| `AdapterFragment.java` | ✅ | Adaptador de fragmentos |
| `AdapterFragmentCallbacks.java` | ✅ | Callbacks de adaptador |

---

## ❌ VIOLACIÓN SRP GRAVE: `LogicalKeyboardOverlay.kt`

**Estado:** 1118 líneas, ~15 responsabilidades mezcladas en 1 archivo.

| Responsabilidad | Líneas | ¿Debería estar separada? |
|:----------------|:------:|:-------------------------:|
| Diseño de teclas (createKey, addCharKey, addSpecialKey, etc.) | ~200 | ✅ Separar a `KeyboardLayoutEngine` |
| Renders de filas (buildNumberRow, buildQwertyRow, etc.) | ~150 | ✅ Misma, en `KeyboardLayoutEngine` |
| Pestañas (switchTab, buildDevKeyboard, buildShortcutsKeyboard) | ~100 | ✅ Misma, en `KeyboardLayoutEngine` |
| Lógica de input (onCharKeyPressed, onSpecialKeyPressed) | ~60 | ✅ Separar a `KeyboardInputHandler` |
| Modificadores (toggleModifier, releaseAllModifiers) | ~80 | ✅ Misma, en `KeyboardInputHandler` |
| Macros (sendMacro, addMacroKey, macroDesc) | ~40 | ✅ Misma, en `KeyboardInputHandler` |
| Estado (shiftActive, ctrlActive, altActive, currentScale, etc.) | ~40 | ✅ Separar a `KeyboardState` |
| Persistencia (saveState, restoreState, pref keys) | ~50 | ✅ Separar a `KeyboardPersistence` |
| Animación RGB (startRGBAnimation, stopRGBAnimation) | ~60 | ✅ Separar a `KeyboardAnimationEngine` |
| Animación de tecla (animateKeyPress) | ~10 | ✅ En `KeyboardAnimationEngine` |
| Pinch-to-zoom (scaleDetector) | ~25 | ✅ Misma, en `KeyboardAnimationEngine` |
| Posicionamiento (constrainAndPositionInitial, clampX/Y) | ~100 | ✅ Separar a `KeyboardPositionManager` |
| AutoSizeText y estilos de tecla | ~30 | ✅ En `KeyboardLayoutEngine` |
| Sticky Keys (updateStickyKeysStatus) | ~20 | ✅ Separar a `KeyboardStickyKeysIndicator` |
| Favoritos (logKeyPress, updateFavoritesRow, dispatchFavoriteAction) | ~100 | ✅ Separar a `KeyboardFavoritesController` |
| Tooltips (condition en addSpecialKey) | ~10 | ✅ En `KeyboardInputHandler` o manejarlo por separado |

**Total: ~1118 líneas → 7 archivos propuestos.**

---

## PROPUESTA ARQUITECTURA SOLID PARA `LogicalKeyboardOverlay.kt`

```
ui/
  keyboard/
    LogicalKeyboardOverlay.kt        → Fachada (coordinadora, <150 líneas)
    KeyboardLayoutEngine.kt          → Construcción de filas y teclas
    KeyboardInputHandler.kt          → Lógica de pulsación, modificadores, macros
    KeyboardState.kt                 → Estado: tab, scale, alpha, shift/ctrl/alt
    KeyboardPersistence.kt           → SharedPreferences (scale, alpha, tab, favoritos)
    KeyboardAnimationEngine.kt       → RGB anim, key press anim, pinch-to-zoom
    KeyboardPositionManager.kt       → constrainAndPositionInitial, clampX/Y, insets
    KeyboardFavoritesController.kt   → logKeyPress, updateFavoritesRow, chips ⭐
    KeyboardStickyKeysIndicator.kt   → updateStickyKeysStatus
```

### Contrato entre la Fachada y los sub-controladores

```
LogicalKeyboardOverlay
  ├── KeyboardState          (lectura/escritura de estado global)
  ├── KeyboardPersistence    (save/restore)
  ├── KeyboardLayoutEngine   (buildKeyboard, buildNumberRow, etc.)
  ├── KeyboardInputHandler   (onCharKeyPressed, toggleModifier, sendMacro)
  ├── KeyboardAnimationEngine(startRGBAnimation, animateKeyPress)
  ├── KeyboardPositionManager(constrainAndPositionInitial, clampX, clampY)
  ├── KeyboardFavoritesController (logKeyPress, updateFavoritesRow)
  └── KeyboardStickyKeysIndicator  (updateStickyKeysStatus, TextView)
```

**Flujo típico:**
1. Usuario pulsa `A` → `onCharKeyPressed("A")` en `KeyboardInputHandler`
2. `KeyboardInputHandler` lee `KeyboardState.shiftActive` para decidir mayúscula
3. Envía al PC vía `NvConnection.sendUtf8Text()`
4. Llama a `KeyboardPersistence.logFrequentKey("A")`

### Beneficios:
- **Testable:** cada sub-controlador puede testearse con mocks
- **Intercambiable:** se puede reemplazar `KeyboardAnimationEngine` si se quiere otro estilo de animación
- **Legible:** un dev nuevo entiende en 5 minutos qué hace cada archivo
- **Game.java no sabe nada:** la fachada `LogicalKeyboardOverlay` sigue siendo la misma clase pública. Game.java no cambia.

---

## USO DE INTERFACES PARA DESACOPLAR

Cada sub-controlador expone una interfaz mínima. `LogicalKeyboardOverlay` (la fachada) se inyecta con estas interfaces:

```kotlin
// KeyboardState.kt
interface KeyboardState {
    var currentTab: Int
    var currentScale: Float
    var shiftActive: Boolean
    var ctrlActive: Boolean
    var altActive: Boolean
    fun addListener(l: StateListener)
}

// KeyboardLayoutEngine.kt  
interface KeyboardLayoutEngine {
    fun buildKeyboard(tab: Int, container: LinearLayout)
    fun createKey(label: String, weight: Float, isSpecial: Boolean): TextView
}

// KeyboardInputHandler.kt
interface KeyboardInputHandler {
    fun onCharKeyPressed(kd: KeyData)
    fun onSpecialKeyPressed(vkCode: Int)
    fun toggleModifier(type: String, btn: TextView)
    fun sendMacro(vararg vkCodes: Int)
}
```

Esto permite:
- **Mockear en tests:** reemplazar `KeyboardInputHandler` real por un mock que registre qué teclas se enviaron
- **Feature flags:** desactivar favoritos cambiando la implementación de `KeyboardFavoritesController` a no-op
- **Perfiles por app:** diferente `KeyboardLayoutEngine` según la app detectada (Android Studio vs VS Code)

---

## ESTADO ACTUAL DEL FAB (v6.0) — YA SOLID

El FAB ya fue refactorizado a SOLID correctamente:

```
OverlayFabController (fachada)
  ├── FABController              → estado y posición del FAB
  ├── RadialMenuController       → pool de ítems radiales + geometría
  ├── OverlayAnimationController → animaciones expand/colapse
  ├── OverlayGestureController   → drag, snap, gestos
  ├── OverlayKeyboardController  → reposicionar FAB con teclado abierto
  └── TooltipController          → tooltips de botones radiales
```

Cada sub-controlador tiene UNA responsabilidad y **no depende de los otros**. Si quieres cambiar la animación de expansión, solo tocas `OverlayAnimationController.java`. Si quieres que el FAB se posicione diferente, solo tocas `FABController.java`.

---

## ESTADO ACTUAL DE GAME.JAVA — VIOLACIÓN MASIVA

**Game.java (3788 líneas)** es el God Object más grande del proyecto. Sus responsabilidades:

| Responsabilidad | Líneas | ¿Extraíble? |
|:----------------|:------:|:-----------:|
| Ciclo de vida (onCreate, onResume, etc.) | ~400 | Permanente (Activity) |
| Inicialización de TODOS los controladores | ~350 | ✅ Extraer a `GameBootstrap.kt` |
| WakeLock, WiFiLock, CPU Lock | ~60 | ✅ Extraer a `StreamPowerManager.kt` |
| Stream connection (start, stop, teardown) | ~300 | Permanente (Activity principal) |
| Reconexión + WoL | ~150 | ✅ Ya en `AutoReconnectManager` |
| SurfaceView + Decoder | ~200 | ✅ Extraer a `StreamSurfaceManager.kt` |
| Manejo de PiP | ~80 | Permanente (Activity) |
| Overlay callbacks (teclado, FAB, taskbar) | ~400 | Permanente (Activity coordina) |
| Input capture + gestos | ~200 | ✅ Extraer a `GestosManager.kt` |
| Orientación | ~50 | ✅ Extraer a `OrientationManager.kt` |
| Stats (RTT, FPS, packet loss) | ~150 | ✅ Extraer a `StreamStats.kt` |
| Controladores virtuales | ~100 | ✅ Ya en `virtual_controller/` |
| Diálogos de error | ~100 | ✅ Ya en `Dialog.java` |
| Badge de conexión híbrido | ~50 | ✅ Ya en `PortraitHybridController` |

**Objetivo:** Reducir Game.java de 3788 → ~800 líneas (solo lo que necesita estar en una Activity).

---

## PLAN DE EXTRACCIÓN POR FASES

### Fase 1 (inmediata): Keyboard
Dividir `LogicalKeyboardOverlay.kt` (1118→150 líneas). Ya tengo la estructura diseñada arriba.

### Fase 2 (1-2 días): Game.java managers
1. `StreamPowerManager.kt` — WakeLock, WiFiLock, CPU Lock, FLAG_KEEP_SCREEN_ON
2. `GameBootstrap.kt` — Inicialización de controladores (FAB, teclado, taskbar, etc.)
3. `StreamSurfaceManager.kt` — SurfaceView lifecycle, decoder setup/teardown

### Fase 3 (2-3 días): Perfiles e IA
4. `KeyboardProfileEngine.kt` — Escucha `ForegroundAppRegistry` y carga el perfil correcto
5. `KeyboardSearchEngine.kt` — Búsqueda de acciones (filtro de teclas)

---

## REGLA DE ORO

Cada archivo en `ui/` debe poder explicarse en una frase:

| Archivo actual | Frase SRP |
|:---------------|:----------|
| `OverlayFabController.java` | Coordina el FAB radial y sus sub-controladores |
| `FABController.java` | Gestiona posición y estado visual del FAB |
| `RadialMenuController.java` | Administra el pool de ítems radiales y geometría |
| `OverlayAnimationController.java` | Ejecuta animaciones de expandir/colapsar |
| `OverlayGestureController.java` | Maneja drag, snap y gestos táctiles del FAB |
| `OverlayKeyboardController.java` | Reposiciona el FAB cuando el teclado overlay se abre |
| `TooltipController.java` | Muestra tooltips temporales sobre botones radiales |
| `RadialRingView.java` | Dibuja el anillo orbital decorativo |
| `SmartTaskbarController.java` | Muestra ventanas del PC como chips actualizables |
| `WindowControlsController.java` | Envía macros de teclado (Min, Max, Close, Alt+Tab) |
| `DevPanelController.java` | Ejecuta comandos Gradle remotos y muestra resultados |
| `FileBrowserController.kt` | Navega y descarga archivos del PC |
| `AdaptiveCursorView.kt` | Renderiza el cursor contextual que cambia de forma |
| `SmartCursorEngine.kt` | Calcula snap magnético y perfil de cursor por app |
| `MouseModeCircle.kt` | Trackpad neón para modo ratón |
| `GameGestures.java` | Traduce gestos táctiles a acciones de stream |
| `MascotEngine.kt` | Anima el pulpo mascota reactivo |
| `RainView.java` | Efecto visual de lluvia decorativo |
| `PortraitHybridController.java` | Modo vertical con stream a pantalla completa |
| `AudioHudController.java` | Muestra niveles de audio en tiempo real |
| `LogicalKeyboardOverlay.kt` | **Fachada del teclado overlay (debería delegar)** |
| `StreamView.java` | Canvas del stream de video |
| `StreamViewTransformController.java` | Zoom y pan del stream |
