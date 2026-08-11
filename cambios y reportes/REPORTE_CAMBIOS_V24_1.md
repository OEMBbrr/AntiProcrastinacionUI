# 📝 REPORTE DE CAMBIOS Y AUDITORÍA DE CÓDIGO - VERSIÓN v24.1.0 (2FA REDISEÑADO + CATEGORÍAS + SCROLL + POMODORO + DYNAMIC ISLAND)

- **Fecha de Modificación**: 10 de Agosto de 2026
- **Versión del Proyecto**: `v24.1.0` (`versionCode = 241`, `versionName = "24.1.0"`)
- **Ubicación del Reporte**: `C:\Users\USUARIO\Documents\AntiProcrastinacion\cambios y reportes\REPORTE_CAMBIOS_V24_1.md`

---

## 📩 1. Órdenes Exactas del Usuario

> *"Crea un reporte .md de que todo está bien."* — **REPORTE_AUDITORIA_IMPLEMENTACION_V24.md** (auditoría completa de V24, todo OK ✅).

> *"Resuelve estos problemas: 1) el 2FA no funciona, el código no llega al teléfono; 2) el scroll en Android está roto; 3) el menú Pomodoro es confuso, no se ve el contador de minutos; 4) el logo, 'MODO ENFOQUE' y la frase se ocultan bajo la cámara (Dynamic Island); 5) las categorías de notas están desincronizadas entre el teléfono y el PC."*

> *"Crea un plan de implementación de lo más difícil a lo más fácil, todo bajo la versión 24.1. Con simulaciones de Firebase por cada cambio."*

> *"Dale bb."* — aprobación para ejecutar el plan de las 5 tareas de v24.1.

**Decisiones confirmadas:**
- El **código 2FA lo genera el TELÉFONO** (no la extensión): la extensión solo solicita, el teléfono muestra la notificación con el código y la extensión verifica lo que el usuario escribe en el PC.
- Las categorías de notas usan una **clave canónica única** entre Android y la extensión: `'general' | 'tarea' | 'idea' | 'reflexion'`.
- Cada cambio valida con **simulaciones** (Firebase RTDB en nodos `sim_*` con limpieza automática).

---

## 🔍 2. Estado Anterior (V24 → V24.1)

1. **2FA roto**: la extensión generaba el código y lo publicaba en `/users/<target>/auth_request`, pero los `target` de Android y de la extensión NO coincidían → el código nunca llegaba al teléfono. La causa era que `getTargetKey()` (extensión: email → syncKey → firebaseUid) y `userRef()` (Android: googleUserEmail → userSyncKey) podían resolver a nodos distintos.
2. **Categorías desincronizadas**: Android guardaba `"General"/"Tarea"/"Idea"/"Reflexión"` (capitalizado, con acento) y la extensión `'general'/'tarea'/'idea'/'reflexion'` (minúsculas, sin acento) → al cruzar dispositivos la categoría se perdía o caía en "General".
3. **Scroll roto**: `ZenScreen.kt` no tenía scroll en su Column principal (contenido cortado en pantallas pequeñas) y el selector de aplicaciones de `ConfigScreen.kt` usaba altura fija con `LazyColumn` anidada.
4. **Menú Pomodoro confuso**: los steppers tenían el valor en un `Text` pequeño bajo la etiqueta; con el problema de scroll el contador de minutos quedaba cortado.
5. **Dynamic Island**: la cabecera de `ZenScreen.kt` solo tenía `padding(top = 12.dp)` y el logo/"MODO ENFOQUE"/frase chocaban con la cámara perforada.

---

## 🛠️ 3. Cambios Realizados y Soluciones Implementadas (V24.1)

### A. 2FA REDISEÑADO — EL TELÉFONO GENERA EL CÓDIGO (Tarea 1)
- **Extensión `background.js`**:
  - Nueva variable persistida `pairedTargetKey`: el nodo real del teléfono, adoptado desde `device_info.target_key` (Firebase) o `target_key` (LAN/WebSocket).
  - `getTargetKey()` ahora prioriza `pairedTargetKey` → la extensión escribe SIEMPRE en el mismo nodo que el teléfono escucha.
  - `requestAuthCode` SOLO publica `{status:'requesting'}` (sin código).
  - `pollForAuthCode()`: polling 1s (máx 30 intentos) hasta leer `status:'pending' + code` del teléfono; lo guarda en memoria (`pendingAuth`) **sin mostrarlo** en el PC.
  - `verifyAuthCode` lee Firebase, compara el código publicado por el teléfono y marca `status:'approved'`; fallback en memoria local.
  - `pollFirebaseSync` y el manejador LAN adoptan `target_key` del teléfono y re-sincronizan si el nodo cambió.
