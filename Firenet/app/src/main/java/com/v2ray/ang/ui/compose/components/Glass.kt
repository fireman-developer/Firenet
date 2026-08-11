package com.v2ray.ang.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.compose.theme.FirenetColors

/**
 * پایه‌ی سبک «شیشه‌ی مایع».
 *
 * هر سطح شیشه‌ای از چهار لایه ساخته می‌شود:
 *  ۱. پرکننده‌ی گرادیانی نیمه‌شفاف که از بالا روشن‌تر است،
 *  ۲. بازتاب تخصصی (specular) در لبه‌ی بالایی که حس ضخامت شیشه می‌دهد،
 *  ۳. حاشیه‌ی گرادیانی که از بالا-چپ روشن است و در پایین-راست محو می‌شود،
 *  ۴. هاله‌ی رنگی اختیاری که وضعیت جاری تونل را منعکس می‌کند.
 *
 * @param shape شکل سطح؛ گوشه‌های گردِ بزرگ‌تر حس مایع بودن را تقویت می‌کنند.
 * @param tint رنگ هاله‌ی وضعیت. با [Color.Unspecified] هاله‌ای کشیده نمی‌شود.
 * @param intensity ضریب شدت پرکننده؛ مقادیر بالای ۱ سطح را پررنگ‌تر می‌کند.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.Unspecified,
    intensity: Float = 1f,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .drawWithCache {
        val fill = Brush.verticalGradient(
            0f to FirenetColors.GlassFillStrong.copy(
                alpha = (FirenetColors.GlassFillStrong.alpha * intensity).coerceIn(0f, 1f)
            ),
            0.55f to FirenetColors.GlassFill.copy(
                alpha = (FirenetColors.GlassFill.alpha * intensity).coerceIn(0f, 1f)
            ),
            1f to FirenetColors.GlassFillSunken.copy(
                alpha = (FirenetColors.GlassFillSunken.alpha * intensity).coerceIn(0f, 1f)
            )
        )
        val specular = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.20f * intensity).coerceIn(0f, 1f)),
                Color.Transparent
            ),
            startY = 0f,
            endY = size.height * 0.38f
        )
        val glow = if (tint == Color.Unspecified) null else Brush.radialGradient(
            colors = listOf(
                tint.copy(alpha = (0.20f * intensity).coerceIn(0f, 1f)),
                Color.Transparent
            ),
            center = Offset(size.width * 0.5f, size.height * 1.1f),
            radius = size.maxDimension * 0.85f
        )
        onDrawBehind {
            drawRect(brush = fill, style = Fill)
            drawRect(brush = specular, style = Fill)
            glow?.let { drawRect(brush = it, style = Fill) }
        }
    }
    .border(
        width = borderWidth,
        brush = Brush.linearGradient(
            colors = listOf(
                FirenetColors.GlassHighlight.copy(alpha = (0.42f * intensity).coerceIn(0f, 1f)),
                FirenetColors.GlassStroke.copy(alpha = (0.20f * intensity).coerceIn(0f, 1f)),
                FirenetColors.GlassStrokeSoft.copy(alpha = (0.30f * intensity).coerceIn(0f, 1f))
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        ),
        shape = shape
    )

/** پنل شیشه‌ای آماده با فاصله‌ی داخلی استاندارد. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.Unspecified,
    intensity: Float = 1f,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .liquidGlass(shape = shape, tint = tint, intensity = intensity)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.White.copy(alpha = 0.35f)),
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(contentPadding),
        content = content
    )
}

/** تراشه‌ی کوچک شیشه‌ای برای وضعیت‌های خلاصه (روشن/خاموش، پینگ و مانند آن). */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false,
    accent: Color = FirenetColors.Accent,
    onClick: (() -> Unit)? = null
) {
    val contentColor = if (active) accent else FirenetColors.TextSecondary
    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        tint = if (active) accent else Color.Unspecified,
        intensity = if (active) 1.15f else 0.85f,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        onClick = onClick
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
                }
                Text(text = text, style = MaterialTheme.typography.labelMedium, color = contentColor)
            }
        }
    }
}

/** دکمه‌ی آیکونی گرد شیشه‌ای — برای منوی همبرگری و اکشن‌های نوار بالا. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    tint: Color = FirenetColors.TextPrimary,
    glow: Color = Color.Unspecified
) {
    Box(
        modifier = modifier
            .size(size)
            .liquidGlass(shape = RoundedCornerShape(50), tint = glow)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.4f)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

/** جداکننده‌ی نازک که در دو سرِ خود محو می‌شود. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier, thickness: Dp = 1.dp) {
    Box(
        modifier = modifier
            .height(thickness)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, FirenetColors.GlassStroke, Color.Transparent)
                )
            )
    )
}
