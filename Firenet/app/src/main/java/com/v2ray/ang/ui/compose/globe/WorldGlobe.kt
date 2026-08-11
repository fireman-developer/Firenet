package com.v2ray.ang.ui.compose.globe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.v2ray.ang.ui.compose.components.rememberIsResumed
import com.v2ray.ang.ui.compose.theme.ConnectionTone
import com.v2ray.ang.ui.compose.theme.FirenetColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * کره‌ی زمین تعاملی صفحه‌ی اصلی.
 *
 * کره با تصویربرداری متعامد (orthographic) رسم می‌شود: هر نقطه‌ی خشکی از مختصات
 * جغرافیایی به مختصات صفحه تبدیل می‌شود و فقط نیم‌کره‌ی روبه‌بیننده کشیده می‌شود.
 * عمق هر نقطه (کسینوس زاویه‌ی مرکزی) هم اندازه و هم روشنایی آن را کنترل می‌کند،
 * بنابراین بدون هیچ موتور سه‌بعدی، حجم کره حس می‌شود.
 *
 * رفتار چرخش بر اساس وضعیت تونل تغییر می‌کند:
 *  - قطع: چرخش بسیار آرام، مثل یک کره‌ی رومیزی رها شده.
 *  - در حال اتصال: چرخش سریع که انتظار را قابل تحمل می‌کند.
 *  - متصل: کره می‌ایستد و کشور سرور را رو به بیننده نگه می‌دارد، با نوسانی
 *    بسیار کوچک تا تصویر خشک به نظر نرسد.
 *
 * @param tone وضعیت جاری تونل.
 * @param target مختصات سروری که نشانگر باید روی آن بنشیند.
 * @param showMarker اگر نادرست باشد، نشانگر رسم نمی‌شود (مثلاً وقتی سروری انتخاب نشده).
 */
@Composable
fun WorldGlobe(
    tone: ConnectionTone,
    target: CountryCoordinates.LatLon,
    modifier: Modifier = Modifier,
    showMarker: Boolean = true
) {
    val yaw = remember { Animatable(12f) }
    val pitch = remember { Animatable(14f) }

    // کره در هر فریم حدود ۱۵۰۰ نقطه‌ی خشکی به‌علاوه‌ی شبکه‌ی نصف‌النهارها را از
    // نو رسم می‌کند. تا وقتی چرخش ادامه دارد، این کار در هر فریم تکرار می‌شود.
    // پس چرخش را به وضعیت RESUMED گره می‌زنیم: به محض اینکه صفحه پشت صفحه‌ی
    // دیگری برود یا برنامه کوچک شود، انیمیشن می‌ایستد، Compose دیگر فریم
    // درخواست نمی‌کند و مصرف پردازنده به صفر می‌رسد.
    val resumed by rememberIsResumed()

    LaunchedEffect(tone, target.lon, resumed) {
        if (!resumed) return@LaunchedEffect
        when (tone) {
            ConnectionTone.Connecting -> {
                val start = yaw.value
                yaw.animateTo(
                    targetValue = start + 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )
            }

            ConnectionTone.Connected -> {
                // کره را رو به جلو می‌چرخانیم تا طول جغرافیایی سرور دقیقاً وسط بایستد.
                var goal = target.lon.toFloat()
                while (goal < yaw.value + 40f) goal += 360f
                yaw.animateTo(goal, tween(1700, easing = FastOutSlowInEasing))
                yaw.animateTo(
                    targetValue = goal + 7f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(7000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }

            else -> {
                val start = yaw.value
                yaw.animateTo(
                    targetValue = start + 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(75_000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )
            }
        }
    }

    LaunchedEffect(tone, target.lat, resumed) {
        if (!resumed) return@LaunchedEffect
        val goal = when (tone) {
            ConnectionTone.Connected -> (target.lat * 0.55).toFloat().coerceIn(-32f, 32f)
            else -> 14f
        }
        pitch.animateTo(goal, tween(1400, easing = FastOutSlowInEasing))
    }

    // تپش نشانگر فقط وقتی معنا دارد که اتصالی در جریان باشد و کسی نگاه کند.
    val pulse = animatedPulse(
        active = resumed && (tone == ConnectionTone.Connected || tone == ConnectionTone.Connecting)
    )

    val landColor = when (tone) {
        ConnectionTone.Connected -> FirenetColors.GlobeLandActive
        ConnectionTone.Blocked -> FirenetColors.Blocked
        ConnectionTone.Connecting -> FirenetColors.AccentSoft
        ConnectionTone.Idle -> FirenetColors.GlobeLandIdle
    }
    val markerColor = FirenetColors.accentFor(tone)

    Canvas(modifier = modifier.fillMaxSize()) {
        val radius = minOf(size.width, size.height) / 2f * 0.92f
        val center = Offset(size.width / 2f, size.height / 2f)
        val lam0 = (yaw.value * PI / 180.0).toFloat()
        val phi0 = (pitch.value * PI / 180.0).toFloat()
        val sinPhi0 = sin(phi0)
        val cosPhi0 = cos(phi0)

        drawAtmosphere(center, radius, markerColor)
        drawOcean(center, radius)
        drawGraticule(center, radius, lam0, sinPhi0, cosPhi0)
        drawLand(center, radius, lam0, sinPhi0, cosPhi0, landColor)
        drawTerminator(center, radius)
        drawRim(center, radius, markerColor)

        if (showMarker) {
            drawMarker(center, radius, lam0, sinPhi0, cosPhi0, target, markerColor, pulse, tone)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// کنترل انیمیشن‌ها بر اساس چرخه‌ی عمر
// ─────────────────────────────────────────────────────────────────────────────

/** تپش تکرارشونده که وقتی [active] نادرست است اصلاً فریم درخواست نمی‌کند. */
@Composable
private fun animatedPulse(active: Boolean): Float = if (active) {
    val transition = rememberInfiniteTransition(label = "globe-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    pulse
} else {
    0f
}

// ─────────────────────────────────────────────────────────────────────────────
// لایه‌های رسم
// ─────────────────────────────────────────────────────────────────────────────

/** هاله‌ی جوّی بیرون کره. */
private fun DrawScope.drawAtmosphere(center: Offset, radius: Float, accent: Color) {
    val outer = radius * 1.55f
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.62f to Color.Transparent,
                0.72f to accent.copy(alpha = 0.16f),
                0.86f to accent.copy(alpha = 0.06f),
                1f to Color.Transparent
            ),
            center = center,
            radius = outer
        ),
        radius = outer,
        center = center
    )
}

/** کره‌ی پایه: اقیانوس تیره با نور از بالا-چپ. */
private fun DrawScope.drawOcean(center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF1B2A55),
                Color(0xFF12203F),
                FirenetColors.GlobeOcean
            ),
            center = Offset(center.x - radius * 0.32f, center.y - radius * 0.36f),
            radius = radius * 1.7f
        ),
        radius = radius,
        center = center
    )
}

