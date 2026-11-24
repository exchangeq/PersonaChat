package com.wunding.personachat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wunding.personachat.BuildConfig
import com.wunding.personachat.PersonaChatApp
import com.wunding.personachat.data.ChatEngine
import com.wunding.personachat.data.Settings
import com.wunding.personachat.data.SettingsStore
import com.wunding.personachat.data.UpdateManager
import com.wunding.personachat.data.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: PersonaChatApp, onBack: () -> Unit, onOpenModels: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(Settings()) }
    var testMsg by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var deviceMsg by remember { mutableStateOf("") }
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var updateMsg by remember { mutableStateOf("") }
    var dlProgress by remember { mutableStateOf(-1) }
    var checking by remember { mutableStateOf(false) }
    var lastCheckAt by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val dir = com.wunding.personachat.data.AppStorage.models(context)
                        val ext = if (uri.toString().endsWith("gguf", true)) ".gguf" else ".task"
                        val f = java.io.File(dir, "model$ext")
                        context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                        f.absolutePath
                    }
                }.onSuccess { path ->
                    s = s.copy(localModelPath = path)
                    SettingsStore.save(app, s)
                    deviceMsg = "已导入模型：" + java.io.File(path).length() / (1024 * 1024) + "MB，点“加载”开始"
                }.onFailure { deviceMsg = "导入失败：" + (it.message ?: "") }
            }
        }
    }

    LaunchedEffect(Unit) { s = SettingsStore.flow(app).first() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                title = { Text("设置", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Group("模型接入") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = s.backend == "local", onClick = { s = s.copy(backend = "local", baseUrl = if (s.baseUrl.isBlank() || s.baseUrl.startsWith("http://127.0.0.1")) "http://127.0.0.1:11434/v1" else s.baseUrl, model = if (s.model == "gpt-4o-mini") "llama3.1" else s.model) }, label = { Text("本地 AI", fontSize = 11.sp) })
                    FilterChip(selected = s.backend == "device", onClick = { s = s.copy(backend = "device") }, label = { Text("手机本地", fontSize = 11.sp) })
                    FilterChip(selected = s.backend == "api", onClick = { s = s.copy(backend = "api", baseUrl = if (s.baseUrl.startsWith("http://127.0.0.1")) "https://api.openai.com/v1" else s.baseUrl) }, label = { Text("API AI", fontSize = 11.sp) })
                }
                Text(
                    when (s.backend) {
                        "device" -> "手机本地：.task 走 MediaPipe（Gemma 1B int4 / GPU），.gguf 走 llama.cpp（Qwen、Llama、Gemma、Phi 等，纯 CPU）；导入一次后完全离线。"
                        "local" -> "本地服务：Ollama / llama.cpp 的 OpenAI 兼容端口，默认 127.0.0.1:11434/v1（需在 Termux 里跑）。"
                        else -> "API：任意 OpenAI 兼容服务，Base URL 要带 /v1。"
                    },
                    fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.backend == "device") {
                    OutlinedTextField(value = s.localModelPath, onValueChange = { s = s.copy(localModelPath = it) }, label = { Text("本地模型路径（.task / .gguf）", fontSize = 10.sp) },
                        placeholder = { Text("导入后自动填入；GGUF 推荐 Q4_K_M、1~2B 参数", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = s.localBackend == "gpu", onClick = { s = s.copy(localBackend = "gpu") }, label = { Text("GPU", fontSize = 10.5.sp) })
                        FilterChip(selected = s.localBackend == "cpu", onClick = { s = s.copy(localBackend = "cpu") }, label = { Text("CPU（老处理器）", fontSize = 10.5.sp) })
                        TextButton(onClick = { modelPicker.launch(arrayOf("*/*")) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("导入模型", fontSize = 10.5.sp) }
                        TextButton(onClick = {
                            scope.launch {
                                deviceMsg = "加载中…（大模型可能要 10~60 秒）"
                                deviceMsg = withContext(Dispatchers.IO) {
                                    if (s.localModelPath.endsWith(".gguf", true)) com.wunding.personachat.data.GgufLlm.load(s.localModelPath, 4)
                                    else com.wunding.personachat.data.DeviceLlm.load(s.localModelPath, s.localBackend)
                                }
                            }
                        }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("加载", fontSize = 10.5.sp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onOpenModels, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("模型库（下载 / 管理）", fontSize = 10.5.sp) }
                    }
                    Text(
                        deviceMsg.ifBlank {
                            if (s.localModelPath.endsWith(".gguf", true)) com.wunding.personachat.data.GgufLlm.status()
                            else com.wunding.personachat.data.DeviceLlm.status()
                        },
                        fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("模型目录：" + com.wunding.personachat.data.ModelStore.displayPath(context), fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = s.model, onValueChange = { s = s.copy(model = it) }, label = { Text("模型标签（用于判断对话模板）", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(value = s.baseUrl, onValueChange = { s = s.copy(baseUrl = it) }, label = { Text("Base URL", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = s.apiKey, onValueChange = { s = s.copy(apiKey = it) }, label = { Text("API Key（仅存本机）", fontSize = 10.sp) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = s.model, onValueChange = { s = s.copy(model = it) }, label = { Text("模型名", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        testing = true; testMsg = ""
                        scope.launch {
                            if (s.backend == "device") {
                                val gguf = s.localModelPath.endsWith(".gguf", true)
                                val loadMsg = withContext(Dispatchers.IO) {
                                    if (gguf) com.wunding.personachat.data.GgufLlm.load(s.localModelPath, 4)
                                    else com.wunding.personachat.data.DeviceLlm.load(s.localModelPath, s.localBackend)
                                }
                                testMsg = loadMsg
                                if (!loadMsg.startsWith("失败")) {
                                    runCatching {
                                        withTimeout(120000) {
                                            ChatEngine.send(s, "你是连接测试助手，只回复 OK", emptyList(), "回复 OK").first()
                                        }
                                    }
                                        .onSuccess { testMsg = "本地生成成功：" + it.take(30) }
                                        .onFailure { testMsg = "本地生成失败：" + (it.message ?: "") }
                                }
                                testing = false
                                return@launch
                            }
                            runCatching {
                                withTimeout(20000) {
                                    withContext(Dispatchers.IO) {
                                        ChatEngine.send(s, "你是连接测试助手，只回复 OK", emptyList(), "回复 OK").first()
                                    }
                                }
                            }.onSuccess { testMsg = "连接成功：${it.take(30)}" }
                                .onFailure { testMsg = "失败：" + (it.message ?: "未知") }
                            testing = false
                        }
                    }, enabled = !testing) { Text(if (testing) "测试中…" else "连接测试", fontSize = 11.sp) }
                    if (testing) CircularProgressIndicator(Modifier.padding(start = 8.dp).size(16.dp), strokeWidth = 2.dp)
                }
                if (testMsg.isNotBlank()) Text(testMsg, fontSize = 10.sp, color = if (testMsg.startsWith("连接成功")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }

            Group("生成参数") {
                Row { Text("温度 ${"%.2f".format(s.temperature)}", fontSize = 10.5.sp, modifier = Modifier.weight(1f)) }
                Slider(value = s.temperature, onValueChange = { s = s.copy(temperature = it) }, valueRange = 0f..2f)
                Row { Text("最大输出 token ${s.maxTokens}", fontSize = 10.5.sp, modifier = Modifier.weight(1f)) }
                Slider(value = s.maxTokens.toFloat(), onValueChange = { s = s.copy(maxTokens = it.toInt()) }, valueRange = 128f..4096f)
                SwitchRow("流式输出", s.stream) { s = s.copy(stream = it) }
                SwitchRow("极速模式（推荐）", s.fastMode) { s = s.copy(fastMode = it) }
                Text("开启后：远程只带最近 8 条对话、输出上限 512 token、人设截断到 6000 字；本地同时缩小上下文，降低卡顿和闪退概率。", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Group("记忆") {
                SwitchRow("允许 AI 写入记忆", s.memoryEnabled) { s = s.copy(memoryEnabled = it) }
                Row { Text("每 ${s.writeEvery} 轮写一条长期记忆", fontSize = 10.5.sp, modifier = Modifier.weight(1f)) }
                Slider(value = s.writeEvery.toFloat(), onValueChange = { s = s.copy(writeEvery = it.toInt()) }, valueRange = 2f..10f, steps = 7)
                Row { Text("短期保留 ${s.shortKeep} 条", fontSize = 10.5.sp, modifier = Modifier.weight(1f)) }
                Slider(value = s.shortKeep.toFloat(), onValueChange = { s = s.copy(shortKeep = it.toInt()) }, valueRange = 4f..30f, steps = 25)
                Row { Text("每次召回 ${s.recallK} 条", fontSize = 10.5.sp, modifier = Modifier.weight(1f)) }
                Slider(value = s.recallK.toFloat(), onValueChange = { s = s.copy(recallK = it.toInt()) }, valueRange = 2f..10f, steps = 7)
            }

            Group("朗读与显示") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动朗读 AI 回复", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("用系统离线中文音色；后续可接角色语音模型（已预留 voiceId）", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = s.autoRead, onCheckedChange = { s = s.copy(autoRead = it) })
                }
                Text("角色音色", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    com.wunding.personachat.data.Voice.profiles.forEach { v ->
                        FilterChip(selected = s.voiceProfile == v.id, onClick = { s = s.copy(voiceProfile = v.id) }, label = { Text(v.label, fontSize = 10.sp) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("试听", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val p = com.wunding.personachat.data.Voice.profiles.firstOrNull { it.id == s.voiceProfile } ?: com.wunding.personachat.data.Voice.profiles.first()
                        com.wunding.personachat.data.Voice.speak("（轻声）今天辛苦了，先坐下吧。", p)
                    }) { Text("播一句", fontSize = 10.5.sp) }
                    TextButton(onClick = { com.wunding.personachat.data.Voice.stop() }) { Text("停止", fontSize = 10.5.sp) }
                }
                Text("对话字号", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.92f to "小", 1.0f to "标准", 1.08f to "大", 1.16f to "特大").forEach { (v, label) ->
                        FilterChip(selected = kotlin.math.abs(s.fontScale - v) < 0.01f, onClick = { s = s.copy(fontScale = v) }, label = { Text(label, fontSize = 10.sp) })
                    }
                }
            }

            Group("内容分级与同意记录") {
                Text("含病娇 / 囚禁 / 极限标签的人设，首次进入对话需确认已满 18 岁并同意虚构协议。", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    context.getSharedPreferences("consent", 0).edit().clear().apply()
                    android.widget.Toast.makeText(context, "已清除全部同意记录，下次进入需重新确认", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("清除同意记录", fontSize = 11.sp) }
            }

            Group("外观") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = s.theme == "dark", onClick = { s = s.copy(theme = "dark") }, label = { Text("深色", fontSize = 11.sp) })
                    FilterChip(selected = s.theme == "light", onClick = { s = s.copy(theme = "light") }, label = { Text("浅色", fontSize = 11.sp) })
                    FilterChip(selected = s.theme == "system", onClick = { s = s.copy(theme = "system") }, label = { Text("跟随系统", fontSize = 11.sp) })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("点缀色", fontSize = 10.5.sp, modifier = Modifier.weight(1f))
                    ColorDot(AccentGreen, s.accent == "green") { s = s.copy(accent = "green") }
                    ColorDot(AccentAmber, s.accent == "amber") { s = s.copy(accent = "amber") }
                }
            }

            Group("人设包仓库") {
                OutlinedTextField(value = s.personaRepo, onValueChange = { s = s.copy(personaRepo = it) }, label = { Text("GitHub repo（owner/name）", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("默认 exchangeq/PocketForge-Personas，700 个 .aist；首页云图标可浏览下载。", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(onClick = {
                scope.launch { SettingsStore.save(app, s); saved = true }
            }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Text(if (saved) "已保存" else "保存设置") }

            Group("APK 远程更新") {
                Text("从 GitHub Release 拉最新版本，下载后调系统安装器覆盖安装（签名必须一致）。", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val now = System.currentTimeMillis()
                            if (checking || now - lastCheckAt < 10000) {
                                updateMsg = "刚检查过，稍等几秒再点，避免触发 GitHub 限流"
                                return@launch
                            }
                            checking = true
                            lastCheckAt = now
                            updateMsg = "检查中…"
                            runCatching { withContext(Dispatchers.IO) { UpdateManager.check(s.updateRepo) } }
                                .onSuccess { u ->
                                    if (u == null) updateMsg = "最新版本没有 APK 资产"
                                    else if (u.version == BuildConfig.VERSION_NAME) updateMsg = "已是最新：v${u.version}"
                                    else {
                                        update = u; updateMsg = "发现新版本 v${u.version}（${u.size / 1024 / 1024}MB）"
                                    }
                                }
                                .onFailure {
                                    val m = it.message ?: ""
                                    updateMsg = if (m.contains("403") || m.contains("rate limit", true)) "请求太频繁，被 GitHub 限流了，等 1 分钟再试" else "检查失败：$m"
                                }
                            checking = false
                        }
                    }, enabled = dlProgress < 0 && !checking) { Text(if (checking) "检查中…" else "检查更新", fontSize = 11.sp) }
                    update?.let { u ->
                        TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        UpdateManager.download(context, u) { p -> dlProgress = p }
                                    }
                                }.onSuccess { f ->
                                    dlProgress = -1
                                    updateMsg = "下载完成，正在拉起安装…"
                                    UpdateManager.install(context, f)
                                }.onFailure { dlProgress = -1; updateMsg = "下载失败：" + (it.message ?: "") }
                            }
                        }, enabled = dlProgress < 0) { Text("下载并安装 v${u.version}", fontSize = 11.sp) }
                    }
                }
                if (dlProgress >= 0) {
                    Text("下载中 $dlProgress%", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    androidx.compose.material3.LinearProgressIndicator(progress = { dlProgress / 100f }, modifier = Modifier.fillMaxWidth())
                }
                if (updateMsg.isNotBlank()) Text(updateMsg, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("版本 ${BuildConfig.VERSION_NAME} · 由无定工作室开发", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            Text("所有数据存本机：对话在 SQLite，设置与 Key 在 DataStore；只有对话请求会发到你填的模型地址。", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 18.dp))
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.Canvas(Modifier.size(26.dp).padding(3.dp)) {
        drawCircle(color, radius = size.minDimension / 2)
        if (selected) drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    }
}
