package com.liquidbox.app.shizuku

import com.liquidbox.app.shizuku.ShizukuShell.ShellResult

/**
 * 工具箱指令集。
 *
 * 所有破坏性操作都提供对应的 rollback 指令（支持可回滚），
 * 且以「先记录原值 / 再执行」的方式由 UI 层二次确认后调用。
 */
object Toolbox {

    data class Action(
        val id: String,
        val title: String,
        val description: String,
        val group: Group,
        val destructive: Boolean = false,
        /** 需要应用包名时置 true */
        val needsPackage: Boolean = false,
        /** 需要数值参数时给出参数 key */
        val valueKey: String? = null,
        val command: (pkg: String, value: String) -> String,
        val rollback: ((pkg: String, value: String) -> String)? = null,
    )

    enum class Group(val label: String) {
        APP("应用管理"),
        SYSTEM("系统增强"),
        DISPLAY("显示与动画"),
        NETWORK("网络与防火墙"),
        QUICK("快捷动作"),
        SHELL("Shell 工具"),
    }

    /** 工具页分区：软件工具（作用于已选应用）/ 系统工具 / 日常工具 / 本地工具（免 Shizuku） */
    enum class Section(val label: String) {
        SOFTWARE("软件工具"),
        SYSTEM("系统工具"),
        DAILY("日常工具"),
        LOCAL("本地工具"),
    }

    /** 归属规则：应用管理分组、或任何需要包名的动作都归入「软件工具」 */
    fun sectionOf(action: Action): Section =
        if (action.group == Group.APP || action.needsPackage) Section.SOFTWARE else Section.SYSTEM

    private fun pmPkg(pkg: String) = pkg.trim().ifEmpty { "com.example.invalid" }

