package com.vmdroid

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vmdroid.engine.QemuEngine
import com.vmdroid.engine.VmService
import com.vmdroid.ui.ConsoleScreen
import com.vmdroid.ui.VmDroidTheme
import com.vmdroid.ui.VmEditScreen
import com.vmdroid.ui.VmListScreen

sealed interface Screen {
    data object List : Screen
    data class Edit(val id: String?) : Screen
    data class Console(val id: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Thread { QemuEngine.ensureShare(this) }.start()
        setContent { VmDroidTheme { App() } }
    }
}

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.List) }

    LaunchedEffect(Unit) { vm.toasts.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }
    LaunchedEffect(Unit) { VmService.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }

    BackHandler(enabled = screen != Screen.List) { screen = Screen.List }

    when (val s = screen) {
        Screen.List -> VmListScreen(
            vm = vm,
            onNew = { screen = Screen.Edit(null) },
            onEdit = { screen = Screen.Edit(it) },
            onConsole = { screen = Screen.Console(it) }
        )
        is Screen.Edit -> VmEditScreen(vm = vm, id = s.id, onDone = { screen = Screen.List })
        is Screen.Console -> {
            val cfg = vm.store.get(s.id)
            if (cfg == null) screen = Screen.List else ConsoleScreen(cfg, onBack = { screen = Screen.List })
        }
    }

    vm.busy?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Veuillez patienter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(msg, modifier = Modifier.padding(top = 4.dp))
                }
            }
        )
    }
}
