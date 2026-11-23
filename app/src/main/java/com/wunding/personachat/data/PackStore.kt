package com.wunding.personachat.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class PersonaPack(
    val id: String,
    val name: String,
    val position: String,
    val version: String,
    val source: String,
    val fullText: String,
    val extNames: List<String>,
    val files: List<String>,
    val hash: String,
    val size: Int,
    val remoteUrl: String = "",
) {
    val character: String get() = name.substringBefore(" ·").ifBlank { name }
}

data class RemotePack(val id: String, val name: String, val desc: String, val file: String, val extCount: Int)

object PackStore {
    fun dir(context: Context): File = File(context.filesDir, "personas").apply { mkdirs() }

    fun list(context: Context): List<PersonaPack> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".aist") }
            ?.sortedBy { it.name }
            ?.mapNotNull { runCatching { parse(it.readBytes(), it.name, source = "本地") }.getOrNull() }
            ?: emptyList()

    fun import(context: Context, uri: Uri, nameHint: String? = null): PersonaPack {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("读不到文件")
        val pack = parse(bytes, nameHint ?: "imported.aist", source = "导入")
        val target = File(dir(context), pack.id + ".aist")
        target.writeBytes(bytes)
        return pack
    }

    fun remove(context: Context, id: String): Boolean = File(dir(context), "$id.aist").delete()

    fun parse(bytes: ByteArray, fileName: String, source: String): PersonaPack {
        val entries = LinkedHashMap<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.lowercase().endsWith(".md")) {
                    val text = zip.readBytes().toString(Charsets.UTF_8)
                    entries[e.name] = text
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        if (entries.isEmpty()) throw IllegalStateException("包内没有 .md 文件，不像是 .aist / zip 人设包")
        val aiKey = entries.keys.firstOrNull { it.substringAfterLast('/') == "AI.md" }
        if (aiKey == null) throw IllegalStateException("缺少 AI.md，无法确认包结构")
        val ai = entries[aiKey]!!
        fun field(key: String): String = ai.lineSequence()
            .firstOrNull { it.trim().startsWith("- $key：") || it.trim().startsWith("- $key:") }
            ?.substringAfter("：").orEmpty().trim()
        val name = field("名称").ifBlank { fileName.substringBeforeLast('.') }
        val position = field("定位")
        val version = field("版本").ifBlank { "1.0" }
        val exts = entries.keys.filter { it.substringAfterLast('/').startsWith("扩展包") }
            .sortedBy { it }
        val mainKey = entries.keys.firstOrNull { it.substringAfterLast('/') == "主设定.md" }
        val stripped = { s: String -> s.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n").trim() }
        val body = StringBuilder()
        body.append("【包说明 AI.md】\n").append(stripped(ai)).append("\n\n")
        if (mainKey != null) body.append("【主设定】\n").append(stripped(entries[mainKey]!!)).append("\n\n")
        exts.forEach { k ->
            body.append("【${k.substringAfterLast('/').substringBeforeLast(".md")}】\n")
                .append(stripped(entries[k]!!)).append("\n\n")
        }
        val full = body.toString().take(16000)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(12)
        return PersonaPack(
            id = safeId(name) + "-" + hash.take(6),
            name = name, position = position, version = version, source = source,
            fullText = full, extNames = exts.map { it.substringAfterLast('/').substringBeforeLast(".md") },
            files = entries.keys.sorted(), hash = hash, size = bytes.size,
            remoteUrl = "",
        )
    }

    private fun safeId(s: String): String {
        val ascii = s.map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
        return ascii.ifBlank { "pack" }.take(24)
    }

    fun fetchRemote(repo: String): List<RemotePack> {
        val url = "https://raw.githubusercontent.com/${repo.trim().trim('/')}/main/index.json"
        val body = httpGet(url)
        val arr = JSONObject(body).optJSONArray("packs") ?: throw IllegalStateException("index.json 里没有 packs")
        val out = ArrayList<RemotePack>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(RemotePack(o.optString("id"), o.optString("name"), o.optString("desc"), o.optString("file"), o.optInt("extCount")))
        }
        return out
    }

    fun downloadRemote(context: Context, repo: String, remote: RemotePack): PersonaPack {
        val urls = listOf(
            "https://raw.githubusercontent.com/${repo.trim().trim('/')}/main/${remote.file}",
            "https://cdn.jsdelivr.net/gh/${repo.trim().trim('/')}@main/${remote.file}",
            "https://fastly.jsdelivr.net/gh/${repo.trim().trim('/')}@main/${remote.file}",
        )
        var last: Exception? = null
        for (u in urls) {
            try {
                val bytes = httpGetBytes(u)
                val pack = parse(bytes, remote.file.substringAfterLast('/'), source = "远程")
                File(dir(context), pack.id + ".aist").writeBytes(bytes)
                return pack
            } catch (e: Exception) { last = e }
        }
        throw IllegalStateException("下载失败（直连与两个 CDN 都不通）：${last?.message ?: "未知"}")
    }

    private fun httpGet(url: String): String = httpGetBytes(url).toString(Charsets.UTF_8)

    private fun httpGetBytes(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 25000
        c.setRequestProperty("User-Agent", "PersonaChat")
        return try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }
}
