package com.wunding.personachat.data

import org.json.JSONArray
import org.json.JSONObject

/** 会话导入/导出：与 PocketForge 导出格式兼容，也能读 SillyTavern 的 jsonl。 */
data class ImportedChat(
    val title: String,
    val personaName: String,
    val position: String,
    val personaFull: String,
    val messages: List<Pair<String, String>>,
    val createdAt: Long = System.currentTimeMillis(),
)

object ChatTransfer {
    fun encode(session: SessionEntity, messages: List<MessageEntity>): String = JSONObject().apply {
        put("format", "personachat-chat-v1")
        put("exportedAt", System.currentTimeMillis())
        put("persona_snapshot", JSONObject().apply {
            put("name", session.personaName)
            put("position", session.position)
            put("fullText", session.personaFull)
            put("fullMode", session.fullMode)
        })
        put("session", JSONObject().apply {
            put("id", session.id)
            put("title", session.title)
            put("createdAt", session.createdAt)
        })
        put("messages", JSONArray().apply {
            messages.forEach { m -> put(JSONObject().put("role", m.role).put("content", m.content).put("ts", m.ts)) }
        })
    }.toString(2)

    fun decode(raw: String): ImportedChat {
        val text = raw.trim()
        val msgs = mutableListOf<Pair<String, String>>()
        var name = ""
        var position = ""
        var full = ""
        var title = ""
        var created = System.currentTimeMillis()
        if (text.startsWith("{")) {
            val o = JSONObject(text)
            name = firstNonBlank(
                o.optJSONObject("persona_snapshot")?.optString("name"),
                o.optJSONObject("persona")?.optString("name"),
                o.optJSONObject("character")?.optString("name"),
                o.optString("personaName"),
            )
            position = firstNonBlank(
                o.optJSONObject("persona_snapshot")?.optString("position"),
                o.optJSONObject("persona")?.optString("role"),
                o.optString("position"),
            )
            full = firstNonBlank(
                o.optJSONObject("persona_snapshot")?.optString("fullText"),
                o.optJSONObject("persona")?.optString("raw"),
                o.optString("personaFull"),
                o.optString("fullText"),
            )
            title = firstNonBlank(o.optJSONObject("session")?.optString("title"), o.optString("title"))
            created = o.optJSONObject("session")?.optLong("createdAt") ?: o.optLong("createdAt", System.currentTimeMillis())
            val arr = o.optJSONArray("messages")
            if (arr != null) parseArray(arr, msgs)
            if (full.isBlank()) {
                val ch = o.optJSONObject("character") ?: o.optJSONObject("data")
                if (ch != null) full = buildString {
                    listOf("description", "personality", "scenario").forEach { k -> ch.optString(k).takeIf { s -> s.isNotBlank() }?.let { appendLine(it) } }
                }
            }
        } else if (text.startsWith("[")) {
            parseArray(JSONArray(text), msgs)
        } else {
            // SillyTavern jsonl：每行一个 {name,is_user,mes}
            text.lineSequence().forEach { line ->
                val t = line.trim()
                if (t.startsWith("{")) runCatching {
                    val o = JSONObject(t)
                    val content = o.optString("mes").ifBlank { o.optString("content") }
                    if (content.isNotBlank()) {
                        msgs.add((if (o.optBoolean("is_user")) "user" else "assistant") to content)
                        if (name.isBlank()) name = o.optString("name")
                    }
                }
            }
        }
        require(msgs.isNotEmpty()) { "没有解析到消息；支持 PersonaChat / PocketForge JSON、SillyTavern jsonl" }
        val finalName = name.ifBlank { "导入角色" }
        return ImportedChat(
            title = title.ifBlank { "导入 · " + finalName.substringBefore(" ·") },
            personaName = finalName,
            position = position.ifBlank { "导入对话" },
            personaFull = full.ifBlank { "你正在扮演「$finalName」。保持角色口吻与边界。" },
            messages = msgs,
            createdAt = created,
        )
    }

    private fun parseArray(arr: JSONArray, out: MutableList<Pair<String, String>>) {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val content = o.optString("content").ifBlank { o.optString("mes") }
            if (content.isBlank()) continue
            val role = when {
                o.optBoolean("is_user") -> "user"
                o.optString("role") == "user" -> "user"
                o.optString("role") == "system" -> continue
                else -> "assistant"
            }
            out.add(role to content)
        }
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }.orEmpty()
}
