package com.wunding.personachat.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.wunding.personachat.data.MessageEntity
import com.wunding.personachat.data.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(app: PersonaChatApp, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessions by app.db.dao().sessions().collectAsState(initial = emptyList())
    var menuFor by remember { mutableStateOf<SessionEntity?>(null) }
    var renameTarget by remember { mutableStateOf<SessionEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SessionEntity?>(null) }
    var exportJsonFor by remember { mutableStateOf<SessionEntity?>(null) }
    var exportMdFor by remember { mutableStateOf<SessionEntity?>(null) }
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var pendingMd by remember { mutableStateOf<String?>(null) }

    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val body = pendingJson
        if (uri != null && body != null) scope.launch { withContext(Dispatchers.IO) { writeText(context, uri, body) } }
    }
    val mdLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri: Uri? ->
        val body = pendingMd
        if (uri != null && body != null) scope.launch { withContext(Dispatchers.IO) { writeText(context, uri, body) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                title = { Text("会话", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (sessions.isEmpty()) {
            EmptyStateCanvas("还没有会话。回首页选一个人设包开始。", Modifier.padding(pad))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions, key = { it.id }) { s ->
                    Box2 {
                        Card(
                            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { if (!s.archived) onOpen(s.id) else onOpen(s.id) }, onLongClick = { menuFor = s }),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (s.archived) 0.55f else 1f)),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                InitialAvatar(s.personaName, 32.dp, 10.dp)
                                Spacer(Modifier.size(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(s.title, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f, false))
                                        Text("  " + SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(s.updatedAt)), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(s.position.ifBlank { "角色扮演" }, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    if (s.archived) StatusPill("已归档人设：" + s.personaName.substringBefore(" ·"), MaterialTheme.colorScheme.onSurfaceVariant, Modifier.padding(top = 3.dp))
                                }
                            }
                        }
                        DropdownMenu(expanded = menuFor?.id == s.id, onDismissRequest = { menuFor = null }) {
                            DropdownMenuItem(text = { Text("导出 JSON", fontSize = 12.sp) }, onClick = {
                                menuFor = null
                                scope.launch {
                                    pendingJson = buildJson(app, s)
                                    jsonLauncher.launch("persona-" + s.id + ".json")
                                }
                            })
                            DropdownMenuItem(text = { Text("导出 Markdown", fontSize = 12.sp) }, onClick = {
                                menuFor = null
                                scope.launch {
                                    pendingMd = buildMd(app, s)
                                    mdLauncher.launch("persona-" + s.id + ".md")
                                }
                            })
                            DropdownMenuItem(text = { Text("重命名", fontSize = 12.sp) }, onClick = { menuFor = null; renameTarget = s; renameText = s.title })
                            DropdownMenuItem(text = { Text(if (s.archived) "取消归档" else "归档", fontSize = 12.sp) }, onClick = {
                                menuFor = null
                                scope.launch { withContext(Dispatchers.IO) { app.db.dao().putSession(s.copy(archived = !s.archived)) } }
                            })
                            DropdownMenuItem(text = { Text("删除会话", fontSize = 12.sp) }, onClick = { menuFor = null; deleteTarget = s })
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { s ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名会话") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = renameText.trim()
                    if (name.isNotEmpty()) scope.launch { withContext(Dispatchers.IO) { app.db.dao().putSession(s.copy(title = name)) } }
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }
    deleteTarget?.let { s ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除 ${s.title}？") },
            text = { Text("消息会一起删除，不可恢复。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { app.db.dao().deleteSession(s.id) } }; deleteTarget = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun Box2(content: @Composable () -> Unit) = androidx.compose.foundation.layout.Box { content() }

private fun writeText(context: Context, uri: Uri, body: String) {
    context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
}

private suspend fun buildJson(app: PersonaChatApp, s: SessionEntity): String {
    val msgs = app.db.dao().recent(s.id, 10000).reversed()
    val arr = JSONArray()
    msgs.forEach { m -> arr.put(JSONObject().put("role", m.role).put("content", m.content).put("ts", m.ts)) }
    return JSONObject().apply {
        put("exportedAt", System.currentTimeMillis())
        put("persona_snapshot", JSONObject().apply {
            put("name", s.personaName); put("position", s.position); put("fullText", s.personaFull)
            put("hash", s.personaFull.hashCode())
        })
        put("session", JSONObject().apply { put("id", s.id); put("title", s.title); put("createdAt", s.createdAt) })
        put("messages", arr)
    }.toString(2)
}

private suspend fun buildMd(app: PersonaChatApp, s: SessionEntity): String {
    val msgs = app.db.dao().recent(s.id, 10000).reversed()
    return buildString {
        appendLine("# ${s.title}")
        appendLine()
        appendLine("- 人设：${s.personaName}")
        appendLine("- 定位：${s.position}")
        appendLine("- 导出时间：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
        appendLine()
        msgs.forEach { m ->
            appendLine(if (m.role == "user") "**我：**" else "**${s.personaName.substringBefore(" · ")}：**")
            appendLine(m.content)
            appendLine()
        }
    }
}
