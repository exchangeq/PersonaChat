package com.wunding.personachat.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("pc_settings")

data class Settings(
    val backend: String = "local",       // local / api
    val baseUrl: String = "http://127.0.0.1:11434/v1",
    val apiKey: String = "",
    val model: String = "llama3.1",
    val temperature: Float = 0.8f,
    val maxTokens: Int = 1024,
    val stream: Boolean = true,
    val memoryEnabled: Boolean = true,
    val writeEvery: Int = 4,
    val shortKeep: Int = 12,
    val recallK: Int = 5,
    val theme: String = "dark",          // dark / light / system
    val accent: String = "green",        // green / amber
    val personaRepo: String = "exchangeq/PocketForge-Personas",
    /** 手机本地模型（MediaPipe .task 文件）绝对路径 */
    val localModelPath: String = "",
    val localBackend: String = "gpu",   // gpu / cpu
    val updateRepo: String = "exchangeq/PersonaChat",
)

object SettingsStore {
    private val K = object {
        val backend = stringPreferencesKey("backend")
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val temp = floatPreferencesKey("temperature")
        val maxTokens = intPreferencesKey("max_tokens")
        val stream = booleanPreferencesKey("stream")
        val memOn = booleanPreferencesKey("memory_enabled")
        val writeEvery = intPreferencesKey("write_every")
        val shortKeep = intPreferencesKey("short_keep")
        val recallK = intPreferencesKey("recall_k")
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val repo = stringPreferencesKey("persona_repo")
        val localModel = stringPreferencesKey("local_model")
        val localBackend = stringPreferencesKey("local_backend")
        val updateRepo = stringPreferencesKey("update_repo")
    }

    fun flow(context: Context): Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            backend = p[K.backend] ?: "local",
            baseUrl = p[K.baseUrl] ?: "http://127.0.0.1:11434/v1",
            apiKey = p[K.apiKey] ?: "",
            model = p[K.model] ?: "llama3.1",
            temperature = p[K.temp] ?: 0.8f,
            maxTokens = p[K.maxTokens] ?: 1024,
            stream = p[K.stream] ?: true,
            memoryEnabled = p[K.memOn] ?: true,
            writeEvery = p[K.writeEvery] ?: 4,
            shortKeep = p[K.shortKeep] ?: 12,
            recallK = p[K.recallK] ?: 5,
            theme = p[K.theme] ?: "dark",
            accent = p[K.accent] ?: "green",
            personaRepo = p[K.repo] ?: "exchangeq/PocketForge-Personas",
            localModelPath = p[K.localModel] ?: "",
            localBackend = p[K.localBackend] ?: "gpu",
            updateRepo = p[K.updateRepo] ?: "exchangeq/PersonaChat",
        )
    }

    suspend fun save(context: Context, s: Settings) {
        context.dataStore.edit { p ->
            p[K.backend] = s.backend
            p[K.baseUrl] = s.baseUrl
            p[K.apiKey] = s.apiKey
            p[K.model] = s.model
            p[K.temp] = s.temperature
            p[K.maxTokens] = s.maxTokens
            p[K.stream] = s.stream
            p[K.memOn] = s.memoryEnabled
            p[K.writeEvery] = s.writeEvery
            p[K.shortKeep] = s.shortKeep
            p[K.recallK] = s.recallK
            p[K.theme] = s.theme
            p[K.accent] = s.accent
            p[K.repo] = s.personaRepo
            p[K.localModel] = s.localModelPath
            p[K.localBackend] = s.localBackend
            p[K.updateRepo] = s.updateRepo
        }
    }
}
