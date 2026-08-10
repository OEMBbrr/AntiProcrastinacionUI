# CONTEXTO IA — Estado actual del proyecto AntiProcrastinacion (V24)

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
5. Se **respalda** el APK en la raíz: `AntiProcrastinacion_v24.apk` y `AntiProcrastinacion.apk`.
6. Se **verifica** con `node --check`, `adb shell dumpsys` (versionCode), etc.
7. Se escribe/actualiza el **reporte de cambios** con este formato EXACTO (ver `cambios y reportes\REPORTE_CAMBIOS_V24.md` como plantilla):

```
# 📝 REPORTE DE CAMBIOS Y AUDITORÍA DE CÓDIGO - VERSIÓN vX.0.0
- Fecha de Modificación / Versión del Proyecto / Ubicación del Reporte
## 📩 1. Órdenes Exactas del Usuario        (citas literales del usuario)
## 🔍 2. Estado Anterior                    (cómo estaba el código antes)
## 🛠️ 3. Cambios Realizados y Soluciones    (A/B/C/D... por tema, archivo a archivo)
## 📁 4. Detalle de Archivos Modificados    (ruta completa + qué cambió)
## 🔬 5. Verificaciones Realizadas          (build, node --check, adb, bytes reales)
## ⚠️ 6. Acción Requerida                    (recargar extensión, probar en el teléfono)
```

**Reglas de trabajo actuales (confirmadas 10/08/2026):**
1. **Alcance**: este asistente trabaja SOLO con la extensión de Chrome (`extension_chrome/`) y el APK de Android (`app/`). La web (`pagina_web/`) y la parte iOS/Flutter las maneja **antigravity**: NO tocarlas ni recompilarlas.
2. **Sin git ni GitHub**: no se hacen commits, push ni operaciones de repositorio. Todo el trabajo queda local.
3. **Versión**: los pendientes son mejoras (no añadiduras), se mantienen en **v24** (no subir `versionCode`/`versionName` a menos que el usuario lo ordene explícitamente).
4. **Orden de ejecución**: de lo más difícil a lo más fácil, pero SIEMPRE completando TODO (ningún pendiente queda de lado).
5. **Tarea de textos visibles (quitar "zen"/"web"/"pomodoro"/"sincronizado")**: en PAUSA, la está realizando antigravity. NO tocar hasta que el usuario avise; al terminar, verificar lo que el usuario indique.
6. **Orden del código**: al terminar cada cambio, compilar y revisar sintaxis (`node --check`, `gradlew`, etc.) para dejar todo el código ordenado y sin errores.

**Versionado**: cada cambio = versión nueva (`versionCode`/`versionName` en `app/build.gradle.kts`). Actualmente estamos en **v24** y los pendientes son mejoras, por lo que se mantienen en **v24** (no subir versión salvo orden expresa del usuario).

## Comandos imprescindibles

```powershell
# Compilar APK debug
cd C:\Users\USUARIO\Documents\AntiProcrastinacion
.\gradlew.bat assembleDebug --console=plain

# Verificar JS de la extensión (siempre tras editar)
node --check extension_chrome\background.js
node --check extension_chrome\popup.js
node --check extension_chrome\notes.js

# Instalar en el teléfono (serial 114593744B102019)
F:\Codigo\a\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
# (también sirve: F:\Codigo\a\android-sdk\platform-tools\adb.exe)

# Verificar versión instalada
F:\Codigo\a\platform-tools\adb.exe shell dumpsys package com.antiprocrastinacion.lock | Select-String versionCode
```

- versionCode = 24, versionName = "24.0.0" (`app/build.gradle.kts`)
- package: `com.antiprocrastinacion.lock`
- Tras compilar, respaldar el APK en la raíz como `AntiProcrastinacion_v24.apk` y `AntiProcrastinacion.apk`
- Reporte de cambios en: `cambios y reportes\REPORTE_CAMBIOS_V24.md` (actualizarlo al finalizar)
- La extensión de Chrome se recarga en `chrome://extensions`

