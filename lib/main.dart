import 'dart:async';
import 'dart:math';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'screens/zen_notes_screen.dart';
import 'screens/zen_sleep_screen.dart';
import 'screens/zen_tasks_screen.dart';

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
  Timer? workModeChecker;

  bool isWorkModeActive = false;
  Map<int, Map<String, TimeOfDay?>> workSchedule = {
    1: {'start': null, 'end': null},
    2: {'start': null, 'end': null},
    3: {'start': null, 'end': null},
    4: {'start': null, 'end': null},
    5: {'start': null, 'end': null},
  };

  // Modo Sin Redes/Dopamina
  bool isNoSocialModeActive = false;
  int noSocialRemainingSeconds = 0;
  int noSocialTargetMinutes = 60;
  Timer? noSocialTimer;

  // Modo Paseo
  bool isWalkModeActive = false;

  @override
  void initState() {
    super.initState();
    _checkWorkModeStatus();
    workModeChecker = Timer.periodic(const Duration(seconds: 30), (timer) {
      _checkWorkModeStatus();
    });
  }

  @override
  void dispose() {
    workModeChecker?.cancel();
    timer?.cancel();
    noSocialTimer?.cancel();
    super.dispose();
  }

  void _startNoSocialMode() {
    setState(() {
      isNoSocialModeActive = true;
      noSocialRemainingSeconds = noSocialTargetMinutes * 60;
    });
    noSocialTimer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (noSocialRemainingSeconds > 0) {
        setState(() => noSocialRemainingSeconds--);
      } else {
        t.cancel();
        setState(() => isNoSocialModeActive = false);
      }
    });
  }

  void _checkWorkModeStatus() {
    DateTime now = DateTime.now();
    int day = now.weekday; // 1 = Monday, 7 = Sunday
    
    if (day >= 1 && day <= 5) {
      var schedule = workSchedule[day];
      if (schedule != null && schedule['start'] != null && schedule['end'] != null) {
        TimeOfDay start = schedule['start']!;
        TimeOfDay end = schedule['end']!;
        
        int nowMinutes = now.hour * 60 + now.minute;
        int startMinutes = start.hour * 60 + start.minute;
        int endMinutes = end.hour * 60 + end.minute + 5; // 5 minutos de colchón
        
        bool shouldBeActive = nowMinutes >= startMinutes && nowMinutes < endMinutes;
        if (shouldBeActive != isWorkModeActive) {
          setState(() {
            isWorkModeActive = shouldBeActive;
          });
        }
      } else {
        if (isWorkModeActive) setState(() => isWorkModeActive = false);
      }
    } else {
      if (isWorkModeActive) setState(() => isWorkModeActive = false);
    }
  }

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
    Widget content = Scaffold(
      backgroundColor: const Color(0xFFFDFBF7),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 16.0),
          child: Column(
            children: [
              _buildHeader(),
              if (!isLocked) _buildLauncherUI() else _buildLockScreenUI(),
            ],
          ),
        ),
      ),
    );

    if (isNoSocialModeActive) {
      return ColorFiltered(
        colorFilter: const ColorFilter.matrix([
          0.2126, 0.7152, 0.0722, 0, 0,
          0.2126, 0.7152, 0.0722, 0, 0,
          0.2126, 0.7152, 0.0722, 0, 0,
          0,      0,      0,      1, 0,
        ]),
        child: content,
      );
    }
    return content;
  }

  Widget _buildHeader() {
    return Stack(
      alignment: Alignment.center,
      children: [
        Column(
          children: [
            const Icon(CupertinoIcons.leaf_arrow_circlepath, size: 42, color: Color(0xFF556B2F)),
            const SizedBox(height: 12),
            Text(
              isLocked ? 'Modo Enfoque' : 'Zen Hub',
              style: const TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.w500,
                letterSpacing: -0.5,
                color: Color(0xFF2C3539),
              ),
            ),
            if (isWorkModeActive && !isLocked)
              Container(
                margin: const EdgeInsets.only(top: 8),
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                decoration: BoxDecoration(color: const Color(0xFFE57373).withOpacity(0.1), borderRadius: BorderRadius.circular(12)),
                child: const Text('Modo Trabajo Activo', style: TextStyle(color: Color(0xFFE57373), fontSize: 12, fontWeight: FontWeight.bold)),
              ),
            if (isNoSocialModeActive && !isLocked)
            if (isWalkModeActive && !isLocked)
              Container(
                margin: const EdgeInsets.only(top: 8),
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                decoration: BoxDecoration(color: const Color(0xFF556B2F).withOpacity(0.1), borderRadius: BorderRadius.circular(12)),
                child: const Text('Modo Paseo Activo', style: TextStyle(color: Color(0xFF556B2F), fontSize: 12, fontWeight: FontWeight.bold)),
              ),
          ],
        ),
        if (!isLocked)
          Positioned(
            right: 0,
            top: 0,
            child: IconButton(
              icon: const Icon(CupertinoIcons.settings, color: Colors.grey),
              onPressed: _showWorkModeSettings,
            ),
          )
      ],
    );
  }

  Widget _buildLauncherUI() {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 16),
          // Quote Card (Apple Style)
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(24),
              boxShadow: [
                BoxShadow(color: Colors.black.withOpacity(0.03), blurRadius: 10, offset: const Offset(0, 4)),
              ],
            ),
            child: const Column(
              children: [
                Icon(CupertinoIcons.quote_bubble, color: Color(0xFF556B2F), size: 24),
                SizedBox(height: 12),
                Text(
                  '"La disciplina es el puente entre metas y logros."',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.w400, color: Color(0xFF333333), letterSpacing: -0.3, height: 1.4),
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          
          // Native Apps Row
          Row(
            children: [
              Expanded(child: _buildAppleStyleTile(CupertinoIcons.pencil_outline, "Notas", _showNotasModal)),
              const SizedBox(width: 12),
              Expanded(child: _buildAppleStyleTile(CupertinoIcons.moon_stars, "Sueño", _showSleepCycleModal)),
              const SizedBox(width: 12),
              Expanded(child: _buildAppleStyleTile(CupertinoIcons.checkmark_circle, "Tareas", _showTaskOrganizerModal)),
            ],
          ),
          const SizedBox(height: 24),

          // App List (iOS Settings style)
          const Padding(
            padding: EdgeInsets.only(left: 8, bottom: 8),
            child: Text('APLICACIONES PERMITIDAS', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.black45, letterSpacing: 0.5)),
          ),
          Expanded(
            child: Container(
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(24),
                boxShadow: [
                  BoxShadow(color: Colors.black.withOpacity(0.02), blurRadius: 8, offset: const Offset(0, 2)),
                ],
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(24),
                child: ListView(
                  padding: EdgeInsets.zero,
                  children: [
                    _buildAppleAppRow(CupertinoIcons.camera, "Cámara", isFirst: true),
                    _buildAppleAppRow(CupertinoIcons.chat_bubble_text, "Mensajes"),
                    _buildAppleAppRow(CupertinoIcons.map, "Mapas"),
                    _buildAppleAppRow(CupertinoIcons.phone, "Llamadas"),
                    _buildAppleAppRow(CupertinoIcons.bubble_left_bubble_right, "WhatsApp"),
                    _buildAppleAppRow(CupertinoIcons.music_note, "Apple Music", isMusic: true),
                    _buildAppleAppRow(CupertinoIcons.photo, "Instagram", isDopamine: true),
                    _buildAppleAppRow(CupertinoIcons.gamecontroller, "Casino Slot", isDopamine: true),
                    _buildAppleAppRow(CupertinoIcons.globe, "Navegador Web", isDopamine: true, isLast: true),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 24),

          // Mode Buttons
          Row(
            children: [
              Expanded(
                child: _buildModeButton(
                  icon: CupertinoIcons.eye_slash,
                  label: 'Sin Redes',
                  isActive: isNoSocialModeActive,
                  onPressed: isNoSocialModeActive || isWalkModeActive ? null : () => _showNoSocialSetupModal(context),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _buildModeButton(
                  icon: CupertinoIcons.tree,
                  label: 'Paseo',
                  isActive: isWalkModeActive,
                  onPressed: isNoSocialModeActive || isLocked ? null : () => setState(() => isWalkModeActive = !isWalkModeActive),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _buildModeButton(
                  icon: CupertinoIcons.lock_shield,
                  label: 'Enfoque',
                  isActive: isLocked,
                  onPressed: isNoSocialModeActive || isWalkModeActive ? null : () => _showFocusSetupModal(context),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildModeButton({required IconData icon, required String label, required bool isActive, required VoidCallback? onPressed}) {
    return ElevatedButton(
      onPressed: onPressed,
      style: ElevatedButton.styleFrom(
        backgroundColor: isActive ? const Color(0xFF2C3539) : Colors.white,
        foregroundColor: isActive ? Colors.white : const Color(0xFF2C3539),
        padding: const EdgeInsets.symmetric(vertical: 16),
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16), 
          side: BorderSide(color: isActive ? Colors.transparent : Colors.grey.shade300)
        ),
      ),
      child: Column(
        children: [
          Icon(icon, size: 20),
          const SizedBox(height: 4),
          Text(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, letterSpacing: -0.5)),
        ],
      ),
    );
  }

  Widget _buildLockScreenUI() {
    return Expanded(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 40),
            decoration: BoxDecoration(
              color: Colors.white,
              shape: BoxShape.circle,
              boxShadow: [
                BoxShadow(color: const Color(0xFF556B2F).withOpacity(0.15), blurRadius: 40, spreadRadius: 10),
              ],
            ),
            child: Text(
              formatDuration(remainingSeconds),
              style: const TextStyle(
                fontSize: 64,
                fontWeight: FontWeight.w200,
                color: Color(0xFF2C3539),
                fontFeatures: [FontFeature.tabularFigures()],
              ),
            ),
          ),
          const SizedBox(height: 32),
          const Text(
            'Concéntrate en tu actividad.',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.grey, fontSize: 16, fontWeight: FontWeight.w400, letterSpacing: -0.2),
          ),
          const SizedBox(height: 48),
          if (remainingSeconds > 0) ...[
            OutlinedButton(
              onPressed: () => _showLongChallengeModal(context),
              style: OutlinedButton.styleFrom(
                minimumSize: const Size.fromHeight(56),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                side: const BorderSide(color: Color(0xFF556B2F), width: 1.5),
              ),
              child: const Text('Ya terminé mi actividad', style: TextStyle(color: Color(0xFF556B2F), fontSize: 16, fontWeight: FontWeight.w600)),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () => _showTempChallengeModal(context),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFFE57373),
                foregroundColor: Colors.white,
                elevation: 0,
                minimumSize: const Size.fromHeight(56),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
              ),
              child: const Text('Tregua temporal (5 min)', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            ),
          ] else ...[
            ElevatedButton(
              onPressed: stopLock,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF556B2F),
                foregroundColor: Colors.white,
                elevation: 0,
                minimumSize: const Size.fromHeight(56),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
              ),
              child: const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.lock_open_rounded, size: 20),
                  SizedBox(width: 8),
                  Text('Desbloquear Dispositivo', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                ],
              ),
            ),
          ],
        ],
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

  Widget _buildAppleStyleTile(IconData icon, String label, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(color: Colors.black.withOpacity(0.02), blurRadius: 8, offset: const Offset(0, 2)),
          ],
        ),
        child: Column(
          children: [
            Icon(icon, color: const Color(0xFF556B2F), size: 26),
            const SizedBox(height: 8),
            Text(label, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: Color(0xFF333333))),
          ],
        ),
      ),
    );
  }

  Widget _buildAppleAppRow(IconData icon, String name, {bool isDopamine = false, bool isMusic = false, bool isFirst = false, bool isLast = false}) {
    return InkWell(
      onTap: () {
        if (isWorkModeActive && (isDopamine || isMusic)) {
          _showWorkModeBlockedDialog(name);
          return;
        }
        if (isNoSocialModeActive && (isDopamine || isMusic) && name != "WhatsApp") {
          _showNoSocialBlockedDialog(name);
          return;
        }

        if (isWalkModeActive) {
          if (isMusic) {
            _showWalkModeBlockedDialog(name);
            return;
          }
          if (isDopamine) {
            ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Límite de 15 mins para $name en Modo Paseo.')));
            return;
          }
          if (name == "WhatsApp") {
            ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Límite de 20 mins para WhatsApp en Modo Paseo.')));
            return;
          }
        }

        if (isDopamine) {
           _showIntentionalityDialog(name);
        } else {
           if (isNoSocialModeActive) {
             ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Límite de 30 mins activado para $name.')));
           } else {
             ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Abriendo $name...')));
           }
        }
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
        decoration: BoxDecoration(
          border: isLast ? null : Border(bottom: BorderSide(color: Colors.black.withOpacity(0.05), width: 1)),
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: isDopamine ? Colors.red.withOpacity(0.1) : const Color(0xFF556B2F).withOpacity(0.1),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(icon, color: isDopamine ? Colors.red.shade400 : const Color(0xFF556B2F), size: 20),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Text(
                name,
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w400, color: Color(0xFF2C3539), letterSpacing: -0.3),
              ),
            ),
            Icon(CupertinoIcons.chevron_right, color: Colors.black.withOpacity(0.2), size: 16),
          ],
        ),
      ),
    );
  }

  void _showWorkModeBlockedDialog(String appName) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Bloqueado', style: TextStyle(fontWeight: FontWeight.bold)),
        content: Text('Estás en tu horario de Trabajo/Estudio.\n\n$appName se desbloqueará 5 minutos después de tu hora de salida.'),
        actions: [
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx),
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF2C3539), foregroundColor: Colors.white),
            child: const Text('Entendido'),
          ),
        ],
      ),
    );
  }

  void _showWorkModeSettings() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) {
          return Container(
            height: MediaQuery.of(ctx).size.height * 0.8,
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(CupertinoIcons.briefcase, color: Color(0xFF556B2F), size: 28),
                    SizedBox(width: 12),
                    Text('Horario de Trabajo', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, letterSpacing: -0.5)),
                  ],
                ),
                const SizedBox(height: 8),
                const Text('Configura tus clases o trabajo. Las apps distractoras se bloquearán automáticamente.', style: TextStyle(color: Colors.grey)),
                const SizedBox(height: 24),
                Expanded(
                  child: ListView.separated(
                    itemCount: 5,
                    separatorBuilder: (_, __) => const Divider(),
                    itemBuilder: (ctx, i) {
                      int day = i + 1;
                      List<String> dias = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'];
                      var sched = workSchedule[day]!;
                      
                      return Padding(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        child: Row(
                          children: [
                            SizedBox(
                              width: 80,
                              child: Text(dias[i], style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                            ),
                            Expanded(
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.end,
                                children: [
                                  _buildTimeSelector(ctx, sched['start'], (time) {
                                    setModalState(() => sched['start'] = time);
                                    setState(() {});
                                    _checkWorkModeStatus();
                                  }),
                                  const Padding(padding: EdgeInsets.symmetric(horizontal: 8), child: Text('-')),
                                  _buildTimeSelector(ctx, sched['end'], (time) {
                                    setModalState(() => sched['end'] = time);
                                    setState(() {});
                                    _checkWorkModeStatus();
                                  }),
                                ],
                              ),
                            )
                          ],
                        ),
                      );
                    },
                  ),
                ),
                ElevatedButton(
                  onPressed: () => Navigator.pop(ctx),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF2C3539),
                    foregroundColor: Colors.white,
                    minimumSize: const Size.fromHeight(56),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                  ),
                  child: const Text('Guardar Horarios'),
                )
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildTimeSelector(BuildContext context, TimeOfDay? time, Function(TimeOfDay) onSelected) {
    String formatted = time != null ? "${time.hour.toString().padLeft(2,'0')}:${time.minute.toString().padLeft(2,'0')}" : "--:--";
    return GestureDetector(
      onTap: () async {
        TimeOfDay? picked = await showTimePicker(context: context, initialTime: time ?? const TimeOfDay(hour: 8, minute: 0));
        if (picked != null) onSelected(picked);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: Colors.grey.shade100,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Text(formatted, style: const TextStyle(fontWeight: FontWeight.w600, color: Color(0xFF556B2F))),
      ),
    );
  }

  void _showIntentionalityDialog(String appName) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('¿Intención Real?', style: TextStyle(fontWeight: FontWeight.bold)),
        content: Text('Estás a punto de abrir $appName.\n\n¿Realmente necesitas usarla o solo buscas distraerte un rato?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Solo quería distraerme', style: TextStyle(color: Colors.grey)),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Abriendo $appName...')));
            },
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF556B2F), foregroundColor: Colors.white),
            child: const Text('Necesito usarla'),
          ),
        ],
      ),
    );
  }

  void _showNotasModal() {
    showModalBottomSheet(context: context, isScrollControlled: true, backgroundColor: Colors.transparent, builder: (ctx) => const ZenNotesScreen());
  }

  void _showSleepCycleModal() {
    showModalBottomSheet(context: context, isScrollControlled: true, backgroundColor: Colors.transparent, builder: (ctx) => const ZenSleepScreen());
  }

  void _showTaskOrganizerModal() {
    showModalBottomSheet(context: context, isScrollControlled: true, backgroundColor: Colors.transparent, builder: (ctx) => const ZenTasksScreen());
  }

  void _showFocusSetupModal(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFFFDFBF7),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) {
          return Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('CONFIGURAR MODO ENFOQUE', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Colors.grey)),
                const SizedBox(height: 24),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    IconButton(
                      onPressed: () {
                        if (targetDurationMinutes > 5) {
                          setModalState(() => targetDurationMinutes -= 5);
                          setState(() => targetDurationMinutes = targetDurationMinutes);
                        }
                      },
                      icon: const Icon(Icons.remove_circle_outline),
                    ),
                    Text('$targetDurationMinutes min', style: const TextStyle(fontSize: 36, fontWeight: FontWeight.w200)),
                    IconButton(
                      onPressed: () {
                        setModalState(() => targetDurationMinutes += 5);
                        setState(() => targetDurationMinutes = targetDurationMinutes);
                      },
                      icon: const Icon(Icons.add_circle_outline),
                    ),
                  ],
                ),
                const SizedBox(height: 32),
                ElevatedButton(
                  onPressed: () {
                    Navigator.pop(ctx);
                    startLock();
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF556B2F),
                    foregroundColor: Colors.white,
                    minimumSize: const Size.fromHeight(54),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  ),
                  child: const Text('Iniciar Enfoque', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                ),
                const SizedBox(height: 24),
              ],
            ),
          );
        }
      ),
    );
  }
  void _showNoSocialBlockedDialog(String appName) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Bloqueado', style: TextStyle(fontWeight: FontWeight.bold)),
        content: Text('Estás en el Modo Sin Redes.\n\n$appName se mantendrá bloqueado hasta que se acabe tu temporizador.'),
        actions: [
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx),
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF2C3539), foregroundColor: Colors.white),
            child: const Text('Entendido'),
          ),
        ],
      ),
    );
  }

  void _showWalkModeBlockedDialog(String appName) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Disfruta el momento', style: TextStyle(fontWeight: FontWeight.bold)),
        content: Text('Estás en Modo Paseo.\n\n$appName ha sido bloqueada intencionalmente para que conectes con tu entorno y disfrutes del paseo.'),
        actions: [
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx),
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF556B2F), foregroundColor: Colors.white),
            child: const Text('Volver a caminar'),
          ),
        ],
      ),
    );
  }

  void _showNoSocialSetupModal(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (BuildContext context, StateSetter setModalState) {
            return Container(
              height: MediaQuery.of(context).size.height * 0.7,
              padding: const EdgeInsets.all(32),
              decoration: const BoxDecoration(
                color: Color(0xFFFDFBF7),
                borderRadius: BorderRadius.vertical(top: Radius.circular(32)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Center(
                    child: Container(
                      width: 48,
                      height: 4,
                      decoration: BoxDecoration(color: Colors.grey.shade300, borderRadius: BorderRadius.circular(2)),
                    ),
                  ),
                  const SizedBox(height: 32),
                  const Text('Modo Sin Redes', style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold, letterSpacing: -1, color: Color(0xFF2C3539))),
                  const SizedBox(height: 8),
                  const Text('Bloquea distracciones. La pantalla se pondrá en escala de grises. Este modo no se puede apagar hasta que el tiempo acabe.', style: TextStyle(color: Colors.black54, fontSize: 15, height: 1.4)),
                  const SizedBox(height: 48),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      IconButton(
                        icon: const Icon(CupertinoIcons.minus_circle_fill, size: 40, color: Color(0xFF2C3539)),
                        onPressed: () {
                          if (noSocialTargetMinutes > 15) setModalState(() => noSocialTargetMinutes -= 15);
                        },
                      ),
                      const SizedBox(width: 24),
                      Text('$noSocialTargetMinutes min', style: const TextStyle(fontSize: 40, fontWeight: FontWeight.w200, color: Color(0xFF2C3539), fontFeatures: [FontFeature.tabularFigures()])),
                      const SizedBox(width: 24),
                      IconButton(
                        icon: const Icon(CupertinoIcons.plus_circle_fill, size: 40, color: Color(0xFF2C3539)),
                        onPressed: () {
                          if (noSocialTargetMinutes < 240) setModalState(() => noSocialTargetMinutes += 15);
                        },
                      ),
                    ],
                  ),
                  const Spacer(),
                  ElevatedButton(
                    onPressed: () {
                      Navigator.pop(context);
                      _startNoSocialMode();
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF2C3539),
                      foregroundColor: Colors.white,
                      minimumSize: const Size.fromHeight(60),
                      elevation: 0,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                    ),
                    child: const Text('Comenzar Desintoxicación', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }
}