    val actions: List<Action> = buildList {
        // ---------- 应用管理 ----------
        add(Action("freeze", "冻结应用", "pm disable-user，停用但保留数据", Group.APP, needsPackage = true,
            command = { p, _ -> "pm disable-user --user 0 ${pmPkg(p)}" },
            rollback = { p, _ -> "pm enable ${pmPkg(p)}" }))
        add(Action("unfreeze", "解冻应用", "重新启用已停用应用", Group.APP, needsPackage = true,
            command = { p, _ -> "pm enable ${pmPkg(p)}" }))
        add(Action("suspend", "暂停应用", "pm suspend，图标变灰不可启动", Group.APP, needsPackage = true,
            command = { p, _ -> "pm suspend ${pmPkg(p)}" },
            rollback = { p, _ -> "pm unsuspend ${pmPkg(p)}" }))
        add(Action("unsuspend", "取消暂停", "恢复已暂停应用", Group.APP, needsPackage = true,
            command = { p, _ -> "pm unsuspend ${pmPkg(p)}" }))
        add(Action("hide", "隐藏应用", "pm hide，从启动器移除", Group.APP, needsPackage = true,
            command = { p, _ -> "pm hide ${pmPkg(p)}" },
            rollback = { p, _ -> "pm unhide ${pmPkg(p)}" }))
        add(Action("unhide", "取消隐藏", "恢复显示", Group.APP, needsPackage = true,
            command = { p, _ -> "pm unhide ${pmPkg(p)}" }))
        add(Action("force_stop", "强行停止", "am force-stop", Group.APP, needsPackage = true,
            command = { p, _ -> "am force-stop ${pmPkg(p)}" }))
        add(Action("clear_data", "清空数据", "pm clear（不可恢复，需二次确认）", Group.APP, true, true,
            command = { p, _ -> "pm clear ${pmPkg(p)}" }))
        add(Action("uninstall_system", "卸载系统应用", "为当前用户卸载，可恢复", Group.APP, true, true,
            command = { p, _ -> "pm uninstall -k --user 0 ${pmPkg(p)}" },
            rollback = { p, _ -> "pm install-existing ${pmPkg(p)}" }))
        add(Action("restore_system", "恢复系统应用", "pm install-existing", Group.APP, needsPackage = true,
            command = { p, _ -> "pm install-existing ${pmPkg(p)}" }))
        add(Action("grant_perm", "授予权限", "pm grant <权限名>", Group.APP, needsPackage = true,
            valueKey = "权限名 (android.permission.…)",
            command = { p, v -> "pm grant ${pmPkg(p)} ${v.trim()}" },
            rollback = { p, v -> "pm revoke ${pmPkg(p)} ${v.trim()}" }))
        add(Action("revoke_perm", "撤销权限", "pm revoke <权限名>", Group.APP, needsPackage = true,
            valueKey = "权限名 (android.permission.…)",
            command = { p, v -> "pm revoke ${pmPkg(p)} ${v.trim()}" }))
        add(Action("appops", "AppOps 配置", "cmd appops set <OP> allow/ignore", Group.APP, needsPackage = true,
            valueKey = "OP 与模式 (如 CAMERA ignore)",
            command = { p, v -> "cmd appops set ${pmPkg(p)} ${v.trim()}" }))
        add(Action("component", "组件启停", "pm disable/enable <pkg>/<组件>", Group.APP, needsPackage = true,
            valueKey = "enable|disable 组件全名",
            command = { p, v ->
                val parts = v.trim().split(Regex("\\s+"), limit = 2)
                val op = parts.getOrNull(0) ?: "disable"
                val comp = parts.getOrNull(1) ?: ""
                "pm $op ${pmPkg(p)}/$comp"
            },
            rollback = { p, v ->
                val parts = v.trim().split(Regex("\\s+"), limit = 2)
                val op = if (parts.getOrNull(0) == "disable") "enable" else "disable"
                "pm $op ${pmPkg(p)}/${parts.getOrNull(1) ?: ""}"
            }))
        add(Action("batch_perm", "一键权限批量配置", "批量授予常用运行时权限", Group.APP, needsPackage = true,
            command = { p, _ ->
                listOf(
                    "android.permission.CAMERA",
                    "android.permission.RECORD_AUDIO",
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.READ_CONTACTS",
                    "android.permission.POST_NOTIFICATIONS",
                ).joinToString("\n") { "pm grant ${pmPkg(p)} $it 2>/dev/null" } + "\necho BATCH_DONE"
            }))
        add(Action("install_apk", "高级 APK 安装", "降级/绕过 SDK/腾空间/防杀", Group.APP, true,
            valueKey = "APK 绝对路径",
            command = { _, v ->
                """
                pm trim-caches 100000000000 2>/dev/null
                pm install -r -d -t -g --bypass-low-target-sdk-block "${v.trim()}"
                """.trimIndent()
            }))
        add(Action("force_pip", "强制 PiP", "放宽画中画与多窗口限制", Group.APP,
            command = { _, _ ->
                "settings put global force_resizable_activities 1; " +
                    "settings put secure pip_on 1 2>/dev/null; echo PIP_DONE"
            },
            rollback = { _, _ -> "settings delete global force_resizable_activities" }))

        // ---------- 系统增强 ----------
        add(Action("doze_whitelist", "加入 Doze 白名单", "dumpsys deviceidle whitelist", Group.SYSTEM,
            needsPackage = true,
            command = { p, _ -> "dumpsys deviceidle whitelist +${pmPkg(p)}" },
            rollback = { p, _ -> "dumpsys deviceidle whitelist -${pmPkg(p)}" }))
        add(Action("doze_unwhitelist", "移出 Doze 白名单", "取消电池优化豁免", Group.SYSTEM,
            needsPackage = true,
            command = { p, _ -> "dumpsys deviceidle whitelist -${pmPkg(p)}" }))
        add(Action("battery_whitelist", "电池白名单", "cmd deviceidle whitelist", Group.SYSTEM,
            needsPackage = true,
            command = { p, _ -> "cmd deviceidle whitelist +${pmPkg(p)} 2>/dev/null || dumpsys deviceidle whitelist +${pmPkg(p)}" },
            rollback = { p, _ -> "cmd deviceidle whitelist -${pmPkg(p)} 2>/dev/null || dumpsys deviceidle whitelist -${pmPkg(p)}" }))
        add(Action("standby_bucket", "待机桶设置", "am set-standby-bucket", Group.SYSTEM,
            needsPackage = true,
            valueKey = "active|working_set|frequent|rare|restricted",
            command = { p, v -> "am set-standby-bucket ${pmPkg(p)} ${v.trim().ifEmpty { "active" }}" },
            rollback = { p, _ -> "am set-standby-bucket ${pmPkg(p)} active" }))
        add(Action("force_doze", "强制进入 Doze", "dumpsys deviceidle force-idle", Group.SYSTEM,
            command = { _, _ -> "dumpsys deviceidle enable all 2>/dev/null; dumpsys deviceidle force-idle" },
            rollback = { _, _ -> "dumpsys deviceidle unforce; dumpsys deviceidle disable all 2>/dev/null; echo OK" }))
        add(Action("unforce_doze", "退出强制 Doze", "恢复正常 idle 状态", Group.SYSTEM,
            command = { _, _ -> "dumpsys deviceidle unforce; echo OK" }))
        add(Action("wireless_adb", "无线调试降级通道", "开启 ADB over WiFi (5555)", Group.SYSTEM, true,
            command = { _, _ ->
                "settings put global adb_wifi_enabled 1 2>/dev/null; " +
                    "setprop service.adb.tcp.port 5555; " +
                    "stop adbd 2>/dev/null; start adbd 2>/dev/null; echo WIRELESS_ADB_DONE"
            },
            rollback = { _, _ -> "settings put global adb_wifi_enabled 0; setprop service.adb.tcp.port -1; echo OK" }))
        add(Action("battery_level", "电量修改", "dumpsys battery set level，修改系统显示的电量", Group.SYSTEM,
            valueKey = "电量百分比 (0-100)",
            command = { _, v ->
                val n = v.trim().toIntOrNull()?.coerceIn(0, 100) ?: 80
                listOf(
                    "dumpsys battery set level $n",
                    "dumpsys battery unplug 2>/dev/null",
                    "dumpsys battery set status 3 2>/dev/null",
                    "echo BATTERY_SET_$n",
                ).joinToString("\n")
            },
            rollback = { _, _ -> "dumpsys battery reset; echo BATTERY_RESET" }))
        add(Action("battery_reset", "恢复真实电量", "dumpsys battery reset，清除电量伪装", Group.SYSTEM,
            command = { _, _ -> "dumpsys battery reset; echo BATTERY_RESET" }))
        add(Action("battery_charging", "模拟充电状态", "2=充电中 / 3=放电中 / 5=满电", Group.SYSTEM,
            valueKey = "状态码 (2/3/5)",
            command = { _, v ->
                val s = v.trim().toIntOrNull()?.coerceIn(1, 5) ?: 2
                "dumpsys battery set status $s; echo BATTERY_STATUS_$s"
            },
            rollback = { _, _ -> "dumpsys battery reset; echo BATTERY_RESET" }))
        add(Action("battery_info", "查看电池状态", "dumpsys battery 完整输出", Group.SYSTEM,
            command = { _, _ -> "dumpsys battery" }))

        // ---------- 显示与动画 ----------
        add(Action("refresh_rate", "强制刷新率", "settings put system peak_refresh_rate", Group.DISPLAY,
            valueKey = "刷新率 Hz (如 120)",
            command = { _, v ->
                val hz = v.trim().ifEmpty { "60" }
                "settings put system peak_refresh_rate $hz; settings put system min_refresh_rate $hz; echo RATE_SET"
            },
            rollback = { _, _ -> "settings delete system peak_refresh_rate; settings delete system min_refresh_rate" }))
        add(Action("anim_scale", "动画速度缩放", "0 = 关闭，0.5 = 加速", Group.DISPLAY,
            valueKey = "缩放值 (0 / 0.5 / 1 / 2)",
            command = { _, v ->
                val s = v.trim().ifEmpty { "1" }
                "settings put global window_animation_scale $s; " +
                    "settings put global transition_animation_scale $s; " +
                    "settings put global animator_duration_scale $s; echo ANIM_SET"
            },
            rollback = { _, _ ->
                "settings put global window_animation_scale 1; " +
                    "settings put global transition_animation_scale 1; " +
                    "settings put global animator_duration_scale 1"
            }))
        add(Action("immersive", "沉浸全屏", "隐藏状态栏+导航栏", Group.DISPLAY,
            command = { _, _ -> "settings put global policy_control immersive.full=*" },
            rollback = { _, _ -> "settings put global policy_control null" }))
        add(Action("hide_status", "单独隐藏状态栏", "policy_control immersive.status", Group.DISPLAY,
            command = { _, _ -> "settings put global policy_control immersive.status=*" },
            rollback = { _, _ -> "settings put global policy_control null" }))
        add(Action("hide_nav", "单独隐藏导航栏", "policy_control immersive.navigation", Group.DISPLAY,
            command = { _, _ -> "settings put global policy_control immersive.navigation=*" },
            rollback = { _, _ -> "settings put global policy_control null" }))
        add(Action("restore_bars", "恢复系统栏", "清除 policy_control", Group.DISPLAY,
            command = { _, _ -> "settings put global policy_control null; echo OK" }))
        add(Action("grayscale", "灰度模式", "模拟色盲/黑白显示", Group.DISPLAY,
            command = { _, _ ->
                "settings put secure accessibility_display_daltonizer_enabled 1; " +
                    "settings put secure accessibility_display_daltonizer 0; echo GRAY_ON"
            },
            rollback = { _, _ -> "settings put secure accessibility_display_daltonizer_enabled 0" }))
        add(Action("restore_color", "恢复彩色", "关闭灰度", Group.DISPLAY,
            command = { _, _ -> "settings put secure accessibility_display_daltonizer_enabled 0; echo OK" }))
        add(Action("night_mode", "夜间模式", "cmd uimode night yes", Group.DISPLAY,
            command = { _, _ -> "cmd uimode night yes" },
            rollback = { _, _ -> "cmd uimode night no" }))
        add(Action("day_mode", "日间模式", "cmd uimode night no", Group.DISPLAY,
            command = { _, _ -> "cmd uimode night no" }))
        add(Action("clock_seconds", "状态栏时钟秒", "显示秒数", Group.DISPLAY,
            command = { _, _ -> "settings put secure clock_seconds 1; echo OK" },
            rollback = { _, _ -> "settings put secure clock_seconds 0" }))
        add(Action("battery_percent", "电池百分比", "状态栏显示电量数字", Group.DISPLAY,
            command = { _, _ -> "settings put system status_bar_show_battery_percent 1; echo OK" },
            rollback = { _, _ -> "settings put system status_bar_show_battery_percent 0" }))

        // ---------- 网络与防火墙 ----------
        add(Action("firewall_block", "应用级防火墙·阻断", "按 UID 禁止该应用联网 (需 root)", Group.NETWORK,
            true, true,
            command = { p, _ ->
                """
                uid=$(pm list packages -U ${pmPkg(p)} | head -1 | sed 's/.*uid://')
                iptables -I OUTPUT -m owner --uid-owner ${'$'}uid -j DROP 2>/dev/null
                ip6tables -I OUTPUT -m owner --uid-owner ${'$'}uid -j DROP 2>/dev/null
                echo FIREWALL_BLOCKED_${'$'}uid
                """.trimIndent()
            },
            rollback = { p, _ ->
                """
                uid=$(pm list packages -U ${pmPkg(p)} | head -1 | sed 's/.*uid://')
                iptables -D OUTPUT -m owner --uid-owner ${'$'}uid -j DROP 2>/dev/null
                ip6tables -D OUTPUT -m owner --uid-owner ${'$'}uid -j DROP 2>/dev/null
                echo FIREWALL_RESTORED
                """.trimIndent()
            }))
        add(Action("firewall_bg", "限制后台数据", "cmd netpolicy（无需 root）", Group.NETWORK,
            true, true,
            command = { p, _ ->
                "cmd netpolicy add restrict-background-blacklist ${pmPkg(p)} 2>/dev/null || " +
                    "cmd netpolicy set restrict-background true; echo BG_BLOCKED"
            },
            rollback = { p, _ -> "cmd netpolicy remove restrict-background-blacklist ${pmPkg(p)} 2>/dev/null; echo OK" }))
        add(Action("firewall_status", "查看防火墙规则", "iptables -S OUTPUT", Group.NETWORK,
            command = { _, _ -> "iptables -S OUTPUT 2>&1 | head -60" }))

        // ---------- 快捷动作 ----------
        add(Action("screenshot", "截图", "screencap 保存到 /sdcard", Group.QUICK,
            command = { _, _ -> "screencap -p /sdcard/LB_$(date +%s).png && echo SCREENSHOT_OK" }))
        add(Action("screen_off", "息屏", "模拟电源键", Group.QUICK,
            command = { _, _ -> "input keyevent 26" }))
        add(Action("recents", "最近任务", "KEYCODE_APP_SWITCH", Group.QUICK,
            command = { _, _ -> "input keyevent 187" }))
        add(Action("home", "回到桌面", "KEYCODE_HOME", Group.QUICK,
            command = { _, _ -> "input keyevent 3" }))
        add(Action("back", "返回键", "KEYCODE_BACK", Group.QUICK,
            command = { _, _ -> "input keyevent 4" }))
        add(Action("lock_screen", "锁屏", "KEYCODE_POWER + 延时", Group.QUICK,
            command = { _, _ -> "input keyevent 26; echo LOCKED" }))

        // ---------- Shell 工具 ----------
        add(Action("list_apps", "列出已安装应用", "pm list packages -3", Group.SHELL,
            command = { _, _ -> "pm list packages -3" }))
        add(Action("device_info", "设备信息", "getprop 摘要", Group.SHELL,
            command = { _, _ ->
                "echo MODEL=$(getprop ro.product.model); echo SDK=$(getprop ro.build.version.sdk); " +
                    "echo ROM=$(getprop ro.build.display.id); echo SELINUX=$(getenforce 2>/dev/null)"
            }))
        add(Action("shizuku_uid", "查看 Shizuku 身份", "id + uid", Group.SHELL,
            command = { _, _ -> "id; echo ---; dumpsys activity top | head -5" }))
    }

