package com.pimenov.uikit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.pimenov.uikit.theme.Burgundy
import com.pimenov.uikit.theme.DeepNavy
import com.pimenov.uikit.theme.DeepNavyDark
import com.pimenov.uikit.theme.Gold

enum class SceneTag { TAVERN, FOREST, DUNGEON }

fun sceneTagFrom(name: String?): SceneTag = when (name?.lowercase()) {
    "forest" -> SceneTag.FOREST
    "dungeon" -> SceneTag.DUNGEON
    else -> SceneTag.TAVERN
}

@Composable
fun FantasyBackground(
    tag: SceneTag = SceneTag.TAVERN,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val palette = when (tag) {
        SceneTag.TAVERN -> Triple(DeepNavy, Burgundy.copy(alpha = 0.55f), Gold.copy(alpha = 0.25f))
        SceneTag.FOREST -> Triple(Color(0xFF0B1A14), Color(0xFF1F3A2A), Gold.copy(alpha = 0.15f))
        SceneTag.DUNGEON -> Triple(DeepNavyDark, Color(0xFF2A1B22), Color(0xFF6E5A3A).copy(alpha = 0.3f))
    }
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(palette.first, palette.second, palette.first)
                )
            )
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.third, Color.Transparent),
                    center = Offset(w * 0.7f, h * 0.25f),
                    radius = w * 0.6f
                ),
                radius = w * 0.6f,
                center = Offset(w * 0.7f, h * 0.25f)
            )
            val silhouette = Path().apply {
                moveTo(0f, h)
                when (tag) {
                    SceneTag.TAVERN -> {
                        lineTo(0f, h * 0.78f)
                        lineTo(w * 0.18f, h * 0.78f)
                        lineTo(w * 0.22f, h * 0.7f)
                        lineTo(w * 0.4f, h * 0.7f)
                        lineTo(w * 0.42f, h * 0.62f)
                        lineTo(w * 0.6f, h * 0.62f)
                        lineTo(w * 0.62f, h * 0.72f)
                        lineTo(w * 0.85f, h * 0.72f)
                        lineTo(w * 0.88f, h * 0.8f)
                        lineTo(w, h * 0.8f)
                        lineTo(w, h)
                    }
                    SceneTag.FOREST -> {
                        var x = 0f
                        var top = h * 0.55f
                        while (x < w) {
                            lineTo(x, top)
                            lineTo(x + w * 0.05f, h * 0.45f)
                            lineTo(x + w * 0.1f, top)
                            x += w * 0.1f
                            top = if (top > h * 0.5f) h * 0.6f else h * 0.5f
                        }
                        lineTo(w, h)
                    }
                    SceneTag.DUNGEON -> {
                        lineTo(0f, h * 0.6f)
                        lineTo(w * 0.2f, h * 0.6f)
                        lineTo(w * 0.22f, h * 0.5f)
                        lineTo(w * 0.32f, h * 0.5f)
                        lineTo(w * 0.34f, h * 0.6f)
                        lineTo(w * 0.66f, h * 0.6f)
                        lineTo(w * 0.68f, h * 0.5f)
                        lineTo(w * 0.78f, h * 0.5f)
                        lineTo(w * 0.8f, h * 0.6f)
                        lineTo(w, h * 0.6f)
                        lineTo(w, h)
                    }
                }
                close()
            }
            drawPath(silhouette, color = palette.first.copy(alpha = 0.95f))
        }
        content()
    }
}
