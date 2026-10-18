package com.vmdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vmdroid.MainViewModel
import com.vmdroid.data.Arch
import com.vmdroid.data.Preset
import com.vmdroid.data.VmConfig
import java.util.UUID

@Composable
private fun <T> Picker(label: String, value: String, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label : $value")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (title, v) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { open = false; onPick(v) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmEditScreen(vm: MainViewModel, id: String?, onDone: () -> Unit) {
    val existing = remember(id) { id?.let { vm.store.get(it) } }
    val diskExists = existing?.let { vm.store.diskFile(it.id).exists() } == true

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var preset by remember { mutableStateOf(existing?.preset ?: Preset.ANDROID_X86) }
    var arch by remember { mutableStateOf(existing?.arch ?: preset.arch) }
    var ram by remember { mutableFloatStateOf((existing?.ramMb ?: preset.ram).toFloat()) }
    var cpus by remember { mutableFloatStateOf((existing?.cpus ?: preset.cpus).toFloat()) }
    var diskGb by remember { mutableStateOf((existing?.diskGb ?: preset.disk).toString()) }
    var iso by remember { mutableStateOf(existing?.isoName) }
    var bootIso by remember { mutableStateOf(existing?.bootFromIso ?: true) }
    var network by remember { mutableStateOf(existing?.network ?: true) }
    var forwards by remember { mutableStateOf(existing?.portForwards ?: "") }
    var extra by remember { mutableStateOf(existing?.extraArgs ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val isoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importIso(uri) { n -> iso = n }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (existing == null) "Nouvelle machine" else "Modifier la machine") },
            navigationIcon = {
                IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            },
            actions = {
                TextButton(onClick = {
                    val gb = diskGb.toIntOrNull()
                    when {
                        name.isBlank() -> error = "Donnez un nom à la machine"
                        gb == null || gb < 1 || gb > 512 -> error = "Taille de disque invalide (1 à 512 Go)"
                        else -> {
                            vm.save(
                                VmConfig(
                                    id = existing?.id ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    preset = preset,
                                    arch = arch,
                                    ramMb = ram.toInt(),
                                    cpus = cpus.toInt(),
                                    diskGb = gb,
                                    isoName = iso,
                                    bootFromIso = bootIso,
                                    vncDisplay = existing?.vncDisplay ?: vm.store.nextVncDisplay(),
                                    network = network,
                                    portForwards = forwards,
                                    extraArgs = extra
                                )
                            )
                            onDone()
                        }
                    }
                }) { Text("Enregistrer") }
            }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }

            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Nom") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Picker("Type", preset.label, Preset.values().map { it.label to it }) { p ->
                preset = p
                arch = p.arch
                ram = p.ram.toFloat()
                cpus = p.cpus.toFloat()
                if (!diskExists) diskGb = p.disk.toString()
            }

            Picker("Architecture", arch.label, Arch.values().map { it.label to it }) { arch = it }

            Text("Mémoire : ${ram.toInt()} Mo")
            Slider(value = ram, onValueChange = { ram = (it / 256).toInt() * 256f }, valueRange = 256f..8192f, steps = 30)

            Text("Cœurs : ${cpus.toInt()}")
            Slider(value = cpus, onValueChange = { cpus = it.toInt().toFloat() }, valueRange = 1f..8f, steps = 6)

            OutlinedTextField(
                value = diskGb, onValueChange = { diskGb = it.filter(Char::isDigit) },
                label = { Text("Disque (Go)") }, singleLine = true, enabled = !diskExists,
                supportingText = if (diskExists) ({ Text("Le disque existe déjà, sa taille ne peut plus changer.") }) else null,
                modifier = Modifier.fillMaxWidth()
            )

            Picker(
                "Image ISO", iso ?: "aucune",
                listOf("Aucune" to (null as String?)) + vm.isoList.map { it to (it as String?) }
            ) { iso = it }
            OutlinedButton(onClick = { isoPicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Importer un ISO…")
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("Démarrer depuis l'ISO")
                Switch(checked = bootIso, onCheckedChange = { bootIso = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("Réseau (Internet)")
                Switch(checked = network, onCheckedChange = { network = it })
            }

            OutlinedTextField(
                value = forwards, onValueChange = { forwards = it },
                label = { Text("Redirections de ports hôte:invité (ex. 2222:22)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = extra, onValueChange = { extra = it },
                label = { Text("Arguments QEMU supplémentaires (avancé)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
