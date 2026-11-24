package com.wunding.personachat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wunding.personachat.PersonaChatApp
import com.wunding.personachat.data.PackStore
import com.wunding.personachat.data.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 人设详情 → 查看对话：列出该人设的全部会话，可进入，也可永久删除（消息/短期记忆一起清）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaChatsScreen(app: PersonaChatApp, packId: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val pack = remember(packId) { PackStore.list(app).firstOrNull { it.id == packId } }
    val all by app.db.dao().sessions().collectAsState(initial = emptyList())
    val mine = all.filter { pack != null && it.personaName == pack.name }
    var deleteTarget by remember { mutableStateOf<SessionEntity?>(null) }
    var clearAll by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    fun purge(id: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                app.db.dao().deleteMessages(id)
                app.db.dao().clearShort(id)
                app.db.dao().deleteSession(id)
            }
            msg = "已永久删除该会话（含消息与短期记忆）"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                title = {
                    Column {
                        Text("与 ${pack?.character ?: "角色"} 的对话", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("${mine.size} 个会话 · 永久删除不会残留", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (mine.isNotEmpty()) TextButton(onClick = { clearAll = true }) { Text("全部删除", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (msg.isNotBlank()) Text(msg, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            if (mine.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("这个人设还没有对话。回到详情页点「开始新会话」。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(mine, key = { it.id }) { s ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(s.id) },
                        ) {
                            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(
                                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(s.updatedAt)) +
                                            " · " + if (s.archived) "归档" else "进行中",
                                        fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { deleteTarget = s }) {
                                    Icon(Icons.Default.Delete, "永久删除", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { s ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("永久删除这个会话？") },
            text = { Text("「${s.title}」的全部消息与本会话短期记忆会一起删除，不进入归档、不可恢复。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = { purge(s.id); deleteTarget = null }) { Text("永久删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
    if (clearAll) {
        AlertDialog(
            onDismissRequest = { clearAll = false },
            title = { Text("删除全部 ${mine.size} 个会话？") },
            text = { Text("这个人设的所有对话、消息、短期记忆都会永久删除，不可恢复。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            mine.forEach {
                                app.db.dao().deleteMessages(it.id)
                                app.db.dao().clearShort(it.id)
                                app.db.dao().deleteSession(it.id)
                            }
                        }
                        clearAll = false
                        msg = "已清空该人设的全部会话"
                    }
                }) { Text("全部删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { clearAll = false }) { Text("取消") } },
        )
    }
}
