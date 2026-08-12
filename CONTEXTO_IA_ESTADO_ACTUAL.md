# CONTEXTO IA — Estado actual del proyecto AntiProcrastinacion-NuevoUI (V31)

> Este archivo es el punto de reanudación. Si el chat se compacta o se abre uno
> nuevo, leer este archivo ANTES de hacer cualquier cambio.

## Ruta del proyecto

```
C:\Users\USUARIO\Documents\AntiProcrastinacion-NuevoUI
```

(NO el antiguo `C:\Users\USUARIO\Documents\AntiProcrastinacion`, que es el proyecto viejo).

- adb: `F:\codigo\A\android-sdk\platform-tools\adb.exe`
- Paquete: `com.antiprocrastinacion.lock`
- `app/build.gradle.kts`: `versionCode = 243`, `versionName = "26.2.0"`.
- Último build instalado y respaldado: `app-debug.apk` (== v26.2.0, instalado 11/08/2026).

## V31 — AHORRO DE BATERÍA AUTOMÁTICO (sincronizado con los modos)

Especificación: al activarse CUALQUIER modo (Enfoque, Sin Redes, Escuela/Trabajo o
Paseo) → activar el ahorro de batería del sistema; al salir todos los modos →
apagarlo si el usuario no lo tenía encendido antes.

### Descubrimiento clave (verificado por adb, 11/08/2026)
- El TECNO LI7 (HiOS/Android 15) exige `android.permission.WRITE_SECURE_SETTINGS`
  (nivel signature/privilegiado) para escribir `low_power` en `Settings.Secure/Global`.
  La app SOLO puede pedir `WRITE_SETTINGS` ("Modificar ajustes del sistema").
- Verificado vía shell: `settings put global low_power 1` SÍ funciona (shell tiene
  WRITE_SECURE_SETTINGS), así que la clave es correcta; solo falta el permiso.
- CONCLUSIÓN: el cambio SILENCIOSO del ahorro de batería es IMPOSIBLE en este
  dispositivo sin root. En dispositivos AOSP "limpios" sí puede funcionar con WRITE_SETTINGS.

### Solución implementada en `LockManager.kt`
- `syncBatterySaver()` (se llama desde el bucle del servicio):
  - Rate-limit de reintento: solo intenta escribir si cambió el estado de los modos o
    si pasaron 10 minutos (`lastBatteryShouldBeOn`, `lastBatteryAttemptMs`). Esto evita
    el spam de logs/errores cada 500 ms que había antes.
  - Guarda el estado previo (`KEY_BATTERY_SAVER_PREV_STATE`) para restaurarlo al salir.
  - Al fallar (WRITE_SECURE_SETTINGS denegado) muestra `showBatterySaverNudge()`.
- `showBatterySaverNudge()`: notificación ÚNICA por sesión de modo (canal
  `zen_battery_nudge`, ID 1002) que abre `Settings.ACTION_BATTERY_SAVER_SETTINGS`
  para que el usuario active el ahorro manualmente. Se resetea al salir de todos
  los modos (`KEY_BATTERY_SAVER_NUDGE_SHOWN`).
- Clave nueva: `KEY_BATTERY_SAVER_NUDGE_SHOWN`. Imports añadidos: NotificationChannel,
  NotificationManager, PendingIntent, Build, NotificationCompat.

### Estado de la prueba en TECNO LI7
- App instalada (v26.2.0). El ahorro de batería AUTOMÁTICO no puede activarse en este
  dispositivo; el flujo esperado es: entrar a un modo → aparece el aviso → el usuario
  toca y activa el ahorro manualmente en Ajustes. Para probar el aviso: activar
  cualquier modo y mirar la bandeja de notificaciones (ID 1002).
- Pendiente de verificación en vivo: que el aviso se muestre una sola vez por modo y
  no se repita al reintentar (rate-limit 10 min).

## V31 — BLOQUEO DE NOTIFICACIONES (rediseñado según pedido del usuario, 11/08/2026)

Modelo acordado (preguntado y confirmado por el usuario):
- **Llamadas y SMS siempre pasan**, igual que las notificaciones propias de la app.
- **Enfoque, Sin Redes y Escuela/Trabajo: bloquean TODO** apenas se activan
  (antes cada modo tenía sus excepciones; ahora nada más entrar el modo, todo se oculta).
