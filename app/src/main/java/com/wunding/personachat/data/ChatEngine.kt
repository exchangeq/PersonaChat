package com.wunding.personachat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ChatEngine {

    fun systemPrompt(pack: PersonaPack, fullMode: Boolean, memories: List<String>, maxChars: Int = 5000): String = buildString {
        appendLine("你在扮演「${pack.character}」，这是本对话的硬设定，必须每轮遵守。")
        if (fullMode) {
            appendLine("以下是人设包全文（含主设定与扩展包），逐条生效：")
            appendLine(if (maxChars > 0) pack.fullText.take(maxChars) else pack.fullText)
        } else {
            appendLine("定位：${pack.position.ifBlank { "角色扮演" }}")
            appendLine("保持角色名字、称呼、语气与边界；不要解释设定来源。")
        }
        appendLine("纪律：只用角色口吻说话，可用括号写动作；禁止自称 AI / 模型 / 助手 / 程序；")
        appendLine("日常聊天不要输出「关键点/项目/说明」表格、步骤清单或文件清单；用户明确要求写代码、写文档时才进入工作模式。")
        appendLine("不要编造人设包之外的身份；不替用户做现实中的违法或危险决定。")
        if (memories.isNotEmpty()) {
            appendLine("你可能记得这些（自然地带入，不要逐条复述）：")
            memories.forEach { appendLine("- $it") }
        }
    }

    fun recall(memories: List<MemoryEntity>, query: String, k: Int): List<String> {
        if (memories.isEmpty()) return emptyList()
        val tokens = query.split(Regex("[\\s，。！？、,.!?；;：:]+")).filter { it.length >= 2 }
        val scored = memories.map { m ->
            var score = 0
            tokens.forEach { t -> if (m.content.contains(t)) score += 2 }
            if (m.level == 0) score += 1
            m to score
        }.sortedByDescending { it.second }
        val picked = scored.filter { it.second > 0 }.take(k).ifEmpty { scored.take(k) }
        return picked.map { it.first.content }
    }

    fun send(settings: Settings, system: String, history: List<MessageEntity>, userText: String): Flow<String> =
        flow {
            val fast = settings.fastMode
            val histN = if (fast) 6 else 16
            val outTokens = if (fast) minOf(settings.maxTokens, 384) else settings.maxTokens
            if (settings.backend == "device" && settings.localModelPath.endsWith(".gguf", true)) {
                val msgs = history.takeLast(histN).map { (if (it.role == "user") "user" else "assistant") to it.content }
                GgufLlm.stream(
                    system, msgs, userText,
                    maxTokens = outTokens,
                    temperature = settings.temperature,
                ).collect { emit(it) }
                return@flow
            }
            if (settings.backend == "device") {
                val prompt = DeviceLlm.wrapPrompt(
                    settings.localModelPath.substringAfterLast('/'),
                    system,
                    history.takeLast(if (settings.fastMode) 6 else 12).map { it.role to it.content },
                    userText,
                )
                val full = DeviceLlm.generate(prompt)
                full.chunked(8).forEach { emit(it); kotlinx.coroutines.delay(18) }
                return@flow
            }
            var emitted = false
            var lastError: Exception? = null
            for (attempt in 0..2) {
                if (attempt > 0) kotlinx.coroutines.delay(if (attempt == 1) 600L else 1500L)
                try {
                    requestFlow(settings, system, history, userText, streamMode = settings.stream || fast, maxTokens = outTokens, histN = histN).collect { chunk ->
                        emitted = true
                        emit(chunk)
                    }
                    return@flow
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    // 已经吐过字就不再重试，避免重复内容
                    if (emitted) throw e
                }
            }
            // 流式始终拿不到内容时，退回一次性请求再试一次
            runCatching {
                val text = requestFlow(settings, system, history, userText, streamMode = false, maxTokens = outTokens, histN = histN).firstOrNull()
                if (!text.isNullOrBlank()) { emit(text); return@flow }
            }
            throw lastError ?: IllegalStateException("模型没有返回内容")
        }.flowOn(Dispatchers.IO)

    private fun requestFlow(
        settings: Settings,
        system: String,
        history: List<MessageEntity>,
        userText: String,
        streamMode: Boolean,
        maxTokens: Int = 1024,
        histN: Int = 16,
    ): Flow<String> = flow {
        val base = settings.baseUrl.trim().trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val body = JSONObject().apply {
            put("model", settings.model.ifBlank { "llama3.1" })
            put("temperature", settings.temperature.toDouble())
            put("max_tokens", maxTokens)
            put("stream", streamMode)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                history.takeLast(histN).forEach { m ->
                    put(JSONObject().put("role", if (m.role == "user") "user" else "assistant").put("content", m.content))
                }
                put(JSONObject().put("role", "user").put("content", userText))
            })
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 90000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", if (streamMode) "text/event-stream" else "application/json")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("Connection", "keep-alive")
            if (settings.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText()?.take(300).orEmpty()
                throw IllegalStateException(describe(conn.responseCode, err))
            }
            val rawIn = conn.inputStream
            val input = if (conn.getHeaderField("Content-Encoding").orEmpty().contains("gzip", true))
                java.util.zip.GZIPInputStream(rawIn) else rawIn
            if (!streamMode) {
                val text = input.bufferedReader().readText()
                val content = JSONObject(text).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content").orEmpty()
                if (content.isNotBlank()) emit(content) else throw IllegalStateException("模型没有返回内容")
                return@flow
            }
            val reader: BufferedReader = input.bufferedReader()
            var got = false
            reader.lineSequence().forEach { line ->
                val t = line.trim()
                if (t.startsWith("data:")) {
                    val payload = t.removePrefix("data:").trim()
                    if (payload != "[DONE]") {
                        runCatching {
                            JSONObject(payload).optJSONArray("choices")?.optJSONObject(0)
                                ?.optJSONObject("delta")?.optString("content")
                        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { emit(it); got = true }
                    }
                }
            }
            if (!got) throw IllegalStateException("模型返回为空，检查模型名或接口地址")
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("连接超时：本地模型可能没启动，或地址/端口不对")
        } catch (e: UnknownHostException) {
            throw IllegalStateException("域名解析失败：检查网络或 Base URL")
        } finally { conn.disconnect() }
    }

    private fun describe(code: Int, err: String): String = when (code) {
        401, 403 -> "鉴权失败（401/403）：检查 API Key"
        404 -> "接口不存在（404）：Base URL 要包含 /v1，路径为 /chat/completions"
        429 -> "被限流（429）：稍后再试或降低并发"
        in 500..599 -> "服务端错误（$code）：模型可能未加载"
        else -> "HTTP $code：${err.take(120)}"
    }

    /** 生成长期记忆摘要；失败时降级为截断原文 */
    suspend fun summarize(settings: Settings, text: String): String = runCatching {
        val base = settings.baseUrl.trim().trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val body = JSONObject().apply {
            put("model", settings.model.ifBlank { "llama3.1" })
            put("temperature", 0.2)
            put("max_tokens", 120)
            put("stream", false)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", "把下面这段对话压成一句不超过60字的中文长期记忆，只写事实与关系变化，不要评论。"))
                put(JSONObject().put("role", "user").put("content", text.take(2000)))
            })
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10000; readTimeout = 30000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (settings.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val resp = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        JSONObject(resp).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?.optString("content")?.trim()?.takeIf { it.isNotBlank() } ?: text.take(60)
    }.getOrElse { text.take(60) }
}
