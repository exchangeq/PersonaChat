package com.wunding.personachat.data

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 对话朗读与角色音色渲染。
 *
 * 设计：系统中文 TTS 负责“发音”，本文件的 VoiceTimbre + VoiceDsp 负责“音色”。
 * 同一段文字先合成成 WAV，再按角色种子做共振峰/气声/饱和度/空间感处理，
 * 所以导出的 WAV 是这条角色独有的原创音频，不是简单调 pitch。
 */
data class VoiceProfile(
    val id: String,
    val label: String,
    val pitch: Float,
    val rate: Float,
    val desc: String,
    /** 系统 TTS 音色名；为空表示自动选择 */
    val voiceId: String = "",
    val engine: String = "system",
    /** 角色音色种子；用于生成确定性的音色参数 */
    val seed: Int = 0,
)

/** 角色音色的 DSP 参数。数值都不大，避免听起来像变声器。 */
data class VoiceTimbre(
    val warmth: Float = 0.10f,
    val brightness: Float = 0.0f,
    val breath: Float = 0.0f,
    val drive: Float = 0.0f,
    val room: Float = 0.0f,
    val formant: Float = 0.0f,
) {
    companion object {
        fun of(seed: Int, profileId: String): VoiceTimbre {
            val s = kotlin.math.abs(seed)
            val n = { k: Int -> ((s / k) % 100) / 100f }
            return when (profileId) {
                "whisper" -> VoiceTimbre(0.22f + n(1) * 0.12f, -0.08f, 0.10f + n(7) * 0.05f, 0.0f, 0.10f + n(11) * 0.06f, 0.10f)
                "cool" -> VoiceTimbre(0.06f + n(3) * 0.08f, 0.16f + n(5) * 0.08f, 0.0f, 0.04f + n(9) * 0.03f, 0.02f, -0.12f)
                "deep" -> VoiceTimbre(0.24f + n(2) * 0.10f, -0.12f, 0.0f, 0.05f, 0.04f, -0.20f)
                "young" -> VoiceTimbre(0.04f, 0.18f + n(4) * 0.10f, 0.0f, 0.02f, 0.01f, 0.16f)
                "soft" -> VoiceTimbre(0.16f + n(6) * 0.10f, 0.04f, 0.02f, 0.0f, 0.03f, 0.04f)
                else -> VoiceTimbre(0.10f + n(8) * 0.08f, n(12) * 0.08f, 0.0f, 0.0f, 0.02f, 0.0f)
            }
        }
    }
}

private class RenderJob(
    val file: File,
    val profile: VoiceProfile,
    val cb: (Boolean, File) -> Unit,
) {
    val done = AtomicBoolean(false)
}

