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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.wunding.personachat.data.PackStore
import com.wunding.personachat.data.PersonaPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PersonaDetailScreen(app: PersonaChatApp, packId: String, onBack: () -> Unit, onStartChat: (String) -> Unit, onOpenChats: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val pack = remember(packId) { PackStore.list(app).firstOrNull { it.id == packId } }
    var openMain by remember { mutableStateOf(false) }
    var openExt by remember { mutableStateOf(false) }
    var openFiles by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                title = { Text("人设详情", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (pack == null) {
            Column(Modifier.fillMaxSize().padding(pad).padding(20.dp)) { Text("包不见了，可能已被移除。", fontSize = 13.sp) }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.size(6.dp))
            InitialAvatar(pack.name, 72.dp, 18.dp)
            Text(pack.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Text("${pack.position.ifBlank { "未标注定位" }} · v${pack.version}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill("只读", MaterialTheme.colorScheme.onSurfaceVariant)
                StatusPill("已锁定", MaterialTheme.colorScheme.primary)
            }
            Text("来源 ${pack.source} · 锁定哈希 ${pack.hash}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))

            Spacer(Modifier.size(14.dp))
            SectionCard(
                title = "主设定",
                subtitle = "${pack.fullText.count { it == '\n' }} 行 · 全文注入（由模型上下文自动裁剪）",
                expanded = openMain,
                onToggle = { openMain = !openMain },
            ) {
                Text(pack.fullText, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
            }
            SectionCard(
                title = "扩展包（${pack.extNames.size}）",
                subtitle = pack.extNames.joinToString(" / ").ifBlank { "无扩展包" },
                expanded = openExt,
                onToggle = { openExt = !openExt },
            ) {
                pack.extNames.forEachIndexed { i, n -> Text("${i + 1}. $n", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 1.dp)) }
            }
            SectionCard(
                title = "文件清单（${pack.files.size}）",
                subtitle = "包内路径，只读",
                expanded = openFiles,
                onToggle = { openFiles = !openFiles },
            ) {
                pack.files.forEach { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            Spacer(Modifier.size(16.dp))
            if (com.wunding.personachat.data.Consent.required(pack.name, pack.position, pack.fullText)) {
                StatusPill("18+ · 需同意协议", MaterialTheme.colorScheme.error, Modifier.padding(bottom = 8.dp))
            }
            Button(onClick = {
                val need = com.wunding.personachat.data.Consent.required(pack.name, pack.position, pack.fullText) &&
                    !com.wunding.personachat.data.Consent.accepted(context, pack.name)
                if (need) showConsent = true
                else scope.launch {
                    val sid = withContext(Dispatchers.IO) { startSession(app, pack) }
                    onStartChat(sid)
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("开始新会话") }
            OutlinedButton(onClick = onOpenChats, modifier = Modifier.fillMaxWidth()) { Text("查看对话 · 删除多余会话", fontSize = 12.sp) }
            Spacer(Modifier.size(4.dp))
            TextButton(onClick = { confirmRemove = true }) { Text("从列表移除", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error) }
            Text("包内内容不可编辑、不可删除；移除只影响本地列表。", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 18.dp))
        }
    }
    if (showConsent) {
        MatureConsentDialog(
            personaName = pack?.name ?: "",
            onAgree = {
                pack?.let {
                    com.wunding.personachat.data.Consent.accept(context, it.name)
                    showConsent = false
                    scope.launch {
                        val sid = withContext(Dispatchers.IO) { startSession(app, it) }
                        onStartChat(sid)
                    }
                }
            },
            onDecline = { showConsent = false },
        )
    }
    if (confirmRemove) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("移除 ${pack?.name}？") },
            text = { Text("会删掉本地包文件；已有会话保留为归档。", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    pack?.let { p -> scope.launch { withContext(Dispatchers.IO) { PackStore.remove(app, p.id) }; onBack() } }
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit, body: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "展开", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expanded) {
            Spacer(Modifier.size(8.dp))
            body()
        }
    }
}
