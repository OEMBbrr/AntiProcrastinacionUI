# CONTEXTO IA — Estado actual del proyecto AntiProcrastinacion (V24.1)

> Este archivo es el punto de reanudación. Si el chat se compacta o se abre uno
> nuevo, leer este archivo ANTES de hacer cualquier cambio.

## Ruta del proyecto

```
C:\Users\USUARIO\Documents\AntiProcrastinacion
```

NO está en F:\. Los SDKs están en `F:\Codigo\a\` (solo herramientas).

## MÉTODO DE TRABAJO (obligatorio, así trabajamos aquí)

Reglas grabadas en `ORDEN_E_INSTRUCCIONES_DEL_USUARIO.txt` (ANTI-HALLUCINATION PROTOCOL):
1. **Leer primero** `PLAN_DE_ACCION_Y_ESPECIFICACIONES.md` antes de tocar nada.
2. **No asumir ni trabajar con dudas**: ante cualquier incertidumbre, hacer las preguntas necesarias ANTES de actuar.
3. **Nunca declarar éxito** sin una compilación limpia verificada y una instalación real por ADB confirmada.
4. **Nunca inventar** datos: todo lo que se reporta (builds, adb, bytes) debe ser verificado realmente.

**Ciclo de cada cambio (la orden exacta que da el usuario):**
1. El usuario da su **orden exacta** (texto literal). Se conserva palabra por palabra.
2. Se documenta el **estado anterior** (cómo estaba el código antes del cambio).
3. Se implementan los **cambios agregados**, archivo por archivo.
4. Se **compila** (`.\gradlew.bat assembleDebug --console=plain`) y se **instala** por adb en `114593744B102019`.
5. Se **respalda** el APK en la raíz: `AntiProcrastinacion_v24_1.apk` (y `AntiProcrastinacion.apk`).
6. Se **verifica** con `node --check`, `adb shell dumpsys` (versionCode), scripts de simulación, etc.
7. Se escribe/actualiza el **reporte de cambios** con el formato EXACTO (ver `cambios y reportes\REPORTE_CAMBIOS_V24.md` como plantilla).

**Reglas de trabajo actuales (confirmadas 10/08/2026):**
1. **Alcance**: este asistente trabaja SOLO con la extensión de Chrome (`extension_chrome/`) y el APK de Android (`app/`). La web (`pagina_web/`) y la parte iOS/Flutter las maneja **antigravity**: NO tocarlas ni recompilarlas.
2. **Sin git ni GitHub**: no se hacen commits, push ni operaciones de repositorio. Todo el trabajo queda local.
3. **Versión**: cada cambio = versión nueva. Actualmente estamos en **v24.1** (`versionCode = 241`, `versionName = "24.1.0"`).
4. **Orden de ejecución**: de lo más difícil a lo más fácil, pero SIEMPRE completando TODO.
5. **Tarea de textos visibles (quitar "zen"/"web"/"pomodoro"/"sincronizado")**: en PAUSA, la está realizando antigravity. NO tocar hasta que el usuario avise; al terminar, verificar lo que el usuario indique.
6. **Simulaciones**: cada cambio debe validarse con un script en `simulaciones/` (conectan a la misma Firebase RTDB SOLO en nodos `sim_*`, con token anónimo, y **siempre limpian**). No escribir en nodos reales de usuario.
7. **Orden del código**: al terminar cada cambio, compilar y revisar sintaxis (`node --check`, `gradlew`, etc.).

## Comandos imprescindibles

```powershell
# Compilar APK debug
cd C:\Users\USUARIO\Documents\AntiProcrastinacion
.\gradlew.bat assembleDebug --console=plain

# Verificar JS de la extensión (siempre tras editar)
node --check extension_chrome\background.js
node --check extension_chrome\popup.js
node --check extension_chrome\notes.js

# Ejecutar simulaciones (Firebase RTDB, nodos sim_* con limpieza automática)
node simulaciones\simular_2fa.mjs
node simulaciones\simular_categorias.mjs
node simulaciones\verificar_scroll.mjs
node simulaciones\simular_pomodoro_limites.mjs

