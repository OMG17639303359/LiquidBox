package com.liquidbox.app.ai

import com.liquidbox.app.data.AiConfig
import com.liquidbox.app.data.LogStore
import com.liquidbox.app.data.ToolPolicies
import com.liquidbox.app.data.ToolPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AI Agent 引擎：工具调用链循环。
 *
 * 每个工具在执行前按 Off / Ask / Auto 策略判定：
 *  - OFF  拒绝
 *  - ASK  挂起并请求 UI 弹出液态玻璃确认卡片
 *  - AUTO 直接执行
 */
object AgentEngine {

    const val MAX_STEPS = 14

    data class PendingAction(
        val tool: String,
        val args: JSONObject,
        val description: String,
    )

    data class AgentState(
        val running: Boolean = false,
        val status: String = "",
        val pending: PendingAction? = null,
        val finished: Boolean = false,
    )

    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    /** 用户确认 / 拒绝挂起的工具调用 */
    private var pendingDecision: Boolean? = null

    fun approve() {
        pendingDecision = true
        _state.value = _state.value.copy(pending = null, status = "正在执行…")
    }

    fun deny() {
        pendingDecision = false
        _state.value = _state.value.copy(pending = null, status = "已取消该动作")
    }

    fun cancel() {
        pendingDecision = false
        _state.value = AgentState(running = false, status = "已停止", finished = true)
    }

    fun reset() {
        _state.value = AgentState()
    }

    suspend fun run(
        userInput: String,
        config: AiConfig,
        images: List<LlmClient.Image> = emptyList(),
    ) = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            _state.value = AgentState(false, "请先在设置中填写 API Key", finished = true)
            LogStore.append("system", "缺少 API Key，任务未启动")
            return@withContext
        }
        _state.value = AgentState(running = true, status = "正在思考…")
        LogStore.append("user", buildString {
            if (images.isNotEmpty()) append("[附带 ${images.size} 张图片] ")
            append(userInput)
        })

        val messages = mutableListOf<LlmClient.Msg>()
        messages += LlmClient.Msg(role = "user", content = userInput, images = images)

        var steps = 0
        while (steps++ < MAX_STEPS) {
            if (!_state.value.running) return@withContext

            val result = LlmClient.chat(config, messages)
            if (result is LlmClient.Result.Err) {
                LogStore.append("error", result.message)
                _state.value = _state.value.copy(running = false, status = "请求失败: ${result.message}", finished = true)
                return@withContext
            }
            val reply = (result as LlmClient.Result.Ok).reply

            if (reply.text.isNotBlank()) {
                LogStore.append("assistant", reply.text)
                _state.value = _state.value.copy(status = reply.text.take(160))
            }

            if (reply.calls.isEmpty()) {
                _state.value = _state.value.copy(running = false, status = "任务完成", finished = true)
                return@withContext
            }

            messages += LlmClient.Msg(role = "assistant", content = reply.text, toolCalls = reply.calls)

            for (call in reply.calls) {
                if (!_state.value.running) return@withContext
                val tool = ToolRegistry.byName(call.name)
                val argsStr = call.arguments.toString()
                LogStore.append("tool_call", "${call.name}  $argsStr")

                val toolResult = if (tool == null) {
                    "未知工具: ${call.name}"
                } else {
                    when (ToolPolicies.get(call.name)) {
                        ToolPolicy.OFF -> "该工具已被禁用 (Off)，请改用其它方式完成。"
                        ToolPolicy.ASK -> {
                            // 请求 UI 确认（挂起等待液态玻璃确认卡片）
                            pendingDecision = null
                            _state.value = _state.value.copy(
                                status = "等待确认: ${call.name}",
                                pending = PendingAction(call.name, call.arguments, describe(call.name, call.arguments)),
                            )
                            val deadline = System.currentTimeMillis() + 120_000L
                            while (pendingDecision == null && System.currentTimeMillis() < deadline && _state.value.running) {
                                Thread.sleep(120)
                            }
                            val approved = pendingDecision == true
                            pendingDecision = null
                            if (!approved) {
                                _state.value = _state.value.copy(status = "已拒绝 ${call.name}")
                                "用户拒绝执行该动作。"
                            } else {
                                executeLogged(tool, call.arguments)
                            }
                        }
                        ToolPolicy.AUTO -> executeLogged(tool, call.arguments)
                    }
                }

                LogStore.append("tool_result", "$toolResult".take(4000))
                messages += LlmClient.Msg(
                    role = "tool",
                    content = toolResult.take(12000),
                    toolCallId = call.id,
                    toolName = call.name,
                )
            }
        }
        _state.value = _state.value.copy(running = false, status = "已达最大步数上限", finished = true)
        LogStore.append("system", "已达最大步数上限 $MAX_STEPS")
    }

    private fun executeLogged(tool: ToolRegistry.Tool, args: JSONObject): String = runCatching {
        tool.execute(args)
    }.getOrElse { "执行异常: ${it.message}" }

    private fun describe(name: String, args: JSONObject): String = when (name) {
        "tap" -> "点击坐标 (${args.optInt("x")}, ${args.optInt("y")})"
        "swipe" -> "滑动 (${args.optInt("x1")},${args.optInt("y1")}) → (${args.optInt("x2")},${args.optInt("y2")})"
        "input_text" -> "输入文本「${args.optString("text")}」"
        "press_key" -> "按下按键 ${args.optInt("keycode")}"
        "launch_app" -> "启动应用 ${args.optString("package")}"
        "run_shell" -> "执行 shell: ${args.optString("command")}"
        "toolbox_action" -> "工具箱动作 ${args.optString("action_id")} ${args.optString("package")}"
        "qr_generate" -> "生成二维码：${args.optString("text").take(60)}"
        "zip_extract" -> "解压 ${args.optString("name")}"
        "zip_pack" -> "打包工作目录为 ${args.optString("name")}"
        "file_read" -> "读取文件 ${args.optString("name")}"
        "file_create", "file_write" -> "写文件 ${args.optString("name")}"
        "file_rename" -> "改名 ${args.optString("old")} → ${args.optString("new")}"
        "file_delete" -> "删除 ${args.optString("name")}"
        else -> "$name ${args.toString().take(120)}"
    }
}
