package com.antiprocrastinacion.lock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*

// V24: fase del Pomodoro sincronizado (trabajo o descanso) con tiempos absolutos
data class PomodoroPhase(val type: String, val startTime: Long, val endTime: Long, val title: String = "")

// V32: segmento del plan de actividades (trabajo con título o descanso) del Modo Enfoque
data class FocusSegment(val type: String, val title: String, val durationMinutes: Int)

class LockManager private constructor(context: Context) {
    // V31: contexto de aplicación (para WRITE_SETTINGS, dialer/SMS y clasificación de apps)
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("anti_procrastinacion_prefs", Context.MODE_PRIVATE)

    // Servidor LAN para descubrimiento local por Wi-Fi
    val lanServer = LanServer(context, this)

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firebaseDb: FirebaseDatabase by lazy { FirebaseDatabase.getInstance("https://antiprocrastinacion-26975-default-rtdb.firebaseio.com") }

    init {
        // Inicializar clave de dispositivo única si no existe
        if (prefs.getString("device_unique_pin", null) == null) {
            val randomPin = "ZEN-" + (1000..9999).random()
            prefs.edit().putString("device_unique_pin", randomPin).apply()
        }
        lanServer.start()

        try {
            pushDeviceHeartbeatToFirebase()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // V24 (Bug 4 de la auditoría): Firebase Anonymous Auth como respaldo.
        // Garantiza un token válido (auth != null) para las reglas de RTDB aunque
        // el usuario aún no haya iniciado sesión con Google (modo PIN/sync key).
        try {
            ensureAnonymousAuth()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * V24 (Bug 4): inicia sesión anónima en Firebase si no hay ningún usuario.
     * El SDK adjunta automáticamente el token a todas las operaciones de RTDB.
     */
    fun ensureAnonymousAuth() {
        if (firebaseAuth.currentUser != null) return
        firebaseAuth.signInAnonymously()
            .addOnSuccessListener { result ->
                android.util.Log.d("ZEN_SYNC", "Firebase auth anónima activada (uid=${result.user?.uid})")
            }
            .addOnFailureListener { err ->
                android.util.Log.e("ZEN_SYNC", "Fallo al crear auth anónima: ${err.message}")
            }
    }

    companion object {
        // V31: SINGLETON — el servicio, la activity, el widget y el listener de
        // notificaciones comparten UNA sola instancia (un solo LanServer y latido).
        @Volatile private var instance: LockManager? = null

        fun getInstance(context: Context): LockManager {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: LockManager(app).also { instance = it }
            }
        }

        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_LOCK_END_TIME = "lock_end_time"
        private const val KEY_LONG_PHRASE = "long_phrase"
        private const val KEY_MEDIUM_PHRASE = "medium_phrase"
        private const val KEY_SHORT_PHRASE = "short_phrase"
        
        private const val KEY_TEMP_UNLOCK_END_TIME = "temp_unlock_end_time"
        private const val KEY_COOLDOWN_END_TIME = "cooldown_end_time"
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"

        // V20: preferencias de ajustes
        private const val KEY_DARK_MODE = "dark_mode_enabled"
        private const val KEY_CROSS_DEVICE_LOCK = "cross_device_lock_enabled"

        // V26: acento del sistema (preset de color elegido en Ajustes -> Colores)
        private const val KEY_ACCENT_PRESET = "accent_preset_key"

        // V24 (Propuesta 1): Pomodoro sincronizado entre dispositivos
        private const val KEY_POMODORO_PHASES = "pomodoro_phases_json"
        // V32: plan de actividades del Modo Enfoque + anuncio de segmento
        private const val KEY_FOCUS_ACTIVITY_PLAN = "focus_activity_plan_json"
        private const val KEY_FOCUS_LAST_ANNOUNCED = "focus_last_announced_segment"

        // V28: Modo Escuela/Trabajo (activación + horario L-V)
        private const val KEY_WORK_MODE_ENABLED = "work_mode_enabled"
        private const val KEY_WORK_SCHEDULE = "work_schedule_json"
        private const val KEY_WORK_WHATSAPP_MINUTES = "work_whatsapp_minutes"
        private const val KEY_WORK_WHATSAPP_SESSION_START = "work_whatsapp_session_start"
        const val WORK_MODE_GRACE_MINUTES = 5 // colchón tras la hora de salida

        // V28: configuración de los modos (Paseo / Sin Redes / Enfoque)
        private const val KEY_PASEO_DOPAMINE_MINUTES = "paseo_dopamine_minutes"
        private const val KEY_PASEO_WHATSAPP_MINUTES = "paseo_whatsapp_minutes"
        private const val KEY_PASEO_MUSIC_BLOCKED = "paseo_music_blocked"

        // V29: Modo Paseo — estado de activación y control de uso continuo
        private const val KEY_PASEO_MODE_ENABLED = "paseo_mode_enabled"
        private const val KEY_PASEO_START_TIME = "paseo_start_time"
        private const val KEY_PASEO_USAGE_JSON = "paseo_usage_json"
        private const val KEY_PASEO_BLOCKED_JSON = "paseo_blocked_json"

        private const val KEY_NOSOCIAL_DEFAULT_MINUTES = "nosocial_default_minutes"
        private const val KEY_NOSOCIAL_GRAYSCALE = "nosocial_grayscale"
        private const val KEY_FOCUS_DEFAULT_MINUTES = "focus_default_minutes"

        // V30: Modo Sin Redes — estado activo, límite de uso y tregua propia
        private const val KEY_NOSOCIAL_MODE_ENABLED = "nosocial_mode_enabled"
        private const val KEY_NOSOCIAL_START_TIME = "nosocial_start_time"
        private const val KEY_NOSOCIAL_END_TIME = "nosocial_end_time"
        private const val KEY_NOSOCIAL_USAGE_JSON = "nosocial_usage_json"
        private const val KEY_NOSOCIAL_ALL_BLOCKED_UNTIL = "nosocial_all_blocked_until"
        private const val KEY_NOSOCIAL_TEMP_UNLOCK_UNTIL = "nosocial_temp_unlock_until"
        private const val KEY_NOSOCIAL_TREGUA_COOLDOWN_UNTIL = "nosocial_tregua_cooldown_until"
        // V30: toggle manual de la escala de grises PROPIA de la app
        private const val KEY_APP_GRAYSCALE_MANUAL = "app_grayscale_manual_enabled"

        // V31: AHORRO DE BATERÍA AUTOMÁTICO (se activa con cualquier modo activo)
        private const val KEY_BATTERY_SAVER_BY_APP = "battery_saver_enabled_by_app"
        private const val KEY_BATTERY_SAVER_PREV_STATE = "battery_saver_prev_state"
        private const val KEY_BATTERY_SAVER_NUDGE_SHOWN = "battery_saver_nudge_shown"
        // V31: BLOQUEO DE NOTIFICACIONES (NotificationListenerService)
        private const val KEY_NOTIF_BLOCKING_ENABLED = "notif_blocking_enabled"
        private const val KEY_NOTIF_BLOCKING_ALWAYS = "notif_blocking_always"
        private const val KEY_PASEO_NOTIF_ALLOWED = "paseo_notifications_allowed"

        // V30: constantes del Modo Sin Redes según la visión del usuario
        const val NOSOCIAL_APP_LIMIT_MINUTES = 30      // límite seguido por app permitida
        const val NOSOCIAL_ALL_BLOCKED_MINUTES = 5      // cooldown con TODAS las apps bloqueadas
        const val NOSOCIAL_TREGUA_MINUTES = 5           // tregua de 5 minutos
        const val NOSOCIAL_TREGUA_COOLDOWN_MINUTES = 10 // cooldown de la tregua
        
        // 1. Frases MUY LARGAS (150 a 200 palabras) para "Ya terminé mi actividad"
        val LONG_FINISH_EARLY_PHRASES = listOf(
            "Al tomar la firme decisión de dar por concluida mi actividad antes de tiempo, reconozco plenamente que la verdadera autodisciplina no consiste en buscar atajos hacia la comodidad, sino en honrar cada promesa que me hago a mí mismo. Reconozco que mi mente intentará justificar la pausa y buscará refugio en la gratificación inmediata de una pantalla, pero mi compromiso con el éxito real y con mi propio crecimiento personal se encuentra por encima de cualquier impulso efímero. Cada minuto dedicado a la concentración profunda y al trabajo bien ejecutado añade un valor incalculable a mi futuro, moldeando mi carácter y fortaleciendo mi capacidad de enfoque. Por lo tanto, declaro con total claridad y convicción que soy el único dueño de mi tiempo, que rechazo las distracciones innecesarias y que asumo la responsabilidad absoluta de mis acciones, eligiendo siempre la excelencia sobre la facilidad del momento presente.",
            
            "La prisa por dar por terminada una tarea importante suele ser el reflexionar sobre la impaciencia y el deseo inconsciente de regresar a las distracciones cotidianas que no aportan valor real a mi vida. Al escribir estas palabras de manera pausada y consciente, me obligo a desacelerar el ritmo de mi pensamiento y a cuestionar la verdadera urgencia de desbloquear mi dispositivo. Comprendo que la capacidad de sostener la atención en una sola tarea durante periodos prolongados es una de las virtudes más escasas y valiosas de nuestra época. Cada vez que me detengo a reflexionar antes de actuar impulsivamente, estoy entrenando a mi cerebro para ser más fuerte, más paciente y más resistente al aburrimiento. Elijo proteger mi paz mental y mi rendimiento profesional por encima de la falsa urgencia del entorno digital, manteniendo el control absoluto sobre mis hábitos diarios.",
            
            "Completar mi jornada con integridad requiere honestidad conmigo mismo para evaluar si realmente he entregado mi mejor esfuerzo o si simplemente busco una salida rápida para revisar notificaciones sin importancia. El verdadero progreso no se logra mediante esfuerzos a medias ni con interrupciones constantes, sino a través de la dedicación constante y el respeto riguroso por mi propio tiempo. Al asumir este reto de escritura prolongado, acepto que el camino hacia mis grandes metas requiere sacrificio, paciencia y una determinación inquebrantable que no se doblega ante la comodidad momentánea. Reafirmo mi decisión de mantener la mente despejada, libre de la necesidad constante de estímulos digitales, y me comprometo a continuar trabajando con foco, disciplina y claridad de propósito en todo lo que emprenda.",
            
            "La autodisciplina no es un castigo ni una limitación, sino la mayor demostración de respeto que puedo ofrecerme a mí mismo y a mis sueños a largo plazo. Cuando elijo dar por finalizada mi actividad de manera anticipada, debo estar completamente seguro de que he agotado todo mi potencial creativo y productivo en el periodo asignado. No permitiré que el aburrimiento temporal ni la ansiedad por revisar las redes sociales dicten el ritmo de mi jornada diaria. Cada palabra que escribo cuidadosamente en este texto refuerza mi voluntad, fortalece mi paciencia y me recuerda que los grandes logros requieren tiempo, constancia y una mente serena capaz de dominar las tentaciones pasajeras. Tomo las riendas de mi atención y continúo avanzando hacia mi propósito con absoluta firmeza.",
            
            "El tiempo que dedico al trabajo profundo y sin interrupciones es la inversión más valiosa que puedo hacer en mi desarrollo personal y profesional. Al intentar desbloquear mi dispositivo antes de que el temporizador llegue a su fin, me detengo a considerar si esta decisión responde a una necesidad genuina o al simple hábito automático de buscar distracción. Elijo la conciencia sobre la impulsividad y la paciencia sobre la inmediatez. Reconozco que la capacidad de enfocarme sin desviarme es la clave fundamental para alcanzar la excelencia en cualquier área de mi vida. Con cada línea escrita de forma correcta, reafirmo mi compromiso de mantener la mente centrada, proteger mi espacio de trabajo y actuar siempre con absoluta disciplina."
        )

        // 2. Colección de 100 FRASES MEDIANAS (20 a 50 palabras) para la Tregua Temporal
        val MEDIUM_TEMP_UNLOCK_PHRASES = listOf(
            "Acepto esta breve tregua de cinco minutos para atender una necesidad puntual, comprometiéndome a regresar de inmediato a mi actividad principal tan pronto como concluya este tiempo.",
            "Utilizaré estos cinco minutos de acceso temporal de manera consciente y responsable, evitando caer en la trampa del desplazamiento infinito y protegiendo mi enfoque de trabajo.",
            "Esta pausa temporal es un beneficio breve y controlled. Me mantendré atento al tiempo para retomar mis tareas sin perder el ritmo de productividad que he construido.",
            "Reconozco que el tiempo de esta tregua es limitado. Atenderé únicamente lo estrictamente necesario y cerraré la aplicación para continuar con mi sesión de enfoque profundo.",
            "Aprovecho este breve intervalo con propósito claro y sin desviarme hacia distracciones innecesarias, preparado para retornar de inmediato a mi objetivo principal.",
            "El control de mis impulsos me permite usar la tecnología como una herramienta útil, sin permitir que consuma mi tiempo de valor ni altere mi planificación diaria.",
            "Mantengo la mente centrada durante esta pausa corta, recordando que cada minuto invertido con disciplina me acerca a mis objetivos más importantes.",
            "Esta tregua es un descanso consciente y breve. No permitiré que se convierta en una distracción prolongada que arruine la jornada de trabajo que he iniciado.",
            "Respeto los límites que me he fijado para el uso de aplicaciones. Al finalizar estos cinco minutos, cerraré el dispositivo para retomar mi concentración sin excusas.",
            "La constancia en el trabajo diario construye mi futuro. Uso este breve intervalo para lo indispensable y regreso a mis tareas con renovada energía y enfoque.",
            "Cada elección responsable refuerza mi voluntad. Disfruto de este breve periodo de acceso y retomo mi actividad con determinación y claridad mental.",
            "La productividad requiere saber gestionar las pausas. Utilizo este tiempo de manera estratégica y vuelvo de inmediato a mis deberes principales.",
            "Acepto el desafío de mantener el control sobre mis hábitos. Estos cinco minutos sirven para lo necesario sin comprometer mi rendimiento.",
            "No me dejo llevar por la inercia del entretenimiento digital. Utilizo esta tregua con propósito firme y retomo mi trabajo con entusiasmo.",
            "El tiempo es mi recurso más valioso. Hago un uso responsable de este permiso temporal y regreso al enfoque con total serenidad.",
            "Valoro mi espacio de concentración y protejo mis metas de largo plazo. Esta breve pausa finalizará pronto y volveré a dar lo mejor de mí.",
            "La autodisciplina consiste en mantener mis compromisos personales incluso durante los descansos. Retomaré mi labor en cuanto venza el temporizador.",
            "Entiendo la importancia de la paciencia y el ritmo continuo. Aprovecho estos minutos con moderación y sigo adelante con mi plan de trabajo.",
            "Uso la tecnología con intención clara y sin perder el rumbo. Al terminar esta pausa, volveré a enfocarme en lo que realmente importa.",
            "La atención sostenida es el motor de mis logros. Utilizo este breve respiro de forma ordenada para volver con fuerza a mi tarea principal.",
            "Reconozco que la concentración profunda es un hábito que se cultiva día a día. Mantengo la disciplina durante esta tregua temporal.",
            "Cada pausa controlada me ayuda a mantener la claridad mental. Uso estos cinco minutos con moderación y retorno al trabajo productivo.",
            "El equilibrio entre el esfuerzo y el descanso requiere autocontrol. Aprovecho este tiempo breve para atender lo pendiente con responsabilidad.",
            "Mi voluntad es más fuerte que cualquier distracción digital. Finalizada esta tregua, continuaré mi camino con foco inquebrantable.",
            "Asumo el compromiso de cuidar mi atención. Utilizaré estos minutos con prudencia para regresar al trabajo con absoluta claridad de meta.",
            "Los grandes proyectos se logran con constancia y límites claros. Aprovecho esta pausa breve y regreso a mis responsabilidades habituales.",
            "La serenidad y la disciplina guían mis acciones. Uso esta tregua de cinco minutos y retomo mi tarea sin perder el impulso inicial.",
            "Cuidar mi tiempo me permite avanzar con confianza. Utilizo esta pausa con propósito y retorno de inmediato al esfuerzo focalizado.",
            "La fuerza de voluntad se ejercita en las decisiones cotidianas. Manejo esta tregua con prudencia para continuar con mi sesión de enfoque.",
            "La mente clara sabe cuándo pausar y cuándo retomar el esfuerzo. Concluiré esta tregua a tiempo para seguir construyendo mis metas.",
            "Elijo actuar con intención antes que ceder a la impulsividad. Utilizo este espacio corto y regreso a trabajar con entusiasmo.",
            "El trabajo bien hecho me brinda paz y satisfacción. Mantengo la moderación durante estos minutos y vuelvo a mi rutina de enfoque.",
            "Protejo mi mente de estímulos excesivos usando la tecnología con moderación. Al vencer los cinco minutos, cerraré el acceso.",
            "Cada minuto cuenta cuando persigo objetivos ambiciosos. Uso esta pausa de manera eficiente y retorno a mis labores habituales.",
            "La constancia genera resultados duraderos. Utilizo esta tregua con responsabilidad para continuar mi camino con firmeza.",
            "Acepto este intervalo breve como una pausa merecida pero estrictamente delimitada. Retomaré mi trabajo sin dilaciones innecesarias.",
            "La verdadera libertad surge del dominio propio. Manejo este tiempo de tregua con sensatez y retomo mi plan con total enfoque.",
            "Mantener la palabra dada a mí mismo fortalece mi carácter. Cumpliré el tiempo de esta pausa y regresaré a mis tareas principales.",
            "La concentración es un refugio de paz y claridad. Aprovecho este momento corto y me preparo para volver al trabajo profundo.",
            "Uso este tiempo para atender lo verdaderamente urgente y regreso de inmediato a mi actividad de enfoque con mente renovada.",
            "La autodisciplina transforma las intenciones en logros concretos. Manejo esta pausa con inteligencia para mantener mi ritmo diario.",
            "El hábito del trabajo fidedigno requiere cuidar cada detalle. Uso este descanso breve y continúo avanzando hacia mis propósitos.",
            "No permito que la impaciencia dirija mis decisiones. Utilizo esta tregua con orden y vuelvo a mi labor de forma dedicada.",
            "La claridad en mis prioridades me ayuda a gestionar los descansos. Aprovecho estos minutos y continúo con mi plan de acción.",
            "El respeto por mi tiempo es la base de mi crecimiento. Finalizaré esta tregua en el tiempo estipulado para volver al trabajo.",
            "La calma y la determinación son mis mejores aliadas. Utilizo esta pausa controlada y regreso con energía a mis metas de hoy.",
            "Manejo las distracciones con firmeza y serenidad. Esta tregua de cinco minutos es breve y me reincorporaré a mi tarea al instante.",
            "El éxito personal requiere constancia en las pequeñas acciones. Uso estos minutos con moderación y sigo adelante con mi jornada.",
            "Cultivo una relación saludable con el entorno digital. Aprovecho este respiro de forma consciente y retorno a mi trabajo focalizado.",
            "Mi compromiso con la excelencia se demuestra en cada momento. Utilizo esta tregua respetando los tiempos fijados inicialmente.",
            "La paz mental proviene de actuar en coherencia con mis valores. Uso este breve intervalo y vuelvo a enfocarme en mis objetivos.",
            "Afronto cada jornada con mentalidad positiva y disciplinada. Esta tregua corta sirve para lo justo antes de retomar el trabajo.",
            "El valor del trabajo profundo reside en la constancia sin interrupciones severas. Retomaré mi tarea tan pronto venza esta tregua.",
            "Mantengo el control de mis decisiones en todo momento. Aprovecho estos cinco minutos de tregua y continúo con mi progreso.",
            "La autodisciplina me brinda la libertad de elegir mi camino. Utilizo este respiro breve y vuelvo a mis labores con energía.",
            "La satisfacción del deber cumplido supera cualquier distracción efímera. Uso esta tregua con orden y retorno a mi actividad.",
            "Cuidar mi energía mental es clave para un alto rendimiento. Aprovecho este espacio corto para volver renovado a mis tareas.",
            "La firmeza en mis hábitos me asegura un futuro productivo. Manejo estos cinco minutos con sensatez y continuo con mi esfuerzo.",
            "Cada pausa debe sumar claridad, no confusión. Utilizo esta tregua con propósito claro y regreso de inmediato a mi tarea.",
            "El enfoque es el secreto de la eficiencia. Uso estos cinco minutos con moderación y retorno al trabajo con total entrega.",
            "La determinación me sostiene frente a las tentaciones digitales. Aprovecho esta breve tregua y retomo el camino de mis metas.",
            "Valoro la oportunidad de trabajar sin interrupciones. Esta pausa será breve y regresaré a mi tarea con renovado entusiasmo.",
            "El dominio de la atención es mi mayor fortaleza. Manejo este tiempo de tregua con prudencia para continuar con mi sesión.",
            "Cada acción alineada con mi propósito fortalece mi voluntad. Uso estos cinco minutos y regreso de inmediato a mi actividad.",
            "La serenidad en el esfuerzo me permite lograr grandes metas. Utilizo esta tregua con calma y retomo mi jornada con firmeza.",
            "La constancia supera cualquier obstáculo en el camino. Aprovecho esta pausa de cinco minutos y vuelvo a enfocarme con energía.",
            "La autodisciplina es el puente entre mis deseos y la realidad. Uso este tiempo con moderación y continúo con mi labor.",
            "Acepto el compromiso de finalizar lo que empiezo. Esta tregua de cinco minutos concluirá pronto y retornaré a mi trabajo.",
            "La mente enfocada encuentra soluciones en lugar de excusas. Manejo este espacio con sensatez y regreso a mi plan diario.",
            "El progreso continuo exige cuidar cada minuto del día. Utilizo esta tregua con orden y retomo mis actividades habituales.",
            "La fortaleza mental se construye con decisiones conscientes. Aprovecho esta pausa breve y continuo avanzando hacia mi objetivo.",
            "La serenidad y la acción coordinada son clave para el éxito. Uso estos minutos de tregua de forma inteligente y enfocado.",
            "No me desvío de las metas que me he propuesto alcanzar. Utilizo este tiempo temporal y regreso a mi labor con determinación.",
            "La disciplina en las pequeñas cosas abre puertas a grandes logros. Aprovecho esta pausa de cinco minutos y sigo trabajando.",
            "El tiempo bien gestionado trae tranquilidad y buenos frutos. Uso este respiro breve y vuelvo a mi rutina de concentración.",
            "La intención clara guía cada uno de mis pasos. Manejo esta tregua temporal con moderación y continuo mi camino de esfuerzo.",
            "La autodisciplina me permite disfrutar de descansos merecidos sin perder el rumbo. Concluida la tregua, volveré a enfocarme.",
            "Protejo mi atención de las interrupciones irrelevantes. Utilizo estos cinco minutos con propósito y regreso a mi trabajo.",
            "La constancia es la llave que abre todas las metas. Aprovecho esta pausa breve y retomo mi actividad con renovada energía.",
            "La paciencia y el trabajo duro rinden siempre buenos frutos. Uso esta tregua temporal con sensatez y continúo mi jornada.",
            "El equilibrio mental exige aprender a parar y continuar a tiempo. Manejo estos cinco minutos y regreso a mis responsabilidades.",
            "La mente enfocada no se distrae por estímulos pasajeros. Utilizo esta breve tregua con responsabilidad y vuelvo a trabajar.",
            "El respeto a mi plan de trabajo me asegura paz y satisfacción. Aprovecho este descanso corto y sigo enfocado en mi meta.",
            "Cada minuto de trabajo enfocado cuenta para mi crecimiento. Uso esta tregua temporal con orden y regreso a mi esfuerzo diario.",
            "La autodisciplina me permite ser el dueño de mi tiempo. Manejo estos cinco minutos con madurez y retorno a mis actividades.",
            "La claridad de objetivos me sostiene en todo momento. Utilizo este breve tiempo y vuelvo a enfocarme con absoluta serenidad.",
            "La fortaleza de voluntad se demuestra en el autocontrol diario. Aprovecho esta tregua y continúo avanzando hacia mis metas.",
            "La serenidad en el trabajo me brinda excelentes resultados. Uso estos minutos de tregua con equilibrio y retorno a mi labor.",
            "La constancia es mi mejor herramienta para triunfar. Manejo este intervalo corto con sensatez y regreso a mi actividad.",
            "Cuidar mi tiempo es cuidar mi propia vida. Utilizo esta tregua de cinco minutos con prudencia y continuo con mi esfuerzo.",
            "La autodisciplina me permite superar cualquier momento de fatiga. Aprovecho esta pausa breve y sigo firme en mis metas.",
            "La mente serena trabaja con mayor rapidez y precisión. Uso este descanso temporal de forma ordenada y regreso a mi tarea.",
            "El trabajo enfocado genera resultados que perduran. Manejo estos cinco minutos con madurez y continúo con mi plan diario.",
            "La determinación en mis hábitos construye mi éxito personal. Utilizo esta tregua con propósito y retorno de inmediato al foco.",
            "La paz interior es el fruto de actuar con disciplina. Aprovecho esta breve pausa y vuelvo a mis labores con entusiasmo.",
            "La constancia diaria es el camino seguro hacia la excelencia. Uso esta tregua temporal con equilibrio y continúo mi marcha.",
            "El control de mis impulsos me otorga verdadera libertad. Manejo estos cinco minutos con moderación y regreso a mi trabajo.",
            "La autodisciplina transforma los sueños en metas alcanzadas. Utilizo este descanso breve y continuo firme con mi enfoque.",
            "La serenidad y el trabajo constante vencen la procrastinación. Aprovecho este espacio corto y retorno a mis actividades diarias.",
            "Mi compromiso con el crecimiento personal permanece inquebrantable. Uso esta tregua de cinco minutos con total responsabilidad y vuelvo al trabajo."
        )

        // 3. Frases CORTAS (1 oración corta, 8 a 14 palabras) para cuando TERMINA el temporizador
        val SHORT_FINISHED_PHRASES = listOf(
            "He cumplido mi meta con éxito y ahora disfruto de mi tiempo con tranquilidad.",
            "Mi tiempo de enfoque ha terminado y he completado mi objetivo satisfactoriamente.",
            "He demostrado autodisciplina y concluyo mi sesión de trabajo con total serenidad.",
            "Misión cumplida: mi esfuerzo de hoy ha dado sus frutos con éxito.",
            "Felicidades a mí mismo por mantener la concentración y superar el desafío."
        )
    }

    var isLocked: Boolean
        get() {
            val locked = prefs.getBoolean(KEY_IS_LOCKED, false)
            if (!locked) return false
            val end = prefs.getLong(KEY_LOCK_END_TIME, 0L)
            if (end > 0 && System.currentTimeMillis() >= end) {
                prefs.edit().putBoolean(KEY_IS_LOCKED, false).apply()
                return false
            }
            return true
        }
        set(value) = prefs.edit().putBoolean(KEY_IS_LOCKED, value).apply()

    var lockEndTime: Long
        get() = prefs.getLong(KEY_LOCK_END_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCK_END_TIME, value).apply()

    var longPhrase: String
        get() = prefs.getString(KEY_LONG_PHRASE, LONG_FINISH_EARLY_PHRASES[0]) ?: LONG_FINISH_EARLY_PHRASES[0]
        set(value) = prefs.edit().putString(KEY_LONG_PHRASE, value).apply()

    var mediumPhrase: String
        get() = prefs.getString(KEY_MEDIUM_PHRASE, MEDIUM_TEMP_UNLOCK_PHRASES[0]) ?: MEDIUM_TEMP_UNLOCK_PHRASES[0]
        set(value) = prefs.edit().putString(KEY_MEDIUM_PHRASE, value).apply()

    var shortPhrase: String
        get() = prefs.getString(KEY_SHORT_PHRASE, SHORT_FINISHED_PHRASES[0]) ?: SHORT_FINISHED_PHRASES[0]
        set(value) = prefs.edit().putString(KEY_SHORT_PHRASE, value).apply()

    val timeRemaining: Long
        get() {
            val remaining = lockEndTime - System.currentTimeMillis()
            return if (remaining < 0) 0 else remaining
        }

    var tempUnlockEndTime: Long
        get() = prefs.getLong(KEY_TEMP_UNLOCK_END_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_TEMP_UNLOCK_END_TIME, value).apply()

    var cooldownEndTime: Long
        get() = prefs.getLong(KEY_COOLDOWN_END_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_COOLDOWN_END_TIME, value).apply()

    // V24 (Propuesta 5): tiempo de fin de sesión remota detectada (PC -> teléfono)
    private var remoteSessionEndTime: Long = 0L

    /** V24 (Propuesta 5): true si hay una sesión de enfoque activa en CUALQUIER dispositivo.
     * Protege ajustes: mientras haya sesión activa (local o remota), cambiar ajustes exige biometría/PIN. */
    val isFocusSessionActive: Boolean
        get() = isLocked || isTempUnlocked || System.currentTimeMillis() < remoteSessionEndTime

    fun updateRemoteSessionEndTime(expiresAt: Long) {
        if (expiresAt > 0) {
            remoteSessionEndTime = maxOf(remoteSessionEndTime, expiresAt)
        }
    }

    var allowedPackages: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED_PACKAGES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, value).apply()

    // V20: Modo oscuro (claro por defecto)
    // V24: el modo oscuro es INDEPENDIENTE por dispositivo (Bug 2 de la auditoría).
    // Ya NO se publica en /config para no forzar el tema de la extensión de Chrome.
    var darkModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
        }

