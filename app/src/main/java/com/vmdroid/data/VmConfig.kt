package com.vmdroid.data

import org.json.JSONObject

enum class Arch(val label: String, val binary: String) {
    X86_64("PC 64 bits (x86_64)", "libqemu-system-x86_64.so"),
    I386("PC 32 bits (i386)", "libqemu-system-i386.so"),
    ARM64("ARM 64 bits (aarch64)", "libqemu-system-aarch64.so")
}

enum class Preset(val label: String, val arch: Arch, val ram: Int, val cpus: Int, val disk: Int) {
    ANDROID_X86("Android-x86 / Bliss OS", Arch.X86_64, 3072, 2, 16),
    LINUX("Linux (Debian, Ubuntu…)", Arch.X86_64, 2048, 2, 16),
    WINDOWS("Windows 10 / 7", Arch.X86_64, 4096, 2, 40),
    LINUX_ARM("Linux ARM64", Arch.ARM64, 2048, 2, 16),
    LEGACY("Ancien PC 32 bits (XP, Linux léger)", Arch.I386, 512, 1, 8),
    CUSTOM("Personnalisé", Arch.X86_64, 2048, 2, 16)
}

data class VmConfig(
    val id: String,
    val name: String,
    val preset: Preset,
    val arch: Arch,
    val ramMb: Int,
    val cpus: Int,
    val diskGb: Int,
    val isoName: String?,
    val bootFromIso: Boolean,
    val vncDisplay: Int,
    val network: Boolean,
    val portForwards: String,
    val extraArgs: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("preset", preset.name)
        put("arch", arch.name)
        put("ramMb", ramMb)
        put("cpus", cpus)
        put("diskGb", diskGb)
        put("isoName", isoName ?: JSONObject.NULL)
        put("bootFromIso", bootFromIso)
        put("vncDisplay", vncDisplay)
        put("network", network)
        put("portForwards", portForwards)
        put("extraArgs", extraArgs)
    }

    companion object {
        fun fromJson(o: JSONObject) = VmConfig(
            id = o.getString("id"),
            name = o.getString("name"),
            preset = runCatching { Preset.valueOf(o.getString("preset")) }.getOrDefault(Preset.CUSTOM),
            arch = runCatching { Arch.valueOf(o.getString("arch")) }.getOrDefault(Arch.X86_64),
            ramMb = o.optInt("ramMb", 2048),
            cpus = o.optInt("cpus", 2),
            diskGb = o.optInt("diskGb", 16),
            isoName = if (o.isNull("isoName")) null else o.getString("isoName"),
            bootFromIso = o.optBoolean("bootFromIso", true),
            vncDisplay = o.optInt("vncDisplay", 1),
            network = o.optBoolean("network", true),
            portForwards = o.optString("portForwards", ""),
            extraArgs = o.optString("extraArgs", "")
        )
    }
}
