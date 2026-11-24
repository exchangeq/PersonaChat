package com.wunding.personachat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.wunding.personachat.data.ChatEngine
import com.wunding.personachat.data.MessageEntity
import com.wunding.personachat.data.PackStore
import com.wunding.personachat.data.PersonaPack
import com.wunding.personachat.data.RemotePack
import com.wunding.personachat.data.SessionEntity
import com.wunding.personachat.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: PersonaChatApp,
    onOpenPack: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var packs by remember { mutableStateOf<List<PersonaPack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PersonaPack?>(null) }
    var error by remember { mutableStateOf("") }
    var showRemote by remember { mutableStateOf(false) }
    var remote by remember { mutableStateOf<List<RemotePack>>(emptyList()) }
    var remoteBusy by remember { mutableStateOf("") }
    var remoteQuery by remember { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    var refreshKey by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var tagFilter by remember { mutableStateOf("全部") }
    var showCreate by remember { mutableStateOf(false) }
    var cName by remember { mutableStateOf("") }
    var cPos by remember { mutableStateOf("") }
    var cDesc by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(refreshKey) {
        loading = true
        packs = withContext(Dispatchers.IO) { PackStore.list(app) }
        loading = false
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                loading = true
                error = ""
                runCatching { withContext(Dispatchers.IO) { PackStore.import(app, uri) } }
                    .onSuccess { p -> preview = p; packs = withContext(Dispatchers.IO) { PackStore.list(app) } }
                    .onFailure { error = "解析失败：" + (it.message ?: "未知原因") }
                loading = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("人设包", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { refreshKey++ }) { Icon(Icons.Filled.Refresh, "刷新", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = {
                        scope.launch {
                            showRemote = true
                            runCatching {
                                val s = SettingsStore.flow(app).first()
                                withContext(Dispatchers.IO) { PackStore.fetchRemote(s.personaRepo) }
                            }.onSuccess { remote = it }.onFailure { error = "远程清单失败：" + (it.message ?: "") }
                        }
                    }) { Icon(Icons.Filled.CloudDownload, "远程包", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onOpenModels) { Icon(Icons.Filled.Memory, "模型库", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { importer.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.FileOpen, "导入", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, "创建人设", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "设置", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
            )
        },
    ) {
        PullToRefreshBox(isRefreshing = loading, onRefresh = { refreshKey++ }, modifier = Modifier.fillMaxSize().padding(it)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${packs.size} 个本地包", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenSessions) { Text("会话列表 ›", fontSize = 11.sp) }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("搜索角色名 / 定位 / 性格 / 世界观", fontSize = 11.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("全部", "二次元", "病娇", "日常").forEach { f ->
                    FilterChip(selected = tagFilter == f, onClick = { tagFilter = f }, label = { Text(f, fontSize = 9.5.sp) })
                }
            }
            if (error.isNotBlank()) {
                ErrorInlineCard(error, onRetry = { error = ""; refreshKey++ }, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            }
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
            }
            val visible = packs.filter { p ->
                val hitQuery = query.isBlank() || p.name.contains(query, true) || p.position.contains(query, true) || p.character.contains(query, true) || p.fullText.contains(query, true)
                val hitTag = when (tagFilter) {
                    "二次元" -> p.position.contains("魔法") || p.position.contains("机娘") || p.position.contains("忍者") || p.position.contains("猎人") || p.position.contains("阴阳师") || p.position.contains("龙族") || p.position.contains("偶像") || p.position.contains("女仆") || p.position.contains("骑士") || p.position.contains("旅行者")
                    "病娇" -> p.position.contains("病娇") || p.fullText.contains("病娇")
                    "日常" -> p.position.contains("日常") || p.position.isBlank()
                    else -> true
                }
                hitQuery && hitTag
            }
            if (visible.isEmpty() && !loading) {
                EmptyStateCanvas(if (query.isBlank()) "还没有人设包。点右上角导入 .aist / zip / PNG 角色卡，或从远程包库拉取。" else "没有匹配“$query”的包。")
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible, key = { it.id }) { p ->
                        PackRow(
                            pack = p,
                            onOpen = { onOpenPack(p.id) },
                            onChat = {
                                scope.launch {
                                    val sid = withContext(Dispatchers.IO) { startSession(app, p) }
                                    onOpenChat(sid)
                                }
                            },
                            onRemove = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { PackStore.remove(app, p.id) }
                                    packs = withContext(Dispatchers.IO) { PackStore.list(app) }
                                }
                            },
                        )
                    }
                }
            }
        }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("创建人设", fontSize = 16.sp) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("随机生成：姓名 / 性格 / 世界观", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val d = com.wunding.personachat.data.PersonaGen.generate()
                            cName = d.name; cPos = d.position; cDesc = d.desc
                        }) { Text("换一套", fontSize = 11.sp) }
                    }
                    OutlinedTextField(value = cName, onValueChange = { cName = it }, label = { Text("名字", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cPos, onValueChange = { cPos = it }, label = { Text("定位（如 傲娇 · 猫娘）", fontSize = 10.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cDesc, onValueChange = { cDesc = it }, label = { Text("性格 / 世界观 / 说话方式 / 雷点（可随机）", fontSize = 10.sp) }, minLines = 4, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (cName.isBlank()) return@TextButton
                    showCreate = false
                    scope.launch {
                        val p = withContext(Dispatchers.IO) { PackStore.createLocal(app, cName.trim(), cPos.trim(), cDesc.trim()) }
                        packs = withContext(Dispatchers.IO) { PackStore.list(app) }
                        val sid = withContext(Dispatchers.IO) { startSession(app, p) }
                        onOpenChat(sid)
                    }
                }) { Text("创建并开始") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }

    preview?.let { p ->
        ModalBottomSheet(onDismissRequest = { preview = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialAvatar(p.name, 44.dp)
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${p.position.ifBlank { "未标注" }} · v${p.version} · ${p.source} · ${p.files.size} 个文件", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("主设定摘要", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(p.fullText.take(220).replace("\n", " "), fontSize = 12.sp, maxLines = 5, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text("扩展包：${p.extNames.size} 个 · 锁定哈希 ${p.hash}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { preview = null }) { Text("取消") }
                    TextButton(onClick = {
                        preview = null
                        scope.launch {
                            val sid = withContext(Dispatchers.IO) { startSession(app, p) }
                            onOpenChat(sid)
                        }
                    }) { Text("确认导入并开始") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showRemote) {
        ModalBottomSheet(onDismissRequest = { showRemote = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                Text("远程包库 · ${remote.size} 个", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (remote.isEmpty()) Text("没拉到清单，检查设置里的仓库名或网络", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = remoteQuery, onValueChange = { remoteQuery = it },
                    placeholder = { Text("搜索远程角色：名字 / 定位 / 世界观", fontSize = 11.sp) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                )
                val remoteVisible = remote.filter {
                    remoteQuery.isBlank() || it.name.contains(remoteQuery, true) || it.desc.contains(remoteQuery, true)
                }
                if (remoteVisible.isEmpty()) Text("远程库里没有匹配“$remoteQuery”的角色", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.fillMaxWidth().height(440.dp)) {
                    items(remoteVisible, key = { it.id }) { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            InitialAvatar(r.name, 32.dp)
                            Spacer(Modifier.size(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, fontSize = 12.5.sp, maxLines = 1)
                                Text("${r.desc} · ${r.extCount} 扩展", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            if (remoteBusy == r.id) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else TextButton(onClick = {
                                scope.launch {
                                    remoteBusy = r.id
                                    runCatching {
                                        val s = SettingsStore.flow(app).first()
                                        withContext(Dispatchers.IO) { PackStore.downloadRemote(app, s.personaRepo, r) }
                                    }.onSuccess {
                                        preview = it
                                        showRemote = false
                                        packs = withContext(Dispatchers.IO) { PackStore.list(app) }
                                    }.onFailure { error = it.message ?: "下载失败" }
                                    remoteBusy = ""
                                }
                            }) { Text("下载", fontSize = 11.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PackRow(pack: PersonaPack, onOpen: () -> Unit, onChat: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Box {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(pack.name, 40.dp)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pack.name, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f, false))
                        Text("  v${pack.version}", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${pack.position.ifBlank { "角色扮演" }} · ${pack.files.count { it.endsWith(".md") }} 段 · ${pack.extNames.size} 扩展", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Row(Modifier.padding(top = 4.dp)) {
                        StatusPill("未加载", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onChat) { Text("加载", fontSize = 11.5.sp, fontWeight = FontWeight.Medium) }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("查看详情", fontSize = 12.sp) }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text("重新解析", fontSize = 12.sp) }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(text = { Text("移除包", fontSize = 12.sp) }, onClick = { menu = false; confirm = true })
        }
        if (confirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("移除 ${pack.name}？") },
                text = { Text("只会从本地列表删除包文件；已建的会话保留。", fontSize = 12.sp) },
                confirmButton = { TextButton(onClick = { confirm = false; onRemove() }) { Text("移除") } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
            )
        }
    }
}

suspend fun startSession(app: PersonaChatApp, pack: PersonaPack): String {
    val now0 = System.currentTimeMillis()
    // 同一个人设只保留一个会话：再次“加载”直接回到原对话，不再新开
    val existing = app.db.dao().sessionForPersona(pack.name)
    if (existing != null) {
        app.db.dao().putSession(existing.copy(
            personaFull = pack.fullText, position = pack.position,
            archived = false, updatedAt = now0,
        ))
        return existing.id
    }
    val sid = newSessionId()
    val now = System.currentTimeMillis()
    val title = pack.character + " 对话 " + SimpleDateFormat("HH:mm", Locale.US).format(Date(now))
    app.db.dao().putSession(
        SessionEntity(
            id = sid, title = title, personaName = pack.name, position = pack.position,
            personaFull = pack.fullText, fullMode = true, archived = false, createdAt = now, updatedAt = now,
        )
    )
    app.db.dao().addMessage(MessageEntity(sessionId = sid, role = "assistant", content = "（${pack.character} 已就位。设定已锁定，本对话内不可更改。）", ts = now))
    return sid
}
