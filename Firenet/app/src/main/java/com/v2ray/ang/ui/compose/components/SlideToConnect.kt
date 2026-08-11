package com.v2ray.ang.ui.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.compose.theme.ConnectionTone
import com.v2ray.ang.ui.compose.theme.FirenetColors
import kotlinx.coroutines.launch

/**
 * کنترل کشویی اتصال.
 *
 * برخلاف یک دکمه‌ی ساده، برای وصل و قطع شدن باید دستگیره را تا انتهای مسیر کشید.
 * دلیلش این است که قطع ناخواسته‌ی تونل در میانه‌ی کار پرهزینه است و یک لمس
 * تصادفی نباید آن را رقم بزند.
 *
 * جهت کشیدن با جهت چیدمان صفحه هماهنگ است: در فارسی دستگیره از راست به چپ و در
 * انگلیسی از چپ به راست حرکت می‌کند.
 *
 * @param tone وضعیت جاری تونل؛ در حالت «در حال اتصال» کنترل قفل می‌شود.
 * @param onConnect وقتی کاربر مسیر را در حالت قطع تا انتها بکشد صدا زده می‌شود.
 * @param onDisconnect وقتی کاربر مسیر را در حالت وصل تا انتها برگرداند.
 */
@Composable
fun SlideToConnect(
    tone: ConnectionTone,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    labelConnect: String,
    labelDisconnect: String,
    labelConnecting: String,
    labelBlocked: String,
    trackWidth: androidx.compose.ui.unit.Dp = 178.dp,
    trackHeight: androidx.compose.ui.unit.Dp = 66.dp
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()

    val inset = 5.dp
    val thumbSize = trackHeight - inset * 2
    val travelPx = with(density) { (trackWidth - thumbSize - inset * 2).toPx() }

    val isOn = tone == ConnectionTone.Connected
    val locked = tone == ConnectionTone.Connecting

    // ‎0‎ یعنی دستگیره در ابتدای مسیر، ‎1‎ یعنی انتهای مسیر.
    val progress = remember { Animatable(if (isOn) 1f else 0f) }

    LaunchedEffect(isOn, locked) {
        if (!progress.isRunning) progress.animateTo(if (isOn) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow))
    }

    val accent = FirenetColors.accentFor(tone)

    // درخشش راهنما فقط وقتی لازم است که کاربر باید کاری بکند و صفحه جلوی چشمش
    // باشد. در حالت متصل، این انیمیشن بی‌پایان هیچ چیزی به کاربر نمی‌گفت و فقط
    // در هر فریم رسم دوباره‌ی نوار را می‌طلبید.
    val resumed by rememberIsResumed()
    val hint = if (resumed && !isOn && !locked) {
        val shimmer = rememberInfiniteTransition(label = "slide-hint")
        val value by shimmer.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
            label = "hint"
        )
        value
    } else {
        0f
    }

    val label = when (tone) {
        ConnectionTone.Connected -> labelDisconnect
        ConnectionTone.Connecting -> labelConnecting
        ConnectionTone.Blocked -> labelBlocked
        ConnectionTone.Idle -> labelConnect
    }

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .liquidGlass(shape = RoundedCornerShape(50), tint = accent, intensity = 1.2f)
            .draggable(
                enabled = !locked,
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    val signed = if (rtl) -delta else delta
                    scope.launch {
                        progress.snapTo((progress.value + signed / travelPx).coerceIn(0f, 1f))
                    }
                },
                onDragStopped = {
                    scope.launch {
                        val p = progress.value
                        if (!isOn && p > 0.62f) {
                            progress.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                            onConnect()
                        } else if (isOn && p < 0.38f) {
                            progress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                            onDisconnect()
                        } else {
                            progress.animateTo(if (isOn) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // مسیر پرشده پشت دستگیره
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(50))
                .graphicsLayer { alpha = 0.55f + 0.45f * progress.value }
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.30f * progress.value), Color.Transparent)
                    )
                )
        )

        // برچسب راهنما — وقتی دستگیره حرکت می‌کند محو می‌شود.
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = FirenetColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .offset(x = thumbSize / 2)
                .graphicsLayer {
                    alpha = (1f - progress.value * 1.6f).coerceIn(0f, 1f) *
                        (0.72f + 0.28f * kotlin.math.sin(hint * 2f * Math.PI.toFloat()).let { (it + 1f) / 2f })
                }
        )

        // دستگیره
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = inset)
                .offset(x = with(density) { (travelPx * progress.value).toDp() })
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbBrush(accent, progress.value)),
            contentAlignment = Alignment.Center
        ) {
            when {
                locked -> CircularProgressIndicator(
                    modifier = Modifier.size(thumbSize * 0.5f),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )

                isOn -> Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = labelDisconnect,
                    tint = Color.White,
                    modifier = Modifier.size(thumbSize * 0.46f)
                )

                tone == ConnectionTone.Blocked -> Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = labelBlocked,
                    tint = Color.White,
                    modifier = Modifier.size(thumbSize * 0.5f)
                )

                else -> Icon(
                    imageVector = if (rtl) Icons.Rounded.KeyboardArrowLeft else Icons.Rounded.KeyboardArrowRight,
                    contentDescription = labelConnect,
                    tint = Color.White,
                    modifier = Modifier.size(thumbSize * 0.56f)
                )
            }
        }
    }
}

private fun thumbBrush(accent: Color, progress: Float) = Brush.linearGradient(
    colors = listOf(
        accent.copy(alpha = 0.95f),
        accent.copy(alpha = 0.72f + 0.28f * progress)
    ),
    start = Offset.Zero,
    end = Offset.Infinite
)
