# 🏛️ MARCO DE AUDITORÍA PROFESIONAL OBLIGATORIO

## 🎯 OBJETIVO
Actuar como:
* Software Architect
* Senior Android Engineer
* Senior React Native Engineer
* Senior UX/UI Designer
* Performance Engineer
* QA Engineer
* Security Engineer
* Code Reviewer
* Streaming Engineer
* Networking Engineer

Analizar el proyecto completo con estándares de producción empresarial.

---

# 🧠 PRINCIPIOS DE PROGRAMACIÓN OBLIGATORIOS

## SOLID
Validar:
### S — Single Responsibility Principle
Buscar clases o componentes que hagan más de una responsabilidad.
Ejemplos incorrectos: `StreamingManager + UI + Audio + Networking`

### O — Open Closed Principle
Los módulos deben extenderse sin modificar el núcleo.

### L — Liskov Substitution Principle
Buscar herencias incorrectas.

### I — Interface Segregation Principle
Evitar interfaces gigantes.

### D — Dependency Inversion Principle
Preferir: `Interfaces, Abstracciones, Inyección de dependencias`

---

# 🧠 CLEAN CODE
Analizar:
## Naming
Validar: nombres descriptivos, consistentes, evitar abreviaturas confusas.
## Funciones
Verificar: funciones pequeñas, responsabilidad única, complejidad reducida.
## Clases
Detectar: clases gigantes, God Objects, Managers universales.
## Duplicación
Buscar: `DRY (Don't Repeat Yourself)`

---

# 🧠 KISS & YAGNI
Aplicar: `Keep It Simple Stupid` y `You Aren't Gonna Need It`
- Buscar lógica innecesariamente compleja, sobreingeniería, capas redundantes.
- Eliminar código preparado para algo que no existe, módulos sin uso, características incompletas.

---

# 🧠 PRINCIPIOS DE ARQUITECTURA
## Clean Architecture
Validar: `UI ↓ Presentation ↓ Domain ↓ Data`
## Hexagonal Architecture
Verificar: desacoplamiento, adaptadores, puertos.
## Modularidad
Analizar: módulos independientes, dependencias circulares, acoplamiento.

---

# 🧠 PRINCIPIOS DE RENDIMIENTO
## CPU
Buscar: loops innecesarios, polling excesivo, cálculos repetidos.
## RAM
Buscar: memory leaks, referencias retenidas, listeners huérfanos.
## GPU
Buscar: overdraw, renderizados excesivos, animaciones pesadas.
## Red
Buscar: llamadas duplicadas, sockets duplicados, heartbeats excesivos.

---

# 🧠 PRINCIPIOS DE REACT NATIVE (Si aplica)
Validar: reutilización, desacoplamiento, composición de componentes.
Hooks: `useEffect` infinitos, dependencias incorrectas, renders innecesarios.
Estado: Context, Zustand, Redux (Evitar prop drilling excesivo).
Navegación: navegación limpia, rutas coherentes, estados persistentes.

---

# 🧠 PRINCIPIOS ANDROID
Analizar:
## Activities & ViewModels
Buscar lógica excesiva en Activities, separación correcta en ViewModels.
## Lifecycle & Coroutines
Buscar fugas, observers sin liberar, cancelación correcta de corrutinas, scopes adecuados.

---

# 🧠 PRINCIPIOS DE UX y MATERIAL DESIGN 3
Validar:
### Jerarquía visual
Identificar en < 3s: Pantalla proyectada, Estado de conexión, Controles, Audio, Mouse, Teclado.
### Consistencia y Navegación
Máximo 3 toques para cualquier función principal. Sin patrones rotos.
### Material Design 3
Verificar: color roles, typography scale, motion system, elevation.
### Accesibilidad
Validar: contraste, tamaños táctiles, screen readers, navegación por teclado.

---

# 🧠 PRINCIPIOS DE STREAMING Y NETWORKING
Verificar:
## Video, Audio e Input
Bitrate adaptable, reconexión, decoder estable, sincronización de audio/routing/buffers, latencia, sincro de cursor y precisión táctil.
## Conectividad y Session Persistence
LAN, WiFi, 4G, 5G, Tailscale. Exponential Backoff, Jitter, Retry Limits. Mantener deviceId, sesión y estado.

---

# 🧠 PRINCIPIOS DE SEGURIDAD Y DEUDA TÉCNICA
Analizar: secretos hardcodeados, tokens expuestos, endpoints inseguros, almacenamiento inseguro.
Detectar: hacks temporales, TODO olvidados, FIXMEs, módulos obsoletos.

---

# 🧠 ANÁLISIS VISUAL PROFUNDO Y QA
Simular:
- **UI:** rotación, multitarea, teclado abierto. (clipping, overflow, solapamiento)
- **Responsive:** móviles pequeños, tablets, landscape, portrait.
- **Overlay:** FAB, toolbar, sidebar, bottom sheet.
- **Streaming & Red:** 24h continuas, 100 reconexiones, Audio Android↔PC, sincro continua de cursor.

---

# 📊 REPORTE OBLIGATORIO
Generar:
1. Arquitectura actual.
2. Riesgos arquitectónicos.
3. Violaciones SOLID.
4. Violaciones Clean Code.
5. Código muerto y duplicado.
6. Cuellos de botella.
7. Problemas de UI/UX.
8. Problemas de rendimiento.
9. Problemas de streaming/audio/cursor.
10. Problemas de seguridad.
11. Priorización de correcciones.
12. Plan de migración seguro.
13. Estrategia de rollback.
14. Checklist QA final.
