package com.vmdroid.engine

import android.content.Context
import android.os.Build
import com.vmdroid.data.Arch
import com.vmdroid.data.VmConfig
import com.vmdroid.data.VmStore
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.ZipInputStream

object QemuEngine {

    private fun nativeDir(ctx: Context) = File(ctx.applicationInfo.nativeLibraryDir)

    fun binary(ctx: Context, arch: Arch) = File(nativeDir(ctx), arch.binary)
    fun qemuImg(ctx: Context) = File(nativeDir(ctx), "libqemu-img.so")
    fun isAvailable(ctx: Context, arch: Arch) = binary(ctx, arch).exists()
    fun anyAvailable(ctx: Context) = Arch.values().any { isAvailable(ctx, it) }

    fun shareDir(ctx: Context) = File(ctx.filesDir, "qemu-share").also { it.mkdirs() }
    fun shareInstalled(ctx: Context) = (shareDir(ctx).list()?.size ?: 0) > 0

    fun vncPort(vm: VmConfig) = 5900 + vm.vncDisplay

    /** Extrait assets/qemu-share.zip (si présent) au premier lancement. */
    fun ensureShare(ctx: Context) {
        if (shareInstalled(ctx)) return
        if (ctx.assets.list("")?.contains("qemu-share.zip") != true) return
        try {
            ctx.assets.open("qemu-share.zip").use { extractZip(it, shareDir(ctx)) }
        } catch (_: Exception) {
        }
    }

    fun extractZip(input: InputStream, dest: File) {
        val root = dest.canonicalPath
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val out = File(dest, e.name)
                if (!out.canonicalPath.startsWith(root + File.separator)) continue // protection zip-slip
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }

    fun diskFormat(f: File): String = try {
        f.inputStream().use { s ->
            val b = ByteArray(4)
            if (s.read(b) == 4 && b[0] == 'Q'.code.toByte() && b[1] == 'F'.code.toByte() &&
                b[2] == 'I'.code.toByte() && b[3] == 0xFB.toByte()
            ) "qcow2" else "raw"
        }
    } catch (e: Exception) {
        "raw"
    }

    fun createDisk(ctx: Context, store: VmStore, vm: VmConfig) {
        val f = store.diskFile(vm.id)
        val img = qemuImg(ctx)
        if (img.exists()) {
            val p = ProcessBuilder(img.absolutePath, "create", "-f", "qcow2", f.absolutePath, "${vm.diskGb}G")
                .redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            if (p.waitFor() == 0) return
            throw IOException("qemu-img : $text")
        }
        // Repli : fichier brut « sparse » (n'occupe l'espace que progressivement).
        RandomAccessFile(f, "rw").use { it.setLength(vm.diskGb * 1024L * 1024L * 1024L) }
    }

    private fun kvmUsable(vm: VmConfig): Boolean =
        vm.arch == Arch.ARM64 && Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a" &&
            File("/dev/kvm").let { it.canRead() && it.canWrite() }

    fun buildCommand(ctx: Context, store: VmStore, vm: VmConfig): List<String> {
        val bin = binary(ctx, vm.arch)
        val share = shareDir(ctx)
        val disk = store.diskFile(vm.id)
        val fmt = diskFormat(disk)
        val iso = vm.isoName?.let { store.isoFile(it) }?.takeIf { it.exists() }
        val kvm = kvmUsable(vm)

        val a = mutableListOf(
            bin.absolutePath, "-L", share.absolutePath,
            "-name", vm.name,
            "-m", vm.ramMb.toString(),
            "-smp", vm.cpus.toString()
        )

        val fastDisk = vm.preset.name in listOf("ANDROID_X86", "LINUX", "LINUX_ARM")
        val ifType = if (fastDisk || vm.arch == Arch.ARM64) "virtio" else "ide"

        when (vm.arch) {
            Arch.X86_64, Arch.I386 -> {
                a += listOf("-machine", "pc", "-cpu", "max", "-accel", "tcg,tb-size=256")
                a += listOf("-vga", "std", "-usb", "-device", "usb-tablet")
                a += listOf("-drive", "file=${disk.absolutePath},format=$fmt,if=$ifType,cache=writeback")
                if (iso != null) a += listOf("-cdrom", iso.absolutePath)
                a += listOf("-boot", if (vm.bootFromIso && iso != null) "order=d,menu=on" else "order=c,menu=on")
            }
            Arch.ARM64 -> {
                a += listOf("-machine", "virt", "-cpu", if (kvm) "host" else "cortex-a72")
                a += if (kvm) listOf("-accel", "kvm") else listOf("-accel", "tcg,tb-size=256")
                a += listOf("-bios", File(share, "edk2-aarch64-code.fd").absolutePath)
                a += listOf("-device", "virtio-gpu-pci", "-device", "qemu-xhci", "-device", "usb-kbd", "-device", "usb-tablet")
                a += listOf("-drive", "file=${disk.absolutePath},format=$fmt,if=virtio,cache=writeback")
                if (iso != null) {
                    a += listOf("-drive", "file=${iso.absolutePath},media=cdrom,if=none,id=cd0,readonly=on")
                    a += listOf("-device", "usb-storage,drive=cd0,bootindex=0")
                }
            }
        }

        if (vm.network) {
            val fwds = vm.portForwards.split(",", ";", " ").mapNotNull { s ->
                val p = s.trim().split(":")
                if (p.size == 2 && p[0].toIntOrNull() != null && p[1].toIntOrNull() != null)
                    ",hostfwd=tcp::${p[0]}-:${p[1]}" else null
            }.joinToString("")
            val nic = if (vm.arch == Arch.ARM64) "virtio-net-pci" else "e1000"
            a += listOf("-netdev", "user,id=n0$fwds", "-device", "$nic,netdev=n0")
        }

        a += listOf("-display", "none", "-vnc", "127.0.0.1:${vm.vncDisplay}", "-monitor", "none", "-serial", "none")

        if (vm.extraArgs.isNotBlank()) a += vm.extraArgs.trim().split(Regex("\\s+"))
        return a
    }
}