- **Caminata/salida/cita (Paseo): bloquea por defecto**, pero hay un ajuste en Config
  para permitir notificaciones ahí.
- **Sin ningún modo**: se bloquea solo con la opción "Siempre" activa.
- Ajustes nuevos en Config > Bloqueo de notificaciones:
  - Alcance: **"Solo con modos"** (defecto) vs **"Siempre"** (bloquea sin modo también).
  - **"Permitir notificaciones en caminata"** (switch, defecto OFF).

### Cambios de código
- `LockManager.kt`:
  - Claves nuevas: `notif_blocking_always`, `paseo_notifications_allowed`
    (propiedades `notifBlockingAlways`, `paseoNotificationsAllowed`).
  - `shouldBlockNotification()` reescrito: ahora el orden es
    master switch → propias/llamadas/SMS → modo restrictivo → caminata → "siempre".
    Ya no usa `isNoSocialTempUnlocked`/`isNoSocialAllBlocked`/`isNoSocialAppBlocked`/
    `isWorkModeAppAllowed`/`isDopamineWalkPackage`/`isMusicPackage` (esas funciones se
    conservan para el bloqueo de USO, no de notificaciones).
- `ZenNotificationListenerService.kt`: añadido log diagnóstico
  `Log.d("ZEN_NOTIF_BLOCK", "Recibida notificación de <pkg>")` al inicio de
  `onNotificationPosted` (facilita la verificación; sin él los logs solo salen al bloquear).
- `ConfigScreen.kt`: selector de alcance (2 pills "Solo con modos"/"Siempre") y switch
  de caminata dentro de la tarjeta SISTEMA > Bloqueo de notificaciones. Textos actualizados.

### Verificación EN VIVO (TECNO LI7, 11/08/2026) — ÉXITO
- Con Sin Redes + Trabajo activos, un amigo envió mensajes de WhatsApp y llegó una de
  Instagram: logcat mostró para cada notificación
  `Recibida notificación de com.whatsapp` + `Bloqueada notificación de com.whatsapp`
  (y com.instagram.android), ocultándose en ~5-15 ms. Listener conectado
  (`ServiceRecord` c:android, proxy en `dumpsys notification`).
- NOTA de prueba: `cmd notification post` SIEMPRE atribuye a `com.android.shell`
  (uid 2000), no al paquete indicado → NO sirve para probar el bloqueo. Usar
  notificaciones reales y `adb logcat -s ZEN_NOTIF_BLOCK:*`.

## V32/V33 - PLAN DE ACTIVIDADES DEL MODO ENFOQUE + ALARMAS (11/08/2026)

Especificación del usuario (V33, confirmado con 2 preguntas):
- **Descanso DENTRO de una actividad**: se RESTA del tiempo de la actividad y esta se
  divide en bloques iguales. Ej: 2 h con 2 descansos de 20 min → 3 bloques de ~26 min.
- **Descanso ENTRE actividades (exterior)**: se SUMA al total. Ej: 50 + descanso 10 +
  20 = 80 min de enfoque.
- Total del enfoque = suma de actividades (slot completo) + descansos exteriores.
- **Validación**: un descanso exterior necesita una actividad DESPUÉS; si queda al final
  (o el plan empieza con descanso, o hay 2 descansos exteriores seguidos, o los descansos
  internos superan el tiempo de su actividad) → el plan NO se puede iniciar y se muestra
  el motivo en rojo. Botón "!" abre la ayuda explicando las reglas.
- Alarmas: AUTOMÁTICAS en cada cambio (actividad→descanso→actividad). Ya no hay opción manual.
- NOTA: el límite del 15% del V32 quedó OBSOLETO con este modelo (se elimina).

### Modelo
- `FocusSegment(type, title, durationMinutes, internalBreakCount=0, internalBreakMinutes=5)`
  en `LockManager.kt`; `type` = "work"/"rest" (rest = descanso exterior).
