package com.v2ray.ang.ui.compose.globe

import android.util.Base64
import kotlin.math.cos
import kotlin.math.floor

/**
 * ماسک خشکی‌های کره‌ی زمین.
 *
 * به جای نگه‌داشتن مختصات هر نقطه، فقط یک بیت به ازای هر نقطه‌ی نامزد ذخیره شده
 * است: خشکی یا آب. خودِ نقاط در زمان اجرا و با همان الگوریتمی بازتولید می‌شوند که
 * ماسک با آن ساخته شده، بنابراین کل نقشه در ۶۵۸ بایت جا می‌شود.
 *
 * نقاط روی حلقه‌های عرض جغرافیایی با گام ثابت [STEP] چیده شده‌اند و تعداد نقاط هر
 * حلقه با کسینوس عرض جغرافیایی کم می‌شود؛ نتیجه، توزیع تقریباً هم‌مساحت روی سطح
 * کره است، نه تجمع نقاط در قطبین که در شبکه‌ی ساده‌ی طول/عرض رخ می‌دهد.
 *
 * منبع داده: Natural Earth 1:50m (از طریق بسته‌ی world-atlas)، نمونه‌برداری‌شده در
 * گام ۲٫۸ درجه.
 */
object WorldMask {

    private const val STEP = 2.8
    private const val LAT_START = -88.0
    private const val LAT_END = 88.0

    /** ماسک فشرده‌شده؛ هر بیت یک نقطه‌ی نامزد، از پرارزش‌ترین بیت هر بایت. */
    private const val ENCODED =
        "//5//z8//D8P/+APB//8AAgP//4AAQAHv+AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIAAAAAAAA" +
        "AAAAkAAAAAAAAAAAAGAAAAAAAAAAAAABgAAAAAAAABAAAAA4AAAAAAAAACAAAAB4AAAAAAAAAAQAAAAHgAAAAAAA" +
        "AHAAAAAADwAAAAAAAAB8AAAAAAH4AAAcAAAA9+AAAAAAB/AAAHgAAAH/4AAAAAAD+AAAPwAAAH/8AAAAAAA/4AAD" +
        "+IAAAf/gAAAAAAB/4AAH8YAAAf/BAAAAAAB/8AAH/MAAAB/gAAAAAAAf/gAAf+QAAADmAAAAAAAB//AAA/8AAAAB" +
        "kAAAAAAAD//gAAf8AAAAAAiAAAAAAB//4AAP+AAABABwAAAAAAAf/8AAH/wAAAgB4AAAAAAAH/8AAB/+AAAJ0QAA" +
        "AAAAAAf+AAAP/4AACOAAAAAAAAAD/gAAB//gAAQwAAAAAAAAA/gAB///4AIAAAAAAAAAABPgAA///+AIAgAAAAAA" +
        "AABAAAB///wAYHgAAAAAAAAeAAAD//+4BgeAAAAAAAAdhgAAf//nwePRAAAAAAAOZAAAP//34fPgAAAAAAAcAAAA" +
        "f///j//0AAAAAAHgAAAD//vf//8AAAAAAH0AAAD//9///4AAAAAB/8AAAf6H///+AAAAAD/+AAAXCf///ywAAAA/" +
        "/gAAMK/f//kQAAAD/+AABl3z///AAAAD//AACzjf//+QAAA//8AAf/v//+AAAD//IAH/////AAAH/vgAv////xAA" +
        "H/+AAT///4YAH+OACb///xA3/iEA9///6D/8TAP//////kwHf//9/hODj//gEjgAv4AA8BOAAPAEAFkAAgAAAA=="

    /**
     * نقاط خشکی به صورت آرایه‌ی تخت: هر نقطه دو خانه — عرض و طول جغرافیایی بر
     * حسب رادیان. آرایه‌ی تخت را عمداً به لیستِ شیء ترجیح داده‌ایم تا در حلقه‌ی
     * رسم، هیچ تخصیص حافظه‌ای رخ ندهد.
     */
    val landPoints: FloatArray by lazy { decode() }

    /** تعداد نقاط خشکی. */
    val landCount: Int get() = landPoints.size / 2

    private fun decode(): FloatArray {
        val bytes = Base64.decode(ENCODED, Base64.NO_WRAP)
        val result = ArrayList<Float>(3200)
        var bit = 0
        var latIdx = 0
        while (true) {
            val lat = LAT_START + latIdx * STEP
            if (lat > LAT_END + 1e-9) break
            val ringCount = maxOf(1, floor(360.0 * cos(Math.toRadians(lat)) / STEP + 0.5).toInt())
            for (k in 0 until ringCount) {
                val isLand = (bytes[bit ushr 3].toInt() and (0x80 ushr (bit and 7))) != 0
                if (isLand) {
                    val lon = -180.0 + k * (360.0 / ringCount)
                    result.add(Math.toRadians(lat).toFloat())
                    result.add(Math.toRadians(lon).toFloat())
                }
                bit++
            }
            latIdx++
        }
        return result.toFloatArray()
    }
}
