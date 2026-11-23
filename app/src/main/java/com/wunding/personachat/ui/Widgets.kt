package com.wunding.personachat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

private fun hueOf(name: String): Float {
    var h = 0
    for (c in name) h = (h * 31 + c.code) and 0x7fffffff
    return (h % 360).toFloat()
}

fun avatarColor(name: String): Color {
    val h = hueOf(name)
    return Color.hsl(h, 0.42f, 0.42f)
}

@Composable
fun InitialAvatar(name: String, size: Dp = 40.dp, corner: Dp = 12.dp, modifier: Modifier = Modifier) {
    val letter = name.trim().takeIf { it.isNotEmpty() }?.let { if (it.first().isLetter()) it.first().uppercaseChar().toString() else it.take(1) } ?: "?"
    val color = avatarColor(name)
    Box(
        modifier = modifier
            .size(size)
            .background(color, RoundedCornerShape(corner))
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color.White.copy(alpha = 0.95f), fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 纯代码主图标：圆角方块 + 闭合环 + 书签缺口 */
@Composable
fun AppIconCanvas(size: Dp = 84.dp, tint: Color = LocalAccent.current) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val r = s * 0.26f
        drawRoundRect(
            color = tint.copy(alpha = 0.12f),
            topLeft = Offset(s * 0.06f, s * 0.06f),
            size = Size(s * 0.88f, s * 0.88f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
        )
        drawCircle(color = tint, radius = s * 0.30f, center = Offset(s / 2, s / 2), style = Stroke(width = s * 0.075f))
        val p = Path().apply {
            moveTo(s * 0.50f, s * 0.27f)
            lineTo(s * 0.50f, s * 0.56f)
            lineTo(s * 0.66f, s * 0.70f)
        }
        drawPath(p, tint, style = Stroke(width = s * 0.07f, cap = StrokeCap.Round))
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Canvas(Modifier.size(6.dp)) { drawCircle(color) }
        Text(text, fontSize = 10.5.sp, color = color)
    }
}

@Composable
fun EmptyStateCanvas(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(96.dp)) {
            val s = size.minDimension
            val stroke = Stroke(width = s * 0.05f, cap = StrokeCap.Round)
            val c = Color.Gray.copy(alpha = 0.55f)
            drawRoundRect(
                color = c.copy(alpha = 0.35f),
                topLeft = Offset(s * 0.10f, s * 0.08f),
                size = Size(s * 0.80f, s * 0.84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.12f),
                style = stroke,
            )
            val p = Path().apply {
                moveTo(s * 0.38f, s * 0.24f)
                lineTo(s * 0.62f, s * 0.24f)
                lineTo(s * 0.62f, s * 0.62f)
                lineTo(s * 0.50f, s * 0.53f)
                lineTo(s * 0.38f, s * 0.62f)
                close()
            }
            drawPath(p, c, style = Stroke(width = s * 0.045f, cap = StrokeCap.Round))
        }
        Text(text, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
    }
}

/** 逐字淡入 + 上移 8dp，间隔 180ms */
@Composable
fun CharByCharText(text: String, startDelay: Long = 0, fontSize: androidx.compose.ui.unit.TextUnit = 13.sp, color: Color, letterSpacing: androidx.compose.ui.unit.TextUnit = 2.sp) {
    Row(horizontalArrangement = Arrangement.Center) {
        text.forEachIndexed { i, ch ->
            val alpha = remember { Animatable(0f) }
            val offset = remember { Animatable(8f) }
            LaunchedEffect(Unit) {
                delay(startDelay + i * 180L)
                alpha.animateTo(1f, tween(250))
                offset.animateTo(0f, tween(250))
            }
            Text(
                ch.toString(),
                fontSize = fontSize,
                color = color.copy(alpha = alpha.value),
                letterSpacing = letterSpacing,
                modifier = Modifier.padding(top = offset.value.dp),
            )
        }
    }
}

@Composable
fun ErrorInlineCard(message: String, onRetry: (() -> Unit)? = null, onSettings: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text("出错了", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        Text(message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        if (onRetry != null || onSettings != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (onRetry != null) androidx.compose.material3.TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) { Text("重试", fontSize = 11.sp) }
                if (onSettings != null) androidx.compose.material3.TextButton(onClick = onSettings, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) { Text("打开设置", fontSize = 11.sp) }
            }
        }
    }
}
