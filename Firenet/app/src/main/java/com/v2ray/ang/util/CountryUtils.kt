package com.v2ray.ang.util

import android.content.Context
import com.v2ray.ang.R
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

object CountryUtils {

    fun getFlagResId(context: Context, remark: String?): Int {
        // 1. اگر ریمارک خالی بود، پیش‌فرض را برگردان
        if (remark.isNullOrEmpty()) return R.drawable.unknown

        // 2. تلاش برای استخراج کد کشور مستقیماً از ایموجی پرچم (مثل 🇩🇪 -> de)
        val emojiCode = extractCountryCodeFromEmoji(remark)
        if (emojiCode != null) {
            val resId = getResId(context, emojiCode)
            if (resId != 0) return resId
        }

        // 3. نرمال‌سازی متن (تبدیل فونت‌های عجیب 𝐃𝐄 به DE و حذف اعراب)
        val normalizedRemark = normalizeText(remark)

        // 4. تشخیص کد کشور از روی متن نرمال شده
        val code = detectCountryCode(normalizedRemark)
        
        // 5. دریافت عکس
        val resId = getResId(context, code)
        // اگر پیدا نشد، تلاش برای پیدا کردن عکس unknown
        if (resId == 0) {
             val unknownId = context.resources.getIdentifier("unknown", "drawable", context.packageName)
             return if (unknownId != 0) unknownId else R.drawable.unknown
        }
        return resId
    }

    private fun getResId(context: Context, code: String): Int {
        // تلاش اول: نام دقیق (مثلا "ir")
        var resId = context.resources.getIdentifier(code, "drawable", context.packageName)
        // تلاش دوم: با پیشوند (مثلا "flag_ir")
        if (resId == 0) {
            resId = context.resources.getIdentifier("flag_$code", "drawable", context.packageName)
        }
        return resId
    }

    /**
     * این تابع ایموجی پرچم را پیدا کرده و به کد دو حرفی تبدیل می‌کند.
     * مثال: 🇩🇪 -> "de"
     */
    private fun extractCountryCodeFromEmoji(text: String): String? {
        val flagPattern = Pattern.compile("[\\uD83C][\\uDDE6-\\uDDFF][\\uD83C][\\uDDE6-\\uDDFF]")
        val matcher = flagPattern.matcher(text)
        if (matcher.find()) {
            val flag = matcher.group()
            val sb = StringBuilder()
            var i = 0
            while (i < flag.length) {
                val codePoint = flag.codePointAt(i)
                if (codePoint in 0x1F1E6..0x1F1FF) {
                    sb.append((codePoint - 0x1F1E6 + 'a'.code).toChar())
                }
                i += Character.charCount(codePoint)
            }
            if (sb.length == 2) return sb.toString()
        }
        return null
    }

    /**
     * تبدیل فونت‌های فانتزی و حذف کاراکترهای خاص
     */
    private fun normalizeText(text: String): String {
        var normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        normalized = normalized.lowercase(Locale.ROOT)
        return normalized
    }

