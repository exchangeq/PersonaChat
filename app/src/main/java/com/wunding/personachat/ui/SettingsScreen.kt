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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: PersonaChatApp, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(Settings()) }
    var testMsg by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

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
                    FilterChip(selected = s.backend == "api", onClick = { s = s.copy(backend = "api", baseUrl = if (s.baseUrl.startsWith("http://127.0.0.1")) "https://api.openai.com/v1" else s.baseUrl) }, label = { Text("API AI", fontSize = 11.sp) })
                }
                Text(
                    if (s.backend == "local") "本地：Ollama / llama.cpp 的 OpenAI 兼容端口，默认 127.0.0.1:11434/v1；老处理器建议 4bit 小模型。"
                    else "API：任意 OpenAI 兼容服务，Base URL 要带 /v1。",
                    fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = s.baseUrl, onValueChange = { s = s.copy(baseUrl = it) }, label = { Text("Base URL", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = s.apiKey, onValueChange = { s = s.copy(apiKey = it) }, label = { Text("API Key（仅存本机）", fontSize = 10.sp) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = s.model, onValueChange = { s = s.copy(model = it) }, label = { Text("模型名", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        testing = true; testMsg = ""
                        scope.launch {
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
                Text("默认 exchangeq/PocketForge-Personas，230 个 .aist；首页云图标可浏览下载。", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(onClick = {
                scope.launch { SettingsStore.save(app, s); saved = true }
            }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Text(if (saved) "已保存" else "保存设置") }

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