# Instalar en el teléfono (serial 114593744B102019)
F:\Codigo\a\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk

# Verificar versión instalada
F:\Codigo\a\platform-tools\adb.exe shell dumpsys package com.antiprocrastinacion.lock | Select-String versionCode
```

- versionCode = 241, versionName = "24.1.0" (`app/build.gradle.kts`)
- package: `com.antiprocrastinacion.lock`
- Tras compilar, respaldar el APK en la raíz como `AntiProcrastinacion_v24_1.apk`
- Reporte de cambios en: `cambios y reportes\REPORTE_CAMBIOS_V24_1.md`
- La extensión de Chrome se recarga en `chrome://extensions`

## V24.1 — CAMBIOS REALIZADOS (5 tareas del plan, TODAS implementadas y compiladas)

> Plan maestro: `PLAN_DE_IMPLEMENTACION_V24_1.md`. Reporte: `REPORTE_CAMBIOS_V24_1.md`.

### T1. Rediseño del 2FA del Bloqueo Cruzado (el TELÉFONO genera el código)
- **Causa raíz**: los targets de Android y la extensión no coincidían → el código nunca llegaba.
- **Extensión** (`background.js`): nueva `pairedTargetKey` (nodo adoptado del teléfono), `adoptPairedTargetKey()`, `getTargetKey()` prioriza el nodo adoptado, `requestAuthCode` SOLO publica `status:'requesting'` (sin código), `pollForAuthCode()` (polling 1s, 30 intentos) espera `status:'pending'+code` del teléfono y lo guarda en memoria SIN mostrarlo, `verifyAuthCode` lee Firebase y compara, `pollFirebaseSync` adopta `deviceData.target_key`.
- **Android** (`LockManager.kt`): `targetKey` público (misma limpieza que la extensión), `userRef()` lo usa, `startAuthRequestListener` genera el código (`1000..9999`) cuando ve `status:'requesting'` y lo publica como `status:'pending'+code`, `pushDeviceHeartbeatToFirebase` y `LanServer.buildStatusJson` publican `target_key`.
- **popup**: textos actualizados ("el teléfono generará el código"), estado "esperando generación" si aún no hay código.
- Compatibilidad: si llega `status:'pending'+code` de una extensión vieja, se muestra igual.

### T2. Categorías de notas normalizadas
- **Causa raíz**: Android guardaba `"General"/"Tarea"/"Idea"/"Reflexión"` (capitalizado/acento) y la extensión `'general'/'tarea'/'idea'/'reflexion'` (minúsculas) → la categoría cruzada se perdía.
- Clave canónica compartida: `'general' | 'tarea' | 'idea' | 'reflexion'`.
- `LockManager.kt`: `normalizeCategory()` (tolera mayúsculas/acentos), aplicado en `addNote`, lectura remota y `loadLocalNotes` (migración). `ZenNote.category` default `"general"`.
- `ZenNotesModal.kt`: listas canónicas, `categoryLabel()` para mostrar "Tarea"/"Reflexión", filtro por claves canónicas.
- `background.js` y `notes.js`: `normalizeCategory()` al leer/escribir (tolera acentos con NFD).

### T3. Scroll en ConfigScreen y ZenScreen
- `ZenScreen.kt`: `verticalScroll(rememberScrollState())` en el Column principal (L193) para que cabecera + frase + temporizador + botones se vean en pantallas pequeñas.
- `ConfigScreen.kt`: selector de aplicaciones con `heightIn(max = 240.dp)` + `LazyColumn` (antes altura fija 160dp) para que la lista scrollee por sí sola y el padre aparte; el modal de Ajustes mantiene su propio `verticalScroll`.

### T4. Menú Pomodoro con valor destacado
- `ConfigScreen.kt`: steppers rediseñados estilo extensión: "Descansos: **N** descansos" y "Descanso (máx M): **X min**" con el valor en 22sp bold ZenGreen, botones `+/-` de 32dp.
- Se respeta `computePomodoroLimits` (regla 25%) de `LockManager.kt:378`.
- Simulación: Android y popup.js devuelven los MISMOS límites; `background.js:893` conserva la tabla fija antigua (57/59 puntos difieren) → **pendiente de alineación** (ya anotado en la auditoría, no es fallo de v24.1).

