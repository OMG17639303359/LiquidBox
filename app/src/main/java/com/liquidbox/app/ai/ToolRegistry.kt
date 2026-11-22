package com.liquidbox.app.ai

import com.liquidbox.app.LiquidBoxApp
import com.liquidbox.app.shizuku.ShizukuShell
import com.liquidbox.app.shizuku.Toolbox
import com.liquidbox.app.tools.LocalTools
import org.json.JSONArray
import org.json.JSONObject

/** 工具定义 + 执行器 */
object ToolRegistry {

    data class Tool(
        val name: String,
        val description: String,
        val parameters: JSONObject,
        val dangerous: Boolean = false,
        val execute: (JSONObject) -> String,
    )

    private fun obj(vararg pairs: Pair<String, Any>) = JSONObject().apply {
        pairs.forEach { (k, v) -> put(k, v) }
    }

    private fun prop(type: String, description: String) =
        obj("type" to type, "description" to description)

    private fun params(
        vararg props: Pair<String, JSONObject>,
        required: List<String> = emptyList(),
    ) = obj(
        "type" to "object",
        "properties" to JSONObject().apply { props.forEach { put(it.first, it.second) } },
        "required" to JSONArray(required),
    )

    private fun result(r: ShizukuShell.ShellResult, okText: String) =
        if (r.ok) okText else r.output

