import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';

class ZenTasksScreen extends StatefulWidget {
  const ZenTasksScreen({super.key});

  @override
  State<ZenTasksScreen> createState() => _ZenTasksScreenState();
}

class _Task {
  String title;
  bool isCompleted;
  _Task({required this.title, this.isCompleted = false});
}

class _ZenTasksScreenState extends State<ZenTasksScreen> {
  final TextEditingController _controller = TextEditingController();
  final List<_Task> _tasks = [
    _Task(title: "Terminar informe de ventas", isCompleted: false),
    _Task(title: "Llamar al seguro", isCompleted: true),
  ];

  void _addTask() {
    if (_controller.text.trim().isNotEmpty) {
      setState(() {
        _tasks.insert(0, _Task(title: _controller.text.trim()));
        _controller.clear();
      });
    }
  }

  void _toggleTask(int index) {
    setState(() {
      _tasks[index].isCompleted = !_tasks[index].isCompleted;
      // Move completed to bottom optionally, but for now just toggle
    });
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
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 12),
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(color: Colors.grey.shade300, borderRadius: BorderRadius.circular(2)),
            ),
          ),
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('Tareas', style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold, letterSpacing: -0.5, color: Color(0xFF2C3539))),
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
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                boxShadow: [
                  BoxShadow(color: Colors.black.withOpacity(0.03), blurRadius: 10, offset: const Offset(0, 4)),
                ],
              ),
              child: Row(
                children: [
                  const Icon(CupertinoIcons.circle, color: Colors.grey, size: 22),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextField(
                      controller: _controller,
                      textInputAction: TextInputAction.done,
                      onSubmitted: (_) => _addTask(),
                      decoration: InputDecoration(
                        hintText: 'Añadir nueva tarea...',
                        hintStyle: TextStyle(color: Colors.grey.shade400, fontSize: 16),
                        border: InputBorder.none,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              itemCount: _tasks.length,
              itemBuilder: (ctx, i) {
                final task = _tasks[i];
                return GestureDetector(
                  onTap: () => _toggleTask(i),
                  child: Container(
                    margin: const EdgeInsets.only(bottom: 12),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: Colors.grey.shade200),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          task.isCompleted ? CupertinoIcons.check_mark_circled_solid : CupertinoIcons.circle,
                          color: task.isCompleted ? const Color(0xFF556B2F) : Colors.grey,
                          size: 24,
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Text(
                            task.title,
                            style: TextStyle(
                              fontSize: 16,
                              color: task.isCompleted ? Colors.grey : const Color(0xFF333333),
                              decoration: task.isCompleted ? TextDecoration.lineThrough : null,
                            ),
                          ),
                        ),
                      ],
                    ),
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
