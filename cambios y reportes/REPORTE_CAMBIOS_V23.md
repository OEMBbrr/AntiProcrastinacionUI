# 📝 REPORTE DE CAMBIOS Y AUDITORÍA DE CÓDIGO - VERSIÓN v23.0.0

- **Fecha de Modificación**: 9 de Agosto de 2026
- **Versión del Proyecto**: `v23.0.0` (`versionCode = 23`, `versionName = "23.0.0"`)
- **Ubicación del Reporte**: `C:\Users\USUARIO\Documents\AntiProcrastinacion\cambios y reportes\REPORTE_CAMBIOS_V23.md`

---

## 📩 1. Orden Exacta del Usuario

> *"Por cierto agrega el menú de los ajustes tambien en la pestaña de las notas y de iniciar sesión y sincroniza que si yo activo el modo oscuro en la Extensión se active en esas 2 pestañas, y viceversa, tambien usa los emojis que se usa en la app de android (NO CAMBIES NADA DE LA APP DE ANDROID ES PARA QUE LOS USES DE REFERENCIA) que son emojis con el color de la app para lo de mi cuenta y ajustes, no esos emojis feos de windows. Tambien agrega que si yo activo el bloqueo cruzado en el telefono se vea activo en la PC y viceversa, está vez tu vas a compilar la V23 y a crear el reporte así como te había explicado, con todo como estaba antes, mi instrucción exacta y los cambios que le hiciste"*

---

## 🔍 2. Estado Anterior (Como estaba antes en v22.0.0)

1. **Menú de Perfil y Ajustes solo en el popup**: el menú desplegable (Mi Cuenta / Ajustes) existía únicamente en `popup.html`. Las pestañas del bloc de notas (`notes.html`) y de iniciar sesión (`login.html`) **no** tenían acceso a los ajustes.
2. **Modo oscuro no sincronizado en todas las pestañas**: el tema se aplicaba en el popup y en las notas, pero la página de iniciar sesión (`login.html`) se quedaba siempre en claro y sin escuchar los cambios remotos.
3. **Emojis de Windows en el menú**: los items usaban emojis del sistema (👤, ⚙️, 🌙, 🔄), no los iconos con el color de la app que usa Android (`Icons.Default.Person`, `Icons.Default.Settings`, `Icons.Default.DarkMode`, `Icons.Default.Sync` con tinte `ZenOlive`).
4. **Versión del proyecto**: `v22.0.0` (`versionCode = 22`).

---

## 🛠️ 3. Cambios Realizados y Soluciones Implementadas (Versión 23.0.0)

### A. Menú de Perfil y Ajustes en TODAS las pestañas de la extensión
- **`notes.html`**: se reemplazó el `user-pill` por el botón de perfil (`btn-profile-menu` con avatar 👤 de la app, email o "Cargando cuenta...", chevron ▾) que abre el desplegable con **Mi Cuenta** (modal con Iniciar Sesión con Google) y **Ajustes** (modal con toggles Modo Oscuro y Bloqueo Cruzado).
- **`login.html`**: se agregó un menú de perfil flotante (esquina superior derecha) con **Mi Cuenta** (dispara el inicio de sesión con Google) y **Ajustes** (modal con los toggles).
- **`popup.html`**: sin cambios de lógica, ya tenía el menú desde V20.3.

### B. Sincronización del Modo Oscuro en las 2 pestañas (y viceversa)
- **`notes.js`** y **`login.js`**: nueva función `applyTheme(dark)` que aplica `data-theme="dark"` en `<html>`, carga los ajustes con `getSettings` al abrir y escucha `settingsChanged` para reflejar cambios venidos del popup, del teléfono o de la otra pestaña al instante.
- Toggle en cualquier pestaña → `setSetting('darkMode')` → `background.js` guarda en `chrome.storage.local`, publica en Firebase `/users/<target>/config/dark_mode_enabled.json` y difunde `settingsChanged` a popup, notas y login.
- **`notes.css`** y **`login.html`** (CSS): paleta oscura replicando la de la app (`#0F171E`, `#1B252E`, `#F1F5F9`, `#94A3B8`, `#5F7564`).

### C. Iconos con el color de la app (referencia Android, sin tocar Android)
- Se crearon 4 SVGs estilo Material con relleno `ZenOlive #5A6E61` (los mismos iconos que Android pinta con `tint = ZenOlive`), reemplazando los emojis de Windows:
  - `emojis/account_icon.svg` (Person) → Mi Cuenta.
  - `emojis/settings_icon.svg` (Settings) → Ajustes.
  - `emojis/dark_mode_icon.svg` (DarkMode) → Modo Oscuro.
  - `emojis/sync_icon.svg` (Sync) → Bloqueo Cruzado.