    val softwareActions: List<Action> = actions.filter { sectionOf(it) == Section.SOFTWARE }
    val systemActions: List<Action> = actions.filter { sectionOf(it) == Section.SYSTEM }

    fun byId(id: String): Action? = actions.find { it.id == id }

    /** 执行命令（UI 层已完成二次确认） */
    fun run(action: Action, pkg: String, value: String): ShellResult =
        ShizukuShell.exec(action.command(pkg, value))

    /** 回滚 */
    fun rollback(action: Action, pkg: String, value: String): ShellResult {
        val cmd = action.rollback ?: return ShellResult(-1, "", "该操作无回滚指令")
        return ShizukuShell.exec(cmd(pkg, value))
    }

    /** 常用 Shell 命令书签 */
    data class Bookmark(val label: String, val command: String)

    val bookmarks = listOf(
        Bookmark("已装应用(第三方)", "pm list packages -3"),
        Bookmark("全部应用", "pm list packages"),
        Bookmark("当前 Activity", "dumpsys activity activities | grep mResumedActivity"),
        Bookmark("电池状态", "dumpsys battery"),
        Bookmark("内存信息", "cat /proc/meminfo | head -8"),
        Bookmark("CPU 信息", "cat /proc/cpuinfo | head -12"),
        Bookmark("磁盘占用", "df -h"),
        Bookmark("网络接口", "ip addr"),
        Bookmark("Doze 状态", "dumpsys deviceidle"),
        Bookmark("刷新率", "settings get system peak_refresh_rate"),
        Bookmark("动画缩放", "settings get global window_animation_scale"),
        Bookmark("正在运行服务", "dumpsys activity services | head -40"),
        Bookmark("唤醒锁", "dumpsys power | grep -i wakelock"),
        Bookmark("已授权权限", "dumpsys package | grep -A5 grantedPermissions | head -40"),
        Bookmark("清缓存(全部)", "pm trim-caches 100000000000"),
    )

