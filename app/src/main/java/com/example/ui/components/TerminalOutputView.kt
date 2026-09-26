package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LineType
import com.example.model.TerminalLine
import com.example.model.TerminalTheme

@Composable
fun TerminalOutputView(
    lines: List<TerminalLine>,
    prompt: String,
    inputText: String,
    theme: TerminalTheme,
    fontSizeSp: Int,
    focusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onSubmitCommand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto scroll to bottom when lines change
    LaunchedEffect(lines.size, inputText) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size)
        }
    }

    // Blinking cursor transition
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    SelectionContainer(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(lines, key = { it.id }) { line ->
                CommandLineItem(line = line, theme = theme, fontSizeSp = fontSizeSp)
            }

            // Active prompt line
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = prompt,
                        color = theme.promptColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = onInputChange,
                            textStyle = TextStyle(
                                color = theme.foreground,
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(theme.cursorColor),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send,
                                autoCorrectEnabled = false
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = { onSubmitCommand() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("terminal_command_input")
                        )

                        // If input is empty, show classic retro blinking cursor block
                        if (inputText.isEmpty()) {
                            Text(
                                text = "█",
                                color = theme.cursorColor.copy(alpha = cursorAlpha),
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CommandLineItem(
    line: TerminalLine,
    theme: TerminalTheme,
    fontSizeSp: Int
) {
    val (textColor, isBold) = when (line.type) {
        LineType.COMMAND -> theme.promptColor to true
        LineType.ERROR -> theme.errorColor to true
        LineType.SUCCESS -> theme.successColor to false
        LineType.INFO -> theme.accent to false
        LineType.ASCII_HEADER -> theme.promptColor to true
        LineType.OUTPUT, LineType.PROMPT -> theme.foreground to false
    }

    Text(
        text = line.text,
        color = textColor,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSizeSp.sp,
        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        lineHeight = (fontSizeSp + 5).sp,
        modifier = Modifier.fillMaxWidth()
    )
}