- **Android `LockManager.kt`**:
  - Nuevo `targetKey` público (misma limpieza que la extensión); `userRef()` lo usa.
  - `startAuthRequestListener`: al ver `status:'requesting'` el teléfono **genera** el código `(1000..9999)`, lo publica de vuelta como `status:'pending' + code` y notifica a la UI (notificación + diálogo). Compatibilidad con el flujo viejo (`status:'pending' + code` entrante).
  - `pushDeviceHeartbeatToFirebase` y `LanServer.buildStatusJson` publican `target_key`.
- **popup**: textos del modal actualizados ("el teléfono lo generará y te llegará por notificación"); si el usuario confirma antes de que el teléfono genere, se muestra "esperando generación" y se reintenta.

### B. CATEGORÍAS DE NOTAS NORMALIZADAS (Tarea 2)
- Clave canónica única: `'general' | 'tarea' | 'idea' | 'reflexion'`.
- **Android**: `normalizeCategory()` en `LockManager.kt` (tolera mayúsculas y acentos), aplicada al crear notas (`addNote`), al leer de Firebase y al cargar locales (migración). `ZenNote.category` default `"general"`. `ZenNotesModal.kt`: listas de categorías canónicas, `categoryLabel()` para mostrar "Tarea"/"Reflexión" y filtro por clave canónica.
- **Extensión**: `normalizeCategory()` en `background.js` (aplicada en `getNotes` y `addNote`) y en `notes.js` (al filtrar y etiquetar). Usa `normalize('NFD')` para quitar acentos.

### C. SCROLL EN ConfigScreen Y ZenScreen (Tarea 3)
- **`ZenScreen.kt`**: `.verticalScroll(rememberScrollState())` en el Column principal (L193) manteniendo `fillMaxSize` y `Arrangement.SpaceBetween`.
- **`ConfigScreen.kt`**: selector de aplicaciones con `heightIn(max = 240.dp)` + `LazyColumn` (la lista scrollea por sí sola dentro del límite y el padre aparte); el modal de Ajustes conserva su propio `verticalScroll`.

### D. MENÚ POMODORO CON VALOR DESTACADO (Tarea 4)
- **`ConfigScreen.kt`**: steppers rediseñados al estilo de la extensión:
  - "Descansos: **N** descansos" con el número en 22sp bold `ZenGreen`.
  - "Descanso (máx M): **X min**" con el valor en 22sp bold `ZenGreen`.
  - Botones `+/-` compactos de 32dp; respetan `computePomodoroLimits` (regla 25%).

### E. LOGO / "MODO ENFOQUE" / FRASE BAJO LA CÁMARA (Tarea 5)
- **`ZenScreen.kt`**: cabecera con `padding(top = 56.dp)` (antes 12dp) para que nada quede oculto bajo la cámara perforada / Dynamic Island; con el scroll de T3 el contenido se puede desplazar en cualquier pantalla.

### F. VERSIONADO
- **`app/build.gradle.kts`**: `versionCode = 241`, `versionName = "24.1.0"`.
- APK respaldado: `AntiProcrastinacion_v24_1.apk` (19,5 MB).

---

## 📁 4. Detalle de Archivos Modificados

### App Android
1. **[`LockManager.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LockManager.kt)**: `targetKey`, `userRef()` reescrito, `startAuthRequestListener` genera y publica el código (status requesting→pending), `normalizeCategory()` aplicada en `addNote`/lectura remota/`loadLocalNotes`, `ZenNote.category` default `"general"`, `pushDeviceHeartbeatToFirebase` con `target_key`.
2. **[`ZenNotesModal.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ZenNotesModal.kt)**: categorías canónicas + `categoryLabel()` + filtro canónico.
3. **[`ConfigScreen.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ConfigScreen.kt)**: selector de apps con `heightIn(max=240.dp)`, steppers Pomodoro rediseñados (valor destacado).
4. **[`ZenScreen.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ZenScreen.kt)**: `verticalScroll` en Column principal, cabecera con `padding(top = 56.dp)`.
5. **[`LanServer.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LanServer.kt)**: `target_key` en `buildStatusJson`.
6. **[`app/build.gradle.kts`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/build.gradle.kts)**: `versionCode = 241`, `versionName = "24.1.0"`.

### Extensión Chrome
7. **[`background.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/background.js)**: `pairedTargetKey`/`adoptPairedTargetKey`, `getTargetKey()` prioriza el nodo adoptado, `requestAuthCode` (status requesting), `pollForAuthCode`, `verifyAuthCode` (lee Firebase), adopción de `target_key` en `pollFirebaseSync` y LAN, `normalizeCategory()` en notas.
8. **[`popup.html`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.html)**: textos del modal 2FA ("el teléfono lo generará").
9. **[`popup.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.js)**: manejo de "esperando generación" del código y textos de error.
10. **[`notes.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/notes.js)**: `normalizeCategory()` al filtrar/etiquetar.