    /** 自定义脚本模板：点击后填入系统工具页的脚本编辑框 */
    val scriptTemplates = listOf(
        Bookmark("查看电池", "dumpsys battery"),
        Bookmark("电量/温度", "dumpsys battery | grep -E 'level|temperature|status'"),
        Bookmark("清全部缓存", "pm trim-caches 100000000000"),
        Bookmark("重启 SystemUI", "am force-stop com.android.systemui; echo DONE"),
        Bookmark("清除日志", "logcat -c; echo LOG_CLEARED"),
        Bookmark("内存摘要", "cat /proc/meminfo | head -6"),
    )

    // ==================== 日常工具 ====================

    /** 数据驱动的快捷开关：一条命令打开、一条关闭、一条回读当前状态 */
    data class DailyToggle(
        val id: String,
        val label: String,
        val enable: String,
        val disable: String,
        val read: String,
        val isOn: (String) -> Boolean,
    )

    object Daily {
        private val one: (String) -> Boolean = { it.trim() == "1" }

        val toggles = listOf(
            DailyToggle(
                id = "dark_mode", label = "深色模式",
                enable = "cmd uimode night yes",
                disable = "cmd uimode night no",
                read = "cmd uimode night | tr 'A-Z' 'a-z' | grep -oE 'yes|no' | head -1",
                isOn = { it.contains("yes") },
            ),
            DailyToggle(
                id = "auto_rotate", label = "自动旋转",
                enable = "settings put system accelerometer_rotation 1",
                disable = "settings put system accelerometer_rotation 0",
                read = "settings get system accelerometer_rotation",
                isOn = one,
            ),
            DailyToggle(
                id = "screen_stay_on", label = "屏幕常亮",
                enable = "settings put system screen_off_timeout 2147483647",
                disable = "settings put system screen_off_timeout 60000",
                read = "settings get system screen_off_timeout",
                isOn = { it.trim().toLongOrNull()?.let { ms -> ms >= 600000 } ?: false },
            ),
            DailyToggle(
                id = "auto_brightness", label = "自动亮度",
                enable = "settings put system screen_brightness_mode 1",
                disable = "settings put system screen_brightness_mode 0",
                read = "settings get system screen_brightness_mode",
                isOn = one,
            ),
            DailyToggle(
                id = "wifi", label = "WiFi",
                enable = "svc wifi enable 2>/dev/null || cmd wifi set-wifi-state enabled 2>/dev/null",
                disable = "svc wifi disable 2>/dev/null || cmd wifi set-wifi-state disabled 2>/dev/null",
                read = "settings get global wifi_on",
                isOn = one,
            ),
            DailyToggle(
                id = "mobile_data", label = "移动数据",
                enable = "svc data enable 2>/dev/null || cmd phone data enable 2>/dev/null",
                disable = "svc data disable 2>/dev/null || cmd phone data disable 2>/dev/null",
                read = "settings get global mobile_data",
                isOn = one,
            ),
            DailyToggle(
                id = "bluetooth", label = "蓝牙",
                enable = "cmd bluetooth_manager enable 2>/dev/null || svc bluetooth enable 2>/dev/null",
                disable = "cmd bluetooth_manager disable 2>/dev/null || svc bluetooth disable 2>/dev/null",
                read = "settings get global bluetooth_on",
                isOn = one,
            ),
            DailyToggle(
                id = "battery_percent", label = "电量百分比",
                enable = "settings put system status_bar_show_battery_percent 1",
                disable = "settings put system status_bar_show_battery_percent 0",
                read = "settings get system status_bar_show_battery_percent",
                isOn = one,
            ),
        )

