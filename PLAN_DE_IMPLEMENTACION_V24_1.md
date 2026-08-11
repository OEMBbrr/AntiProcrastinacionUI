# 📋 PLAN DE IMPLEMENTACIÓN v24.1

- **Versión**: 24.1 (marcador para distinguir estos cambios)
- **Ritmo**: lento pero seguro. Uno por uno, de lo más difícil a lo más fácil.
- **Método**: cada cambio incluye una **SIMULACIÓN** (Firebase + lógica interna) y una **COMPILACIÓN/VERIFICACIÓN** antes de dar por cerrado.
- **Alcance**: App Android (`app/`) + Extensión Chrome (`extension_chrome/`). Web/iOS no se tocan aquí.

---

## 📊 Orden de ejecución (de más difícil a más fácil)

| # | Tarea | Dificultad | Simulación |
|---|-------|-----------|------------|
| 1 | Rediseño del flujo 2FA del Bloqueo Cruzado (el código no llega al teléfono) | 🔴 Muy alta | Script Firebase que simula la solicitud de la extensión → generación del código en el "teléfono" → verificación |
| 2 | Categorías de notas desincronizadas (Android vs Extensión) | 🟠 Alta | Script que escribe notas desde ambos lados con distintos formatos y verifica la normalización |
| 3 | Scroll roto en ConfigScreen y ZenScreen | 🟡 Media | Compilación + verificación estructural (sin anidar verticalScroll) |
| 4 | Menú Pomodoro de Android confuso (no se ve el valor de minutos) | 🟡 Media | Verificación de UI + simulación de `computePomodoroLimits` |
| 5 | Logo / "MODO ENFOQUE" / frase ocultos bajo la cámara (Dynamic Island) | 🟢 Baja | Compilación + captura de pantalla ADB |

---

## 🔴 TAREA 1 — REDISEÑO DEL FLUJO 2FA DEL BLOQUEO CRUZADO

### Problema (reportado por el usuario)
> El código de autenticación de dos pasos no funciona: el código no llega al teléfono. Se necesita que la extensión solicite el código, el teléfono genere un código y le comunique a la extensión cuál es el correcto, y que al escribir el código en la extensión ésta verifique con la aplicación que sea correcto para permitir desactivar el Bloqueo Cruzado.

### Causa raíz (verificada en el código)
- Hoy el **código lo genera la extensión** (`background.js:448-481`, `requestAuthCode`) y lo publica en `/users/<target>/auth_request.json`.
- El teléfono lo lee por `userRef()?.child("auth_request")` (`LockManager.kt:765-792`) y muestra la notificación (`LockMonitoringService.kt:104-141`).
- **Fallo principal**: el `target` que usa la extensión y el que escucha el teléfono pueden **no coincidir**:
  - Extensión: `getTargetKey()` → `userEmail` → `syncKey` → `firebaseUid` (`background.js:95-100`).
  - Android: `userRef()` → `googleUserEmail` → `userSyncKey` (`LockManager.kt:542-548`).
  - Si el PC está con correo de Google y el teléfono emparejado por PIN (`ZEN-XXXX`), escriben y escuchan en nodos distintos → **el código nunca llega**.
- Además, el flujo depende de que el servicio Android esté vivo y del estado local del service worker (MV3).

### Diseño nuevo (lo que pidió el usuario)
1. La extensión **solicita** un código (escribe `auth_request` con `status: "requesting"`, sin código).
2. El teléfono (listener de Firebase) **recibe la solicitud, genera el código**, muestra la notificación **y publica el código correcto** de vuelta (en el mismo nodo `auth_request` → `status: "pending", code: "XXXX"`).
3. La extensión **lee el código publicado por el teléfono** (polling corto) y lo guarda como `pendingAuth.code` (lo conoce, pero **no lo muestra**).
4. El usuario escribe el código en el popup → la extensión **compara** con el código del teléfono (o lo envía al teléfono para verificar).
5. Si coincide → aplica el toggle del Bloqueo Cruzado y publica `status: "approved"`. Si no → error "código incorrecto".

