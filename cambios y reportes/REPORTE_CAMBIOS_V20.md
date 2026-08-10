# 📝 REPORTE DE RESOLUCIÓN DE AUDITORÍA Y CAMBIOS - VERSIÓN v20.0.0

- **Fecha de Modificación**: 9 de Agosto de 2026
- **Versión del Proyecto**: `v20.0.0` (`versionCode = 20`, `versionName = "20.0.0"`)
- **Ubicación del Reporte**: `C:\Users\USUARIO\Documents\AntiProcrastinacion\cambios y reportes\REPORTE_CAMBIOS_V20.md`

---

## 📩 1. Orden Exacta del Usuario

> *"si empiezo el modo enfoque desde la PC no se inicia en el telefono"*

> *"corrige lo de adelantar el texto que uno tiene que escribir para terminar el bloqueo desde la extensión, porque se puede seleccionar el texto, entonces que pasa si presiono control y lo muevo se pega, entonces tu tienes un anticopy muy leve porque lit con borrar 3 letras y volverlas a escribir lo paso, entonces compara la cantidad de caracteres que hay en todo el texto y que la persona minimo tenga que escribir la mitad"*

> *"quita el cuadro rojo feo que sale por adelante del logo de la extensión, si vas a hacer eso agrega un puntico rojo que cambie a verde disimulado desde la parte de la extensión en una esquinita sin tartar nada"*

---

## 🔍 2. Hallazgos de Auditoría (Estado Anterior)

1. **🟡 Bloqueo cruzado PC → Teléfono NO funcionaba con la app en segundo plano**:
   - El listener de `lock_state` solo se registraba en `MainActivity` (app en primer plano).
   - `LockMonitoringService` se detenía solo (`stopSelf()`) cuando no había bloqueo activo, así que con la app cerrada o en segundo plano **no había ningún listener activo**: si el usuario iniciaba el modo enfoque desde la extensión de Chrome, el teléfono no reaccionaba.

2. **🟡 Anti-copy del reto de escritura demasiado débil**:
   - El texto objetivo del reto se podía **seleccionar** y arrastrar con Ctrl hacia el área de escritura (drag-copy).
   - La verificación solo comparaba la cadena exacta (`===`), así que bastaba pegar el texto, borrar 3 letras y volverlas a escribir para superarlo.

3. **🟡 Cuadro rojo/verde sobre el logo de la extensión**:
   - Desde V17 el icono mostraba un badge emoji **"🟢" / "🔴"** que Chrome renderizaba como un **cuadro rojo feo** pegado al logo.

4. **🟡 (V20 inicial) Sin modo oscuro, sin menú de ajustes y bloqueo cruzado unilateral**:
   - La app solo tenía tema claro.
   - No existía una tarjeta/menú de Ajustes.
   - El bloqueo sincronizado solo iba en un sentido (Android → Chrome); Android no aplicaba el bloqueo iniciado desde Chrome.

---

## 🛠️ 3. Soluciones Aplicadas (Versión 20.0.0)

