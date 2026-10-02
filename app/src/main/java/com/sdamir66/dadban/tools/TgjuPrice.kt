package com.sdamir66.dadban.tools

// ═══════════════════════════════════════════════════════════════
//  TgjuPrice
//  مدل داده برای هر آیتم قیمت (ارز، طلا، سکه، رمزارز)
// ═══════════════════════════════════════════════════════════════

data class TgjuPrice(
    val key: String,             // "price_dollar_rl" یا "398096"
    val title: String,           // "دلار آمریکا"
    val price: Double,           // 1053883974.67
    val change: Double,          // تغییر
    val changePercent: Double,   // درصد تغییر
    val direction: String,       // "high" | "low" | ""
    val low: Double,             // کمترین
    val high: Double,            // بیشترین
    val time: String             // زمان به‌روزرسانی (رشته)
)

// ═══════════════════════════════════════════════════════════════
//  دسته‌بندی‌ها
// ═══════════════════════════════════════════════════════════════

enum class PriceCategory(val label: String, val keys: List<String>) {
    CURRENCY("ارز", listOf(
        "price_dollar_rl",  // دلار
        "price_eur",        // یورو
        "price_gbp",        // پوند
        "price_aed",        // درهم امارات
        "price_try",        // لیر ترکیه
        "price_iqd",        // دینار عراق
        "price_rub",        // روبل روسیه
        "price_kwd"         // دینار کویت
    )),

    GOLD_COIN("طلا و سکه", listOf(
        "geram18",          // طلای ۱۸ عیار
        "geram24",          // طلای ۲۴ عیار
        "mesghal",          // مثقال طلا
        "sekee",            // سکه امامی
        "sekeb",            // سکه بهار آزادی
        "nim",              // نیم‌سکه
        "rob",              // ربع‌سکه
        "gerami"            // سکه گرمی
    )),

    CRYPTO("رمزارز", listOf(
        "398096",   // بیت کوین
        "398097",   // اتریوم
        "137138",   // تتر
        "137203",   // بایننس کوین
        "137205",   // ریپل
        "137137",   // کاردانو
        "137139",   // دوج کوین
        "137140",   // سولانا
        "137141",   // ترون
        "137206",   // پولکادات
        "137207",   // پالیگان
        "137222",   // شیبا اینو
        "137121",   // لایت کوین
        "137122",   // بیت کوین کش
        "137119",   // استلار
        "137123",   // مونرو
        "137142",   // ایاس
        "137143",   // تزوس
        "137144"    // فایل کوین
    ));

    companion object {
        val DEFAULT_SELECTED = listOf(
            "price_dollar_rl",  // دلار
            "price_eur",        // یورو
            "geram18",          // طلای ۱۸ عیار
            "sekee",            // سکه امامی
            "nim",              // نیم‌سکه
            "rob"               // ربع‌سکه
        )

        // ✅ همه‌ی key ها به ترتیب: ارز → طلا → سکه → رمزارز
        val ALL_KEYS: List<String> = CURRENCY.keys + GOLD_COIN.keys + CRYPTO.keys

        // ✅ دسته‌ی هر key
        fun categoryOf(key: String): PriceCategory? {
            return entries.find { key in it.keys }
        }
    }
}
