import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:intl/intl.dart';

class ZenSleepScreen extends StatefulWidget {
  const ZenSleepScreen({super.key});

  @override
  State<ZenSleepScreen> createState() => _ZenSleepScreenState();
}

class _ZenSleepScreenState extends State<ZenSleepScreen> {
  List<DateTime> _wakeTimes = [];
  bool _calculating = false;

  void _calculateWakeTimes() {
    setState(() => _calculating = true);
    
    // Average time to fall asleep: 15 minutes
    DateTime fallAsleepTime = DateTime.now().add(const Duration(minutes: 15));
    
    _wakeTimes.clear();
    // Calculate 6, 5, 4, 3 sleep cycles (each cycle is 90 mins)
    for (int cycles = 6; cycles >= 3; cycles--) {
      _wakeTimes.add(fallAsleepTime.add(Duration(minutes: 90 * cycles)));
    }
    
    setState(() => _calculating = false);
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.9,
      decoration: const BoxDecoration(
        color: Color(0xFFFDFBF7),
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Column(
        children: [
          const SizedBox(height: 12),
          Container(
            width: 40,
            height: 4,
            decoration: BoxDecoration(color: Colors.grey.shade300, borderRadius: BorderRadius.circular(2)),
          ),
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('Ciclo de Sueño', style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold, letterSpacing: -0.5, color: Color(0xFF2C3539))),
                IconButton(
                  icon: const Icon(CupertinoIcons.xmark_circle_fill, color: Colors.grey, size: 28),
                  onPressed: () => Navigator.pop(context),
                )
              ],
            ),
          ),
          const SizedBox(height: 24),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 24),
            child: Text(
              'Un ciclo de sueño dura 90 minutos. Despertar al final de un ciclo te hará sentir más descansado y alerta.',
              style: TextStyle(color: Colors.black54, fontSize: 16, height: 1.4),
            ),
          ),
          const SizedBox(height: 32),
          
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: ElevatedButton(
              onPressed: _calculateWakeTimes,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF2C3539),
                foregroundColor: Colors.white,
                minimumSize: const Size.fromHeight(60),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
              ),
              child: const Text('Si me acuesto ahora...', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            ),
          ),
          
          const SizedBox(height: 32),
          if (_wakeTimes.isNotEmpty)
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                itemCount: _wakeTimes.length,
                itemBuilder: (ctx, i) {
                  int cycles = 6 - i;
                  DateTime time = _wakeTimes[i];
                  String formattedTime = DateFormat('hh:mm a').format(time);
                  
                  return Container(
                    margin: const EdgeInsets.only(bottom: 12),
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: i == 0 || i == 1 ? const Color(0xFF556B2F).withOpacity(0.1) : Colors.white,
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: i == 0 || i == 1 ? const Color(0xFF556B2F) : Colors.grey.shade200),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(formattedTime, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: Color(0xFF2C3539))),
                            const SizedBox(height: 4),
                            Text('$cycles ciclos (${cycles * 1.5} horas)', style: const TextStyle(color: Colors.black54, fontSize: 14)),
                          ],
                        ),
                        if (i == 0 || i == 1)
                          const Text('Recomendado', style: TextStyle(color: Color(0xFF556B2F), fontWeight: FontWeight.bold, fontSize: 12)),
                        Icon(CupertinoIcons.alarm, color: i == 0 || i == 1 ? const Color(0xFF556B2F) : Colors.grey),
                      ],
                    ),
                  );
                },
              ),
            )
          else
            const Expanded(
              child: Center(
                child: Icon(CupertinoIcons.moon_stars, size: 64, color: Colors.black12),
              ),
            )
        ],
      ),
    );
  }
}
