package com.wunding.personachat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wunding.personachat.PersonaChatApp
import com.wunding.personachat.data.ChatEngine
import com.wunding.personachat.data.MemoryEntity
import com.wunding.personachat.data.MessageEntity
import com.wunding.personachat.data.PersonaPack
import com.wunding.personachat.data.SessionEntity
import androidx.compose.material3.FilterChip
import com.wunding.personachat.data.Settings
import com.wunding.personachat.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    app: PersonaChatApp,
    sessionId: String,
    onBack: () -> Unit,
    onDetail: (String) -> Unit,
    onMemory: (String) -> Unit,
    onSettings: () -> Unit,
    onSwitchSession: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var session by remember { mutableStateOf<SessionEntity?>(null) }
    var settings by remember { mutableStateOf(Settings()) }
    val messagesState = remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var streamText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var lastText by remember { mutableStateOf("") }
    var shortCount by remember { mutableStateOf(0) }
    var memCount by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val allSessions by app.db.dao().sessions().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<MessageEntity?>(null) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(sessionId) {
        session = withContext(Dispatchers.IO) { app.db.dao().session(sessionId) }
        settings = SettingsStore.flow(app).first()
    }
    LaunchedEffect(sessionId) {
        app.db.dao().messages(sessionId).collectLatest { list ->
            messagesState.value = list
            if (list.isNotEmpty()) listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(sessionId, settings.memoryEnabled) {
        val short = withContext(Dispatchers.IO) { app.db.dao().shortOf(sessionId).size }
        val total = withContext(Dispatchers.IO) { (0..2).sumOf { app.db.dao().count(it) } }
        shortCount = short; memCount = total
    }

    fun refreshCounts() {
        scope.launch {
            shortCount = withContext(Dispatchers.IO) { app.db.dao().shortOf(sessionId).size }
            memCount = withContext(Dispatchers.IO) { (0..2).sumOf { app.db.dao().count(it) } }
        }
    }

    fun send(text: String, appendUser: Boolean = true) {
        val s = session ?: return
        if (input.isBlank() && text.isBlank() || sending) return
        val content = text.trim().ifBlank { input.trim() }
        if (content.isEmpty()) return
        lastText = content
        sending = true; error = ""; streamText = ""; input = ""
        job = scope.launch {
            try {
                if (appendUser) withContext(Dispatchers.IO) {
                    app.db.dao().addMessage(MessageEntity(sessionId = sessionId, role = "user", content = content, ts = System.currentTimeMillis()))
                    app.db.dao().putSession(s.copy(updatedAt = System.currentTimeMillis()))
                }
                val history = withContext(Dispatchers.IO) { app.db.dao().recent(sessionId, 16).reversed() }
                val mems = withContext(Dispatchers.IO) { app.db.dao().recallPool(1) }
                val pack = PersonaPack(id = s.id, name = s.personaName, position = s.position, version = "1.0", source = "会话快照", fullText = s.personaFull, extNames = emptyList(), files = emptyList(), hash = "", size = 0)
                val system = ChatEngine.systemPrompt(pack, s.fullMode, ChatEngine.recall(mems, content, settings.recallK))
                val sb = StringBuilder()
                ChatEngine.send(settings, system, history.dropLast(1), content).collect { delta ->
                    sb.append(delta); streamText = sb.toString()
                }
                val reply = sb.toString().trim()
                if (reply.isEmpty()) throw IllegalStateException("模型没有返回内容")
                withContext(Dispatchers.IO) {
                    app.db.dao().addMessage(MessageEntity(sessionId = sessionId, role = "assistant", content = reply, ts = System.currentTimeMillis()))
                    app.db.dao().putSession(s.copy(updatedAt = System.currentTimeMillis()))
                    if (settings.memoryEnabled) {
                        app.db.dao().addMemory(MemoryEntity(level = 2, content = "我：${content.take(80)}\n${s.personaName.substringBefore(" ·")}：${reply.take(120)}", sourceSession = sessionId, ts = System.currentTimeMillis()))
                        val shorts = app.db.dao().shortOf(sessionId)
                        if (shorts.size > settings.shortKeep) {
                            shorts.take(shorts.size - settings.shortKeep).forEach { app.db.dao().deleteMemory(it.id) }
                        }
                        val turns = app.db.dao().countMessages(sessionId)
                        if (turns % (settings.writeEvery * 2) < 2) {
                            val summary = ChatEngine.summarize(settings, "我：$content\nAI：$reply")
                            app.db.dao().addMemory(MemoryEntity(level = 1, content = summary, sourceSession = sessionId, ts = System.currentTimeMillis()))
                        }
                    }
                }
                streamText = ""
            } catch (e: kotlinx.coroutines.CancellationException) {
                val partial = streamText.trim()
                if (partial.isNotEmpty()) withContext(Dispatchers.IO) {
                    app.db.dao().addMessage(MessageEntity(sessionId = sessionId, role = "assistant", content = partial + "…", ts = System.currentTimeMillis()))
                }
                streamText = ""
            } catch (e: Exception) {
                error = e.message ?: "发送失败"
                streamText = ""
            } finally {
                sending = false; refreshCounts()
            }
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    InitialAvatar(session?.personaName ?: "?", 28.dp, 9.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(session?.personaName ?: "载入中…", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("人设已锁定 · ${session?.position?.ifBlank { "角色扮演" } ?: ""}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(checked = session?.fullMode ?: true, onCheckedChange = { on ->
                            scope.launch {
                                val s = session ?: return@launch
                                val ns = s.copy(fullMode = on)
                                withContext(Dispatchers.IO) { app.db.dao().putSession(ns) }
                                session = ns
                            }
                        })
                        Text(if (session?.fullMode == true) "完整人设" else "纯净", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(allSessions.take(8), key = { it.id }) { it ->
                    FilterChip(
                        selected = it.id == sessionId,
                        onClick = { if (it.id != sessionId) onSwitchSession(it.id) },
                        label = { Text(it.title.substringAfter("对话 ").ifBlank { it.personaName.substringBefore(" ·") }, fontSize = 10.sp) },
                    )
                }
            }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val rows = messagesState.value
                if (streamText.isNotEmpty()) {
                    item(key = "stream") {
                        MessageBubble(MessageEntity(sessionId = sessionId, role = "assistant", content = streamText, ts = System.currentTimeMillis()), character = session?.personaName?.substringBefore(" ·") ?: "", onCopy = { copyText(context, streamText) }, onDelete = {}, onRemember = {}, streaming = true)
                    }
                }
                items(rows.reversed(), key = { it.id }) { m ->
                    MessageBubble(
                        m,
                        character = session?.personaName?.substringBefore(" ·") ?: "",
                        onCopy = { copyText(context, m.content) },
                        onDelete = { scope.launch { withContext(Dispatchers.IO) { app.db.dao().deleteMessage(m.id) } } },
                        onRemember = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    app.db.dao().addMemory(MemoryEntity(level = 0, content = m.content.take(200), sourceSession = sessionId, ts = System.currentTimeMillis(), locked = true))
                                }
                                refreshCounts()
                            }
                        },
                        onEdit = { editing = m; editText = m.content },
                        onRegenerate = if (m.role == "assistant" && m.id == messagesState.value.lastOrNull()?.id) {
                            {
                                scope.launch {
                                    val lastUser = withContext(Dispatchers.IO) {
                                        app.db.dao().deleteMessage(m.id)
                                        app.db.dao().recent(sessionId, 8).firstOrNull { it.role == "user" }
                                    }
                                    if (lastUser != null) send(lastUser.content, appendUser = false)
                                }
                            }
                        } else null,
                    )
                }
            }
            if (error.isNotBlank()) {
                ErrorInlineCard(error, onRetry = {
                    error = ""
                    if (lastText.isNotBlank()) send(lastText, appendUser = false) else send(input, appendUser = false)
                }, onSettings = onSettings, modifier = Modifier.padding(horizontal = 12.dp))
            }
            if (session?.archived == true) {
                Text("已归档人设：重新导入相同包后可继续对话；当前只读查看历史。", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (settings.memoryEnabled) "记忆：开 · 短期 $shortCount 条 · 长期 $memCount 条" else "纯净模式：仅人设注入",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onMemory(sessionId) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("管理记忆", fontSize = 10.sp) }
                if (sending) TextButton(onClick = { job?.cancel(); job = null }) { Text("停止生成", fontSize = 10.sp, color = MaterialTheme.colorScheme.error) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    enabled = session?.archived != true,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 120.dp),
                    placeholder = { Text(if (session?.fullMode == true) "说点什么…" else "纯净模式：只注入人设…", fontSize = 12.sp) },
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    shape = RoundedCornerShape(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    color = if (sending || input.isBlank()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                ) {
                    IconButton(onClick = { send(input) }, enabled = !sending && input.isNotBlank() && session?.archived != true) {
                        Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = if (sending || input.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            TextButton(onClick = { session?.let { s -> onDetail(loadPackId(app, s)) } }, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)) {
                Text("查看只读人设", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    editing?.let { m ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑消息", fontSize = 15.sp) },
            text = { OutlinedTextField(value = editText, onValueChange = { editText = it }, minLines = 4, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    val body = editText.trim()
                    if (body.isNotEmpty()) scope.launch { withContext(Dispatchers.IO) { app.db.dao().updateMessage(m.id, body) } }
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
    }
}

private fun loadPackId(app: PersonaChatApp, s: SessionEntity): String {
    return com.wunding.personachat.data.PackStore.list(app).firstOrNull { it.name == s.personaName }?.id ?: ""
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("message", text))
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(m: MessageEntity, character: String, onCopy: () -> Unit, onDelete: () -> Unit, onRemember: () -> Unit, onEdit: (() -> Unit)? = null, onRegenerate: (() -> Unit)? = null, streaming: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    val user = m.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        if (!user) Text(character.ifBlank { "AI" }, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        Box {
            Surface(
                color = if (user) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(0.86f).combinedClickable(onClick = {}, onLongClick = { menu = true }),
            ) {
                Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                    Row {
                        Text(m.content, fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface)
                        if (streaming) {
                            val blink = rememberInfiniteTransition(label = "cursor")
                            val a by blink.animateFloat(
                                initialValue = 1f, targetValue = 0.15f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                    tween(500), RepeatMode.Reverse
                                ), label = "cursorAlpha",
                            )
                            Text("▍", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = a))
                        }
                    }
                    Text(SimpleDateFormat("HH:mm", Locale.US).format(Date(m.ts)), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("复制", fontSize = 12.sp) }, onClick = { menu = false; onCopy() })
                if (onEdit != null) DropdownMenuItem(text = { Text("编辑这条", fontSize = 12.sp) }, onClick = { menu = false; onEdit() })
                if (!user) {
                    DropdownMenuItem(text = { Text("加入永久记忆", fontSize = 12.sp) }, onClick = { menu = false; onRemember() })
                    DropdownMenuItem(text = { Text("重新生成", fontSize = 12.sp) }, onClick = { menu = false; onRegenerate?.invoke() })
                    DropdownMenuItem(text = { Text("删除这条", fontSize = 12.sp) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
