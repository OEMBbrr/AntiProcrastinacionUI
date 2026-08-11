# Reporte: Funcionalidad de Reconocimiento de Fechas y Mejoras en Notas

## 1. Reconocimiento Automático de Fechas (NLP en Notas)
La idea de detectar fechas y horas en las notas (ej. "Reunión *mañana a las 4pm*") es excelente. Permite que las notas dejen de ser texto estático y se conviertan en acciones vivas y programables.

### Cambios necesarios en Android (`app/src/...`)
1. **Detección NLP Local**: Utilizar una librería de procesamiento de lenguaje natural en Android (como *Natty* para Java/Kotlin o expresiones regulares avanzadas) para escanear el texto de cada nota al guardarla.
2. **Integración con Alarmas/Calendario**: Cuando Android detecte un patrón de fecha, el texto en la UI será subrayado como un enlace. Al tocarlo, un `BottomSheet` te preguntará si deseas programar una alarma del sistema (`AlarmManager`) o añadirlo al calendario nativo.
3. **Modelo en Firebase**: El objeto de la nota subido a Firebase debe agregar un array nuevo: `reminders: [1754029402000, ...]`.

### Cambios necesarios en la Extensión de Chrome (`notes.js`)
1. La extensión usaría una librería ligera de JS como *ChronoNode* (o Regex manual) al renderizar la tarjeta de la nota.
2. Si detecta texto temporal, lo envolverá en un `<span class="date-highlight">` (en color coral o verde oliva).
3. Si el momento definido llega mientras usas la PC, el `background.js` activaría un `chrome.notifications.create` avisándote del evento.

---

## 2. Otras ideas de altísimo valor para agregar a las Notas

- **Botón "Enfocarme en esto"**: Al escribir una nota como *"Estudiar matemáticas"*, podrías darle un clic a un botón de "Target 🎯" en la tarjeta. Esto iniciaría de inmediato el temporizador de Bloqueo/Pomodoro, colocando el título de tu nota como la meta oficial de la sesión en tu pantalla.
- **Links Automáticos y Smart Chips**: Si escribes un link (ej. `youtube.com/tutorial`), la app automáticamente lo renderiza como una caja cliqueable.
- **Soporte para Listas Rápidas (Markdown)**: Poder escribir con guiones o números y que la nota entienda que estás haciendo una Check-list (donde puedas marcar checkboxes de tareas terminadas directamente desde la tarjeta).
