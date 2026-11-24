package com.wunding.personachat.data

import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * 本地角色音色 DSP：对系统 TTS 生成的 PCM WAV 做轻量处理。
 *
 * 不是神经网络变声，也不是云端克隆；它是完全离线的“发音 + 音色塑形”：
 * 低通暖度、高频亮度、气声、饱和、短混响、formant 偏移。
 * 处理失败会返回 false，Voice 会保留原始 TTS 音频，保证至少有声音。
 */
object VoiceDsp {
    fun process(file: File, timbre: VoiceTimbre): Boolean {
        if (!file.exists() || file.length() < 44) return false
        if (timbre.warmth == 0f && timbre.brightness == 0f && timbre.breath == 0f &&
            timbre.drive == 0f && timbre.room == 0f && timbre.formant == 0f) return false
        return runCatching { processWav(file, timbre) }.getOrDefault(false)
    }

    private fun processWav(file: File, t: VoiceTimbre): Boolean {
        val bytes = file.readBytes()
        if (bytes.size < 44 || tag(bytes, 0) != "RIFF" || tag(bytes, 8) != "WAVE") return false
        var fmt = -1
        var data = -1
        var dataLen = 0
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = tag(bytes, pos)
            val len = le32(bytes, pos + 4)
            val body = pos + 8
            if (len < 0 || body + len > bytes.size) break
            when (id) {
                "fmt " -> fmt = body
                "data" -> { data = body; dataLen = len }
            }
            pos = body + len + (len and 1)
        }
        if (fmt < 0 || data < 0 || dataLen <= 0) return false
        val audioFormat = le16(bytes, fmt)
        val channels = le16(bytes, fmt + 2)
        val sampleRate = le32(bytes, fmt + 4)
        val bits = le16(bytes, fmt + 14)
        if (audioFormat != 1 || channels !in 1..2 || bits != 16 || sampleRate <= 0) return false
        val frameBytes = channels * 2
        val frames = min(dataLen / frameBytes, (bytes.size - data) / frameBytes)
        if (frames <= 0) return false

        val end = data + frames * frameBytes
        val low = FloatArray(channels)
        val prev = FloatArray(channels)
        val echoLen = maxOf(1, (sampleRate * 0.055f).toInt())
        val echo = Array(channels) { FloatArray(echoLen) }
        val echoPos = IntArray(channels)
        var rnd = (t.warmth * 100000f).toInt() or 1
        fun noise(): Float {
            rnd = rnd * 1103515245 + 12345
            return ((rnd ushr 16) and 0x7fff) / 16384f - 1f
        }
        val warmMix = (t.warmth.coerceIn(0f, 0.45f))
        val bright = t.brightness.coerceIn(-0.3f, 0.35f)
        val breath = t.breath.coerceIn(0f, 0.22f)
        val drive = 1f + t.drive.coerceIn(0f, 0.35f) * 4f
        val room = t.room.coerceIn(0f, 0.22f)
        val formant = t.formant.coerceIn(-0.25f, 0.25f)

        var p = data
        repeat(frames) {
            for (ch in 0 until channels) {
                var x = le16s(bytes, p) / 32768f
                p += 2
                // 一阶低通：暖度和 formant 偏低频，高频保留给 brightness。
                low[ch] += 0.045f * (x - low[ch])
                x += (low[ch] - x) * warmMix
                x += (x - low[ch]) * bright
                if (formant != 0f) x += (x - prev[ch]) * formant * 0.35f
                prev[ch] = x
                if (breath > 0f) x += noise() * breath * 0.018f
                if (drive > 1.01f) x = tanh(x * drive)
                if (room > 0f) {
                    val e = echo[ch][echoPos[ch]]
                    echo[ch][echoPos[ch]] = x
                    echoPos[ch] = (echoPos[ch] + 1) % echoLen
                    x += e * room * 0.45f
                }
                val v = (x * 32767f).toInt().coerceIn(-32768, 32767)
                bytes[p - 2] = (v and 0xff).toByte()
                bytes[p - 1] = ((v ushr 8) and 0xff).toByte()
            }
        }
        // 只处理前 frames 个采样，尾部保持原样。
        if (end > data) file.writeBytes(bytes)
        return true
    }

    private fun tanh(x: Float): Float {
        val e = exp((2f * x).coerceIn(-12f, 12f))
        return (e - 1f) / (e + 1f)
    }

    private fun tag(b: ByteArray, at: Int): String = String(b, at, 4, Charsets.US_ASCII)

    private fun le16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8)

    private fun le16s(b: ByteArray, at: Int): Int {
        val v = le16(b, at)
        return if (v >= 0x8000) v - 0x10000 else v
    }

    private fun le32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8) or
            ((b[at + 2].toInt() and 0xff) shl 16) or ((b[at + 3].toInt() and 0xff) shl 24)
}
