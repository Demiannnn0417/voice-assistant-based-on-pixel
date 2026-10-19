package com.demian.voiceassistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.demian.voiceassistant.R

/**
 * 语音助手的主视觉：一个带呼吸光环的圆形「语音球」。
 * 点击即开始聆听；[active] 为 true 时光环会扩散动画。
 */
@Composable
fun VoiceOrb(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    level: Float = 0f,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    Box(
        modifier = modifier
            .size(240.dp)
            .graphicsLayer {
                val s = if (active) 1f + level.coerceIn(0f, 1f) * 0.06f else 1f
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        // 静态光晕
        Box(
            Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(primary.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
        )

        // 呼吸光环
        if (active) {
            OrbRing(fraction = pulse, color = primary)
            OrbRing(fraction = (pulse + 0.5f) % 1f, color = secondary)
        }

        // 主按钮
        Box(
            Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(primary, secondary)))
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_stat_mic),
                contentDescription = "开始聆听",
                tint = Color.White,
                modifier = Modifier.size(60.dp),
            )
        }
    }
}

@Composable
private fun OrbRing(fraction: Float, color: Color) {
    Box(
        Modifier
            .size(150.dp + 100.dp * fraction)
            .border(
                width = 2.dp,
                color = color.copy(alpha = (1f - fraction) * 0.4f),
                shape = CircleShape,
            )
    )
}
