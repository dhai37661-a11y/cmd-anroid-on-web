package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.FileExplorerDrawer
import com.example.ui.components.MatrixAnimation
import com.example.ui.components.SettingsModal
import com.example.ui.components.TerminalOutputView
import com.example.ui.components.TextEditorModal
import com.example.ui.components.TitleBar
import com.example.ui.components.VirtualKeyboardBar
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TerminalViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                TerminalApp(
                    onRequestStoragePermission = { requestStoragePermission() }
                )
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }
}

@Composable
fun TerminalApp(
    onRequestStoragePermission: () -> Unit,
    viewModel: TerminalViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    // Launcher for older Android storage permission
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkStoragePermissionState()
    }

    // Handle permission request event from viewModel
    LaunchedEffect(uiState.requestPermissionEvent) {
        if (uiState.requestPermissionEvent) {
            viewModel.onPermissionRequestedHandled()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                onRequestStoragePermission()
            } else {
                legacyStoragePermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }

    // Back handling for modals
    BackHandler(enabled = uiState.isEditorOpen || uiState.isExplorerOpen || uiState.isSettingsOpen || uiState.isMatrixActive) {
        when {
            uiState.isMatrixActive -> viewModel.dismissMatrix()
            uiState.isEditorOpen -> viewModel.closeEditor()
            uiState.isExplorerOpen -> viewModel.toggleExplorer()
            uiState.isSettingsOpen -> viewModel.toggleSettings()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier
            .fillMaxSize()
            .background(uiState.theme.background)
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(uiState.theme.background)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.ime)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Title bar
                TitleBar(
                    title = uiState.windowTitle,
                    theme = uiState.theme,
                    isStoragePermissionNeeded = uiState.isStoragePermissionNeeded,
                    onToggleExplorer = { viewModel.toggleExplorer() },
                    onToggleSettings = { viewModel.toggleSettings() },
                    onClearScreen = { viewModel.clearScreen() },
                    onRequestStorage = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            onRequestStoragePermission()
                        } else {
                            legacyStoragePermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            )
                        }
                    }
                )

                // Terminal Output & Input
                TerminalOutputView(
                    lines = uiState.lines,
                    prompt = uiState.currentPrompt,
                    inputText = uiState.commandInput,
                    theme = uiState.theme,
                    fontSizeSp = uiState.fontSizeSp,
                    focusRequester = focusRequester,
                    onInputChange = { viewModel.onInputChange(it) },
                    onSubmitCommand = { viewModel.submitCommand() },
                    modifier = Modifier.weight(1f)
                )

                // Virtual Keystroke Bar
                VirtualKeyboardBar(
                    theme = uiState.theme,
                    onHistoryUp = { viewModel.navigateHistory(-1) },
                    onHistoryDown = { viewModel.navigateHistory(1) },
                    onTab = { viewModel.onTabAutoComplete() },
                    onInsertSymbol = { viewModel.insertSymbol(it) },
                    onQuickCommand = { cmd -> viewModel.submitCommand(cmd) }
                )
            }

            // File Explorer Drawer (slides in from bottom)
            AnimatedVisibility(
                visible = uiState.isExplorerOpen,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                FileExplorerDrawer(
                    currentPath = uiState.currentDirectoryPath,
                    files = uiState.currentFiles,
                    theme = uiState.theme,
                    onNavigateDir = { dir -> viewModel.navigateToDirectoryFromExplorer(dir) },
                    onOpenFile = { file -> viewModel.openFileFromExplorer(file) },
                    onRunCommand = { cmd -> viewModel.submitCommand(cmd) },
                    onClose = { viewModel.toggleExplorer() }
                )
            }

            // Text Editor Modal (MS-DOS EDIT style)
            if (uiState.isEditorOpen) {
                TextEditorModal(
                    file = uiState.editingFile,
                    content = uiState.editingContent,
                    theme = uiState.theme,
                    onContentChange = { viewModel.onEditorContentChange(it) },
                    onSave = { viewModel.saveEditorContent() },
                    onClose = { viewModel.closeEditor() }
                )
            }

            // Settings Modal
            if (uiState.isSettingsOpen) {
                SettingsModal(
                    currentTheme = uiState.theme,
                    currentFontSize = uiState.fontSizeSp,
                    currentPromptStyle = uiState.promptStyle,
                    isHapticEnabled = uiState.hapticEnabled,
                    isStoragePermissionNeeded = uiState.isStoragePermissionNeeded,
                    onSelectTheme = { viewModel.setTheme(it) },
                    onSelectFontSize = { viewModel.setFontSize(it) },
                    onSelectPromptStyle = { viewModel.setPromptStyle(it) },
                    onToggleHaptic = { viewModel.toggleHaptics() },
                    onRequestStoragePermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            onRequestStoragePermission()
                        } else {
                            legacyStoragePermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            )
                        }
                    },
                    onClose = { viewModel.toggleSettings() }
                )
            }

            // Digital Rain Matrix Animation Easter Egg
            AnimatedVisibility(
                visible = uiState.isMatrixActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MatrixAnimation(
                    onDismiss = { viewModel.dismissMatrix() }
                )
            }
        }
    }
}
