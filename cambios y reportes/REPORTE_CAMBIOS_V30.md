# 📝 REPORTE DE CAMBIOS - VERSIÓN V30 (MODO SIN REDES COMPLETO + ESCALA DE GRISES)

- **Fecha de Modificación**: 11 de Agosto de 2026
- **Versión del Proyecto**: `26.0.0` (`versionCode = 242`)
- **Ubicación del Reporte**: `C:\Users\USUARIO\Documents\AntiProcrastinacion-NuevoUI\cambios y reportes\REPORTE_CAMBIOS_V30.md`

---

## 🎯 1. Lo que se pidió

- **Modo Sin Redes COMPLETO**: inicia con duración (no se puede desactivar hasta que acabe).
  - Bloquea RRSS, juegos, navegadores y apps de dopamina; **WhatsApp permitida** con límite de **30 min seguidos**.
  - Al superar el límite de WhatsApp: **cooldown de 5 min con TODAS las apps bloqueadas** (sin tregua).
  - **Tregua propia de 5 min** (con cooldown de 10 min entre treguas), independiente de la del Modo Enfoque.
- **Escala de grises**:
  - **Propia de la app (launcher + cajón de apps)**: desatura la UI de AntiProcrastinación en Sin Redes, Paseo y Escuela/Trabajo. No toca el resto del teléfono.
  - **Del sistema (Modo Enfoque)**: activa el monocromo nativo del teléfono (daltonizer) durante el horario de escuela/trabajo, y lo apaga en tregua, descanso y al salir del horario. Requiere el permiso **WRITE_SETTINGS**.

---

## 🔍 2. Estado anterior

- El Modo Sin Redes era solo una tarjeta decorativa en `ModosScreen` que iniciaba un **Modo Enfoque** (no bloqueaba nada propio).
- No existía escala de grises ni para la app ni para el sistema.
- El widget del home solo tenía Modo Enfoque y Modo Paseo.

---

## 🛠️ 3. Cambios realizados

### `LockManager.kt`
- Claves y constantes del Modo Sin Redes (`NOSOCIAL_APP_LIMIT_MINUTES=30`, `NOSOCIAL_ALL_BLOCKED_MINUTES=5`, `NOSOCIAL_TREGUA_MINUTES=5`, `NOSOCIAL_TREGUA_COOLDOWN_MINUTES=10`).
- Estado activo: `startNoSocialMode(minutes)` / `stopNoSocialMode()`, `isNoSocialModeActive()`, `noSocialRemainingMs()`.
- Cooldown total: `isNoSocialAllBlocked()`, `activateNoSocialAllBlocked()` (limpia el contador de WhatsApp).
- Tregua propia: `startNoSocialTregua()`, `isNoSocialTempUnlocked()`, `isNoSocialTreguaCooldown()`, `noSocialTreguaRemainingMs()`.
- Uso seguido de WhatsApp: `noSocialUsageMs()`, `addNoSocialUsage()`, `resetNoSocialUsage()` (se reinicia al cambiar de app, en tregua y tras el cooldown).
- `isNoSocialAppBlocked(pkg)` (RRSS/juegos/navegadores salvo WhatsApp) y `isNoSocialPackageBlockedNow(pkg)` para la UI.
- `isAppGrayscaleActive()` (activa el desaturado del launcher en Sin Redes / Paseo / Escuela-Trabajo).

### `GrayscaleManager.kt` (NUEVO)
- `GrayscaleManager`: escala de grises del SISTEMA vía daltonizer (string keys `accessibility_display_daltonizer*`), con `canWriteSystemSettings()` y `openSystemSettingsIntent()`.
- `AppGrayscaleWrapper`: desatura la UI del launcher/cajón con `RenderEffect` + `ColorFilter` (solo Android 12+; en versiones menores es no-op).

### `LockMonitoringService.kt`
- Rama **Modo Sin Redes** en el bucle (prioridad máxima sobre Escuela/Trabajo y Paseo): `monitorNoSocialLoop()` (llamadas/SMS libres, tregua abierta, cooldown total sin tregua, WhatsApp con límite 30 min → activa cooldown, resto de dopamina expulsión inmediata) + `relaunchNoSocialBlock()`.
- Sincronización del grayscale del sistema en el Modo Enfoque: `syncSystemGrayscale()` (se activa SOLO en horario y sin tregua; escribe solo cuando cambia el estado). Se apaga en `onDestroy()`.
- Notificación con estado del Modo Sin Redes (activo / cooldown / tregua / tiempo restante).

