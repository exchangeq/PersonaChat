package com.wunding.personachat.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 对话朗读。现在用系统 TTS（离线中文音色，按角色切换 pitch/rate 近似区分）；
 * VoiceProfile 预留了 voiceId/engine/model 字段，后续接角色语音合成（如 Bert-VITS2 / GPT-SoVITS）
 * 时，只要替换 speak() 里的合成实现，上层 UI 与设置不用改。
 */
data class VoiceProfile(
    val id: String,
    val label: String,
    val pitch: Float,
    val rate: Float,
    val desc: String,
    /** 预留：未来角色语音模型的音色 ID / 模型名，当前为空 */
    val voiceId: String = "",
    val engine: String = "system",
)

object Voice {
    val profiles = listOf(
        VoiceProfile("default", "默认", 1.0f, 1.0f, "系统中文音色，最稳"),
        VoiceProfile("soft", "温柔", 1.12f, 0.94f, "偏高偏慢，适合治愈系"),
        VoiceProfile("cool", "清冷", 0.92f, 1.02f, "偏低偏快，适合冷淡/毒舌"),
        VoiceProfile("young", "少年", 1.20f, 1.08f, "偏高偏快，适合元气角色"),
        VoiceProfile("deep", "低沉", 0.78f, 0.88f, "低而慢，适合成熟角色"),
        VoiceProfile("whisper", "耳语", 1.05f, 0.82f, "很慢，适合病娇/黏人"),
    )

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, VoiceProfile>? = null
    var speaking: Boolean = false
        private set

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.SIMPLIFIED_CHINESE
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { speaking = true }
                    override fun onDone(utteranceId: String?) { speaking = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { speaking = false }
                })
                pending?.let { (text, p) -> pending = null; speakNow(text, p) }
            }
        }
    }

    fun speak(text: String, profile: VoiceProfile) {
        val clean = text.replace(Regex("（[^）]*）"), "").replace(Regex("\\*[^*]*\\*"), "").trim()
        if (clean.isBlank()) return
        if (!ready) { pending = clean to profile; return }
        speakNow(clean.take(1200), profile)
    }

    private fun speakNow(text: String, profile: VoiceProfile) {
        val t = tts ?: return
        t.setPitch(profile.pitch)
        t.setSpeechRate(profile.rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pc-" + System.currentTimeMillis())
    }

    fun stop() {
        tts?.stop()
        speaking = false
    }

    fun release() {
        tts?.shutdown(); tts = null; ready = false
    }
}