### Simulaciones (nueva carpeta `simulaciones/`)
11. **[`simular_2fa.mjs`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/simulaciones/simular_2fa.mjs)**: protocolo completo 2FA contra Firebase real (nodo `sim_2fa_v241`): solicitud → teléfono genera → polling → verificación → aprobación → rechazo → limpieza.
12. **[`simular_categorias.mjs`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/simulaciones/simular_categorias.mjs)**: normalización (8 casos) + guardado/lectura cruzada Android↔extensión en `sim_categorias_v241` + limpieza.
13. **[`verificar_scroll.mjs`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/simulaciones/verificar_scroll.mjs)**: revisión estática de patrones prohibidos (scroll anidado, LazyColumn sin altura acotada).
14. **[`simular_pomodoro_limites.mjs`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/simulaciones/simular_pomodoro_limites.mjs)**: replica `computePomodoroLimits` de Android/popup/background y compara (lógica pura, sin Firebase).

---

## 🔬 5. Verificaciones Realizadas

- `node --check` aprobado en `background.js`, `popup.js`, `notes.js` y en los 4 scripts de simulación.
- Compilación: `.\gradlew.bat assembleDebug --console=plain` → **BUILD SUCCESSFUL** (varias veces, tras cada tarea).
- `simulaciones/simular_2fa.mjs` → **6/6 PASS** (extensión solicita sin código, teléfono genera 2565, polling lo lee sin mostrarlo, verificación correcta → `approved`, rechazo de código mal escrito, limpieza del nodo).
- `simulaciones/simular_categorias.mjs` → **5/5 PASS** (8 casos de normalización, Firebase guarda `reflexion`, extensión filtra, Android filtra nota de la extensión, limpieza).
- `simulaciones/verificar_scroll.mjs` → **sin problemas estructurales** (scroll principal en ambas pantallas, LazyColumn acotada, modal con scroll propio).
- `simulaciones/simular_pomodoro_limites.mjs` → Android y popup.js devuelven los **mismos** límites en todos los casos; `background.js` (tabla fija) difiere en 57/59 puntos → **pendiente de alineación** (anotado).
- APK generado: `app\build\outputs\apk\debug\app-debug.apk` (19,5 MB, `versionCode = 241`).
- Respaldos: `AntiProcrastinacion_v24_1.apk` en la raíz.
- NOTA: las reglas de RTDB permiten leer/escribir sin token (verificado durante la simulación); el auth anónimo de Firebase está deshabilitado (`ADMIN_ONLY_OPERATION`). Se anota como observación de seguridad, no se modifica.

---

## ⚠️ 6. Acción Requerida (No es un bug)

- **Recargar la extensión** en `chrome://extensions` (botón 🔄 de la tarjeta "AntiProcrastinación").
- **Instalar el APK** (`adb install -r AntiProcrastinacion_v24_1.apk`) y probar en el teléfono:
  1. **2FA**: desde la extensión togglear Bloqueo Cruzado (activar y desactivar) → el teléfono debe generar el código y mostrarlo por notificación/diálogo → escribirlo en el PC → se aplica. Revisar `adb logcat -s ZEN_2FA`.
  2. **Categorías**: crear una nota en el teléfono con categoría "Reflexión" y en el PC con "Tarea" → en ambos dispositivos deben verse en la pestaña correcta.
  3. **Scroll**: en ConfigScreen expandir "APLICACIONES PERMITIDAS" y la tarjeta POMODORO → todo debe poder subir/bajar; en ZenScreen durante el enfoque, todo el contenido debe ser scrolleable.
  4. **Pomodoro**: el valor de descansos y minutos debe verse grande y claro (estilo extensión).
  5. **Dynamic Island**: durante el enfoque, el logo, "MODO ENFOQUE" y la frase no deben quedar bajo la cámara.

---

## 📌 7. Observaciones y pendientes

- **`background.js:893-900`** conserva la tabla fija de límites Pomodoro en lugar de la regla 25% (diferencias anotadas por `simular_pomodoro_limites.mjs`). NO es un fallo de v24.1 (la auditoría ya lo tenía como Tarea Pendiente); alinearlo cuando se trabaje esa parte.
- **Texto visible "Zen"/"Pomodoro"/"web"/"sincronizado"**: en pausa, lo realiza antigravity. NO tocar.
- **Seguridad**: reglas RTDB abiertas (lectura/escritura sin token) y auth anónimo deshabilitado — revisar reglas de Firebase para producción.
