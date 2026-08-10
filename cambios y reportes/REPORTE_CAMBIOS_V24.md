# 📝 REPORTE DE CAMBIOS Y AUDITORÍA DE CÓDIGO - VERSIÓN v24.0.0 (REDISEÑO POMODORO + 2FA)

- **Fecha de Modificación**: 10 de Agosto de 2026
- **Versión del Proyecto**: `v24.0.0` (`versionCode = 24`, `versionName = "24.0.0"`)
- **Ubicación del Reporte**: `C:\Users\USUARIO\Documents\AntiProcrastinacion\cambios y reportes\REPORTE_CAMBIOS_V24.md`

---

## 📩 1. Órdenes Exactas del Usuario

> *"Quiero que hagas otra nueva v24... el modo Pomodoro la configuración no va a estar en ajustes (aunque ahí se activa), sino que se configuraría en la pantalla de inicio del modo enfoque. Si tengo activado el modo pomodoro me sale un apartado en el que puedo abrir y configurar, y dependiendo de la cantidad de minutos o de hora en el modo enfoque permite colocar más o menos modos de descansos y configurarlos en más o menos tiempo. Si tengo un modo de enfoque de 4 horas y configuro el descanso en 15 minutos pueda no tener dos sino cuatro descansos. Si es menos de 10 min no existe el modo pomodoro, y si es de 10 a 30 min el descanso máximo es de 5 min."*

> *"La biometría va a ser siempre para el modo bloqueo cruzado... si es desde la computadora que me pida un código que llegue a una notificación del teléfono y si es correcto sí, como un tipo de autenticación de dos pasos. Si es desde el teléfono, contraseña del teléfono o biometría."*

**Decisiones confirmadas por el usuario (aclaraciones):**
- En la fase de **descanso** = **Tregua libre** (desbloqueo automático sin escribir frase).
- La estructura **empieza y termina en TRABAJO**: N descansos → **N+1 bloques de trabajo iguales** (ej. 4 h + 2 descansos = 3 bloques de ~70 min).
- Pomodoro se **activa** en Ajustes pero se **configura** en la pantalla de inicio del enfoque.
- El toggle de Bloqueo Cruzado exige **SIEMPRE** autenticación (haya o no sesión de enfoque): en teléfono biometría/PIN; en PC código por notificación (2 pasos).

---

## 🔍 2. Estado Anterior (V24 rectificada)

1. **Pomodoro se configuraba en el modal de Ajustes** (switch + steppers Trabajo/Descanso), sin relación con la duración del enfoque ni con bloques trabajo/descanso.
2. **No existían fases de descanso**: el descanso era solo un número de minutos aislado, no se programaban bloques de trabajo/descanso dentro del enfoque.
3. **Sin tregua libre**: no había desbloqueo automático durante el descanso; toda tregua exigía resolver un reto.
4. **Bloqueo Cruzado sin 2FA en el PC**: el switch de la extensión se cambiaba directamente, sin verificación en el teléfono.
5. **Biometría solo con sesión activa** (`isFocusSessionActive`): al activar/desactivar Bloqueo Cruzado sin enfoque en curso no pedía nada.
6. La extensión desconocía `pomodoro_rest_count` y las `phases` (el estado LAN/Firebase no las incluía).

---

## 🛠️ 3. Cambios Realizados y Soluciones Implementadas (V24 rediseñada)

### A. POMODORO CONFIGURADO EN LA PANTALLA DE INICIO DEL ENFOQUE
- **Reglas dinámicas por duración** (espejo Android = extensión):
  - Enfoque **< 10 min** → NO existe Pomodoro (aviso en la UI).
  - **10-30 min** → descanso máx **5 min**, máx **1 descanso**.
  - **30-60 min** → máx 10 min, máx 2 descansos.
  - **60-120 min** → máx 15 min, máx 3 descansos.
  - **120-180 min** → máx 20 min, máx 4 descansos.
  - **> 180 min** (ej. 4 h) → máx 20 min, **hasta 6 descansos** (el usuario puede elegir, p. ej. 4 descansos de 15 min en un enfoque de 4 h).
