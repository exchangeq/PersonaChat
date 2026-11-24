package com.wunding.personachat.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 可下载的 GGUF 模型条目：HF 主源 + ModelScope 国内镜像。 */
data class GgufModel(
    val id: String,
    val name: String,
    val sizeMb: Int,
    val desc: String,
    val tag: String,          // general / reasoning
    val hfUrl: String,
    val msUrl: String,
    val fileName: String,
)

object ModelStore {
    val catalog = listOf(
        GgufModel(
            "qwen05", "Qwen2.5-0.5B-Instruct Q4_K_M", 370,
            "通义千问 2.5 超轻量，老手机也能跑，适合先试水", "general",
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "https://modelscope.cn/models/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/master/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        ),
        GgufModel(
            "qwen15", "Qwen2.5-1.5B-Instruct Q4_K_M", 1020,
            "中文优化，1.5B 甜点：速度与质量的平衡，推荐首选", "general",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "https://modelscope.cn/models/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/master/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        ),
        GgufModel(
            "qwen3b", "Qwen2.5-3B-Instruct Q4_K_M", 1900,
            "3B 质量更好，8GB 内存以上再选；响应会慢一些", "general",
            "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            "https://modelscope.cn/models/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/master/qwen2.5-3b-instruct-q4_k_m.gguf",
            "qwen2.5-3b-instruct-q4_k_m.gguf",
        ),
        GgufModel(
            "llama1b", "Llama-3.2-1B-Instruct Q4_K_M", 720,
            "Meta 最新小模型，英文强、指令跟随稳，中文一般", "general",
            "https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            "https://modelscope.cn/models/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/master/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        ),
        GgufModel(
            "tiny", "TinyLlama-1.1B-Chat Q4_K_M", 670,
            "超轻对话模型，内存 1GB 级别也能加载，适合备用", "general",
            "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            "https://modelscope.cn/models/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/master/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
        ),
        GgufModel(
            "ds15", "DeepSeek-R1-Distill-Qwen-1.5B Q4_K_M", 1100,
            "带推理链的 1.5B：会先想再答，适合复杂设定与逻辑题", "reasoning",
            "https://huggingface.co/unsloth/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            "https://modelscope.cn/models/unsloth/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/master/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
        ),
        GgufModel(
            "qwen15r", "Qwen2.5-1.5B-Instruct Q4_K_M（推理档）", 1020,
            "同一模型的推理用法：适合让角色做判断、推演剧情", "reasoning",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "https://modelscope.cn/models/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/master/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        ),
    )

    fun dir(ctx: Context): File = AppStorage.models(ctx)

    /** 当前模型目录（给界面展示）。 */
    fun displayPath(ctx: Context): String = dir(ctx).absolutePath

    fun downloaded(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".gguf", true) }?.sortedBy { it.name } ?: emptyList()

    fun isDownloaded(ctx: Context, m: GgufModel): Boolean = File(dir(ctx), m.fileName).let { it.exists() && it.length() > 1024 * 1024 }

    /** 下载到临时 .part（支持断点续传），校验 GGUF 魔数后改名；取消保留残片以便续传。 */
    suspend fun download(
        ctx: Context,
        model: GgufModel,
        mirror: Boolean,
        onProgress: (Long, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = File(dir(ctx), model.fileName)
        if (target.exists() && target.length() > 1024 * 1024) return@withContext target
        val part = File(dir(ctx), model.fileName + ".part")
        val url = if (mirror) model.msUrl else model.hfUrl
        try {
            if (!parallelFetch(url, part, onProgress)) fetch(url, part, onProgress)
            if (part.length() < 1024 * 1024) throw IllegalStateException("文件过小，可能被拦截或地址失效")
            val magic = head(part, 4)
            if (magic.size < 4 || magic[0] != 'G'.code.toByte() || magic[1] != 'G'.code.toByte() ||
                magic[2] != 'U'.code.toByte() || magic[3] != 'F'.code.toByte()
            ) throw IllegalStateException("不是有效 GGUF 文件")
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IllegalStateException("无法写入模型目录")
            target
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e
        }
    }

    /** 自定义直链下载：文件名取自 URL，非法字符替换；同样支持断点续传。 */
    suspend fun downloadUrl(
        ctx: Context,
        url: String,
        onProgress: (Long, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val raw = url.substringBefore('?').substringAfterLast('/').ifBlank { "custom.gguf" }
        val name = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").let { if (it.endsWith(".gguf", true)) it else "$it.gguf" }
        val target = File(dir(ctx), name)
        val part = File(dir(ctx), name + ".part")
        if (!parallelFetch(url, part, onProgress)) fetch(url, part, onProgress)
        if (part.length() < 1024 * 1024) throw IllegalStateException("文件过小，链接可能不是直链")
        val magic = head(part, 4)
        if (magic.size < 4 || magic[0] != 'G'.code.toByte() || magic[1] != 'G'.code.toByte() ||
            magic[2] != 'U'.code.toByte() || magic[3] != 'F'.code.toByte()
        ) throw IllegalStateException("不是有效 GGUF 文件")
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IllegalStateException("无法写入模型目录")
        target
    }

    /**
     * 4 段 Range 并行下载：HF / ModelScope 对单连接限速，多连接通常能明显提速。
     * 返回 false 表示不支持或任一段失败，调用方回退单流（会清理残片）。
     */
    private fun parallelFetch(url: String, part: File, onProgress: (Long, Long) -> Unit): Boolean {
        val total = probeTotal(url) ?: return false
        if (total < 8L * 1024 * 1024) return false
        runCatching { part.delete() }
        val threads = 4
        val chunk = total / threads
        val done = java.util.concurrent.atomic.AtomicLong(0)
        val errors = java.util.concurrent.atomic.AtomicInteger(0)
        val started = System.currentTimeMillis()
        var lastEmit = 0L
        val workers = (0 until threads).map { i ->
            val start = i * chunk
            val end = if (i == threads - 1) total - 1 else start + chunk - 1
            kotlin.concurrent.thread {
                runCatching {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 20000
                        readTimeout = 60000
                        setRequestProperty("User-Agent", "PersonaChat/1.4")
                        setRequestProperty("Accept", "application/octet-stream")
                        setRequestProperty("Range", "bytes=$start-$end")
                    }
                    try {
                        if (conn.responseCode != 206) throw IllegalStateException("不支持分段")
                        java.io.RandomAccessFile(part, "rw").use { raf ->
                            raf.seek(start)
                            conn.inputStream.use { input ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n <= 0) break
                                    raf.write(buf, 0, n)
                                    val d = done.addAndGet(n.toLong())
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmit > 120 || d >= total) {
                                        lastEmit = now
                                        onProgress(d, total)
                                    }
                                }
                            }
                        }
                    } finally { conn.disconnect() }
                }.onFailure { errors.incrementAndGet() }
            }
        }
        workers.forEach { it.join() }
        if (errors.get() > 0 || part.length() != total) {
            runCatching { part.delete() }
            return false
        }
        onProgress(total, total)
        return true
    }

    /** 探测文件总大小（Range 0-0 的 Content-Range），失败返回 null。 */
    private fun probeTotal(url: String): Long? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("User-Agent", "PersonaChat/1.4")
                setRequestProperty("Range", "bytes=0-0")
            }
            try {
                if (conn.responseCode != 206) null
                else conn.getHeaderField("Content-Range").orEmpty().substringAfter('/').trim().toLongOrNull()
            } finally { conn.disconnect() }
        } catch (_: Throwable) { null }
    }

    /** 断点续传：有 .part 就带 Range 续，服务器不支持则从头写。 */
    private suspend fun fetch(url: String, part: File, onProgress: (Long, Long) -> Unit) {
        val start = if (part.exists()) part.length() else 0L
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20000
            readTimeout = 60000
            setRequestProperty("User-Agent", "PersonaChat/1.4")
            setRequestProperty("Accept", "application/octet-stream")
            if (start > 0) setRequestProperty("Range", "bytes=$start-")
        }
        try {
            val code = conn.responseCode
            val append = code == 206 && start > 0
            if (code !in 200..299) throw IllegalStateException("HTTP $code，换镜像或稍后重试")
            if (!append && part.exists()) part.delete()
            val base = if (append) start else 0L
            val total = if (conn.contentLengthLong > 0) base + conn.contentLengthLong else 0L
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = base
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun head(f: File, n: Int): ByteArray {
        java.io.FileInputStream(f).use { input ->
            val b = ByteArray(n)
            var off = 0
            while (off < n) {
                val r = input.read(b, off, n - off)
                if (r <= 0) break
                off += r
            }
            return if (off == n) b else b.copyOf(off)
        }
    }
}