- `validateFocusPlan(plan): String?` (top-level): devuelve el error o null.
- `PomodoroPhase` lleva `title` opcional. Claves: `focus_activity_plan_json`,
  `focus_last_announced_segment`.

### Cambios de código
- `LockManager.kt`:
  - `buildPlanSchedule()` V33: expande cada actividad en (n+1) bloques de trabajo con n
    descansos internos entre medias (el trabajo = duración − n·bm, repartido en bloques
    iguales); los descansos exteriores pasan como fases "rest" entre actividades.
  - `focusPlanWorkMinutes`: minutos efectivos de trabajo (actividades − descansos internos).
  - `startLock()`: usa el plan SOLO si `validateFocusPlan() == null`; el total = suma de
    segmentos. Resetea `focus_last_announced_segment` a la 1ª fase.
  - `pollSegmentAlarm()` + `playSegmentAlarm()` (igual que V32): sonido aleatorio ~5 s en
    cada cambio de segmento; `stopLock()` corta la alarma.
  - ELIMINADO el Pomodoro automático (petición del usuario, V32): `pomodoroEnabled`,
    `pomodoroWorkMinutes`, `pomodoroRestMinutes`, `pomodoroRestCount`,
    `computePomodoroLimits`, `buildPomodoroSchedule`, claves KEY_POMODORO_ENABLED/_WORK/
    _REST_*, `POMODORO_MAX_*` y su sync por Firebase/LanServer. Los descansos SOLO salen del plan.
- `LockMonitoringService.kt`: `lockManager.pollSegmentAlarm()` en cada pasada del bucle.
- `ModosScreen.kt` (`ModeCard` + `FocusPlanEditor` + `FocusActivityRow` + `FocusBreakRow`):
  - "Crear plan de actividades" siembra UNA actividad (sin descanso colgante).
  - Actividad: título editable + duración (+/-) + "Descansos dentro: [n] × [bm min]" con
    vista previa "→ N bloques de ~X min".
  - Descanso exterior: fila verde "Descanso (entre actividades)", solo se puede añadir si
    la última fila es una actividad; se suma al total.
  - Botón "!" (círculo) abre AlertDialog con las reglas. Botón "Quitar" elimina el plan.
  - Errores de validación en rojo + botón "Iniciar plan (X min)" deshabilitado si es inválido.
- `ConfigScreen.kt`: ELIMINADA la sección "Descansos" (Pomodoro) de AJUSTES GENERALES y su
  tarjeta — los descansos se configuran únicamente en el plan de actividades.
- `LanServer.kt`: ya no publica `pomodoro_enabled/work/rest/count`; solo comparte `phases`.
- `ZenScreen.kt`: píldora "ACTIVIDAD ACTUAL: <título>" (solo con plan; en descanso sale el
  banner verde de tregua libre).

### Sonidos (app/src/main/res/raw)
- Descargados (biblioteca de sonidos de Google Actions): `alarm_beep.ogg`, `alarm_clock.ogg`,
  `alarm_bugle.ogg`, `alarm_digital_long.ogg`, `pop_soft.ogg`.
- Generados por código (PowerShell, síntesis sinusoidal con armónicos y decaimiento):
  `chime_soft.wav`, `chime_warm.wav`, `chime_bright.wav`. Script: `gen_chimes.ps1` en Temp.

### Estado de la prueba (V33)
- Compilación OK (`:app:compileDebugKotlin` + `:app:assembleDebug` BUILD SUCCESSFUL).
- Instalado en TECNO LI7 (11/08/2026, 3º build del día) con el nuevo editor de plan.
- PENDIENTE prueba en vivo (2 escenarios):
  1. Actividad + descanso interno → verificar el desglose "N bloques de ~X min" y que la
     actividad totaliza su duración (con las treguas dentro).
  2. Actividad 50 + descanso exterior 10 + actividad 20 → verificar que el total suma 80,
     que al quitar la última actividad el plan queda inválido (mensaje + botón deshabilitado),
     y que suena la alarma en cada cambio de segmento.
  - Logs: `adb logcat -s ZEN_PLAN:*`. Si el plan guardado de pruebas anteriores era
    inválido (descanso final colgante), el botón Iniciar saldrá deshabilitado a propósito.

## MÉTODO DE TRABAJO (obligatorio)

