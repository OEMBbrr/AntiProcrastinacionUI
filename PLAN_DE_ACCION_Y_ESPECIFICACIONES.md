# 📌 PLAN DE ACCIÓN Y ESPECIFICACIONES TÉCNICAS (AntiProcrastinación)

> **ID de Conversación de Referencia**: `d42ef4a7-b261-4a4d-8c29-d0405a1da327`  
> **Ubicación del Documento**: Raíz del Proyecto (`C:\Users\USUARIO\Documents\AntiProcrastinacion\PLAN_DE_ACCION_Y_ESPECIFICACIONES.md`)  
> **Estado de APKs**: Se mantuvieron únicamente las APKs históricas desde **`v1` hasta `v14`**. La siguiente versión limpia a compilar en el nuevo chat será **`v15.0.0`** (`versionCode = 15`).

---

## 1. Contexto General del Proyecto

`AntiProcrastinación` es un sistema multiplataforma (Android App, Extensión de Chrome, Página Web y Guía iOS) diseñado para bloquear distracciones y potenciar el enfoque profundo.

- **Aplicación Android**: Creada en Jetpack Compose, con sistema de bloqueo por temporizador, launcher predeterminado opcional, overlay sobre apps no permitidas, treguas temporales y sistema de notas personal sincronizado.
- **Extensión de Chrome**: Bloqueador web en Manifest V3 con reglas dinámicas de red (`declarativeNetRequest`), inicio de sesión con Google OAuth2, panel de notas rápido en páginas bloqueadas y espacio de trabajo de notas en pestaña completa (`notes.html`).
- **Base de Datos**: Firebase Realtime Database bajo la ruta `/users/<sanitized_email>/notes`.

---

## 2. Requerimientos Específicos para ANDROID (A implementar en v15.0.0)

### 2.1 Carrusel Animado de 150 Frases de Reflexión y Motivación
- **Archivo de Frases**: `MotivationalPhrases.kt` con 150 oraciones estéticas escritas en Kotlin (`listOf(...)`).
- **Ubicación en UI**: En `ZenScreen.kt`, colocado exactamente entre la cabecera (Logo y Nombre) y los números del temporizador.
- **Tiempo de Permanencia**: Cada oración se muestra por **60 segundos exactos** (`delay(60000)` en `LaunchedEffect`).
- **Animaciones de Transición**: Utilizar `AnimatedContent` con `togetherWith`:
  - Entrada: Deslizamiento desde la derecha hacia la izquierda (`slideInHorizontally { width -> width }`) + **Fade-In suave** (`fadeIn(tween(800))`).
  - Salida: Deslizamiento hacia la izquierda (`slideOutHorizontally { width -> -width }`) + **Fade-Out suave** (`fadeOut(tween(800))`).
- **Intocable / Solo Lectura**: Tarjeta estilizada en background Krem/Zen sin eventos táctiles para evitar distracciones.

### 2.2 Acceso Prominente a Notas durante el Modo Enfoque
- **Botón Principal**: Botón azul brillante (`Color(0xFF2563EB)`) de 54dp de alto ubicado directamente debajo de los dígitos del temporizador en `ZenScreen.kt` con el texto:  
  **`MIS NOTAS (Anotar idea o pendiente)`** e icono 3D `memo_3d.png`.
- **Botón Flotante (FAB)**: `ExtendedFloatingActionButton` en la esquina inferior derecha del `Scaffold`.
- **Modal de Notas (`ZenNotesModal.kt`)**:
  - Control de escalado de texto `A-` / `A+` de 12sp a 24sp.
  - Buscador en tiempo real por palabra clave.
  - Botón de copia al portapapeles y eliminación de nota.
  - Filtro por categorías (`General`, `Tarea`, `Idea`, `Reflexión`).

### 2.3 Auto-Desbloqueo al Terminar el Temporizador (Sin Frases)
- **Cero Frases Requeridas**: Al llegar a `00:00`, se elimina por completo la obligación de escribir oraciones cortas o largas.
- **Desbloqueo Automático**: Cuando `timeRemaining <= 0`, `ZenScreen.kt` invoca automáticamente `lockManager.stopLock()` y `onUnlocked()`.
- **Botonera Directa**: Si el usuario está viendo la pantalla de finalización, un solo tap en **`🔓 Finalizar y Volver a Inicio`** desbloquea la app.

### 2.4 Solución al Bug de Sesiones Pegadas (`LockManager.kt`)
- **Propiedad `isLocked`**:
  ```kotlin
  var isLocked: Boolean
      get() {
          val locked = prefs.getBoolean(KEY_IS_LOCKED, false)
          if (!locked) return false
          val end = prefs.getLong(KEY_LOCK_END_TIME, 0L)
          if (end > 0 && System.currentTimeMillis() >= end) {
              prefs.edit().putBoolean(KEY_IS_LOCKED, false).apply()
              return false
          }
          return true
      }
  ```
  Esto garantiza que si el teléfono se reinicia o se abre la app habiendo pasado el tiempo del bloqueo, la app retorne inmediatamente a `ConfigScreen` sin quedarse trabada en sesiones antiguas.

### 2.5 Protección de Cortina de Notificaciones
- **`MainActivity.kt`**: Método `onWindowFocusChanged(hasFocus)` que envía el broadcast `Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)` y refuerza el modo inmersivo sticky cuando `hasFocus == false` durante una sesión de bloqueo activa.

### 2.6 Recuentos de Emojis 3D Microsoft Fluent
- Recursos almacenados en `app/src/main/res/drawable/`:
  - `memo_3d.png`
  - `light_bulb_3d.png`
  - `pushpin_3d.png`
  - `rocket_3d.png`
  - `brain_3d.png`
  - `sparkles_3d.png`

---

## 3. Especificaciones para la Extensión de Chrome y Web

- **Espacio de Trabajo Dedicado**: `extension_chrome/notes.html`, `notes.js`, `notes.css`.
- **Banner Motivacional Rotativo**: Mensajes de reflexión alternando cada 30 segundos con fundido de opacidad.
- **Almacenamiento Híbrido**: Guardado **instantáneo local** en `chrome.storage.local` como respaldo en 0.001s, y sincronización en segundo plano con Firebase RTDB.

---

## 4. Sistema de Diseño Visual (Tokens Zen)

- `CreamBackground`: `#F9F8F6`
- `ZenOlive`: `#5A6E61`
- `ZenOliveDark`: `#46564B`
- `ZenSage`: `#8C9B90`
- `ZenCharcoal`: `#1F2421`
- `ZenWhite`: `#FFFFFF`
- `ZenGreen`: `#2E7D32`
- `ZenCoral`: `#D97D75`

---

## 5. Instrucciones para el Nuevo Chat

1. Abrir un nuevo chat y proporcionar este ID de conversación: `d42ef4a7-b261-4a4d-8c29-d0405a1da327`.
2. Indicar al asistente que lea este archivo `PLAN_DE_ACCION_Y_ESPECIFICACIONES.md` en la raíz del proyecto.
3. El asistente compilará la versión limpia **v15.0.0** (`versionCode = 15`, `versionName = "15.0.0"`) únicamente tras recibir tu autorización explícita.
