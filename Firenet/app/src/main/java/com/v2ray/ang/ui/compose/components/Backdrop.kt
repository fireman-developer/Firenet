package com.v2ray.ang.ui.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.v2ray.ang.ui.compose.theme.ConnectionTone
import com.v2ray.ang.ui.compose.theme.FirenetColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * پس‌زمینه‌ی سراسری برنامه.
 *
 * سه لایه دارد: یک گرادیان عمودی تیره، دو هاله‌ی رنگی که آرام جابه‌جا می‌شوند و
 * رنگشان را از وضعیت تونل می‌گیرند، و یک میدان ستاره‌ی بسیار کم‌رنگ که به تصویر
 * عمق می‌دهد. هاله‌ها با گرادیان شعاعی ساخته شده‌اند تا روی دستگاه‌های قدیمی هم
 * بدون هزینه‌ی محو کردن (blur) نرم دیده شوند.
 */
@Composable
fun AuroraBackdrop(
    tone: ConnectionTone,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val aura by animateColorAsState(
        targetValue = FirenetColors.auraFor(tone),
        animationSpec = tween(durationMillis = 900),
        label = "aura"
    )

    // جابه‌جایی هاله‌ها فقط وقتی اجرا می‌شود که صفحه دیده شود. این انیمیشن ۲۶
    // ثانیه‌ای بی‌آنکه به چشم بیاید، در پس‌زمینه هم فریم می‌گرفت و کل صفحه را
    // در هر فریم دوباره رسم می‌کرد.
    val resumed by rememberIsResumed()

    val drift = if (!resumed) 0f else {
        val transition = rememberInfiniteTransition(label = "backdrop")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 26_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "drift"
        )
        value
    }

    // میدان ستاره یک بار ساخته می‌شود و ثابت می‌ماند تا سوسو نزند.
    val stars = remember {
        val rnd = Random(20260807)
        List(70) { Triple(rnd.nextFloat(), rnd.nextFloat(), 0.05f + rnd.nextFloat() * 0.18f) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to FirenetColors.BackdropTop,
                    0.5f to FirenetColors.BackdropMid,
                    1f to FirenetColors.BackdropBottom
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            stars.forEach { (x, y, a) ->
                drawCircle(
                    color = Color.White.copy(alpha = a * 0.5f),
                    radius = 1.2f,
                    center = Offset(x * size.width, y * size.height)
                )
            }

            val bigRadius = size.maxDimension * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(aura.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(
                        size.width * (0.28f + 0.06f * cos(drift)),
                        size.height * (0.30f + 0.05f * sin(drift))
                    ),
                    radius = bigRadius
                ),
                radius = bigRadius,
                center = Offset(
                    size.width * (0.28f + 0.06f * cos(drift)),
                    size.height * (0.30f + 0.05f * sin(drift))
                )
            )

            val smallRadius = size.maxDimension * 0.48f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(aura.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(
                        size.width * (0.80f - 0.07f * sin(drift * 0.8f)),
                        size.height * (0.74f + 0.05f * cos(drift * 0.8f))
                    ),
                    radius = smallRadius
                ),
                radius = smallRadius,
                center = Offset(
                    size.width * (0.80f - 0.07f * sin(drift * 0.8f)),
                    size.height * (0.74f + 0.05f * cos(drift * 0.8f))
                )
            )
        }
        content()
    }
}
