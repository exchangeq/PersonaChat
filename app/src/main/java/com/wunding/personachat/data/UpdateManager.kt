package com.wunding.personachat.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(val version: String, val url: String, val notes: String, val size: Long)

object UpdateManager {
    fun check(repo: String): AppUpdate? {
        val r = repo.trim().trim('/')
        val conn = (URL("https://api.github.com/repos/$r/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000; readTimeout = 20000
            setRequestProperty("User-Agent", "PersonaChat")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        if (conn.responseCode !in 200..299) throw IllegalStateException("更新检查失败 HTTP ${conn.responseCode}")
        val body = conn.inputStream.bufferedReader().readText()
        val o = JSONObject(body)
        val tag = o.optString("tag_name").removePrefix("v").removePrefix("V")
        val assets = o.optJSONArray("assets") ?: return null
        var apkUrl = ""; var size = 0L
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                apkUrl = a.optString("browser_download_url"); size = a.optLong("size"); break
            }
        }
        if (apkUrl.isBlank()) return null
        return AppUpdate(tag, apkUrl, o.optString("body"), size)
    }

    fun download(context: Context, update: AppUpdate, onProgress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "PersonaChat-${update.version}.apk")
        if (out.length() == update.size && update.size > 0) return out
        val conn = (URL(update.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 60000
            setRequestProperty("User-Agent", "PersonaChat")
        }
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n); total += n
                    if (update.size > 0) onProgress(((total * 100) / update.size).toInt())
                }
            }
        }
        return out
    }

    fun install(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
