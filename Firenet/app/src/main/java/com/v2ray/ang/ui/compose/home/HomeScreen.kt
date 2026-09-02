package com.v2ray.ang.ui.compose.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.components.AuroraBackdrop
import com.v2ray.ang.ui.compose.components.GlassIconButton
import com.v2ray.ang.ui.compose.components.GlassPanel
import com.v2ray.ang.ui.compose.components.LocationSelector
import com.v2ray.ang.ui.compose.components.MetricsBar
import com.v2ray.ang.ui.compose.components.SlideToConnect
import com.v2ray.ang.ui.compose.globe.WorldGlobe
import com.v2ray.ang.ui.compose.theme.ConnectionTone
import com.v2ray.ang.ui.compose.theme.FirenetColors

/**
 * صفحه‌ی اصلی برنامه.
 *
 * چیدمان عمداً کم‌عنصر است: کره در مرکز توجه، سنجه‌ها بالای کنترل‌ها و همه‌ی
 * تنظیمات پشت یک دکمه‌ی منو. کره وقتی تونل قطع است کوچک می‌شود و به لبه‌ی چپ
 * می‌رود تا فضای صفحه به کنترل اتصال برسد؛ با وصل شدن، به مرکز برمی‌گردد و
 * بزرگ می‌شود چون آن لحظه، محل سرور مهم‌ترین اطلاعات روی صفحه است.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onMenuClick: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onServerClick: () -> Unit,
    onPingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = state.tone
    val active = tone == ConnectionTone.Connected || tone == ConnectionTone.Connecting

    // جای‌گیری کره؛ یک فنر نرم تا جابه‌جایی مکانیکی به نظر نرسد.
    val spec = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow)
    val globeScale by animateFloatAsState(if (active) 1f else 0.60f, spec, label = "globeScale")
    val globeShift by animateFloatAsState(if (active) 0f else -0.36f, spec, label = "globeShift")
    val globeLift by animateFloatAsState(if (active) 0f else -0.06f, spec, label = "globeLift")

    AuroraBackdrop(tone = tone, modifier = modifier.fillMaxSize()) {

        // ── لایه‌ی کره ──────────────────────────────────────────────────────
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val globeSide = minOf(maxWidth, maxHeight * 0.52f)
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val heightPx = with(LocalDensity.current) { maxHeight.toPx() }

            WorldGlobe(
                tone = tone,
                target = state.server.coordinates,
                showMarker = state.server.isSelected,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(globeSide)
                    .graphicsLayer {
                        scaleX = globeScale
                        scaleY = globeScale
                        translationX = widthPx * globeShift
                        translationY = heightPx * globeLift
                    }
            )
        }

        // ── لایه‌ی محتوا ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            TopBar(
                tone = tone,
                onMenuClick = onMenuClick,
                accountLabel = state.account.username.takeIf { state.account.loaded }
            )

            Spacer(Modifier.height(18.dp))

            StatusHeadline(state = state)

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = tone == ConnectionTone.Blocked,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                KillSwitchBanner(modifier = Modifier.padding(bottom = 12.dp))
            }

            MetricsBar(
                downloadSpeed = state.downloadSpeed,
                uploadSpeed = state.uploadSpeed,
                ping = state.ping,
                tone = tone,
                downloadLabel = stringResource(R.string.home_metric_download),
                uploadLabel = stringResource(R.string.home_metric_upload),
                pingLabel = stringResource(R.string.home_metric_ping),
                onPingClick = onPingClick,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // در چیدمان راست‌به‌چپ، اولین عنصر سمت راست می‌نشیند؛ یعنی کنترل
            // اتصال سمت راست و کادر انتخاب سرور در سمت چپِ آن قرار می‌گیرد.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SlideToConnect(
                    tone = tone,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    labelConnect = stringResource(R.string.home_slide_to_connect),
                    labelDisconnect = stringResource(R.string.home_slide_to_disconnect),
                    labelConnecting = stringResource(R.string.home_connecting),
                    labelBlocked = stringResource(R.string.home_blocked_short)
                )
                LocationSelector(
                    flagResId = state.server.flagResId.takeIf { it != 0 } ?: R.drawable.unknown,
                    title = state.server.name,
                    subtitle = state.server.subtitle,
                    tone = tone,
                    onClick = onServerClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun TopBar(
    tone: ConnectionTone,
    onMenuClick: () -> Unit,
    accountLabel: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(
            icon = Icons.Rounded.Menu,
            contentDescription = stringResource(R.string.home_open_menu),
            onClick = onMenuClick,
            glow = if (tone == ConnectionTone.Connected) FirenetColors.Connected else Color.Unspecified
        )

        Spacer(Modifier.weight(1f))

        GlassPanel(
            shape = RoundedCornerShape(50),
            intensity = 0.8f,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = FirenetColors.accentFor(tone),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = accountLabel ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = FirenetColors.TextSecondary
                )
            }
        }
    }
}

/**
 * تیتر وضعیت. عمداً بزرگ نوشته می‌شود چون تنها چیزی است که کاربر معمولاً
 * پس از باز کردن برنامه می‌خواند.
 */
@Composable
private fun StatusHeadline(state: HomeUiState) {
    val tone = state.tone
    val headline = state.statusHeadline.ifBlank {
        stringResource(if (tone == ConnectionTone.Connected) R.string.connected else R.string.not_connected)
    }
    // در حین جست‌وجوی خودکار، پیشرفت جای توضیح وضعیت را می‌گیرد؛ کاربر باید
    // ببیند که کار در جریان است، نه اینکه برنامه گیر کرده باشد.
    val detail = when {
        state.autoSearching && state.autoTotal > 0 -> stringResource(
            R.string.home_auto_searching_progress, state.autoDone, state.autoTotal
        )

        state.autoSearching -> stringResource(R.string.home_auto_searching)

        else -> state.statusDetail.ifBlank {
            stringResource(
                when (tone) {
                    ConnectionTone.Connected -> R.string.home_connected_detail
                    ConnectionTone.Connecting -> R.string.home_connecting_detail
                    ConnectionTone.Blocked -> R.string.home_blocked_detail
                    ConnectionTone.Idle -> R.string.home_idle_detail
                }
            )
        }
    }

    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = headline,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = FirenetColors.accentFor(tone)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = FirenetColors.TextTertiary,
            modifier = Modifier.fillMaxWidth(0.82f)
        )
    }
}

@Composable
private fun KillSwitchBanner(modifier: Modifier = Modifier) {
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tint = FirenetColors.Blocked,
        intensity = 1.2f,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = FirenetColors.Blocked,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.kill_switch_active),
                style = MaterialTheme.typography.labelLarge,
                color = FirenetColors.TextPrimary,
                textAlign = TextAlign.Start
            )
        }
    }
}
