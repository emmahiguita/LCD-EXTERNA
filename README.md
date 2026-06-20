# SmartDisplay AI - Teclado DEV Profesional & Gestión de Monitores Virtuales

Este repositorio contiene el sistema de teclado y control del cliente de streaming **SmartDisplay AI** (basado en Moonlight Android) optimizado para flujos de trabajo de desarrollo profesional.

Las modificaciones han sido integradas respetando estrictamente el diseño de la interfaz visual actual, la latencia de entrada y la total compatibilidad con servidores **Sunshine / Moonlight**.

---

## 📂 Estructura del Proyecto

* **`moonlight-android-master/`**: Código fuente de la aplicación cliente para Android.
  * 📄 `app/src/main/res/layout/overlay_keyboard.xml`: Interfaz visual en XML que define las pestañas (NORMAL / DEV), la barra compacta superior y los chips de atajos de teclado.
  * 📄 `app/src/main/java/com/limelight/ui/SmartDisplayOverlay.java`: Lógica de cableado e inyección de eventos del teclado. Registra los atajos rápidos, sticky keys y eventos de pulsación larga.
  * 📄 `app/src/main/java/com/limelight/Game.java`: Controlador principal que actúa como puente receptor. Gestiona la lógica de conmutación de pantallas utilizando el protocolo de entrada del stream.
* **`src/app/` / `src/components/`**: Módulos e interfaces de la aplicación web complementaria SmartDisplay en Next.js.
* **`.gitignore`**: Configuración optimizada para excluir archivos temporales, logs pesados de depuración (Android dumps) y APKs binarios del repositorio para mantenerlo ligero.

---

## ⌨️ Modo de Uso y Características Nuevas

### 1. Atajos DEV Profesionales (Pestaña DEV)
Se han reemplazado chips heredados por los comandos más críticos utilizados en editores modernos (**VS Code, Cursor, Windsurf, Android Studio**):
* **`Ctrl + ` `** (Botón `Ctrl+``): Abre o cierra de inmediato la terminal integrada en tu editor de código de la PC.
* **`Ctrl + Shift + P`** (Botón `Ctrl+Sh+P`): Abre la Paleta de Comandos del IDE para buscar ajustes o ejecutar comandos rápidamente.
* **`Ctrl + K`**: Invoca al asistente inteligente de Inteligencia Artificial (IA) integrado para editar código en línea (Cursor / Windsurf).

### 2. Pulsación Larga (Long Press) Informativa
* **Cómo usar**: Mantén pulsado cualquier chip de atajo (ej. `Ctrl+Sh+P`) durante **1.5 segundos**.
* **Qué hace**: Muestra una notificación temporal (`Toast`) en la pantalla con el comando exacto y una breve explicación de su uso.
* **Seguridad**: Consume el evento táctil para que el comando **no sea enviado a la PC** por error mientras solo quieres consultar su funcionalidad.

### 3. Conmutación de Escritorios Virtuales (Botones de Monitores 🖥)
Optimizado especialmente para flujos de trabajo con **un solo monitor físico / laptop portátil**:
* Al presionar los botones `🖥 1`, `🖥 2` y `🖥 3` del panel de monitores, el cliente detecta de forma inteligente tu ubicación y simula un entorno multi-pantalla enviando comandos relativos:
  * Desplazamiento a la derecha: **`Ctrl + Win + Flecha Derecha`**
  * Desplazamiento a la izquierda: **`Ctrl + Win + Flecha Izquierda`**
* **Beneficio**: Cambia de forma instantánea entre 3 espacios de trabajo independientes de Windows directo desde tu móvil (por ejemplo: Escritorio 1 para Código, Escritorio 2 para Documentación, Escritorio 3 para Terminal).

---

## 🛠 Instrucciones de Compilación

Para compilar la aplicación cliente Android sin errores de ambigüedad en los sabores del build de Moonlight, ejecuta el siguiente comando desde la carpeta del proyecto Android:

```powershell
cd moonlight-android-master
.\gradlew.bat :app:compileNonRootDebugSources --no-daemon
```
