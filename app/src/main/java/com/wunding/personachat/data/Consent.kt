package com.wunding.personachat.data

import android.content.Context

/**
 * 内容分级与同意协议：
 * 含病娇 / 囚禁 / 极限 / 监禁 / 调教等标签的人设，进入对话前必须确认
 * 「已满 18 岁 + 虚构 + 双方自愿 + 可随时退出」。
 * 记录按人设名保存；换包同名也沿用（同名视为同一角色）。
 */
object Consent {
    const val VERSION = "1"
    private val flags = listOf("病娇", "囚禁", "极限", "监禁", "调教", "黑化")

    fun required(name: String, position: String = "", text: String = ""): Boolean {
        val hay = name + " " + position + " " + text.take(2000)
        return flags.any { hay.contains(it) }
    }

    fun accepted(ctx: Context, personaName: String): Boolean =
        ctx.getSharedPreferences("consent", 0).getString(key(personaName), "") == VERSION

    fun accept(ctx: Context, personaName: String) {
        ctx.getSharedPreferences("consent", 0).edit().putString(key(personaName), VERSION).apply()
    }

    fun revoke(ctx: Context, personaName: String) {
        ctx.getSharedPreferences("consent", 0).edit().remove(key(personaName)).apply()
    }

    private fun key(name: String): String = "c-" + name.hashCode()
}