- Aplicados en el desplegable, los encabezados de los modales y las filas de ajustes de `popup.html`, `notes.html` y `login.html`.
- **NO se modificó ningún archivo de la app Android** (solo se usó como referencia de iconos y colores).

### D. Bloqueo Cruzado activo visible en la PC y el teléfono (y viceversa)
- El toggle **Bloqueo Cruzado** ahora existe en popup, notas y login, y todos apuntan al mismo `setSetting('crossDeviceLock')`.
- `background.js` ya lee `/users/<target>/config/cross_device_lock_enabled.json` en cada ciclo de sincronización: si el teléfono lo activa/desactiva, `background` lo actualiza en memoria/storage y difunde `settingsChanged`, de modo que el interruptor se ve activo (o inactivo) en tiempo real en la PC. A la inversa, cambiarlo en la PC lo publica en Firebase y la app Android lo recibe por su listener de `config`.

### E. Compilación de la V23
- `build.gradle.kts`: `versionCode = 23`, `versionName = "23.0.0"`.
- Compilación SOLO con Gradle (`.\gradlew.bat assembleDebug --console=plain`) → **BUILD SUCCESSFUL** (5s, 7 ejecutadas / 28 up-to-date).
- APK generado: `app/build/outputs/apk/debug/app-debug.apk` (19.4 MB) con `versionCode = 23`, `versionName = "23.0.0"` (confirmado en `output-metadata.json`).

---

## 📁 4. Detalle de Archivos Modificados

### Extensión web
1. **[`extension_chrome/emojis/account_icon.svg`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/emojis/account_icon.svg)**: icono Person de Material en `ZenOlive` (nuevo).
2. **[`extension_chrome/emojis/settings_icon.svg`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/emojis/settings_icon.svg)**: icono Settings de Material en `ZenOlive` (nuevo).
3. **[`extension_chrome/emojis/dark_mode_icon.svg`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/emojis/dark_mode_icon.svg)**: icono DarkMode de Material en `ZenOlive` (nuevo).
4. **[`extension_chrome/emojis/sync_icon.svg`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/emojis/sync_icon.svg)**: icono Sync de Material en `ZenOlive` (nuevo).
5. **[`extension_chrome/popup.html`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.html)**: iconos de la app en el desplegable, avatar, encabezados de modales y filas de ajustes.
6. **[`extension_chrome/popup.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.js)**: sin cambios de lógica (el menú y la sincronización ya existían).
7. **[`extension_chrome/styles.css`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/styles.css)**: `.menu-icon`, `.menu-icon-white`, `.setting-label` flex para alinear los iconos.
8. **[`extension_chrome/notes.html`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/notes.html)**: menú de perfil/ajustes en el header y modales Mi Cuenta + Ajustes.
9. **[`extension_chrome/notes.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/notes.js)**: lógica del desplegable, modales, toggles sincronizados, `getSettings` + `settingsChanged` con actualización de toggles.
10. **[`extension_chrome/notes.css`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/notes.css)**: estilos del menú, switches Zen, modales y overrides oscuros.
11. **[`extension_chrome/login.html`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/login.html)**: menú de perfil flotante, modal Ajustes, CSS del menú/modales/switch y overrides oscuros.
12. **[`extension_chrome/login.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/login.js)**: `applyTheme`, menú desplegable (Mi Cuenta → login, Ajustes → modal), toggles sincronizados, `getSettings` + `settingsChanged`.

### App Android
13. **[`app/build.gradle.kts`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/build.gradle.kts#L16-L17)**: `versionCode = 23`, `versionName = "23.0.0"`. (Único cambio Android: la versión, para la compilación.)

---

## 🔬 5. Verificaciones Realizadas

- `node --check` aprobado en `popup.js`, `background.js`, `notes.js` y `login.js` tras los cambios.
- Confirmado por inspección: `getSettings`/`setSetting` en `background.js` + `broadcastSettingsChanged` mantienen el modo oscuro y el bloqueo cruzado sincronizados entre popup, notas, login y la app Android (vía `/users/<target>/config/`).
- Compilación V23: `.\gradlew.bat assembleDebug --console=plain` → **BUILD SUCCESSFUL**; `output-metadata.json` confirma `versionCode 23` / `versionName "23.0.0"`.
- No se modificó código de la app Android (solo la versión en `build.gradle.kts`).

---

## ⚠️ 6. Acción Requerida en la PC (No es un bug)

- Después de modificar los archivos de la extensión, **Chrome NO recarga extensiones desempacadas automáticamente**. En `chrome://extensions` hay que pulsar el botón de recargar (🔄) de la tarjeta "AntiProcrastinación" para que los cambios surtan efecto (popup, notas y login).
- El APK `app-debug.apk` (V23) queda listo para instalar en el teléfono.
