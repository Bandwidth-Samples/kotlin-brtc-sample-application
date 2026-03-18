package com.bandwidth.brtcsample.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AudioWaveformView(
    levels: List<Float>,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 50
    val barSpacing = 2f

    Column(modifier = modifier) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = color.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
                .border(0.5.dp, color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
        ) {
            val totalSpacing = barSpacing * (barCount - 1)
            val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(1f)
            val midY = size.height / 2f

            val samples = if (levels.size >= barCount) {
                levels.takeLast(barCount)
            } else {
                List(barCount - levels.size) { 0f } + levels
            }

            for (i in 0 until barCount) {
                val level = samples[i]
                val halfHeight = (level * midY).coerceAtLeast(1f)
                val x = i * (barWidth + barSpacing)
                val ageFraction = i.toFloat() / barCount
                val alpha = 0.25f + 0.75f * ageFraction

                drawRoundRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(x, midY - halfHeight),
                    size = Size(barWidth, halfHeight * 2),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                )
            }
        }
    }
}
