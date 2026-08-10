# 🔍 REPORTE DE AUDITORÍA DE CÓDIGO Y NARRATIVA DEL SISTEMA DUAL

- **Fecha de Auditoría y Planificación**: 10 de Agosto de 2026
- **Estado del Código**: Auditado (Sin modificaciones de código por orden expresa del usuario)
- **Alcance del Plan**: Ajustes coordinados en **Aplicación Android** y **Extensión de Chrome**
- **Ubicación del Reporte**: `C:\Users\USUARIO\Documents\AntiProcrastinacion\cambios y reportes\REPORTE_AUDITORIA_SISTEMA_DUAL.md`

---

## 📌 1. Resumen Ejecutivo y Alcance Dual

Este plan de mejoras requiere modificaciones coordinadas tanto en el **Código Android (Kotlin / Jetpack Compose)** como en la **Extensión de Chrome (JavaScript MV3 / Service Worker)** para garantizar la sincronización bilateral sin conflictos.

---

## 🐞 2. Hallazgos y Modificaciones por Componente

### 📲 A. Ajustes Requeridos en la Aplicación Android (`app/src/main/java/...`)
1. **[`LockManager.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/LockManager.kt)**:
   - Forzar la publicación de `source_device: "android"` al activar cualquier temporizador de enfoque para despertar inmediatamente el Service Worker de la extensión.
   - Desvincular `dark_mode_enabled` del nodo global compartido `/config` para que las `SharedPreferences` locales del teléfono gestionen el tema visual sin alterar la PC.
2. **[`ConfigScreen.kt`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ConfigScreen.kt)**:
   - Ajustar los controles de la tarjeta de Ajustes para guardar la preferencia visual localmente.
   - Incorporar la UI del temporizador Pomodoro personalizable con tope máximo de 30 minutos de descanso.

---

### 🌐 B. Ajustes Requeridos en la Extensión de Chrome (`extension_chrome/...`)
1. **[`background.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/background.js)**:
   - Implementar alarmas activas (`chrome.alarms`) y escuchadores de navegación para evitar que Manifest V3 duerma el proceso de escucha del teléfono.
   - Modificar la lectura de `/config.json` para no sobrescribir el `darkModeEnabled` guardado localmente en `chrome.storage.local`.
2. **[`popup.js`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.js), [`popup.html`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/popup.html) y [`styles.css`](file:///C:/Users/USUARIO/Documents/AntiProcrastinacion/extension_chrome/styles.css)**:
   - Ajustar la interfaz del popup para reflejar el estado del temporizador Pomodoro y gestionar el tema visual de la PC de manera independiente.

---

## 🚀 3. Propuestas Aprobadas para el Sistema Dual

### ⏱️ 1. Modo Pomodoro Sincronizado Personalizable (Máximo 30m de Descanso)
- **Android + Extensión**: Tiempos de trabajo y descansos personalizables, con tope máximo de **30 minutos de descanso**. Sincronización bilateral.

### 🌓 2. Modo Oscuro / Claro 100% Independiente
- **Android + Extensión**: La app en el teléfono puede estar en Modo Oscuro mientras la extensión en la PC está en Modo Claro (o viceversa) según la preferencia del usuario en cada dispositivo.

### 🔑 3. Tregua de Emergencia Verificada por Segundo Dispositivo
- **Android + Extensión**: Pedir tregua desde la PC requiere validación corta en el teléfono para evitar tentaciones.

### ⚡ 4. Servidor WebSocket Local de Baja Latencia
- **Android + Extensión**: Comunicación LAN directa por WebSocket a menos de 10ms cuando ambos comparten Wi-Fi.

### 🛡️ 5. Protección de Ajustes por PIN / Biometría
- **Android**: Bloqueo con PIN o huella dactilar para evitar modificar ajustes durante el enfoque activo.