## TAREAS PENDIENTES (5) — de más difícil a más fácil; se ejecutan TODAS (sin dejar nada de lado)

> **La tarea 2 (quitar textos visibles) está en PAUSA**: la está realizando antigravity. NO tocar hasta que el usuario avise; al terminar, verificar lo que el usuario indique.

### 1. Límite de descansos: regla del 25%
El usuario quiere que la cantidad de descansos permitidos sea **≤ 25% del tiempo
total de enfoque**, y que el **máximo de tiempo por descanso sea 30 minutos**.

Ejemplos que dio (enfoque de 4 h = 240 min):
- 4 descansos de 15 min (60 min = 25%) ✓
- 3 de 20 min (60 min = 25%) ✓
- 6 de 10 min (60 min = 25%) ✓
- 6 de 20 min (120 min = 50%) ✗ — HOY SE PERMITE, hay que bloquearlo

Regla a implementar:
```
maxRestMinutes = min(30, floor(totalMin * 0.25))          # por descanso
maxRestCount   = floor(totalMin * 0.25 / restMinutes)     # según duración elegida
```

Ubicación del código actual:
- `app/src/main/java/com/antiprocrastinacion/lock/LockManager.kt:377`
  `fun computePomodoroLimits(totalMinutes: Int): Pair<Int, Int>` — devuelve
  `(maxRest, maxRestCount)` por rangos fijos: `<10→0/0, ≤30→5/1, ≤60→10/2, ≤120→15/3, ≤180→20/4, else→20/6`.
  **Esta función debe cambiar**: ahora `maxRestCount` depende de `restMinutes`,
  así que hay que pasar `restMinutes` como parámetro (o recalcular en el setter).
- `LockManager.kt:344` `pomodoroRestCount` setter: `coerceIn(1, 6)` — revisar tope.
- `LockManager.kt:387` `buildPomodoroSchedule(...)` — usa `computePomodoroLimits`.
- `LockManager.kt:691` `startConfigListener` — aplica `pomodoro_rest_count` con `coerceIn(1, 6)`.
- `ConfigScreen.kt:962` — `val (pomodoroMaxRest, pomodoroMaxRestCount) = lockManager.computePomodoroLimits(focusTotalMinutes)` y los steppers (líneas ~1037-1100). Si cambia la firma, actualizar aquí.
- `extension_chrome/popup.js:181,190` — `computePomodoroLimits(getFocusTotalMinutes())` (espejo en background.js).

### 2. Quitar textos visibles con "zen", "web", "pomodoro", "sincronizado"
Estas palabras son claves internas del proyecto; NO deben verse en la interfaz.
Excepciones explícitas del usuario:
- En la sección de **versión** todo debe quedar en blanco.
- En **"Mi Cuenta"** SÍ debe indicarse si está sincronizado o no.

Textos visibles localizados:
- `ConfigScreen.kt:547` — `"Oscuro (estilo web)"` / `"Claro Zen"` → cambiar a
  `"Oscuro Zen"` o solo `"Modo oscuro"` sin descripción (decisión del usuario: preferible "Modo oscuro").
- `ConfigScreen.kt:626` — `"Pomodoro Sincronizado"` (título toggle).
- `ConfigScreen.kt:630` — `"Comparte ciclos de trabajo y descanso entre PC y teléfono."`
- `ConfigScreen.kt:988` — `"POMODORO"` (título tarjeta).
- `ConfigScreen.kt:994` — `"Comparte ciclos de trabajo y descanso con la extensión."`
- `ConfigScreen.kt:1014` — `"El Pomodoro solo está disponible para enfoques de 10 minutos o más..."`
- `ConfigScreen.kt:1325` — `"Versión v24.0.0 (Pomodoro Sincronizado, Biometría y WebSocket LAN)"` → dejar `"Versión v24.0.0"` o similar sin las palabras prohibidas.
- `ZenScreen.kt:299` — `"DESCANSO POMODORO"` (banner).
- `ZenNotesModal.kt:290` — `"...se mantendrán sincronizadas."`
- `extension_chrome/popup.html:50` — `"☕ DESCANSO POMODORO — Tregua libre activa"`
- `extension_chrome/popup.html:77` — `"POMODORO"` (título setup)
- `extension_chrome/popup.html:196` — `"Pomodoro Sincronizado"` (toggle)
- `extension_chrome/popup.html:168` — `"Mis Notas Zen"`
- `extension_chrome/notes.js:155` — `"✨ Guardar Nota Zen"`
- Revisar también `login.js`, `notes.html`, `login.html`, `styles.css` y `blocked.html`/`blocked.js`.

