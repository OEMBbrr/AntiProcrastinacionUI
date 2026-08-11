import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';

class ZenNotesScreen extends StatefulWidget {
  const ZenNotesScreen({super.key});

  @override
  State<ZenNotesScreen> createState() => _ZenNotesScreenState();
}

class _ZenNotesScreenState extends State<ZenNotesScreen> {
  final TextEditingController _controller = TextEditingController();
  final List<String> _notes = [
    "La paciencia es la clave del progreso.",
    "Revisar el código de iOS mañana por la mañana."
  ];

  void _addNote() {
    if (_controller.text.trim().isNotEmpty) {
      setState(() {
        _notes.insert(0, _controller.text.trim());
        _controller.clear();
      });
    }
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
                const Text('Notas', style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold, letterSpacing: -0.5, color: Color(0xFF2C3539))),
                IconButton(
                  icon: const Icon(CupertinoIcons.xmark_circle_fill, color: Colors.grey, size: 28),
                  onPressed: () => Navigator.pop(context),
                )
              ],
            ),
          ),
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24.0),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(color: Colors.black.withOpacity(0.03), blurRadius: 10, offset: const Offset(0, 4)),
                ],
              ),
              child: TextField(
                controller: _controller,
                maxLines: null,
                keyboardType: TextInputType.multiline,
                textInputAction: TextInputAction.done,
                onSubmitted: (_) => _addNote(),
                decoration: InputDecoration(
                  hintText: 'Escribe un pensamiento...',
                  hintStyle: TextStyle(color: Colors.grey.shade400),
                  border: InputBorder.none,
                  suffixIcon: IconButton(
                    icon: const Icon(CupertinoIcons.arrow_up_circle_fill, color: Color(0xFF556B2F)),
                    onPressed: _addNote,
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 24),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              itemCount: _notes.length,
              itemBuilder: (ctx, i) {
                return Container(
                  margin: const EdgeInsets.only(bottom: 12),
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: Colors.grey.shade200),
                  ),
                  child: Text(
                    _notes[i],
                    style: const TextStyle(fontSize: 16, color: Color(0xFF333333), height: 1.4),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
