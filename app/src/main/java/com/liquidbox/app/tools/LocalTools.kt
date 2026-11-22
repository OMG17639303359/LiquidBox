package com.liquidbox.app.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.liquidbox.app.data.SecureStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 本地工具：全部为纯 Kotlin / Android 平台 API，不依赖 Shizuku、不执行 shell、不需要 root。
 *
 * 工作目录默认落在应用私有外部目录（getExternalFilesDir），零权限即可读写；
 * 用户也可通过 SAF 选择任意公开目录并持久授权。所有文件操作都走 DocumentFile，
 * 因此两种来源统一处理。
 */
object LocalTools {

    private const val KEY_TREE = "local_tree_uri"

    data class Item(
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val mime: String,
        val lastModified: Long,
    )

    fun treeUri(): String = SecureStore.getPlain(KEY_TREE)
    fun setTreeUri(uri: String?) {
        if (uri.isNullOrBlank()) SecureStore.remove(KEY_TREE)
        else SecureStore.putPlain(KEY_TREE, uri)
    }
    fun hasCustomTree(): Boolean = treeUri().isNotBlank()

    /** 当前工作根目录：优先用户选的 SAF 目录，否则应用私有目录 */
    fun root(context: Context): DocumentFile {
        val tree = treeUri()
        if (tree.isNotBlank()) {
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(tree)) }
                .getOrNull()?.let { return it }
        }
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val work = File(base, "workspace").apply { if (!exists()) mkdirs() }
        return DocumentFile.fromFile(work)!!
    }

    fun rootLabel(context: Context): String =
        if (hasCustomTree()) "已选目录" else "应用私有目录"

    /** 按相对路径（"/" 分隔）逐级 findFile，返回目标目录 */
    private fun dir(context: Context, rel: String): DocumentFile? {
        var cur = root(context)
        if (rel.isBlank()) return cur
        for (seg in rel.split("/").filter { it.isNotBlank() }) {
            cur = cur.findFile(seg) ?: return null
            if (!cur.isDirectory) return null
        }
        return cur
    }

    private fun safeSegment(name: String): String? {
        val n = name.trim()
        if (n.isEmpty() || n == "." || n == "..") return null
        if (n.contains('/') || n.contains('\\') || n.contains('\n')) return null
        return n
    }

    // ---------------- 文件查看 / 添加 / 改名 / 删除 ----------------

    fun list(context: Context, rel: String): List<Item> = runCatching {
        val d = dir(context, rel) ?: return emptyList()
        d.listFiles().map { f ->
            Item(
                name = f.name ?: "(无名)",
                isDir = f.isDirectory,
                size = if (f.isDirectory) 0L else f.length(),
                mime = f.type ?: if (f.isDirectory) "文件夹" else "文件",
                lastModified = f.lastModified(),
            )
        }.sortedWith(compareByDescending<Item> { it.isDir }.thenBy { it.name.lowercase() })
    }.getOrDefault(emptyList())

    fun readText(context: Context, rel: String, name: String, maxBytes: Int = 64 * 1024): String = runCatching {
        val seg = safeSegment(name) ?: return "文件名不合法"
        val d = dir(context, rel) ?: return "目录不存在"
        val f = d.findFile(seg) ?: return "找不到 $seg"
        if (f.isDirectory) return "$seg 是文件夹"
        context.contentResolver.openInputStream(f.uri)?.use { input ->
            val bytes = input.readBytes().let { if (it.size > maxBytes) it.copyOf(maxBytes) else it }
            val text = String(bytes, Charsets.UTF_8)
            if (text.count { it == '\uFFFD' } > 8) return "二进制文件（非文本），无法预览"
            if (text.isEmpty()) "（空文件）" else text
        } ?: "无法打开文件"
    }.getOrElse { "读取失败: ${it.message}" }

    fun createFile(context: Context, rel: String, name: String, content: String): String = runCatching {
        val seg = safeSegment(name) ?: return "文件名不合法（不能含 / 或为空）"
        val d = dir(context, rel) ?: return "目录不存在，请先选择有效的工作目录"
        if (d.findFile(seg) != null) return "$seg 已存在"
        val mime = if (seg.endsWith(".json", true)) "application/json"
        else if (seg.endsWith(".txt", true) || !seg.contains('.')) "text/plain"
        else "application/octet-stream"
        val f = d.createFile(mime, seg) ?: return "创建失败"
        context.contentResolver.openOutputStream(f.uri, "w")?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
        } ?: return "无法写入"
        "已创建 $seg（${content.length} 字符）"
    }.getOrElse { "创建失败: ${it.message}" }

    /** 覆盖写文本文件：不存在则创建，存在则清空重写 */
    fun writeText(context: Context, rel: String, name: String, content: String): String = runCatching {
        val seg = safeSegment(name) ?: return "文件名不合法"
        val d = dir(context, rel) ?: return "目录不存在"
        val f = d.findFile(seg) ?: d.createFile("text/plain", seg) ?: return "创建失败"
        context.contentResolver.openOutputStream(f.uri, "w")?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
        } ?: return "无法写入"
        "已写入 $seg（${content.length} 字符）"
    }.getOrElse { "写入失败: ${it.message}" }

    fun createFolder(context: Context, rel: String, name: String): String = runCatching {
        val seg = safeSegment(name) ?: return "文件夹名不合法"
        val d = dir(context, rel) ?: return "目录不存在"
        if (d.findFile(seg) != null) return "$seg 已存在"
        d.createDirectory(seg) ?: return "创建失败"
        "已创建文件夹 $seg"
    }.getOrElse { "创建失败: ${it.message}" }

    fun rename(context: Context, rel: String, oldName: String, newName: String): String = runCatching {
        val nn = safeSegment(newName) ?: return "新名称不合法"
        val d = dir(context, rel) ?: return "目录不存在"
        val f = d.findFile(safeSegment(oldName) ?: return "原名不合法") ?: return "找不到 $oldName"
        if (d.findFile(nn) != null && nn != oldName) return "$nn 已存在"
        if (f.renameTo(nn)) "已改名为 $nn" else "改名失败（系统拒绝）"
    }.getOrElse { "改名失败: ${it.message}" }

    fun delete(context: Context, rel: String, name: String): String = runCatching {
        val seg = safeSegment(name) ?: return "名称不合法"
        val d = dir(context, rel) ?: return "目录不存在"
        val f = d.findFile(seg) ?: return "找不到 $seg"
        if (recursiveDelete(f)) "已删除 $seg" else "删除失败"
    }.getOrElse { "删除失败: ${it.message}" }

    private fun recursiveDelete(f: DocumentFile): Boolean {
        if (f.isDirectory) f.listFiles().forEach { recursiveDelete(it) }
        return f.delete()
    }

    // ---------------- ZIP 解压 / 打包 ----------------

    /** 从用户选中的 zip Uri 解压到工作目录下的子文件夹，带 Zip Slip 防护 */
    fun unzip(context: Context, zipUri: Uri, rel: String): String = runCatching {
        val d = dir(context, rel) ?: return "目录不存在"
        var count = 0
        val buffer = ByteArray(16 * 1024)
        context.contentResolver.openInputStream(zipUri)?.use { input ->
            ZipInputStream(input).use { zis ->
                while (true) {
                    val entry: ZipEntry = zis.nextEntry ?: break
                    val raw = entry.name
                    // 防 Zip Slip：拒绝绝对路径、盘符、.. 越级
                    if (raw.startsWith("/") || raw.contains("\\") || raw.split("/").any { it == ".." }) {
                        zis.closeEntry(); continue
                    }
                    val parts = raw.split("/").filter { it.isNotEmpty() }
                    if (parts.isEmpty()) { zis.closeEntry(); continue }
                    if (entry.isDirectory) {
                        ensurePath(d, parts, isDir = true)
                        zis.closeEntry(); continue
                    }
                    val parentParts = parts.dropLast(1)
                    val parent = if (parentParts.isEmpty()) d else ensurePath(d, parentParts, isDir = true)
                    if (parent == null) { zis.closeEntry(); continue }
                    val fileName = parts.last()
                    val existing = parent.findFile(fileName)
                    existing?.delete()
                    val target = parent.createFile("application/octet-stream", fileName)
                        ?: run { zis.closeEntry(); continue }
                    context.contentResolver.openOutputStream(target.uri, "w")?.use { out ->
                        while (true) {
                            val n = zis.read(buffer)
                            if (n <= 0) break
                            out.write(buffer, 0, n)
                        }
                    }
                    count++
                    zis.closeEntry()
                }
            }
        } ?: return "无法打开 zip"
        if (count == 0) "zip 为空或没有可解压的文件" else "解压完成：共 $count 个文件到当前目录"
    }.getOrElse { "解压失败: ${it.message}" }

    private fun ensurePath(root: DocumentFile, parts: List<String>, isDir: Boolean): DocumentFile? {
        var cur = root
        for (p in parts) {
            val seg = safeSegment(p) ?: return null
            var next = cur.findFile(seg)
            if (next == null) next = cur.createDirectory(seg)
            if (next == null) return null
            cur = next
        }
        return cur
    }

    /** 解压工作目录里已存在的某个 zip 文件（按文件名定位），供 AI 工具链调用 */
    fun unzipByName(context: Context, rel: String, name: String): String = runCatching {
        val seg = safeSegment(name) ?: return "文件名不合法"
        val d = dir(context, rel) ?: return "目录不存在"
        val f = d.findFile(seg) ?: return "找不到 $seg（请先把它放入工作目录）"
        unzip(context, f.uri, rel)
    }.getOrElse { "解压失败: ${it.message}" }

    /** 把当前目录（含子目录）打包成一个 zip 文件 */
    fun zip(context: Context, rel: String, outName: String): String = runCatching {
        val d = dir(context, rel) ?: return "目录不存在"
        val name = (safeSegment(outName) ?: return "文件名不合法").let {
            if (it.endsWith(".zip", true)) it else "$it.zip"
        }
        d.findFile(name)?.delete()
        val target = d.createFile("application/zip", name) ?: return "创建 zip 失败"
        var count = 0
        context.contentResolver.openOutputStream(target.uri, "w")?.use { out ->
            ZipOutputStream(out).use { zos ->
                fun walk(node: DocumentFile, prefix: String) {
                    if (node.name == name) return
                    if (node.isDirectory) {
                        node.listFiles().forEach { walk(it, prefix + node.name + "/") }
                    } else {
                        zos.putNextEntry(ZipEntry(prefix + node.name))
                        context.contentResolver.openInputStream(node.uri)?.use { it.copyTo(zos) }
                        zos.closeEntry()
                        count++
                    }
                }
                d.listFiles().forEach { walk(it, "") }
            }
        } ?: return "无法写入 zip"
        "打包完成：$name（$count 个文件）"
    }.getOrElse { "打包失败: ${it.message}" }

    // ---------------- 二维码生成 ----------------

    /** 用 ZXing 生成二维码位图；返回 android.graphics.Bitmap，Compose 侧 asImageBitmap() 显示 */
    fun qrBitmap(text: String, size: Int = 640): Bitmap? = runCatching {
        if (text.isEmpty()) return null
        val hints = HashMap<EncodeHintType, Any>().apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 2)
        }
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val w = matrix.width
        val h = matrix.height
        for (x in 0 until size) {
            for (y in 0 until size) {
                val mx = x * w / size
                val my = y * h / size
                bmp.setPixel(x, y, if (matrix.get(mx, my)) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    }.getOrNull()

    /** 把二维码保存为 PNG 到工作目录 */
    fun saveQrPng(context: Context, rel: String, name: String, text: String): String = runCatching {
        val bmp = qrBitmap(text) ?: return "生成失败（内容为空或过长）"
        val d = dir(context, rel) ?: return "目录不存在"
        val fileName = (safeSegment(name) ?: return "文件名不合法").let {
            if (it.endsWith(".png", true)) it else "$it.png"
        }
        d.findFile(fileName)?.delete()
        val f = d.createFile("image/png", fileName) ?: return "创建文件失败"
        context.contentResolver.openOutputStream(f.uri, "w")?.use {
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) return "写入失败"
        } ?: return "无法写入"
        "已保存 $fileName（${bmp.width}×${bmp.height}）"
    }.getOrElse { "保存失败: ${it.message}" }

    // ---------------- 文本工具（纯计算，不需要目录） ----------------

    fun base64(mode: String, text: String): String = runCatching {
        when (mode) {
            "decode" -> String(Base64.decode(text.trim(), Base64.DEFAULT), Charsets.UTF_8)
            else -> Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }.getOrElse { "失败: ${it.message}" }

    fun hash(algo: String, text: String): String = runCatching {
        val md = MessageDigest.getInstance(algo)
        md.update(text.toByteArray(Charsets.UTF_8))
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrElse { "失败: ${it.message}" }

    fun password(length: Int, upper: Boolean, digits: Boolean, symbols: Boolean): String {
        val lower = "abcdefghijkmnopqrstuvwxyz"
        val up = if (upper) "ABCDEFGHJKLMNPQRSTUVWXYZ" else ""
        val dg = if (digits) "23456789" else ""
        val sy = if (symbols) "!@#\$%&*+-=?" else ""
        val pool = (lower + up + dg + sy).ifEmpty { lower }
        val rnd = SecureRandom()
        val n = length.coerceIn(4, 64)
        return buildString { repeat(n) { append(pool[rnd.nextInt(pool.length)]) } }
    }

    fun stats(text: String): String {
        val chars = text.length
        val noSpace = text.count { !it.isWhitespace() }
        val lines = if (text.isEmpty()) 0 else text.lines().size
        val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        val bytes = text.toByteArray(Charsets.UTF_8).size
        return "字符 $chars · 不含空白 $noSpace · 词 $words · 行 $lines · UTF-8 $bytes 字节"
    }

    fun json(text: String): String = runCatching {
        val t = text.trim()
        when {
            t.startsWith("{") -> JSONObject(t).toString(2)
            t.startsWith("[") -> JSONArray(t).toString(2)
            else -> "不是合法 JSON（需以 { 或 [ 开头）"
        }
    }.getOrElse { "JSON 解析失败: ${it.message}" }
}
