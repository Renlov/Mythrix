package com.pimenov.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pimenov.uikit.theme.Burgundy
import com.pimenov.uikit.theme.DeepNavyDark
import com.pimenov.uikit.theme.Gold
import com.pimenov.uikit.theme.Parchment

enum class BubbleAuthor { PLAYER, DM, SYSTEM }

@Composable
fun ChatBubble(text: String, author: BubbleAuthor, modifier: Modifier = Modifier) {
    val (bg, fg, align) = when (author) {
        BubbleAuthor.PLAYER -> Triple(Burgundy, Parchment, Arrangement.End)
        BubbleAuthor.DM -> Triple(DeepNavyDark, Parchment, Arrangement.Start)
        BubbleAuthor.SYSTEM -> Triple(Gold.copy(alpha = 0.18f), Gold, Arrangement.Center)
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = align
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text = text, color = fg, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
