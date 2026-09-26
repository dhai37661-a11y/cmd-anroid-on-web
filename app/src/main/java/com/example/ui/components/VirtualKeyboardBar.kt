package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TerminalTheme

@Composable
fun VirtualKeyboardBar(
    theme: TerminalTheme,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
    onTab: () -> Unit,
    onInsertSymbol: (String) -> Unit,
    onQuickCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.titleBarBg)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Navigation keys
        KeyButton(text = "↑", theme = theme, testTag = "key_up") { onHistoryUp() }
        KeyButton(text = "↓", theme = theme, testTag = "key_down") { onHistoryDown() }
        KeyButton(text = "TAB", theme = theme, isSpecial = true, testTag = "key_tab") { onTab() }

        // Path and terminal symbols
        KeyButton(text = "\\", theme = theme, testTag = "key_backslash") { onInsertSymbol("\\") }
        KeyButton(text = "/", theme = theme, testTag = "key_slash") { onInsertSymbol("/") }
        KeyButton(text = "..", theme = theme, testTag = "key_dots") { onInsertSymbol("..") }
        KeyButton(text = "\"", theme = theme, testTag = "key_quote") { onInsertSymbol("\"") }
        KeyButton(text = ">", theme = theme, testTag = "key_redirect") { onInsertSymbol(" > ") }
        KeyButton(text = "|", theme = theme, testTag = "key_pipe") { onInsertSymbol(" | ") }

        // Quick CMD shortcuts
        CmdChip(label = "DIR", theme = theme, testTag = "cmd_dir") { onQuickCommand("dir") }
        CmdChip(label = "DEMO BAT", theme = theme, testTag = "cmd_demo_bat") { onQuickCommand("demo bat") }
        CmdChip(label = "DEMO PY", theme = theme, testTag = "cmd_demo_py") { onQuickCommand("demo py") }
        CmdChip(label = "DEMO JS", theme = theme, testTag = "cmd_demo_js") { onQuickCommand("demo js") }
        CmdChip(label = "EDIT", theme = theme, testTag = "cmd_edit") { onQuickCommand("edit script.bat") }
        CmdChip(label = "SH", theme = theme, testTag = "cmd_sh") { onQuickCommand("sh uname -a") }
        CmdChip(label = "CD ..", theme = theme, testTag = "cmd_cd_up") { onQuickCommand("cd ..") }
        CmdChip(label = "TREE", theme = theme, testTag = "cmd_tree") { onQuickCommand("tree") }
        CmdChip(label = "SYSINFO", theme = theme, testTag = "cmd_sysinfo") { onQuickCommand("systeminfo") }
        CmdChip(label = "IPCONFIG", theme = theme, testTag = "cmd_ipconfig") { onQuickCommand("ipconfig") }
        CmdChip(label = "HELP", theme = theme, testTag = "cmd_help") { onQuickCommand("help") }
        CmdChip(label = "CLS", theme = theme, isDanger = true, testTag = "cmd_cls") { onQuickCommand("cls") }
    }
}

@Composable
private fun KeyButton(
    text: String,
    theme: TerminalTheme,
    isSpecial: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    val bg = if (isSpecial) theme.accent.copy(alpha = 0.25f) else Color(0xFF2C2F36)
    val textColor = if (isSpecial) theme.accent else theme.foreground

    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun CmdChip(
    label: String,
    theme: TerminalTheme,
    isDanger: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    val bg = when {
        isDanger -> Color(0xFF5A1E1E)
        else -> theme.accent.copy(alpha = 0.15f)
    }
    val textColor = when {
        isDanger -> Color(0xFFFF8A80)
        else -> theme.accent
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .sizeIn(minHeight = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
