package com.liquidbox.app.ai

import com.liquidbox.app.data.AiConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 多 Provider LLM 客户端，统一输出内部的消息/工具调用结构。
 * 无后端、无遥测，请求直接从设备发往用户配置的 Base URL。
 */
object LlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /** 随消息附带的图片（已压缩为 base64） */
    data class Image(val mime: String, val base64: String) {
        val dataUrl: String get() = "data:$mime;base64,$base64"
    }

    /** 统一对话消息 */
    data class Msg(
        val role: String,               // user / assistant / tool
        val content: String = "",
        val toolCalls: List<Call> = emptyList(),
        val toolCallId: String? = null,
        val toolName: String? = null,
        val images: List<Image> = emptyList(),
    )

    data class Call(val id: String, val name: String, val arguments: JSONObject)

    /** 统一响应 */
    data class Reply(val text: String, val calls: List<Call>)

    sealed interface Result {
        data class Ok(val reply: Reply) : Result
        data class Err(val message: String) : Result
    }

    fun chat(config: AiConfig, messages: List<Msg>): Result = try {
        val wire = config.provider.wire
        val (url, body, headers) = when (wire) {
            AiConfig.Wire.OPENAI -> buildOpenAi(config, messages)
            AiConfig.Wire.ANTHROPIC -> buildAnthropic(config, messages)
            AiConfig.Wire.GEMINI -> buildGemini(config, messages)
        }
        val reqBuilder = Request.Builder().url(url)
            .header("Content-Type", "application/json")
        headers.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) reqBuilder.header(k, v) }
        parseExtraHeaders(config.extraHeaders).forEach { (k, v) -> reqBuilder.header(k, v) }

        client.newCall(
            reqBuilder.post(body.toString().toRequestBody(JSON_MEDIA)).build()
        ).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result.Err("HTTP ${resp.code}: ${text.take(600)}")
            }
            Result.Ok(
                when (wire) {
                    AiConfig.Wire.OPENAI -> parseOpenAi(text)
                    AiConfig.Wire.ANTHROPIC -> parseAnthropic(text)
                    AiConfig.Wire.GEMINI -> parseGemini(text)
                },
            )
        }
    } catch (e: Exception) {
        Result.Err(e.message ?: e.toString())
    }

    // ---------------- OpenAI-compatible ----------------
    private fun buildOpenAi(config: AiConfig, messages: List<Msg>): Triple<String, JSONObject, Map<String, String>> {
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", fullSystemPrompt()))
        messages.forEach { m ->
            when {
                m.role == "tool" -> arr.put(JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", m.toolCallId ?: "")
                    put("content", m.content)
                })
                m.toolCalls.isNotEmpty() -> arr.put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", m.content)
                    put("tool_calls", JSONArray().apply {
                        m.toolCalls.forEach { c ->
                            put(JSONObject().apply {
                                put("id", c.id)
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", c.name)
                                    put("arguments", c.arguments.toString())
                                })
                            })
                        }
                    })
                })
                else -> {
                    // 有图片时用 OpenAI 多模态 content 数组，否则保持纯文本字符串（兼容性更好）
                    val content: Any = if (m.images.isEmpty()) {
                        m.content
                    } else {
                        JSONArray().apply {
                            put(JSONObject().put("type", "text").put("text", m.content))
                            m.images.forEach { img ->
                                put(JSONObject().put("type", "image_url").put(
                                    "image_url", JSONObject().put("url", img.dataUrl),
                                ))
                            }
                        }
                    }
                    arr.put(JSONObject().put("role", m.role).put("content", content))
                }
            }
        }
        val body = JSONObject().apply {
            put("model", config.model)
            put("messages", arr)
            put("tools", ToolRegistry.openAiToolsArray())
            put("tool_choice", "auto")
            put("temperature", 0.2)
        }
        val base = config.baseUrl.trimEnd('/')
        return Triple(
            "$base/chat/completions",
            body,
            mapOf("Authorization" to "Bearer ${config.apiKey}"),
        )
    }

    private fun parseOpenAi(text: String): Reply {
        val msg = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val content = msg.optString("content", "")
        val calls = mutableListOf<Call>()
        msg.optJSONArray("tool_calls")?.let { tc ->
            for (i in 0 until tc.length()) {
                val o = tc.getJSONObject(i)
                val f = o.getJSONObject("function")
                calls += Call(
                    o.optString("id", "call_$i"),
                    f.optString("name"),
                    runCatching { JSONObject(f.optString("arguments", "{}")) }.getOrDefault(JSONObject()),
                )
            }
        }
        return Reply(content, calls)
    }

    // ---------------- Anthropic ----------------
    private fun buildAnthropic(config: AiConfig, messages: List<Msg>): Triple<String, JSONObject, Map<String, String>> {
        val arr = JSONArray()
        // Anthropic 把 user/tool 结果作为 user 内容块；连续同角色需合并
        val pending = mutableListOf<JSONObject>()
        fun flush(role: String) {
            if (pending.isEmpty()) return
            arr.put(JSONObject().put("role", role).put("content", JSONArray().apply {
                pending.forEach { put(it) }
            }))
            pending.clear()
        }
        messages.forEach { m ->
            when {
                m.role == "tool" -> {
                    pending.add(JSONObject().apply {
                        put("type", "tool_result")
                        put("tool_use_id", m.toolCallId ?: "")
                        put("content", m.content)
                    })
                }
                m.toolCalls.isNotEmpty() -> {
                    flush("user")
                    val blocks = JSONArray()
                    if (m.content.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", m.content))
                    m.toolCalls.forEach { c ->
                        blocks.put(JSONObject().apply {
                            put("type", "tool_use")
                            put("id", c.id)
                            put("name", c.name)
                            put("input", c.arguments)
                        })
                    }
                    arr.put(JSONObject().put("role", "assistant").put("content", blocks))
                }
                else -> {
                    flush("user")
                    val blocks = JSONArray().put(JSONObject().put("type", "text").put("text", m.content))
                    m.images.forEach { img ->
                        blocks.put(JSONObject().put("type", "image").put(
                            "source", JSONObject()
                                .put("type", "base64")
                                .put("media_type", img.mime)
                                .put("data", img.base64),
                        ))
                    }
                    arr.put(JSONObject().put("role", m.role).put("content", blocks))
                }
            }
        }
        flush("user")
        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", 4096)
            put("system", fullSystemPrompt())
            put("messages", arr)
            put("tools", ToolRegistry.anthropicToolsArray())
        }
        val base = config.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages"
        return Triple(
            url,
            body,
            mapOf(
                "x-api-key" to config.apiKey,
                "anthropic-version" to "2023-06-01",
            ),
        )
    }

    private fun parseAnthropic(text: String): Reply {
        val root = JSONObject(text)
        val content = root.optJSONArray("content") ?: JSONArray()
        val sb = StringBuilder()
        val calls = mutableListOf<Call>()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            when (b.optString("type")) {
                "text" -> sb.append(b.optString("text"))
                "tool_use" -> calls += Call(
                    b.optString("id"),
                    b.optString("name"),
                    b.optJSONObject("input") ?: JSONObject(),
                )
            }
        }
        return Reply(sb.toString(), calls)
    }

    // ---------------- Gemini ----------------
    private fun buildGemini(config: AiConfig, messages: List<Msg>): Triple<String, JSONObject, Map<String, String>> {
        val contents = JSONArray()
        messages.forEach { m ->
            when {
                m.role == "tool" -> {
                    val response = JSONObject().apply {
                        put("name", m.toolName ?: "tool")
                        put("response", JSONObject().put("output", m.content.take(12000)))
                    }
                    val part = JSONObject().put("functionResponse", response)
                    val parts = JSONArray().put(part)
                    contents.put(JSONObject().put("role", "function").put("parts", parts))
                }
                m.toolCalls.isNotEmpty() -> contents.put(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().apply {
                        m.toolCalls.forEach { c ->
                            put(JSONObject().put("functionCall", JSONObject().apply {
                                put("name", c.name)
                                put("args", c.arguments)
                            }))
                        }
                    })
                })
                else -> contents.put(JSONObject().apply {
                    put("role", if (m.role == "assistant") "model" else "user")
                    put("parts", JSONArray().apply {
                        if (m.content.isNotBlank()) put(JSONObject().put("text", m.content))
                        m.images.forEach { img ->
                            put(JSONObject().put("inlineData", JSONObject()
                                .put("mimeType", img.mime)
                                .put("data", img.base64)))
                        }
                    })
                })
            }
        }
        val body = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", fullSystemPrompt()))))
            put("contents", contents)
            put("tools", ToolRegistry.geminiToolsArray())
            put("generationConfig", JSONObject().put("temperature", 0.2))
        }
        val base = config.baseUrl.trimEnd('/')
        val model = config.model.trim().removePrefix("models/")
        return Triple("$base/v1beta/models/$model:generateContent?key=${config.apiKey}", body, emptyMap())
    }

    private fun parseGemini(text: String): Reply {
        val root = JSONObject(text)
        val candidate = root.optJSONArray("candidates")?.optJSONObject(0) ?: return Reply("", emptyList())
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val sb = StringBuilder()
        val calls = mutableListOf<Call>()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            p.optString("text").takeIf { it.isNotBlank() }?.let { sb.append(it) }
            p.optJSONObject("functionCall")?.let { fc ->
                calls += Call(
                    "gemini_$i",
                    fc.optString("name"),
                    fc.optJSONObject("args") ?: JSONObject(),
                )
            }
        }
        return Reply(sb.toString(), calls)
    }

    private fun parseExtraHeaders(raw: String): List<Pair<String, String>> =
        raw.lines().mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }

    private fun fullSystemPrompt(): String =
        AiConfig.DEFAULT_SYSTEM_PROMPT +
            "\n\n除手机操作外，你还具备一组「本地工具」，不需要 Shizuku 即可使用：" +
            "qr_generate 生成二维码、zip_extract/zip_pack 解压与打包、" +
            "file_list/file_read/file_create/file_write/file_rename/file_delete 管理工作目录文件、" +
            "text_base64/text_hash/text_password/text_stats/text_json 处理文本。" +
            "用户发来的图片请结合视觉理解后再决定操作。" +
            "\n\n可用工具箱预置动作（通过 toolbox_action 调用）：\n" +
            ToolRegistry.actionIdsDescription() +
            "\n\n屏幕坐标基于设备实际分辨率，请先 read_screen 获取控件 bounds 再计算中心点。"
}