### Cambios a realizar
**Extensión (`extension_chrome/background.js`):**
- `requestAuthCode`: publicar `{id, requester:'chrome_extension', status:'requesting', requested_at, expires_at}` **sin código**.
- Añadir un **polling corto** (ej. cada 1 s, máx ~60 s) que lea `auth_request` buscando `status === 'pending' && code` y lo guarde en `pendingAuth.code` (no mostrarlo).
- `verifyAuthCode`: comparar con `pendingAuth.code` y, al aprobar, publicar `{id, status:'approved', approved_at}`.
- Unificar el `target`: **prioridad fija** `syncKey` → `email` → `uid` en `getTargetKey()` para que coincida con Android (y log de depuración del nodo en uso).
- Timeout/expirados: limpiar `pendingAuth` y `auth_request` caducados.

**App Android:**
- `LockManager.kt` `startAuthRequestListener` (L765): detectar `status === "requesting"` (nuevo), **generar el código** con `Random.nextInt(1000..9999)`, mostrar la notificación y **publicar** `{id, status:'pending', code, code_expires_at}` en `auth_request`.
- `userRef()` (L542): fijar la **misma prioridad** que la extensión (`syncKey` → email) para garantizar el mismo nodo.
- `LockMonitoringService.kt`: mantener la notificación 2FA (ya existe, se reutiliza).
- Asegurar que el servicio arranca con la app para que el listener esté activo al solicitar el código.

**Popups/UI (no cambiar diseño, solo textos):**
- `popup.html`: texto del modal → "Revisa la notificación en tu teléfono y escribe el código".
- `popup.js`: `requestAuthForToggle`/`verifyAuthCode` siguen iguales (ya llaman al background).

### Simulación (obligatoria)
Crear `simulaciones/simular_2fa.mjs` (Node):
- Obtiene token anónimo con `identitytoolkit.googleapis.com` (`accounts:signUp`), igual que la extensión.
- Simula el lado **extensión**: escribe `auth_request` con `status:'requesting'` bajo una **clave de prueba** (`sim_2fa_<timestamp>`).
- Simula el lado **teléfono**: detecta el request, genera código, publica `status:'pending'` + `code`.
- Simula el **polling de la extensión** y verifica que encuentra el código.
- Verifica un código correcto → `approved`; y un código incorrecto → rechazado.
- **Limpia** el nodo de prueba al final (no toca datos reales).

### Verificación final
- `node --check` de los JS modificados.
- Compilar APK (`gradlew assembleDebug`) y verificar que el listener publica el código.
- Prueba real en vivo (PC + teléfono) al final del lote.

---

## 🟠 TAREA 2 — CATEGORÍAS DE NOTAS DESINCRONIZADAS

### Problema (reportado por el usuario)
> Si guardo una nota como "idea" en el teléfono, en la página (extensión) me sale como "General". Y viceversa: si guardo una nota con una categoría (idea, tarea, etc.) desde la extensión, en el teléfono no sale con su categoría, sale como "General".

### Causa raíz (verificada en el código)
- **Android guarda la categoría capitalizada**: `"General"`, `"Tarea"`, `"Idea"`, `"Reflexión"` (`ZenNotesModal.kt:53-54`, `LockManager.kt:932-940`).
- **La extensión usa claves minúsculas**: `'general'`, `'tarea'`, `'idea'`, `'reflexion'` (`notes.js:257-264`, `notes.html:60-63`).
- Consecuencia:
  - Nota "Idea" desde Android → Firebase guarda `"Idea"` → `getCatLabel('Idea')` en la extensión no coincide con `case 'idea'` → cae en `default` → **"General"**.
  - Nota `'idea'` desde la extensión → el badge de Android muestra `'idea'` tal cual y los filtros comparan contra `"Idea"` → **no coincide** → aparece como general/descolocada.

### Solución
**Normalizar la categoría a una clave canónica única en ambos lados:**
- Claves canónicas (minúsculas, sin acentos): `'general'`, `'tarea'`, `'idea'`, `'reflexion'`.
- En Firebase siempre se guarda la clave canónica.
- Cada app traduce clave → etiqueta al mostrar.

### Cambios a realizar
**Android:**
- `LockManager.kt` `addNote` (L932): normalizar la categoría de entrada a clave canónica (mapeo "Idea"→'idea', "Reflexión"→'reflexion', etc.; si es desconocida → 'general').
- `ZenNotesModal.kt`: al mostrar el badge y filtrar, traducir clave canónica → etiqueta ("Idea", "Reflexión"...); normalizar al comparar filtros.
- `ZenNotesModal.kt` creación (L51-54): mapear la etiqueta elegida a clave canónica antes de `addNote` (o normalizarlo en `addNote`).

