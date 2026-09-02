package com.v2ray.ang.ui.compose.loading

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.components.liquidGlass
import com.v2ray.ang.ui.compose.theme.FirenetColors

/**
 * صفحه‌ی راه‌اندازی.
 *
 * این صفحه فقط تزئین نیست. کارهای سنگین شروع برنامه — خواندن قواعد مسیریابی از
 * assets، باز کردن حافظه‌ی MMKV، ساخت درخت Compose صفحه‌ی اصلی — در پس‌زمینه‌ی
 * همین صفحه انجام می‌شوند تا هیچ‌کدام روی نخ اصلی و جلوی چشم کاربر اتفاق نیفتد.
 * چیزی که کاربر می‌بیند یک انیمیشن سبک است؛ چیزی که پشت آن اجرا می‌شود، همان
 * کاری است که قبلاً باعث می‌شد صفحه‌ی اول چند لحظه قفل بماند.
 *
 * همین صفحه پس از آزاد شدن رابط کاربری از حافظه (وقتی برنامه ۴۰ ثانیه در
 * پس‌زمینه بماند) دوباره نمایش داده می‌شود.
 *
 * انیمیشنش عمداً ارزان است: یک کمان چرخان و یک تپش، بدون هیچ نقشه یا نقطه‌ای.
 */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")

    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    val breath by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to FirenetColors.BackdropTop,
                    0.5f to FirenetColors.BackdropMid,
                    1f to FirenetColors.BackdropBottom
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(112.dp)) {
                    val stroke = size.minDimension * 0.045f
                    val inset = stroke / 2f
                    drawArc(
                        color = FirenetColors.GlassStrokeSoft,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - stroke, size.height - stroke
                        ),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                FirenetColors.Accent.copy(alpha = 0.15f),
                                FirenetColors.Accent
                            )
                        ),
                        startAngle = sweep,
                        sweepAngle = 96f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - stroke, size.height - stroke
                        ),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }

                Box(
                    modifier = Modifier
                        .size((66 * breath).dp)
                        .liquidGlass(
                            shape = CircleShape,
                            tint = FirenetColors.Accent,
                            intensity = 1.2f
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "F",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = FirenetColors.Accent
                    )
                }
            }

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = FirenetColors.TextSecondary,
                modifier = Modifier.padding(top = 26.dp)
            )
            Text(
                text = stringResource(R.string.home_loading),
                style = MaterialTheme.typography.labelMedium,
                color = FirenetColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
