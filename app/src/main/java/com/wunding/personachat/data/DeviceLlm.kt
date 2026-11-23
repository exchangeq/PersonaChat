package com.wunding.personachat.data

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference

/**
 * 手机本地推理：MediaPipe LLM（.task 模型，Gemma 系列 int4 推荐）。
 * 模型不随 APK 分发，用户在设置里导入一次，之后离线可用。
 */
object DeviceLlm {
    @Volatile private var inst: LlmInference? = null
    @Volatile private var loadedPath: String = ""
    @Volatile private var lastError: String = ""
    private var app: Context? = null

    fun init(context: Context) { app = context.applicationContext }

    val isLoaded: Boolean get() = inst != null

    fun status(): String = when {
        inst != null -> "已加载：" + loadedPath.substringAfterLast('/')
        lastError.isNotBlank() -> lastError
        else -> "未加载模型"
    }

    @Synchronized
    fun load(path: String, backend: String): String {
        val ctx = app ?: return "应用上下文未初始化"
        if (path.isBlank()) return "先导入 .task 模型文件"
        if (!java.io.File(path).exists()) return "模型文件不存在：" + path
        if (inst != null && loadedPath == path && lastError.isBlank()) return "已加载"
        runCatching { inst?.close() }
        inst = null; loadedPath = ""; lastError = ""
        return try {
            val opts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(2048)
                .setPreferredBackend(
                    if (backend == "cpu") LlmInference.Backend.CPU else LlmInference.Backend.GPU
                )
                .build()
            inst = LlmInference.createFromOptions(ctx, opts)
            loadedPath = path
            "已加载：" + path.substringAfterLast('/')
        } catch (e: Throwable) {
            lastError = "加载失败：" + (e.message ?: e.javaClass.simpleName) + "（显存不足可换 CPU 或更小模型）"
            lastError
        }
    }

    fun generate(prompt: String): String {
        val engine = inst ?: throw IllegalStateException("本地模型未加载：到设置里导入 .task 并加载")
        return engine.generateResponse(prompt).trim().ifBlank { throw IllegalStateException("本地模型返回为空") }
    }

    fun close() {
        runCatching { inst?.close() }
        inst = null; loadedPath = ""
    }

    /** Gemma 对话模板；非 gemma 模型用简单 user/assistant 前缀 */
    fun wrapPrompt(modelName: String, system: String, turns: List<Pair<String, String>>, user: String): String {
        val gemma = modelName.contains("gemma", true) || modelName.endsWith(".task")
        return if (gemma) buildString {
            append("<start_of_turn>user\n").append(system).append("\n\n")
            turns.forEach { (r, c) -> append(if (r == "user") "我：" else "角色：").append(c).append("\n") }
            append("我：").append(user).append("<end_of_turn>\n<start_of_turn>model\n")
        } else buildString {
            append(system).append("\n\n")
            turns.forEach { (r, c) -> append(if (r == "user") "User: " else "Assistant: ").append(c).append("\n") }
            append("User: ").append(user).append("\nAssistant:")
        }
    }
}
