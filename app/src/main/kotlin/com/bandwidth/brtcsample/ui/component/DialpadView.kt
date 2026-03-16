package com.bandwidth.brtcsample.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class DialpadKey(val digit: String, val letters: String)

private val keys = listOf(
    listOf(DialpadKey("1", ""), DialpadKey("2", "ABC"), DialpadKey("3", "DEF")),
    listOf(DialpadKey("4", "GHI"), DialpadKey("5", "JKL"), DialpadKey("6", "MNO")),
    listOf(DialpadKey("7", "PQRS"), DialpadKey("8", "TUV"), DialpadKey("9", "WXYZ")),
    listOf(DialpadKey("*", ""), DialpadKey("0", "+"), DialpadKey("#", "")),
)

@Composable
fun DialpadView(
    onDigit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in keys) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for (key in row) {
                    DialpadButton(key = key, onClick = { onDigit(key.digit) })
                }
            }
        }
    }
}

@Composable
private fun DialpadButton(key: DialpadKey, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(key.digit, fontSize = 32.sp, fontWeight = FontWeight.Light)
            Text(
                text = key.letters.ifEmpty { "\u00A0" },
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }
    }
}