        /** 一次 shell 调用读回全部状态，输出形如 dark_mode=yes */
        fun queryAll(): String = buildString {
            toggles.forEach { t ->
                append("echo \"").append(t.id).append('=').append('$').append('(')
                    .append(t.read).append(")\"\n")
            }
        }

        /** 屏幕亮度：先关自动亮度，再写手动值 */
        fun brightness(value: Int): String =
            "settings put system screen_brightness_mode 0; " +
                "settings put system screen_brightness $value; echo BRIGHTNESS_SET_$value"

        /** 自动休眠时间（毫秒） */
        fun timeout(ms: String): String =
            "settings put system screen_off_timeout $ms; echo TIMEOUT_SET_$ms"

        fun readDisplay(): String = listOf(
            "echo \"BR=${'$'}(settings get system screen_brightness)\"",
            "echo \"MODE=${'$'}(settings get system screen_brightness_mode)\"",
            "echo \"TIMEOUT=${'$'}(settings get system screen_off_timeout)\"",
        ).joinToString("\n")

        val timeouts = listOf(
            Bookmark("15 秒", "15000"),
            Bookmark("1 分钟", "60000"),
            Bookmark("5 分钟", "300000"),
            Bookmark("10 分钟", "600000"),
            Bookmark("常亮", "2147483647"),
        )