object Voice {
    val profiles = listOf(
        VoiceProfile("default", "默认", 1.0f, 1.0f, "系统中文音色，最稳"),
        VoiceProfile("soft", "温柔", 1.12f, 0.94f, "偏高偏慢，适合治愈系"),
        VoiceProfile("cool", "清冷", 0.92f, 1.02f, "偏低偏快，适合冷淡/毒舌"),
        VoiceProfile("young", "少年", 1.20f, 1.08f, "偏高偏快，适合元气角色"),
        VoiceProfile("deep", "低沉", 0.78f, 0.88f, "低而慢，适合成熟角色"),
        VoiceProfile("whisper", "耳语", 1.05f, 0.82f, "很慢，适合病娇/黏人"),
    )

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var initError = ""
    private val pending = ArrayDeque<Pair<String, VoiceProfile>>()
    private val lock = Any()
    private val renderJobs = ConcurrentHashMap<String, RenderJob>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var speaking: Boolean = false
        private set
    private val speakingIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun init(context: Context) {
        if (tts != null) return
        val app = context.applicationContext
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // 回调可能早于 tts 字段赋值，post 到主线程再配置，消除初始化竞态。
                main.post { configureEngine(tts) }
            } else {
                ready = false
                initError = "系统 TTS 初始化失败（status=$status），请到系统设置安装中文语音引擎"
            }
        }
    }

    private fun configureEngine(t: TextToSpeech?) {
        if (t == null) {
            ready = false
            initError = "TTS 初始化被释放，请点“重新检测语音引擎”"
            return
        }
        val lang = runCatching { t.setLanguage(Locale.SIMPLIFIED_CHINESE) }.getOrDefault(-1)
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            ready = false
            initError = "系统没有可用的中文语音数据，请安装中文 TTS 引擎"
            return
        }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speaking = true }
            override fun onDone(utteranceId: String?) = finishSpeak(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onTtsError(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = onTtsError(utteranceId)
        })
        ready = true
        initError = ""
        flushPending()
    }

    private fun onTtsError(utteranceId: String?) {
        if (utteranceId?.startsWith("render-") == true) {
            finishRender(utteranceId, false)
        } else {
            if (utteranceId != null) speakingIds.remove(utteranceId)
            if (speakingIds.isEmpty()) speaking = false
        }
    }

    private fun finishSpeak(utteranceId: String?) {
        if (utteranceId != null) speakingIds.remove(utteranceId)
        if (speakingIds.isEmpty()) speaking = false
        finishRender(utteranceId, true)
    }

    private fun flushPending() {
        val items = synchronized(lock) {
            val list = pending.toList(); pending.clear(); list
        }
        items.forEach { (text, p) -> speakNow(text, p) }
    }

    /** 重新初始化：系统语音引擎装好后不用重启 App。 */
    fun reinit(context: Context) {
        runCatching { tts?.shutdown() }
        tts = null; ready = false; initError = ""
        speakingIds.clear(); speaking = false
        synchronized(lock) { pending.clear() }
        init(context)
    }

    fun statusText(): String = when {
        ready -> "就绪 · 中文音色 ${availableVoiceNames().size} 个"
        tts == null -> "未初始化"
        initError.isNotBlank() -> initError
        else -> "正在初始化…"
    }

    fun profile(id: String): VoiceProfile = profiles.firstOrNull { it.id == id } ?: profiles.first()

    /** 当前可离线使用的中文音色；优先过滤需要联网下载的音色，避免朗读到一半没声。 */
    fun availableVoiceNames(): List<String> {
        val t = tts ?: return emptyList()
        if (!ready) return emptyList()
        return runCatching {
            val all = t.voices.orEmpty().filter { it.locale.language == "zh" && !it.isNetworkConnectionRequired }
            val list = all.map { it.name }.distinct().sorted()
            if (list.isNotEmpty()) list else t.voices.orEmpty().filter { it.locale.language == "zh" }.map { it.name }.distinct().sorted()
        }.getOrDefault(emptyList())
    }

    fun speak(text: String, profile: VoiceProfile) {
        // 同时去掉全角/半角动作括号与星号动作，避免把“（他伸手……）”读出来。
        val clean = text
            .replace(Regex("（[^）]*）"), "")
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\*[^*]*\\*"), "")
            .trim()
        if (clean.isBlank()) return
        if (!ready) {
            synchronized(lock) { pending.addLast(clean to profile) }
            if (tts == null) initError = "系统语音引擎未初始化"
            return
        }
        speakNow(clean, profile)
    }

    private fun applyProfile(t: TextToSpeech, profile: VoiceProfile) {
        // 顺序很重要：先语言，再具体 voice，最后 pitch/rate。反过来会被 setLanguage 重置。
        runCatching { t.setLanguage(Locale.SIMPLIFIED_CHINESE) }
        val wanted = profile.voiceId
        if (wanted.isNotBlank()) {
            val v = t.voices?.firstOrNull { it.name == wanted && it.locale.language == "zh" && !it.isNetworkConnectionRequired }
                ?: t.voices?.firstOrNull { it.name == wanted && it.locale.language == "zh" }
            if (v != null) {
                runCatching {
                    t.voice = v
                    t.setPitch(profile.pitch.coerceIn(0.4f, 2.0f))
                    t.setSpeechRate(profile.rate.coerceIn(0.4f, 2.0f))
                }
                return
            }
            // 选中的音色被卸载/不可用时不要静音，回落到当前默认中文音色。
        }
        t.setPitch(profile.pitch.coerceIn(0.4f, 2.0f))
        t.setSpeechRate(profile.rate.coerceIn(0.4f, 2.0f))
    }

    private fun speakNow(text: String, profile: VoiceProfile) {
        val t = tts ?: return
        applyProfile(t, profile)
        val chunks = splitForTts(text)
        if (chunks.isEmpty()) return
        speakingIds.clear()
        val stamp = System.currentTimeMillis()
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) }
        speaking = true
        chunks.forEachIndexed { i, part ->
            val id = "pc-$stamp-$i"
            speakingIds.add(id)
            val ret = t.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, id)
            if (ret == TextToSpeech.ERROR) {
                speakingIds.remove(id)
                if (speakingIds.isEmpty()) speaking = false
                initError = "系统语音引擎拒绝朗读，请换一个中文音色"
            }
        }
    }

    private fun splitForTts(text: String): List<String> {
        val max = (runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000) - 80).coerceAtLeast(300)
        if (text.length <= max) return listOf(text)
        val out = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + max, text.length)
            if (end < text.length) {
                val cut = text.lastIndexOfAny(charArrayOf('。', '！', '？', '；', '，', '\n'), end - 1)
                if (cut >= start + max / 2) end = cut + 1
            }
            out.add(text.substring(start, end))
            start = end
        }
        return out
    }

    fun stop() {
        synchronized(lock) { pending.clear() }
        speakingIds.clear()
        tts?.stop()
        speaking = false
    }

    /**
     * 渲染一条角色音色 WAV：系统 TTS 发音 + 角色 DSP 音色处理。
     * 回调只触发一次；30 秒没有完成会返回失败，避免 UI 一直转圈。
     */
    fun render(context: Context, text: String, profile: VoiceProfile, onDone: (Boolean, File) -> Unit) {
        val t = tts
        if (t == null || !ready) {
            onDone(false, File(""))
            return
        }
        val clean = text
            .replace(Regex("（[^）]*）"), "")
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\*[^*]*\\*"), "")
            .trim()
        if (clean.isBlank()) { onDone(false, File("")); return }
        val dir = File(context.filesDir, "voice").apply { mkdirs() }
        val file = File(dir, "voice-" + System.currentTimeMillis() + ".wav")
        val id = "render-" + System.currentTimeMillis() + "-" + (profile.seed and 0xffff)
        val job = RenderJob(file, profile, onDone)
        renderJobs[id] = job
        applyProfile(t, profile)
        val max = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000) - 80
        val ret = t.synthesizeToFile(clean.take(max), Bundle(), file, id)
        if (ret != TextToSpeech.SUCCESS) {
            renderJobs.remove(id)
            onDone(false, file)
            return
        }
        main.postDelayed({
            if (renderJobs.remove(id) != null && job.done.compareAndSet(false, true)) {
                runCatching { file.delete() }
                onDone(false, file)
            }
        }, 60_000L)
    }

    private fun finishRender(utteranceId: String?, ok: Boolean) {
        val id = utteranceId ?: return
        val job = renderJobs.remove(id) ?: return
        if (!job.done.compareAndSet(false, true)) return
        if (!ok || !job.file.exists() || job.file.length() < 1024) {
            runCatching { job.file.delete() }
            job.cb(false, job.file)
            return
        }
        // DSP 在后台做，避免主线程卡住；失败就回退原始 TTS 音频，保证有声音。
        Thread {
            val timbre = VoiceTimbre.of(job.profile.seed, job.profile.id)
            val processed = runCatching { VoiceDsp.process(job.file, timbre) }.getOrDefault(false)
            main.post { job.cb(job.file.exists() && job.file.length() > 0, job.file) }
        }.start()
    }

    fun release() {
        runCatching { tts?.shutdown() }
        tts = null; ready = false; initError = ""
        speakingIds.clear(); speaking = false
        synchronized(lock) { pending.clear() }
        renderJobs.clear()
    }
}
