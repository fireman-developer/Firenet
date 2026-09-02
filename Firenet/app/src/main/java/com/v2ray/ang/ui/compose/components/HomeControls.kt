package com.v2ray.ang.ui.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.compose.theme.ConnectionTone
import com.v2ray.ang.ui.compose.theme.FirenetColors
import com.v2ray.ang.ui.compose.theme.MetricNumberStyle

/**
 * نوار سنجه‌ها: سرعت دریافت، سرعت ارسال و تأخیر سرور.
 *
 * اعداد با فونت تک‌عرض نوشته می‌شوند تا با تغییر مقدار، ستون‌ها جابه‌جا نشوند.
 * ستون تأخیر با لمس، آزمایش پینگ سرور فعال را دوباره اجرا می‌کند.
 */
@Composable
fun MetricsBar(
    downloadSpeed: String,
    uploadSpeed: String,
    ping: String,
    tone: ConnectionTone,
    downloadLabel: String,
    uploadLabel: String,
    pingLabel: String,
    onPingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        tint = if (tone == ConnectionTone.Connected) FirenetColors.Connected else Color.Unspecified,
        intensity = 0.95f,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Metric(
                icon = Icons.Rounded.ArrowDownward,
                label = downloadLabel,
                value = downloadSpeed,
                accent = FirenetColors.ConnectedSoft,
                modifier = Modifier.weight(1f)
            )
            VerticalHairline()
            Metric(
                icon = Icons.Rounded.ArrowUpward,
                label = uploadLabel,
                value = uploadSpeed,
                accent = FirenetColors.AccentSoft,
                modifier = Modifier.weight(1f)
            )
            VerticalHairline()
            Metric(
                icon = Icons.Rounded.NetworkPing,
                label = pingLabel,
                value = ping,
                accent = FirenetColors.TextSecondary,
                modifier = Modifier.weight(1f),
                onClick = onPingClick
            )
        }
    }
}

@Composable
private fun RowScope.Metric(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Color.White.copy(alpha = 0.3f)),
                        onClick = onClick
                    )
                } else Modifier
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = FirenetColors.TextTertiary,
                maxLines = 1
            )
        }
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            label = "metric"
        ) { shown ->
            Text(
                text = shown,
                style = MetricNumberStyle,
                color = FirenetColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VerticalHairline() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(30.dp)
            .background(FirenetColors.GlassStrokeSoft)
    )
}

/**
 * کادر انتخاب سرور: پرچم کشور، نام کانفیگ و نشانه‌ی باز شدن فهرست.
 */
@Composable
fun LocationSelector(
    flagResId: Int,
    title: String,
    subtitle: String,
    tone: ConnectionTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        tint = if (tone == ConnectionTone.Connected) FirenetColors.Connected else Color.Unspecified,
        intensity = 1.05f,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Image(
                painter = painterResource(id = flagResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = FirenetColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = FirenetColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.UnfoldMore,
                contentDescription = null,
                tint = FirenetColors.TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
