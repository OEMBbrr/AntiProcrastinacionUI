package com.antiprocrastinacion.lock

import kotlin.random.Random

/**
 * V28: Generador combinatorio de frases y mensajes.
 *
 * En lugar de una lista fija de frases (que se repiten), combina plantillas
 * con bancos de fragmentos gramaticalmente correctos para producir MILES de
 * textos distintos por categoría (el producto de combinaciones supera las
 * 10.000 en cada una):
 *  - motivacionales: 24 plantillas x 25 x 25 -> >15.000
 *  - aviso del cajón: 24 plantillas x 22 x 20 -> >10.500 (personalizada con la app)
 *  - frases largas  : 22 oraciones, se eligen 6 distintas -> millones
 *  - frases medianas: 24 cláusulas, se eligen 3 distintas -> >13.000
 *  - frases cortas  : 18 plantillas x 25 x 25 -> >11.000
 */
object PhraseGenerator {

    private val rnd = Random(System.currentTimeMillis())

    // ------------------------------------------------------------------
    // MOTIVACIONALES (pantalla Zen / launcher / notas)
    // ------------------------------------------------------------------
    private val MOTIV_TEMPLATES = listOf(
        "{A} vale más que {B}.",
        "Elige {A} antes que {B}.",
        "{A} te lleva mucho más lejos que {B}.",
        "No dejes que {B} le robe el lugar a {A}.",
        "Hoy, entre {A} y {B}, elige {A}.",
        "El camino hacia tus metas pasa por {A}, no por {B}.",
        "{A} siempre supera a {B}.",
        "Cuando dudes, recuerda: {A} gana a {B}.",
        "{A} es la inversión que {B} nunca podrá darte.",
        "Prefiere {A} a {B}, aunque sea un poco incómodo.",
        "Cada día que eliges {A} sobre {B}, te acercas a tu mejor versión.",
        "Tu tiempo es tuyo: úsalo en {A}, no lo desperdicies en {B}.",
        "{A} construye futuro; {B} solo roba presente.",
        "La diferencia está en elegir {A} en lugar de {B}.",
        "Detente un segundo: ¿{A} o {B}? Ya sabes la respuesta.",
        "{A} te da tranquilidad; {B} solo te da ruido.",
        "No necesitas {B}; lo que de verdad necesitas es {A}.",
        "Mira hacia adelante con {A} y deja atrás {B}.",
        "{A} es un sí a tu futuro; {B} es un no.",
        "El mejor momento para {A} es ahora; {B} puede esperar.",
        "Si quieres avanzar, alimenta {A} e ignora {B}.",
        "La constancia con {A} vale oro; la tentación de {B} no vale nada.",
        "{A} llena; {B} vacía.",
        "Apuesta por {A} hoy y tu yo del futuro te lo agradecerá."
    )

    private val MOTIV_A = listOf(
        "una nota escrita a tiempo",
        "la disciplina de todos los días",
        "un minuto bien invertido",
        "el hábito de enfocarte",
        "la calma de haber hecho lo correcto",
        "tu paz mental",
        "un objetivo claro",
        "el esfuerzo silencioso",
        "una idea anotada antes de que se escape",
        "la constancia del que no se rinde",
        "el valor de tu atención",
        "una conversación real",
        "un descanso merecido",
        "el trabajo hecho con calma",
        "tu propio criterio",
        "la satisfacción de cumplir tu palabra",
        "una caminata sin pantallas",
        "el silencio que ordena tus ideas",
        "tu palabra contigo mismo",
        "el plan que te prometiste",
        "la lectura que te hace crecer",
        "el paso pequeño de hoy",
        "la versión serena de ti",
        "tu tiempo más valioso",
        "una promesa cumplida"
    )

