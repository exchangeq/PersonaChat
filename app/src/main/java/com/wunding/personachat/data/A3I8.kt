package com.wunding.personachat.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * A3I8：PersonaChat 的对话专用格式。
 * 本质是一个带魔数、校验和、版本号的 JSON 容器，扩展名固定 .a3i8。
 * 除消息外还保存人设快照、长期/短期记忆和角色音色，换设备也能完整恢复。
 */
object A3I8 {
    const val EXT = "a3i8"
    const val MAGIC = "A3I8"
    const val FORMAT = "a3i8-chat-v1"

    fun encode(
        session: SessionEntity,
        messages: List<MessageEntity>,
        memories: List<MemoryEntity>,
        voice: CastVoice?,
    ): String {
        val body = JSONObject().apply {
            put("magic", MAGIC)
            put("format", FORMAT)
            put("app", "PersonaChat")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("session", JSONObject().apply {
                put("title", session.title)
                put("createdAt", session.createdAt)
                put("updatedAt", session.updatedAt)
            })
            put("persona_snapshot", JSONObject().apply {
                put("name", session.personaName)
                put("position", session.position)
                put("fullText", session.personaFull)
                put("fullMode", session.fullMode)
            })
            put("voice", voice?.let {
                JSONObject().apply {
                    put("profileId", it.profileId)
                    put("pitch", it.pitch.toDouble())
                    put("rate", it.rate.toDouble())
                    put("seed", it.seed)
                    put("voiceName", it.voiceName)
                    put("enabled", it.enabled)
                }
            } ?: JSONObject.NULL)
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().apply {
                        put("role", m.role)
                        put("content", m.content)
                        put("ts", m.ts)
                    })
                }
            })
            put("memories", JSONArray().apply {
                memories.forEach { m ->
                    put(JSONObject().apply {
                        put("level", m.level)
                        put("content", m.content)
                        put("ts", m.ts)
                        put("locked", m.locked)
                    })
                }
            })
        }
        body.put("checksum", sha256(canonical(body)))
        return body.toString(2)
    }

    fun isA3I8(raw: String): Boolean = raw.trimStart().startsWith("{") &&
        runCatching { JSONObject(raw).optString("magic") == MAGIC }.getOrDefault(false)

    fun decode(raw: String): ImportedChat {
        val o = JSONObject(raw)
        require(o.optString("magic") == MAGIC) { "不是 .a3i8 文件" }
        require(o.optString("format") == FORMAT) { "版本不支持：${o.optString("format")}" }
        val checksum = o.optString("checksum")
        if (checksum.isNotBlank()) {
            val body = JSONObject(o.toString()).apply { remove("checksum") }
            require(sha256(canonical(body)) == checksum) { "文件校验失败，可能传输中损坏" }
        }
        val ps = o.optJSONObject("persona_snapshot")
        val name = ps?.optString("name").orEmpty().ifBlank { "导入角色" }
        val position = ps?.optString("position").orEmpty().ifBlank { "导入对话" }
        val full = ps?.optString("fullText").orEmpty()
        val title = o.optJSONObject("session")?.optString("title").orEmpty()
        val created = o.optJSONObject("session")?.optLong("createdAt") ?: System.currentTimeMillis()
        val msgs = mutableListOf<Pair<String, String>>()
        o.optJSONArray("messages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val content = m.optString("content")
                if (content.isBlank()) continue
                val role = if (m.optString("role") == "user") "user" else "assistant"
                msgs.add(role to content)
            }
        }
        require(msgs.isNotEmpty()) { ".a3i8 里没有消息" }
        val mems = mutableListOf<Pair<Int, String>>()
        o.optJSONArray("memories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val c = m.optString("content")
                if (c.isNotBlank()) mems.add((m.optInt("level", 2).coerceIn(0, 2)) to c)
            }
        }
        val voice = o.optJSONObject("voice")?.let { v ->
            CastVoice(
                persona = name,
                profileId = v.optString("profileId").ifBlank { "soft" },
                pitch = v.optDouble("pitch", 1.0).toFloat().coerceIn(0.62f, 1.42f),
                rate = v.optDouble("rate", 1.0).toFloat().coerceIn(0.72f, 1.22f),
                seed = v.optInt("seed", 1),
                voiceName = v.optString("voiceName"),
                enabled = v.optBoolean("enabled", true),
            )
        }
        return ImportedChat(
            title = title.ifBlank { "导入 · " + name.substringBefore(" ·") },
            personaName = name,
            position = position,
            personaFull = full.ifBlank { "你正在扮演「$name」。保持角色口吻与边界。" },
            messages = msgs,
            memories = mems,
            voice = voice,
            createdAt = created,
        )
    }

    /** 与 JSONObject 的字段顺序无关的规范化序列化，保证导出/导入校验和稳定。 */
    private fun canonical(value: Any?): String = buildString {
        when (value) {
            is JSONObject -> {
                append('{')
                val keys = value.keys().asSequence().sorted().toList()
                keys.forEachIndexed { i, k ->
                    if (i > 0) append(',')
                    append(JSONObject.quote(k)).append(':').append(canonical(value.opt(k)))
                }
                append('}')
            }
            is JSONArray -> {
                append('[')
                for (i in 0 until value.length()) {
                    if (i > 0) append(',')
                    append(canonical(value.opt(i)))
                }
                append(']')
            }
            is String -> append(JSONObject.quote(value))
            is Number, is Boolean -> append(value.toString())
            null, JSONObject.NULL -> append("null")
            else -> append(JSONObject.quote(value.toString()))
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
