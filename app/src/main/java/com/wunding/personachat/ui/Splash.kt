package com.wunding.personachat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun staged(t: Float): Float = when {
    t < 0.16f -> 0.15f * (t / 0.16f)
    t < 0.52f -> 0.15f + 0.30f * ((t - 0.16f) / 0.36f)
    t < 0.64f -> 0.45f
    t < 0.92f -> 0.45f + 0.50f * ((t - 0.64f) / 0.28f)
    else -> 0.95f + 0.05f * ((t - 0.92f) / 0.08f)
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val accent = LocalAccent.current
    val progress = remember { Animatable(0f) }
    var startedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(5000, easing = LinearEasing))
        finished = true
        delay(300)
        onDone()
    }

    val p = staged(progress.value)
    val status = when {
        progress.value < 0.16f -> "初始化…"
        progress.value < 0.64f -> "加载人设引擎…"
        progress.value < 0.92f -> "加载人设引擎…"
        else -> "准备就绪"
    }
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val sweep by shimmer.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "sweep")

    val iconScale = remember { Animatable(1f) }
    val pageAlpha = remember { Animatable(1f) }
    LaunchedEffect(finished) {
        if (finished) {
            iconScale.animateTo(1.05f, tween(250))
            pageAlpha.animateTo(0f, tween(300))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgDark)
            .clickable(enabled = System.currentTimeMillis() - startedAt > 3400 && !finished) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp).alpha(pageAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.padding(bottom = 28.dp)) { AppIconCanvas(size = 88.dp) }
            Text("人设对话", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE8ECEB), modifier = Modifier.padding(bottom = 10.dp))
            Text("把设定写进对话里", fontSize = 12.sp, color = Color(0xFF6C7875))

            Spacer(Modifier.height(120.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(16.dp).height(1.dp)) { drawLine(Color(0x99FFFFFF).copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f) }
                CharByCharText("由无定工作室开发", color = Color(0x99FFFFFF).copy(alpha = 0.6f))
                Canvas(Modifier.width(16.dp).height(1.dp)) { drawLine(Color(0x99FFFFFF).copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f) }
            }

            Spacer(Modifier.height(46.dp))
            Text(status, fontSize = 11.sp, color = Color(0xFF7A8683), modifier = Modifier.padding(bottom = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(210.dp).height(14.dp)) {
                    val h = 3.dp.toPx()
                    val y = (size.height - h) / 2
                    val w = size.width
                    drawRoundRect(accent.copy(alpha = 0.2f), Offset(0f, y), Size(w, h), CornerRadius(h / 2))
                    val fill = (w * p).coerceAtLeast(h)
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(accent, Color(0xFF9BF5DC))),
                        Offset(0f, y), Size(fill, h), CornerRadius(h / 2),
                    )
                    // 扫光
                    val sx = w * sweep
                    drawPath(Path().apply {
                        moveTo(sx - 18f, y); lineTo(sx + 4f, y); lineTo(sx - 8f, y + h); lineTo(sx - 30f, y + h); close()
                    }, Color.White.copy(alpha = 0.15f))
                    // 节点
                    listOf(0.25f, 0.5f, 0.75f).forEach { n ->
                        val on = p >= n
                        drawCircle(if (on) Color(0xFFD9FFF4) else accent.copy(alpha = 0.25f), radius = if (on) 3f else 2f, center = Offset(w * n, y + h / 2))
                    }
                    // 端点光晕
                    drawCircle(accent.copy(alpha = 0.35f), radius = 8f, center = Offset(fill, y + h / 2))
                    drawCircle(Color.White.copy(alpha = 0.9f), radius = 3f, center = Offset(fill, y + h / 2))
                }
                Spacer(Modifier.width(10.dp))
                AnimatedContent(
                    targetState = (p * 100).roundToInt(),
                    transitionSpec = { (fadeIn(tween(150)) togetherWith fadeOut(tween(150))) },
                    label = "pct",
                ) { v ->
                    Text("$v%", fontSize = 11.sp, color = Color(0xFF9BA6A3), modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}
