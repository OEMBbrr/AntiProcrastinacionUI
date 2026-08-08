# DOCUMENTO DE ARQUITECTURA TÉCNICA: SINCRONIZACIÓN EN TIEMPO REAL
================================================================================
PROYECTO: AntiProcrastinación
TECNOLOGÍA BASE: Firebase Realtime Database (BaaS)
================================================================================

## 1. EL ENFOQUE DE ESTADO GLOBAL (SINGLE SOURCE OF TRUTH)
El ecosistema evoluciona de un modelo de "estado local" (donde cada dispositivo maneja su propio temporizador) a un "estado global". Firebase será la única fuente de la verdad. Si Firebase dice que el usuario está bloqueado, todos los dispositivos vinculados acatan la orden instantáneamente.

## 2. ESTRUCTURA DE LA BASE DE DATOS (JSON)
Para mantener el consumo de datos cercano a cero y evitar que la base de datos crezca, utilizaremos el método de sobrescritura en lugar de acumular un historial.

Estructura del árbol JSON en Firebase Realtime Database (formato de sangría):

    {
      "users": {
        "UID_USUARIO_12345": {
          "profile": {
            "email": "usuario@email.com",
            "role": "admin" // 'admin' (padre) o 'child' (hijo)
          },
          "lock_state": {
            "is_locked": true,
            "expires_at": 1723156000,
            "allowed_apps": ["com.whatsapp", "com.google.android.calculator"]
          }
        }
      }
    }

*Nota: Al usar sobrescritura, el tamaño de la base de datos no superará los pocos kilobytes por usuario.*

## 3. FLUJO DE COMUNICACIÓN

### A. Desde la App Android (Kotlin)
1. El usuario administrador (padre) presiona "Bloquear".
2. La app escribe en Firebase: users/UID/lock_state/is_locked = true.
3. El LockMonitoringService local también se activa para proteger el propio teléfono.

### B. En la Extensión de Chrome (Manifest V3)
1. Al iniciar sesión, la extensión se conecta a Firebase y "escucha" el nodo users/UID/lock_state.
2. Cuando el Android cambia el valor a true, Firebase hace un "push" por WebSocket.
3. El background.js (Service Worker) recibe el evento en milisegundos.
4. La extensión inyecta el bloqueo de red (declarativeNetRequest) redirigiendo las redes sociales al blocked.html.

## 4. CONSIDERACIONES TÉCNICAS Y REGLAS DE SEGURIDAD (FIREBASE RULES)
Para evitar que un usuario manipule el bloqueo desde la consola del navegador, las reglas de Firebase deben asegurar que solo el 'admin' puede escribir en el nodo lock_state, mientras que los dispositivos 'child' solo tienen permiso de lectura:

    {
      "rules": {
        "users": {
          "$uid": {
            ".read": "auth != null && auth.uid == $uid",
            "lock_state": {
              ".write": "auth != null && auth.uid == $uid && data.parent().child('profile/role').val() == 'admin'"
            }
          }
        }
      }
    }