/** شبکه‌ی نصف‌النهارها و مدارها. */
private fun DrawScope.drawGraticule(
    center: Offset,
    radius: Float,
    lam0: Float,
    sinPhi0: Float,
    cosPhi0: Float
) {
    val color = FirenetColors.GlobeGrid
    val stroke = 0.9f

    // نصف‌النهارها هر ۳۰ درجه
    var lonDeg = 0
    while (lonDeg < 360) {
        val lon = (lonDeg * PI / 180.0).toFloat()
        var prev: Offset? = null
        var latDeg = -78
        while (latDeg <= 78) {
            val lat = (latDeg * PI / 180.0).toFloat()
            val p = project(center, radius, lat, lon, lam0, sinPhi0, cosPhi0)
            if (p != null && prev != null) {
                drawLine(color, prev, p, strokeWidth = stroke, cap = StrokeCap.Round)
            }
            prev = p
            latDeg += 6
        }
        lonDeg += 30
    }

    // مدارها: استوا و هر ۳۰ درجه بالا و پایین آن
    for (latDeg in intArrayOf(-60, -30, 0, 30, 60)) {
        val lat = (latDeg * PI / 180.0).toFloat()
        val alpha = if (latDeg == 0) 1.7f else 1f
        var prev: Offset? = null
        var first: Offset? = null
        var lonDeg2 = 0
        while (lonDeg2 <= 360) {
            val lon = (lonDeg2 * PI / 180.0).toFloat()
            val p = project(center, radius, lat, lon, lam0, sinPhi0, cosPhi0)
            if (p != null && prev != null) {
                drawLine(
                    color.copy(alpha = (color.alpha * alpha).coerceAtMost(1f)),
                    prev, p, strokeWidth = stroke, cap = StrokeCap.Round
                )
            }
            if (first == null) first = p
            prev = p
            lonDeg2 += 6
        }
    }
}

/** نقاط خشکی. */
private fun DrawScope.drawLand(
    center: Offset,
    radius: Float,
    lam0: Float,
    sinPhi0: Float,
    cosPhi0: Float,
    landColor: Color
) {
    val pts = WorldMask.landPoints
    val baseDot = (radius * 0.0175f).coerceAtLeast(1.1f)
    var i = 0
    while (i < pts.size) {
        val lat = pts[i]
        val lon = pts[i + 1]
        i += 2

        val cosLat = cos(lat)
        val dLon = lon - lam0
        val cosc = sinPhi0 * sin(lat) + cosPhi0 * cosLat * cos(dLon)
        if (cosc <= 0.04f) continue

        val x = cosLat * sin(dLon)
        val y = cosPhi0 * sin(lat) - sinPhi0 * cosLat * cos(dLon)

        // عمق، هم اندازه و هم روشنایی نقطه را کم می‌کند تا انحنای کره حس شود.
        val depth = cosc
        val alpha = (0.18f + 0.82f * depth * depth).coerceIn(0f, 1f)
        val r = baseDot * (0.55f + 0.45f * depth)

        drawCircle(
            color = landColor.copy(alpha = landColor.alpha * alpha),
            radius = r,
            center = Offset(center.x + radius * x, center.y - radius * y)
        )
    }
}