    private val MOTIV_B = listOf(
        "el scroll sin rumbo",
        "la tentación de mirar el teléfono",
        "un video tras otro",
        "el impulso del momento",
        "la urgencia falsa de las notificaciones",
        "el ruido de las redes",
        "cinco minutos que se vuelven cincuenta",
        "la distracción automática",
        "el aburrimiento que asusta",
        "una notificación cualquiera",
        "el deslizamiento infinito",
        "la prisa innecesaria",
        "lo urgente que no importa",
        "la comparación con los demás",
        "una respuesta que podía esperar",
        "el hábito de posponer",
        "la queja sin acción",
        "el miedo a quedarte en silencio",
        "la dopamina fácil",
        "lo que otros opinan de ti",
        "el refugio en la pantalla",
        "la pereza disfrazada de descanso",
        "el drama de la última hora",
        "la aprobación de desconocidos",
        "el placer que no deja huella"
    )

    private val MOTIV_EMOJI = listOf(
        "", "", "🌿 ", "✨ ", "🧠 ", "🎯 ", "🌊 ", "💡 ", "🕊️ ", "🧘 ", "⚡ ", "🌌 ", "🎨 "
    )

    private fun buildMotivational(): String {
        val t = MOTIV_TEMPLATES.random()
        val a = MOTIV_A.random()
        val b = MOTIV_B.random()
        val base = t.replace("{A}", a).replace("{B}", b)
        return if (rnd.nextBoolean()) {
            MOTIV_EMOJI.random() + base.replaceFirstChar { it.uppercase() }
        } else {
            base.replaceFirstChar { it.uppercase() }
        }
    }

    // ------------------------------------------------------------------
    // AVISO DEL CAJÓN (personalizado con el nombre de la app)
    // ------------------------------------------------------------------
    private val NUDGE_TEMPLATES = listOf(
        "Un momento. Antes de abrir {app}: {A} importa más que {B}.",
        "¿De verdad necesitas {app} ahora, o solo buscas {B}? {A} te espera.",
        "{app} puede esperar diez minutos. {A} no puede esperar a {B}.",
        "Piensa en tu plan de hoy. ¿{app} está en él, o es solo {B}?",
        "Abrir {app} ahora es elegir {B}. Elegir {A} es otra cosa.",
        "Respira. ¿{app} resolverá algo o solo te dará {B}?",
        "{A} vale oro. {app} te ofrecerá {B} a cambio de tu tiempo.",
        "Cada vez que abres {app} por impulso, {B} gana. Hoy elige {A}.",
        "Tómate un minuto: {A} o {app} y {B}. La decisión es tuya.",
        "{app} seguirá ahí en una hora. {A} no siempre vuelve.",
        "Si estás cansado, descansa de verdad: {A}. No {app} y {B}.",
        "¿Qué cambiará en tu vida si abres {app} ahora? Mientras, {A}.",
        "La tentación de {app} es {B}. Tu meta es {A}.",
        "Apaga la pantalla un segundo. {A} gana a {app} y a {B}.",
        "{app} te roba minutos con {B}. Devuelve ese tiempo a {A}.",
        "Pregúntate si {app} merece {B}. Lo que merece tu día es {A}.",
        "Un recordatorio: {A} te lleva lejos; {app} te trae {B}.",
        "¿Realmente tienes algo urgente en {app} o es solo {B}?",
        "Tu tiempo es tuyo. Dáselo a {A}, no a {app} con {B}.",
        "Detente. Entre {app} y {B} o {A}, tu futuro ya eligió.",
        "Nota mental: {app} y {B} pueden esperar. {A} no.",
        "Si abres {app}, quizá veas {B}. Si eliges {A}, quizá lo logres.",
        "Déjalo en manos de la calma: {A} antes que {app}.",
        "Hoy no necesitas {app}. Necesitas {A} y nada de {B}."
    )

    private val NUDGE_A = listOf(
        "una nota escrita a tiempo",
        "la disciplina de todos los días",
        "un minuto bien invertido",
        "el hábito de enfocarte",
        "la calma de haber hecho lo correcto",
        "tu paz mental",
        "un objetivo claro",
        "el esfuerzo silencioso",
        "una idea anotada antes de que se escape",
        "la constancia del que no se rinde",
        "el valor de tu atención",
        "una conversación real",
        "un descanso merecido",
        "el trabajo hecho con calma",
        "tu propio criterio",
        "la satisfacción de cumplir tu palabra",
        "una caminata sin pantallas",
        "el silencio que ordena tus ideas",
        "tu palabra contigo mismo",
        "el plan que te prometiste",
        "la lectura que te hace crecer",
        "tu tiempo más valioso"
    )