### A. Modo Oscuro (replica la web)
- En [`ui/theme/Color.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/theme/Color.kt):
  - Nueva paleta oscura (`DarkCreamBackground #0F171E`, `DarkZenWhite #1B252E`, `DarkZenCharcoal #F1F5F9`, `DarkZenSage #94A3B8`, `DarkZenOlive #5F7564`, `DarkZenGreen #62A06D`, `DarkZenCoral #E57373` + variantes suaves de tarjetas) y variantes claras.
  - Estado global `object ZenTheme { var isDark by mutableStateOf(false) }` y getters de color que cambian según `ZenTheme.isDark`.
- En [`ui/theme/Theme.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/theme/Theme.kt): `AntiProcrastinacionTheme(darkTheme)` con `SideEffect { ZenTheme.isDark = darkTheme }` y `colorScheme` claro/oscuro; toda la app lee los colores vía el estado global.
- En [`LockManager.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LockManager.kt#L227-L230): preferencia persistente `darkModeEnabled` (`KEY_DARK_MODE`, default claro).
- En [`ConfigScreen.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ConfigScreen.kt#L570-L603): toggle **Modo Oscuro** con subtítulo dinámico *"Oscuro (como la web)" / "Claro Zen"*.
- En [`MainActivity.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/MainActivity.kt#L75-L102): el estado se carga desde la preferencia guardada y se propaga a `ConfigScreen` vía `darkTheme`/`onDarkThemeChange`.

### B. Menú de Ajustes
- En [`ConfigScreen.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ConfigScreen.kt#L548-L645): nueva tarjeta **"AJUSTES"** (`Icons.Default.Settings`) con dos toggles: **Modo Oscuro** y **Bloqueo Cruzado**.
- Texto de versión actualizado en [`ConfigScreen.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ConfigScreen.kt#L943): *"Versión v20.0.0 (Modo Oscuro, Bloqueo Cruzado & Menú de Ajustes)"*.

### C. Bloqueo Cruzado bidireccional
- En [`LockManager.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LockManager.kt#L365-L410): `startLockStateListener` escucha en tiempo real `/users/<key>/lock_state`; ignora el estado propio (`source_device == "android"` para evitar eco); si el remoto está bloqueado y no expira → aplica el bloqueo local (fija `isLocked`, `lockEndTime`, limpia tregua/cooldown, asigna frases) y llama al callback; si el remoto desbloquea → desbloquea local.
- Toggle **Bloqueo Cruzado** en [`ConfigScreen.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ConfigScreen.kt#L609-L645) con subtítulo *"Al bloquear un dispositivo, se bloquean los demás (PC y teléfono)."*; la preferencia se publica en Firebase (`/users/<key>/config/cross_device_lock_enabled`) y se respeta desde la extensión ([`background.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/background.js#L596-L620) la lee y aplica `lock_state` de Android sobre Chrome).
- `pushUserProfile(role)` escribe `/users/<key>/profile` (email + rol).

### D. Servicio persistente → el bloqueo PC → Teléfono funciona siempre
- En [`LockMonitoringService.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LockMonitoringService.kt):
  - `onCreate` registra `startLockStateListener { locked -> if (locked) relaunchLockScreenInstant() }` (líneas 46-50): al recibir un bloqueo remoto, lanza `MainActivity` al frente con la pantalla Zen.
  - `onStartCommand` devuelve `START_STICKY` siempre (el servicio ya **no** hace `stopSelf()` cuando no hay bloqueo).
  - El loop de monitoreo pasa a **modo escucha** (`delay(500)`) cuando no hay bloqueo, en vez de detenerse; al terminar el tiempo solo aplica `stopLock()` y sigue escuchando.
  - Notificación **dinámica**: con bloqueo *"Modo Enfoque Máxima Seguridad 🧘"*; sin bloqueo *"AntiProcrastinación 🧘 — Sincronización activa. Esperando modo enfoque..."* (`buildNotification(isLocked)` + `updateNotification()`).
- En [`MainActivity.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/MainActivity.kt#L57-L71): el servicio se arranca **siempre** (en `onCreate` y `onResume`); al desbloquear ya **no** se llama `stopService`, solo `finish()`. Así el modo escucha queda activo en todo momento.

### E. Punto de conexión discreto (se quitó el cuadro rojo/verde del icono)
- En [`extension_chrome/background.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/background.js#L533-L542): `updateExtensionBadge()` ahora limpia el badge (`setBadgeText({ text: "" })`) — nada se dibuja sobre el logo.
- En [`extension_chrome/popup.html`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.html#L14): nuevo `<span class="conn-dot" id="conn-dot">` junto al estado del encabezado.
- En [`extension_chrome/styles.css`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/styles.css#L64-L78): `.conn-dot` = puntico de 9px redondeado, **rojo** (`#D97D75`) por defecto y **verde** (`#2E7D32`) con `.connected`. Estilo Zen, discreto, "sin tocar nada".
- En [`extension_chrome/popup.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.js#L230-L238): `updateUI()` lee `connectedDeviceInfo` de `getState` y alterna la clase `connected` (verde = teléfono conectado por LAN o nube, rojo = sin conexión). Se actualiza solo cada 1 s.

### F. Anti-Copy reforzado + Reto de Escritura al 50%
- En [`extension_chrome/popup.html`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.html#L101): el div `#phrase-target-text` ahora lleva `draggable="false"`, `onselectstart="return false;"`, `ondragstart="return false;"`, `oncopy="return false;"`, `oncut="return false;"`, `oncontextmenu="return false;"`.
- En [`extension_chrome/styles.css`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/styles.css#L531-L544): `.modal-text` con `user-select: none` (+ prefijos) → el texto objetivo **no se puede seleccionar**.
- En [`extension_chrome/popup.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.js#L146-L152): listeners `selectstart`, `dragstart`, `copy`, `cut` → `preventDefault()`.
- **Nueva verificación** ([`popup.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.js#L316-L347)): en vez de `===`, compara carácter a carácter por posición (`Array.from` para acentos) y exige **mínimo el 50% de coincidencia** (`matchRatio >= 0.5`). Se mantiene el mínimo de 10 segundos anti-pegado. Se cumple la orden: *"compara la cantidad de caracteres que hay en todo el texto y que la persona mínimo tenga que escribir la mitad"*.

---

## 📁 4. Detalle de Archivos Modificados

1. **[`app/build.gradle.kts`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/build.gradle.kts#L16-L17)**: `versionCode = 20`, `versionName = "20.0.0"`.
2. **[`ui/theme/Color.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/theme/Color.kt)**: paleta claro + paleta oscura, estado global `ZenTheme.isDark` y getters dinámicos.
3. **[`ui/theme/Theme.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/theme/Theme.kt)**: `AntiProcrastinacionTheme(darkTheme)` con `SideEffect` y `colorScheme` por modo.
4. **[`LockManager.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LockManager.kt)**: `darkModeEnabled`, `crossDeviceLockEnabled` (toggle + publicación en Firebase), `startLockStateListener`, `remoteLockCallback`, `pushUserProfile`.
5. **[`ConfigScreen.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ConfigScreen.kt)**: tarjeta "AJUSTES" con toggles Modo Oscuro y Bloqueo Cruzado; texto de versión `v20.0.0`.
6. **[`MainActivity.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/MainActivity.kt)**: estado `darkTheme`, servicio siempre activo, sin `stopService` al desbloquear, listener de `lock_state`.
7. **[`LockMonitoringService.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LockMonitoringService.kt)**: listener de `lock_state` en `onCreate`, `START_STICKY`, modo escucha, notificación dinámica.
8. **[`extension_chrome/background.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/background.js)**: badge limpiado; lectura y respeto de `cross_device_lock_enabled`; aplicación de `lock_state` Android → Chrome.
9. **[`extension_chrome/popup.html`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.html)**: `<span class="conn-dot">` y atributos anti-selección en `#phrase-target-text`.
10. **[`extension_chrome/popup.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.js)**: puntico de conexión, listeners anti-copy y reto por caracteres al 50%.
11. **[`extension_chrome/styles.css`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/styles.css)**: `.conn-dot` y `user-select: none` en `.modal-text`.

---

## 🧩 4.1 Cambios adicionales V20.2 — Configuración sincronizada en la extensión + quitar el punto del logo

**Orden del usuario:** "Quita el punto rojo feo que sale en el logo de la extensión en la lista de extensiones y tienes que agregar y sincronizar las configuraciones también en la extensión (modo oscuro, bloqueo cruzado)".

### Cambios en la extensión de Chrome
1. **Badge del logo eliminado por completo**: `updateExtensionBadge()` ya solo ejecuta `chrome.action.setBadgeText({ text: "" })` y se removió `setBadgeBackgroundColor`. Ya no se pinta ningún punto rojo/verde sobre el icono (además se limpia al despertar el service worker).
2. **Nueva tarjeta "⚙️ CONFIGURACIÓN:"** en `popup.html` con dos toggles estilo Zen: **🌙 Modo Oscuro** y **🔄 Bloqueo Cruzado** (`#dark-mode-switch`, `#cross-lock-switch`).
3. **Sincronización con Firebase**:
   - Nuevo mensaje `getSettings` → devuelve `{ crossDeviceLockEnabled, darkModeEnabled }`.
   - Nuevo mensaje `setSetting` → actualiza memoria + `chrome.storage.local` y publica en `/users/<target>/config/dark_mode_enabled.json` y `/users/<target>/config/cross_device_lock_enabled.json` (mismas rutas que usa la app Android).
   - `broadcastSettingsChanged()` notifica a popup y notas en vivo (`action: 'settingsChanged'`).
   - `pollFirebaseSync` ahora lee `/users/<target>/config.json` (antes solo `cross_device_lock_enabled.json`) y aplica también `dark_mode_enabled` venido del teléfono.
4. **Modo oscuro aplicado en vivo**: `popup.js` y `notes.js` aplican `data-theme="dark"` en `<html>`; `styles.css` y `notes.css` ahora usan variables CSS y añaden la paleta oscura (replica la de la app: `#0F171E`, `#1B252E`, `#F1F5F9`, `#94A3B8`, `#5F7564`).

### Cambios en la app Android (para completar el ciclo)
5. **[`LockManager.kt`]**: el setter de `darkModeEnabled` ahora también publica en `/users/<uid>/config/dark_mode_enabled` (antes solo el de bloqueo cruzado). Nuevo `startConfigListener()` que escucha `/users/<uid>/config` y aplica en caliente los cambios hechos desde la extensión (modo oscuro y bloqueo cruzado) con callback `(dark, crossLock)`.
6. **[`MainActivity.kt`]**: `darkTheme` pasó a estado de la clase; se registra `startConfigListener` que actualiza `darkTheme` e incrementa `configVersion`; se pasa `configVersion` a `ConfigScreen`.
7. **[`ConfigScreen.kt`]**: nuevo parámetro `configVersion: Int = 0` y `LaunchedEffect(configVersion)` que refresca el toggle de Bloqueo Cruzado cuando cambia remotamente.

### Archivos modificados en esta tanda
- `extension_chrome/background.js`, `extension_chrome/popup.html`, `extension_chrome/popup.js`, `extension_chrome/notes.js`, `extension_chrome/styles.css`, `extension_chrome/notes.css`
- `app/src/main/java/com/antiprocrastinacion/lock/LockManager.kt`, `MainActivity.kt`, `ui/screens/ConfigScreen.kt`

### Verificaciones
- `node --check` aprobado en `background.js`, `popup.js` y `notes.js` tras los cambios.
- Flujo bidireccional confirmado por inspección: extensión → `setSetting` → Firebase `/config/` → app `startConfigListener`; app → setter `darkModeEnabled`/`crossDeviceLockEnabled` → Firebase → extensión `pollFirebaseSync` + `settingsChanged`.

### V20.3 — Menú de Perfil y Ajustes (como en Android)
- **`popup.html`**: se eliminó la tarjeta "⚙️ CONFIGURACIÓN:" del feed principal y la caja de "Cuenta de Google". Ahora la sección de cuenta es un **botón de perfil** (`btn-profile-menu` con avatar 👤, título dinámico y chevron ▾) que abre un **menú desplegable** con dos opciones:
  - **👤 Mi Cuenta** → modal `account-modal` con el botón de Iniciar Sesión con Google y el acceso a "📝 Mis Notas Zen".
  - **⚙️ Ajustes** → modal `settings-modal` con los toggles Modo Oscuro y Bloqueo Cruzado.
- **`popup.js`**: lógica del desplegable (abrir/cerrar con clic fuera), apertura de modales, cierre con botón "Cerrar" o clic en el fondo; el título del perfil muestra la cuenta activa (email) o "Mi Cuenta".
- **`styles.css`**: estilos `.profile-trigger`, `.profile-avatar`, `.profile-text`, `.profile-chevron`, `.profile-dropdown` y `.dropdown-item` (paleta Zen con variables, compatible con el modo oscuro).
- **Resaltado en vivo de la frase de finalización**: al escribir en el reto de 150 palabras, la frase objetivo se colorea carácter a carácter como en Android (`buildHighlightedText`): **verde** (`.phrase-correct`) si la letra coincide, **rojo/coral** (`.phrase-wrong`, subrayado punteado) si falla y **gris** (`.phrase-pending`) si aún no se escribe. Se actualiza con `renderHighlightedPhrase()` en el evento `input`; la comparación usa `Array.from` (coherente con la verificación final del 50%).
- Solo se tocó la **extensión web**; la app Android quedó intacta en esta tanda (el menú equivalente ya se aplicó en Android con Antigravity).

---

## 🔬 5. Verificaciones Realizadas

- `node --check` aprobado en `background.js`, `popup.js`, `notes.js` y `login.js` (sintaxis JS válida tras los cambios V20).
- Confirmado en el código: el servicio queda persistente (`START_STICKY`) con el listener de `lock_state` registrado en `onCreate` y sin ninguna invocación a `stopService` (grep: solo existe la definición, nadie la llama).
- Confirmado en el código: el reto de escritura compara carácter a carácter (`Array.from`) y exige `matchRatio >= 0.5`; el texto objetivo no es seleccionable (`user-select:none` + atributos/eventos).
- Confirmado en el código: `updateExtensionBadge()` limpia el badge del icono; el puntico `#conn-dot` alterna la clase `connected` según `connectedDeviceInfo`.
- El modo oscuro y el menú de ajustes ya estaban implementados y persistidos en `LockManager` (verificado por inspección del código).

### 🔍 5.1 Nota sobre la RTDB
- El reporte V19 ya documenta que la base `https://antiprocrastinacion-26975-default-rtdb.firebaseio.com` existe y la sincronización en vivo funciona (verificación 200 OK con latido en tiempo real); el `HTTP 404` observado fue transitorio. La V20 no cambia la URL de la base.

## ⚠️ 6. Acción Requerida en la PC (No es un bug)

- Después de modificar los archivos de la extensión (`background.js`, `popup.js`, `styles.css`, `popup.html`), **Chrome NO recarga extensiones desempacadas automáticamente**.
- En `chrome://extensions` hay que pulsar el botón de recargar (🔄) de la tarjeta "AntiProcrastinación" para que los cambios surtan efecto.
- Para probar el bloqueo cruzado PC → teléfono:
  1. La app debe estar instalada y con el **mismo** Google en teléfono y extensión.
  2. El toggle **Bloqueo Cruzado** en Ajustes debe estar activado.
  3. Iniciar el enfoque desde la PC → el teléfono debe saltar a la pantalla Zen aunque la app esté en segundo plano.

---

## 📋 7. Orden para antigravity (compilación SOLO con Gradle)

> **NO descargar ni instalar ningún SDK. NO modificar código.**
> Ejecutar únicamente la compilación de debug del proyecto:
> `gradlew assembleDebug` (o `gradle assembleDebug`).
> Si pide SDK que no está en `F:\codigo\A\android-sdk`, NO intentar descargarlo; reportarlo y detenerse.
> Confirmar en el log que el build termina con `BUILD SUCCESSFUL`.
