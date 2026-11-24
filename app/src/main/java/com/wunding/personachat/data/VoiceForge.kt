package com.wunding.personachat.data

import android.content.Context
import kotlin.math.abs

/** 一个角色固定下来的一条音色。同一个角色再次进入对话，音高/语速/系统音色都不变。 */
data class CastVoice(
    val persona: String,
    val profileId: String,
    val pitch: Float,
    val rate: Float,
    val seed: Int,
    val voiceName: String,
) {
    fun profile(): VoiceProfile {
        val base = Voice.profile(profileId)
        return base.copy(
            label = persona.substringBefore(" ·").ifBlank { base.label },
            pitch = pitch,
            rate = rate,
            voiceId = voiceName,
            desc = "角色专属音色 · " + base.label,
        )
    }
}

/**
 * 角色音色引擎：不依赖云端，按人设文本给每个角色派生独立音色。
 * 规则是“基础声线 + 确定性微调 + 系统可用中文音色选择”，
 * 所以同一个角色重进对话仍是同一把声音；想换声线可以手动“重掷”。
 */
object VoiceForge {
    private const val PREFS = "voice_forge"

    fun castFor(context: Context, personaName: String, position: String = "", fullText: String = ""): CastVoice {
        val name = personaName.substringBefore(" ·").ifBlank { "角色" }
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = p.getString(key(name), null)
        if (saved != null) {
            parse(saved, name)?.let { c ->
                // 首次启动时系统 TTS 还没就绪，拿到可用音色后补上，不改变音高/语速。
                if (c.voiceName.isBlank()) {
                    val v = pickVoice(name, c.seed)
                    if (v.isNotBlank()) {
                        val fixed = c.copy(voiceName = v); save(p, fixed); return fixed
                    }
                }
                return c
            }
        }
        val c = derive(name, position, fullText, abs(name.hashCode()) + 1)
        save(p, c)
        return c
    }

    fun regenerate(context: Context, personaName: String, position: String = "", fullText: String = ""): CastVoice {
        val name = personaName.substringBefore(" ·").ifBlank { "角色" }
        val old = castFor(context, name, position, fullText)
        val c = derive(name, position, fullText, old.seed + 7919)
        save(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), c)
        return c
    }

    fun profileFor(context: Context, personaName: String, position: String = "", fullText: String = ""): VoiceProfile =
        castFor(context, personaName, position, fullText).profile()

    fun speak(cast: CastVoice, text: String) = Voice.speak(text, cast.profile())

    fun render(context: android.content.Context, cast: CastVoice, text: String, onDone: (Boolean, java.io.File) -> Unit) =
        Voice.render(context, text, cast.profile(), onDone)

    private fun derive(name: String, position: String, fullText: String, seed: Int): CastVoice {
        val blob = (name + " " + position + " " + fullText.take(2200)).lowercase()
        val base = when {
            listOf("病娇", "黏人", "依存", "占有", "囚禁", "耳语", "偏执").any { blob.contains(it) } -> Voice.profile("whisper")
            listOf("毒舌", "清冷", "傲娇", "机械", "吸血鬼", "冷淡").any { blob.contains(it) } -> Voice.profile("cool")
            listOf("成熟", "低沉", "叔", "父亲", "年长").any { blob.contains(it) } -> Voice.profile("deep")
            listOf("少年", "元气", "活泼", "猫娘", "狐娘", "少女", "男娘", "妹妹").any { blob.contains(it) } -> Voice.profile("young")
            else -> listOf("soft", "cool", "young").let { Voice.profile(it[abs(seed) % it.size]) }
        }
        val dp = ((seed % 7) - 3) * 0.018f
        val dr = (((seed / 7) % 5) - 2) * 0.014f
        return CastVoice(
            persona = name,
            profileId = base.id,
            pitch = (base.pitch + dp).coerceIn(0.62f, 1.42f),
            rate = (base.rate + dr).coerceIn(0.72f, 1.22f),
            seed = seed,
            voiceName = pickVoice(name, seed),
        )
    }

    private fun pickVoice(name: String, seed: Int): String {
        val voices = Voice.availableVoiceNames()
        if (voices.isEmpty()) return ""
        // 优先按角色名稳定选；系统只有一个中文音色时自然都落到它。
        return voices[abs(name.hashCode() + seed) % voices.size]
    }

    private fun key(name: String) = "cast:" + name
    private fun save(p: android.content.SharedPreferences, c: CastVoice) {
        p.edit().putString(key(c.persona), "${c.profileId}|${c.pitch}|${c.rate}|${c.seed}|${c.voiceName}").apply()
    }
    private fun parse(s: String, name: String): CastVoice? = runCatching {
        val a = s.split("|")
        CastVoice(name, a[0], a[1].toFloat(), a[2].toFloat(), a[3].toInt(), a.getOrElse(4) { "" })
    }.getOrNull()
}
