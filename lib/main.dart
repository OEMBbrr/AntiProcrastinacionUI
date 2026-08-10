import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';

void main() {
  runApp(const AntiProcrastinacionApp());
}

class AntiProcrastinacionApp extends StatelessWidget {
  const AntiProcrastinacionApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AntiProcrastinación',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: const Color(0xFF556B2F),
        scaffoldBackgroundColor: const Color(0xFFFDFBF7),
      ),
      home: const MainLockScreen(),
    );
  }
}

class MainLockScreen extends StatefulWidget {
  const MainLockScreen({super.key});

  @override
  State<MainLockScreen> createState() => _MainLockScreenState();
}

class _MainLockScreenState extends State<MainLockScreen> {
  bool isLocked = false;
  int targetDurationMinutes = 10;
  int remainingSeconds = 0;
  Timer? timer;

  // Frases largas para "Ya terminé mi actividad" (150-200 palabras)
  final List<String> longPhrases = [
    "Al tomar la firme decisión de dar por concluida mi actividad antes de tiempo, reconozco plenamente que la verdadera autodisciplina no consiste en buscar atajos hacia la comodidad, sino en honrar cada promesa que me hago a mí mismo. Reconozco que mi mente intentará justificar la pausa y buscará refugio en la gratificación inmediata de una pantalla, pero mi compromiso con el éxito real y con mi propio crecimiento personal se encuentra por encima de cualquier impulso efímero. Cada minuto dedicado a la concentración profunda y al trabajo bien ejecutado añade un valor incalculable a mi futuro, moldeando mi carácter y fortaleciendo mi capacidad de enfoque. Por lo tanto, declaro con total claridad y convicción que soy el único dueño de mi tiempo, que rechazo las distracciones innecesarias y que asumo la responsabilidad absoluta de mis acciones, eligiendo siempre la excelencia sobre la facilidad del momento presente.",
    "La prisa por dar por terminada una tarea importante suele ser el reflejo de la impaciencia y el deseo inconsciente de regresar a las distracciones cotidianas que no aportan valor real a mi vida. Al escribir estas palabras de manera pausada y consciente, me obligo a desacelerar el ritmo de mi pensamiento y a cuestionar la verdadera urgencia de desbloquear mi dispositivo. Comprendo que la capacidad de sostener la atención en una sola tarea durante periodos prolongados es una de las virtudes más escasas y valiosas de nuestra época. Cada vez que me detengo a reflexionar antes de actuar impulsivamente, estoy entrenando a mi cerebro para ser más fuerte, más paciente y más resistente al aburrimiento. Elijo proteger mi paz mental y mi rendimiento profesional por encima de la falsa urgencia del entorno digital, manteniendo el control absoluto sobre mis hábitos diarios."
  ];

  // Frases medianas para la tregua (20-50 palabras)
  final List<String> mediumPhrases = [
    "Acepto esta breve tregua de cinco minutos para atender una necesidad puntual, comprometiéndome a regresar de inmediato a mi actividad principal tan pronto como concluya este tiempo.",
    "Utilizaré estos cinco minutos de acceso temporal de manera consciente y responsable, evitando caer en la trampa del desplazamiento infinito y protegiendo mi enfoque de trabajo.",
    "Esta pausa temporal es un beneficio breve y controlado. Me mantendré atento al tiempo para retomar mis tareas sin perder el ritmo de productividad que he construido."
  ];

  // Frases cortas de finalización (1 oración)
  final List<String> shortPhrases = [
    "He cumplido mi meta con éxito y ahora disfruto de mi tiempo con tranquilidad.",
    "Mi tiempo de enfoque ha terminado y he completado mi objetivo satisfactoriamente.",
    "He demostrado autodisciplina y concluyo mi sesión de trabajo con total serenidad."
  ];

  String currentLongPhrase = "";
  String currentMediumPhrase = "";
  String currentShortPhrase = "";