    val tools: List<Tool> = listOf(
        Tool(
            name = "read_screen",
            description = "读取当前屏幕的 UI 层级（文字、坐标、可点击状态）。操作前应先调用。",
            parameters = params(),
        ) { ShizukuShell.dumpUiHierarchy().take(12000) },

        Tool(
            name = "screenshot",
            description = "对当前屏幕截图并返回文件保存路径",
            parameters = params(),
        ) { ShizukuShell.screenshot() },

        Tool(
            name = "get_device_info",
            description = "获取设备型号、Android 版本、当前执行身份",
            parameters = params(),
        ) {
            ShizukuShell.exec("echo MODEL=\$(getprop ro.product.model); echo SDK=\$(getprop ro.build.version.sdk); id").output
        },

        Tool(
            name = "list_apps",
            description = "列出已安装的第三方应用包名",
            parameters = params(),
        ) { ShizukuShell.exec("pm list packages -3").output },

        Tool(
            name = "tap",
            description = "点击屏幕坐标",
            parameters = params(
                "x" to prop("integer", "横坐标 px"),
                "y" to prop("integer", "纵坐标 px"),
                required = listOf("x", "y"),
            ),
            dangerous = true,
        ) {
            result(ShizukuShell.exec("input tap ${it.optInt("x")} ${it.optInt("y")}"), "已点击")
        },

        Tool(
            name = "swipe",
            description = "滑动屏幕",
            parameters = params(
                "x1" to prop("integer", "起点 x"),
                "y1" to prop("integer", "起点 y"),
                "x2" to prop("integer", "终点 x"),
                "y2" to prop("integer", "终点 y"),
                "duration" to prop("integer", "时长 ms，默认 300"),
                required = listOf("x1", "y1", "x2", "y2"),
            ),
            dangerous = true,
        ) {
            val d = it.optInt("duration", 300)
            result(
                ShizukuShell.exec("input swipe ${it.optInt("x1")} ${it.optInt("y1")} ${it.optInt("x2")} ${it.optInt("y2")} $d"),
                "已滑动",
            )
        },

        Tool(
            name = "input_text",
            description = "在当前焦点输入框输入文本（空格以 %s 代替）",
            parameters = params(
                "text" to prop("string", "要输入的文本"),
                required = listOf("text"),
            ),
            dangerous = true,
        ) {
            val t = it.optString("text").replace(" ", "%s").replace("\"", "\\\"")
            result(ShizukuShell.exec("input text \"$t\""), "已输入")
        },

        Tool(
            name = "press_key",
            description = "按下系统按键。3=Home 4=Back 187=最近任务 26=电源 66=回车",
            parameters = params(
                "keycode" to prop("integer", "KeyEvent 键码"),
                required = listOf("keycode"),
            ),
            dangerous = true,
        ) {
            result(ShizukuShell.exec("input keyevent ${it.optInt("keycode")}"), "已按键")
        },

        Tool(
            name = "launch_app",
            description = "按包名启动应用",
            parameters = params(
                "package" to prop("string", "应用包名"),
                required = listOf("package"),
            ),
            dangerous = true,
        ) {
            val p = it.optString("package")
            result(
                ShizukuShell.exec("monkey -p $p -c android.intent.category.LAUNCHER 1"),
                "已启动 $p",
            )
        },

        Tool(
            name = "run_shell",
            description = "执行任意 shell 命令（危险，可能修改系统状态）",
            parameters = params(
                "command" to prop("string", "完整 shell 命令"),
                required = listOf("command"),
            ),
            dangerous = true,
        ) { ShizukuShell.exec(it.optString("command")).output.take(8000) },

        Tool(
            name = "toolbox_action",
            description = "执行工具箱预置动作。可用 action_id 见系统提示；package/value 为可选参数",
            parameters = params(
                "action_id" to prop("string", "动作 id"),
                "package" to prop("string", "应用包名（可选）"),
                "value" to prop("string", "附加参数（可选）"),
                required = listOf("action_id"),
            ),
            dangerous = true,
        ) {
            val id = it.optString("action_id")
            val action = Toolbox.byId(id)
                ?: return@Tool "未知动作 $id。可用: ${Toolbox.actions.joinToString(",") { a -> a.id }}"
            Toolbox.run(action, it.optString("package"), it.optString("value")).output
        },

        // ============ 本地工具（不需要 Shizuku，操作应用私有工作目录） ============
        Tool(
            name = "qr_generate",
            description = "把文本生成二维码 PNG，保存到工作目录并返回结果。适合把链接、文本转成二维码。",
            parameters = params(
                "text" to prop("string", "二维码内容"),
                "name" to prop("string", "文件名，如 code.png（可选）"),
                required = listOf("text"),
            ),
        ) {
            val ctx = LiquidBoxApp.appContext
            val name = it.optString("name").ifBlank { "qr_${System.currentTimeMillis()}.png" }
            LocalTools.saveQrPng(ctx, "", name, it.optString("text"))
        },

        Tool(
            name = "zip_extract",
            description = "解压工作目录里已存在的 zip 文件到当前目录。",
            parameters = params(
                "name" to prop("string", "zip 文件名，如 data.zip"),
                "path" to prop("string", "相对子目录（可选，默认工作目录根）"),
                required = listOf("name"),
            ),
            dangerous = true,
        ) {
            val ctx = LiquidBoxApp.appContext
            LocalTools.unzipByName(ctx, it.optString("path"), it.optString("name"))
        },

        Tool(
            name = "zip_pack",
            description = "把工作目录（含子目录）打包成一个 zip 文件。",
            parameters = params(
                "name" to prop("string", "输出文件名，如 backup.zip（可选）"),
                "path" to prop("string", "相对子目录（可选）"),
            ),
        ) {
            val ctx = LiquidBoxApp.appContext
            val name = it.optString("name").ifBlank { "archive_${System.currentTimeMillis()}.zip" }
            LocalTools.zip(ctx, it.optString("path"), name)
        },

        Tool(
            name = "file_list",
            description = "列出工作目录里的文件与文件夹（名称、类型、大小）。",
            parameters = params(
                "path" to prop("string", "相对子目录（可选，默认工作目录根）"),
            ),
        ) {
            val ctx = LiquidBoxApp.appContext
            val items = LocalTools.list(ctx, it.optString("path"))
            if (items.isEmpty()) "（目录为空或不存在）"
            else items.joinToString("\n") { f ->
                "${if (f.isDir) "[目录]" else "[文件]"} ${f.name}  ${if (f.isDir) "" else "${f.size} B"}"
            }
        },

        Tool(
            name = "file_read",
            description = "读取工作目录里某个文本文件的内容。",
            parameters = params(
                "name" to prop("string", "文件名"),
                "path" to prop("string", "相对子目录（可选）"),
                required = listOf("name"),
            ),
        ) {
            val ctx = LiquidBoxApp.appContext
            LocalTools.readText(ctx, it.optString("path"), it.optString("name")).take(8000)
        },

        Tool(
            name = "file_create",
            description = "在工作目录新建一个文本文件（若同名已存在则失败）。",
            parameters = params(
                "name" to prop("string", "文件名，如 note.txt"),
                "content" to prop("string", "文件内容"),
                "path" to prop("string", "相对子目录（可选）"),
                required = listOf("name"),
            ),
            dangerous = true,
        ) {
            val ctx = LiquidBoxApp.appContext
            LocalTools.createFile(ctx, it.optString("path"), it.optString("name"), it.optString("content"))
        },

        Tool(
            name = "file_write",
            description = "覆盖写入工作目录里的文本文件（不存在则创建）。",
            parameters = params(
                "name" to prop("string", "文件名"),
                "content" to prop("string", "文件内容"),
                "path" to prop("string", "相对子目录（可选）"),
                required = listOf("name"),
            ),
            dangerous = true,
        ) {
            val ctx = LiquidBoxApp.appContext
            LocalTools.writeText(ctx, it.optString("path"), it.optString("name"), it.optString("content"))
        },

        Tool(
            name = "file_rename",
            description = "给工作目录里的文件或文件夹改名。",
            parameters = params(
                "old" to prop("string", "原名称"),
                "new" to prop("string", "新名称"),
                "path" to prop("string", "相对子目录（可选）"),
                required = listOf("old", "new"),
            ),
            dangerous = true,
        ) {
            val ctx = LiquidBoxApp.appContext
            LocalTools.rename(ctx, it.optString("path"), it.optString("old"), it.optString("new"))
        },

        Tool(
            name = "file_delete",
            description = "删除工作目录里的文件或文件夹（不可恢复）。",
            parameters = params(
                "name" to prop("string", "名称"),
                "path" to prop("string", "相对子目录（可选）"),
                required = listOf("name"),
            ),
            dangerous = true,
        ) {
            val ctx = LiquidBoxApp.appContext
            LocalTools.delete(ctx, it.optString("path"), it.optString("name"))
        },

        Tool(
            name = "text_base64",
            description = "Base64 编码或解码文本。",
            parameters = params(
                "mode" to prop("string", "encode 或 decode"),
                "text" to prop("string", "文本"),
                required = listOf("text"),
            ),
        ) { LocalTools.base64(it.optString("mode"), it.optString("text")) },

        Tool(
            name = "text_hash",
            description = "计算文本哈希，algo 为 MD5 / SHA-1 / SHA-256。",
            parameters = params(
                "algo" to prop("string", "MD5 / SHA-1 / SHA-256"),
                "text" to prop("string", "文本"),
                required = listOf("text"),
            ),
        ) { LocalTools.hash(it.optString("algo").ifBlank { "SHA-256" }, it.optString("text")) },

        Tool(
            name = "text_password",
            description = "生成随机密码。",
            parameters = params(
                "length" to prop("integer", "长度 4-64，默认 16"),
                "upper" to prop("boolean", "含大写"),
                "digits" to prop("boolean", "含数字"),
                "symbols" to prop("boolean", "含符号"),
            ),
        ) {
            LocalTools.password(
                it.optInt("length", 16),
                it.optBoolean("upper", true),
                it.optBoolean("digits", true),
                it.optBoolean("symbols", false),
            )
        },

        Tool(
            name = "text_stats",
            description = "统计文本的字符数、词数、行数、字节数。",
            parameters = params(
                "text" to prop("string", "文本"),
                required = listOf("text"),
            ),
        ) { LocalTools.stats(it.optString("text")) },

        Tool(
            name = "text_json",
            description = "格式化 / 美化 JSON 文本（缩进 2 空格）。",
            parameters = params(
                "text" to prop("string", "JSON 文本"),
                required = listOf("text"),
            ),
        ) { LocalTools.json(it.optString("text")) },
    )

    fun byName(name: String) = tools.find { it.name == name }

    /** OpenAI-compatible tools 数组 */
    fun openAiToolsArray(): JSONArray = JSONArray().apply {
        tools.forEach { t ->
            put(obj(
                "type" to "function",
                "function" to obj(
                    "name" to t.name,
                    "description" to t.description,
                    "parameters" to t.parameters,
                ),
            ))
        }
    }

    /** Anthropic tools 数组 */
    fun anthropicToolsArray(): JSONArray = JSONArray().apply {
        tools.forEach { t ->
            put(obj(
                "name" to t.name,
                "description" to t.description,
                "input_schema" to t.parameters,
            ))
        }
    }

    /** Gemini tools 数组 */
    fun geminiToolsArray(): JSONArray = JSONArray().apply {
        put(obj("functionDeclarations" to JSONArray().apply {
            tools.forEach { t ->
                put(obj(
                    "name" to t.name,
                    "description" to t.description,
                    "parameters" to t.parameters,
                ))
            }
        }))
    }

    fun actionIdsDescription(): String =
        Toolbox.actions.joinToString("\n") { "- ${it.id}: ${it.title}" }
}
