# 📝 REPORTE DE RESOLUCIÓN DE AUDITORÍA Y CAMBIOS - VERSIÓN v18.0.0

- **Fecha de Modificación**: 9 de Agosto de 2026
- **Versión del Proyecto**: `v18.0.0` (`versionCode = 18`, `versionName = "18.0.0"`)
- **Ubicación del Reporte**: `C:\Users\USUARIO\Documents\AntiProcrastinacion\cambios y reportes\REPORTE_CAMBIOS_V18.md`
- **Basado en la Auditoría OpenCode**: `C:\Users\USUARIO\Documents\AntiProcrastinacion\cambios y reportes\Reporte_2026-08-09_19-12-20.md`

---

## 📩 1. Orden Exacta del Usuario

> *"Vale, revisa la carpeta de Cambios y reportes, pronto va haber un nuevo reporte de opencode para que lo veas porque hay varios errores que hay que corregir, pero hasta que no esté listo no hagas nada"*

---

## 🔍 2. Hallazgos de Auditoría OpenCode (Estado Anterior)

1. **🔴 CRASH al Abrir "Mis Notas" en Modo Enfoque**:
   - `Logcat Stacktrace`: `java.lang.SecurityException: Permission Denial: android.intent.action.CLOSE_SYSTEM_DIALOGS broadcast from com.antiprocrastinacion.lock requires android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS.`
   - Al desplegar el modal Compose de notas, la actividad pierde el foco de ventana (`onWindowFocusChanged(hasFocus = false)`).
   - Se intentaba enviar el broadcast `ACTION_CLOSE_SYSTEM_DIALOGS` que en Android 12+ requiere firmas de sistema, lanzando `SecurityException` y cerrando la app de inmediato.

2. **🔴 Sincronización de Notas entre Teléfono y PC (Token Expirado)**:
   - `background.js` llamaba a `refreshFirebaseToken()` al recibir un HTTP `401 Unauthorized` de Firebase, pero la función no estaba definida (`ReferenceError`).
   - Al expirar el ID Token de Firebase (tras 1 hora), las notas dejaban de sincronizarse silenciosamente.

3. **⚠️ Ausencia de Registros de Error en Escritura de Notas**:
   - `addNote` y `deleteNote` en `LockManager.kt` no tenían `addOnSuccessListener` ni `addOnFailureListener`, perdiendo visibilidad de fallos de reglas en Firebase RTDB.

---

## 🛠️ 3. Soluciones Aplicadas (Versión 18.0.0)

### A. Eliminación del Crash en `onWindowFocusChanged`
- En [`MainActivity.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/MainActivity.kt#L166-L174):
  - Se protegió la llamada a `sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))` dentro de un bloque `try-catch (e: Exception)`.
  - Ahora, al abrir el modal "Mis Notas", perder el foco o desplegar el sistema, la app captura la restricción de Android 12+ sin colapsar jamás.

### B. Implementación de `refreshFirebaseToken()` en la Extensión de Chrome
- En [`extension_chrome/background.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/background.js#L40-L65):
  - Se implementó la función síncrona/asíncrona `refreshFirebaseToken()` consumiendo el endpoint oficial:
    `https://securetoken.googleapis.com/v1/token?key=${FIREBASE_API_KEY}` con `grant_type=refresh_token`.
  - El nuevo token de acceso se almacena en `chrome.storage.local`, garantizando sincronización perpetua de notas sin desconexiones a la hora.

### C. Logging y Monitoreo Explícito de RTDB en Android
- En [`LockManager.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LockManager.kt#L485-L510):
  - Se añadieron los listeners `.addOnSuccessListener` y `.addOnFailureListener` a `setValue()` y `removeValue()` bajo el tag `"ZEN_NOTES"`.

---

## 📁 4. Detalle de Archivos Modificados

1. **[`app/build.gradle.kts`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/build.gradle.kts#L16-L17)**: `versionCode = 18`, `versionName = "18.0.0"`.
2. **[`MainActivity.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/MainActivity.kt#L166-L174)**: `try-catch` en `sendBroadcast` previene el crash de "Mis Notas".
3. **[`extension_chrome/background.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/background.js#L40-L65)**: Función `refreshFirebaseToken()` para renovación de token tras 1 hora.
4. **[`LockManager.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LockManager.kt#L485-L510)**: Callbacks de log en operaciones Firebase.
5. **[`ConfigScreen.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ConfigScreen.kt#L770)**: Versión de interfaz actualizada a `v18.0.0`.
