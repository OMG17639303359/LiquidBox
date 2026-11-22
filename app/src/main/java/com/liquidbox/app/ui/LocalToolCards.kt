package com.liquidbox.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liquidbox.app.glass.LiquidButton
import com.liquidbox.app.glass.LocalGlassPalette
import com.liquidbox.app.glass.liquidGlass
import com.liquidbox.app.tools.LocalTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 结果统一走页面底部浮层；onChanged 触发其它卡片刷新（例如选了新目录） */
private suspend fun localRun(block: () -> String): String = withContext(Dispatchers.IO) {
    runCatching { block() }.getOrElse { "失败: ${it.message}" }
}

// =====================================================================
// 1. 工作目录
// =====================================================================
@Composable
fun LocalWorkdirCard(onResult: (String) -> Unit, onChanged: () -> Unit) {
    val palette = LocalGlassPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf(LocalTools.rootLabel(context)) }

    val pickTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = localRun {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                LocalTools.setTreeUri(uri.toString())
                LocalTools.rootLabel(context)
            }
            label = LocalTools.rootLabel(context)
            onResult("已设置工作目录：$r")
            onChanged()
        }
    }

    GlassCard {
        T("工作目录", size = 15, weight = FontWeight.Bold)
        T("文件查看 / 添加 / 改名 / 解压 / 二维码，都在这个目录里进行。默认用应用私有目录，零权限即可使用。",
            size = 11, color = palette.textSecondary)
        T("当前：$label", size = 12, color = palette.accent)
        Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
            LiquidButton(onClick = { pickTree.launch(null) }, modifier = Modifier.weight(1f)) {
                T("选择目录", size = 13, align = TextAlign.Center)
            }
            LiquidButton(onClick = {
                LocalTools.setTreeUri(null)
                label = LocalTools.rootLabel(context)
                onResult("已恢复为应用私有目录")
                onChanged()
            }, modifier = Modifier.weight(1f)) {
                T("恢复默认", size = 13, align = TextAlign.Center)
            }
        }
    }
}

// =====================================================================
// 2. 二维码生成
// =====================================================================
@Composable
fun LocalQrCard(onResult: (String) -> Unit, onChanged: () -> Unit) {
    val palette = LocalGlassPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    // 异步生成，避免输入时在主线程循环 setPixel 造成掉帧
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(text) {
        bitmap = if (text.isBlank()) null
        else withContext(Dispatchers.Default) { LocalTools.qrBitmap(text, 480) }
    }

    GlassCard {
        T("二维码生成", size = 15, weight = FontWeight.Bold)
        T("输入文字或链接，实时生成二维码，可保存为 PNG 到工作目录。",
            size = 11, color = palette.textSecondary)
        GlassTextField(text, { text = it }, "内容：文字 / 网址 / WiFi 等")
        GlassTextField(name, { name = it }, "文件名（可选，如 code.png）")

        val preview = bitmap
        if (preview != null) {
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "二维码预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(180f.dp)
                        .clip(RoundedCornerShape(12f.dp))
                        .background(Color.White)
                        .padding(8f.dp),
                )
            }
        } else {
            T("预览：请输入内容", size = 11, color = palette.textSecondary)
        }

        LiquidButton(onClick = {
            if (text.isBlank()) { onResult("请先输入二维码内容"); return@LiquidButton }
            scope.launch {
                val fileName = name.ifBlank { "qr_${System.currentTimeMillis()}.png" }
                onResult(localRun { LocalTools.saveQrPng(context, "", fileName, text) })
                onChanged()
            }
        }, modifier = Modifier.fillMaxWidth()) {
            T("保存二维码到工作目录", size = 13, align = TextAlign.Center)
        }
    }
}

