package com.v2ray.ang.ui.compose.home

import androidx.compose.runtime.Immutable
import com.v2ray.ang.ui.compose.globe.CountryCoordinates
import com.v2ray.ang.ui.compose.theme.ConnectionTone

/**
 * تمام چیزی که صفحه‌ی اصلی برای رسم خودش لازم دارد، در یک شیء تغییرناپذیر.
 *
 * صفحه هیچ داده‌ای را مستقیم از حافظه یا سرویس نمی‌خواند؛ این کار باعث می‌شود
 * پیش‌نمایش‌ها و آزمون‌ها بدون بالا آوردن تونل هم کار کنند.
 */
@Immutable
data class HomeUiState(
    val tone: ConnectionTone = ConnectionTone.Idle,
    val statusHeadline: String = "",
    val statusDetail: String = "",
    val downloadSpeed: String = "0 KB/s",
    val uploadSpeed: String = "0 KB/s",
    val ping: String = "—",
    val server: SelectedServer = SelectedServer(),
    val account: AccountSummary = AccountSummary(),
    val killSwitchEnabled: Boolean = false,
    val perAppProxyEnabled: Boolean = false,
    val servers: List<ServerRow> = emptyList(),
    val busy: Boolean = false,
    val forcedUpdateUrl: String? = null,
    val optionalUpdateUrl: String? = null,
    val accountSuspended: Boolean = false,
    val message: UiMessage? = null,

    /** حالت «بهترین لوکیشن (خودکار)» روشن است. */
    val autoLocation: Boolean = false,
    /** همین حالا در حال سنجیدن سرورها هستیم. */
    val autoSearching: Boolean = false,
    val autoDone: Int = 0,
    val autoTotal: Int = 0
)

/** سروری که هم‌اکنون انتخاب شده است. */
@Immutable
data class SelectedServer(
    val guid: String = "",
    val name: String = "",
    val subtitle: String = "",
    val flagResId: Int = 0,
    val countryCode: String? = null,
    val coordinates: CountryCoordinates.LatLon = CountryCoordinates.fallback,
    /** این سرور را حالت خودکار انتخاب کرده، نه خود کاربر. */
    val auto: Boolean = false
) {
    val isSelected: Boolean get() = guid.isNotEmpty()
}

/** یک ردیف در فهرست انتخاب سرور. */
@Immutable
data class ServerRow(
    val guid: String,
    val name: String,
    val protocol: String,
    val flagResId: Int,
    val delayMillis: Long,
    val selected: Boolean
)

/**
 * خلاصه‌ی اشتراک کاربر که در منوی کناری نشان داده می‌شود.
 *
 * @param dataProgress نسبت مصرف به سقف، بین ۰ و ۱. برای اشتراک نامحدود `null` است.
 */
@Immutable
data class AccountSummary(
    val username: String = "",
    val statusLabel: String = "",
    val dataUsed: String = "",
    val dataTotal: String = "",
    val dataRemaining: String = "",
    val dataProgress: Float? = null,
    val unlimitedData: Boolean = false,
    val daysRemaining: String = "",
    val unlimitedDays: Boolean = false,
    val loaded: Boolean = false
)

/** پیام یک‌بارمصرف برای نمایش در نوار پایین صفحه. */
@Immutable
data class UiMessage(val text: String, val isError: Boolean = false, val id: Long = 0L)
