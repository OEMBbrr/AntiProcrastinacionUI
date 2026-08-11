# CONTEXTO IA — Estado actual del proyecto AntiProcrastinacion-NuevoUI (V29)

> Este archivo es el punto de reanudación. Si el chat se compacta o se abre uno
> nuevo, leer este archivo ANTES de hacer cualquier cambio.

## Ruta del proyecto

```
C:\Users\USUARIO\Documents\AntiProcrastinacion-NuevoUI
```

(NO el antiguo `C:\Users\USUARIO\Documents\AntiProcrastinacion`, que es el proyecto viejo).

- adb: `F:\codigo\A\android-sdk\platform-tools\adb.exe`
- Paquete: `com.antiprocrastinacion.lock`
- `app/build.gradle.kts`: `versionCode = 242`, `versionName = "26.0.0"` (NO se subió la versión en las últimas builds; los respaldos van como `AntiProcrastinacion-v26.x.x.apk`).
- Último build instalado y respaldado: `AntiProcrastinacion-v26.1.1.apk` (== `app-debug.apk`).

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
