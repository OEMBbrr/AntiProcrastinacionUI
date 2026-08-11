# Reporte de Auditoría: Sincronización Cruzada de Tregua (Teléfono -> Extensión)

## Problema Detectado
Actualmente, existe un problema de sincronización bidireccional relacionado con el modo "Tregua" (descanso de 5 minutos):
1. **Extensión a Teléfono (Solucionado)**: Cuando el usuario solicitaba una tregua desde la extensión, el temporizador principal (`remainingSeconds`) era sobrescrito a 300 segundos (5 minutos), haciendo que el usuario perdiera su tiempo original de enfoque (ej. 5 horas se convertían en 5 minutos). 
   - **Solución implementada**: Se ha corregido en la extensión de Chrome (`background.js`) implementando una variable `treguaUntil` que permite el bypass temporal sin afectar el `remainingSeconds` original.

2. **Teléfono a Extensión (Pendiente)**: Cuando el usuario activa una "Tregua" directamente desde la app móvil (teléfono), la extensión de Chrome **no se entera** de esta pausa y sigue bloqueando las páginas.

## Qué hacer en el código de la App (iOS/Android)
Para solucionar el punto 2, es necesario que la aplicación móvil comunique su estado de tregua hacia la extensión a través de Firebase (o WebSocket). 

### Pasos a seguir en la App Móvil:
1. **Modificar el `lock_state.json`**:
   Cuando se active la tregua en el teléfono, la app debe incluir un nuevo campo en la sincronización de Firebase:
   ```json
   {
     "is_locked": true,
     "expires_at": 1720000000000,
     "tregua_until": 1720000300000 // <- NUEVO CAMPO: Timestamp de cuándo se acaba la tregua
   }
   ```
2. **Alternativa (Tregua Request)**:
   Si se prefiere mantener separado, la app puede escribir en `/users/{uid}/tregua_request.json` un objeto indicando que la tregua fue activada por el teléfono:
   ```json
   {
     "requester": "phone",
     "approved": true,
     "expires_at": 1720000300000
   }
   ```

### Pasos a seguir en la Extensión (Una vez se haga en la app):
En `background.js`, dentro de `pollFirebaseSync()` donde se lee el `lockState`, se debe añadir la lógica para leer este nuevo campo:
```javascript
// Si la app móvil envía tregua_until
if (lockState.tregua_until && lockState.tregua_until > Date.now()) {
    treguaUntil = lockState.tregua_until;
    updateNetRules();
}
```

*Nota: Estos cambios en la app requerirán modificación en el código Dart (`lib/main.dart` u otros managers de estado que interactúan con Firebase).*
