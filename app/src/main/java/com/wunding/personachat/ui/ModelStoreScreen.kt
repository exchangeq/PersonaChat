package com.wunding.personachat.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wunding.personachat.PersonaChatApp
import com.wunding.personachat.data.GgufModel
import com.wunding.personachat.data.GgufLlm
import com.wunding.personachat.data.ModelStore
import com.wunding.personachat.data.Settings
import com.wunding.personachat.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelStoreScreen(app: PersonaChatApp, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var mirror by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf("general") }
    var msg by remember { mutableStateOf("") }
    var showCustom by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
    var customUrl by remember { mutableStateOf("") }
    val progress = remember { mutableStateMapOf<String, Float>() }
    val jobs = remember { mutableStateMapOf<String, Job>() }
    var files by remember { mutableStateOf(listOf<File>()) }
    var activeId by remember { mutableStateOf("") }

    fun refresh() { files = ModelStore.downloaded(context) }

    fun startDownload(model: GgufModel) {
        // 同一时刻只允许一个下载：避免连点造成并发请求、服务器限流
        if (activeId.isNotBlank() && activeId != model.id) {
            msg = "已有模型正在下载，完成或取消后再试"
            return
        }
        if (jobs[model.id]?.isActive == true) {
            jobs[model.id]?.cancel(); jobs.remove(model.id); progress.remove(model.id); return
        }
        activeId = model.id
        jobs[model.id] = scope.launch {
            msg = ""
            try {
                val f = withContext(Dispatchers.IO) {
                    ModelStore.download(context, model, mirror) { done, total ->
                        val p = if (total > 0) done.toFloat() / total else -1f
                        handler.post { progress[model.id] = p }
                    }
                }
                progress.remove(model.id)
                refresh()
                val s = withContext(Dispatchers.IO) { SettingsStore.flow(app).first() }
                withContext(Dispatchers.IO) { SettingsStore.save(app, s.copy(localModelPath = f.absolutePath, backend = "device")) }
                msg = "已下载 ${f.name}，已设为当前本地模型，回设置点“加载”即可"
            } catch (e: kotlinx.coroutines.CancellationException) {
                progress.remove(model.id)
                msg = "已取消下载"
            } catch (e: Throwable) {
                progress.remove(model.id)
                msg = "下载失败：${e.message ?: "网络异常"}"
            } finally {
                jobs.remove(model.id)
                if (activeId == model.id) activeId = ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                title = { Text("模型库", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text("PChat 模型库", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
            Text("本地推理 · 数据不出手机 · 下载后离线可用", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ModelScope 镜像", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(if (mirror) "国内加速" else "HuggingFace", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(8.dp))
                    Switch(checked = mirror, onCheckedChange = { mirror = it })
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = tab == "general", onClick = { tab = "general" }, label = { Text("通用模型", fontSize = 11.sp) })
                FilterChip(selected = tab == "reasoning", onClick = { tab = "reasoning" }, label = { Text("推理模型", fontSize = 11.sp) })
            }
            if (msg.isNotBlank()) Text(msg, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ModelStore.catalog.filter { it.tag == tab }, key = { it.id }) { m ->
                    val p = progress[m.id]
                    val downloaded = remember(files, m.id) { files.any { it.name == m.fileName } }
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 2)
                                Text("~${m.sizeMb} MB", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary)
                            }
                            Text(m.desc, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(8.dp))
                            if (p != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        if (p < 0f) "下载中…（大小未知）" else String.format(Locale.US, "下载中 %.1f%%", p * 100),
                                        fontSize = 11.sp,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { startDownload(m) }) { Text("取消", fontSize = 11.sp) }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (downloaded) {
                                            scope.launch {
                                                val s = withContext(Dispatchers.IO) { SettingsStore.flow(app).first() }
                                                withContext(Dispatchers.IO) { SettingsStore.save(app, s.copy(localModelPath = File(ModelStore.dir(context), m.fileName).absolutePath, backend = "device")) }
                                                msg = "已设为当前模型，回设置点“加载”"
                                            }
                                        } else startDownload(m)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = downloaded || activeId.isBlank() || activeId == m.id,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                ) {
                                    Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(if (downloaded) "使用这个模型" else "下载", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showCustom = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Link, null, Modifier.size(15.dp)); Spacer(Modifier.size(5.dp)); Text("自定义链接", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { refresh(); showManage = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Folder, null, Modifier.size(15.dp)); Spacer(Modifier.size(5.dp)); Text("模型管理", fontSize = 12.sp)
                }
            }
        }
    }

    if (showCustom) {
        var busy by remember { mutableStateOf(false) }
        var cp by remember { mutableStateOf(0f) }
        AlertDialog(
            onDismissRequest = { if (!busy) showCustom = false },
            title = { Text("自定义模型直链", fontSize = 16.sp) },
            text = {
                Column {
                    Text("填 .gguf 直链（HuggingFace / ModelScope / 自建均可），下载后自动设为当前模型。", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = customUrl, onValueChange = { customUrl = it }, label = { Text("https://…/model.gguf", fontSize = 10.sp) }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                    if (busy) {
                        Spacer(Modifier.height(8.dp))
                        Text(if (cp >= 0f) String.format(Locale.US, "下载中 %.1f%%", cp * 100) else "下载中…", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && activeId.isBlank(), onClick = {
                    val url = customUrl.trim()
                    if (url.isBlank()) return@TextButton
                    if (activeId.isNotBlank()) { msg = "已有模型正在下载，完成或取消后再下自定义链接"; return@TextButton }
                    busy = true
                    scope.launch {
                        try {
                            val f = withContext(Dispatchers.IO) {
                                ModelStore.downloadUrl(context, url) { done, total ->
                                    val p = if (total > 0) done.toFloat() / total else -1f
                                    handler.post { cp = p }
                                }
                            }
                            val s = withContext(Dispatchers.IO) { SettingsStore.flow(app).first() }
                            withContext(Dispatchers.IO) { SettingsStore.save(app, s.copy(localModelPath = f.absolutePath, backend = "device")) }
                            refresh(); showCustom = false; msg = "已下载 ${f.name}，已设为当前模型"
                        } catch (e: Throwable) {
                            msg = "下载失败：${e.message ?: ""}"
                        } finally { busy = false }
                    }
                }) { Text("下载") }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }, enabled = !busy) { Text("取消") } },
        )
    }

    if (showManage) {
        var busyFile by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { showManage = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("已下载模型（${files.size}）", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("目录：${ModelStore.dir(context).absolutePath}", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (files.isEmpty()) Text("还没有下载模型。", fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                LazyColumn(Modifier.fillMaxWidth().height(400.dp)) {
                    items(files, key = { it.absolutePath }) { f ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, fontSize = 12.sp, maxLines = 1)
                                Text("${f.length() / (1024 * 1024)} MB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (busyFile == f.absolutePath) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else {
                                TextButton(onClick = {
                                    busyFile = f.absolutePath
                                    scope.launch {
                                        val s = withContext(Dispatchers.IO) { SettingsStore.flow(app).first() }
                                        withContext(Dispatchers.IO) { SettingsStore.save(app, s.copy(localModelPath = f.absolutePath, backend = "device")) }
                                        msg = "加载 ${f.name}…"
                                        msg = withContext(Dispatchers.IO) { GgufLlm.load(f.absolutePath, 4) }
                                        busyFile = ""
                                    }
                                }) { Text("使用", fontSize = 11.sp) }
                                IconButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            if (GgufLlm.path == f.absolutePath) GgufLlm.unload()
                                            f.delete()
                                        }
                                        refresh()
                                    }
                                }) { Icon(Icons.Default.Delete, "删除", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