1. Ante cualquier incertidumbre, preguntar ANTES de actuar (nada de asumir).
2. Nunca declarar éxito sin compilación limpia + instalación real por adb verificada.
3. Nunca inventar datos: todo lo reportado (builds, adb, prefs) debe verificarse.
4. Backups en la raíz del proyecto como `AntiProcrastinacion-v<X>.apk`.

Comandos:
```powershell
.\gradlew.bat :app:assembleDebug
F:\codigo\A\android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
F:\codigo\A\android-sdk\platform-tools\adb.exe shell dumpsys package com.antiprocrastinacion.lock | Select-String versionName
```

## V26.0 — TREGUA App ↔ Extensión (VERSIÓN BASE instalada)

- Arreglada la tregua de emergencia: el PC escribe en `/users/<target>/tregua_request`
  y el teléfono la aprueba/deniega; la extensión la procesa en su siguiente poll.
- Respaldos: `AntiProcrastinacion-v26.0.0.apk` y `AntiProcrastinacion.apk` (mismo MD5).

## V26.1 / V29 — MODO PASEO (implementado desde cero)

Especificación en `Estructura de la App para proximos cambios.md` (sección Modos):
- Activable/desactivable a voluntad, SIN temporizador fijo.
- RRSS/juegos/dopamínicas: límite 15 min seguidos (solo se bloquea esa app).
- WhatsApp: límite 20 min.
- Música: BLOQUEADA intencionalmente.
- Cámara, llamadas, SMS, Google Maps, etc.: apps esenciales, libres.
- No es bloqueo de pantalla: monitorea con `isLocked=false` mientras paseo activo.

Defaults: `paseo_dopamine_minutes=15`, `paseo_whatsapp_minutes=20`, `paseo_music_blocked=true`.

### Archivos tocados
- `app/src/main/java/com/antiprocrastinacion/lock/LauncherUtils.kt`
  - enum `WalkAppCategory {ESSENTIAL, WHATSAPP, MUSIC, DOPAMINE, FREE}`,
    `classifyWalkApp()`, `isMusicPackage()`, `getCameraPackages()` (con caché),
    `isEssentialWalkApp()`, `isDopamineWalkPackage()`.
- `app/src/main/java/com/antiprocrastinacion/lock/LockManager.kt`
  - Claves: `paseo_mode_enabled`, `paseo_start_time`, `paseo_usage_json`, `paseo_blocked_json`.
  - `paseoModeEnabled` (getter/setter ~L502): el setter loguea
    `Log.d("ZEN_PASEO", "paseoModeEnabled -> $value", Throwable("ZEN_PASEO trace"))`
    para cazar a quién lo escribe si vuelve a aparecer el auto-flip.
  - `paseoLimitMs()`, `paseoRemainingMs()`, `paseoUsageMs()`, `addPaseoUsage()`,
    `blockPaseoApp()`, `isPaseoBlocked()`, `paseoBlockedPackages()`,
    `paseoElapsedMinutes()`, `resetPaseoState()`.
- `app/src/main/java/com/antiprocrastinacion/lock/LockMonitoringService.kt`
  - Rama paseo en `startMonitoringLoop()` (delay 500ms), `monitorPaseoLoop()`
    (acumula uso en foreground; bloquea con Toast + relaunch, debounce 1500ms),
    notificación "Modo Paseo activo 🚶".
- `app/src/main/java/com/antiprocrastinacion/lock/MainActivity.kt`
  - Estado `paseoActive` + lambda `togglePaseo` (L161-164), único caller del setter
    (`lockManager.paseoModeEnabled = !lockManager.paseoModeEnabled`).
- `app/src/main/java/com/antiprocrastinacion/lock/ui/launcher/LauncherScreen.kt`
  - `PaseoModeWidget` con Switch (L442-507).
- `app/src/main/java/com/antiprocrastinacion/lock/ui/launcher/ModosScreen.kt`
  - Nueva `ModeToggleCard` (L112-113).
- `app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ConfigScreen.kt`
  - Switch de activación (L1070-1086).

### Bugs corregidos durante V29
- El setter escribía `putBoolean(KEY_PASEO_MODE_ENABLED, true)` hardcodeado → arreglado
  a `value`.

