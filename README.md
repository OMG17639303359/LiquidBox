# LiquidBox · 液态玻璃 Shizuku 工具箱

A Shizuku-powered Android toolbox with a fully liquid-glass UI and an on-device AI agent.

基于 **Shizuku** 的 Android 工具箱，Jetpack Compose 全液态玻璃 UI，全部玻璃效果由
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 的 **Backdrop 2.0.1**
架构实现，未使用任何其他液态玻璃库。

## 功能 · Features

| 分类 Category | 内容 Content |
| --- | --- |
| 应用管理 Apps | 冻结/解冻、停用、隐藏/取消隐藏、暂停、强行停止、清空数据、系统应用卸载与恢复、组件启停、权限与 AppOps 批量配置、高级 APK 安装、强制 PiP |
| 系统增强 System | Doze 白名单、待机桶、强制 Doze、电池白名单、电量修改、无线调试降级通道 |
| 显示与动画 Display | 强制刷新率、动画速度缩放、沉浸全屏、状态栏/导航栏单独隐藏、灰度、夜间模式、时钟秒、电池百分比 |
| 网络与快捷 Network | 应用级防火墙、后台数据限制、截图/息屏/最近任务、Shell 终端与命令书签 |
| 日常与本地工具 Daily & Local | 快捷开关、亮度/休眠、媒体与音量、设备信息、随手维护、录屏截图；二维码生成、ZIP 解压/打包、文件查看/添加/改名/删除、Base64、哈希校验——无需 Shizuku |
| AI 操作手机 AI Agent | OpenAI / Anthropic / Gemini / DeepSeek 四种 Provider、多模态图片上传、自定义 Base URL 与 headers、读屏/点击/滑动/输入/启动 App/执行 shell 的工具体系、每工具 Off/Ask/Auto 权限、危险动作确认卡片、本地执行日志 |

所有破坏性操作均**二次确认**，且带 `rollback` 的动作在结果栏提供**一键回滚**。

## 界面风格 · Styles

- **液态玻璃 Liquid Glass**：官方 catalog 原样引入，壁纸 + Backdrop 实时背面模糊与折射。
- **MIUIX**：浅色纯色风格，无玻璃。
- **M3**：Material 3 深色纯色风格，无玻璃。

## UI 实现要点

**直接引入 Kyant0 官方 catalog 源码，一字未改。**

`app/src/main/java/com/kyant/backdrop/catalog/` 下存放从
`Kyant0/AndroidLiquidGlass@kmp` 的 `app/src/commonMain/kotlin/` 原样复制的文件：
`LiquidBottomTabs.kt` / `LiquidBottomTab.kt` / `LiquidButton.kt` /
`LiquidToggle.kt` / `LiquidSlider.kt`，以及它们依赖的
`DampedDragAnimation.kt` / `InteractiveHighlight.kt` /
`DragGestureInspector.kt` / `ProgressConverter.kt`。
包名保持 `com.kyant.backdrop.catalog.*`，未改动任何一行。

`com.liquidbox.app.glass` 下只保留**薄转发层**：把参数透传给官方组件，
不实现任何玻璃效果。因此折射、色散、拖拽高光等全部是官方实现。

层级遵循官方 `BackdropDemoScaffold`：`Modifier.layerBackdrop` 只修饰壁纸层，
内部不含任何 `drawBackdrop` 玻璃组件；所有玻璃组件在层外采样这个 backdrop。
绝不能把玻璃组件放进 `layerBackdrop` 修饰的层内部，否则会产生自引用递归绘制。

**平台桥接**：官方 catalog 是 KMP 工程，本工程是纯 Android 单平台模块，
故不搬入 `expect/actual` 平台声明文件，改为新增 `utils/PlatformBridge.kt` 提供同名实现
（本工程新增文件，非官方源码）。所有官方组件源码无需任何改动即可编译。

**自定义背景**：设置页可切换内置壁纸或从相册选图，图片复制到应用私有目录持久化。
壁纸即 backdrop 采样源，切换后玻璃内的模糊与折射内容立即变化。

**质量档位**：`FULL / REDUCED / BASIC`，三档均逐帧采样背面并做实时背面模糊；
`FULL` 额外启用 lens 折射。默认完整档，系统不支持 RuntimeShader 时由库自动跳过 lens。

## 构建 · Build

- AGP 9.3.2 / Kotlin 2.4.10 / Compose 1.12.0 / Gradle 9.7.1 / JDK 17
- `compileSdk 37`，`minSdk 26`，版本 1.0beta
- 构建命令：`bash build.sh :app:assembleDebug`
- 已编译安装包见仓库根目录 `LiquidBox-1.0beta.apk`

## 使用前提 · Requirements

1. 安装并启动 [Shizuku](https://github.com/RikkaApps/Shizuku)（或 Sui），通过无线调试/ADB 激活。
2. 打开 App，在首页点击「刷新」，如显示「待授权」则点「请求授权」并允许。
3. AI 功能需自己在设置页填入 Provider、Base URL 与 API Key；Key 用 Android Keystore
   AES-256-GCM 加密后存本地，无后端、无遥测。
4. 部分功能依赖设备能力：`iptables` 防火墙需 root；`pm hide`、`settings put secure` 等
   在部分 ROM 上会被限制，执行结果会在结果栏显示真实 shell 输出。

> 说明：`lens` 折射依赖 Android 13+ 的 RuntimeShader，这是 Backdrop 库自身限制；
> 低版本系统会保留实时背面模糊，属预期行为。
