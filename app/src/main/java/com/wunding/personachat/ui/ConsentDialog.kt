package com.wunding.personachat.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding

@Composable
fun MatureConsentDialog(personaName: String, onAgree: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text("内容分级 · 需要确认", fontSize = 16.sp) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("「$personaName」含病娇 / 囚禁 / 极限向设定。", fontSize = 12.sp)
                Text("继续前请确认：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "· 我已满 18 岁；\n" +
                        "· 这是成年人之间自愿的虚构角色扮演，不对应现实；\n" +
                        "· 现实中的拘禁、威胁、伤害是违法的，不模仿、不实施；\n" +
                        "· 任何一方说「停」就立即结束，不纠缠、不报复；\n" +
                        "· 我可以随时退出对话并永久删除记录。",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onAgree) { Text("我已满 18 岁并同意", fontSize = 12.sp) } },
        dismissButton = { TextButton(onClick = onDecline) { Text("退出", fontSize = 12.sp) } },
    )
}
