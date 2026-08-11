package com.v2ray.ang.ui.compose.globe

/**
 * مختصات تقریبی هر کشور برای نشانه‌گذاری روی کره.
 *
 * کلیدها همان کدهایی هستند که [com.v2ray.ang.util.CountryUtils] از نام کانفیگ
 * استخراج می‌کند، بنابراین پرچم و نشانگر روی کره همیشه به یک کشور اشاره می‌کنند.
 * مختصات‌ها مرکز جمعیتی یا پایتخت هر کشور است؛ برای نشانگری که روی کره‌ای به قطر
 * چند سانتی‌متر رسم می‌شود، این دقت بیش از اندازه کافی است.
 */
object CountryCoordinates {

    /** عرض و طول جغرافیایی بر حسب درجه. */
    data class LatLon(val lat: Double, val lon: Double)

    private val table: Map<String, LatLon> = mapOf(
        "ad" to LatLon(42.5, 1.5),
        "ae" to LatLon(24.5, 54.4),
        "af" to LatLon(34.5, 69.2),
        "al" to LatLon(41.3, 19.8),
        "am" to LatLon(40.2, 44.5),
        "ao" to LatLon(-8.8, 13.2),
        "ar" to LatLon(-34.6, -58.4),
        "at" to LatLon(48.2, 16.4),
        "au" to LatLon(-33.9, 151.2),
        "az" to LatLon(40.4, 49.9),
        "ba" to LatLon(43.9, 18.4),
        "bd" to LatLon(23.8, 90.4),
        "be" to LatLon(50.9, 4.4),
        "bg" to LatLon(42.7, 23.3),
        "bh" to LatLon(26.2, 50.6),
        "bn" to LatLon(4.9, 114.9),
        "bo" to LatLon(-16.5, -68.1),
        "br" to LatLon(-23.6, -46.6),
        "by" to LatLon(53.9, 27.6),
        "ca" to LatLon(45.5, -73.6),
        "ch" to LatLon(47.4, 8.5),
        "cl" to LatLon(-33.4, -70.7),
        "cn" to LatLon(31.2, 121.5),
        "co" to LatLon(4.7, -74.1),
        "cr" to LatLon(9.9, -84.1),
        "cy" to LatLon(35.2, 33.4),
        "cz" to LatLon(50.1, 14.4),
        "de" to LatLon(50.1, 8.7),
        "dk" to LatLon(55.7, 12.6),
        "dom" to LatLon(18.5, -69.9),
        "dz" to LatLon(36.8, 3.1),
        "ec" to LatLon(-0.2, -78.5),
        "ee" to LatLon(59.4, 24.8),
        "eg" to LatLon(30.0, 31.2),
        "es" to LatLon(40.4, -3.7),
        "et" to LatLon(9.0, 38.7),
        "fi" to LatLon(60.2, 24.9),
        "fr" to LatLon(48.9, 2.4),
        "gb" to LatLon(51.5, -0.1),
        "gb_eng" to LatLon(51.5, -0.1),
        "gb_nir" to LatLon(54.6, -5.9),
        "gb_sct" to LatLon(55.9, -3.2),
        "gb_wls" to LatLon(51.5, -3.2),
        "ge" to LatLon(41.7, 44.8),
        "gh" to LatLon(5.6, -0.2),
        "gr" to LatLon(38.0, 23.7),
        "hk" to LatLon(22.3, 114.2),
        "hr" to LatLon(45.8, 16.0),
        "hu" to LatLon(47.5, 19.0),
        "id" to LatLon(-6.2, 106.8),
        "ie" to LatLon(53.3, -6.3),
        "il" to LatLon(32.1, 34.8),
        "ind" to LatLon(19.1, 72.9),
        "iq" to LatLon(33.3, 44.4),
        "ir" to LatLon(35.7, 51.4),
        "is" to LatLon(64.1, -21.9),
        "it" to LatLon(45.5, 9.2),
        "jo" to LatLon(31.9, 35.9),
        "jp" to LatLon(35.7, 139.7),
        "ke" to LatLon(-1.3, 36.8),
        "kg" to LatLon(42.9, 74.6),
        "kh" to LatLon(11.6, 104.9),
        "kr" to LatLon(37.6, 127.0),
        "kw" to LatLon(29.4, 48.0),
        "kz" to LatLon(43.2, 76.9),
        "lb" to LatLon(33.9, 35.5),
        "lk" to LatLon(6.9, 79.9),
        "lt" to LatLon(54.7, 25.3),
        "lu" to LatLon(49.6, 6.1),
        "lv" to LatLon(56.9, 24.1),
        "ma" to LatLon(33.6, -7.6),
        "md" to LatLon(47.0, 28.9),
        "me" to LatLon(42.4, 19.3),
        "mk" to LatLon(42.0, 21.4),
        "mn" to LatLon(47.9, 106.9),
        "mo" to LatLon(22.2, 113.5),
        "mt" to LatLon(35.9, 14.5),
        "mx" to LatLon(19.4, -99.1),
        "my" to LatLon(3.1, 101.7),
        "ng" to LatLon(6.5, 3.4),
        "nl" to LatLon(52.4, 4.9),
        "no" to LatLon(59.9, 10.8),
        "np" to LatLon(27.7, 85.3),
        "nz" to LatLon(-36.8, 174.8),
        "om" to LatLon(23.6, 58.4),
        "pa" to LatLon(9.0, -79.5),
        "pe" to LatLon(-12.0, -77.0),
        "ph" to LatLon(14.6, 121.0),
        "pk" to LatLon(24.9, 67.0),
        "pl" to LatLon(52.2, 21.0),
        "pt" to LatLon(38.7, -9.1),
        "py" to LatLon(-25.3, -57.6),
        "qa" to LatLon(25.3, 51.5),
        "ro" to LatLon(44.4, 26.1),
        "rs" to LatLon(44.8, 20.5),
        "ru" to LatLon(55.8, 37.6),
        "sa" to LatLon(24.7, 46.7),
        "se" to LatLon(59.3, 18.1),
        "sg" to LatLon(1.35, 103.8),
        "si" to LatLon(46.1, 14.5),
        "sk" to LatLon(48.1, 17.1),
        "th" to LatLon(13.8, 100.5),
        "tn" to LatLon(36.8, 10.2),
        "tr" to LatLon(41.0, 29.0),
        "tw" to LatLon(25.0, 121.6),
        "tz" to LatLon(-6.8, 39.3),
        "ua" to LatLon(50.5, 30.5),
        "us" to LatLon(40.7, -74.0),
        "uy" to LatLon(-34.9, -56.2),
        "uz" to LatLon(41.3, 69.2),
        "ve" to LatLon(10.5, -66.9),
        "vn" to LatLon(21.0, 105.8),
        "za" to LatLon(-26.2, 28.0)
    )

    /** مرکز پیش‌فرض وقتی کشور سرور قابل تشخیص نیست: اروپای مرکزی. */
    val fallback = LatLon(48.0, 12.0)

    operator fun get(code: String?): LatLon? = code?.let { table[it.lowercase()] }

    fun getOrFallback(code: String?): LatLon = get(code) ?: fallback

    fun contains(code: String?): Boolean = code != null && table.containsKey(code.lowercase())
}