**Extensión:**
- `notes.js`: `getCatLabel` ya soporta claves canónicas; añadir normalización de entrada por si Firebase trae "Idea"/"Reflexión" viejas (lowercase + quitar acentos) para robustez.
- `background.js` `getNotes`/`addNote` (L566-623): normalizar `category` a clave canónica al leer y al escribir.

### Simulación (obligatoria)
Crear `simulaciones/simular_categorias.mjs`:
- Escribe notas de prueba bajo clave `sim_cat_<timestamp>` con formatos: `"Idea"`, `'idea'`, `"Reflexión"`, `'reflexion'`, `"Tarea"`, `'general'`.
- Verifica que la función de normalización las convierte todas a la clave canónica esperada.
- Verifica la traducción clave → etiqueta para ambas apps.
- **Limpia** el nodo de prueba.

### Verificación final
- `node --check` de los JS modificados.
- Compilar APK.
- Prueba manual: crear "Idea" en el teléfono → verla como Idea en la página; crear "Idea" en la página → verla como Idea en el teléfono.

---

## 🟡 TAREA 3 — SCROLL EN ConfigScreen Y ZenScreen

### Problema (reportado por el usuario)
> La app de Android no permite hacer scroll (subir/bajar) para ver toda la información. Cuando el menú de aplicaciones está abierto y el menú de Pomodoro abierto, no deja ver la información de abajo y el menú de aplicaciones se ve raro. Se necesita poder subir y bajar.

### Causa raíz (verificada en el código)
- `ConfigScreen.kt:170-178`: el `Column` raíz ya tiene `.verticalScroll(rememberScrollState())`, pero hay **listas/menús anidados**:
  - Modal Ajustes (`ConfigScreen.kt:479-482`): `Column` con su propio `verticalScroll` **dentro** de otro scroll → conflicto.
  - Menú de aplicaciones (`ConfigScreen.kt:1193`): `LazyColumn` dentro de contenido con scroll.
- `ZenScreen.kt:190-200`: el `Column` principal usa `Arrangement.SpaceBetween` y **no tiene scroll**; el contenido (banner de descanso, botones, notas) se corta en pantallas pequeñas.

### Cambios a realizar
- `ConfigScreen.kt`:
  - Revisar el modal de Ajustes (L479) y el modal de apps para que **no aniden `verticalScroll`/`LazyColumn` dentro de otro scroll** (el modal debe scrollear por sí solo, no el padre).
  - Asegurar que el contenido del modal de aplicaciones sea scrolleable con límite de altura (`heightIn` + `LazyColumn`), y que los steppers Pomodoro queden visibles sin cortarse.
- `ZenScreen.kt`:
  - Añadir `.verticalScroll(rememberScrollState())` al `Column` principal (L193) manteniendo `fillMaxSize` para que todo el contenido (cabecera, temporizador, banner descanso, botones) se pueda subir/bajar en pantallas pequeñas.
  - Ajustar `Arrangement.SpaceBetween` para que no rompa con el scroll (pasar a `spacedBy` si hace falta).

### Simulación (obligatoria)
- Compilación limpia del APK.
- Revisión estructural por script: `simulaciones/verificar_scroll.mjs` que busca en `ConfigScreen.kt` y `ZenScreen.kt` patrones prohibidos (`verticalScroll` dentro de `verticalScroll`, `LazyColumn` dentro de `verticalScroll`) y los reporta.
- Captura de pantalla ADB del menú de aplicaciones y del menú Pomodoro abiertos (se hace al instalar).

### Verificación final
- Compilar APK sin errores.
- ADB: instalar y confirmar que ConfigScreen y ZenScreen scrollean correctamente.

---

## 🟡 TAREA 4 — MENÚ POMODORO DE ANDROID: MOSTRAR VALOR DE MINUTOS CLARO