NOTA: los `Log.d("ZEN_SYNC", ...)` y los identificadores internos `ZenScreen`,
`ZenGreen`, `ZenSage`, `ZenOlive`, `ZenCharcoal`, `ZenWhite`, `ZenCoral`,
`ZenSoftGreen`, `ZenSoftBlue`, `ZenSoftCoral`, `CreamBackground`, `"ZEN-XXXX"`
(PIN), prefijo `pomodoro_*` en Firebase, etc. NO son textos visibles: NO tocar.

### 3. ZenScreen: logo/título/frase chocan con la Dynamic Island
Al activar el modo enfoque, el logo, el texto "MODO ENFOQUE" y la frase
motivadora quedan demasiado arriba y chocan con la cámara/isla dinámica.
- `ZenScreen.kt:202-263` — cabecera ("Cabecera Zen") con logo + "MODO ENFOQUE" + Card de frases.
- Hay que desplazarla hacia abajo (añadir padding superior ~50-60dp en el `Column`
  exterior de la línea 193, o en la cabecera). El `Scaffold`/`Column` usa `Arrangement.SpaceBetween`.

### 4. Bug: al DESACTIVAR Bloqueo Cruzado desde la extensión NO llega la notificación
El flujo 2FA envía el código al teléfono al ACTIVAR, pero al DESACTIVAR desde la
extensión no llega la notificación del código al teléfono.
- `extension_chrome/popup.js:224-229` — el listener del switch llama
  `requestAuthForToggle(value)` para cualquier valor (debería funcionar al desactivar).
- `extension_chrome/background.js:448-481` — `requestAuthCode` publica en
  `/users/<target>/auth_request.json` con `{id, requester:'chrome_extension', status:'pending', code, requested_at, expires_at}`.
- `LockManager.kt:763-790` — `startAuthRequestListener`: filtra `requester != "chrome_extension"`, `reqId == lastAuthRequestId`, `status == "pending" && code.isNotEmpty()`.
- `LockMonitoringService.kt:24-25,60,104-125` — `AUTH_CHANNEL_ID="anti_proc_auth_channel"`, `AUTH_NOTIFICATION_ID=2001`, `showAuthCodeNotification(code)`.
- `MainActivity.kt:39,98,146-157` — `authCodeRequest` + AlertDialog con el código.
- Sospecha: revisar si al desactivar se corta antes por `crossDeviceLockEnabled=false`
  (ver `pushLockStateToFirebase` en `LockManager.kt:840` que hace `if (!crossDeviceLockEnabled) return;`)
  o si el `auth_request` sí se publica pero el listener del teléfono lo ignora.
  Probar manualmente desactivar desde el popup y ver logs `adb logcat -s ZEN_2FA`.

### 5. Scroll vertical en la app
Hay contenido que queda fuera cuando se expande algo.
- `ConfigScreen.kt:170` — el `Column` principal tiene `Arrangement.SpaceBetween` y
  NO scrollea; el bloque central usa `.weight(1f)` (línea 759) pero con muchos
  elementos (tarjeta Pomodoro expandida, apps, frase) se corta.
  Añadir `verticalScroll(rememberScrollState())` o pasar a `LazyColumn`.
- `ZenScreen.kt:193` — `Column` principal tampoco scrollea; al aparecer el banner de
  descanso + tregua + apps se puede cortar.