    private val NUDGE_B = listOf(
        "el scroll sin rumbo",
        "el impulso del momento",
        "la urgencia falsa de las notificaciones",
        "el ruido de las redes",
        "minutos que se vuelven cincuenta",
        "la distracción automática",
        "el deslizamiento infinito",
        "la prisa innecesaria",
        "lo urgente que no importa",
        "la comparación con los demás",
        "una respuesta que podía esperar",
        "el hábito de posponer",
        "la queja sin acción",
        "la dopamina fácil",
        "el refugio en la pantalla",
        "la pereza disfrazada de descanso",
        "el drama de la última hora",
        "el placer que no deja huella",
        "una excusa más",
        "una ansiedad innecesaria"
    )

    private fun buildNudge(appLabel: String): String {
        val t = NUDGE_TEMPLATES.random()
        val a = NUDGE_A.random()
        val b = NUDGE_B.random()
        return t.replace("{app}", appLabel).replace("{A}", a).replace("{B}", b)
    }

    // ------------------------------------------------------------------
    // FRASES LARGAS (150-200 palabras) al pulsar "Ya terminé mi actividad"
    // ------------------------------------------------------------------
    private val LONG_SENTENCES = listOf(
        "La autodisciplina no es un castigo ni una limitación, sino la mayor muestra de respeto que puedo ofrecerme a mí mismo y a los sueños que quiero alcanzar en el largo plazo.",
        "Al decidir dar por concluida mi actividad antes de tiempo, debo preguntarme con honestidad si he entregado mi mejor esfuerzo o si simplemente busco una excusa para volver a la pantalla.",
        "El tiempo que dedico al trabajo profundo y sin interrupciones es la inversión más valiosa que puedo hacer para mi desarrollo personal y para el futuro que estoy construyendo con mis manos.",
        "Cada palabra que escribo con calma en este momento refuerza mi voluntad, entrena mi paciencia y me recuerda que los grandes logros exigen constancia, serenidad y una mente capaz de dominar la tentación.",
        "No permitiré que el aburrimiento pasajero ni la ansiedad por revisar las redes sociales dicten el ritmo de mi jornada ni decidan el rumbo de mis decisiones importantes.",
        "El verdadero progreso no se construye con esfuerzos a medias ni con interrupciones constantes, sino con dedicación sostenida y con el respeto riguroso por mi propio tiempo y mis compromisos.",
        "Cuando elijo mantener el enfoque a pesar de las ganas de parar, estoy demostrando que mi palabra vale, que mis metas importan y que soy dueño de mi atención.",
        "La mente que aprende a permanecer en silencio y concentrada encuentra soluciones donde otros ven excusas, y construye resultados donde otros se quedan solo en la intención.",
        "Reconozco que mi cerebro buscará refugio en la gratificación inmediata de una notificación, pero mi compromiso con el éxito real está por encima de cualquier impulso efímero del momento presente.",
        "Con cada minuto que protejo de las distracciones, estoy invirtiendo en la versión de mí que quiero ver al final del día, de la semana y de este proyecto importante.",
        "La paz mental que nace de cumplir lo que prometo vale infinitamente más que el ruido de una pantalla o la falsa urgencia de una conversación que bien puede esperar un momento mejor.",
        "Si realmente he terminado mi tarea, lo sabré sin necesidad de desbloquear el teléfono; el teléfono no cambia la verdad de mi esfuerzo, solo me ofrece una distracción cómoda.",
        "Acepto este momento de reflexión como parte del proceso, sabiendo que la incomodidad de mantenerme firme es pequeña comparada con la satisfacción de haber cumplido mi palabra.",
        "La constancia silenciosa de cada jornada forma el carácter y abre puertas que la improvisación y la impulsividad jamás podrán alcanzar en la vida de ninguna persona.",
        "Escribir despacio este texto me obliga a desacelerar, a escucharme y a recordar por qué empecé esta sesión de enfoque y qué es lo que realmente quiero proteger.",
        "Mi tiempo es el recurso más escaso que poseo, y cada decisión de protegerlo frente a la tentación es un paso firme hacia la vida que conscientemente he decidido construir.",
        "La serenidad llega cuando hago lo que dije que haría, cuando cumplo los acuerdos que hice conmigo mismo y cuando pongo mi futuro por delante de mi comodidad inmediata.",
        "No hay éxito que no exija horas de concentración tranquila, ni meta que se alcance cediendo a cada impulso; por eso hoy elijo quedarme conmigo y con mi tarea.",
        "Comprendo que la dificultad de mantener la atención es normal, y justamente por eso entrenarla me hace más fuerte, más libre y más capaz de lograr lo que me propongo.",
        "El recuerdo de los días en que fui constante me da fuerza ahora; quiero que el día de hoy sea otro de esos recuerdos que mañana me den tranquilidad y orgullo.",
        "La tecnología es una herramienta maravillosa, pero solo cuando yo decido usarla y no cuando ella decide usar mi tiempo, mi atención y mi paz para alimentar mi ansiedad.",
        "Terminaré esta reflexión con la misma calma con la que he trabajado, consciente de que cada elección enfocada de hoy es un ladrillo sólido en la casa de mis sueños."
    )

