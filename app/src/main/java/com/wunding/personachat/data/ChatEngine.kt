package com.wunding.personachat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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
    /** 流结束原因是 length（输出被 max_tokens 截断）时发出的内部标记，不会显示给用户。 */
    private const val FINISH_LENGTH = "\u0000A3LEN"

    @Volatile private var activeConn: HttpURLConnection? = null

    /** 停止生成时主动断开正在阻塞的 HTTP 请求，避免“点了停止还在等”。 */
    fun cancelActive() {
        runCatching { activeConn?.disconnect() }
        activeConn = null
    }

    fun systemPrompt(pack: PersonaPack, fullMode: Boolean, memories: List<String>, maxChars: Int = 0): String = buildString {
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
            // 极速模式只压缩上下文，不再把输出硬砍到 384。角色长回复被截断，多半是这里造成的。
            val outTokens = if (fast) settings.maxTokens.coerceIn(320, 2048) else settings.maxTokens.coerceIn(128, 4096)
            if (settings.backend == "device" && settings.localModelPath.endsWith(".gguf", true)) {
                val msgs = history.takeLast(histN).map { (if (it.role == "user") "user" else "assistant") to it.content }
                // 小上下文机型给输出留空间，避免 prompt 把窗口吃满后只吐半句。
                val ctx = GgufLlm.contextLimit()
                val localOut = if (ctx in 1..1024) minOf(outTokens, maxOf(160, ctx / 2)) else outTokens
                GgufLlm.stream(
                    system, msgs, userText,
                    maxTokens = localOut,
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
            var tokenField = "max_tokens"
            for (attempt in 0..2) {
                if (attempt > 0) kotlinx.coroutines.delay(if (attempt == 1) 600L else 1500L)
                try {
                    val full = StringBuilder()
                    var truncated = false
                    requestFlow(settings, system, history, userText, streamMode = settings.stream || fast, maxTokens = outTokens, histN = histN, tokenField = tokenField).collect { chunk ->
                        if (chunk == FINISH_LENGTH) {
                            truncated = true
                        } else {
                            emitted = true
                            full.append(chunk)
                            emit(chunk)
                        }
                    }
                    // 输出到 max_tokens 被切断，或动作括号没闭合：带着已写内容再请求一次，从断点续写，最多两次。
                    var round = 0
                    var needContinue = truncated || hasUnclosedParen(full.toString())
                    while (needContinue && full.isNotBlank() && round < 2) {
                        round++
                        needContinue = false
                        val prior = full.toString()
                        val contHistory = history + MessageEntity(
                            sessionId = history.lastOrNull()?.sessionId ?: "",
                            role = "user",
                            content = userText,
                            ts = System.currentTimeMillis(),
                        ) + MessageEntity(
                            sessionId = history.lastOrNull()?.sessionId ?: "",
                            role = "assistant",
                            content = prior,
                            ts = System.currentTimeMillis(),
                        )
                        requestFlow(
                            settings, system, contHistory,
                            "接着上一段写完。不要重复已经写过的内容，不要解释，不要重新开场。",
                            streamMode = settings.stream || fast,
                            maxTokens = outTokens,
                            histN = histN,
                            tokenField = tokenField,
                        ).collect { chunk ->
                            if (chunk == FINISH_LENGTH) {
                                needContinue = true
                            } else {
                                val clean = if (round == 1) trimOverlap(full.toString(), chunk) else chunk
                                if (clean.isNotEmpty()) { full.append(clean); emit(clean) }
                            }
                        }
                        if (hasUnclosedParen(full.toString())) needContinue = true
                    }
                    return@flow
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    // 兼容 OpenAI 新推理模型：部分接口只认 max_completion_tokens。
                    val msg = e.message.orEmpty()
                    if (!emitted && tokenField == "max_tokens" && msg.contains("max_tokens", true)) {
                        tokenField = "max_completion_tokens"
                        continue
                    }
                    // 已经吐过字就不再重试，避免重复内容
                    if (emitted) throw e
                }
            }
            // 流式始终拿不到内容时，退回一次性请求再试一次
            runCatching {
                val text = requestFlow(settings, system, history, userText, streamMode = false, maxTokens = outTokens, histN = histN, tokenField = tokenField).firstOrNull()
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
        tokenField: String = "max_tokens",
    ): Flow<String> = flow {
        val base = settings.baseUrl.trim().trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val body = JSONObject().apply {
            put("model", settings.model.ifBlank { "llama3.1" })
            put("temperature", settings.temperature.toDouble())
            put(tokenField, maxTokens)
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
        activeConn = conn
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
                val obj = runCatching { JSONObject(text) }.getOrNull()
                    ?: throw IllegalStateException("接口返回不是 JSON：${text.take(80)}")
                val choice = obj.optJSONArray("choices")?.optJSONObject(0)
                val content = sseText(obj).ifBlank {
                    contentOf(choice?.optJSONObject("message")?.opt("content"))
                }
                if (content.isBlank()) {
                    val apiErr = obj.optJSONObject("error")?.optString("message").orEmpty()
                    throw IllegalStateException(apiErr.ifBlank { "模型没有返回内容" })
                }
                emit(content)
                val reason = finishReason(obj)
                if (reason == "length" || reason == "max_tokens") emit(FINISH_LENGTH)
                return@flow
            }
            val reader: BufferedReader = input.bufferedReader()
            var got = false
            var finish: String? = null
            // 不用 lineSequence：收到 [DONE] 要能立刻 break，否则有的服务端不关连接，会一直等到超时。
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val line = reader.readLine() ?: break
                val t = line.trim()
                if (t.isEmpty() || t.startsWith(":")) continue
                if (t.startsWith("event:")) continue
                if (!t.startsWith("data:")) continue
                val payload = t.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                val obj = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                val choice = obj.optJSONArray("choices")?.optJSONObject(0)
                val delta = contentOf(choice?.optJSONObject("delta")?.opt("content"))
                if (delta.isNotEmpty()) {
                    emit(delta); got = true
                } else {
                    // 兼容 Ollama / 部分代理：正文放在 choices[0].text、message.content 或根 response。
                    val fallback = contentOf(choice?.opt("text"))
                        .ifBlank { contentOf(obj.optJSONObject("message")?.opt("content")) }
                        .ifBlank { contentOf(obj.opt("response")) }
                    if (fallback.isNotEmpty()) { emit(fallback); got = true }
                }
                val fr = finishReason(obj)
                if (fr.isNotBlank()) finish = fr
            }
            if (!got) throw IllegalStateException("模型返回为空，检查模型名或接口地址")
            if (finish == "length" || finish == "max_tokens") emit(FINISH_LENGTH)
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("连接超时：本地模型可能没启动，或地址/端口不对")
        } catch (e: UnknownHostException) {
            throw IllegalStateException("域名解析失败：检查网络或 Base URL")
        } finally {
            if (activeConn === conn) activeConn = null
            conn.disconnect()
        }
    }

    /** 括号动作没闭合，通常说明回复在网络/输出上限处被切断。 */
    private fun hasUnclosedParen(text: String): Boolean {
        if (text.isBlank()) return false
        val open = text.count { it == '（' || it == '(' }
        val close = text.count { it == '）' || it == ')' }
        return open > close
    }

    /** 续写时模型常把上一段末尾重复一遍，按最长后缀/前缀重合裁掉。 */
    private fun trimOverlap(prior: String, next: String): String {
        if (prior.isEmpty() || next.isEmpty()) return next
        val max = minOf(24, prior.length, next.length)
        for (k in max downTo 4) {
            if (prior.takeLast(k) == next.take(k)) return next.substring(k)
        }
        return next
    }

    /** 兼容 string / array / object 三种 content 形态。 */
    private fun contentOf(v: Any?): String = when (v) {
        null, JSONObject.NULL -> ""
        is String -> v
        is JSONArray -> (0 until v.length()).joinToString("") { contentOf(v.opt(it)) }
        is JSONObject -> v.optString("text").ifBlank { v.optString("content") }
        else -> ""
    }

    private fun sseText(obj: JSONObject): String {
        val choice = obj.optJSONArray("choices")?.optJSONObject(0)
        return contentOf(choice?.optJSONObject("delta")?.opt("content"))
            .ifBlank { contentOf(choice?.opt("text")) }
            .ifBlank { contentOf(obj.optJSONObject("message")?.opt("content")) }
            .ifBlank { contentOf(obj.opt("response")) }
    }

    private fun finishReason(obj: JSONObject): String {
        val choice = obj.optJSONArray("choices")?.optJSONObject(0)
        val r = choice?.optString("finish_reason").orEmpty()
        if (r.isNotBlank() && r != "null") return r
        val d = obj.optString("done_reason").orEmpty()
        return if (d.isNotBlank() && d != "null") d else ""
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
