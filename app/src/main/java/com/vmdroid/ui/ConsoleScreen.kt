package com.vmdroid.ui

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.collectAsState
import com.vmdroid.data.VmConfig
import com.vmdroid.engine.QemuEngine
import com.vmdroid.engine.VmService
import com.vmdroid.vnc.KeyMap
import com.vmdroid.vnc.RfbClient

private const val SENTINEL = "​"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(vm: VmConfig, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    var frame by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Connexion…") }
    var connected by remember { mutableStateOf(false) }
    var showKb by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var kbText by remember { mutableStateOf(SENTINEL) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val running by VmService.running.collectAsState()
    val kbFocus = remember { FocusRequester() }
    val boxFocus = remember { FocusRequester() }

    val client = remember {
        RfbClient("127.0.0.1", QemuEngine.vncPort(vm), object : RfbClient.Listener {
            override fun onStatus(text: String) { status = text }
            override fun onFrame() { connected = true; frame += 1 }
            override fun onClosed(reason: String) { connected = false; status = reason }
        })
    }

    DisposableEffect(client) {
        client.start()
        view.keepScreenOn = true
        onDispose {
            client.close()
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(showKb) { if (showKb) kbFocus.requestFocus() else boxFocus.requestFocus() }

    fun tap(keysym: Int) {
        if (ctrl) client.key(KeyMap.CTRL, true)
        if (alt) client.key(KeyMap.ALT, true)
        client.key(keysym, true)
        client.key(keysym, false)
        if (alt) client.key(KeyMap.ALT, false)
        if (ctrl) client.key(KeyMap.CTRL, false)
        ctrl = false
        alt = false
    }

    fun map(o: Offset): Pair<Int, Int>? {
        val b = client.bitmap ?: return null
        if (boxSize.width == 0 || boxSize.height == 0) return null
        val s = minOf(boxSize.width.toFloat() / b.width, boxSize.height.toFloat() / b.height)
        val ox = (boxSize.width - b.width * s) / 2f
        val oy = (boxSize.height - b.height * s) / 2f
        val x = ((o.x - ox) / s).toInt().coerceIn(0, b.width - 1)
        val y = ((o.y - oy) / s).toInt().coerceIn(0, b.height - 1)
        return x to y
    }

    fun click(o: Offset, mask: Int) {
        val p = map(o) ?: return
        client.pointer(p.first, p.second, mask)
        client.pointer(p.first, p.second, 0)
    }

    fun wheel(mask: Int) {
        client.pointer(client.lastX, client.lastY, mask)
        client.pointer(client.lastX, client.lastY, 0)
    }

    val stillRunning = vm.id in running

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    IconButton(onClick = { showKb = !showKb }) {
                        Icon(Icons.Filled.Keyboard, contentDescription = "Clavier")
                    }
                    IconButton(onClick = { VmService.stop(ctx, vm.id) }, enabled = stillRunning) {
                        Icon(Icons.Filled.Stop, contentDescription = "Arrêter la machine")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .clipToBounds()
                    .onSizeChanged { boxSize = it }
                    .focusRequester(boxFocus)
                    .focusable()
                    .onKeyEvent { e ->
                        val ne = e.nativeKeyEvent
                        val ks = KeyMap.fromEvent(ne)
                        if (ks != 0 && ne.action != KeyEvent.ACTION_MULTIPLE) {
                            client.key(ks, ne.action == KeyEvent.ACTION_DOWN)
                            true
                        } else false
                    }
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { click(it, 1) },
                                onLongPress = { click(it, 4) }
                            )
                        }
                        .pointerInput(Unit) {
                            var last = Offset.Zero
                            detectDragGestures(
                                onDragStart = { o ->
                                    last = o
                                    map(o)?.let { client.pointer(it.first, it.second, 1) }
                                },
                                onDrag = { change, _ ->
                                    last = change.position
                                    map(change.position)?.let { client.pointer(it.first, it.second, 1) }
                                },
                                onDragEnd = { map(last)?.let { client.pointer(it.first, it.second, 0) } },
                                onDragCancel = { map(last)?.let { client.pointer(it.first, it.second, 0) } }
                            )
                        }
                ) {
                    @Suppress("UNUSED_VARIABLE") val f = frame
                    synchronized(client.lock) {
                        val b = client.bitmap
                        if (b != null && !b.isRecycled) {
                            val s = minOf(size.width / b.width, size.height / b.height)
                            val dw = (b.width * s).toInt()
                            val dh = (b.height * s).toInt()
                            drawImage(
                                image = b.asImageBitmap(),
                                dstOffset = IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt()),
                                dstSize = IntSize(dw, dh),
                                filterQuality = FilterQuality.Low
                            )
                        }
                    }
                }
                if (!connected) {
                    Text(status, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                }
            }

            if (showKb) {
                OutlinedTextField(
                    value = kbText,
                    onValueChange = { nv ->
                        when {
                            nv.isEmpty() -> tap(KeyMap.BACKSPACE)
                            nv.length > 1 -> nv.removePrefix(SENTINEL).forEach { tap(KeyMap.fromChar(it.code)) }
                        }
                        kbText = SENTINEL
                    },
                    singleLine = true,
                    placeholder = { Text("Tapez ici pour écrire dans la machine") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { tap(KeyMap.RETURN) }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).focusRequester(kbFocus)
                )
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = ctrl, onClick = { ctrl = !ctrl }, label = { Text("Ctrl") })
                FilterChip(selected = alt, onClick = { alt = !alt }, label = { Text("Alt") })
                AssistChip(onClick = { tap(KeyMap.ESCAPE) }, label = { Text("Échap") })
                AssistChip(onClick = { tap(KeyMap.TAB) }, label = { Text("Tab") })
                AssistChip(onClick = { tap(KeyMap.SUPER) }, label = { Text("Win") })
                AssistChip(onClick = { tap(KeyMap.LEFT) }, label = { Text("←") })
                AssistChip(onClick = { tap(KeyMap.UP) }, label = { Text("↑") })
                AssistChip(onClick = { tap(KeyMap.DOWN) }, label = { Text("↓") })
                AssistChip(onClick = { tap(KeyMap.RIGHT) }, label = { Text("→") })
                AssistChip(onClick = { tap(KeyMap.DELETE) }, label = { Text("Suppr") })
                AssistChip(onClick = {
                    client.key(KeyMap.CTRL, true); client.key(KeyMap.ALT, true)
                    client.key(KeyMap.DELETE, true); client.key(KeyMap.DELETE, false)
                    client.key(KeyMap.ALT, false); client.key(KeyMap.CTRL, false)
                }, label = { Text("Ctrl+Alt+Suppr") })
                AssistChip(onClick = { wheel(8) }, label = { Text("Molette ↑") })
                AssistChip(onClick = { wheel(16) }, label = { Text("Molette ↓") })
            }
        }
    }
}