### Problema (reportado por el usuario)
> El menú anterior de Pomodoro era mejor: se podían modificar bien el número de descansos y los minutos. Ahora el contador de descansos está muy grande, no se ve si hay un contador de minutos, y aunque dice "máximo 30 minutos" no se puede ver exactamente en cuánto se está ajustando el tiempo al subir/bajar.

### Causa raíz (verificada en el código)
- `ConfigScreen.kt:1022-1102`: la tarjeta Pomodoro usa `IconButton` de 36dp con `+/-`, y el valor de minutos está en un `Text` pequeño al lado de la etiqueta "Duración del descanso (máx X)". La jerarquía visual no hace evidente el **valor actual** de minutos; con el problema de scroll (Tarea 3) el stepper de minutos queda cortado → "no veo el contador de minutos".

### Cambios a realizar
- `ConfigScreen.kt` tarjeta Pomodoro:
  - Rediseñar el stepper de **minutos** para que el valor actual (`X min`) sea grande y visible (estilo del menú anterior), con `+/-` compactos.
  - Ídem para el número de descansos (contador compacto y claro).
  - Mostrar siempre "Descansos: N" y "Descanso: X min" con el valor destacado (negrita + color), no solo la etiqueta pequeña.
  - Respetar los límites `computePomodoroLimits` (regla 25%) ya implementados en `LockManager.kt:378-384`.
- Alinear la UI con la extensión (`popup.html:83-97`) para consistencia: "Descansos: N" / "Descanso (máx M): X min".

### Simulación (obligatoria)
- `simulaciones/simular_pomodoro_limites.mjs`: replica `computePomodoroLimits` de Android (`LockManager.kt:378-384`), de `popup.js:121-127` y de `background.js:775-782`; **verifica que los tres devuelven los mismos límites** (y reporta la diferencia de background.js como pendiente de alineación).
- Compilación APK + captura de pantalla ADB de la tarjeta Pomodoro.

### Verificación final
- Compilar sin errores.
- ADB: confirmar que al subir/bajar descansos y minutos se ve claramente el valor actual.

---

## 🟢 TAREA 5 — LOGO / "MODO ENFOQUE" / FRASE BAJO LA CÁMARA (DYNAMIC ISLAND)

### Problema (reportado por el usuario)
> El logo, el nombre del modo de enfoque y la frase motivadora se siguen ocultando debajo de la cámara del teléfono. Hay que ajustarlos un poco más abajo para que no queden ocultos, y con la opción de scrollear (Tarea 3) quedaría bien.

### Causa raíz (verificada en el código)
- `ZenScreen.kt:201-263`: la cabecera tiene `Column` con `padding(top = 12.dp)` y el `Scaffold` solo aplica `innerPadding`. En pantallas con cámara perforada / Dynamic Island el contenido queda bajo la cámara.

### Cambios a realizar
- `ZenScreen.kt` cabecera (L202-204): aumentar el padding superior a **~56.dp** (o usar `statusBarsPadding()` + margen) para que el logo, "MODO ENFOQUE" y la tarjeta de frase queden debajo de la cámara.
- Ajustar para que con el scroll (Tarea 3) la cabecera quede centrada/visible en cualquier pantalla.

### Simulación (obligatoria)
- Compilación APK.
- Captura de pantalla ADB de ZenScreen (enfoque activo) para confirmar que nada queda bajo la cámara.

### Verificación final
- Compilar sin errores.
- ADB: captura y confirmación visual.

---

## 📦 Control de versiones y entregables
- `app/build.gradle.kts`: subir `versionCode` a `241` y `versionName` a `"24.1.0"`.
- Respaldos APK: `AntiProcrastinacion_v24_1.apk` (cada vez que se compile).
- Reporte final: `cambios y reportes/REPORTE_CAMBIOS_V24_1.md` siguiendo el formato de `REPORTE_CAMBIOS_V24.md`.
- Actualizar `CONTEXTO_IA_ESTADO_ACTUAL.md` al terminar.

## 🧪 Infraestructura de simulación
- Carpeta nueva: `simulaciones/` con scripts Node reutilizables (conectan a la misma Firebase RTDB con token anónimo y **siempre limpian** los nodos de prueba `sim_*`).
- Los scripts se ejecutan con `node simulaciones/<script>.mjs`.
- Regla: **no escribir en nodos reales** de usuario (solo `sim_*`), no modificar datos reales.
