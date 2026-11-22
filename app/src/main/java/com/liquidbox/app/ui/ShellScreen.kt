package com.liquidbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import com.liquidbox.app.glass.LiquidButton
import com.liquidbox.app.glass.LocalGlassPalette
import com.liquidbox.app.glass.liquidGlass
import com.liquidbox.app.shizuku.ShizukuShell
import com.liquidbox.app.shizuku.Toolbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShellScreen() {
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("LiquidBox Shell\n以 Shizuku 身份执行，输入命令后点运行。\n") }

    Column(Modifier.fillMaxSize()) {
        GlassCard {
            T("内置 Shell 终端", size = 17, weight = androidx.compose.ui.text.font.FontWeight.Bold)
            T("命令以 shell/root 身份执行，请谨慎输入破坏性指令。", size = 12, color = palette.textSecondary)
            GlassTextField(command, { command = it }, "输入 shell 命令，如 pm list packages -3")
            Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
                LiquidButton(onClick = {
                    val cmd = command
                    if (cmd.isBlank()) return@LiquidButton
                    scope.launch {
                        output += "\n$ $cmd\n"
                        val r = withContext(Dispatchers.IO) { ShizukuShell.exec(cmd) }
                        output += r.output + "\n"
                    }
                }, modifier = Modifier.weight(1f)) {
                    T("运行", size = 14, align = TextAlign.Center)
                }
                LiquidButton(onClick = { output = "已清空\n" }, modifier = Modifier.weight(1f)) {
                    T("清屏", size = 14, align = TextAlign.Center)
                }
            }
        }

        Spacer(Modifier.height(10f.dp))

        GlassCard(modifier = Modifier.weight(1f)) {
            T("输出", size = 14, weight = androidx.compose.ui.text.font.FontWeight.Bold)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedRectangle(16f.dp))
                    .liquidGlass(
                        shape = { RoundedRectangle(16f.dp) },
                        blurDp = 4f, lensHeightDp = 8f, lensAmountDp = 10f,
                    )
                    .padding(14f.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                T(
                    output.takeLast(12000),
                    modifier = Modifier.fillMaxWidth(),
                    size = 12,
                    color = palette.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(10f.dp))
        SectionTitle("常用命令书签")
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .height(190f.dp),
            verticalArrangement = Arrangement.spacedBy(8f.dp),
        ) {
            items(Toolbox.bookmarks, key = { it.label }) { bm ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .liquidGlass(blurDp = 5f, lensHeightDp = 8f, lensAmountDp = 10f)
                        .padding(horizontal = 14f.dp, vertical = 8f.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        T(bm.label, size = 13, weight = androidx.compose.ui.text.font.FontWeight.Medium)
                        T(bm.command, size = 11, color = palette.textSecondary, maxLines = 1)
                    }
                    Spacer(Modifier.padding(6f.dp))
                    LiquidButton(onClick = { command = bm.command }) {
                        T("填入", size = 12, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}
