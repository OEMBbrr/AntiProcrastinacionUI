package com.antiprocrastinacion.lock

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64

/**
 * Servidor embebido en Android para Conexión LAN Directa (Wi-Fi Local).
 * Escucha en el Puerto 8888 para detección instantánea por la Extensión de Chrome.
 *
 * V24 (Propuesta 4): además del endpoint HTTP JSON (compatibilidad con fetch),
 * soporta WebSocket (ws://IP:8888) para comunicaciones sub-10ms:
 *   - El cliente recibe el estado actual al conectar y en cada mensaje.
 *   - El servidor responde Pong a los Pings del navegador.
 */
class LanServer(
    private val context: Context,
    private val lockManager: LockManager
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var lastLanPingTime: Long = 0
    val isLanActive: Boolean
        get() = isRunning && (System.currentTimeMillis() - lastLanPingTime) < 15000

    fun start() {
        if (isRunning) return
        isRunning = true
        serverJob = scope.launch {
            try {
                // Configurar el socket para que reutilice la dirección y evite errores "Address already in use"
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(java.net.InetSocketAddress("0.0.0.0", 8888))
                serverSocket = socket

                Log.d("ZEN_LAN", "Servidor LAN iniciado en Puerto 8888 (HTTP + WebSocket)")

                while (isRunning && serverSocket?.isClosed == false) {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client)
                }
            } catch (e: Exception) {
                Log.e("ZEN_LAN", "Error en Servidor LAN: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverJob?.cancel()
    }

    private fun buildStatusJson(): JSONObject = JSONObject().apply {
        put("status", "ok")
        put("brand", lockManager.deviceBrand)
        put("model", lockManager.deviceModel)
        put("device_pin", lockManager.devicePin)
        put("user_email", lockManager.googleUserEmail)
        put("target_key", lockManager.targetKey)
        put("is_locked", lockManager.isLocked)
        put("remaining_seconds", lockManager.timeRemaining / 1000)
        put("is_lan", true)
        put("pomodoro_enabled", lockManager.pomodoroEnabled)
        put("pomodoro_work_minutes", lockManager.pomodoroWorkMinutes)
        put("pomodoro_rest_minutes", lockManager.pomodoroRestMinutes)
        put("pomodoro_rest_count", lockManager.pomodoroRestCount)
        put("phases", JSONArray().apply {
            lockManager.pomodoroPhases.forEach { p ->
                put(JSONObject().apply {
                    put("type", p.type)
                    put("start_ms", p.startTime)
                    put("end_ms", p.endTime)
                })
            }
        })
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                val input = socket.getInputStream()
                val out: OutputStream = socket.getOutputStream()

                val requestLineBytes = readLineBytes(input) ?: return@launch
                lastLanPingTime = System.currentTimeMillis()
                val requestLine = String(requestLineBytes, Charsets.UTF_8)

                // Leer cabeceras de la petición
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLineBytes(input) ?: break
                    if (line.isEmpty()) break
                    val text = String(line, Charsets.UTF_8)
                    val idx = text.indexOf(':')
                    if (idx > 0) {
                        headers[text.substring(0, idx).trim().lowercase()] = text.substring(idx + 1).trim()
                    }
                }

                // V24 (Propuesta 4): handshake WebSocket si el cliente lo solicita
                val upgrade = headers["upgrade"]?.lowercase()
                val connection = headers["connection"]?.lowercase()
                if (upgrade == "websocket" && connection?.contains("upgrade") == true) {
                    val key = headers["sec-websocket-key"]
                    if (key != null) {
                        doWebSocketHandshake(out, key)
                        handleWebSocket(socket, input, out)
                        return@launch
                    }
                }

                // Ruta HTTP normal (compatibilidad con fetch de la extensión)
                val jsonResponse = buildStatusJson().toString()

                val responseHeader = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                        "Content-Length: ${jsonResponse.toByteArray(Charsets.UTF_8).size}\r\n" +
                        "Connection: close\r\n\r\n"

                out.write(responseHeader.toByteArray(Charsets.UTF_8))
                out.write(jsonResponse.toByteArray(Charsets.UTF_8))
                out.flush()
            } catch (e: Exception) {
                Log.e("ZEN_LAN", "Error procesando cliente LAN: ${e.message}")
            } finally {
                try {
                    socket.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun readLineBytes(input: InputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buffer.size() > 0) buffer.toByteArray() else null
            if (b == '\n'.code) {
                var bytes = buffer.toByteArray()
                if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) {
                    bytes = bytes.copyOf(bytes.size - 1)
                }
                return bytes
            }
            buffer.write(b)
        }
    }

    // ============================================================================
    // V24 (Propuesta 4): WebSocket
    // ============================================================================

    private fun doWebSocketHandshake(out: OutputStream, key: String) {
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
        )
        val header = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun handleWebSocket(socket: Socket, input: InputStream, out: OutputStream) {
        try {
            socket.soTimeout = 60000
            // Enviar el estado actual al cliente recién conectado
            sendFrame(out, 0x1, buildStatusJson().toString().toByteArray(Charsets.UTF_8))
            while (isRunning && !socket.isClosed) {
                val b0 = input.read()
                if (b0 < 0) break
                val b1 = input.read()
                if (b1 < 0) break
                val opcode = b0 and 0x0F
                val masked = (b1 and 0x80) != 0
                var len = b1 and 0x7F
                when (len) {
                    126 -> {
                        val l1 = input.read()
                        val l2 = input.read()
                        if (l1 < 0 || l2 < 0) return
                        len = (l1 shl 8) or l2
                    }
                    127 -> {
                        var l = 0L
                        for (i in 0 until 8) {
                            val b = input.read()
                            if (b < 0) return
                            l = (l shl 8) or (b.toLong() and 0xFF)
                        }
                        len = l.toInt()
                    }
                }
                if (len < 0 || len > 1_000_000) return

                val mask = ByteArray(4)
                if (masked) {
                    var read = 0
                    while (read < 4) {
                        val r = input.read(mask, read, 4 - read)
                        if (r < 0) return
                        read += r
                    }
                }

                val payload = ByteArray(len)
                var total = 0
                while (total < len) {
                    val r = input.read(payload, total, len - total)
                    if (r < 0) return
                    total += r
                }
                if (masked) {
                    for (i in payload.indices) {
                        payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                    }
                }

                when (opcode) {
                    0x8 -> { // Close
                        sendFrame(out, 0x8, ByteArray(0))
                        return
                    }
                    0x9 -> { // Ping -> Pong
                        sendFrame(out, 0xA, ByteArray(0))
                    }
                    0x1 -> { // Texto: actualizar estado y responder con el estado en vivo
                        lastLanPingTime = System.currentTimeMillis()
                        val message = String(payload, Charsets.UTF_8)
                        Log.d("ZEN_LAN", "WS mensaje recibido: $message")
                        sendFrame(out, 0x1, buildStatusJson().toString().toByteArray(Charsets.UTF_8))
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("ZEN_LAN", "Conexión WebSocket cerrada: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun sendFrame(out: OutputStream, opcode: Int, payload: ByteArray) {
        val header = ByteArrayOutputStream()
        header.write(0x80 or opcode) // FIN + opcode
        val len = payload.size
        when {
            len < 126 -> header.write(len)
            len < 65536 -> {
                header.write(126)
                header.write((len shr 8) and 0xFF)
                header.write(len and 0xFF)
            }
            else -> {
                header.write(127)
                var l = len.toLong()
                for (i in 7 downTo 0) {
                    header.write(((l shr (8 * i)) and 0xFF).toInt())
                }
            }
        }
        out.write(header.toByteArray())
        if (payload.isNotEmpty()) out.write(payload)
        out.flush()
    }
}
