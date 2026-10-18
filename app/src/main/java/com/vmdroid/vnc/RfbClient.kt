package com.vmdroid.vnc

import android.graphics.Bitmap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Client VNC (RFB 3.8) minimaliste : sécurité « None », encodages Raw + CopyRect + DesktopSize. */
class RfbClient(
    private val host: String,
    private val port: Int,
    private val listener: Listener
) {
    interface Listener {
        fun onStatus(text: String)
        fun onFrame()
        fun onClosed(reason: String)
    }

    val lock = Any()

    @Volatile
    var bitmap: Bitmap? = null
        private set

    @Volatile
    var lastX = 0

    @Volatile
    var lastY = 0

    @Volatile
    private var closed = false
    private var socket: Socket? = null
    private var out: DataOutputStream? = null
    private val sender = Executors.newSingleThreadExecutor()
    private var fbW = 0
    private var fbH = 0

    fun start() {
        Thread({ runLoop() }, "rfb").start()
    }

    fun close() {
        closed = true
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        sender.shutdownNow()
    }

    private fun runLoop() {
        var reason = "Connexion fermée"
        try {
            var s: Socket? = null
            var attempt = 0
            while (!closed && s == null && attempt < 40) {
                try {
                    val k = Socket()
                    k.connect(InetSocketAddress(host, port), 2000)
                    s = k
                } catch (e: IOException) {
                    attempt++
                    listener.onStatus("Démarrage de la machine… ($attempt)")
                    Thread.sleep(1000)
                }
            }
            if (s == null) {
                reason = "Impossible de se connecter à l'écran de la VM"
                return
            }
            socket = s
            s.tcpNoDelay = true
            val inp = DataInputStream(BufferedInputStream(s.getInputStream(), 1 shl 16))
            val o = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
            out = o

            // Poignée de main
            inp.readFully(ByteArray(12))
            o.write("RFB 003.008\n".toByteArray())
            o.flush()
            val n = inp.readUnsignedByte()
            if (n == 0) {
                val msg = ByteArray(inp.readInt())
                inp.readFully(msg)
                throw IOException(String(msg))
            }
            val types = ByteArray(n)
            inp.readFully(types)
            if (!types.contains(1)) throw IOException("Authentification VNC non supportée")
            o.writeByte(1)
            o.flush()
            if (inp.readInt() != 0) throw IOException("Connexion refusée")
            o.writeByte(1) // ClientInit : écran partagé
            o.flush()

            // ServerInit
            val w = inp.readUnsignedShort()
            val h = inp.readUnsignedShort()
            inp.readFully(ByteArray(16))
            inp.readFully(ByteArray(inp.readInt()))
            allocate(w, h)

            // SetPixelFormat : 32 bits, little endian, 0x00RRGGBB
            synchronized(o) {
                o.writeByte(0); o.write(ByteArray(3))
                o.writeByte(32); o.writeByte(24); o.writeByte(0); o.writeByte(1)
                o.writeShort(255); o.writeShort(255); o.writeShort(255)
                o.writeByte(16); o.writeByte(8); o.writeByte(0); o.write(ByteArray(3))
                // SetEncodings : Raw, CopyRect, DesktopSize
                o.writeByte(2); o.writeByte(0); o.writeShort(3)
                o.writeInt(0); o.writeInt(1); o.writeInt(-223)
                o.flush()
            }
            requestUpdate(false)
            listener.onStatus("Connecté")

            while (!closed) {
                when (val t = inp.readUnsignedByte()) {
                    0 -> {
                        inp.readByte()
                        val nr = inp.readUnsignedShort()
                        var resized = false
                        repeat(nr) { if (readRect(inp)) resized = true }
                        listener.onFrame()
                        requestUpdate(!resized)
                    }
                    1 -> {
                        inp.readByte(); inp.readUnsignedShort()
                        val nc = inp.readUnsignedShort()
                        inp.readFully(ByteArray(nc * 6))
                    }
                    2 -> {}
                    3 -> {
                        inp.readFully(ByteArray(3))
                        inp.readFully(ByteArray(inp.readInt()))
                    }
                    else -> throw IOException("Message VNC inconnu ($t)")
                }
            }
        } catch (e: Exception) {
            reason = if (closed) "Fermé" else (e.message ?: "Connexion interrompue")
        } finally {
            if (!closed) listener.onClosed(reason)
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun allocate(w: Int, h: Int) {
        fbW = w
        fbH = h
        synchronized(lock) { bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
    }

    /** Retourne true si l'écran a été redimensionné. */
    private fun readRect(inp: DataInputStream): Boolean {
        val x = inp.readUnsignedShort()
        val y = inp.readUnsignedShort()
        val rw = inp.readUnsignedShort()
        val rh = inp.readUnsignedShort()
        when (val enc = inp.readInt()) {
            0 -> {
                val buf = ByteArray(rw * rh * 4)
                inp.readFully(buf)
                val px = IntArray(rw * rh)
                ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(px)
                for (i in px.indices) px[i] = px[i] or -16777216
                synchronized(lock) {
                    val b = bitmap
                    if (b != null && x + rw <= b.width && y + rh <= b.height) b.setPixels(px, 0, rw, x, y, rw, rh)
                }
            }
            1 -> {
                val sx = inp.readUnsignedShort()
                val sy = inp.readUnsignedShort()
                synchronized(lock) {
                    val b = bitmap
                    if (b != null && sx + rw <= b.width && sy + rh <= b.height &&
                        x + rw <= b.width && y + rh <= b.height
                    ) {
                        val px = IntArray(rw * rh)
                        b.getPixels(px, 0, rw, sx, sy, rw, rh)
                        b.setPixels(px, 0, rw, x, y, rw, rh)
                    }
                }
            }
            -223 -> {
                allocate(rw, rh)
                return true
            }
            else -> throw IOException("Encodage VNC non supporté ($enc)")
        }
        return false
    }

    private fun requestUpdate(incremental: Boolean) {
        val o = out ?: return
        synchronized(o) {
            o.writeByte(3); o.writeByte(if (incremental) 1 else 0)
            o.writeShort(0); o.writeShort(0); o.writeShort(fbW); o.writeShort(fbH)
            o.flush()
        }
    }

    private fun send(block: DataOutputStream.() -> Unit) {
        val o = out ?: return
        try {
            sender.execute {
                try {
                    synchronized(o) { o.block(); o.flush() }
                } catch (_: IOException) {
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    /** mask : bit0 = gauche, bit1 = milieu, bit2 = droit, bit3 = molette haut, bit4 = molette bas. */
    fun pointer(x: Int, y: Int, mask: Int) {
        lastX = x; lastY = y
        send { writeByte(5); writeByte(mask); writeShort(x); writeShort(y) }
    }

    fun key(keysym: Int, down: Boolean) {
        send { writeByte(4); writeByte(if (down) 1 else 0); writeShort(0); writeInt(keysym) }
    }
}