### `MainActivity.kt`
- Estado `noSocialActive` + ticker cada 1s que lo refresca (expira solo).
- `onStartNoSocial(minutes)` y `noSocialTregua()` pasados a launcher y Modos.
- `AppGrayscaleWrapper` aplicado a la pantalla `launcher` y `drawer`.

### `ModosScreen.kt`
- Tarjeta `NoSocialModeCard` real: steppers de duración (default 60 min de ajustes), estado en vivo (inactivo / activo con cuenta atrás / cooldown / tregua), botón "Pedir tregua (5 min)" con su cooldown. No se puede cancelar una vez iniciado.

### `LauncherScreen.kt`
- Widget `NoSocialModeWidget` en el home: estado en vivo + botón de tregua; si está inactivo, botón para ir a Modos.

### `AppDrawerScreen.kt`
- Las apps bloqueadas en Modo Sin Redes se atenúan; al tocarlas, diálogo explicando que no están disponibles (o pedir tregua). Cabecera informa del estado.

### `ConfigScreen.kt`
- Nueva tarjeta de **PERMISOS** para conceder "Modificar ajustes del sistema" (WRITE_SETTINGS), con botón que abre los ajustes del sistema. Aclara que es opcional (Sin Redes/Paseo usan la escala propia de la app).

### `AndroidManifest.xml`
- Añadido `android.permission.WRITE_SETTINGS`.

---

## 📁 4. Archivos modificados

1. `app/src/main/java/com/antiprocrastinacion/lock/LockManager.kt`
2. `app/src/main/java/com/antiprocrastinacion/lock/GrayscaleManager.kt` (NUEVO)
3. `app/src/main/java/com/antiprocrastinacion/lock/LockMonitoringService.kt`
4. `app/src/main/java/com/antiprocrastinacion/lock/MainActivity.kt`
5. `app/src/main/java/com/antiprocrastinacion/lock/ui/launcher/ModosScreen.kt`
6. `app/src/main/java/com/antiprocrastinacion/lock/ui/launcher/LauncherScreen.kt`
7. `app/src/main/java/com/antiprocrastinacion/lock/ui/launcher/AppDrawerScreen.kt`
8. `app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ConfigScreen.kt`
9. `app/src/main/AndroidManifest.xml`

---

## ✅ 5. Verificaciones

- `.\gradlew.bat assembleDebug` → **BUILD SUCCESSFUL** (solo warnings preexistentes de `Icons.Filled.DirectionsWalk` deprecado).
- APK instalado en el dispositivo (`adb install -r`) → **Success**.
- La escala de grises del sistema (Modo Enfoque) es un **no-op si no hay WRITE_SETTINGS**, así que no deja el teléfono atascado en monocromo; el servicio además la apaga en `onDestroy`.

---

## 📱 6. Pruebas manuales sugeridas

1. **Modos → Modo Sin Redes**: inicia 15 min → aparece "● ACTIVO", la notificación muestra el tiempo restante y el launcher/cajón se desaturan. WhatsApp se abre; el resto de RRSS/juegos expulsa al home con aviso.
2. **Tregua**: pulsa "Pedir tregua" → todo abierto 5 min; el botón queda en cooldown 10 min.
3. **Límite WhatsApp (opcional, acortar espera en logs)**: usar WhatsApp 30 min seguidos → cooldown de 5 min con TODAS las apps bloqueadas y sin tregua; al terminar, WhatsApp vuelve a tener 30 min completos.
4. **Fin automático**: al agotarse el tiempo, el modo se limpia solo y la UI vuelve a color.
5. **Escala del sistema (Enfoque)**: Config → PERMISOS → "Conceder permiso" → durante el horario de Escuela/Trabajo la pantalla queda en monocromo; en tregua/descanso/fuera de horario vuelve a color. `adb logcat -s ZEN_GRAYSCALE ZEN_NOSOCIAL`.
6. **Widget del home**: estado en vivo + tregua; al estar inactivo, botón a Modos.