- **Estructura de bloques**: N descansos → **N+1 bloques de trabajo iguales**; `workMs = (total - N*restMs)/(N+1)`. Empieza y termina en TRABAJO.
- **`LockManager.kt`**: `data class PomodoroPhase(type, startTime, endTime)`; prefs `pomodoro_rest_count` y `pomodoro_phases_json`; `computePomodoroLimits(total)`; `buildPomodoroSchedule(start, total, restCount, restMinutes)`; `currentPomodoroPhase(now)`; `isPomodoroRestPhase`; `startLock` construye las fases si `pomodoroEnabled && total >= 10`; `stopLock` las limpia.
- **`ConfigScreen.kt`**: tarjeta "POMODORO" expandible en la pantalla de inicio del enfoque (junto al selector HORAS:MINUTOS) con steppers de **nº de descansos** y **duración del descanso**, límites dinámicos según la duración elegida y vista previa del reparto de bloques (con aviso si los bloques de trabajo quedan < 10 min). Los steppers se **eliminaron del modal de Ajustes** (solo queda el switch de activación).
- **Extensión `background.js`**: `pomodoroRestCount`, `pomodoroPhases`, `computePomodoroLimits`, `buildPomodoroSchedule`, `currentPomodoroPhase`, `broadcastPhase`; `startTimer` construye y persiste las fases; `pushLockStateToFirebase` publica `phases` y `pomodoro_enabled`; `pollFirebaseSync` replica las fases remotas y lee `pomodoro_rest_count`.
- **Extensión `popup.html`/`popup.js`**: apartado POMODORO en el setup (solo visible con el Pomodoro activo) con steppers de descansos y duración, límites según HORAS:MINUTOS, aviso de "< 10 min" y vista previa de bloques; banner "☕ DESCANSO POMODORO" durante la fase de descanso.

### B. TREGUA LIBRE DURANTE EL DESCANSO (rest phase)
- **`LockManager.kt`**: `unlockForRest()` desbloquea hasta el final de la fase de descanso **sin** reto ni cooldown (`cooldownEndTime = 0`); `endPomodoroRestNow()` termina el descanso anticipadamente y re-programa las fases restantes (vuelve a trabajo).
- **`ZenScreen.kt`**: el ticker detecta la fase (`isPomodoroRestPhase`) y llama `unlockForRest()` automáticamente; banner verde "DESCANSO POMODORO — Tregua libre activa" con cuenta regresiva y botón "Terminar descanso (volver a trabajo)". Al terminar el descanso se re-bloquea.
- **Extensión `background.js`**: `startBackgroundTimer` detecta el paso a descanso y **libera los sitios bloqueados** (`updateNetRules`), re-bloquea al volver a trabajo y difunde `pomodoroPhaseChanged`.

### C. BLOQUEO CRUZADO CON 2 PASOS (2FA) Y BIOMETRÍA SIEMPRE
- **PC → teléfono (2 pasos)**: al togglear Bloqueo Cruzado en la extensión, `requestAuthCode` genera un código de 4 dígitos (válido 3 min) y lo publica en `/users/<target>/auth_request`. El teléfono lo muestra en una **notificación de alta prioridad** (`LockMonitoringService`, canal `anti_proc_auth_channel`, id 2001) y en un diálogo (`MainActivity`). El usuario lo escribe en el popup y `verifyAuthCode` lo valida antes de aplicar el toggle (`setSetting('crossDeviceLock')`).
- **Teléfono**: el switch de Bloqueo Cruzado ahora usa `requireBiometricAuth` **SIEMPRE** (biometría fuerte o PIN del dispositivo), sin depender de `isFocusSessionActive`.
- **`LockManager.kt`**: `startAuthRequestListener(callback)` escucha `auth_request` con `requester == 'chrome_extension'`, filtra por `lastAuthRequestId` para evitar ecos.
- **`popup.html`/`popup.js`**: modal `#auth-code-modal` con estado "📱 Revisa la notificación", input de 4 dígitos y manejo de error/cancelación (revertir el switch si se cancela).

### D. SINCRONIZACIÓN DE FASES (Firebase + LAN)
- **`pushLockStateToFirebase` (Android y extensión)** incluyen `phases` (start_ms/end_ms/type) y `pomodoro_enabled`; `lockStateListener` de Android aplica las fases enviadas por la extensión y `pollFirebaseSync` de la extensión replica las del teléfono.
- **`LanServer.kt`** `buildStatusJson` ahora envía `pomodoro_rest_count` y `phases`; el cliente WebSocket de la extensión los aplica en vivo (import `JSONArray` añadido).

---

## 📁 4. Detalle de Archivos Modificados

### App Android
1. **[`LockManager.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LockManager.kt)**: `PomodoroPhase`, prefs `pomodoro_rest_count`/`pomodoro_phases_json`, `computePomodoroLimits`, `buildPomodoroSchedule`, `currentPomodoroPhase`, `isPomodoroRestPhase`, `unlockForRest`, `endPomodoroRestNow`, `startLock`/`stopLock` con schedule, `pushLockStateToFirebase` con fases, listener remoto con fases, `pomodoro_rest_count` en config remota y `startAuthRequestListener`.
2. **[`ConfigScreen.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ConfigScreen.kt)**: tarjeta POMODORO en el setup de enfoque (steppers dinámicos + vista previa), steppers eliminados del modal Ajustes, Bloqueo Cruzado siempre con biometría.
3. **[`ZenScreen.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ZenScreen.kt)**: detección de fase descanso, banner "DESCANSO POMODORO (Tregua libre)", botón "Terminar descanso".
4. **[`MainActivity.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/MainActivity.kt)**: `startAuthRequestListener` y diálogo con el código 2FA.
5. **[`LockMonitoringService.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LockMonitoringService.kt)**: canal `anti_proc_auth_channel` (IMPORTANCE_HIGH) y notificación del código (`AUTH_NOTIFICATION_ID = 2001`).
6. **[`LanServer.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LanServer.kt)**: `pomodoro_rest_count` + `phases` en el estado JSON del WebSocket (import `JSONArray`).