    private fun detectCountryCode(text: String): String {
        return when {
            // --- امارات (UAE) ---
            contains(text, "andorra", "ad") -> "ad"
            contains(text, "uae", "emirates", "dubai", "abudhabi", "امارات", "دبی", "ae") -> "ae"
            
            // --- افغانستان ---
            contains(text, "afghanistan", "افغانستان", "af") -> "af"
            contains(text, "antigua", "barbuda", "ag") -> "ag"
            contains(text, "anguilla", "ai") -> "ai"
            contains(text, "albania", "البانی", "al") -> "al"
            contains(text, "armenia", "ارمنستان", "am") -> "am"
            contains(text, "angola", "انگو", "ao") -> "ao"
            contains(text, "antarctica", "aq") -> "aq"
            contains(text, "argentina", "ارژانتین", "ar") -> "ar"
            contains(text, "american samoa", "as") -> "as"
            
            // --- اتریش ---
            contains(text, "austria", "vienna", "اتریش", "وین", "at") -> "at"
            
            // --- استرالیا ---
            contains(text, "australia", "sydney", "استرالیا", "سیدنی", "au") -> "au"
            contains(text, "aruba", "aw") -> "aw"
            contains(text, "aland", "ax") -> "ax"
            contains(text, "azerbaijan", "آذربایجان", "az") -> "az"
            contains(text, "bosnia", "herzegovina", "بوسنی", "ba") -> "ba"
            contains(text, "barbados", "bb") -> "bb"
            contains(text, "bangladesh", "بنگلادش", "bd") -> "bd"
            contains(text, "belgium", "brussels", "بلژیک", "بروکسل", "be") -> "be"
            contains(text, "burkina faso", "bf") -> "bf"
            contains(text, "bulgaria", "بلغارستان", "bg") -> "bg"
            contains(text, "bahrain", "بحرین", "bh") -> "bh"
            contains(text, "burundi", "bi") -> "bi"
            contains(text, "benin", "bj") -> "bj"
            contains(text, "st barthelemy", "bl") -> "bl"
            contains(text, "bermuda", "bm") -> "bm"
            contains(text, "brunei", "برونئی", "bn") -> "bn"
            contains(text, "bolivia", "بولیوی", "bo") -> "bo"
            contains(text, "bonaire", "bq") -> "bq"
            contains(text, "brazil", "برزیل", "br") -> "br"
            contains(text, "bahamas", "باهاما", "bs") -> "bs"
            contains(text, "bhutan", "بوتان", "bt") -> "bt"
            contains(text, "bouvet", "bv") -> "bv"
            contains(text, "botswana", "bw") -> "bw"
            contains(text, "belarus", "بلاروس", "by") -> "by"
            contains(text, "belize", "bz") -> "bz"
            
            // --- کانادا ---
            contains(text, "canada", "montreal", "vancouver", "کانادا", "مونترال", "ca") -> "ca"
            contains(text, "cocos", "cc") -> "cc"
            contains(text, "congo", "کنگو", "cd") -> "cd"
            contains(text, "central african", "cf") -> "cf"
            contains(text, "congo brazzaville", "cg") -> "cg"
            
            // --- سوئیس ---
            contains(text, "switzerland", "zurich", "سوئیس", "زوریخ", "ch") -> "ch"
            contains(text, "ivory coast", "cote d'ivoire", "ci") -> "ci"
            contains(text, "cook islands", "ck") -> "ck"
            contains(text, "chile", "شیلی", "cl") -> "cl"
            contains(text, "cameroon", "کامرون", "cm") -> "cm"
            
            // --- چین ---
            contains(text, "china", "beijing", "shanghai", "چین", "پکن", "شانگهای", "cn") -> "cn"
            contains(text, "colombia", "کلمبیا", "co") -> "co"
            contains(text, "costa rica", "کاستاریکا", "cr") -> "cr"
            contains(text, "cuba", "کوبا", "cu") -> "cu"
            contains(text, "cape verde", "cv") -> "cv"
            contains(text, "curacao", "cw") -> "cw"
            contains(text, "christmas island", "cx") -> "cx"
            contains(text, "cyprus", "قبرس", "cy") -> "cy"
            contains(text, "czech", "prague", "چک", "پراگ", "cz") -> "cz"
            
            // --- آلمان ---
            contains(text, "germany", "deutschland", "frankfurt", "falkenstein", "hetzner", "آلمان", "فرانکفورت", "هتزنر", "de") -> "de"
            contains(text, "djibouti", "dj") -> "dj"
            contains(text, "denmark", "copenhagen", "دانمارک", "کپنهاگ", "dk") -> "dk"
            contains(text, "dominica", "dm") -> "dm"
            contains(text, "dominican republic", "do") -> "dom"
            contains(text, "algeria", "الجزایر", "dz") -> "dz"
            contains(text, "ecuador", "اکوادور", "ec") -> "ec"
            contains(text, "estonia", "استونی", "ee") -> "ee"
            contains(text, "egypt", "مصر", "eg") -> "eg"
            contains(text, "western sahara", "eh") -> "eh"
            contains(text, "eritrea", "اریتره", "er") -> "er"
            contains(text, "spain", "madrid", "اسپانیا", "مادرید", "es") -> "es"
            contains(text, "ethiopia", "اتیوپی", "et") -> "et"
            
            // --- فنلاند ---
            contains(text, "finland", "helsinki", "فنلاند", "هلسینکی", "fi") -> "fi"
            contains(text, "fiji", "فیجی", "fj") -> "fj"
            contains(text, "falkland", "fk") -> "fk"
            contains(text, "micronesia", "fm") -> "fm"
            contains(text, "faroe", "fo") -> "fo"
            
            // --- فرانسه ---
            contains(text, "france", "paris", "marseille", "فرانسه", "پاریس", "fr") -> "fr"
            contains(text, "gabon", "گابن", "ga") -> "ga"
            contains(text, "england", "انگلیس", "gb_eng") -> "gb_eng"
            contains(text, "northern ireland", "gb_nir") -> "gb_nir"
            contains(text, "scotland", "اسکاتلند", "gb_sct") -> "gb_sct"
            contains(text, "wales", "ولز", "gb_wls") -> "gb_wls"
            
            // --- بریتانیا ---
            contains(text, "united kingdom", "uk", "britain", "london", "manchester", "بریتانیا", "لندن", "gb") -> "gb"
            contains(text, "grenada", "gd") -> "gd"
            contains(text, "georgia", "گرجستان", "ge") -> "ge"
            contains(text, "french guiana", "gf") -> "gf"
            contains(text, "guernsey", "gg") -> "gg"
            contains(text, "ghana", "غنا", "gh") -> "gh"
            contains(text, "gibraltar", "gi") -> "gi"
            contains(text, "greenland", "گرینلند", "gl") -> "gl"
            contains(text, "gambia", "گامبیا", "gm") -> "gm"
            contains(text, "guinea", "گینه", "gn") -> "gn"
            contains(text, "guadeloupe", "gp") -> "gp"
            contains(text, "equatorial guinea", "gq") -> "gq"
            contains(text, "greece", "athens", "یونان", "آتن", "gr") -> "gr"
            contains(text, "south georgia", "gs") -> "gs"
            contains(text, "guatemala", "گواتمالا", "gt") -> "gt"
            contains(text, "guam", "gu") -> "gu"
            contains(text, "guinea-bissau", "gw") -> "gw"
            contains(text, "guyana", "gy") -> "gy"
            contains(text, "hong kong", "هنگ کنگ", "hk") -> "hk"
            contains(text, "heard island", "hm") -> "hm"
            contains(text, "honduras", "هندوراس", "hn") -> "hn"
            contains(text, "croatia", "کرواسی", "hr") -> "hr"
            contains(text, "haiti", "هائیتی", "ht") -> "ht"
            contains(text, "hungary", "budapest", "مجارستان", "بوداپست", "hu") -> "hu"
            contains(text, "indonesia", "jakarta", "اندونزی", "جاکارتا", "id") -> "id"
            contains(text, "ireland", "dublin", "ایرلند", "دوبلین", "ie") -> "ie"
            contains(text, "israel", "tel aviv", "اسرائیل", "تلاویو", "il") -> "il"
            contains(text, "isle of man", "im") -> "im"
            contains(text, "india", "mumbai", "delhi", "هند", "دهلی", "مومبای", "in") -> "ind"
            contains(text, "british indian ocean", "io") -> "io"
            contains(text, "iraq", "عراق", "iq") -> "iq"
            
            // --- ایران ---
            contains(text, "iran", "tehran", "shiraz", "mashhad", "irancell", "mci", "ایران", "تهران", "ایرانسل", "همراه", "مخابرات", "ir") -> "ir"
            contains(text, "iceland", "ایسلند", "is") -> "is"
            contains(text, "italy", "rome", "milan", "ایتالیا", "رم", "میلان", "it") -> "it"
            contains(text, "jersey", "je") -> "je"
            contains(text, "jamaica", "جامائیکا", "jm") -> "jm"
            contains(text, "jordan", "اردن", "jo") -> "jo"
            contains(text, "japan", "tokyo", "osaka", "ژاپن", "توکیو", "jp") -> "jp"
            contains(text, "kenya", "کنیا", "ke") -> "ke"
            contains(text, "kyrgyzstan", "قرقیزستان", "kg") -> "kg"
            contains(text, "cambodia", "کامبوج", "kh") -> "kh"
            contains(text, "kiribati", "ki") -> "ki"
            contains(text, "comoros", "کومور", "km") -> "km"
            contains(text, "st kitts", "kn") -> "kn"
            contains(text, "north korea", "pyongyang", "کره شمالی", "kp") -> "kp"
            contains(text, "south korea", "seoul", "کره جنوبی", "سئول", "kr") -> "kr"
            contains(text, "kuwait", "کویت", "kw") -> "kw"
            contains(text, "cayman islands", "ky") -> "ky"
            contains(text, "kazakhstan", "قزاقستان", "kz") -> "kz"
            contains(text, "laos", "لائوس", "la") -> "la"
            contains(text, "lebanon", "لبنان", "lb") -> "lb"
            contains(text, "st lucia", "lc") -> "lc"
            contains(text, "liechtenstein", "لیختن", "li") -> "li"
            contains(text, "sri lanka", "سریلانکا", "lk") -> "lk"
            contains(text, "liberia", "لیبریا", "lr") -> "lr"
            contains(text, "lesotho", "ls") -> "ls"
            contains(text, "lithuania", "لیتوانی", "lt") -> "lt"
            contains(text, "luxembourg", "لوکزامبورگ", "lu") -> "lu"
            contains(text, "latvia", "لتونی", "lv") -> "lv"
            contains(text, "libya", "لیبی", "ly") -> "ly"
            contains(text, "morocco", "مراکش", "ma") -> "ma"
            contains(text, "monaco", "موناکو", "mc") -> "mc"
            contains(text, "moldova", "مولداوی", "md") -> "md"
            contains(text, "montenegro", "مونته", "me") -> "me"
            contains(text, "st martin", "mf") -> "mf"
            contains(text, "madagascar", "ماداگاسکار", "mg") -> "mg"
            contains(text, "marshall islands", "mh") -> "mh"
            contains(text, "macedonia", "skopje", "مقدونیه", "mk") -> "mk"
            contains(text, "mali", "مالی", "ml") -> "ml"
            contains(text, "myanmar", "burma", "میانمار", "mm") -> "mm"
            contains(text, "mongolia", "مغولستان", "mn") -> "mn"
            contains(text, "macau", "ماکائو", "mo") -> "mo"
            contains(text, "northern mariana", "mp") -> "mp"
            contains(text, "martinique", "mq") -> "mq"
            contains(text, "mauritania", "موریتانی", "mr") -> "mr"
            contains(text, "montserrat", "ms") -> "ms"
            contains(text, "malta", "مالت", "mt") -> "mt"
            contains(text, "mauritius", "موریس", "mu") -> "mu"
            contains(text, "maldives", "مالدیو", "mv") -> "mv"
            contains(text, "malawi", "مالاوی", "mw") -> "mw"
            contains(text, "mexico", "مکزیک", "mx") -> "mx"
            contains(text, "malaysia", "مالزی", "my") -> "my"
            contains(text, "mozambique", "موزامبیک", "mz") -> "mz"
            contains(text, "namibia", "نامیبیا", "na") -> "na"
            contains(text, "new caledonia", "nc") -> "nc"
            contains(text, "niger", "نیجر", "ne") -> "ne"
            contains(text, "norfolk island", "nf") -> "nf"
            contains(text, "nigeria", "نیجریه", "ng") -> "ng"
            contains(text, "nicaragua", "نیکاراگوئه", "ni") -> "ni"
            
            // --- هلند ---
            contains(text, "netherlands", "amsterdam", "holland", "هلند", "آمستردام", "nl") -> "nl"
            contains(text, "norway", "oslo", "نروژ", "اسلو", "no") -> "no"
            contains(text, "nepal", "نپال", "np") -> "np"
            contains(text, "nauru", "نائورو", "nr") -> "nr"
            contains(text, "niue", "nu") -> "nu"
            contains(text, "new zealand", "نیوزلند", "nz") -> "nz"
            contains(text, "oman", "عمان", "om") -> "om"
            contains(text, "panama", "پاناما", "pa") -> "pa"
            contains(text, "peru", "پرو", "pe") -> "pe"
            contains(text, "french polynesia", "pf") -> "pf"
            contains(text, "papua new guinea", "گینه نو", "pg") -> "pg"
            contains(text, "philippines", "فیلیپین", "ph") -> "ph"
            contains(text, "pakistan", "پاکستان", "pk") -> "pk"
            contains(text, "poland", "warsaw", "لهستان", "ورشو", "pl") -> "pl"
            contains(text, "st pierre", "pm") -> "pm"
            contains(text, "pitcairn", "pn") -> "pn"
            contains(text, "puerto rico", "پورتوریکو", "pr") -> "pr"
            contains(text, "palestine", "gaza", "فلسطین", "غزه", "ps") -> "ps"
            contains(text, "portugal", "lisbon", "پرتغال", "لیسبون", "pt") -> "pt"
            contains(text, "palau", "پالائو", "pw") -> "pw"
            contains(text, "paraguay", "پاراگوئه", "py") -> "py"
            contains(text, "qatar", "doha", "قطر", "دوحه", "qa") -> "qa"
            contains(text, "reunion", "re") -> "re"
            contains(text, "romania", "رومانی", "ro") -> "ro"
            contains(text, "serbia", "صربستان", "rs") -> "rs"
            
            // --- روسیه ---
            contains(text, "russia", "moscow", "saint petersburg", "روسیه", "مسکو", "ru") -> "ru"
            contains(text, "rwanda", "رواندا", "rw") -> "rw"
            contains(text, "saudi arabia", "riyadh", "عربستان", "ریاض", "sa") -> "sa"
            contains(text, "solomon islands", "sb") -> "sb"
            contains(text, "seychelles", "سیشل", "sc") -> "sc"
            contains(text, "sudan", "سودان", "sd") -> "sd"
            contains(text, "sweden", "stockholm", "سوئد", "استکهلم", "se") -> "se"
            contains(text, "singapore", "سنگاپور", "sg") -> "sg"
            contains(text, "st helena", "sh") -> "sh"
            contains(text, "slovenia", "اسلوونی", "si") -> "si"
            contains(text, "svalbard", "jan mayen", "sj") -> "sj"
            contains(text, "slovakia", "اسلواکی", "sk") -> "sk"
            contains(text, "sierra leone", "سیرالئون", "sl") -> "sl"
            contains(text, "san marino", "سن مارینو", "sm") -> "sm"
            contains(text, "senegal", "سنگال", "sn") -> "sn"
            contains(text, "somalia", "سومالی", "so") -> "so"
            contains(text, "suriname", "سورینام", "sr") -> "sr"
            contains(text, "south sudan", "سودان جنوبی", "ss") -> "ss"
            contains(text, "sao tome", "st") -> "st"
            contains(text, "el salvador", "السالوادور", "sv") -> "sv"
            contains(text, "sint maarten", "sx") -> "sx"
            contains(text, "syria", "سوریه", "sy") -> "sy"
            contains(text, "eswatini", "swaziland", "sz") -> "sz"
            contains(text, "turks and caicos", "tc") -> "tc"
            contains(text, "chad", "چاد", "td") -> "td"
            contains(text, "french southern territories", "tf") -> "tf"
            contains(text, "togo", "توگو", "tg") -> "tg"
            contains(text, "thailand", "bangkok", "تایلند", "بانکوک", "th") -> "th"
            contains(text, "tajikistan", "تاجیکستان", "tj") -> "tj"
            contains(text, "tokelau", "tk") -> "tk"
            contains(text, "timor-leste", "tl") -> "tl"
            contains(text, "turkmenistan", "ترکمنستان", "tm") -> "tm"
            contains(text, "tunisia", "تونس", "tn") -> "tn"
            contains(text, "tonga", "تونگا", "to") -> "to"
            
            // --- ترکیه ---
            contains(text, "turkey", "ankara", "istanbul", "ترکیه", "استانبول", "آنکارا", "tr") -> "tr"
            contains(text, "trinidad", "tobago", "ترینیداد", "tt") -> "tt"
            contains(text, "tuvalu", "تووالو", "tv") -> "tv"
            contains(text, "taiwan", "taipei", "تایوان", "تایپه", "tw") -> "tw"
            contains(text, "tanzania", "تانزانیا", "tz") -> "tz"
            contains(text, "ukraine", "kiev", "kyiv", "اوکراین", "کیف", "ua") -> "ua"
            contains(text, "uganda", "اوگاندا", "ug") -> "ug"
            contains(text, "us minor outlying", "um") -> "um"
            
            // --- آمریکا ---
            contains(text, "usa", "united states", "america", "new york", "california", "آمریکا", "ایالات متحده", "نیویورک", "us") -> "us"
            contains(text, "uruguay", "اروگوئه", "uy") -> "uy"
            contains(text, "uzbekistan", "ازبکستان", "uz") -> "uz"
            contains(text, "vatican", "holy see", "واتیکان", "va") -> "va"
            contains(text, "st vincent", "grenadines", "vc") -> "vc"
            contains(text, "venezuela", "ونزوئلا", "ve") -> "ve"
            contains(text, "british virgin islands", "vg") -> "vg"
            contains(text, "us virgin islands", "vi") -> "vi"
            contains(text, "vietnam", "hanoi", "ویتنام", "هانوی", "vn") -> "vn"
            contains(text, "vanuatu", "وانواتو", "vu") -> "vu"
            contains(text, "wallis", "futuna", "wf") -> "wf"
            contains(text, "samoa", "ساموآ", "ws") -> "ws"
            contains(text, "kosovo", "کوزوو", "xk") -> "xk"
            contains(text, "yemen", "یمن", "ye") -> "ye"
            contains(text, "mayotte", "yt") -> "yt"
            contains(text, "south africa", "johannesburg", "آفریقای جنوبی", "za") -> "za"
            contains(text, "zambia", "زامبیا", "zm") -> "zm"
            contains(text, "zimbabwe", "زیمبابوه", "zw") -> "zw"
            else -> "unknown"
        }
    }

    private fun contains(text: String, vararg keywords: String): Boolean {
        for (keyword in keywords) {
            // اگر کلمه کلیدی کوتاه بود (مثل tr یا us) باید چک کنیم که جزئی از کلمه دیگر نباشد
            // وگرنه Traffic یا Internet اشتباها مچ میشن
            if (keyword.length <= 2) {
                // این پترن می‌گوید: قبل و بعد از کلمه نباید حرف یا عدد باشد
                val pattern = Pattern.compile("(?<![a-z0-9])${Pattern.quote(keyword)}(?![a-z0-9])")
                if (pattern.matcher(text).find()) return true
            } else {
                // برای کلمات طولانی، وجود ساده کافی است
                if (text.contains(keyword)) return true
            }
        }
        return false
    }
}