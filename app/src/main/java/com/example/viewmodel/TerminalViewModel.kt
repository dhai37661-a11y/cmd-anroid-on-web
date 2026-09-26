package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CmdDatabase
import com.example.data.repository.CmdRepository
import com.example.engine.CommandInterpreter
import com.example.model.FileEntry
import com.example.model.LineType
import com.example.model.PromptStyle
import com.example.model.TerminalLine
import com.example.model.TerminalTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class TerminalUiState(
    val lines: List<TerminalLine> = emptyList(),
    val currentPrompt: String = "C:\\Users\\Android\\Downloads>",
    val currentDirectoryPath: String = "",
    val commandInput: String = "",
    val theme: TerminalTheme = TerminalTheme.CMD_CLASSIC,
    val fontSizeSp: Int = 13,
    val windowTitle: String = "Command Prompt",
    val isMatrixActive: Boolean = false,
    val isEditorOpen: Boolean = false,
    val editingFile: File? = null,
    val editingContent: String = "",
    val isExplorerOpen: Boolean = false,
    val currentFiles: List<FileEntry> = emptyList(),
    val isSettingsOpen: Boolean = false,
    val promptStyle: PromptStyle = PromptStyle.WINDOWS,
    val isStoragePermissionNeeded: Boolean = false,
    val requestPermissionEvent: Boolean = false,
    val hapticEnabled: Boolean = true
)

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CmdRepository
    private val interpreter: CommandInterpreter
    private val vibrator = application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private val localHistory = mutableListOf<String>()
    private var historyIndex = -1

    init {
        val database = CmdDatabase.getInstance(application)
        repository = CmdRepository(database.cmdDao())

        interpreter = CommandInterpreter(
            context = application,
            onThemeChange = { newTheme -> setTheme(newTheme) },
            onTitleChange = { newTitle -> _uiState.update { it.copy(windowTitle = newTitle) } },
            onOpenEditor = { file -> openEditor(file) },
            onTriggerMatrix = { _uiState.update { it.copy(isMatrixActive = true) } },
            onRequestStoragePermission = {
                _uiState.update { it.copy(requestPermissionEvent = true) }
            }
        )

        // Initial welcome lines
        val initialLines = listOf(
            TerminalLine(text = "Microsoft Windows [Version 11.0.22631.3296]", type = LineType.ASCII_HEADER),
            TerminalLine(text = "(c) Microsoft Corporation. All rights reserved.", type = LineType.OUTPUT),
            TerminalLine(text = "", type = LineType.OUTPUT),
            TerminalLine(text = "Android Phone CMD & Storage Manager v2.5", type = LineType.SUCCESS),
            TerminalLine(text = "Working directory mapped to storage/emulated/0 (C:\\)", type = LineType.INFO),
            TerminalLine(text = "Type 'help' for available commands or 'dir' to list files.", type = LineType.INFO),
            TerminalLine(text = "", type = LineType.OUTPUT)
        )

        checkStoragePermissionState()

        _uiState.update {
            it.copy(
                lines = initialLines,
                currentPrompt = interpreter.getPromptString(),
                currentDirectoryPath = interpreter.currentDir.absolutePath,
                currentFiles = interpreter.listCurrentDirectoryFiles()
            )
        }

        // Load recent history from Room
        viewModelScope.launch {
            repository.recentHistory.collect { entities ->
                localHistory.clear()
                localHistory.addAll(entities.map { it.command }.distinct().reversed())
            }
        }
    }

    fun checkStoragePermissionState() {
        val needs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            !Environment.isExternalStorageManager()
        } else {
            false
        }
        _uiState.update { it.copy(isStoragePermissionNeeded = needs) }
    }

    fun onPermissionRequestedHandled() {
        _uiState.update { it.copy(requestPermissionEvent = false) }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(commandInput = text) }
    }

    fun submitCommand(customCmd: String? = null) {
        val cmd = (customCmd ?: _uiState.value.commandInput).trim()
        if (cmd.isEmpty()) return

        vibrate()

        val promptSnapshot = _uiState.value.currentPrompt

        // Add user command to lines
        val userCommandLine = TerminalLine(text = "$promptSnapshot $cmd", type = LineType.COMMAND)
        _uiState.update {
            it.copy(
                lines = it.lines + userCommandLine,
                commandInput = ""
            )
        }

        // Record in history & DB
        localHistory.add(cmd)
        historyIndex = localHistory.size
        viewModelScope.launch {
            repository.recordCommand(cmd)
        }

        viewModelScope.launch {
            val results = interpreter.execute(cmd)

            // Check if clear screen
            if (results.any { it.text == "__CLEAR_SCREEN__" }) {
                _uiState.update {
                    it.copy(
                        lines = emptyList(),
                        currentPrompt = interpreter.getPromptString(),
                        currentDirectoryPath = interpreter.currentDir.absolutePath,
                        currentFiles = interpreter.listCurrentDirectoryFiles()
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        lines = it.lines + results,
                        currentPrompt = interpreter.getPromptString(),
                        currentDirectoryPath = interpreter.currentDir.absolutePath,
                        currentFiles = interpreter.listCurrentDirectoryFiles()
                    )
                }
            }
        }
    }

    fun navigateHistory(direction: Int) {
        vibrate()
        if (localHistory.isEmpty()) return

        if (direction < 0) { // Up (older)
            if (historyIndex > 0) {
                historyIndex--
                _uiState.update { it.copy(commandInput = localHistory[historyIndex]) }
            } else if (historyIndex == -1 || historyIndex == localHistory.size) {
                historyIndex = localHistory.size - 1
                _uiState.update { it.copy(commandInput = localHistory[historyIndex]) }
            }
        } else { // Down (newer)
            if (historyIndex < localHistory.size - 1) {
                historyIndex++
                _uiState.update { it.copy(commandInput = localHistory[historyIndex]) }
            } else {
                historyIndex = localHistory.size
                _uiState.update { it.copy(commandInput = "") }
            }
        }
    }

    fun onTabAutoComplete() {
        vibrate()
        val currentInput = _uiState.value.commandInput
        val completions = interpreter.getTabCompletions(currentInput)
        if (completions.isNotEmpty()) {
            val prefix = if (currentInput.contains(" ")) {
                currentInput.substringBeforeLast(" ") + " "
            } else {
                ""
            }
            _uiState.update { it.copy(commandInput = prefix + completions[0]) }
        }
    }

    fun insertSymbol(symbol: String) {
        vibrate()
        _uiState.update { it.copy(commandInput = it.commandInput + symbol) }
    }

    fun clearScreen() {
        vibrate()
        _uiState.update { it.copy(lines = emptyList()) }
    }

    fun setTheme(theme: TerminalTheme) {
        _uiState.update { it.copy(theme = theme) }
    }

    fun setFontSize(size: Int) {
        _uiState.update { it.copy(fontSizeSp = size.coerceIn(10, 20)) }
    }

    fun setPromptStyle(style: PromptStyle) {
        interpreter.promptStyle = style
        _uiState.update {
            it.copy(
                promptStyle = style,
                currentPrompt = interpreter.getPromptString()
            )
        }
    }

    fun openEditor(file: File) {
        val content = try {
            if (file.exists()) file.readText() else ""
        } catch (e: Exception) {
            "// Error reading file: ${e.message}"
        }
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editingFile = file,
                editingContent = content
            )
        }
    }

    fun onEditorContentChange(newContent: String) {
        _uiState.update { it.copy(editingContent = newContent) }
    }

    fun saveEditorContent() {
        val file = _uiState.value.editingFile ?: return
        try {
            file.writeText(_uiState.value.editingContent)
            _uiState.update {
                it.copy(
                    isEditorOpen = false,
                    lines = it.lines + TerminalLine(text = "Saved '${file.name}' successfully.", type = LineType.SUCCESS),
                    currentFiles = interpreter.listCurrentDirectoryFiles()
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    lines = it.lines + TerminalLine(text = "Failed to save file: ${e.message}", type = LineType.ERROR)
                )
            }
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(isEditorOpen = false, editingFile = null, editingContent = "") }
    }

    fun toggleExplorer() {
        vibrate()
        _uiState.update {
            val next = !it.isExplorerOpen
            it.copy(
                isExplorerOpen = next,
                currentFiles = interpreter.listCurrentDirectoryFiles()
            )
        }
    }

    fun navigateToDirectoryFromExplorer(dir: File) {
        vibrate()
        submitCommand("cd \"${dir.name}\"")
    }

    fun openFileFromExplorer(file: File) {
        vibrate()
        if (file.isDirectory) {
            navigateToDirectoryFromExplorer(file)
        } else {
            // If text / code, open in editor; else start
            val ext = file.extension.lowercase()
            if (ext in listOf("txt", "bat", "cmd", "sh", "json", "xml", "log", "md", "html", "css", "js", "py", "kt")) {
                submitCommand("edit \"${file.name}\"")
            } else {
                submitCommand("start \"${file.name}\"")
            }
        }
    }

    fun toggleSettings() {
        vibrate()
        _uiState.update { it.copy(isSettingsOpen = !it.isSettingsOpen) }
    }

    fun toggleHaptics() {
        _uiState.update { it.copy(hapticEnabled = !it.hapticEnabled) }
    }

    fun dismissMatrix() {
        _uiState.update { it.copy(isMatrixActive = false) }
    }

    private fun vibrate() {
        if (!_uiState.value.hapticEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(15)
            }
        } catch (_: Exception) {}
    }
}