### T5. Logo / "MODO ENFOQUE" / frase bajo la cámara
- `ZenScreen.kt`: cabecera con `padding(top = 56.dp)` (antes 12dp) para que nada quede oculto bajo la cámara perforada / Dynamic Island.

### Versionado
- `app/build.gradle.kts`: `versionCode = 241`, `versionName = "24.1.0"`.
- APK respaldado en la raíz: `AntiProcrastinacion_v24_1.apk`.
- Simulaciones en `simulaciones/` (todas PASS y con limpieza).

## TAREAS EN PAUSA (NO TOCAR)

- **Textos visibles (quitar "zen"/"web"/"pomodoro"/"sincronizado")**: la está realizando antigravity. NO tocar hasta que el usuario avise; al terminar, verificar lo que el usuario indique.
- **Pendiente de alineación (anotado, no urgente)**: `background.js:893` usa tabla fija de límites Pomodoro en vez de la regla 25% (coincide con Android/popup solo en 2/59 puntos). Alinearlo cuando se trabaje esa parte.

## Arquitectura rápida

- **App Android (Compose)**: `app/src/main/java/com/antiprocrastinacion/lock/`
  - `LockManager.kt` — lógica de bloqueo, Pomodoro, Firebase RTDB, listeners, 2FA (teléfono genera el código), categorías normalizadas.
  - `LockMonitoringService.kt` — overlay/servicio + notificaciones (código 2FA).
  - `MainActivity.kt` — actividad principal + diálogo del código 2FA.
  - `LanServer.kt` — servidor LAN puerto 8888 (HTTP + WebSocket); publica `target_key`.
  - `ui/screens/ZenScreen.kt` — pantalla de bloqueo/enfoque (scroll + padding cabecera 56dp).
  - `ui/screens/ConfigScreen.kt` — configuración (inicio del enfoque) + steppers Pomodoro rediseñados.
  - `ui/screens/ZenNotesModal.kt`, `ui/theme/*` — notas y tema.
- **Extensión Chrome**: `extension_chrome/`
  - `background.js` — service worker: Firebase, bloqueo web, pomodoro, 2FA (nodo adoptado), categorías.
  - `popup.html/js/css` — popup con setup Pomodoro + modal auth-code (textos "el teléfono genera").
  - `notes.html/js`, `login.html/js`, `blocked.html/js`.
- **Simulaciones**: `simulaciones/simular_2fa.mjs`, `simular_categorias.mjs`, `verificar_scroll.mjs`, `simular_pomodoro_limites.mjs`.
- **Firebase RTDB**: `https://antiprocrastinacion-26975-default-rtdb.firebaseio.com`,
  nodos `/users/<key>/...`: `lock_state`, `config`, `auth_request`, `tregua_request`,
  `device_info` (incluye `target_key`), `notes`, `profile`. Clave por correo Google → sync key → UID.
  - NOTA: las reglas RTDB permiten leer/escribir sin token (se comprobó al simular). El auth anónimo de Firebase está deshabilitado (`ADMIN_ONLY_OPERATION`).

## Archivos de instrucciones clave en la raíz
- `ORDEN_E_INSTRUCCIONES_DEL_USUARIO.txt` — protocolo anti-alucinación y versionado.
- `PLAN_DE_ACCION_Y_ESPECIFICACIONES.md` — especificaciones del proyecto.
- `PLAN_DE_IMPLEMENTACION_V24_1.md` — plan maestro de las 5 tareas v24.1.
- `CONTEXTO_PROYECTO_IA.txt` — arquitectura general.
- `cambios y reportes\REPORTE_CAMBIOS_V2X.md` — reportes por versión (plantilla).

## Aviso importante

Si el contexto se pierde otra vez: leer ESTE archivo, `REPORTE_CAMBIOS_V24_1.md` y
`ORDEN_E_INSTRUCCIONES_DEL_USUARIO.txt` antes de tocar nada. La ruta del proyecto es
`C:\Users\USUARIO\Documents\AntiProcrastinacion` (NO buscar en F:).
