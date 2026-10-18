package com.vmdroid

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vmdroid.data.VmConfig
import com.vmdroid.data.VmStore
import com.vmdroid.engine.QemuEngine
import com.vmdroid.engine.VmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store = VmStore(app)
    val vms = mutableStateListOf<VmConfig>()
    var isoList by mutableStateOf<List<String>>(emptyList())
    var busy by mutableStateOf<String?>(null)
    var shareOk by mutableStateOf(QemuEngine.shareInstalled(app))
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
        refresh()
    }

    fun refresh() {
        vms.clear()
        vms.addAll(store.list())
        isoList = store.isoDir().list()?.sorted() ?: emptyList()
        shareOk = QemuEngine.shareInstalled(getApplication())
    }

    fun save(c: VmConfig) {
        store.save(c)
        refresh()
    }

    fun delete(c: VmConfig) {
        VmService.stop(getApplication(), c.id)
        store.delete(c.id)
        refresh()
    }

    fun importIso(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cr = getApplication<Application>().contentResolver
                var name = "image.iso"
                var size = -1L
                cr.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (ni >= 0) name = c.getString(ni) ?: name
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    }
                }
                name = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val dest = store.isoFile(name)
                busy = "Import de $name…"
                cr.openInputStream(uri)!!.use { i ->
                    dest.outputStream().use { o ->
                        val buf = ByteArray(1 shl 20)
                        var total = 0L
                        var last = -1
                        while (true) {
                            val n = i.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            total += n
                            if (size > 0) {
                                val p = (total * 100 / size).toInt()
                                if (p != last) {
                                    last = p
                                    busy = "Import de $name… $p %"
                                }
                            } else {
                                busy = "Import de $name… ${total / 1048576} Mo"
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    refresh()
                    onDone(name)
                }
            } catch (e: Exception) {
                toasts.tryEmit("Import impossible : ${e.message}")
            } finally {
                busy = null
            }
        }
    }

    /** Importe un zip contenant le dossier « share » de QEMU (BIOS, ROM, UEFI…). */
    fun importShare(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                busy = "Installation des fichiers du moteur…"
                getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
                    QemuEngine.extractZip(it, QemuEngine.shareDir(getApplication()))
                }
                withContext(Dispatchers.Main) { refresh() }
                toasts.tryEmit("Fichiers du moteur installés")
            } catch (e: Exception) {
                toasts.tryEmit("Échec : ${e.message}")
            } finally {
                busy = null
            }
        }
    }
}