### El misterio del "auto-flip" (RESUELTO, no se reproduce)
- Se reportó que `paseo_mode_enabled` pasaba a `false` al arrancar con el pref en `true`.
- Tras instalar el build correcto (v26.1.1 con logging), el pref **permanece true** en
  4 arranques en frío y NO hay llamadas al setter (0 logs `ZEN_PASEO`).
- En la sesión anterior se habían confundido logs inexistentes (la salida del terminal
  duplica líneas y hubo alucinación). El único escritor de `paseo_mode_enabled` es el
  setter vía `togglePaseo` (toques reales del usuario). No hay auto-flip en el código.
- Si reaparece, el log con `Throwable("ZEN_PASEO trace")` dirá quién llama.

### Modo Enfoque (estado actual)
- Desactivado manualmente editando prefs vía run-as (sin instalar):
  `is_locked=false`, `lock_end_time=0`, `temp_unlock_end_time=0`, `cooldown_end_time=0`,
  `pomodoro_phases_json=[]`.
- IMPORTANTE: NO se llamó a `stopLock()`, así que Firebase sigue con `is_locked=true`;
  la extensión del PC puede seguir mostrando el bloqueo. (Avisado al usuario.)
- El código de `ACTION_STOP_FOCUS` que se había probado fue REVERTIDO
  (ZenWidgetProvider.kt y MainActivity.kt quedaron como estaban).

## Método para editar prefs manualmente (run-as, sin instalar)

```powershell
$adb = "F:\codigo\A\android-sdk\platform-tools\adb.exe"
& $adb shell "am force-stop com.antiprocrastinacion.lock"
& $adb shell "run-as com.antiprocrastinacion.lock cat shared_prefs/anti_procrastinacion_prefs.xml" > prefs.xml
# editar prefs.xml (reemplazar <boolean name="paseo_mode_enabled" value="X"/>)
& $adb push prefs.xml /data/local/tmp/prefs.xml
& $adb shell "run-as com.antiprocrastinacion.lock sh -c 'cp /data/local/tmp/prefs.xml shared_prefs/anti_procrastinacion_prefs.xml'"
& $adb shell "run-as com.antiprocrastinacion.lock cat shared_prefs/anti_procrastinacion_prefs.xml" | Select-String paseo_mode_enabled
```

OJO con estos glitches del entorno (no son errores de la app):
- La salida del terminal a veces DUPLICA líneas: verificar con `Select-Object -Unique`.
- El `grep`/`rg` del dispositivo puede no existir: filtrar en el PC (`Select-String`).
- `logcat -c` seguido de arranque en frío y `logcat -d` para capturar logs de arranque.

## Aviso importante

Si el contexto se pierde otra vez: leer ESTE archivo, `Estructura de la App para
proximos cambios.md` (especificación de modos) y `ORDEN_E_INSTRUCCIONES_DEL_USUARIO.txt`.
La ruta del proyecto es `C:\Users\USUARIO\Documents\AntiProcrastinacion-NuevoUI`.

## DÓNDE ESTÁN LAS INSTRUCCIONES (importante para un chat nuevo)

Los archivos de instrucciones están en la MISMA carpeta del proyecto NUEVO:

```
C:\Users\USUARIO\Documents\AntiProcrastinacion-NuevoUI\
    ORDEN_E_INSTRUCCIONES_DEL_USUARIO.txt
    PLAN_DE_ACCION_Y_ESPECIFICACIONES.md
    CONTEXTO_PROYECTO_IA.txt
    Estructura de la App para proximos cambios.md
    PLAN_DE_IMPLEMENTACION_V24_1.md
    CONFIGURACION_ENTORNO_IA.txt
    INSTRUCCION_MENU_PERFIL_Y_AJUSTES.txt
```

NOTA: la carpeta vieja `C:\Users\USUARIO\Documents\AntiProcrastinacion\` tiene COPIA
IDÉNTICA de estos archivos (mismos MD5, verificados 11/08/2026), pero NO es la carpeta
de trabajo. Trabajar SIEMPRE en `AntiProcrastinacion-NuevoUI`.
