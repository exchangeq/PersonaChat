package com.wunding.personachat.data

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * GGUF 本地推理：llama.cpp（官方 Android arm64 预编译库 + 自写 JNI）。
 * 支持 Qwen / Llama / Gemma / Phi 等 GGUF；优先使用模型内置 chat template，
 * 无模板时回退到 user/assistant 纯文本前缀。全程离线，不联网。
 */
object GgufLlm {
    @Volatile private var handle: Long = 0L
    @Volatile private var loadedPath: String = ""
    @Volatile private var lastError: String = ""
    @Volatile private var generating: Boolean = false
    private var app: Context? = null

    fun init(context: Context) { app = context.applicationContext }

    val isLoaded: Boolean get() = handle != 0L
    val path: String get() = loadedPath

    /** 当前 GGUF 加载时使用的上下文窗口，用于给输出/人设分配预算。 */
    fun contextLimit(): Int = if (handle != 0L) safeContext(loadedPath) else 0

    fun status(): String = when {
        handle != 0L -> "GGUF 已加载：" + loadedPath.substringAfterLast('/') + "（" + nativeInfo(handle) + "）"
        lastError.isNotBlank() -> lastError
        else -> "未加载 GGUF 模型"
    }

    @Synchronized
    fun load(path: String, threads: Int = 4): String {
        if (generating) return "正在生成回复，先点停止再切换模型"
        if (path.isBlank()) return "先导入 .gguf 模型文件"
        if (!java.io.File(path).exists()) return "模型文件不存在：$path"
        if (handle != 0L && loadedPath == path) return "已加载"
        unload()
        lastError = ""
        val f = java.io.File(path)
        if (f.length() < 1024 * 1024) return "模型文件过小，可能没下载完整".also { lastError = it }
        val magic = runCatching { f.inputStream().use { input -> ByteArray(4).also { input.read(it) } } }.getOrNull()
        if (magic == null || String(magic, Charsets.US_ASCII) != "GGUF") {
            lastError = "不是有效 GGUF（缺少 GGUF 文件头），请重新下载模型"
            return lastError
        }
        return try {
            System.loadLibrary("personagguf")
            val totalMb = deviceTotalMb()
            val safeThreads = threads.coerceIn(1, if (totalMb <= 6144L) 2 else 4)
            handle = nativeLoad(path, safeThreads, app?.applicationInfo?.nativeLibraryDir ?: "", safeContext(path))
            if (handle == 0L) {
                lastError = "加载失败：文件无效或内存不足"
                lastError
            } else {
                loadedPath = path
                "GGUF 已加载：" + path.substringAfterLast('/')
            }
        } catch (e: Throwable) {
            lastError = "加载失败：" + (e.message ?: e.javaClass.simpleName) + "（Q4_K_M 最稳，2B 以下更适合手机）"
            lastError
        }
    }

    /** 请求停生成：native 每个 token 检查一次，最迟一个 token 后返回。 */
    fun cancel() {
        val h = handle
        if (h != 0L) runCatching { nativeCancel(h) }
    }

    fun unload() {
        if (generating) { cancel(); return }
        if (handle != 0L) runCatching { nativeFree(handle) }
        handle = 0L
        loadedPath = ""
    }

    /** 流式生成。role 只取 user / assistant，system 单独传入。 */
    fun stream(
        system: String,
        history: List<Pair<String, String>>,
        user: String,
        maxTokens: Int,
        temperature: Float,
    ): Flow<String> = callbackFlow {
        val h = handle
        if (h == 0L) {
            close(IllegalStateException("GGUF 模型未加载：设置里选 .gguf 后点加载"))
            return@callbackFlow
        }
        val roles = ArrayList<String>()
        val contents = ArrayList<String>()
        history.forEach { (r, c) ->
            roles.add(if (r == "user") "user" else "assistant")
            contents.add(c)
        }
        roles.add("user"); contents.add(user)
        val sink = TokenSink { trySend(it) }
        generating = true
        try {
            withContext(Dispatchers.Default) {
                nativeGenerate(
                    h, system, roles.toTypedArray(), contents.toTypedArray(),
                    maxTokens.coerceIn(32, 2048), temperature, sink
                )
            }
        } catch (e: Throwable) {
            close(e)
            return@callbackFlow
        } finally {
            generating = false
        }
        close()
        awaitClose { cancel() }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    class TokenSink(val cb: (String) -> Unit) {
        @Suppress("unused")
        fun onToken(s: String) = cb(s)
    }

    /** 低内存/大模型自动降上下文：宁可慢一点，也别让 LMK 把进程杀掉。 */
    private fun safeContext(path: String): Int {
        val modelMb = runCatching { java.io.File(path).length() / (1024 * 1024) }.getOrDefault(0L)
        val totalMb = runCatching {
            val am = app?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi); mi.totalMem / (1024 * 1024)
        }.getOrDefault(4096L)
        return when {
            modelMb >= 1800 || totalMb <= 4096 -> 512
            modelMb >= 1400 || totalMb <= 6144 -> 1024
            else -> 2048
        }
    }

    private fun deviceTotalMb(): Long = runCatching {
        val am = app?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi); mi.totalMem / (1024 * 1024)
    }.getOrDefault(4096L)

    private external fun nativeLoad(path: String, threads: Int, libDir: String, ctxLimit: Int): Long
    private external fun nativeGenerate(
        handle: Long, system: String, roles: Array<String>, contents: Array<String>,
        maxTokens: Int, temperature: Float, sink: TokenSink
    ): Int
    private external fun nativeCancel(handle: Long)
    private external fun nativeInfo(handle: Long): String
    private external fun nativeFree(handle: Long)
}