- El modal de Ajustes ya scrollea (`ConfigScreen.kt:481`).

## ESTADO COMPLETADO (V24 rediseño — ya hecho y compilado)

- Pomodoro sincronizado: fases trabajo/descanso absolutas, `N` descansos → `N+1`
  bloques de trabajo iguales, empieza y termina en trabajo.
  Estructuras: `data class PomodoroPhase(type, startTime, endTime)`, `KEY_POMODORO_PHASES`,
  `buildPomodoroSchedule`, `currentPomodoroPhase`, `isPomodoroRestPhase`,
  `unlockForRest` (tregua libre durante descanso), `endPomodoroRestNow`.
- Config: tarjeta POMODORO en la pantalla de inicio del enfoque con preview y
  límites dinámicos; toggle en modal Ajustes; steppers eliminados del modal Ajustes.
- 2FA Bloqueo Cruzado: switch SIEMPRE con `requireBiometricAuth`; la extensión
  pide código → notificación en el teléfono → verificación en el popup
  (modal `#auth-code-modal`).
- Banner "DESCANSO POMODORO — Tregua libre activa" en ZenScreen y en el popup.
- LanServer publica `pomodoro_rest_count` y `phases`.
- Corrección de compilación: `ZenScreen.kt:100` → `phase?.endTime?.minus(now) ?: 0L`.
- Verificado: `node --check` OK, BUILD SUCCESSFUL, `adb install` Success,
  versionCode=24, respaldos `AntiProcrastinacion_v24.apk` / `AntiProcrastinacion.apk`.
- Códigos 2FA ya son aleatorios: `background.js:452` → `String(Math.floor(1000 + Math.random() * 9000))`.

## Arquitectura rápida

- **App Android (Compose)**: `app/src/main/java/com/antiprocrastinacion/lock/`
  - `LockManager.kt` — lógica de bloqueo, Pomodoro, Firebase RTDB, listeners.
  - `LockMonitoringService.kt` — overlay/servicio + notificaciones (código 2FA).
  - `MainActivity.kt` — actividad principal + diálogo del código 2FA.
  - `LanServer.kt` — servidor LAN puerto 8888 (HTTP + WebSocket).
  - `ui/screens/ZenScreen.kt` — pantalla de bloqueo/enfoque.
  - `ui/screens/ConfigScreen.kt` — configuración (inicio del enfoque).
  - `ui/screens/ZenNotesModal.kt`, `ui/theme/*` — notas y tema.
- **Extensión Chrome**: `extension_chrome/`
  - `background.js` — service worker: Firebase, bloqueo web, pomodoro, 2FA.
  - `popup.html/js/css` — popup con setup Pomodoro + modal auth-code.
  - `notes.html/js`, `login.html/js`, `blocked.html/js`.
- **Firebase RTDB**: `https://antiprocrastinacion-26975-default-rtdb.firebaseio.com`,
  nodos `/users/<key>/...`: `lock_state`, `config`, `auth_request`, `tregua_request`,
  `device_info`, `notes`, `profile`. Clave por correo Google → sync key → UID.

## Archivos de instrucciones clave en la raíz
- `ORDEN_E_INSTRUCCIONES_DEL_USUARIO.txt` — protocolo anti-alucinación y versionado.
- `PLAN_DE_ACCION_Y_ESPECIFICACIONES.md` — especificaciones del proyecto.
- `CONTEXTO_PROYECTO_IA.txt` — arquitectura general.
- `INSTRUCCION_MENU_PERFIL_Y_AJUSTES.txt` — menú perfil/ajustes.
- `cambios y reportes\REPORTE_CAMBIOS_V2X.md` — reportes por versión (plantilla).

## Aviso importante

Si el contexto se pierde otra vez: leer ESTE archivo, `REPORTE_CAMBIOS_V24.md` y
`ORDEN_E_INSTRUCCIONES_DEL_USUARIO.txt` antes de tocar nada. La ruta del proyecto es
`C:\Users\USUARIO\Documents\AntiProcrastinacion` (NO buscar en F:).
