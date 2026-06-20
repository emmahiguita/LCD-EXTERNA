# 📱💻 SMARTDISPLAY AI
## Documento de Arquitectura, Diseño, Metodologías, Tecnologías y Plan Maestro

---

## 1. Visión del Producto
SmartDisplay AI es una plataforma modular y de alto rendimiento que convierte cualquier dispositivo Android en una extensión inteligente del computador.

### Objetivo Principal
> [!NOTE]
> Un dispositivo Android debe poder convertirse en una extensión inteligente del PC (segunda pantalla, touchpad de precisión, entrada de audio/webcam e inyector de macros asistido por IA) desde cualquier red local (USB-C, Wi-Fi Direct, Wi-Fi local) o remota de forma segura y con la menor latencia posible.

---

## 2. Diferenciadores y Análisis de Mercado

| Plataforma | Segunda Pantalla | Control Remoto | IA Contextual | Adaptación Móvil |
| :--- | :---: | :---: | :---: | :---: |
| **SpaceDesk** | ✅ Sí | ❌ No | ❌ No | ❌ No |
| **SuperDisplay** | ✅ Sí | ❌ No | ❌ No | ❌ No |
| **RustDesk** | ❌ No | ✅ Sí | ❌ No | ❌ No |
| **SmartDisplay AI** | ✅ **Sí** | ✅ **Sí** | ✅ **Sí** | ✅ **Sí** |

---

## 3. Patrones de Diseño & Principios SOLID

### S - Single Responsibility (Responsabilidad Única)
Cada servicio de backend y controlador del frontend se encarga de un dominio específico:
*   `DisplayModule`: Renderizado de streams de video, decodificación por hardware y buffers.
*   `InputModule`: Inyección de eventos táctiles, touchpad relative drags y emulación de teclado.
*   `NetworkModule`: Negociación de puertos y puente Wi-Fi / USB.

### O - Open/Closed (Abierto/Cerrado)
Toda comunicación de red depende de una abstracción:
```typescript
interface ConnectionProvider {
  connect(): Promise<boolean>;
  disconnect(): Promise<boolean>;
  sendData(payload: Buffer): Promise<void>;
}
```
Esto permite expandir el sistema a nuevos protocolos (e.g. WebRTC, USB-C bulk, Wi-Fi Direct) sin alterar el flujo principal de datos.

### L - Liskov Substitution (Sustitución de Liskov)
Las implementaciones concretas como `USBConnectionProvider` y `WifiConnectionProvider` deben poder alternarse dinámicamente en tiempo de ejecución a través del gestor `SmartConnect` sin interrumpir la sesión activa del usuario.

### I - Interface Segregation (Segregación de Interfaces)
Diseñamos interfaces compactas y segregadas en lugar de una interfaz global:
*   `DisplayManager`: Controla la escala y refresco del panel.
*   `InputManager`: Controla gestos y macros.
*   `AudioManager`: Controla los canales de micrófono y audio bidireccional.

### D - Dependency Inversion (Inversión de Dependencias)
Los módulos de alto nivel (como la UI de Next.js and el Dashboard) no se acoplan a los binarios directos de `adb` o `scrcpy`, sino que dependen de APIs y adaptadores intermedios del agente de Electron.

---

## 4. Estructura de Capas (Clean Architecture)

### Mobile Application (Android / Kotlin)
*   **Presentation**: Vistas Compose, ViewModels de UI, gestión de renderizado de Canvas.
*   **Domain**: Casos de uso de inyección de entrada local, mapeo de macros y triggers de seguridad.
*   **Data / Infrastructure**: Repositorios locales (Room), sockets UDP/TCP de Sunshine/Moonlight, decodificador WebRTC.

### Desktop Agent (Windows / Electron / Next.js)
*   **UI / Front**: Interfaz reactiva Next.js con Tailwind CSS y Lucide Icons.
*   **Application / Electron**: Orquestador principal (`main.js`) que maneja subprocesos de ADB, radar UDP y sincronización de portapapeles.
*   **Infrastructure / OS**: Drivers de pantalla virtual (IddSampleDriver / Virtual Display Driver) y canal de inyección de entrada a nivel de Kernel de Windows.

---

## 5. Roadmap de Lanzamientos

### Fase 1: MVP (Mínimo Viable)
*   Integración de conductor de pantalla virtual.
*   Conectividad automática híbrida (USB-C + Wi-Fi local).
*   Visor de latencia ultra baja a 60 FPS (H.265).

### Fase 2: Control Remoto & Companion
*   Touchpad interactivo multitáctil.
*   Teclado de macros de desarrollo con Ctrl, Alt, F1-F12 y scripts integrados (Git / Debug).
*   Transferencia de archivos asíncrona.

### Fase 3: Multimedia Bridge
*   Redirección de audio bidireccional (PC a Android y viceversa).
*   Cámara móvil como webcam del sistema y uso de micrófono Bluetooth.

### Fase 4: Inteligencia de Interfaz (Smart UI)
*   Detección de foco de aplicaciones de Windows.
*   Asistente integrado (Gemini/OpenAI) para ampliación táctil automatizada y asistencia técnica de código.
