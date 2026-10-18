package com.vmdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vmdroid.MainViewModel
import com.vmdroid.data.VmConfig
import com.vmdroid.engine.QemuEngine
import com.vmdroid.engine.VmService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmListScreen(
    vm: MainViewModel,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onConsole: (String) -> Unit
) {
    val ctx = LocalContext.current
    val running by VmService.running.collectAsState()
    var logsFor by remember { mutableStateOf<VmConfig?>(null) }
    var deleteFor by remember { mutableStateOf<VmConfig?>(null) }
    val sharePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importShare(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VMDroid") },
                actions = {
                    IconButton(onClick = { sharePicker.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Importer les fichiers du moteur (zip)")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Icon(Icons.Filled.Add, contentDescription = "Nouvelle machine") }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!QemuEngine.anyAvailable(ctx)) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            "Moteur QEMU introuvable : placez les binaires libqemu-system-*.so dans app/src/main/jniLibs/arm64-v8a/ " +
                                "puis recompilez (voir le README).",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            if (!vm.shareOk) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(
                            "Fichiers du moteur (BIOS / ROM) non installés : importez le zip du dossier « share » de QEMU " +
                                "avec le bouton dossier en haut.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            if (vm.vms.isEmpty()) {
                item {
                    Text(
                        "Aucune machine. Appuyez sur + pour en créer une (Android-x86, Linux, Windows…).",
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
            items(vm.vms, key = { it.id }) { c ->
                val isRunning = c.id in running
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${c.preset.label} · ${c.arch.label}\n${c.ramMb} Mo RAM · ${c.cpus} cœur(s) · disque ${c.diskGb} Go" +
                                (c.isoName?.let { "\nISO : $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (!QemuEngine.isAvailable(ctx, c.arch)) {
                            Text(
                                "Moteur ${c.arch.binary} absent",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (isRunning) {
                                IconButton(onClick = { onConsole(c.id) }) {
                                    Icon(Icons.Filled.Terminal, contentDescription = "Écran")
                                }
                                IconButton(onClick = { VmService.stop(ctx, c.id) }) {
                                    Icon(Icons.Filled.Stop, contentDescription = "Arrêter")
                                }
                            } else {
                                IconButton(onClick = {
                                    VmService.start(ctx, c.id)
                                    onConsole(c.id)
                                }) { Icon(Icons.Filled.PlayArrow, contentDescription = "Démarrer") }
                                IconButton(onClick = { onEdit(c.id) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Modifier")
                                }
                            }
                            IconButton(onClick = { logsFor = c }) {
                                Icon(Icons.Filled.Description, contentDescription = "Journaux")
                            }
                            if (!isRunning) {
                                IconButton(onClick = { deleteFor = c }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    logsFor?.let { c ->
        val text = remember(c.id) {
            vm.store.logFile(c.id).takeIf { it.exists() }?.readLines()?.takeLast(200)?.joinToString("\n")
                ?: "(aucun journal)"
        }
        AlertDialog(
            onDismissRequest = { logsFor = null },
            confirmButton = { TextButton(onClick = { logsFor = null }) { Text("Fermer") } },
            title = { Text("Journal – ${c.name}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        )
    }

    deleteFor?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Supprimer « ${c.name} » ?") },
            text = { Text("Le disque virtuel de cette machine sera effacé définitivement.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(c); deleteFor = null }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Annuler") } }
        )
    }
}