// =====================================================================
// 3. ZIP 解压 / 打包
// =====================================================================
@Composable
fun LocalZipCard(onResult: (String) -> Unit, onChanged: () -> Unit) {
    val palette = LocalGlassPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var zipName by remember { mutableStateOf("") }

    val pickZip = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            onResult(localRun { LocalTools.unzip(context, uri, "") })
            onChanged()
        }
    }

    GlassCard {
        T("ZIP 解压 / 打包", size = 15, weight = FontWeight.Bold)
        T("从任意位置选 zip 解压到工作目录（带路径穿越防护）；或把整个工作目录打包成 zip。",
            size = 11, color = palette.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
            LiquidButton(onClick = { pickZip.launch("*/*") }, modifier = Modifier.weight(1f)) {
                T("选择 zip 解压", size = 12, align = TextAlign.Center)
            }
        }
        GlassTextField(zipName, { zipName = it }, "打包输出名（可选，如 backup.zip）")
        LiquidButton(onClick = {
            scope.launch {
                val n = zipName.ifBlank { "archive_${System.currentTimeMillis()}.zip" }
                onResult(localRun { LocalTools.zip(context, "", n) })
                onChanged()
            }
        }, modifier = Modifier.fillMaxWidth()) {
            T("打包工作目录为 zip", size = 13, align = TextAlign.Center)
        }
    }
}

// =====================================================================
// 4. 文件管理：查看 / 添加 / 改名 / 删除 / 进入子目录
// =====================================================================
@Composable
fun LocalFilesCard(onResult: (String) -> Unit, version: Int, onChanged: () -> Unit) {
    val palette = LocalGlassPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var rel by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(emptyList<LocalTools.Item>()) }
    var selected by remember { mutableStateOf<LocalTools.Item?>(null) }
    var viewerText by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var renameTo by remember { mutableStateOf("") }

    suspend fun refresh() {
        items = withContext(Dispatchers.IO) { LocalTools.list(context, rel) }
    }
    LaunchedEffect(version, rel) { refresh() }

    GlassCard {
        T("文件管理", size = 15, weight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("目录：/${rel}", Modifier.weight(1f), size = 12, color = palette.accent, maxLines = 1)
            if (rel.isNotBlank()) {
                LiquidButton(onClick = {
                    rel = rel.substringBeforeLast('/', "")
                    selected = null
                    viewerText = ""
                }) { T("返回上级", size = 11, align = TextAlign.Center) }
            }
            LiquidButton(onClick = { scope.launch { refresh() } }) {
                T("刷新", size = 11, align = TextAlign.Center)
            }
        }

        // 文件列表（普通 Column，避免与外层 LazyColumn 嵌套滚动冲突）
        if (items.isEmpty()) {
            T("（空目录，或工作目录不可读）", size = 11, color = palette.textSecondary)
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320f.dp)
                    .clip(RoundedCornerShape(12f.dp))
                    .border(1f.dp, palette.textSecondary.copy(alpha = 0.35f), RoundedCornerShape(12f.dp)),
            ) {
                items.take(60).forEach { f ->
                    val isSel = selected?.name == f.name
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSel) palette.accent.copy(alpha = 0.18f)
                                else androidx.compose.ui.graphics.Color.Transparent,
                            )
                            .clickable {
                                selected = f
                                viewerText = ""
                                renameTo = f.name
                            }
                            .padding(horizontal = 12f.dp, vertical = 9f.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T(if (f.isDir) "▣" else "▤", size = 14, color = palette.accent)
                        Column(Modifier.weight(1f).padding(start = 10f.dp)) {
                            T(f.name, size = 13, maxLines = 1)
                            T(
                                if (f.isDir) "文件夹" else "${f.size} B · ${f.mime}",
                                size = 10, color = palette.textSecondary, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        selected?.let { f ->
            T("已选中：${f.name}", size = 12, color = palette.accent, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                if (f.isDir) {
                    LiquidButton(onClick = {
                        rel = if (rel.isBlank()) f.name else "$rel/${f.name}"
                        selected = null; viewerText = ""
                    }, modifier = Modifier.weight(1f)) { T("进入", size = 12, align = TextAlign.Center) }
                } else {
                    LiquidButton(onClick = {
                        scope.launch {
                            viewerText = localRun { LocalTools.readText(context, rel, f.name) }
                        }
                    }, modifier = Modifier.weight(1f)) { T("查看内容", size = 12, align = TextAlign.Center) }
                }
                LiquidButton(onClick = {
                    scope.launch {
                        onResult(localRun { LocalTools.delete(context, rel, f.name) })
                        selected = null; viewerText = ""
                        refresh(); onChanged()
                    }
                }, modifier = Modifier.weight(1f)) { T("删除", size = 12, align = TextAlign.Center) }
            }
            GlassTextField(renameTo, { renameTo = it }, "改名为…")
            LiquidButton(onClick = {
                if (renameTo.isBlank()) { onResult("请输入新名称"); return@LiquidButton }
                scope.launch {
                    onResult(localRun { LocalTools.rename(context, rel, f.name, renameTo) })
                    selected = null
                    refresh(); onChanged()
                }
            }, modifier = Modifier.fillMaxWidth()) { T("确认改名", size = 12, align = TextAlign.Center) }
        }

        if (viewerText.isNotBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .liquidGlass(blurDp = 3f, lensHeightDp = 6f, lensAmountDp = 8f)
                    .padding(12f.dp),
            ) {
                T(viewerText.take(1500), size = 11, color = palette.textPrimary)
            }
            LiquidButton(onClick = { clipboard.setText(AnnotatedString(viewerText)) },
                modifier = Modifier.fillMaxWidth()) {
                T("复制内容到剪贴板", size = 12, align = TextAlign.Center)
            }
        }

        T("新建", size = 12, weight = FontWeight.Bold, color = palette.textSecondary)
        GlassTextField(newName, { newName = it }, "名称（文件带扩展名 / 文件夹名）")
        GlassTextField(newContent, { newContent = it }, "文件内容（仅新建文件时填写）")
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            LiquidButton(onClick = {
                if (newName.isBlank()) { onResult("请输入名称"); return@LiquidButton }
                scope.launch {
                    onResult(localRun { LocalTools.createFile(context, rel, newName, newContent) })
                    newName = ""; newContent = ""
                    refresh(); onChanged()
                }
            }, modifier = Modifier.weight(1f)) { T("新建文件", size = 12, align = TextAlign.Center) }
            LiquidButton(onClick = {
                if (newName.isBlank()) { onResult("请输入名称"); return@LiquidButton }
                scope.launch {
                    onResult(localRun { LocalTools.createFolder(context, rel, newName) })
                    newName = ""
                    refresh(); onChanged()
                }
            }, modifier = Modifier.weight(1f)) { T("新建文件夹", size = 12, align = TextAlign.Center) }
        }
    }
}

