package com.wunding.personachat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wunding.personachat.PersonaChatApp
import com.wunding.personachat.data.CastVoice
import com.wunding.personachat.data.PackStore
import com.wunding.personachat.data.PersonaPack
import com.wunding.personachat.data.Voice
import com.wunding.personachat.data.VoiceForge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(app: PersonaChatApp, onBack: () -> Unit) {
    var packs by remember { mutableStateOf<List<PersonaPack>>(emptyList()) }
    var casts by remember { mutableStateOf<Map<String, CastVoice>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { PackStore.list(app) }
        packs = list
        casts = withContext(Dispatchers.IO) { list.associate { it.name to VoiceForge.castFor(app, it.name, it.position, it.fullText) } }
        loading = false
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色音色库", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                "每个角色自动生成一条独立音色：基础声线由人设决定，音高与语速做确定性微调；同一角色重进对话声音不变，点「重掷」才会换。",
                fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            if (loading) {
                Text("正在为角色排音色…", fontSize = 11.sp, modifier = Modifier.padding(14.dp))
            } else if (packs.isEmpty()) {
                Text("还没有本地人设包。先在首页导入或下载一个。", fontSize = 11.sp, modifier = Modifier.padding(14.dp))
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(packs, key = { it.id }) { p ->
                        val cast = casts[p.name]
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(p.position.ifBlank { "角色扮演" } + " · " + Voice.profile(cast?.profileId ?: "default").label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("种子 ${cast?.seed ?: 0}", fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (cast != null) {
                                    Text("音高 ${"%.2f".format(cast.pitch)} · 语速 ${"%.2f".format(cast.rate)}" + if (cast.voiceName.isNotBlank()) " · ${cast.voiceName}" else "", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                        TextButton(onClick = {
                                            VoiceForge.speak(cast, "（${p.character}）……你来了。今天，想听我说什么？")
                                        }) { Text("试听", fontSize = 10.5.sp) }
                                        TextButton(onClick = {
                                            Voice.stop()
                                            val next = VoiceForge.regenerate(app, p.name, p.position, p.fullText)
                                            casts = casts + (p.name to next)
                                            VoiceForge.speak(next, "（${p.character}）……现在的声音，可以吗？")
                                        }) { Text("重掷音色", fontSize = 10.5.sp) }
                                        TextButton(onClick = {
                                            val c = cast ?: return@TextButton
                                            VoiceForge.render(context, c, "（${p.character}）……你来了。今天，想听我说什么？") { ok, file ->
                                                scope.launch(Dispatchers.Main) {
                                                    if (ok && file.length() > 0) shareVoice(context, file, p.character)
                                                    else Toast.makeText(context, "音频生成失败，系统语音可能还没就绪", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }) { Text("生成音频", fontSize = 10.5.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 对话页“本角色音色”弹窗：只改当前角色，不动全局设置。 */
@Composable
fun VoiceCastDialog(
    app: PersonaChatApp,
    personaName: String,
    position: String,
    fullText: String,
    onDismiss: () -> Unit,
) {
    var cast by remember { mutableStateOf(VoiceForge.castFor(app, personaName, position, fullText)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${personaName.substringBefore(" ·")} 的音色", fontSize = 15.sp) },
        text = {
            Column {
                Text("基础声线：${Voice.profile(cast.profileId).label}", fontSize = 11.sp)
                Text("音高 ${"%.2f".format(cast.pitch)} · 语速 ${"%.2f".format(cast.rate)}" + if (cast.voiceName.isNotBlank()) "\n系统音色：${cast.voiceName}" else "", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(2.dp))
                Text("同一角色会一直用这条音色；重掷只影响这个角色。", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                TextButton(onClick = {
                    VoiceForge.render(context, cast, "……你来了。先别说话，让我听一会儿。") { ok, file ->
                        scope.launch(Dispatchers.Main) {
                            if (ok && file.length() > 0) shareVoice(context, file, cast.persona)
                            else Toast.makeText(context, "音频生成失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("生成音频样本", fontSize = 10.5.sp) }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { VoiceForge.speak(cast, "……你来了。先别说话，让我听一会儿。") }) { Text("试听", fontSize = 10.5.sp) }
                TextButton(onClick = {
                    Voice.stop()
                    cast = VoiceForge.regenerate(app, personaName, position, fullText)
                    VoiceForge.speak(cast, "……这样说话，你会不会更记得我？")
                }) { Text("重掷", fontSize = 10.5.sp) }
                TextButton(onClick = onDismiss) { Text("好", fontSize = 10.5.sp) }
            }
        },
    )
}


private fun shareVoice(context: Context, file: File, label: String) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$label 的音色样本")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享角色音色"))
    }.onFailure { Toast.makeText(context, "分享失败：" + (it.message ?: ""), Toast.LENGTH_SHORT).show() }
}
