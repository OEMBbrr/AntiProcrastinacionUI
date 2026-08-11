# Informe V26.0.0 — AntiProcrastinación

**Versión:** 26.0.0 (versionCode 242)
**APK:** `AntiProcrastinacion-v26.0.0.apk` (en la raíz del proyecto, 19,65 MB)
**Fecha:** 11/08/2026

---

## 1. Cómo estaba antes (V24.1.0)

- **Tema del launcher independiente:** el launcher usaba su propia paleta de grises (`LauncherColors.kt`), mientras el modo enfoque (Zen) tenía otra paleta crema/salvia. No se heredaba el color de acento entre ambos.
- **Sin sistema de acento:** los colores `ZenOlive`/`Primario` eran fijos; no existía ningún ajuste para cambiar el color del sistema.
- **Sin widget de escritorio:** no había ningún `AppWidgetProvider` ni layout de widget.
- **Inicio no configurado como launcher:** la app se abría solo como app normal; el HOME del sistema mostraba el launcher de fábrica (Transsion/hilauncher).
- **Cajón de aplicaciones con jank:** cada icono se decodificaba de disco en cada composición (`getAppIconBitmap` directo dentro de `AppDrawerScreen`), provocando scroll "a los golpes".
- **Sin inicio rápido de Modo Enfoque** desde el launcher: había que entrar a Ajustes.
- Sin detección de "¿soy el HOME predeterminado?" y sin tarjeta para establecerlo.

## 2. Instrucciones del usuario (feedback solicitado)

1. La UI no se parece al modo enfoque: aplicar los colores del sistema Zen a todo el launcher.
2. Optimizar animaciones y el scroll del cajón de aplicaciones (va "a los golpes").
3. Ajustes: apartado para cambiar los colores del sistema, que se aplique a todo.
4. El inicio debe ser un launcher real: notas recientes, frases motivacionales y botones para iniciar los modos, con acceso desde un widget de escritorio.
5. Preguntas de diseño respondidas:
   - Widget movible/redimensionable **+ app como inicio** (opción recomendada, elegida).
   - Colores del sistema: **presets de acento** (opción recomendada, elegida).

## 3. Cambios hechos

### 3.1 Sistema de acento global
- **`ui/theme/Color.kt`**: nuevas clases `AccentPreset` y `AccentPresets` con 6 presets (OLIVA, SAGE, CORAL, VERDE, GRIS, ARENA) + `all` y `byKey`. `ZenTheme` ahora expone `accent` y `isDark`; `ZenOlive` y `Primario` devuelven el color del preset activo (claro/oscuro según `isDark`). Añadidos `ZenSurfaceElevated` y `ZenBorderLight`.
- **`ui/launcher/LauncherColors.kt`**: toda la paleta `Lch*` ahora delega en la paleta Zen (crema, salvia, oliva, coral), unificando el aspecto del launcher con el modo enfoque.
- **`LockManager.kt`**: nueva clave `accent_preset_key` y propiedad `accentPresetKey` (persistida, default `"oliva"`).
- **`MainActivity.kt`**: al iniciar carga `ZenTheme.accent = AccentPresets.byKey(lockManager.accentPresetKey)`; conecta `onAccentChange` en ConfigScreen.

### 3.2 Ajustes → Colores del sistema
- **`ui/screens/ConfigScreen.kt`**: nueva sección "COLORES DEL SISTEMA" con círculos de los 6 presets y check en el activo. Al pulsar se guarda en prefs y se propaga al instante a todo el sistema (`ZenTheme.accent = preset`). Parámetro nuevo `onAccentChange: (String) -> Unit`.

### 3.3 Optimización del cajón de aplicaciones
- **`LauncherUtils.kt`**: caché en memoria de iconos (`iconCache` + `getAppIconBitmapCached`) — cada icono se decodifica UNA sola vez.
- **`ui/launcher/AppDrawerScreen.kt`**: los iconos se precargan en bloque en un único `withContext(IO)`; cada `AppLogo` lee de la caché con `remember(packageName)`, eliminando `LaunchedEffect` por item y el jank del scroll.

### 3.4 Launcher como inicio real
- **`ui/launcher/LauncherScreen.kt`**: parámetro `onStartFocus: (Int) -> Unit`; tarjeta "Modo Enfoque" con botones 25/45/90 min que inician el bloqueo directo; detección `isDefaultHome`; tarjeta "Hazme tu Inicio" (con botón "Establecer como Inicio" → `openHomeSettings`) que solo se muestra si la app NO es el HOME.
- **`LauncherUtils.kt`**: nuevo `isDefaultHome(context)` (RoleManager en API 29+; `resolveActivity` + `MATCH_DEFAULT_ONLY` en versiones anteriores).
- **`MainActivity.kt`**: `onStartFocus` conectado → `lockManager.startLock(minutes)` + servicio de monitoreo.
- La app ya estaba registrada con `CATEGORY_HOME`/`CATEGORY_DEFAULT` en el manifiesto.