// =====================================================================
// 5. 文本工具：Base64 / 哈希 / 密码 / 统计 / JSON
// =====================================================================
@Composable
fun LocalTextCard() {
    val palette = LocalGlassPalette.current
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    fun set(r: String) { result = r }

    GlassCard {
        T("文本工具", size = 15, weight = FontWeight.Bold)
        T("全部本地计算，不联网、不需要任何权限。", size = 11, color = palette.textSecondary)
        GlassTextField(input, { input = it }, "输入文本")

        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            LiquidButton(onClick = { set(LocalTools.base64("encode", input)) }, modifier = Modifier.weight(1f)) {
                T("B64 编码", size = 11, align = TextAlign.Center)
            }
            LiquidButton(onClick = { set(LocalTools.base64("decode", input)) }, modifier = Modifier.weight(1f)) {
                T("B64 解码", size = 11, align = TextAlign.Center)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            LiquidButton(onClick = { set(LocalTools.hash("MD5", input)) }, modifier = Modifier.weight(1f)) {
                T("MD5", size = 11, align = TextAlign.Center)
            }
            LiquidButton(onClick = { set(LocalTools.hash("SHA-256", input)) }, modifier = Modifier.weight(1f)) {
                T("SHA-256", size = 11, align = TextAlign.Center)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            LiquidButton(onClick = { set(LocalTools.password(16, true, true, false)) }, modifier = Modifier.weight(1f)) {
                T("随机密码", size = 11, align = TextAlign.Center)
            }
            LiquidButton(onClick = { set(LocalTools.stats(input)) }, modifier = Modifier.weight(1f)) {
                T("字数统计", size = 11, align = TextAlign.Center)
            }
            LiquidButton(onClick = { set(LocalTools.json(input)) }, modifier = Modifier.weight(1f)) {
                T("JSON 美化", size = 11, align = TextAlign.Center)
            }
        }

        if (result.isNotBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .liquidGlass(blurDp = 3f, lensHeightDp = 6f, lensAmountDp = 8f)
                    .padding(12f.dp),
            ) {
                T(result.take(2000), size = 12, color = palette.textPrimary)
            }
            LiquidButton(onClick = { clipboard.setText(AnnotatedString(result)) },
                modifier = Modifier.fillMaxWidth()) {
                T("复制结果", size = 12, align = TextAlign.Center)
            }
        }
    }
}
