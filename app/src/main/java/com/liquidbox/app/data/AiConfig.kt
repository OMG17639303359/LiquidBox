package com.liquidbox.app.data

import org.json.JSONArray
import org.json.JSONObject

/** AI 配置：Provider / Base URL / 模型 / 额外 headers，API Key 走 Keystore */
data class AiConfig(
    val provider: Provider = Provider.OPENAI,
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val extraHeaders: String = "",
    val apiKey: String = "",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
) {
    /** 线上协议（wire）：决定请求/响应格式；多个厂商可共用同一种协议 */
    enum class Wire { OPENAI, ANTHROPIC, GEMINI }

    enum class Provider(
        val label: String,
        val defaultBase: String,
        val defaultModel: String,
        val wire: Wire,
        val supportsVision: Boolean = false,
    ) {
        OPENAI("OpenAI-compatible", "https://api.openai.com/v1", "gpt-4o-mini", Wire.OPENAI, true),
        DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat", Wire.OPENAI, false),
        ANTHROPIC("Anthropic", "https://api.anthropic.com", "claude-sonnet-4-20250514", Wire.ANTHROPIC, true),
        GEMINI("Google Gemini", "https://generativelanguage.googleapis.com", "gemini-2.0-flash", Wire.GEMINI, true),
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "你是 Android 手机操作助手。通过提供的工具读取屏幕、点击、滑动、输入、启动应用或执行 shell 命令。" +
                "操作前先读屏确认当前界面；分步执行，每步说明你在做什么；不猜测不存在的控件坐标。" +
                "完成用户目标后给出简短总结。"

        private const val K_PROVIDER = "ai_provider"
        private const val K_BASE = "ai_base"
        private const val K_MODEL = "ai_model"
        private const val K_HEADERS = "ai_headers"
        private const val K_KEY = "ai_key_encrypted"
        private const val K_SYSTEM = "ai_system"

        fun load(): AiConfig {
            val provider = runCatching {
                Provider.valueOf(SecureStore.getPlain(K_PROVIDER, Provider.OPENAI.name))
            }.getOrDefault(Provider.OPENAI)
            return AiConfig(
                provider = provider,
                baseUrl = SecureStore.getPlain(K_BASE, provider.defaultBase),
                model = SecureStore.getPlain(K_MODEL, provider.defaultModel),
                extraHeaders = SecureStore.getPlain(K_HEADERS),
                apiKey = SecureStore.getDecrypted(K_KEY) ?: "",
                systemPrompt = SecureStore.getPlain(K_SYSTEM, DEFAULT_SYSTEM_PROMPT),
            )
        }

        fun save(config: AiConfig) {
            SecureStore.putPlain(K_PROVIDER, config.provider.name)
            SecureStore.putPlain(K_BASE, config.baseUrl)
            SecureStore.putPlain(K_MODEL, config.model)
            SecureStore.putPlain(K_HEADERS, config.extraHeaders)
            SecureStore.putPlain(K_SYSTEM, config.systemPrompt)
            if (config.apiKey.isNotBlank()) SecureStore.putEncrypted(K_KEY, config.apiKey)
        }

        fun clearKey() = SecureStore.remove(K_KEY)
    }
}

/** 工具权限模型：Off / Ask / Auto */
enum class ToolPolicy(val label: String) { OFF("Off"), ASK("Ask"), AUTO("Auto") }

object ToolPolicies {
    private const val PREFIX = "tool_policy_"
    private val defaults = mapOf(
        "read_screen" to ToolPolicy.AUTO,
        "screenshot" to ToolPolicy.AUTO,
        "get_device_info" to ToolPolicy.AUTO,
        "list_apps" to ToolPolicy.AUTO,
        "tap" to ToolPolicy.ASK,
        "swipe" to ToolPolicy.ASK,
        "input_text" to ToolPolicy.ASK,
        "press_key" to ToolPolicy.ASK,
        "launch_app" to ToolPolicy.ASK,
        "run_shell" to ToolPolicy.ASK,
        "toolbox_action" to ToolPolicy.ASK,
        // 本地工具（不需要 Shizuku）
        "qr_generate" to ToolPolicy.AUTO,
        "zip_extract" to ToolPolicy.ASK,
        "zip_pack" to ToolPolicy.AUTO,
        "file_list" to ToolPolicy.AUTO,
        "file_read" to ToolPolicy.AUTO,
        "file_create" to ToolPolicy.ASK,
        "file_write" to ToolPolicy.ASK,
        "file_rename" to ToolPolicy.ASK,
        "file_delete" to ToolPolicy.ASK,
        "text_base64" to ToolPolicy.AUTO,
        "text_hash" to ToolPolicy.AUTO,
        "text_password" to ToolPolicy.AUTO,
        "text_stats" to ToolPolicy.AUTO,
        "text_json" to ToolPolicy.AUTO,
    )
    fun get(tool: String): ToolPolicy = runCatching {
        ToolPolicy.valueOf(SecureStore.getPlain(PREFIX + tool, defaults[tool]?.name ?: ToolPolicy.ASK.name))
    }.getOrDefault(defaults[tool] ?: ToolPolicy.ASK)

    fun set(tool: String, policy: ToolPolicy) = SecureStore.putPlain(PREFIX + tool, policy.name)
    fun allTools(): List<String> = defaults.keys.toList()
    fun defaultOf(tool: String) = defaults[tool] ?: ToolPolicy.ASK
}

/** 执行日志（本地，无后端无遥测） */
data class LogEntry(val time: Long, val role: String, val text: String) {
    fun toJson() = JSONObject().apply {
        put("t", time); put("r", role); put("x", text)
    }.toString()

    companion object {
        fun fromJson(s: String) = runCatching {
            val o = JSONObject(s)
            LogEntry(o.getLong("t"), o.getString("r"), o.getString("x"))
        }.getOrNull()
    }
}

object LogStore {
    private const val KEY = "agent_logs"
    private const val MAX = 300

    fun append(role: String, text: String) {
        val arr = JSONArray(SecureStore.getPlain(KEY, "[]"))
        arr.put(LogEntry(System.currentTimeMillis(), role, text).let { JSONObject(it.toJson()) })
        while (arr.length() > MAX) arr.remove(0)
        SecureStore.putPlain(KEY, arr.toString())
    }

    fun all(): List<LogEntry> {
        val arr = runCatching { JSONArray(SecureStore.getPlain(KEY, "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { LogEntry.fromJson(arr.getString(i)) }.getOrNull()
        }
    }

    fun clear() = SecureStore.putPlain(KEY, "[]")
}