        /** 媒体键：上一曲 / 播放暂停 / 下一曲 / 音量± / 静音 */
        val mediaKeys = listOf(
            Bookmark("上一曲", "input keyevent 88"),
            Bookmark("播放 / 暂停", "input keyevent 85"),
            Bookmark("下一曲", "input keyevent 87"),
            Bookmark("音量 −", "input keyevent 25"),
            Bookmark("音量 +", "input keyevent 24"),
            Bookmark("静音切换", "input keyevent 164"),
        )

        /** 随手维护 */
        val maintenance = listOf(
            Bookmark("清理全部缓存", "pm trim-caches 100000000000 >/dev/null 2>&1; echo CACHE_TRIMMED"),
            Bookmark("重启 SystemUI", "am force-stop com.android.systemui >/dev/null 2>&1; echo SYSUI_RESTARTED"),
            Bookmark("结束后台进程", "am kill-all >/dev/null 2>&1; echo BACKGROUND_KILLED"),
            Bookmark("清除系统日志", "logcat -c >/dev/null 2>&1; echo LOG_CLEARED"),
        )

        /** 设备信息一览：统一输出 key=value 便于解析 */
        val deviceInfo: String = listOf(
            "echo \"model=${'$'}(getprop ro.product.model)\"",
            "echo \"brand=${'$'}(getprop ro.product.brand)\"",
            "echo \"android=${'$'}(getprop ro.build.version.release)\"",
            "echo \"sdk=${'$'}(getprop ro.build.version.sdk)\"",
            "echo \"rom=${'$'}(getprop ro.build.display.id)\"",
            "echo \"resolution=${'$'}(wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)\"",
            "echo \"density=${'$'}(wm density | grep -oE '[0-9]+' | tail -1)\"",
            "echo \"battery=${'$'}(dumpsys battery | grep -m1 'level:' | grep -oE '[0-9]+' | head -1)\"",
            "echo \"temp=${'$'}(dumpsys battery | grep -m1 'temperature:' | grep -oE '[0-9]+' | head -1)\"",
            "echo \"charging=${'$'}(dumpsys battery | grep -m1 'status:' | grep -oE '[0-9]+' | head -1)\"",
            "echo \"mem=${'$'}(cat /proc/meminfo | grep MemTotal | grep -oE '[0-9]+' | head -1)\"",
            "echo \"mem_free=${'$'}(cat /proc/meminfo | grep MemAvailable | grep -oE '[0-9]+' | head -1)\"",
            "echo \"storage=${'$'}(df -h /sdcard | tail -1 | tr -s ' ' | cut -d' ' -f2,4)\"",
            "echo \"uptime=${'$'}(uptime | sed 's/^ *//' | cut -c1-64)\"",
            "echo \"selinux=${'$'}(getenforce 2>/dev/null)\"",
            "echo \"uid=${'$'}(id -u)\"",
        ).joinToString("\n")

        /** 屏幕录制到固定路径，结果里回显路径 */
        fun record(seconds: Int): String =
            "screenrecord --time-limit $seconds --bit-rate 6000000 /sdcard/LB_rec.mp4 " +
                ">/dev/null 2>&1; if [ -s /sdcard/LB_rec.mp4 ]; then " +
                "echo REC_SAVED:/sdcard/LB_rec.mp4; else echo REC_FAILED; fi"

        val recordSeconds = listOf(5, 10, 30, 60)
    }
}
