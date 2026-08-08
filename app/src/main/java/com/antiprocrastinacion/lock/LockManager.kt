package com.antiprocrastinacion.lock

import android.content.Context
import android.content.SharedPreferences

class LockManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("anti_procrastinacion_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_LOCK_END_TIME = "lock_end_time"
        private const val KEY_LONG_PHRASE = "long_phrase"
        private const val KEY_MEDIUM_PHRASE = "medium_phrase"
        private const val KEY_SHORT_PHRASE = "short_phrase"
        
        private const val KEY_TEMP_UNLOCK_END_TIME = "temp_unlock_end_time"
        private const val KEY_COOLDOWN_END_TIME = "cooldown_end_time"
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
        
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
        get() = prefs.getBoolean(KEY_IS_LOCKED, false)
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

    var allowedPackages: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED_PACKAGES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, value).apply()

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
        val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000)
        lockEndTime = endTime
        // Asignación aleatoria de frases
        longPhrase = LONG_FINISH_EARLY_PHRASES.random()
        mediumPhrase = MEDIUM_TEMP_UNLOCK_PHRASES.random()
        shortPhrase = SHORT_FINISHED_PHRASES.random()
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
        pushLockStateToFirebase(false, 0L)
    }

    fun startTempUnlock() {
        val now = System.currentTimeMillis()
        tempUnlockEndTime = now + (5 * 60 * 1000)
        cooldownEndTime = now + (15 * 60 * 1000)
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

    fun pushLockStateToFirebase(locked: Boolean, expiresAt: Long) {
        Thread {
            try {
                val rawKey = userSyncKey
                val syncKey = rawKey.lowercase().trim().replace(Regex("[^a-z0-9_@.-]"), "_")
                val url = java.net.URL("https://antiprocrastinacion-sync-default-rtdb.firebaseio.com/users/$syncKey/lock_state.json")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                
                val json = """{"is_locked":$locked,"expires_at":$expiresAt,"updated_at":${System.currentTimeMillis()},"source_device":"android"}"""
                conn.outputStream.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