    // ------------------------------------------------------------------
    // FRASES MEDIANAS (20-50 palabras) para la Tregua Temporal
    // ------------------------------------------------------------------
    private val MEDIUM_CLAUSES = listOf(
        "Acepto esta breve tregua de unos minutos para atender algo puntual.",
        "Regresaré a mi actividad principal en cuanto termine este breve tiempo.",
        "El control de mis impulsos me permite usar la tecnología con moderación.",
        "Esta pausa es un descanso consciente y no un pretexto para distraerme.",
        "Cada minuto que protejo mi enfoque acerca mis metas más importantes.",
        "Cerraré las aplicaciones innecesarias para retomar mi trabajo con calma.",
        "La constancia en las pequeñas decisiones construye resultados duraderos.",
        "Reconozco que el tiempo de esta tregua es limitado y valioso.",
        "Respeto los límites que me he fijado para cuidar mi concentración.",
        "La autodisciplina es el puente entre mis deseos y mis logros.",
        "Usaré estos minutos con propósito claro y sin caer en el desplazamiento infinito.",
        "Al terminar la pausa, volveré de inmediato a mi sesión de enfoque.",
        "La mente serena trabaja mejor y con menos interrupciones.",
        "No permitiré que la inercia del teléfono cambie mi plan de hoy.",
        "Mi palabra contigo mismo vale más que cualquier distracción pasajera.",
        "El silencio y el orden mental son aliados de mi rendimiento.",
        "Cada elección responsable durante la pausa fortalece mi voluntad.",
        "Atiendo lo necesario con rapidez para retomar el ritmo de trabajo.",
        "La paciencia y el esfuerzo continuo rinden siempre buenos frutos.",
        "Esta tregua breve terminará y continuaré con mi labor sin excusas.",
        "Protejo mi atención de los estímulos que no aportan nada real.",
        "La serenidad de cumplir lo que empiezo vale más que un estímulo fácil.",
        "Vuelvo al trabajo con la mente despejada y la meta clara.",
        "El dominio de mi tiempo es la mayor muestra de respeto hacia mí."
    )

    // ------------------------------------------------------------------
    // FRASES CORTAS (8-14 palabras) al terminar el temporizador
    // ------------------------------------------------------------------
    private val SHORT_TEMPLATES = listOf(
        "{A}, y {B}.",
        "{A}. Ahora {B}.",
        "Hoy {A}.",
        "{A}: {B}.",
        "{A} porque {B}.",
        "Listo: {A} y {B}.",
        "{A}, además {B}.",
        "Cumplido: {A} y {B}.",
        "{A}. También {B}.",
        "{A}; {B}.",
        "Bien hecho: {A} y {B}.",
        "{A} y, sobre todo, {B}.",
        "Sí: {A}, {B}.",
        "{A}; ahora {B}.",
        "Por fin {A} y {B}.",
        "{A}; igualmente {B}.",
        "Perfecto: {A}, y {B}.",
        "{A}, y por eso {B}."
    )