    // V26: clave del preset de acento elegido en Ajustes -> Colores.
    // Se aplica a TODO el sistema (launcher, notas, organizador, modo enfoque).
    var accentPresetKey: String
        get() = prefs.getString(KEY_ACCENT_PRESET, "oliva") ?: "oliva"
        set(value) {
            prefs.edit().putString(KEY_ACCENT_PRESET, value).apply()
        }

    // V20: Bloqueo cruzado entre dispositivos (activo por defecto)
    var crossDeviceLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_CROSS_DEVICE_LOCK, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CROSS_DEVICE_LOCK, value).apply()
            // Publicar la preferencia en la nube para que la extensión la respete
            try {
                val ref = userRef()?.child("config")?.child("cross_device_lock_enabled") ?: return
                ref.setValue(value)
            } catch (e: Exception) {
                android.util.Log.e("ZEN_SYNC", "Error publicando config cross_device: ${e.message}")
            }
            // V24 (Bug 1 de la auditoría): si se activa el bloqueo cruzado con una
            // sesión de enfoque ya en curso, publicar el estado actual para que la
            // extensión de Chrome lo aplique de inmediato (source_device: android).
            if (value && isLocked) {
                pushLockStateToFirebase(true, lockEndTime)
            }
        }

    // V32: el pomodoro automático se eliminó — los descansos se configuran en el
    // plan de actividades del Modo Enfoque (FocusSegment).

    // ================================================================================
    // V28: MODO ESCUELA / TRABAJO (activación + horario de Lunes a Viernes)
    // El bloqueo de apps distractoras se activa automáticamente dentro del horario.
    // ================================================================================

    /** Horario de un día (day: 1=Lunes ... 5=Viernes). Tiempos en minutos del día (0-1439). */
    data class DaySchedule(val day: Int, val startMinute: Int, val endMinute: Int)

    private val workScheduleGson = Gson()

    var workModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_WORK_MODE_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_WORK_MODE_ENABLED, value).apply()
        }

    var workSchedule: List<DaySchedule>
        get() {
            val json = prefs.getString(KEY_WORK_SCHEDULE, null) ?: return emptyList()
            return try {
                val type = object : TypeToken<List<DaySchedule>>() {}.type
                workScheduleGson.fromJson<List<DaySchedule>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            prefs.edit().putString(KEY_WORK_SCHEDULE, workScheduleGson.toJson(value)).apply()
        }

    /** Establece (o elimina) el horario de un día. Si start/end no son válidos, lo borra. */
    fun setWorkSchedule(day: Int, startMinute: Int, endMinute: Int) {
        val updated = workSchedule.filterNot { it.day == day }.toMutableList()
        if (day in 1..5 && startMinute in 0..1439 && endMinute in 0..1439 && endMinute > startMinute) {
            updated.add(DaySchedule(day, startMinute, endMinute))
        }
        workSchedule = updated.sortedBy { it.day }
    }

    /** True si el Modo Escuela/Trabajo está activado Y estamos dentro de un horario (con colchón de salida). */
    fun isWorkModeActive(now: Long = System.currentTimeMillis()): Boolean {
        if (!workModeEnabled) return false
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        val isoDay = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            java.util.Calendar.SATURDAY -> 6
            else -> 7
        }
        val sched = workSchedule.firstOrNull { it.day == isoDay } ?: return false
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return nowMin >= sched.startMinute && nowMin < sched.endMinute + WORK_MODE_GRACE_MINUTES
    }

    /** Milisegundo absoluto en que termina el bloque de Escuela/Trabajo activo
     * (hora de salida + colchón de 5 min). 0 si no hay bloque activo ahora. */
    fun workModeEndMillis(now: Long = System.currentTimeMillis()): Long {
        if (!workModeEnabled) return 0L
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        val isoDay = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            java.util.Calendar.SATURDAY -> 6
            else -> 7
        }
        val sched = workSchedule.firstOrNull { it.day == isoDay } ?: return 0L
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val effectiveEnd = sched.endMinute + WORK_MODE_GRACE_MINUTES
        if (nowMin < sched.startMinute || nowMin >= effectiveEnd) return 0L
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis + effectiveEnd * 60_000L
    }

    // V28: en Modo Escuela/Trabajo, WhatsApp queda exento pero con límite de
    // minutos seguidos (por defecto 15). Se reinicia al salir del modo.
    var workWhatsAppMinutes: Int
        get() = prefs.getInt(KEY_WORK_WHATSAPP_MINUTES, 15).coerceIn(1, 60)
        set(value) = prefs.edit().putInt(KEY_WORK_WHATSAPP_MINUTES, value.coerceIn(1, 60)).apply()

    /** Milisegundos restantes de WhatsApp dentro del Modo Escuela activo. */
    fun workWhatsAppRemainingMs(): Long {
        if (!isWorkModeActive()) return 0L
        val start = prefs.getLong(KEY_WORK_WHATSAPP_SESSION_START, 0L)
        if (start == 0L) return workWhatsAppMinutes * 60_000L
        return (workWhatsAppMinutes * 60_000L - (System.currentTimeMillis() - start)).coerceAtLeast(0L)
    }

    /** True si en Modo Escuela el usuario aún puede usar WhatsApp (no agotó su límite). */
    fun isWorkWhatsAppAvailable(): Boolean = workWhatsAppRemainingMs() > 0

    /** Inicia (o conserva) la sesión de WhatsApp cuando se abre durante el modo Escuela. */
    fun startWorkWhatsAppSessionIfNeeded() {
        if (!isWorkModeActive()) return
        val start = prefs.getLong(KEY_WORK_WHATSAPP_SESSION_START, 0L)
        val now = System.currentTimeMillis()
        if (start == 0L || now - start > workWhatsAppMinutes * 60_000L) {
            prefs.edit().putLong(KEY_WORK_WHATSAPP_SESSION_START, now).apply()
        }
    }

    /** Borra la sesión de WhatsApp (se llama al salir del modo Escuela o al cerrar la app). */
    fun clearWorkWhatsAppSession() {
        prefs.edit().remove(KEY_WORK_WHATSAPP_SESSION_START).apply()
    }

    // ================================================================================
    // V28: CONFIGURACIÓN DE LOS MODOS (Paseo / Sin Redes / Enfoque)
    // ================================================================================

    // Modo Paseo: límites configurables (apps dopamina, WhatsApp, música)
    // V29: por defecto 15 min RRSS/juegos y 20 min WhatsApp según la visión del usuario.
    var paseoDopamineMinutes: Int
        get() = prefs.getInt(KEY_PASEO_DOPAMINE_MINUTES, 15).coerceIn(1, 120)
        set(value) = prefs.edit().putInt(KEY_PASEO_DOPAMINE_MINUTES, value.coerceIn(1, 120)).apply()

    var paseoWhatsAppMinutes: Int
        get() = prefs.getInt(KEY_PASEO_WHATSAPP_MINUTES, 20).coerceIn(1, 120)
        set(value) = prefs.edit().putInt(KEY_PASEO_WHATSAPP_MINUTES, value.coerceIn(1, 120)).apply()

    var paseoMusicBlocked: Boolean
        get() = prefs.getBoolean(KEY_PASEO_MUSIC_BLOCKED, true)
        set(value) = prefs.edit().putBoolean(KEY_PASEO_MUSIC_BLOCKED, value).apply()

    // ================================================================================
    // V29: MODO PASEO — ACTIVACIÓN VOLUNTARIA SIN TEMPORIZADOR
    // ================================================================================

    /** True si el Modo Paseo está activo (se activa/desactiva a voluntad, sin temporizador). */
    var paseoModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PASEO_MODE_ENABLED, false)
        set(value) {
            android.util.Log.d(
                "ZEN_PASEO",
                "paseoModeEnabled -> $value (prev=${prefs.getBoolean(KEY_PASEO_MODE_ENABLED, false)})",
                Throwable("ZEN_PASEO trace")
            )
            prefs.edit().putBoolean(KEY_PASEO_MODE_ENABLED, value).apply()
            if (value) {
                prefs.edit().putLong(KEY_PASEO_START_TIME, System.currentTimeMillis()).apply()
            }
            resetPaseoState()
        }

    val paseoStartTime: Long
        get() = prefs.getLong(KEY_PASEO_START_TIME, 0L)

    /** Minutos transcurridos desde que se activó el Modo Paseo (0 si inactivo). */
    val paseoElapsedMinutes: Long
        get() = if (!paseoModeEnabled || paseoStartTime <= 0L) 0L
                else (System.currentTimeMillis() - paseoStartTime) / 60_000L

    /** Tiempo acumulado de uso de una app durante el Modo Paseo (solo mientras está en primer plano). */
    fun paseoUsageMs(pkg: String): Long {
        val json = prefs.getString(KEY_PASEO_USAGE_JSON, null) ?: return 0L
        return try {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            val map = pomodoroGson.fromJson<Map<String, Long>>(json, type) ?: emptyMap()
            map[pkg] ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /** Límite de uso seguido para una app dentro del Modo Paseo. */
    fun paseoLimitMs(pkg: String): Long =
        if (LauncherUtils.isWhatsApp(pkg)) paseoWhatsAppMinutes * 60_000L
        else paseoDopamineMinutes * 60_000L

    /** Tiempo restante permitido de una app dentro del Modo Paseo. */
    fun paseoRemainingMs(pkg: String): Long =
        (paseoLimitMs(pkg) - paseoUsageMs(pkg)).coerceAtLeast(0L)

    /** Acumula tiempo de uso de una app del Modo Paseo y persiste el total. */
    fun addPaseoUsage(pkg: String, deltaMs: Long) {
        if (deltaMs <= 0L) return
        try {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            val map = (prefs.getString(KEY_PASEO_USAGE_JSON, null)?.let {
                pomodoroGson.fromJson<Map<String, Long>>(it, type) ?: emptyMap()
            } ?: emptyMap()).toMutableMap()
            map[pkg] = (map[pkg] ?: 0L) + deltaMs
            prefs.edit().putString(KEY_PASEO_USAGE_JSON, pomodoroGson.toJson(map)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** True si una app ya alcanzó su límite y quedó bloqueada durante el Modo Paseo. */
    fun isPaseoBlocked(pkg: String): Boolean {
        val json = prefs.getString(KEY_PASEO_BLOCKED_JSON, null) ?: return false
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val list = pomodoroGson.fromJson<List<String>>(json, type) ?: emptyList()
            pkg in list
        } catch (e: Exception) {
            false
        }
    }

    /** Bloquea una app durante el Modo Paseo (solo esa app; el resto sigue disponible). */
    fun blockPaseoApp(pkg: String) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            val list = (prefs.getString(KEY_PASEO_BLOCKED_JSON, null)?.let {
                pomodoroGson.fromJson<List<String>>(it, type) ?: emptyList()
            } ?: emptyList()).toMutableList()
            if (pkg !in list) list.add(pkg)
            prefs.edit().putString(KEY_PASEO_BLOCKED_JSON, pomodoroGson.toJson(list)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Apps bloqueadas durante el Modo Paseo. */
    fun paseoBlockedPackages(): List<String> {
        val json = prefs.getString(KEY_PASEO_BLOCKED_JSON, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            pomodoroGson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun resetPaseoState() {
        prefs.edit()
            .remove(KEY_PASEO_USAGE_JSON)
            .remove(KEY_PASEO_BLOCKED_JSON)
            .apply()
    }

    // Modo Sin Redes: duración por defecto (15-240 min) + escala de grises
    var noSocialDefaultMinutes: Int
        get() = prefs.getInt(KEY_NOSOCIAL_DEFAULT_MINUTES, 60).coerceIn(15, 240)
        set(value) = prefs.edit().putInt(KEY_NOSOCIAL_DEFAULT_MINUTES, value.coerceIn(15, 240)).apply()

    var noSocialGrayScale: Boolean
        get() = prefs.getBoolean(KEY_NOSOCIAL_GRAYSCALE, true)
        set(value) = prefs.edit().putBoolean(KEY_NOSOCIAL_GRAYSCALE, value).apply()

    // ================================================================================
    // V30: MODO SIN REDES — ESTADO ACTIVO Y LÍMITES
    // Bloquea RRSS/juegos/navegadores/dopamina (excepto WhatsApp). Las apps permitidas
    // tienen un límite de 30 min seguidos; al superarlo TODAS se bloquean durante un
    // cooldown de 5 min (sin tregua). Tiene tregua propia de 5 min (cooldown 10 min).
    // No se puede desactivar hasta que acabe el tiempo programado.
    // ================================================================================

    var noSocialModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOSOCIAL_MODE_ENABLED, false)
        private set(value) = prefs.edit().putBoolean(KEY_NOSOCIAL_MODE_ENABLED, value).apply()

    val noSocialStartTime: Long
        get() = prefs.getLong(KEY_NOSOCIAL_START_TIME, 0L)

    val noSocialEndTime: Long
        get() = prefs.getLong(KEY_NOSOCIAL_END_TIME, 0L)

    /** True si el Modo Sin Redes está activo (enabled + dentro del tiempo programado). */
    fun isNoSocialModeActive(now: Long = System.currentTimeMillis()): Boolean =
        noSocialModeEnabled && now < noSocialEndTime

    /** Milisegundos restantes del Modo Sin Redes (0 si no está activo). */
    fun noSocialRemainingMs(): Long {
        if (!isNoSocialModeActive()) return 0L
        return (noSocialEndTime - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** Inicia el Modo Sin Redes por `minutes`. No se puede desactivar hasta que termine. */
    fun startNoSocialMode(minutes: Int) {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(KEY_NOSOCIAL_MODE_ENABLED, true)
            .putLong(KEY_NOSOCIAL_START_TIME, now)
            .putLong(KEY_NOSOCIAL_END_TIME, now + minutes.coerceAtLeast(1) * 60_000L)
            .remove(KEY_NOSOCIAL_USAGE_JSON)
            .remove(KEY_NOSOCIAL_ALL_BLOCKED_UNTIL)
            .remove(KEY_NOSOCIAL_TEMP_UNLOCK_UNTIL)
            .remove(KEY_NOSOCIAL_TREGUA_COOLDOWN_UNTIL)
            .apply()
        android.util.Log.d("ZEN_NOSOCIAL", "Modo Sin Redes iniciado por $minutes min")
    }

    /** Detiene el Modo Sin Redes (se llama automáticamente al expirar el tiempo). */
    fun stopNoSocialMode() {
        prefs.edit()
            .putBoolean(KEY_NOSOCIAL_MODE_ENABLED, false)
            .remove(KEY_NOSOCIAL_START_TIME)
            .remove(KEY_NOSOCIAL_END_TIME)
            .remove(KEY_NOSOCIAL_USAGE_JSON)
            .remove(KEY_NOSOCIAL_ALL_BLOCKED_UNTIL)
            .remove(KEY_NOSOCIAL_TEMP_UNLOCK_UNTIL)
            .remove(KEY_NOSOCIAL_TREGUA_COOLDOWN_UNTIL)
            .apply()
        android.util.Log.d("ZEN_NOSOCIAL", "Modo Sin Redes detenido (tiempo agotado)")
    }

    /** True si estamos en el cooldown de bloqueo total (TODAS las apps bloqueadas, sin tregua). */
    fun isNoSocialAllBlocked(): Boolean =
        System.currentTimeMillis() < prefs.getLong(KEY_NOSOCIAL_ALL_BLOCKED_UNTIL, 0L)

    /** Tiempo restante del cooldown de bloqueo total (0 si no está activo). */
    fun noSocialAllBlockedRemainingMs(): Long {
        if (!isNoSocialAllBlocked()) return 0L
        return (prefs.getLong(KEY_NOSOCIAL_ALL_BLOCKED_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** Activa el cooldown de 5 min con TODAS las apps bloqueadas (sin tregua disponible). */
    fun activateNoSocialAllBlocked() {
        prefs.edit()
            .putLong(KEY_NOSOCIAL_ALL_BLOCKED_UNTIL, System.currentTimeMillis() + NOSOCIAL_ALL_BLOCKED_MINUTES * 60_000L)
            .remove(KEY_NOSOCIAL_TEMP_UNLOCK_UNTIL)
            .remove(KEY_NOSOCIAL_TREGUA_COOLDOWN_UNTIL)
            .apply()
        // Tras el cooldown, WhatsApp vuelve a tener 30 min completos de uso seguido.
        resetNoSocialUsage()
        android.util.Log.d("ZEN_NOSOCIAL", "Cooldown de bloqueo total activado (${NOSOCIAL_ALL_BLOCKED_MINUTES} min)")
    }

    // --- Tregua del Modo Sin Redes (independiente de la del Modo Enfoque) ---

    /** True si la tregua del Modo Sin Redes está activa (permite abrir lo bloqueado). */
    fun isNoSocialTempUnlocked(): Boolean =
        System.currentTimeMillis() < prefs.getLong(KEY_NOSOCIAL_TEMP_UNLOCK_UNTIL, 0L)

    /** True si la tregua del Modo Sin Redes está en cooldown (no disponible aún). */
    fun isNoSocialTreguaCooldown(): Boolean =
        System.currentTimeMillis() < prefs.getLong(KEY_NOSOCIAL_TREGUA_COOLDOWN_UNTIL, 0L)

    /** Tiempo restante de la tregua del Modo Sin Redes. */
    fun noSocialTreguaRemainingMs(): Long {
        val end = prefs.getLong(KEY_NOSOCIAL_TEMP_UNLOCK_UNTIL, 0L)
        if (end <= 0L) return 0L
        return (end - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** Concede 5 min de tregua (solo si el modo está activo, no en bloqueo total y sin cooldown de tregua). */
    fun startNoSocialTregua() {
        if (!isNoSocialModeActive()) return
        if (isNoSocialAllBlocked()) return
        if (isNoSocialTreguaCooldown()) return
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_NOSOCIAL_TEMP_UNLOCK_UNTIL, now + NOSOCIAL_TREGUA_MINUTES * 60_000L)
            .putLong(KEY_NOSOCIAL_TREGUA_COOLDOWN_UNTIL, now + NOSOCIAL_TREGUA_COOLDOWN_MINUTES * 60_000L)
            .apply()
        // La tregua reinicia el límite seguido de WhatsApp.
        resetNoSocialUsage()
        android.util.Log.d("ZEN_NOSOCIAL", "Tregua de ${NOSOCIAL_TREGUA_MINUTES} min concedida")
    }

    // --- Uso continuo de las apps permitidas (límite de 30 min seguidos) ---

    /** Tiempo acumulado de uso seguido de una app permitida durante el Modo Sin Redes. */
    fun noSocialUsageMs(pkg: String): Long {
        val json = prefs.getString(KEY_NOSOCIAL_USAGE_JSON, null) ?: return 0L
        return try {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            val map = pomodoroGson.fromJson<Map<String, Long>>(json, type) ?: emptyMap()
            map[pkg] ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /** Acumula uso seguido de una app permitida durante el Modo Sin Redes. */
    fun addNoSocialUsage(pkg: String, deltaMs: Long) {
        if (deltaMs <= 0L) return
        try {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            val map = (prefs.getString(KEY_NOSOCIAL_USAGE_JSON, null)?.let {
                pomodoroGson.fromJson<Map<String, Long>>(it, type) ?: emptyMap()
            } ?: emptyMap()).toMutableMap()
            map[pkg] = (map[pkg] ?: 0L) + deltaMs
            prefs.edit().putString(KEY_NOSOCIAL_USAGE_JSON, pomodoroGson.toJson(map)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Reinicia el contador de uso seguido (al cambiar de app, en tregua o tras el cooldown). */
    fun resetNoSocialUsage() {
        prefs.edit().remove(KEY_NOSOCIAL_USAGE_JSON).apply()
    }

    /** Apps bloqueadas en el Modo Sin Redes: RRSS/juegos/navegadores/dopamina, EXCEPTO WhatsApp. */
    fun isNoSocialAppBlocked(pkg: String): Boolean =
        LauncherUtils.isDopamineWalkPackage(pkg) && !LauncherUtils.isWhatsApp(pkg)

    /** Estado de bloqueo ACTUAL de un paquete en el Modo Sin Redes (para la UI del cajón). */
    fun isNoSocialPackageBlockedNow(pkg: String): Boolean {
        if (!isNoSocialModeActive()) return false
        if (isNoSocialAllBlocked()) return true
        if (isNoSocialTempUnlocked()) return false
        return isNoSocialAppBlocked(pkg)
    }

    // ================================================================================
    // V30: ESCALA DE GRISES DE APP (launcher) — Sin Redes / Paseo / Escuela-Trabajo
    // Solo afecta al menú principal y al cajón de aplicaciones de AntiProcrastinación.
    // ================================================================================

    /** True si debe activarse la escala de grises PROPIA de la app (dentro del launcher
     *  y la pantalla de bloqueo del Modo Enfoque). No afecta a otras apps. */
    fun isAppGrayscaleActive(): Boolean {
        if (isLocked) return true                 // Modo Enfoque (pantalla de bloqueo)
        if (isWorkModeActive()) return true       // horario Escuela/Trabajo
        if (paseoModeEnabled) return true         // Modo Paseo
        if (appGrayscaleManualEnabled) return true // toggle manual del home
        return noSocialGrayScale && isNoSocialModeActive()
    }

    // V30: toggle MANUAL de la escala de grises PROPIA de la app (botón del home).
    // Desatura solo la interfaz de AntiProcrastinación; nunca afecta a otras apps.
    var appGrayscaleManualEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_GRAYSCALE_MANUAL, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_GRAYSCALE_MANUAL, value).apply()

    // ================================================================================
    // V31: AHORRO DE BATERÍA AUTOMÁTICO + BLOQUEO DE NOTIFICACIONES
    // Visión: al activarse CUALQUIER modo -> ahorro de batería + silencio de
    // notificaciones de apps que distraen (solo llamadas y mensajes de texto).
    // ================================================================================

    /** True si hay algún modo activo ahora (Enfoque, Sin Redes, Escuela/Trabajo o Paseo). */
    fun isAnyModeActive(): Boolean =
        isLocked || isNoSocialModeActive() || isWorkModeActive() || paseoModeEnabled

    /** ¿Tiene la app permiso "Modificar ajustes del sistema" (WRITE_SETTINGS)? */
    fun canWriteSystemSettings(): Boolean =
        Settings.System.canWrite(appContext)

    /** Intent para abrir la pantalla que concede WRITE_SETTINGS a la app. */
    fun openWriteSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${appContext.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // V31: control del reintento (evita spam de logs si el dispositivo no permite
    // escribir "low_power" sin WRITE_SECURE_SETTINGS, como HiOS/TECNO).
    private var lastBatteryShouldBeOn: Boolean? = null
    private var lastBatteryAttemptMs = 0L

    /**
     * Sincroniza el modo ahorro de batería del sistema con el estado de los modos.
     * Se activa cuando entra cualquier modo; se apaga solo cuando NO hay ningún modo
     * y el usuario no lo había activado manualmente antes.
     *
     * En dispositivos que exigen WRITE_SECURE_SETTINGS (signature) el cambio silencioso
     * es imposible; entonces se muestra UN aviso que abre el ajuste de ahorro de batería
     * (una vez por sesión de modo) y se reintenta como mucho cada 10 minutos.
     */
    fun syncBatterySaver() {
        try {
            val shouldBeOn = isAnyModeActive()
            val enabledByApp = prefs.getBoolean(KEY_BATTERY_SAVER_BY_APP, false)
            val stateChanged = lastBatteryShouldBeOn != shouldBeOn
            lastBatteryShouldBeOn = shouldBeOn

            if (!shouldBeOn) {
                // Sin modos: permitir que el próximo modo vuelva a avisar de la batería.
                prefs.edit().remove(KEY_BATTERY_SAVER_NUDGE_SHOWN).apply()
            }
            if (shouldBeOn == enabledByApp) return       // estado ya sincronizado

            val now = System.currentTimeMillis()
            if (!stateChanged && now - lastBatteryAttemptMs < 10 * 60_000L) return
            lastBatteryAttemptMs = now

            if (!canWriteSystemSettings()) {
                // Sin WRITE_SETTINGS: no-op silencioso (se reintenta al concederlo).
                return
            }

            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (shouldBeOn) {
                // Recordar el estado previo para restaurarlo al salir del modo.
                prefs.edit().putBoolean(KEY_BATTERY_SAVER_PREV_STATE, powerManager.isPowerSaveMode).apply()
                Settings.Secure.putInt(appContext.contentResolver, "low_power", 1)
                Settings.Secure.putInt(appContext.contentResolver, "low_power_sticky", 1)
                prefs.edit().putBoolean(KEY_BATTERY_SAVER_BY_APP, true).apply()
                android.util.Log.d("ZEN_BATTERY", "Ahorro de batería ACTIVADO por un modo activo")
            } else {
                // Solo apagar si nosotros lo encendimos y antes estaba apagado
                // (no pisar la decisión manual del usuario).
                if (!prefs.getBoolean(KEY_BATTERY_SAVER_PREV_STATE, false)) {
                    Settings.Secure.putInt(appContext.contentResolver, "low_power", 0)
                    Settings.Secure.putInt(appContext.contentResolver, "low_power_sticky", 0)
                }
                prefs.edit().putBoolean(KEY_BATTERY_SAVER_BY_APP, false).apply()
                android.util.Log.d("ZEN_BATTERY", "Ahorro de batería DESACTIVADO: sin modos activos")
            }
        } catch (e: Exception) {
            // El dispositivo exige WRITE_SECURE_SETTINGS (p.ej. TECNO/HiOS): avisamos
            // una vez para que el usuario lo active manualmente y no repetimos hasta
            // que cambie el estado o pasen 10 minutos.
            android.util.Log.w("ZEN_BATTERY", "No se puede activar el ahorro automático (se reintentará): ${e.message}")
            if (isAnyModeActive()) showBatterySaverNudge()
        }
    }

    /**
     * V31: notificación única (una vez por sesión de modo) que abre el ajuste de
     * ahorro de batería del sistema, cuando el cambio automático no es posible.
     */
    private fun showBatterySaverNudge() {
        if (prefs.getBoolean(KEY_BATTERY_SAVER_NUDGE_SHOWN, false)) return
        prefs.edit().putBoolean(KEY_BATTERY_SAVER_NUDGE_SHOWN, true).apply()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    "zen_battery_nudge",
                    "Ahorro de batería",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Aviso para activar el ahorro de batería al iniciar un modo" }
                manager.createNotificationChannel(channel)
            }
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                appContext,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(appContext, "zen_battery_nudge")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Activa el ahorro de batería")
                .setContentText("Tienes un modo activo. Activa el ahorro de batería para gastar menos energía (toca para abrir).")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Tienes un modo activo. Este dispositivo no permite activar el ahorro de batería automáticamente; tócalo para abrirlo y actívalo tú."))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(1002, notification)
        } catch (e: Exception) {
            // Aviso no crítico
        }
    }

    /** V31: toggle global del bloqueo de notificaciones (por defecto ACTIVADO). */
    var notifBlockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_BLOCKING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF_BLOCKING_ENABLED, value).apply()

    /**
     * V31: alcance del bloqueo de notificaciones.
     * - false (por defecto) = "Solo con modos": bloquea únicamente cuando hay un modo activo.
     * - true = "Siempre": bloquea también sin ningún modo activo.
     */
    var notifBlockingAlways: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_BLOCKING_ALWAYS, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF_BLOCKING_ALWAYS, value).apply()

    /**
     * V31: el modo caminata/salida/cita bloquea notificaciones por defecto; si el usuario
     * activa este ajuste, la caminata deja pasar notificaciones.
     */
    var paseoNotificationsAllowed: Boolean
        get() = prefs.getBoolean(KEY_PASEO_NOTIF_ALLOWED, false)
        set(value) = prefs.edit().putBoolean(KEY_PASEO_NOTIF_ALLOWED, value).apply()

    /**
     * V31: decide si la notificación de un paquete debe ocultarse.
     * Llamadas y mensajes de texto SIEMPRE se dejan visibles, igual que las
     * notificaciones propias de AntiProcrastinación (temporizador y 2FA).
     *
     * - Enfoque, Sin Redes y Escuela/Trabajo: todo se bloquea apenas se activan.
     * - Caminata/salida/cita: bloquea por defecto; se permite si el ajuste
     *   "notificaciones en caminata" está activo.
     * - Sin ningún modo: solo se bloquea con la opción "Siempre".
     */
    fun shouldBlockNotification(pkg: String): Boolean {
        if (!notifBlockingEnabled) return false
        if (pkg == appContext.packageName) return false          // temporizador y 2FA propios
        if (LauncherUtils.getDefaultDialerPackage(appContext) == pkg) return false  // llamadas
        if (LauncherUtils.getDefaultSmsPackage(appContext) == pkg) return false     // SMS

        // Modos restrictivos: todo bloqueado.
        if (isLocked || isNoSocialModeActive() || isWorkModeActive()) return true

        // Modo caminata/salida/cita: bloquea salvo que el ajuste lo permita.
        if (paseoModeEnabled) return !paseoNotificationsAllowed

        // Sin ningún modo activo: solo con la opción "Siempre".
        return notifBlockingAlways
    }

    // Modo Enfoque: duración por defecto (5-480 min)
    var focusDefaultMinutes: Int
        get() = prefs.getInt(KEY_FOCUS_DEFAULT_MINUTES, 25).coerceIn(5, 480)
        set(value) = prefs.edit().putInt(KEY_FOCUS_DEFAULT_MINUTES, value.coerceIn(5, 480)).apply()

    // ================================================================================
    // V24: PROGRAMACIÓN POMODORO (fases de trabajo/descanso del enfoque en curso)
    // Estructura: N descansos -> N+1 bloques de trabajo iguales. Empieza y termina en trabajo.
    // ================================================================================
    private val pomodoroGson = Gson()

    var pomodoroPhases: List<PomodoroPhase>
        get() {
            val json = prefs.getString(KEY_POMODORO_PHASES, null) ?: return emptyList()
            return try {
                val type = object : TypeToken<List<PomodoroPhase>>() {}.type
                pomodoroGson.fromJson<List<PomodoroPhase>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            prefs.edit().putString(KEY_POMODORO_PHASES, pomodoroGson.toJson(value)).apply()
        }

    /** Fase actual del Pomodoro en `now` (null si no hay Pomodoro activo). */
    fun currentPomodoroPhase(now: Long): PomodoroPhase? =
        pomodoroPhases.firstOrNull { now >= it.startTime && now < it.endTime }

    // ================================================================================
    // V32: PLAN DE ACTIVIDADES — el usuario divide el enfoque en actividades (con
    // título y duración) y descansos colocados donde quiera. Cada cambio de segmento
    // suena una alarma corta (~5 s).
    // ================================================================================

    var focusActivityPlan: List<FocusSegment>
        get() {
            val json = prefs.getString(KEY_FOCUS_ACTIVITY_PLAN, null) ?: return emptyList()
            return try {
                val type = object : TypeToken<List<FocusSegment>>() {}.type
                pomodoroGson.fromJson<List<FocusSegment>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            prefs.edit().putString(KEY_FOCUS_ACTIVITY_PLAN, pomodoroGson.toJson(value)).apply()
        }

    /** Minutos totales del plan (0 si no hay plan). */
    val focusPlanTotalMinutes: Int
        get() = focusActivityPlan.sumOf { it.durationMinutes.coerceAtLeast(1) }

    /** Convierte el plan en fases cronológicas (work/rest) desde startTime. */
    fun buildPlanSchedule(startTime: Long, plan: List<FocusSegment>): List<PomodoroPhase> {
        if (plan.isEmpty()) return emptyList()
        val phases = mutableListOf<PomodoroPhase>()
        var t = startTime
        for (seg in plan) {
            val type = if (seg.type == "rest") "rest" else "work"
            val title = if (type == "work") {
                seg.title.trim().ifBlank { "Actividad" }
            } else {
                "Descanso"
            }
            val durMs = seg.durationMinutes.coerceIn(1, 480) * 60_000L
            phases.add(PomodoroPhase(type, t, t + durMs, title))
            t += durMs
        }
        return phases
    }

    /** Título de la actividad/descanso actual (para la pantalla de bloqueo). */
    fun currentActivityTitle(now: Long): String {
        val phase = currentPomodoroPhase(now) ?: return ""
        return phase.title.ifBlank { if (phase.type == "rest") "Descanso" else "Enfoque" }
    }

    private val lastAnnouncedSegmentKey: String
        get() = prefs.getString(KEY_FOCUS_LAST_ANNOUNCED, "") ?: ""

    private fun setLastAnnouncedSegmentKey(key: String) {
        prefs.edit().putString(KEY_FOCUS_LAST_ANNOUNCED, key).apply()
    }

    /** Comprueba si cambió el segmento actual y, si lo hizo, suena la alarma. Barato: 1 lectura. */
    fun pollSegmentAlarm() {
        if (!isLocked) return
        val now = System.currentTimeMillis()
        val phase = currentPomodoroPhase(now) ?: return
        val key = "${phase.startTime}|${phase.endTime}|${phase.type}"
        val last = lastAnnouncedSegmentKey
        if (key != last) {
            if (last.isNotEmpty()) {
                playSegmentAlarm(now)
            }
            setLastAnnouncedSegmentKey(key)
        }
    }

    @Volatile private var alarmPlayer: MediaPlayer? = null

    /** Reproduce una alarma corta (~5 s) eligiendo un sonido distinto cada vez. */
    private fun playSegmentAlarm(now: Long) {
        val phase = currentPomodoroPhase(now)
        try {
            val pool = if (phase?.type == "rest") {
                // descanso: tonos suaves
                intArrayOf(R.raw.chime_soft, R.raw.chime_warm, R.raw.chime_bright, R.raw.pop_soft)
            } else {
                // cambio de actividad: timbres más claros
                intArrayOf(
                    R.raw.alarm_beep, R.raw.alarm_clock, R.raw.alarm_bugle,
                    R.raw.alarm_digital_long, R.raw.chime_soft, R.raw.chime_bright
                )
            }
            val resId = pool[pool.indices.random()]
            alarmPlayer?.let {
                try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
                try { it.release() } catch (_: Exception) {}
            }
            val player = MediaPlayer.create(appContext, resId) ?: return
            player.isLooping = true
            player.start()
            alarmPlayer = player
            val label = runCatching { appContext.resources.getResourceEntryName(resId) }.getOrNull() ?: "?"
            android.util.Log.d("ZEN_PLAN", "Alarma de segmento -> $label")
            Handler(Looper.getMainLooper()).postDelayed({
                try { player.stop() } catch (_: Exception) {}
                try { player.release() } catch (_: Exception) {}
                if (alarmPlayer === player) alarmPlayer = null
            }, 5000)
        } catch (e: Exception) {
            android.util.Log.e("ZEN_PLAN", "No se pudo reproducir la alarma", e)
        }
    }

    /** True si ahora mismo estamos en una fase de DESCANSO del Pomodoro. */
    val isPomodoroRestPhase: Boolean
        get() = currentPomodoroPhase(System.currentTimeMillis())?.type == "rest"

    /** Descanso libre: durante una fase de descanso se permite desbloqueo sin reto. */
    fun unlockForRest() {
        val phase = currentPomodoroPhase(System.currentTimeMillis()) ?: return
        if (phase.type != "rest") return
        val now = System.currentTimeMillis()
        tempUnlockEndTime = phase.endTime.coerceAtMost(lockEndTime)
        cooldownEndTime = 0L
        android.util.Log.d("ZEN_POMODORO", "Descanso libre concedido hasta $tempUnlockEndTime")
        // V28: propagar el desbloqueo temporal a la extensión (tregua_until)
        pushLockStateToFirebase(isLocked, lockEndTime)
    }

    val isTempUnlocked: Boolean
        get() = System.currentTimeMillis() < tempUnlockEndTime

    val isCooldownActive: Boolean
        get() = System.currentTimeMillis() < cooldownEndTime

    val cooldownTimeRemaining: Long
        get() {
            val remaining = cooldownEndTime - System.currentTimeMillis()
            return if (remaining < 0) 0 else remaining
        }

    fun startLock(durationMinutes: Int) {
        val now = System.currentTimeMillis()
        val plan = focusActivityPlan
        // V32: si hay plan de actividades, el plan define la duración y las fases
        // (los descansos ya no se generan solos: se configuran en el plan)
        pomodoroPhases = if (plan.isNotEmpty()) {
            buildPlanSchedule(now, plan)
        } else {
            emptyList()
        }
        val effectiveMinutes = if (plan.isNotEmpty()) focusPlanTotalMinutes else durationMinutes
        val endTime = now + (effectiveMinutes * 60 * 1000)
        lockEndTime = endTime
        // V32: no anunciar la primera fase al iniciar el enfoque
        setLastAnnouncedSegmentKey(
            pomodoroPhases.firstOrNull()?.let { "${it.startTime}|${it.endTime}|${it.type}" } ?: ""
        )
        // Asignación aleatoria de frases (generador combinatorio, miles de variantes)
        longPhrase = PhraseGenerator.longFinish()
        mediumPhrase = PhraseGenerator.mediumTemp()
        shortPhrase = PhraseGenerator.shortFinished()
        isLocked = true
        tempUnlockEndTime = 0L
        cooldownEndTime = 0L
        pushLockStateToFirebase(true, endTime)
    }

    fun stopLock() {
        isLocked = false
        lockEndTime = 0L
        tempUnlockEndTime = 0L
        cooldownEndTime = 0L
        pomodoroPhases = emptyList()
        setLastAnnouncedSegmentKey("")
        alarmPlayer?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        alarmPlayer = null
        pushLockStateToFirebase(false, 0L)
    }

    fun startTempUnlock() {
        val now = System.currentTimeMillis()
        tempUnlockEndTime = now + (5 * 60 * 1000)
        cooldownEndTime = now + (15 * 60 * 1000)
        // V28: propagar la tregua a la extensión (tregua_until)
        pushLockStateToFirebase(isLocked, lockEndTime)
    }

    /** Termina el descanso Pomodoro en curso y adelanta el resto de fases para volver a trabajo ya. */
    fun endPomodoroRestNow() {
        val now = System.currentTimeMillis()
        val phase = currentPomodoroPhase(now) ?: return
        if (phase.type != "rest") return
        val remainingRest = phase.endTime - now
        val phases = pomodoroPhases.toMutableList()
        val idx = phases.indexOfFirst { it.startTime == phase.startTime && it.endTime == phase.endTime }
        if (idx < 0) return
        phases.removeAt(idx)
        val updated = phases.map { p ->
            if (p.startTime >= phase.endTime) {
                PomodoroPhase(p.type, p.startTime - remainingRest, p.endTime - remainingRest, p.title)
            } else {
                p
            }
        }
        pomodoroPhases = updated
        tempUnlockEndTime = 0L
        android.util.Log.d("ZEN_POMODORO", "Descanso terminado anticipadamente; fases re-programadas")
    }

    val devicePin: String
        get() {
            var pin = prefs.getString("device_unique_pin", "") ?: ""
            if (pin.isEmpty()) {
                val randomNum = 1000 + java.util.Random().nextInt(9000)
                pin = "ZEN-$randomNum"
                prefs.edit().putString("device_unique_pin", pin).apply()
            }
            return pin
        }

    var userSyncKey: String
        get() {
            val key = prefs.getString("user_sync_key", "") ?: ""
            return if (key.isNotEmpty()) key else devicePin
        }
        set(value) {
            val clean = value.lowercase().trim().replace(Regex("[^a-z0-9_@.-]"), "_")
            prefs.edit().putString("user_sync_key", clean).apply()
            pushLockStateToFirebase(isLocked, lockEndTime)
        }

    val deviceBrand: String = android.os.Build.MANUFACTURER.uppercase()
    val deviceModel: String = android.os.Build.MODEL

    var isExtensionConnected: Boolean = false
        private set

    /** UID de Firebase Auth (puede ser de sesión anónima de respaldo) */
    val firebaseUid: String?
        get() = firebaseAuth.currentUser?.uid

    /** V24 (Bug 4): true solo si hay sesión de Google real (no anónima) */
    val isGoogleSignedIn: Boolean
        get() = firebaseAuth.currentUser?.providerData?.any { it.providerId == "google.com" } == true

    /** Email de Google del usuario autenticado */
    val googleUserEmail: String
        get() = firebaseAuth.currentUser?.email ?: prefs.getString("google_user_email", "") ?: ""

    /**
     * V24.1: clave limpia del nodo que usa esta app en Firebase (/users/<targetKey>).
     * Es la MISMA que publica el teléfono en device_info y en LAN para que la
     * extensión la adopte y ambos dispositivos usen exactamente el mismo nodo.
     */
    val targetKey: String
        get() {
            val email = googleUserEmail
            val rawKey = if (email.isNotEmpty()) email else userSyncKey
            return rawKey.lowercase().trim().replace(Regex("[^a-z0-9_]"), "_")
        }

    /**
     * V24 (Bug 4): referencia base al nodo del usuario.
     * Prioridad: correo de Google → sync key (ZEN-XXXX) → UID.
     * Se antepone el sync key al UID para que una sesión anónima de respaldo
     * NO cambie la ruta de datos de los usuarios que conectan por PIN.
     */
    private fun userRef(): com.google.firebase.database.DatabaseReference {
        val key = targetKey
        android.util.Log.d("ZEN_SYNC", "userRef path -> /users/${key} (email='${googleUserEmail}', pin='${devicePin}')")
        return firebaseDb.getReference("users").child(key)
    }

    private var extensionPingListener: ValueEventListener? = null

    /**
     * Inicia el listener en tiempo real para detectar si la extensión de Chrome está conectada.
     * Escucha /users/<UID>/device_info/extension_last_ping y compara con el tiempo actual.
     */
    fun startExtensionPingListener() {
        val ref = userRef()?.child("device_info")?.child("extension_last_ping") ?: return
        
        // Eliminar listener anterior si existe
        extensionPingListener?.let { ref.removeEventListener(it) }

        extensionPingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val extPing = snapshot.getValue(Long::class.java) ?: 0L
                isExtensionConnected = (System.currentTimeMillis() - extPing) < 30000
                android.util.Log.d("ZEN_SYNC", "Realtime extPing change: ${extPing}, active: ${isExtensionConnected}")
            }
            override fun onCancelled(error: DatabaseError) {
                isExtensionConnected = false
                android.util.Log.e("ZEN_SYNC", "Realtime listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(extensionPingListener!!)
    }

    private var lockStateListener: ValueEventListener? = null
    private var remoteLockCallback: ((Boolean) -> Unit)? = null

    /**
     * V20: Inicia el listener en tiempo real de /users/<UID>/lock_state
     * para propagar el bloqueo cruzado Chrome -> Android.
     * Si otro dispositivo (extensión Chrome) inicia o termina un bloqueo,
     * este teléfono reacciona igual (siempre que el bloqueo cruzado esté activado).
     */
    fun startLockStateListener(callback: ((Boolean) -> Unit)? = null) {
        if (callback != null) remoteLockCallback = callback
        val ref = userRef()?.child("lock_state") ?: return

        lockStateListener?.let { ref.removeEventListener(it) }

        lockStateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isLockedRemote = snapshot.child("is_locked").getValue(Boolean::class.java) ?: false
                val expiresAtRemote = snapshot.child("expires_at").getValue(Long::class.java) ?: 0L
                val sourceDevice = snapshot.child("source_device").getValue(String::class.java) ?: ""

                // No reaccionar a nuestro propio estado (evitar eco infinito)
                if (sourceDevice == "android") {
                    // V24 (Propuesta 5): aun ignorando eco, actualizar timestamp de sesión remota
                    updateRemoteSessionEndTime(expiresAtRemote)
                    return
                }
                if (!crossDeviceLockEnabled) {
                    // V24 (Propuesta 5): aunque el bloqueo cruzado esté OFF, seguimos
                    // rastreando la sesión remota para proteger ajustes
                    updateRemoteSessionEndTime(expiresAtRemote)
                    return
                }

                val now = System.currentTimeMillis()
                if (isLockedRemote && expiresAtRemote > now) {
                    if (!isLocked) {
                        // Aplicar bloqueo remoto (no re-enviar a Firebase para evitar eco)
                        isLocked = true
                        lockEndTime = expiresAtRemote
                        tempUnlockEndTime = 0L
                        cooldownEndTime = 0L
                        // V24: aplicar las fases Pomodoro enviadas por el dispositivo remoto
                        val remotePhases = mutableListOf<PomodoroPhase>()
                        val phasesSnap = snapshot.child("phases")
                        if (phasesSnap.exists()) {
                            for (child in phasesSnap.children) {
                                val type = child.child("type").getValue(String::class.java) ?: "work"
                                val startMs = child.child("start_ms").getValue(Long::class.java) ?: 0L
                                val endMs = child.child("end_ms").getValue(Long::class.java) ?: 0L
                                if (endMs > startMs) remotePhases.add(PomodoroPhase(type, startMs, endMs))
                            }
                        }
                        pomodoroPhases = remotePhases
                        if (longPhrase.isBlank()) longPhrase = PhraseGenerator.longFinish()
                        if (mediumPhrase.isBlank()) mediumPhrase = PhraseGenerator.mediumTemp()
                        if (shortPhrase.isBlank()) shortPhrase = PhraseGenerator.shortFinished()
                        android.util.Log.d("ZEN_SYNC", "Bloqueo cruzado aplicado desde $sourceDevice (hasta $expiresAtRemote)")
                        remoteLockCallback?.invoke(true)
                    }
                } else if (!isLockedRemote && isLocked) {
                    // Desbloqueo remoto
                    isLocked = false
                    lockEndTime = 0L
                    tempUnlockEndTime = 0L
                    cooldownEndTime = 0L
                    pomodoroPhases = emptyList()
                    remoteSessionEndTime = 0L
                    android.util.Log.d("ZEN_SYNC", "Desbloqueo cruzado aplicado desde $sourceDevice")
                    remoteLockCallback?.invoke(false)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("ZEN_SYNC", "Lock state listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(lockStateListener!!)
    }

    private var configListener: ValueEventListener? = null
    private var remoteConfigCallback: ((Boolean, Boolean) -> Unit)? = null
    /**
     * V20.2: Inicia el listener en tiempo real de /users/<UID>/config para que
     * los ajustes cambiados desde la extensión de Chrome (bloqueo cruzado) se
     * reflejen al instante en el teléfono, sin reiniciar la app.
     * V24: el modo oscuro ya NO se sincroniza desde la extensión (Bug 2).
     */
    fun startConfigListener(callback: ((Boolean, Boolean) -> Unit)? = null) {
        if (callback != null) remoteConfigCallback = callback
        val ref = userRef()?.child("config") ?: return

        configListener?.let { ref.removeEventListener(it) }

        configListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val remoteCross = snapshot.child("cross_device_lock_enabled").getValue(Boolean::class.java)

                if (remoteCross != null && remoteCross != crossDeviceLockEnabled) {
                    prefs.edit().putBoolean(KEY_CROSS_DEVICE_LOCK, remoteCross).apply()
                    android.util.Log.d("ZEN_SYNC", "Bloqueo cruzado remoto aplicado desde la extensión: $remoteCross")
                }

                // V32: los ajustes Pomodoro remotos se eliminaron (descansos via plan)
                if (remoteCross != null) {
                    remoteConfigCallback?.invoke(
                        darkModeEnabled,
                        crossDeviceLockEnabled
                    )
                }
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("ZEN_SYNC", "Config listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(configListener!!)
    }

    // ================================================================================
    // V24 (Propuesta 2): TREGUA DE EMERGENCIA VERIFICADA POR EL 2º DISPOSITIVO
    // El PC escribe una solicitud en /users/<target>/tregua_request; el teléfono
    // la aprueba o deniega y la extensión la procesa en su siguiente poll.
    // ================================================================================
    private val treguaRequestCallbacks = mutableListOf<(String, Long) -> Unit>()
    private var treguaRequestListener: ValueEventListener? = null

    fun startTreguaRequestListener(callback: ((String, Long) -> Unit)? = null) {
        if (callback != null && !treguaRequestCallbacks.contains(callback)) {
            treguaRequestCallbacks.add(callback)
        }
        if (treguaRequestListener != null) return // ya escuchando
        val ref = userRef()?.child("tregua_request") ?: return

        treguaRequestListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reqId = snapshot.child("id").getValue(String::class.java) ?: return
                val requester = snapshot.child("requester").getValue(String::class.java) ?: ""
                val responded = snapshot.child("responded").getValue(Boolean::class.java) ?: false
                val requestedAt = snapshot.child("requested_at").getValue(Long::class.java) ?: 0L
                // Ignorar solicitudes propias o ya respondidas (evitar eco)
                if (requester == "android" || responded) return
                android.util.Log.d("ZEN_SYNC", "Solicitud de tregua desde $requester ($reqId)")
                treguaRequestCallbacks.toList().forEach { cb -> cb.invoke(reqId, requestedAt) }
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("ZEN_SYNC", "Listener de tregua cancelado: ${error.message}")
            }
        }
        ref.addValueEventListener(treguaRequestListener!!)
    }

    /** Responde a la solicitud de tregua del otro dispositivo */
    fun respondTreguaRequest(approved: Boolean) {
        val ref = userRef()?.child("tregua_request") ?: return
        val data = mapOf(
            "approved" to approved,
            "responded" to true,
            "responded_by" to "android",
            "responded_at" to System.currentTimeMillis()
        )
        ref.updateChildren(data)
    }

    // ================================================================================
    // V24.1: AUTENTICACIÓN DE DOS PASOS DEL BLOQUEO CRUZADO (código del TELÉFONO -> PC)
    // El PC SOLO solicita (status:'requesting'). El TELÉFONO genera el código, lo
    // muestra en su notificación y lo publica de vuelta (status:'pending' + code).
    // El usuario escribe el código en el PC y la extensión lo verifica.
    // ================================================================================
    private val authRequestCallbacks = mutableListOf<(String, String) -> Unit>() // (reqId, code)
    private var authRequestListener: ValueEventListener? = null
    private var lastAuthRequestId = ""

    fun startAuthRequestListener(callback: ((String, String) -> Unit)? = null) {
        if (callback != null && !authRequestCallbacks.contains(callback)) {
            authRequestCallbacks.add(callback)
        }
        if (authRequestListener != null) return // ya escuchando
        val ref = userRef()?.child("auth_request") ?: return

        authRequestListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reqId = snapshot.child("id").getValue(String::class.java) ?: return
                val requester = snapshot.child("requester").getValue(String::class.java) ?: ""
                val status = snapshot.child("status").getValue(String::class.java) ?: ""
                val code = snapshot.child("code").getValue(String::class.java) ?: ""
                val expiresAt = snapshot.child("expires_at").getValue(Long::class.java) ?: (System.currentTimeMillis() + 3 * 60 * 1000)
                // Solo atender solicitudes del PC y nuevas
                if (requester != "chrome_extension") return
                if (reqId == lastAuthRequestId) return
                lastAuthRequestId = reqId
                if (System.currentTimeMillis() > expiresAt) return // solicitud caducada

                if (status == "requesting") {
                    // V24.1: la extensión SOLICITA el código; el TELÉFONO lo genera,
                    // lo muestra por notificación y lo publica de vuelta para que la
                    // extensión lo lea y pueda verificarlo al escribirlo en el PC.
                    val generated = (1000..9999).random().toString()
                    android.util.Log.d("ZEN_2FA", "Solicitud del PC recibida. Código generado por el teléfono: $generated")
                    ref.updateChildren(
                        mapOf(
                            "id" to reqId,
                            "status" to "pending",
                            "code" to generated,
                            "requested_at" to (snapshot.child("requested_at").getValue(Long::class.java) ?: System.currentTimeMillis()),
                            "expires_at" to expiresAt,
                            "responded_at" to System.currentTimeMillis()
                        )
                    )
                    // Notificar a las UI (notificación + diálogo MainActivity)
                    authRequestCallbacks.toList().forEach { cb -> cb.invoke(reqId, generated) }
                } else if (status == "pending" && code.isNotEmpty()) {
                    // Compatibilidad: extensión vieja que ya enviaba el código (status pending)
                    android.util.Log.d("ZEN_2FA", "Código de verificación recibido desde el PC (flujo antiguo): $code")
                    authRequestCallbacks.toList().forEach { cb -> cb.invoke(reqId, code) }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("ZEN_2FA", "Listener de auth_request cancelado: ${error.message}")
            }
        }
        ref.addValueEventListener(authRequestListener!!)
    }

    /**
     * Envía heartbeat del dispositivo Android a Firebase usando el SDK.
     * Escribe en /users/<UID>/device_info
     */
    fun pushDeviceHeartbeatToFirebase(onResult: ((Boolean) -> Unit)? = null) {
        val ref = userRef()?.child("device_info")
        if (ref == null) {
            onResult?.invoke(false)
            return
        }

        val now = System.currentTimeMillis()
        val data = mapOf(
            "brand" to deviceBrand,
            "model" to deviceModel,
            "android_last_ping" to now,
            "last_ping" to now,
            "online" to true,
            "target_key" to targetKey
        )

        ref.updateChildren(data).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Leer extension_last_ping para verificar conexión
                ref.child("extension_last_ping").get().addOnSuccessListener { snapshot ->
                    val extPing = snapshot.getValue(Long::class.java) ?: 0L
                    val diff = now - extPing
                    isExtensionConnected = diff < 30000
                    android.util.Log.d("ZEN_SYNC", "Heartbeat SUCCESS! extPing=${extPing}, diff=${diff}ms, active=${isExtensionConnected}")
                    onResult?.invoke(isExtensionConnected)
                }.addOnFailureListener { err ->
                    android.util.Log.e("ZEN_SYNC", "Heartbeat read extPing FAILED: ${err.message}")
                    isExtensionConnected = false
                    onResult?.invoke(false)
                }
            } else {
                android.util.Log.e("ZEN_SYNC", "Heartbeat updateChildren FAILED: ${task.exception?.message}")
                isExtensionConnected = false
                onResult?.invoke(false)
            }
        }
    }

    /**
     * Envía estado de bloqueo a Firebase usando el SDK.
     * Escribe en /users/<UID>/lock_state
     */
    fun pushLockStateToFirebase(locked: Boolean, expiresAt: Long) {
        // V20: si el bloqueo cruzado está desactivado, este dispositivo no publica su estado
        if (!crossDeviceLockEnabled) return
        pushDeviceHeartbeatToFirebase()
        val ref = userRef()?.child("lock_state") ?: return

        val phasesData = pomodoroPhases.map { p ->
            mapOf(
                "type" to p.type,
                "start_ms" to p.startTime,
                "end_ms" to p.endTime
            )
        }

        val data = mapOf(
            "is_locked" to locked,
            "expires_at" to expiresAt,
            // V28: desbloqueo temporal (tregua) activo desde el teléfono; 0 si no hay
            "tregua_until" to (if (isTempUnlocked) tempUnlockEndTime else 0L),
            "updated_at" to System.currentTimeMillis(),
            "source_device" to "android",
            "pomodoro_enabled" to (locked && pomodoroPhases.isNotEmpty()),
            "phases" to phasesData
        )

        ref.setValue(data)
    }

    /**
     * Escribe el perfil del usuario en /users/<UID>/profile
     */
    fun pushUserProfile(role: String = "admin") {
        val ref = userRef()?.child("profile") ?: return
        val email = googleUserEmail
        if (email.isNotEmpty()) {
            val data = mapOf(
                "email" to email,
                "role" to role
            )
            ref.setValue(data)
        }
    }

    // ================================================================================
    // GESTIÓN DE NOTAS ZEN (HÍBRIDO: LOCAL DE RESPUESTA INSTANTÁNEA + NUBE FIREBASE)
    // ================================================================================
    private val gson = Gson()
    private var notesListener: ValueEventListener? = null
    private var activeNotesCallback: ((List<ZenNote>) -> Unit)? = null

    fun loadLocalNotes(): List<ZenNote> {
        val json = prefs.getString("zen_notes_local_json", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ZenNote>>() {}.type
            val notes = gson.fromJson<List<ZenNote>>(json, type) ?: emptyList()
            // V24.1: migrar categorías viejas a la clave canónica al cargar
            notes.map { it.copy(category = normalizeCategory(it.category)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveLocalNotes(notes: List<ZenNote>) {
        try {
            val json = gson.toJson(notes)
            prefs.edit().putString("zen_notes_local_json", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var heartbeatJob: Job? = null

    /**
     * Inicia un bucle periódico de latidos (ping) a Firebase cada 15 segundos
     * para que la Extensión de Chrome reconozca la conexión activa VERDE sin desconectarse a los 30s.
     */
    fun startPeriodicHeartbeat(scope: CoroutineScope) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    pushDeviceHeartbeatToFirebase()
                } catch (e: Exception) {
                    android.util.Log.e("ZEN_SYNC", "Error en latido periódico: ${e.message}")
                }
                delay(15000) // Latido continuo cada 15 segundos
            }
        }
    }

    fun stopPeriodicHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /**
     * V24.1 (Bug categorías): normaliza cualquier categoría a la clave CANÓNICA que
     * usan todos los dispositivos: 'general' | 'tarea' | 'idea' | 'reflexion'.
     * Tolera mayúsculas, acentos y etiquetas viejas ("Tarea", "Reflexión", etc.)
     * para que las notas sincronicen correctamente entre Android y la extensión.
     */
    fun normalizeCategory(raw: String): String {
        val key = raw.trim().lowercase()
            .replace("ó", "o")
            .replace("í", "i")
            .replace("é", "e")
            .replace("á", "a")
            .replace("ú", "u")
        return when (key) {
            "", "general" -> "general"
            "tarea" -> "tarea"
            "idea" -> "idea"
            "reflexion" -> "reflexion"
            else -> "general"
        }
    }

    fun addNote(content: String, category: String = "general", onComplete: ((Boolean) -> Unit)? = null) {
        if (content.isBlank()) return
        val noteId = "note_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val newNote = ZenNote(
            id = noteId,
            content = content.trim(),
            category = normalizeCategory(category),
            timestamp = System.currentTimeMillis(),
            deviceSource = "android"
        )

        // 1. Guardado local instantáneo (0.001s)
        val currentLocal = loadLocalNotes().toMutableList()
        currentLocal.removeAll { it.id == noteId }
        currentLocal.add(0, newNote)
        saveLocalNotes(currentLocal)
        activeNotesCallback?.invoke(currentLocal)
        onComplete?.invoke(true)

        // 2. Sincronización en segundo plano con Firebase RTDB (sin bloquear la UI)
        try {
            val ref = userRef()?.child("notes")?.child(noteId)
            if (ref != null) {
                val data = mapOf(
                    "id" to noteId,
                    "content" to newNote.content,
                    "category" to newNote.category,
                    "timestamp" to newNote.timestamp,
                    "deviceSource" to newNote.deviceSource
                )
                ref.setValue(data).addOnSuccessListener {
                    android.util.Log.d("ZEN_NOTES", "Nota guardada con éxito en Firebase: $noteId")
                }.addOnFailureListener { err ->
                    android.util.Log.e("ZEN_NOTES", "Fallo guardando nota en Firebase: ${err.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ZEN_NOTES", "Error guardando en Firebase (guardado local preservado): ${e.message}")
        }
    }

    fun deleteNote(noteId: String, onComplete: ((Boolean) -> Unit)? = null) {
        // 1. Eliminación local instantánea
        val currentLocal = loadLocalNotes().toMutableList()
        currentLocal.removeAll { it.id == noteId }
        saveLocalNotes(currentLocal)
        activeNotesCallback?.invoke(currentLocal)
        onComplete?.invoke(true)

        // 2. Eliminación en Firebase RTDB
        try {
            userRef()?.child("notes")?.child(noteId)?.removeValue()?.addOnSuccessListener {
                android.util.Log.d("ZEN_NOTES", "Nota eliminada con éxito en Firebase: $noteId")
            }?.addOnFailureListener { err ->
                android.util.Log.e("ZEN_NOTES", "Fallo eliminando nota en Firebase: ${err.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("ZEN_NOTES", "Error borrando en Firebase: ${e.message}")
        }
    }

    fun observeNotes(onNotesUpdated: (List<ZenNote>) -> Unit) {
        activeNotesCallback = onNotesUpdated
        // Emitir inmediatamente las notas locales sin demoras
        val localList = loadLocalNotes()
        onNotesUpdated(localList)

        // Escuchar cambios de Firebase RTDB para combinar notas remotas
        try {
            val ref = userRef()?.child("notes") ?: return
            if (notesListener != null) {
                ref.removeEventListener(notesListener!!)
            }
            notesListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val remoteList = mutableListOf<ZenNote>()
                    for (child in snapshot.children) {
                        val id = child.child("id").getValue(String::class.java) ?: child.key ?: ""
                        val content = child.child("content").getValue(String::class.java) ?: ""
                        val category = normalizeCategory(child.child("category").getValue(String::class.java) ?: "")
                        val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        val source = child.child("deviceSource").getValue(String::class.java) ?: "android"
                        if (content.isNotEmpty()) {
                            remoteList.add(ZenNote(id, content, category, timestamp, source))
                        }
                    }
                    
                    // Fusionar notas locales y remotas sin duplicados
                    val mergedMap = LinkedHashMap<String, ZenNote>()
                    loadLocalNotes().forEach { mergedMap[it.id] = it }
                    remoteList.forEach { mergedMap[it.id] = it }
                    
                    val finalList = mergedMap.values.sortedByDescending { it.timestamp }
                    saveLocalNotes(finalList)
                    onNotesUpdated(finalList)
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.w("ZEN_NOTES", "Firebase listener cancelado: ${error.message}")
                }
            }
            ref.addValueEventListener(notesListener!!)
        } catch (e: Exception) {
            android.util.Log.e("ZEN_NOTES", "Error iniciando observeNotes en Firebase: ${e.message}")
        }
    }
}

data class ZenNote(
    val id: String = "",
    val content: String = "",
    val category: String = "general",
    val timestamp: Long = 0L,
    val deviceSource: String = "android"
)


