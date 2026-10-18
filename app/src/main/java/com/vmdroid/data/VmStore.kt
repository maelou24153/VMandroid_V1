package com.vmdroid.data

import android.content.Context
import org.json.JSONArray
import java.io.File

class VmStore(private val ctx: Context) {
    private val file = File(ctx.filesDir, "vms.json")

    @Synchronized
    fun list(): List<VmConfig> = try {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { VmConfig.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(l: List<VmConfig>) {
        file.writeText(JSONArray(l.map { it.toJson() }).toString())
    }

    fun get(id: String): VmConfig? = list().firstOrNull { it.id == id }

    @Synchronized
    fun save(c: VmConfig) {
        val l = list().toMutableList()
        val i = l.indexOfFirst { it.id == c.id }
        if (i >= 0) l[i] = c else l.add(c)
        write(l)
    }

    @Synchronized
    fun delete(id: String) {
        write(list().filter { it.id != id })
        File(ctx.filesDir, "vms/$id").deleteRecursively()
    }

    fun vmDir(id: String): File = File(ctx.filesDir, "vms/$id").also { it.mkdirs() }
    fun diskFile(id: String): File = File(vmDir(id), "disk.img")
    fun logFile(id: String): File = File(vmDir(id), "qemu.log")

    /** Dossier des ISO (visible depuis un gestionnaire de fichiers : Android/data/com.vmdroid/files/isos). */
    fun isoDir(): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "isos").also { it.mkdirs() }
    fun isoFile(name: String): File = File(isoDir(), name)

    fun nextVncDisplay(): Int = (list().maxOfOrNull { it.vncDisplay } ?: 0) + 1
}