  void startLock() {
    setState(() {
      isLocked = true;
      remainingSeconds = targetDurationMinutes * 60;
      currentLongPhrase = longPhrases[Random().nextInt(longPhrases.length)];
      currentMediumPhrase = mediumPhrases[Random().nextInt(mediumPhrases.length)];
      currentShortPhrase = shortPhrases[Random().nextInt(shortPhrases.length)];
    });

    timer?.cancel();
    timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (remainingSeconds > 0) {
        setState(() {
          remainingSeconds--;
        });
      } else {
        t.cancel();
      }
    });
  }

  void stopLock() {
    timer?.cancel();
    setState(() {
      isLocked = false;
      remainingSeconds = 0;
    });
  }

  String formatDuration(int totalSecs) {
    int hrs = totalSecs ~/ 3600;
    int mins = (totalSecs % 3600) ~/ 60;
    int secs = totalSecs % 60;
    if (hrs > 0) {
      return "${hrs.toString().padLeft(2, '0')}:${mins.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}";
    }
    return "${mins.toString().padLeft(2, '0')}:${secs.toString().padLeft(2, '0')}";
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            children: [
              const SizedBox(height: 20),
              // Header
              Column(
                children: [
                  Container(
                    width: 70,
                    height: 70,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: const Color(0xFF556B2F).withOpacity(0.1),
                      border: Border.all(color: const Color(0xFF556B2F), width: 1),
                    ),
                    child: const Icon(Icons.self_improvement, size: 36, color: Color(0xFF556B2F)),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    isLocked ? 'MODO ENFOQUE (iOS)' : 'antiprocrastinación',
                    style: const TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.w300,
                      letterSpacing: 1.2,
                      color: Color(0xFF2C3539),
                    ),
                  ),
                ],
              ),
              const Spacer(),
              // Central Block
              if (!isLocked)
                Column(
                  children: [
                    const Text('TEMPORIZADOR DE ENFOQUE', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.grey)),
                    const SizedBox(height: 16),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        IconButton(
                          onPressed: () {
                            if (targetDurationMinutes > 5) {
                              setState(() => targetDurationMinutes -= 5);
                            }
                          },
                          icon: const Icon(Icons.remove_circle_outline),
                        ),
                        Text('$targetDurationMinutes min', style: const TextStyle(fontSize: 36, fontWeight: FontWeight.w200)),
                        IconButton(
                          onPressed: () {
                            setState(() => targetDurationMinutes += 5);
                          },
                          icon: const Icon(Icons.add_circle_outline),
                        ),
                      ],
                    ),
                    const SizedBox(height: 32),
                    ElevatedButton(
                      onPressed: startLock,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF556B2F),
                        foregroundColor: Colors.white,
                        minimumSize: const Size.fromHeight(54),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      ),
                      child: const Text('Iniciar Enfoque', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                    ),
                  ],
                )
              else
                Column(
                  children: [
                    Text(
                      formatDuration(remainingSeconds),
                      style: const TextStyle(fontSize: 56, fontWeight: FontWeight.w200, color: Color(0xFF2C3539)),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Mantén tu mente centrada en tu actividad actual.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.grey, fontSize: 13),
                    ),
                    const SizedBox(height: 32),
                    if (remainingSeconds > 0) ...[
                      OutlinedButton(
                        onPressed: () => _showLongChallengeModal(context),
                        style: OutlinedButton.styleFrom(
                          minimumSize: const Size.fromHeight(50),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                          side: const BorderSide(color: Color(0xFF556B2F)),
                        ),
                        child: const Text('Ya terminé mi actividad', style: TextStyle(color: Color(0xFF556B2F))),
                      ),
                      const SizedBox(height: 12),
                      ElevatedButton(
                        onPressed: () => _showTempChallengeModal(context),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFFE57373),
                          foregroundColor: Colors.white,
                          minimumSize: const Size.fromHeight(50),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                        ),
                        child: const Text('Tregua temporal (5 min)'),
                      ),
                    ] else ...[
                      ElevatedButton(
                        onPressed: stopLock,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.green,
                          foregroundColor: Colors.white,
                          minimumSize: const Size.fromHeight(50),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                        ),
                        child: const Text('Finalizar y Desbloquear'),
                      ),
                    ],
                  ],
                ),
              const Spacer(),
              // Emergency Footer
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () {},
                      icon: const Icon(Icons.call, color: Colors.green, size: 18),
                      label: const Text('Teléfono', style: TextStyle(fontSize: 12, color: Colors.black87)),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () {},
                      icon: const Icon(Icons.message, color: Color(0xFF556B2F), size: 18),
                      label: const Text('Mensajes', style: TextStyle(fontSize: 12, color: Colors.black87)),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showLongChallengeModal(BuildContext context) {
    final TextEditingController inputController = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Verificación de Finalización', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Escribe el siguiente texto exactamente para desbloquear:', style: TextStyle(fontSize: 12, color: Colors.grey)),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(color: const Color(0xFFFDFBF7), borderRadius: BorderRadius.circular(8)),
                child: Text(currentLongPhrase, style: const TextStyle(fontSize: 12, height: 1.4)),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: inputController,
                maxLines: 4,
                decoration: InputDecoration(
                  hintText: 'Empieza a escribir aquí...',
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancelar')),
          ElevatedButton(
            onPressed: () {
              if (inputController.text.trim() == currentLongPhrase.trim() || _calculateMatchRatio(inputController.text, currentLongPhrase) >= 0.5) {
                Navigator.pop(ctx);
                stopLock();
              }
            },
            child: const Text('Desbloquear'),
          ),
        ],
      ),
    );
  }

  double _calculateMatchRatio(String input, String target) {
    if (target.isEmpty) return 0.0;
    int matches = 0;
    int minLength = input.length < target.length ? input.length : target.length;
    for (int i = 0; i < minLength; i++) {
      if (input[i] == target[i]) matches++;
    }
    return matches / target.length;
  }

  void _showTempChallengeModal(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Desafío de Tregua (5 Min)', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ElevatedButton(
              onPressed: () {
                Navigator.pop(ctx);
                setState(() {
                  remainingSeconds = 5 * 60;
                });
              },
              child: const Text('Resolver Problema Matemático'),
            ),
            const SizedBox(height: 8),
            OutlinedButton(
              onPressed: () {
                Navigator.pop(ctx);
                setState(() {
                  remainingSeconds = 5 * 60;
                });
              },
              child: const Text('Escribir Frase de Enfoque'),
            ),
          ],
        ),
      ),
    );
  }
}
