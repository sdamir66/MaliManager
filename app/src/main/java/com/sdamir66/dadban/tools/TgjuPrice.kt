package com.sdamir66.dadban.tools

// ═══════════════════════════════════════════════════════════════
//  TgjuPrice
// ═══════════════════════════════════════════════════════════════

data class TgjuPrice(
    val key: String,
    val title: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val direction: String,
    val low: Double,
    val high: Double,
    val time: String
)

// ═══════════════════════════════════════════════════════════════
//  PriceCatalog
//  تمام mapping ها و ترتیب‌ها
// ═══════════════════════════════════════════════════════════════

object PriceCatalog {

    // ═══ ترتیب کامل نمایش در لیست کلی ═══
    // طلا → سکه‌ها (امامی، بهار، قدیمی‌ها، گرمی) → ارزها → رمزارزها
    val ALL_ORDERED: List<String> = listOf(
        // ─── طلا ───
        "geram18",          // طلای ۱۸ عیار
        "geram24",          // طلای ۲۴ عیار
        "mesghal",          // مثقال طلا
        "gold_ounce",       // انس طلا
        "silver_ounce",     // انس نقره

        // ─── سکه‌ها ───
        "sekee",            // سکه امامی
        "sekeb",            // سکه بهار آزادی
        "137142",           // تمام سکه (قبل ۸۶)
        "nim",              // نیم‌سکه
        "137143",           // نیم‌سکه (قبل ۸۶)
        "rob",              // ربع‌سکه
        "137144",           // ربع‌سکه (قبل ۸۶)
        "gerami",           // سکه گرمی

        // ─── ارزها ───
        "price_dollar_rl",  // دلار
        "price_eur",        // یورو
        "price_gbp",        // پوند
        "price_aed",        // درهم
        "price_try",        // لیر ترکیه
        "price_iqd",        // دینار عراق
        "price_rub",        // روبل
        "price_kwd",        // دینار کویت

        // ─── رمزارزها ───
        "398096",           // بیت کوین
        "137138",           // تتر
        "398097",           // اتریوم
        "137203",           // بایننس کوین
        "137205",           // ریپل
        "137140",           // سولانا
        "137137",           // کاردانو
        "137139",           // دوج کوین
        "137141",           // ترون
        "137206",           // پولکادات
        "137207",           // پالیگان
        "137222",           // شیبا اینو
        "137121",           // لایت کوین
        "137122",           // بیت کوین کش
        "137119",           // استلار
        "137123"            // مونرو
    )

    // ═══ تاپ‌لیست پیش‌فرض ═══
    val DEFAULT_SELECTED: List<String> = listOf(
        "price_dollar_rl",  // دلار
        "137138",           // تتر
        "price_eur",        // یورو
        "sekee",            // سکه امامی
        "sekeb",            // سکه بهار آزادی
        "nim",              // نیم‌سکه
        "rob",              // ربع‌سکه
        "398096"            // بیت کوین
    )

    // ═══ رمزارزها (برای API نوبیتکس) ═══
    val CRYPTO_KEYS: Set<String> = setOf(
        "398096", "398097", "137138", "137203", "137205", "137137",
        "137139", "137140", "137141", "137206", "137207", "137222",
        "137121", "137122", "137119", "137123"
    )

    // ═══ mapping item_id → نماد نوبیتکس ═══
    val CRYPTO_SYMBOLS: Map<String, String> = mapOf(
        "398096" to "btc",
        "398097" to "eth",
        "137138" to "usdt",
        "137203" to "bnb",
        "137205" to "xrp",
        "137137" to "ada",
        "137139" to "doge",
        "137140" to "sol",
        "137141" to "trx",
        "137206" to "dot",
        "137207" to "matic",
        "137222" to "shib",
        "137121" to "ltc",
        "137122" to "bch",
        "137119" to "xlm",
        "137123" to "xmr"
    )

    // ═══ عنوان فارسی هر رمزارز ═══
    val CRYPTO_TITLES: Map<String, String> = mapOf(
        "398096" to "بیت کوین",
        "398097" to "اتریوم",
        "137138" to "تتر",
        "137203" to "بایننس کوین",
        "137205" to "ریپل",
        "137137" to "کاردانو",
        "137139" to "دوج کوین",
        "137140" to "سولانا",
        "137141" to "ترون",
        "137206" to "پولکادات",
        "137207" to "پالیگان",
        "137222" to "شیبا اینو",
        "137121" to "لایت کوین",
        "137122" to "بیت کوین کش",
        "137119" to "استلار",
        "137123" to "مونرو"
    )

    // ═══ ترتیب نمایش در تاپ‌لیست ═══
    fun sortForTopList(keys: List<String>): List<String> {
        return keys.sortedBy { key ->
            val index = DEFAULT_SELECTED.indexOf(key)
            if (index >= 0) index else Int.MAX_VALUE
        }
    }

    // ═══ ترتیب نمایش در لیست کلی ═══
    fun sortForAllList(keys: List<String>): List<String> {
        return keys.sortedBy { key ->
            val index = ALL_ORDERED.indexOf(key)
            if (index >= 0) index else Int.MAX_VALUE
        }
    }

    // ═══ عنوان فارسی پیش‌فرض (قبل از لود از API) ═══
    val DEFAULT_TITLES: Map<String, String> = mapOf(
        // طلا
        "geram18" to "طلای ۱۸ عیار",
        "geram24" to "طلای ۲۴ عیار",
        "mesghal" to "مثقال طلا",
        "gold_ounce" to "انس طلا",
        "silver_ounce" to "انس نقره",
        // سکه
        "sekee" to "سکه امامی",
        "sekeb" to "سکه بهار آزادی",
        "137142" to "تمام سکه (قبل ۸۶)",
        "nim" to "نیم‌سکه",
        "137143" to "نیم‌سکه (قبل ۸۶)",
        "rob" to "ربع‌سکه",
        "137144" to "ربع‌سکه (قبل ۸۶)",
        "gerami" to "سکه گرمی",
        // ارز
        "price_dollar_rl" to "دلار آمریکا",
        "price_eur" to "یورو",
        "price_gbp" to "پوند",
        "price_aed" to "درهم امارات",
        "price_try" to "لیر ترکیه",
        "price_iqd" to "دینار عراق",
        "price_rub" to "روبل روسیه",
        "price_kwd" to "دینار کویت"
    )
}
