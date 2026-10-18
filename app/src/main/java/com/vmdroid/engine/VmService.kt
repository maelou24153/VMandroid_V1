package com.vmdroid.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vmdroid.data.VmStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class VmService : Service() {

    companion object {
        private const val ACTION_START = "com.vmdroid.START"
        private const val ACTION_STOP = "com.vmdroid.STOP"
        private const val EXTRA_ID = "id"
        private const val CHANNEL = "vm"
        private const val NOTIF_ID = 1

        val running = MutableStateFlow<Set<String>>(emptySet())
        val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

        fun start(ctx: Context, id: String) {
            val i = Intent(ctx, VmService::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, id)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context, id: String) {
            val i = Intent(ctx, VmService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_ID, id)
            ctx.startForegroundService(i)
        }
    }

    private val procs = ConcurrentHashMap<String, Process>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, notification())
        val id = intent?.getStringExtra(EXTRA_ID)
        when (intent?.action) {
            ACTION_START -> if (id != null) launchVm(id)
            ACTION_STOP -> if (id != null) stopVm(id)
        }
        if (procs.isEmpty() && intent?.action != ACTION_START) stopSelfIfIdle()
        return START_NOT_STICKY
    }

    private fun launchVm(id: String) {
        if (procs.containsKey(id)) return
        Thread({
            val store = VmStore(this)
            try {
                val vm = store.get(id) ?: throw IllegalStateException("Machine introuvable")
                if (!QemuEngine.isAvailable(this, vm.arch)) {
                    throw IllegalStateException(
                        "Le moteur QEMU pour « ${vm.arch.label} » est absent (${vm.arch.binary}). Voir le README."
                    )
                }
                if (!store.diskFile(id).exists()) QemuEngine.createDisk(this, store, vm)
                val cmd = QemuEngine.buildCommand(this, store, vm)
                val log = store.logFile(id)
                log.writeText("$ " + cmd.joinToString(" ") + "\n")
                val pb = ProcessBuilder(cmd)
                    .directory(filesDir)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                pb.environment()["TMPDIR"] = cacheDir.absolutePath
                pb.environment()["HOME"] = filesDir.absolutePath
                val p = pb.start()
                procs[id] = p
                running.value = running.value + id
                refreshNotification()
                val code = p.waitFor()
                log.appendText("\n[QEMU terminé, code $code]\n")
                if (code != 0 && code != 143 && code != 137) {
                    messages.tryEmit("« ${vm.name} » s'est arrêtée (code $code). Voir les journaux.")
                }
            } catch (e: Exception) {
                messages.tryEmit(e.message ?: "Erreur au démarrage")
            } finally {
                procs.remove(id)
                running.value = running.value - id
                refreshNotification()
                stopSelfIfIdle()
            }
        }, "vm-$id").start()
    }

    private fun stopVm(id: String) {
        val p = procs[id] ?: return
        p.destroy()
        Thread {
            try {
                Thread.sleep(3000)
                if (p.isAlive) p.destroyForcibly()
            } catch (_: InterruptedException) {
            }
        }.start()
    }

    private fun stopSelfIfIdle() {
        if (procs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        procs.values.forEach { it.destroyForcibly() }
        procs.clear()
        running.value = emptySet()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Machines virtuelles", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = procs.size
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("VMDroid")
            .setContentText(if (n == 0) "Démarrage…" else "$n machine(s) virtuelle(s) en cours")
            .setOngoing(true)
            .build()
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification())
    }
}