### Extensión Chrome
7. **[`background.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/background.js)**: `pomodoroRestCount`, `pomodoroPhases`, `pendingAuth`, `computePomodoroLimits`/`buildPomodoroSchedule`/`currentPomodoroPhase`, `broadcastPhase`, `startTimer`/`stopTimer` con fases, `pushLockStateToFirebase` con fases, `requestAuthCode`/`verifyAuthCode`, WS LAN con fases y rest count, `pollFirebaseSync` con fases y rest count, `getState` con fase actual.
8. **[`popup.html`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.html)**: apartado POMODORO en el setup, banner de descanso, modal 2FA (`#auth-code-modal`), steppers de Ajustes eliminados.
9. **[`popup.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.js)**: config Pomodoro en setup (límites dinámicos + vista previa), flujo 2FA (request/verify/cancel), banner de descanso, reflejo de `pomodoroPhaseChanged`/`settingsChanged`.
10. **[`styles.css`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/styles.css)**: estilos del apartado Pomodoro del setup, banner de descanso y modal 2FA (`#auth-code-input`, `.auth-pending`, `.auth-error`).

---

## 🔬 5. Verificaciones Realizadas

- `node --check` aprobado en `background.js`, `popup.js` y `notes.js`.
- Compilación: `.\gradlew.bat assembleDebug --console=plain` → **BUILD SUCCESSFUL** (1 error corregido: `phase` nullable en `ZenScreen` → `phase?.endTime?.minus(now)`; recompilado sin warnings).
- Instalación: `adb install -r app-debug.apk` → **Success** en `114593744B102019` (TECNO LI7); `dumpsys package` confirma `versionCode=24` / `versionName=24.0.0`.
- Respaldos actualizados en la raíz: `AntiProcrastinacion_v24.apk` (20.629.719 bytes) y `AntiProcrastinacion.apk`.

---

## ⚠️ 6. Acción Requerida (No es un bug)

- **Recargar la extensión** en `chrome://extensions` (botón 🔄 de la tarjeta "AntiProcrastinación").
- Verificar en el teléfono:
  - **Config Pomodoro en el inicio del enfoque**: activar Pomodoro en Ajustes → aparece el apartado POMODORO junto al selector de tiempo; probar con 4 h (debe permitir hasta 6 descansos) y con 20 min (máx descanso 5 min, 1 descanso); con 9 min sale el aviso de "10 minutos o más".
  - **Tregua libre en descanso**: iniciar un enfoque largo con Pomodoro y, cuando empiece el descanso, comprobar que el teléfono queda desbloqueado sin reto (banner "DESCANSO POMODORO") y que se re-bloquea al volver al trabajo.
  - **2FA del Bloqueo Cruzado**: desde la extensión togglear Bloqueo Cruzado → llega una notificación al teléfono con el código → escribirlo en el popup → se aplica el cambio. Con el teléfono, el toggle SIEMPRE pide biometría/PIN.
- La V23 sigue respaldada (`AntiProcrastinacion_v23.apk`) y la V24 rectificada reemplaza el APK anterior de la raíz.

---

## ✨ 7. Ajustes Adicionales de la Sesión V24 (Solo Android & Extensión)

1. **⏱️ Lógica de Descansos (25% Máximo)**:
   - Se reescribió `computePomodoroLimits` y `buildPomodoroSchedule` para garantizar que **el tiempo total de descanso nunca exceda el 25% del tiempo de enfoque** (ej. 4 horas de enfoque = máximo 1 hora de descanso en total).
   - El tope máximo por descanso individual se mantiene en **30 minutos**.
2. **🧹 Limpieza Visual (Modo Incógnito de Funciones)**:
   - Se eliminaron las palabras **"Pomodoro"**, **"Zen"**, **"Estilo Web"** y **"Sincronizado"** de la interfaz visual visible al usuario, reemplazándolas por "Descansos", "Mis Notas" y "Modo Oscuro" para mayor discreción.
   - En la sección de versión ahora solo dice "Versión v24.0.0".
3. **📱 Ajustes de UI Android**:
   - Se añadió `verticalScroll` a `ConfigScreen.kt` para evitar que las opciones se corten en pantallas pequeñas al desplegar menús.
   - Se ajustó el margen superior (`padding(top = 50.dp)`) en la pantalla de bloqueo (`ZenScreen.kt`) para **evitar colisiones con la cámara / Dynamic Island**.
4. **🔔 Notificación de Pantalla Completa (Full Screen Intent)**:
   - Las solicitudes de autenticación y tregua enviadas desde el PC ahora disparan notificaciones con `setFullScreenIntent` y prioridad máxima en Android para despertar la pantalla de forma más agresiva.