### 3.5 Widget de escritorio (nuevo)
- **`ZenWidgetProvider.kt`** (`AppWidgetProvider`): renderiza título, botones 25/45/90 (PendingIntent → `START_FOCUS` con `extra_duration`), frase motivacional aleatoria y enlace "Abrir mis notas".
- **`res/layout/zen_widget.xml`**: layout `RemoteViews` con 3 botones en fila, frase (2 líneas) y enlace a Notas.
- **`res/xml/zen_widget_info.xml`**: movible y **redimensionable** (`resizeMode="horizontal|vertical"`, celdas 4x2, rango 120–250dp ancho / 60–140dp alto).
- **`res/drawable/zen_widget_bg.xml`** y **`zen_widget_btn_bg.xml`**: fondo crema redondeado y botones color acento.
- **`res/values/zen_widget_colors.xml`** y **`strings.xml`**: colores y textos del widget.
- **`AndroidManifest.xml`**: `<receiver>` con el provider del widget y `android.appwidget.provider`.
- **`MainActivity.kt`**: `handleWidgetIntent(...)` + `onNewIntent(...)` (launcher es `singleTask`) para procesar `START_FOCUS` (inicia bloqueo) y `OPEN_NOTES` (abre notas).

### 3.6 Versión
- **`app/build.gradle.kts`**: versionCode `241 → 242`, versionName `24.1.0 → 26.0.0`.

### 3.7 Launcher oficial (arreglo posterior)
**Problema detectado:** pese a declarar `CATEGORY_HOME` en el manifiesto, al pulsar Inicio el sistema seguía mostrando `com.transsion.hilauncher` (launcher de fábrica). `dumpsys role` confirmaba que hilauncher era el titular del rol HOME, no nuestra app.

**Arreglos:**
- **`LauncherUtils.requestHomeRole(context)`**: el botón "Establecer como Inicio" ahora usa `RoleManager.createRequestRoleIntent(ROLE_HOME)` en Android 10+ (diálogo oficial "Usar siempre / Una vez") con fallback a `ACTION_HOME_SETTINGS` en versiones antiguas.
- **`LauncherUtils.isDefaultHome(context)`**: detección fiable de si la app es el HOME (RoleManager en API 29+; `resolveActivity` + `MATCH_DEFAULT_ONLY` antes).
- **`MainActivity.onNewIntent`**: al recibir un intent `ACTION_MAIN + CATEGORY_HOME` (pulsar Inicio desde Notas, Organizador, cajón, etc.) se vuelve al launcher (o a la pantalla Zen si hay bloqueo activo).
- **En el dispositivo:** se asignó el rol HOME por `adb shell cmd role` (quitar hilauncher → añadir nuestra app). Verificado: HOME abre `com.antiprocrastinacion.lock/.MainActivity` y `dumpsys role` → `holders=com.antiprocrastinacion.lock`.

## 4. Verificación en dispositivo (adb, 114593744B102019)

- `assembleDebug` compila sin errores.
- APK instalada (`Success`).
- **Widget registrado** en el sistema: `dumpsys appwidget` → `com.antiprocrastinacion.lock/...ZenWidgetProvider`.
- **Launcher renderiza** la tarjeta "Modo Enfoque" con botones 25/45/90, frase motivacional, Tareas de hoy, Ciclo de sueño y Notas recientes (con las notas reales del usuario).
- **App es el HOME predeterminado**: `dumpsys role` → `holders=com.antiprocrastinacion.lock` (por eso la tarjeta "Hazme tu Inicio" se oculta correctamente).
- **Acción del widget probada**: `am start -a ...OPEN_NOTES` entregado al `singleTask` en ejecución → pantalla de Notas visible.

## 5. Limitaciones conocidas

- Un widget **no puede hacerse "no borrable"** en Android (los widgets los gestiona el usuario desde el escritorio); se implementó movible/redimensionable como se acordó.
- El presupuesto de colores "antes" es aproximado: la paleta gris heredada no está documentada, se reemplazó por la Zen como pidió el usuario.
- El rol HOME asignado por adb persiste, pero si la app se desinstala se pierde; basta con pulsar "Establecer como Inicio" (ahora usa RoleManager, el diálogo oficial del sistema).
- El widget usa colores claros fijos (crema) aunque el modo oscuro esté activo.
