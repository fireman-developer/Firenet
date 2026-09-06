package com.v2ray.ang.ui.compose.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.components.GlassPanel
import com.v2ray.ang.ui.compose.theme.FirenetColors

/**
 * فهرست انتخاب سرور که از پایین صفحه بالا می‌آید.
 *
 * ردیف‌ها بر اساس تأخیر اندازه‌گیری‌شده مرتب می‌شوند: سرورهای آزمایش‌شده و سریع
 * بالا، سرورهای آزمایش‌نشده پایین. سروری که در دسترس نبوده هم پنهان نمی‌شود چون
 * ممکن است آزمایش در لحظه‌ی بدی انجام شده باشد.
 */
@Composable
fun ServerPickerSheet(
    servers: List<ServerRow>,
    onSelect: (String) -> Unit,
    onTestAll: () -> Unit,
    onRefreshConfigs: () -> Unit,
    modifier: Modifier = Modifier,
    lastUpdated: String? = null,
    autoSelected: Boolean = false,
    autoSearching: Boolean = false,
    onSelectAuto: () -> Unit = {}
) {
    val ordered = remember(servers) {
        servers.sortedWith(
            compareBy(
                { if (it.delayMillis > 0) 0 else 1 },
                { if (it.delayMillis > 0) it.delayMillis else Long.MAX_VALUE },
                { it.name }
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.server_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = FirenetColors.TextPrimary
                )
                Text(
                    text = if (!lastUpdated.isNullOrEmpty()) {
                        "${stringResource(R.string.server_sheet_subtitle, servers.size)} • بروزرسانی: $lastUpdated"
                    } else {
                        stringResource(R.string.server_sheet_subtitle, servers.size)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = FirenetColors.TextTertiary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // دکمه بروزرسانی کانفیگ‌ها
                GlassPanel(
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                    onClick = onRefreshConfigs
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "بروزرسانی",
                            tint = FirenetColors.AccentSoft,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "بروزرسانی",
                            style = MaterialTheme.typography.labelMedium,
                            color = FirenetColors.TextSecondary
                        )
                    }
                }

                // دکمه تست سرعت سرورها
                if (servers.isNotEmpty()) {
                    GlassPanel(
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                        onClick = onTestAll
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = FirenetColors.AccentSoft,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.server_sheet_test_all),
                                style = MaterialTheme.typography.labelMedium,
                                color = FirenetColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        if (ordered.isEmpty()) {
            Text(
                text = stringResource(R.string.server_sheet_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = FirenetColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp)
            )
        } else {
            AutoLocationRow(
                selected = autoSelected,
                searching = autoSearching,
                onClick = onSelectAuto,
                modifier = Modifier.padding(bottom = 9.dp)
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(ordered, key = { it.guid }) { row ->
                    ServerRowItem(
                        row = row.copy(selected = row.selected && !autoSelected),
                        onClick = { onSelect(row.guid) }
                    )
                }
            }
        }
    }
}

/**
 * ردیف «بهترین لوکیشن».
 *
 * بالای فهرست و جدا از بقیه می‌نشیند چون یک سرور نیست، یک سیاست است: هر بار که
 * وصل می‌شوید، برنامه سرورها را می‌سنجد و سریع‌ترین را برمی‌دارد.
 */
@Composable
private fun AutoLocationRow(
    selected: Boolean,
    searching: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tint = if (selected) FirenetColors.Accent else Color.Unspecified,
        intensity = if (selected) 1.2f else 0.9f,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(FirenetColors.Accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = FirenetColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.server_auto_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = FirenetColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        if (searching) R.string.server_auto_searching else R.string.server_auto_subtitle
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = FirenetColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = FirenetColors.Accent,
                    strokeWidth = 2.dp
                )
            } else if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = FirenetColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ServerRowItem(row: ServerRow, onClick: () -> Unit) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tint = if (row.selected) FirenetColors.Accent else Color.Unspecified,
        intensity = if (row.selected) 1.15f else 0.85f,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(
                    id = row.flagResId.takeIf { it != 0 } ?: R.drawable.unknown
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = FirenetColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = row.protocol,
                    style = MaterialTheme.typography.labelSmall,
                    color = FirenetColors.TextTertiary
                )
            }
            DelayBadge(row.delayMillis)
            if (row.selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = FirenetColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** برچسب تأخیر با رنگ‌بندی سه‌گانه: خوب، قابل قبول، ضعیف. */
@Composable
private fun DelayBadge(delayMillis: Long) {
    val (text, color) = when {
        delayMillis <= 0L -> stringResource(R.string.server_delay_untested) to FirenetColors.TextDisabled
        delayMillis < 200L -> "$delayMillis ms" to FirenetColors.Connected
        delayMillis < 500L -> "$delayMillis ms" to FirenetColors.Warning
        else -> "$delayMillis ms" to FirenetColors.Blocked
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}