/** سایه‌ی سمت پایین-راست که حجم کره را تقویت می‌کند. */
private fun DrawScope.drawTerminator(center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                1f to Color(0xFF03060F).copy(alpha = 0.55f)
            ),
            center = Offset(center.x - radius * 0.28f, center.y - radius * 0.30f),
            radius = radius * 1.45f
        ),
        radius = radius,
        center = center
    )
}

/** نور لبه‌ی کره. */
private fun DrawScope.drawRim(center: Offset, radius: Float, accent: Color) {
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f),
                accent.copy(alpha = 0.22f),
                Color.Transparent
            ),
            start = Offset(center.x - radius, center.y - radius),
            end = Offset(center.x + radius, center.y + radius)
        ),
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.018f)
    )
}

/** نشانگر کشور سرور به همراه حلقه‌های تپنده. */
private fun DrawScope.drawMarker(
    center: Offset,
    radius: Float,
    lam0: Float,
    sinPhi0: Float,
    cosPhi0: Float,
    target: CountryCoordinates.LatLon,
    color: Color,
    pulse: Float,
    tone: ConnectionTone
) {
    val lat = (target.lat * PI / 180.0).toFloat()
    val lon = (target.lon * PI / 180.0).toFloat()
    val cosLat = cos(lat)
    val dLon = lon - lam0
    val cosc = sinPhi0 * sin(lat) + cosPhi0 * cosLat * cos(dLon)
    if (cosc <= 0f) return

    val x = cosLat * sin(dLon)
    val y = cosPhi0 * sin(lat) - sinPhi0 * cosLat * cos(dLon)
    val p = Offset(center.x + radius * x, center.y - radius * y)

    // نزدیک لبه، نشانگر محو می‌شود تا ناگهان قطع نشود.
    val edgeFade = (cosc / 0.25f).coerceIn(0f, 1f)
    val dotR = radius * 0.028f

    if (tone == ConnectionTone.Connected || tone == ConnectionTone.Connecting) {
        val ringR = dotR * (1.6f + pulse * 4.2f)
        drawCircle(
            color = color.copy(alpha = (1f - pulse) * 0.45f * edgeFade),
            radius = ringR,
            center = p,
            style = Stroke(width = radius * 0.007f)
        )
        val ring2Phase = (pulse + 0.5f) % 1f
        drawCircle(
            color = color.copy(alpha = (1f - ring2Phase) * 0.30f * edgeFade),
            radius = dotR * (1.6f + ring2Phase * 4.2f),
            center = p,
            style = Stroke(width = radius * 0.006f)
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.55f * edgeFade), Color.Transparent),
            center = p,
            radius = dotR * 3.4f
        ),
        radius = dotR * 3.4f,
        center = p
    )
    drawCircle(color = color.copy(alpha = edgeFade), radius = dotR, center = p)
    drawCircle(
        color = Color.White.copy(alpha = 0.85f * edgeFade),
        radius = dotR * 0.42f,
        center = p
    )
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * تبدیل مختصات جغرافیایی به مختصات صفحه با تصویربرداری متعامد.
 * اگر نقطه پشت کره باشد، `null` برمی‌گرداند.
 */
private fun project(
    center: Offset,
    radius: Float,
    lat: Float,
    lon: Float,
    lam0: Float,
    sinPhi0: Float,
    cosPhi0: Float
): Offset? {
    val cosLat = cos(lat)
    val dLon = lon - lam0
    val cosc = sinPhi0 * sin(lat) + cosPhi0 * cosLat * cos(dLon)
    if (cosc <= 0f) return null
    val x = cosLat * sin(dLon)
    val y = cosPhi0 * sin(lat) - sinPhi0 * cosLat * cos(dLon)
    return Offset(center.x + radius * x, center.y - radius * y)
}

/** کمکی برای اطمینان از اینکه زاویه در بازه‌ی ‎[-180, 180]‎ می‌ماند. */
internal fun normalizeDegrees(value: Float): Float {
    var v = value % 360f
    if (v > 180f) v -= 360f
    if (v < -180f) v += 360f
    return if (abs(v) < 1e-4f) 0f else v
}