    private val SHORT_A = listOf(
        "he terminado mi sesión",
        "mi enfoque dio frutos",
        "cumplí mi compromiso",
        "terminé lo que empecé",
        "superé el desafío",
        "he ganado mi tiempo",
        "mi constancia funcionó",
        "vencí a la tentación",
        "he protegido mi atención",
        "me mantuve firme",
        "honré mi palabra",
        "avancé hacia mi meta",
        "mi esfuerzo rindió",
        "cerré con éxito",
        "he hecho mi parte",
        "hoy me respeto",
        "la disciplina ganó",
        "completé mi plan",
        "estuve presente",
        "cumplí conmigo",
        "hice lo que dije",
        "logré mi objetivo",
        "terminé a tiempo",
        "sigo mi camino",
        "he sido constante"
    )

    private val SHORT_B = listOf(
        "ahora disfruto el descanso",
        "mi tiempo es mío",
        "me siento en paz",
        "puedo relajarme",
        "la satisfacción me acompaña",
        "el buen trabajo habla por sí solo",
        "lo importante ya está hecho",
        "hoy me lo gané",
        "nada me distrajo",
        "mi futuro lo agradece",
        "puedo celebrarlo",
        "la calma me pertenece",
        "sigo creciendo",
        "el plan continúa",
        "mi mente está tranquila",
        "lo logré a mi ritmo",
        "estoy orgulloso de mí",
        "el siguiente paso espera",
        "mi paz mental crece",
        "he aprendido hoy",
        "puedo descansar",
        "mi atención regresa",
        "hoy gané una batalla",
        "mis metas están más cerca",
        "estoy listo para seguir"
    )

    // ------------------------------------------------------------------
    // API pública
    // ------------------------------------------------------------------

    /** Frase motivacional aleatoria (miles de combinaciones posibles). */
    fun motivational(): String = buildMotivational()

    /**
     * Lista de [count] frases motivacionales DISTINTAS.
     * [seed] permite predefinir textos al inicio (p. ej. las curadas).
     */
    fun motivationalList(count: Int, seed: List<String> = emptyList()): List<String> {
        val seen = HashSet<String>(count * 2)
        val result = ArrayList<String>(count)
        seed.forEach {
            if (seen.add(it)) result.add(it)
        }
        var guard = 0
        while (result.size < count && guard < count * 200) {
            val s = buildMotivational()
            if (seen.add(s)) result.add(s)
            guard++
        }
        return result
    }

    /**
     * Mensaje del cajón para una app concreta. Personalizado con el nombre
     * de la app; [previous] evita repetir el mismo mensaje dos veces seguidas.
     */
    fun drawerNudge(appLabel: String, previous: String? = null): String {
        var msg = buildNudge(appLabel)
        var guard = 0
        while (msg == previous && guard < 10) {
            msg = buildNudge(appLabel)
            guard++
        }
        return msg
    }

    /** Frase larga (150-200 palabras) para "Ya terminé mi actividad". */
    fun longFinish(): String {
        return LONG_SENTENCES.shuffled(rnd).take(6).joinToString(" ")
    }

    /** Frase mediana (20-50 palabras) para la Tregua Temporal. */
    fun mediumTemp(): String {
        return (1..3).map { MEDIUM_CLAUSES.random() }.joinToString(" ")
    }

    /** Frase corta (8-14 palabras) al terminar el temporizador. */
    fun shortFinished(): String {
        val t = SHORT_TEMPLATES.random()
        val a = SHORT_A.random()
        val b = SHORT_B.random()
        return t.replace("{A}", a).replace("{B}", b).replaceFirstChar { it.uppercase() }
    }
}
