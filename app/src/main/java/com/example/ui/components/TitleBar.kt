package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TerminalTheme

@Composable
fun TitleBar(
    title: String,
    theme: TerminalTheme,
    isStoragePermissionNeeded: Boolean,
    onToggleExplorer: () -> Unit,
    onToggleSettings: () -> Unit,
    onClearScreen: () -> Unit,
    onRequestStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(theme.titleBarBg)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Terminal icon and Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2B2B2B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "CMD Icon",
                    tint = theme.accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = title,
                color = theme.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Storage access status
            if (isStoragePermissionNeeded) {
                IconButton(
                    onClick = onRequestStorage,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("storage_perm_button")
                ) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = Color(0xFFFF5252),
                                modifier = Modifier.size(8.dp)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = "Grant Storage Permission",
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // File explorer toggle
            IconButton(
                onClick = onToggleExplorer,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("file_explorer_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "File Explorer",
                    tint = theme.accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Clear screen
            IconButton(
                onClick = onClearScreen,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("clear_screen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Clear Console",
                    tint = theme.foreground.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Settings / Themes
            IconButton(
                onClick = onToggleSettings,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "Themes and Settings",
                    tint = theme.foreground.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Windows-like decorative exit button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onClearScreen() }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close session",
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
