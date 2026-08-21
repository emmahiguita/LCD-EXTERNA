---
name: material_design_3
description: "Guidelines for Material Design 3 (M3) including elevation, motion, easing, and transitions."
---

# Material Design 3 (M3) Guidelines

When building or updating user interfaces, adhere to the following Material 3 principles to ensure a professional, modern, and fluid experience.

## 1. Elevation (Spatial Relationships)
- **Concept:** M3 abandons heavy shadows for a combination of subtle shadows and tonal color (tinting the surface color).
- **Levels (0 to 5):** Use lower levels (1-2) for resting states of cards, lists, and dialogs. Use higher levels (3-5) for floating elements (FABs, menus, dragged items) to indicate focus and separation.
- **Implementation:** Use M3 color tokens (`surfaceContainer`, `surfaceContainerLow`, `surfaceContainerHigh`) to represent elevation, supplemented by standard elevation shadows when contrast is needed.

## 2. Motion (Easing & Duration)
Motion should feel expressive and natural, not robotic.
- **Easing Curves:**
  - **Emphasized:** The core M3 curve. Starts quickly and decelerates smoothly. Use for major transitions (e.g., opening a menu).
  - **Standard:** Smooth start and end. Use for simple, minor movements.
- **Durations:**
  - **Short (50-150ms):** Micro-interactions, ripples, color changes, small state changes.
  - **Medium (200-300ms):** Opening small menus, expanding cards, switching local states.
  - **Long (400-500ms):** Major layout changes, full-screen transitions.

## 3. Transition Patterns
Use specific choreography for different types of UI changes.
- **Container Transform:** Use when an element expands into a larger container (e.g., a FAB transforming into a full-screen menu or dialog). Maintains a visual connection.
- **Shared Axis (X, Y, Z):** Use for navigating between sibling views or hierarchical levels. Elements slide in sync along an axis (e.g., moving forward/backward through a wizard uses X-axis; drilling down into a list uses Z-axis).
- **Fade Through:** Use when switching between elements that have no strong spatial relationship (e.g., switching bottom navigation tabs).
- **Fade:** Use for elements entering or exiting within the boundaries of the screen (e.g., a toast or a dialog appearing).

## Applying to SmartDisplay AI
- Ensure the **Octopus FAB** uses Emphasized curves for opening/closing, not linear or legacy overshoot.
- Use **Container Transform** if expanding the FAB into a larger control panel.
- Update UI components to rely on **tonal elevation** (surface colors) rather than just heavy black shadows.
