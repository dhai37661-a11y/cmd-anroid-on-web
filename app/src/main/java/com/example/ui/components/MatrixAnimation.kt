package com.example.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun MatrixAnimation(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(40)
            frame++
        }
    }

    val columns = 35
    val dropPositions = remember { IntArray(columns) { Random.nextInt(-30, 0) } }
    val chars = "0123456789ABCDEFｦｱｳｴｵｶｷｹｺｻｼｽｾｿﾀﾂﾃﾅﾆﾇﾈﾊﾋﾎﾏﾐﾑﾒﾓﾔﾕﾗﾘﾜ"

    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#00FF66")
            textSize = 34f
            isAntiAlias = true
            isFakeBoldText = true
        }
    }
    val headPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#FFFFFF")
            textSize = 36f
            isAntiAlias = true
            isFakeBoldText = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvas = drawContext.canvas.nativeCanvas
            val colWidth = size.width / columns
            val rowHeight = 36f
            val maxRows = (size.height / rowHeight).toInt()

            for (i in 0 until columns) {
                val currentY = dropPositions[i]
                val x = i * colWidth

                // Draw trail
                for (j in 0..12) {
                    val row = currentY - j
                    if (row in 0..maxRows) {
                        val alpha = ((12 - j) / 12f * 255).toInt().coerceIn(20, 255)
                        textPaint.alpha = alpha
                        val char = chars[Random.nextInt(chars.length)]
                        val paint = if (j == 0) headPaint else textPaint
                        canvas.drawText(char.toString(), x, row * rowHeight, paint)
                    }
                }

                // Advance drop
                dropPositions[i] = currentY + 1
                if (dropPositions[i] - 12 > maxRows && Random.nextFloat() > 0.95f) {
                    dropPositions[i] = 0
                }
            }
        }

        Text(
            text = "[ THE MATRIX - TAP ANYWHERE TO EXIT ]",
            color = Color(0xFF00FF66),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}
