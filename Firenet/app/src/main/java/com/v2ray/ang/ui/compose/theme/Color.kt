package com.v2ray.ang.ui.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * پالت رنگ Firenet.
 *
 * پایه‌ی طراحی «شیشه‌ی مایع» است: یک پس‌زمینه‌ی عمیق و تیره که لایه‌های شیشه‌ای
 * نیمه‌شفاف روی آن می‌نشینند. رنگ‌ها بر اساس وضعیت اتصال تغییر می‌کنند تا کاربر
 * بدون خواندن هیچ متنی وضعیت تونل را تشخیص بدهد.
 */
object FirenetColors {

    // ── پس‌زمینه ─────────────────────────────────────────────────────────────
    val BackdropTop = Color(0xFF05070F)
    val BackdropMid = Color(0xFF0A0F1F)
    val BackdropBottom = Color(0xFF12172E)

    // ── هاله‌ی وضعیت (پشت کره) ───────────────────────────────────────────────
    val AuraIdle = Color(0xFF2A3358)
    val AuraConnecting = Color(0xFFF97910)
    val AuraConnected = Color(0xFF16C79A)
    val AuraBlocked = Color(0xFFE0334B)

    // ── برند ─────────────────────────────────────────────────────────────────
    val Accent = Color(0xFFF97910)
    val AccentSoft = Color(0xFFFFA352)
    val Connected = Color(0xFF16C79A)
    val ConnectedSoft = Color(0xFF5BE8C4)
    val Blocked = Color(0xFFFF5B6E)
    val Warning = Color(0xFFFFC53D)

    // ── شیشه ─────────────────────────────────────────────────────────────────
    val GlassFill = Color(0x1AFFFFFF)
    val GlassFillStrong = Color(0x2EFFFFFF)
    val GlassFillSunken = Color(0x0FFFFFFF)
    val GlassHighlight = Color(0x66FFFFFF)
    val GlassStroke = Color(0x33FFFFFF)
    val GlassStrokeSoft = Color(0x1FFFFFFF)

    // ── متن ──────────────────────────────────────────────────────────────────
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xCCE6EAFF)
    val TextTertiary = Color(0x8FBFC7E8)
    val TextDisabled = Color(0x5CBFC7E8)

    // ── کره ──────────────────────────────────────────────────────────────────
    val GlobeLandIdle = Color(0xFF5A6799)
    val GlobeLandActive = Color(0xFF6FE3C4)
    val GlobeOcean = Color(0xFF0B1330)
    val GlobeRim = Color(0x4D7FA8FF)
    val GlobeGrid = Color(0x1A9FB6FF)

    /** رنگ شاخصی که با وضعیت جاری تونل مطابقت دارد. */
    fun accentFor(state: ConnectionTone): Color = when (state) {
        ConnectionTone.Idle -> Accent
        ConnectionTone.Connecting -> AccentSoft
        ConnectionTone.Connected -> Connected
        ConnectionTone.Blocked -> Blocked
    }

    fun auraFor(state: ConnectionTone): Color = when (state) {
        ConnectionTone.Idle -> AuraIdle
        ConnectionTone.Connecting -> AuraConnecting
        ConnectionTone.Connected -> AuraConnected
        ConnectionTone.Blocked -> AuraBlocked
    }
}

/** چهار حالت بصری که کل رابط کاربری بر اساس آن رنگ می‌گیرد. */
enum class ConnectionTone { Idle, Connecting, Connected, Blocked }
