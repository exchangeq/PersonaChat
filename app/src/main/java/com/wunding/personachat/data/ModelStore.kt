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

    /** 下载到临时 .part，校验 GGUF 魔数后改名；取消时清理残片。 */
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
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20000
                readTimeout = 60000
                setRequestProperty("User-Agent", "PersonaChat/1.3")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code，换镜像或稍后重试")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done, if (total > 0) total else 0L)
                    }
                }
            }
            if (part.length() < 1024 * 1024) throw IllegalStateException("文件过小，可能被拦截或地址失效")
            // GGUF 魔数校验
            val magic = part.inputStream().use { it.readNBytes(4) }
            if (magic.size < 4 || magic[0] != 'G'.code.toByte() || magic[1] != 'G'.code.toByte() ||
                magic[2] != 'U'.code.toByte() || magic[3] != 'F'.code.toByte()
            ) throw IllegalStateException("不是有效 GGUF 文件")
            if (target.exists()) target.delete()
            part.renameTo(target)
            target
        } catch (e: CancellationException) {
            runCatching { part.delete() }
            throw e
        } catch (e: Throwable) {
            runCatching { part.delete() }
            throw e
        } finally {
            conn?.disconnect()
        }
    }

    /** 自定义直链下载：文件名取自 URL，非法字符替换。 */
    suspend fun downloadUrl(
        ctx: Context,
        url: String,
        onProgress: (Long, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val raw = url.substringBefore('?').substringAfterLast('/').ifBlank { "custom.gguf" }
        val name = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").let { if (it.endsWith(".gguf", true)) it else "$it.gguf" }
        val target = File(dir(ctx), name)
        val part = File(dir(ctx), name + ".part")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20000
                readTimeout = 60000
                setRequestProperty("User-Agent", "PersonaChat/1.3")
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n); done += n
                        onProgress(done, if (total > 0) total else 0L)
                    }
                }
            }
            if (part.length() < 1024 * 1024) throw IllegalStateException("文件过小，链接可能不是直链")
            val magic = part.inputStream().use { it.readNBytes(4) }
            if (magic.size < 4 || magic[0] != 'G'.code.toByte() || magic[1] != 'G'.code.toByte() ||
                magic[2] != 'U'.code.toByte() || magic[3] != 'F'.code.toByte()
            ) throw IllegalStateException("不是有效 GGUF 文件")
            if (target.exists()) target.delete()
            part.renameTo(target)
            target
        } catch (e: CancellationException) {
            runCatching { part.delete() }; throw e
        } catch (e: Throwable) {
            runCatching { part.delete() }; throw e
        } finally {
            conn?.disconnect()
        }
    }
}
