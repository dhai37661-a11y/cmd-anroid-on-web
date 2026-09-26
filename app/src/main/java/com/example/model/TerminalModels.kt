package com.example.model

import androidx.compose.ui.graphics.Color

enum class LineType {
    COMMAND,
    OUTPUT,
    ERROR,
    SUCCESS,
    INFO,
    ASCII_HEADER,
    PROMPT
}

data class TerminalLine(
    val id: Long = System.nanoTime(),
    val text: String,
    val type: LineType = LineType.OUTPUT,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TerminalTheme(
    val id: String,
    val displayName: String,
    val background: Color,
    val foreground: Color,
    val accent: Color,
    val promptColor: Color,
    val cursorColor: Color,
    val errorColor: Color,
    val successColor: Color,
    val titleBarBg: Color
) {
    CMD_CLASSIC(
        id = "classic",
        displayName = "Command Prompt (Classic)",
        background = Color(0xFF0C0C0C),
        foreground = Color(0xFFCCCCCC),
        accent = Color(0xFF00D2FF),
        promptColor = Color(0xFFFFFFFF),
        cursorColor = Color(0xFFFFFFFF),
        errorColor = Color(0xFFFF5252),
        successColor = Color(0xFF69F0AE),
        titleBarBg = Color(0xFF1F1F1F)
    ),
    MATRIX_GREEN(
        id = "matrix",
        displayName = "Matrix Terminal",
        background = Color(0xFF040A04),
        foreground = Color(0xFF00FF66),
        accent = Color(0xFF76FF03),
        promptColor = Color(0xFF00FF66),
        cursorColor = Color(0xFF00FF66),
        errorColor = Color(0xFFFF3366),
        successColor = Color(0xFF00FF66),
        titleBarBg = Color(0xFF0A180A)
    ),
    CYBER_AMBER(
        id = "amber",
        displayName = "Amber CRT",
        background = Color(0xFF120B02),
        foreground = Color(0xFFFFB000),
        accent = Color(0xFFFFD54F),
        promptColor = Color(0xFFFFC107),
        cursorColor = Color(0xFFFFB000),
        errorColor = Color(0xFFFF7043),
        successColor = Color(0xFFFFD54F),
        titleBarBg = Color(0xFF241604)
    ),
    POWERSHELL_BLUE(
        id = "powershell",
        displayName = "PowerShell Blue",
        background = Color(0xFF012456),
        foreground = Color(0xFFEEF3F7),
        accent = Color(0xFFFFEB3B),
        promptColor = Color(0xFFFFEB3B),
        cursorColor = Color(0xFFFFFFFF),
        errorColor = Color(0xFFFF5252),
        successColor = Color(0xFF69F0AE),
        titleBarBg = Color(0xFF0C1935)
    ),
    MODERN_DARK(
        id = "modern",
        displayName = "Modern Dark",
        background = Color(0xFF121417),
        foreground = Color(0xFFE2E8F0),
        accent = Color(0xFF38BDF8),
        promptColor = Color(0xFF38BDF8),
        cursorColor = Color(0xFF38BDF8),
        errorColor = Color(0xFFF87171),
        successColor = Color(0xFF4ADE80),
        titleBarBg = Color(0xFF1E222A)
    )
}

enum class PromptStyle(val displayName: String) {
    WINDOWS("Windows (C:\\...> )"),
    LINUX("Linux (/storage/...$ )"),
    SHORT("Short (CMD> )")
}

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isReadable: Boolean,
    val isWritable: Boolean,
    val extension: String = ""
)
