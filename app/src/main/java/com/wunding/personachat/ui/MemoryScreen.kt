package com.wunding.personachat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wunding.personachat.PersonaChatApp
import com.wunding.personachat.data.MemoryEntity
import com.wunding.personachat.data.Settings
import com.wunding.personachat.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(app: PersonaChatApp, sessionId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val mems by app.db.dao().memories().collectAsState(initial = emptyList())
    var settings by remember { mutableStateOf(Settings()) }
    var addLevel by remember { mutableStateOf(-1) }
    var editTarget by remember { mutableStateOf<MemoryEntity?>(null) }
    var text by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { settings = SettingsStore.flow(app).first() }
    val permanent = mems.filter { it.level == 0 }
    val long = mems.filter { it.level == 1 }
    val short = mems.filter { it.level == 2 && it.sourceSession == sessionId }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                title = { Text("记忆管理", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("允许 AI 写入记忆", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                    Text("关闭后不再生成短期/长期记忆，仅保留已有", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.memoryEnabled, onCheckedChange = { on ->
                    settings = settings.copy(memoryEnabled = on)
                    scope.launch { SettingsStore.save(app, settings) }
                })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("永久", permanent.size, Modifier.weight(1f))
                Stat("长期", long.size, Modifier.weight(1f))
                Stat("短期", short.size, Modifier.weight(1f))
            }
            MemSection("永久记忆 · 手动锁定，永不自动删除", permanent, showLevel = 0,
                onAdd = { addLevel = 0; text = "" },
                onEdit = { editTarget = it; text = it.content },
                onDelete = { m -> scope.launch { withContext(Dispatchers.IO) { app.db.dao().deleteMemory(m.id) } } },
            )
            MemSection("长期记忆 · 跨会话摘要", long, showLevel = 1,
                onAdd = { addLevel = 1; text = "" },
                onEdit = { editTarget = it; text = it.content },
                onDelete = { m -> scope.launch { withContext(Dispatchers.IO) { app.db.dao().deleteMemory(m.id) } } },
                extra = { m ->
                    TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { app.db.dao().setMemoryLevel(m.id, 2) } } }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                        Text("降为短期", fontSize = 9.5.sp)
                    }
                },
            )
            MemSection("短期记忆 · 本会话最近消息", short, showLevel = 2,
                onAdd = null,
                onEdit = null,
                onDelete = { m -> scope.launch { withContext(Dispatchers.IO) { app.db.dao().deleteMemory(m.id) } } },
                extra = { TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { app.db.dao().clearShort(sessionId) } } }) { Text("清空短期", fontSize = 9.5.sp) } },
            )
            var q by remember { mutableStateOf("") }
            var hit by remember { mutableStateOf<List<Triple<String, Int, String>>>(emptyList()) }
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text("检索测试", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                Text("输入一句话，看当前会召回哪几条、命中方式是什么。", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = q, onValueChange = { q = it }, label = { Text("测试一句", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    val tokens = q.split(Regex("[\\s，。！？、,.!?；;：:]+")).filter { it.length >= 2 }
                    val scored = mems.map { m ->
                        val hits = tokens.count { m.content.contains(it) }
                        Triple(m.content, hits, if (hits > 0) "关键词 ×$hits" else "兜底（最近）")
                    }.sortedByDescending { it.second }.take(settings.recallK)
                    hit = scored
                }) { Text("跑一次召回", fontSize = 10.5.sp) }
                hit.forEachIndexed { i, h ->
                    Text("${i + 1}. [${h.third}] ${h.first.take(80)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            Spacer(Modifier.size(18.dp))
        }
    }

    if (addLevel >= 0 || editTarget != null) {
        AlertDialog(
            onDismissRequest = { addLevel = -1; editTarget = null },
            title = { Text(if (editTarget != null) "编辑记忆" else "新增记忆") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 3, placeholder = { Text("一句话写清楚", fontSize = 11.sp) })
            },
            confirmButton = {
                TextButton(onClick = {
                    val body = text.trim()
                    if (body.isNotEmpty()) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                if (editTarget != null) app.db.dao().updateMemory(editTarget!!.id, body)
                                else app.db.dao().addMemory(MemoryEntity(level = addLevel, content = body, sourceSession = sessionId, ts = System.currentTimeMillis(), locked = addLevel == 0))
                            }
                        }
                    }
                    addLevel = -1; editTarget = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { addLevel = -1; editTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)).padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemSection(
    title: String,
    items: List<MemoryEntity>,
    showLevel: Int,
    onAdd: (() -> Unit)?,
    onEdit: ((MemoryEntity) -> Unit)?,
    onDelete: (MemoryEntity) -> Unit,
    extra: (@Composable (MemoryEntity) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(showLevel != 2) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (onAdd != null) IconButton(onClick = onAdd, modifier = Modifier.size(26.dp)) { Icon(Icons.Filled.Add, "添加", tint = MaterialTheme.colorScheme.primary) }
            TextButton(onClick = { open = !open }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text(if (open) "收起" else "展开", fontSize = 10.sp) }
        }
        if (open) {
            if (items.isEmpty()) Text("还没有", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(m.content, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(m.ts)) + if (m.sourceSession.isNotBlank() && showLevel != 2) " · 来自会话" else "",
                            fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (m.level == 0) Icon(Icons.Filled.Lock, "锁定", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(13.dp).padding(top = 2.dp))
                    if (onEdit != null) IconButton(onClick = { onEdit(m) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Edit, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp)) }
                    IconButton(onClick = { onDelete(m) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp)) }
                    extra?.let { it(m) }
                }
            }
        }
    }
}
