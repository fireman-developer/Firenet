package com.v2ray.ang.ui.compose.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.components.GlassDivider
import com.v2ray.ang.ui.compose.components.GlassPanel
import com.v2ray.ang.ui.compose.components.liquidGlass
import com.v2ray.ang.ui.compose.theme.ConnectionTone
import com.v2ray.ang.ui.compose.theme.FirenetColors

/** کارهایی که از منوی کناری قابل انجام است. */
enum class MenuAction {
    PerAppProxy, Routing, Assets, Settings, Logs, About, CheckUpdate, Logout
}

/**
 * منوی کناری.
 *
 * اولین چیزی که کاربر می‌بیند وضعیت اشتراک است — حجم و روزهای باقی‌مانده —
 * چون بیشترین دفعاتی که این منو باز می‌شود دقیقاً برای دیدن همین دو عدد است.
 * کلیدهای پرکاربرد (Kill Switch و پروکسی هر برنامه) بلافاصله پس از آن می‌آیند
 * و بقیه‌ی تنظیمات پایین‌تر قرار می‌گیرند.
 */
@Composable
fun SideMenu(
    state: HomeUiState,
    onToggleKillSwitch: () -> Unit,
    onAction: (MenuAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    listOf(
                        FirenetColors.BackdropTop.copy(alpha = 0.97f),
                        FirenetColors.BackdropBottom.copy(alpha = 0.97f)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AccountHeader(state)
        DataCard(state)
        DaysCard(state)

        SectionLabel(stringResource(R.string.menu_quick_settings))

        ToggleRow(
            icon = Icons.Rounded.Security,
            title = stringResource(R.string.kill_switch),
            summary = stringResource(R.string.menu_kill_switch_summary),
            checked = state.killSwitchEnabled,
            onToggle = onToggleKillSwitch
        )

        MenuRow(
            icon = Icons.Rounded.Apps,
            title = stringResource(R.string.quick_per_app),
            summary = stringResource(R.string.menu_per_app_summary),
            trailing = stringResource(
                if (state.perAppProxyEnabled) R.string.status_on else R.string.status_off
            ),
            trailingHighlighted = state.perAppProxyEnabled,
            onClick = { onAction(MenuAction.PerAppProxy) }
        )

        SectionLabel(stringResource(R.string.menu_more))

        MenuRow(Icons.Rounded.Route, stringResource(R.string.routing_settings_title)) {
            onAction(MenuAction.Routing)
        }
        MenuRow(Icons.Rounded.CloudDownload, stringResource(R.string.title_user_asset_setting)) {
            onAction(MenuAction.Assets)
        }
        MenuRow(Icons.Rounded.Settings, stringResource(R.string.title_settings)) {
            onAction(MenuAction.Settings)
        }
        MenuRow(Icons.Rounded.Terminal, stringResource(R.string.title_logcat)) {
            onAction(MenuAction.Logs)
        }
        MenuRow(Icons.Rounded.SystemUpdate, stringResource(R.string.title_check_update)) {
            onAction(MenuAction.CheckUpdate)
        }
        MenuRow(Icons.Rounded.Info, stringResource(R.string.title_about)) {
            onAction(MenuAction.About)
        }

        Spacer(Modifier.height(4.dp))
        GlassDivider(Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))

        MenuRow(
            icon = Icons.AutoMirrored.Rounded.Logout,
            title = stringResource(R.string.menu_logout),
            accent = FirenetColors.Blocked,
            onClick = { onAction(MenuAction.Logout) }
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AccountHeader(state: HomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .liquidGlass(shape = CircleShape, tint = FirenetColors.accentFor(state.tone)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.account.username.take(1).uppercase().ifBlank { "•" },
                style = MaterialTheme.typography.titleLarge,
                color = FirenetColors.TextPrimary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.account.username.ifBlank { stringResource(R.string.menu_account) },
                style = MaterialTheme.typography.titleMedium,
                color = FirenetColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.account.statusLabel.isNotBlank()) {
                Text(
                    text = state.account.statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = FirenetColors.TextTertiary
                )
            }
        }
    }
}

/** کارت حجم: عدد باقی‌مانده درشت، نوار مصرف زیر آن. */
@Composable
private fun DataCard(state: HomeUiState) {
    val account = state.account
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        tint = FirenetColors.Accent,
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.DataUsage,
                    contentDescription = null,
                    tint = FirenetColors.AccentSoft,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.nav_data_remaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = FirenetColors.TextTertiary
                )
            }

            Text(
                text = if (account.unlimitedData) {
                    stringResource(R.string.nav_unlimited)
                } else {
                    account.dataRemaining.ifBlank { "—" }
                },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = FirenetColors.TextPrimary
            )

            if (!account.unlimitedData) {
                UsageBar(progress = account.dataProgress ?: 0f)
                Text(
                    text = stringResource(
                        R.string.menu_data_used_of_total,
                        account.dataUsed.ifBlank { "—" },
                        account.dataTotal.ifBlank { "—" }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = FirenetColors.TextTertiary
                )
            }
        }
    }
}

/**
 * نوار مصرف. وقتی مصرف از ۸۵ درصد بگذرد رنگ به هشدار تغییر می‌کند تا کاربر
 * پیش از تمام شدن حجم متوجه شود.
 */
@Composable
private fun UsageBar(progress: Float) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "usage")
    val color = when {
        animated >= 0.95f -> FirenetColors.Blocked
        animated >= 0.85f -> FirenetColors.Warning
        else -> FirenetColors.Accent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(FirenetColors.GlassFillSunken)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color))
                )
        )
    }
}

@Composable
private fun DaysCard(state: HomeUiState) {
    val account = state.account
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = FirenetColors.ConnectedSoft,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nav_days_remaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = FirenetColors.TextTertiary
                )
                Text(
                    text = if (account.unlimitedDays || account.daysRemaining.isBlank()) {
                        stringResource(R.string.menu_days_left_unlimited)
                    } else {
                        stringResource(R.string.menu_days_left, account.daysRemaining)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = FirenetColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = FirenetColors.TextTertiary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tint = if (checked) FirenetColors.Accent else Color.Unspecified,
        intensity = if (checked) 1.1f else 0.85f,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onToggle
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (checked) FirenetColors.Accent else FirenetColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = FirenetColors.TextPrimary)
                Text(summary, style = MaterialTheme.typography.labelSmall, color = FirenetColors.TextTertiary)
            }
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FirenetColors.Accent,
                    uncheckedThumbColor = FirenetColors.TextTertiary,
                    uncheckedTrackColor = FirenetColors.GlassFillSunken,
                    uncheckedBorderColor = FirenetColors.GlassStroke
                )
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    trailing: String? = null,
    trailingHighlighted: Boolean = false,
    accent: Color = FirenetColors.TextSecondary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (accent == FirenetColors.Blocked) accent else FirenetColors.TextPrimary
                )
                if (summary != null) {
                    Text(summary, style = MaterialTheme.typography.labelSmall, color = FirenetColors.TextTertiary)
                }
            }
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (trailingHighlighted) FirenetColors.Accent else FirenetColors.TextTertiary
                )
            }
        }
    }
}
