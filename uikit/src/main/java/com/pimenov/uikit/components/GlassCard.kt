package com.pimenov.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.pimenov.uikit.theme.DeepNavy
import com.pimenov.uikit.theme.DeepNavyDark
import com.pimenov.uikit.theme.Gold

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DeepNavy.copy(alpha = 0.85f),
                        DeepNavyDark.copy(alpha = 0.92f)
                    )
                )
            )
            .border(1.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) { content() }
}
